import { useState, type FormEvent } from 'react'
import { Navigate } from 'react-router-dom'
import { Button, Card, Field } from '@/components/ui'
import { extractApiError, useAuth } from '@/lib/auth'
import { humaniseError } from '@/lib/api'

export function SignIn() {
  const { status, signIn } = useAuth()
  const [orgCode, setOrgCode] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (status === 'authenticated') {
    return <Navigate to="/" replace />
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await signIn(orgCode.trim().toLowerCase(), username.trim(), password)
    } catch (caught) {
      const apiError = await extractApiError(caught)
      // Both an unknown organisation and bad credentials land here with distinct codes, but the
      // *messages* for INVALID_CREDENTIALS are identical by design — see AuthenticationService.
      setError(
        apiError !== null
          ? humaniseError(apiError.code, apiError.requestId)
          : 'Could not reach the server. Check your connection and try again.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="signin">
      <div className="signin__panel">
        <Card title="Sign in to HR Admin">
          <form onSubmit={handleSubmit} noValidate>
            <Field
              label="Organisation code"
              value={orgCode}
              onChange={(e) => setOrgCode(e.target.value)}
              autoComplete="organization"
              hint="The short code your organisation was set up with, e.g. acme."
              required
            />
            <Field
              label="Username or email"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              required
            />
            <Field
              label="Password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />

            {error !== null ? (
              <p className="signin__error" role="alert">
                {error}
              </p>
            ) : null}

            <Button type="submit" variant="primary" loading={submitting}>
              Sign in
            </Button>
          </form>
        </Card>

        <p className="signin__footnote">
          Admin console. For everyday HR tasks, use the mobile app.
        </p>
      </div>
    </main>
  )
}
