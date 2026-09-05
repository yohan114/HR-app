import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { QueryErrorState } from '@/components/QueryErrorState'
import { Badge, Button, Card, DataTable, EmptyState, Field, LoadingState } from '@/components/ui'
import { authApi, extractApiError, humaniseError } from '@/lib/api'
import type { Device, MfaStatus } from '@hr/client'

/**
 * The account security page: registered devices, and two-factor authentication.
 *
 * Both halves are the user's own account rather than an administrative view of someone else's, so
 * there is no permission gate — every authenticated user may manage their own security, and the
 * endpoints derive the subject from the token rather than from a path parameter.
 */
export function Security() {
  return (
    <div className="stack">
      <h1>Security</h1>
      <MfaSection />
      <DeviceSection />
    </div>
  )
}

/* -------------------------------------------------------------------------- */
/* Two-factor authentication                                                   */
/* -------------------------------------------------------------------------- */

function MfaSection() {
  const query = useQuery({ queryKey: ['mfa-status'], queryFn: () => authApi.getMfaStatus() })

  if (query.isPending) return <LoadingState label="Checking two-factor status…" />
  if (query.isError) {
    return <QueryErrorState error={query.error} onRetry={() => void query.refetch()} />
  }

  return query.data.enabled ? (
    <MfaEnabled status={query.data} />
  ) : (
    <MfaEnrolment pending={query.data.enrolmentPending} />
  )
}

/**
 * Enrolment: get a secret, prove it works, receive recovery codes.
 *
 * Two steps rather than one because a secret the user has not yet proved they can read is a lockout
 * waiting to happen — they scan it, we enable, they discover their clock is wrong, and now nobody
 * can sign in. Confirming a live code first makes that impossible.
 */
function MfaEnrolment({ pending }: { pending: boolean }) {
  const queryClient = useQueryClient()
  const [secret, setSecret] = useState<string | null>(null)
  const [code, setCode] = useState('')
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const begin = useMutation({
    mutationFn: () => authApi.beginMfaEnrolment(),
    onSuccess: (enrolment) => {
      setSecret(enrolment.secret)
      setError(null)
    },
    onError: async (cause) => setError(await messageFor(cause)),
  })

  const confirm = useMutation({
    mutationFn: () => authApi.confirmMfaEnrolment({ mfaCodeRequest: { code } }),
    onSuccess: (response) => {
      // Held in state, not refetched: this is the only time the server will ever return them.
      setRecoveryCodes(response.recoveryCodes)
      setError(null)
    },
    onError: async (cause) => {
      setError(await messageFor(cause))
      setCode('')
    },
  })

  if (recoveryCodes !== null) {
    // The status refresh is deliberately deferred until the user dismisses this card. Refreshing
    // on success would report MFA as enabled, which swaps this component out for the enabled view
    // and takes the codes with it — and the server will never show them again.
    return (
      <RecoveryCodes
        codes={recoveryCodes}
        onDone={() => {
          setRecoveryCodes(null)
          void queryClient.invalidateQueries({ queryKey: ['mfa-status'] })
        }}
      />
    )
  }

  if (secret === null) {
    return (
      <Card
        title="Two-factor authentication"
        actions={
          <Button onClick={() => begin.mutate()} disabled={begin.isPending}>
            {pending ? 'Resume setup' : 'Set up'}
          </Button>
        }
      >
        <p>
          Protect your account with a code from an authenticator app, in addition to your password.
        </p>
        {pending && (
          <p className="field__hint">
            You started setting this up but did not finish. Starting again issues a new secret, so
            remove any half-finished entry from your authenticator app first.
          </p>
        )}
        {error !== null && <p className="field__error">{error}</p>}
      </Card>
    )
  }

  return (
    <Card title="Two-factor authentication">
      <ol className="stack">
        <li>
          <p>Add this secret to your authenticator app:</p>
          {/*
            Shown for manual entry rather than as a QR code, which would need a rendering
            dependency this console does not carry. Every authenticator app accepts manual entry;
            grouping is what makes typing 32 characters off a screen survivable.
          */}
          <code className="secret">{group(secret)}</code>
        </li>
        <li>
          <p>Then enter the six-digit code it shows:</p>
          <Field
            label="Verification code"
            value={code}
            onChange={(event) => setCode(event.target.value)}
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            error={error ?? undefined}
          />
        </li>
      </ol>
      <div className="row">
        <Button onClick={() => confirm.mutate()} disabled={code.length !== 6 || confirm.isPending}>
          {confirm.isPending ? 'Checking…' : 'Turn on'}
        </Button>
        <Button variant="ghost" onClick={() => setSecret(null)}>
          Cancel
        </Button>
      </div>
    </Card>
  )
}

/**
 * Shown exactly once, after enrolment or regeneration.
 *
 * The server stores only hashes, so it genuinely cannot show these again — which is worth saying
 * plainly rather than leaving the user to find out when they are locked out of their account.
 */
function RecoveryCodes({ codes, onDone }: { codes: string[]; onDone: () => void }) {
  return (
    <Card title="Save your recovery codes">
      <p>
        Each code works once, and lets you sign in if you lose your phone. This is the only time
        they can be shown — we store them hashed, so we cannot show them again.
      </p>
      <ul className="recovery-codes">
        {codes.map((recoveryCode) => (
          <li key={recoveryCode}>
            <code>{recoveryCode}</code>
          </li>
        ))}
      </ul>
      <Button onClick={onDone}>I have saved these</Button>
    </Card>
  )
}

/** The enabled state: how many codes are left, and the two ways out. */
function MfaEnabled({ status }: { status: MfaStatus }) {
  const queryClient = useQueryClient()
  const [action, setAction] = useState<'regenerate' | 'disable' | null>(null)
  const [code, setCode] = useState('')
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const finish = () => {
    setAction(null)
    setCode('')
    setError(null)
    void queryClient.invalidateQueries({ queryKey: ['mfa-status'] })
  }

  const regenerate = useMutation({
    mutationFn: () => authApi.regenerateRecoveryCodes({ mfaCodeRequest: { code } }),
    onSuccess: (response) => {
      setRecoveryCodes(response.recoveryCodes)
      finish()
    },
    onError: async (cause) => {
      setError(await messageFor(cause))
      setCode('')
    },
  })

  const disable = useMutation({
    mutationFn: () => authApi.disableMfa({ mfaCodeRequest: { code } }),
    onSuccess: finish,
    onError: async (cause) => {
      setError(await messageFor(cause))
      setCode('')
    },
  })

  if (recoveryCodes !== null) {
    return <RecoveryCodes codes={recoveryCodes} onDone={() => setRecoveryCodes(null)} />
  }

  // Three is the point at which running out stops being hypothetical.
  const codesLow = status.recoveryCodesRemaining <= 3

  return (
    <Card
      title="Two-factor authentication"
      actions={<Badge tone="success">On</Badge>}
    >
      <p>
        {status.recoveryCodesRemaining} recovery{' '}
        {status.recoveryCodesRemaining === 1 ? 'code' : 'codes'} remaining.
        {codesLow && ' Generate a new set before you run out.'}
      </p>

      {action === null ? (
        <div className="row">
          <Button variant="secondary" onClick={() => setAction('regenerate')}>
            New recovery codes
          </Button>
          <Button variant="ghost" onClick={() => setAction('disable')}>
            Turn off
          </Button>
        </div>
      ) : (
        <div className="stack">
          <p>
            {action === 'disable'
              ? 'Enter a current code to turn two-factor authentication off.'
              : 'Enter a current code. Generating a new set invalidates the codes you have now.'}
          </p>
          {/*
            The code is required by the server, not just collected here: turning off a second
            factor is exactly what an attacker with a stolen session would want to do first, so it
            takes proof of possession like any other sensitive change.
          */}
          <Field
            label="Verification code"
            value={code}
            onChange={(event) => setCode(event.target.value)}
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            error={error ?? undefined}
          />
          <div className="row">
            <Button
              variant={action === 'disable' ? 'danger' : 'primary'}
              onClick={() => (action === 'disable' ? disable.mutate() : regenerate.mutate())}
              disabled={code.length !== 6 || disable.isPending || regenerate.isPending}
            >
              {action === 'disable' ? 'Turn off' : 'Generate'}
            </Button>
            <Button
              variant="ghost"
              onClick={() => {
                setAction(null)
                setCode('')
                setError(null)
              }}
            >
              Cancel
            </Button>
          </div>
        </div>
      )}
    </Card>
  )
}

/* -------------------------------------------------------------------------- */
/* Devices                                                                     */
/* -------------------------------------------------------------------------- */

function DeviceSection() {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const query = useQuery({ queryKey: ['devices'], queryFn: () => authApi.listDevices() })

  const revoke = useMutation({
    mutationFn: (id: string) => authApi.revokeDevice({ id }),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['devices'] })
    },
    onError: async (cause) => setError(await messageFor(cause)),
  })

  if (query.isPending) return <LoadingState label="Loading your devices…" />
  if (query.isError) {
    return <QueryErrorState error={query.error} onRetry={() => void query.refetch()} />
  }
  if (query.data.length === 0) {
    return (
      <Card title="Devices">
        <EmptyState
          title="No devices registered"
          description="Devices appear here once you sign in to the mobile app."
        />
      </Card>
    )
  }

  return (
    <Card title="Devices">
      <p>Sign-ins on your account. Revoke anything you do not recognise.</p>
      {error !== null && <p className="field__error">{error}</p>}
      <DataTable
        caption="Devices registered to your account"
        rows={query.data}
        rowKey={(device: Device) => device.id}
        columns={[
          {
            header: 'Device',
            render: (device: Device) => (
              <>
                <strong>{device.model ?? platformName(device.platform)}</strong>
                {device.current === true && <Badge tone="neutral">This device</Badge>}
              </>
            ),
          },
          {
            header: 'System',
            render: (device: Device) =>
              [platformName(device.platform), device.osVersion].filter(Boolean).join(' '),
          },
          { header: 'App', render: (device: Device) => device.appVersion ?? '—' },
          {
            header: 'Protection',
            render: (device: Device) => (
              <>
                {device.biometricEnrolled === true && <Badge tone="success">Biometric</Badge>}
                {/*
                  Untrusted is worth surfacing and trusted is not: a badge on every normal row is
                  decoration, while the one row that failed attestation is the whole point of
                  showing this column.
                */}
                {!device.trusted && <Badge tone="warning">Not trusted</Badge>}
              </>
            ),
          },
          { header: 'Last used', render: (device: Device) => formatLastSeen(device.lastSeenAt) },
          {
            header: '',
            render: (device: Device) => (
              <Button
                variant="ghost"
                onClick={() => {
                  // Revoking the current device ends this session, and the auth layer will land
                  // the user on the sign-in page a moment later. Saying so first means that is a
                  // consequence they chose rather than a logout that appears to come from nowhere.
                  const warning =
                    device.current === true
                      ? 'This is the device you are using. Revoking it signs you out here. Continue?'
                      : `Revoke ${device.model ?? platformName(device.platform)}? It will need to sign in again.`
                  if (window.confirm(warning)) revoke.mutate(device.id)
                }}
                disabled={revoke.isPending}
              >
                Revoke
              </Button>
            ),
          },
        ]}
      />
    </Card>
  )
}

/* -------------------------------------------------------------------------- */
/* Helpers                                                                     */
/* -------------------------------------------------------------------------- */

async function messageFor(cause: unknown): Promise<string> {
  const apiError = await extractApiError(cause)
  if (apiError === null) return 'Something went wrong. Please try again.'
  return humaniseError(apiError.code, apiError.requestId)
}

/** Groups a base32 secret into fours, because nobody transcribes 32 unbroken characters correctly. */
function group(secret: string): string {
  return secret.replace(/(.{4})/g, '$1 ').trim()
}

function platformName(platform: string): string {
  const names: Record<string, string> = {
    ANDROID: 'Android',
    IOS: 'iPhone',
    WEB: 'Browser',
    KIOSK: 'Kiosk',
  }
  return names[platform] ?? platform
}

/**
 * "3 days ago" rather than a timestamp.
 *
 * The question this column answers is "is this device still in use", and a relative age answers it
 * without the reader doing date arithmetic against today.
 */
function formatLastSeen(lastSeenAt: Date | undefined): string {
  if (lastSeenAt === undefined) return 'Never'

  const days = Math.floor((Date.now() - lastSeenAt.getTime()) / 86_400_000)
  if (days <= 0) return 'Today'
  if (days === 1) return 'Yesterday'
  if (days < 30) return `${days} days ago`
  return lastSeenAt.toLocaleDateString()
}
