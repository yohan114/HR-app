import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { QueryErrorState } from '@/components/QueryErrorState'
import { Badge, Button, Card, LoadingState, Switch } from '@/components/ui'
import { extractApiError, humaniseError, meApi } from '@/lib/api'
import type { ChannelPreference, NotificationSettings as NotificationSettingsType, QuietHoursSettings } from '@hr/client'

export interface NotifiableEventDef {
  key: string
  label: string
  description: string
  category: 'Leave' | 'Payroll' | 'Attendance' | 'Compliance' | 'Security'
}

export const NOTIFIABLE_EVENTS: NotifiableEventDef[] = [
  {
    key: 'leave.decided',
    label: 'Leave decisions',
    description: 'When your leave request is approved, rejected, or updated',
    category: 'Leave',
  },
  {
    key: 'leave.requested',
    label: 'Leave requests',
    description: 'When a direct report or team member submits a leave request',
    category: 'Leave',
  },
  {
    key: 'payroll.payslip.published',
    label: 'Payslips',
    description: 'When a new payslip is issued and available to download',
    category: 'Payroll',
  },
  {
    key: 'attendance.missing',
    label: 'Missing attendance',
    description: 'When a scheduled clock-in or clock-out was not registered',
    category: 'Attendance',
  },
  {
    key: 'document.expiring',
    label: 'Expiring documents',
    description: 'When an uploaded ID, visa, certificate, or contract is near its expiry date',
    category: 'Compliance',
  },
  {
    key: 'security.new_device',
    label: 'New sign-ins & devices',
    description: 'When your account signs in on a device or browser not seen before',
    category: 'Security',
  },
]

type ChannelType = 'PUSH' | 'EMAIL'
const CONFIGURABLE_CHANNELS: ChannelType[] = ['EMAIL', 'PUSH']

function defaultEnabled(_channel: ChannelType): boolean {
  return true
}

function expand(settings?: NotificationSettingsType): Record<string, boolean> {
  const stored = new Map<string, boolean>()
  settings?.preferences?.forEach((pref) => {
    stored.set(`${pref.eventKey}:${pref.channel}`, pref.enabled)
  })

  const matrix: Record<string, boolean> = {}
  for (const event of NOTIFIABLE_EVENTS) {
    for (const ch of CONFIGURABLE_CHANNELS) {
      const compositeKey = `${event.key}:${ch}`
      const explicit = stored.get(compositeKey)
      matrix[compositeKey] = explicit !== undefined ? explicit : defaultEnabled(ch)
    }
  }
  return matrix
}

function collapse(
  matrix: Record<string, boolean>,
  quietHours: QuietHoursSettings,
): NotificationSettingsType {
  const preferences: ChannelPreference[] = []

  for (const event of NOTIFIABLE_EVENTS) {
    for (const ch of CONFIGURABLE_CHANNELS) {
      const compositeKey = `${event.key}:${ch}`
      const enabled = matrix[compositeKey] ?? defaultEnabled(ch)
      if (enabled !== defaultEnabled(ch)) {
        preferences.push({
          eventKey: event.key,
          channel: ch,
          enabled,
        })
      }
    }
  }

  return {
    preferences,
    quietHours,
  }
}

export function NotificationSettings() {
  const query = useQuery({
    queryKey: ['notification-settings'],
    queryFn: () => meApi.getNotificationSettings(),
  })

  if (query.isPending) return <LoadingState label="Loading notification preferences…" />
  if (query.isError) {
    return <QueryErrorState error={query.error} onRetry={() => void query.refetch()} />
  }

  return <NotificationSettingsForm initialSettings={query.data} />
}

function NotificationSettingsForm({
  initialSettings,
}: {
  initialSettings: NotificationSettingsType
}) {
  const queryClient = useQueryClient()
  const detectedTz = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'

  const [matrix, setMatrix] = useState<Record<string, boolean>>(() => expand(initialSettings))
  const [quietHours, setQuietHours] = useState<QuietHoursSettings>(() => ({
    enabled: initialSettings.quietHours?.enabled ?? false,
    startAt: initialSettings.quietHours?.startAt ?? '22:00',
    endAt: initialSettings.quietHours?.endAt ?? '07:00',
    timezone: initialSettings.quietHours?.timezone || detectedTz,
  }))

  const [isSaved, setIsSaved] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  // Track whether there are unsaved edits
  const initialExpanded = expand(initialSettings)
  const isMatrixDirty = Object.keys(matrix).some((key) => matrix[key] !== initialExpanded[key])
  const isQuietHoursDirty =
    (quietHours.enabled ?? false) !== (initialSettings.quietHours?.enabled ?? false) ||
    quietHours.startAt !== (initialSettings.quietHours?.startAt ?? '22:00') ||
    quietHours.endAt !== (initialSettings.quietHours?.endAt ?? '07:00') ||
    quietHours.timezone !== (initialSettings.quietHours?.timezone || detectedTz)
  const isDirty = isMatrixDirty || isQuietHoursDirty

  const saveMutation = useMutation({
    mutationFn: (payload: NotificationSettingsType) =>
      meApi.replaceNotificationSettings({ notificationSettings: payload }),
    onSuccess: (updated) => {
      queryClient.setQueryData(['notification-settings'], updated)
      setMatrix(expand(updated))
      if (updated.quietHours) {
        setQuietHours({
          enabled: updated.quietHours.enabled ?? false,
          startAt: updated.quietHours.startAt,
          endAt: updated.quietHours.endAt,
          timezone: updated.quietHours.timezone,
        })
      }
      setIsSaved(true)
      setSaveError(null)
      setTimeout(() => setIsSaved(false), 4000)
    },
    onError: async (err) => {
      const parsed = await extractApiError(err)
      setSaveError(parsed ? humaniseError(parsed.code, parsed.requestId) : 'Failed to save settings.')
    },
  })

  function toggleChannel(eventKey: string, channel: ChannelType) {
    const key = `${eventKey}:${channel}`
    setMatrix((prev) => ({
      ...prev,
      [key]: !prev[key],
    }))
    setIsSaved(false)
  }

  function handleSave() {
    setSaveError(null)
    const payload = collapse(matrix, quietHours)
    saveMutation.mutate(payload)
  }

  function handleReset() {
    setMatrix(expand(initialSettings))
    setQuietHours({
      enabled: initialSettings.quietHours?.enabled ?? false,
      startAt: initialSettings.quietHours?.startAt ?? '22:00',
      endAt: initialSettings.quietHours?.endAt ?? '07:00',
      timezone: initialSettings.quietHours?.timezone || detectedTz,
    })
    setIsSaved(false)
    setSaveError(null)
  }

  return (
    <div className="stack" style={{ gap: '1.5rem', maxWidth: '900px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 style={{ margin: 0 }}>Notification Preferences</h1>
          <p className="field__hint" style={{ marginTop: '0.25rem' }}>
            Choose which alerts you receive and manage quiet hours for scheduled downtime.
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          {isDirty && <Badge tone="warning">Unsaved changes</Badge>}
          {isSaved && <Badge tone="success">Saved</Badge>}
          <Button variant="secondary" onClick={handleReset} disabled={!isDirty || saveMutation.isPending}>
            Revert
          </Button>
          <Button variant="primary" onClick={handleSave} disabled={!isDirty || saveMutation.isPending}>
            {saveMutation.isPending ? 'Saving…' : 'Save Changes'}
          </Button>
        </div>
      </div>

      {saveError && (
        <div
          role="alert"
          style={{
            padding: '0.75rem 1rem',
            backgroundColor: 'rgba(239, 68, 68, 0.1)',
            border: '1px solid var(--danger, #ef4444)',
            borderRadius: '6px',
            color: 'var(--danger, #ef4444)',
            fontSize: '0.875rem',
          }}
        >
          {saveError}
        </div>
      )}

      {/* Quiet Hours Card */}
      <Card title="Quiet Hours Schedule">
        <p className="field__hint" style={{ marginBottom: '1.25rem' }}>
          During quiet hours, push notifications and sound alerts are silenced. Urgent security notifications will still reach your inbox.
        </p>

        <div className="stack" style={{ gap: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div>
              <strong>Enable Quiet Hours</strong>
              <div className="field__hint">Mute non-critical alerts on a recurring daily window</div>
            </div>
            <Switch
              checked={quietHours.enabled ?? false}
              onChange={(enabled) => setQuietHours((prev) => ({ ...prev, enabled }))}
              label="Enable quiet hours"
            />
          </div>

          {quietHours.enabled && (
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
                gap: '1rem',
                paddingTop: '1rem',
                borderTop: '1px solid var(--border-subtle, #30363d)',
              }}
            >
              <div>
                <label className="field__label" htmlFor="quiet-hours-start">
                  Start Time
                </label>
                <input
                  id="quiet-hours-start"
                  type="time"
                  className="input"
                  value={quietHours.startAt.slice(0, 5)}
                  onChange={(e) => setQuietHours((prev) => ({ ...prev, startAt: e.target.value }))}
                />
              </div>

              <div>
                <label className="field__label" htmlFor="quiet-hours-end">
                  End Time
                </label>
                <input
                  id="quiet-hours-end"
                  type="time"
                  className="input"
                  value={quietHours.endAt.slice(0, 5)}
                  onChange={(e) => setQuietHours((prev) => ({ ...prev, endAt: e.target.value }))}
                />
              </div>

              <div>
                <label className="field__label" htmlFor="quiet-hours-tz">
                  Timezone
                </label>
                <input
                  id="quiet-hours-tz"
                  type="text"
                  className="input"
                  value={quietHours.timezone}
                  onChange={(e) => setQuietHours((prev) => ({ ...prev, timezone: e.target.value }))}
                />
                <span className="field__hint">IANA time zone name</span>
              </div>
            </div>
          )}
        </div>
      </Card>

      {/* Channel Delivery Matrix Card */}
      <Card title="Channel Delivery Preferences">
        <p className="field__hint" style={{ marginBottom: '1.25rem' }}>
          Toggle delivery channels per notification event. Settings are synchronised across the web console and mobile application.
        </p>

        <div style={{ overflowX: 'auto' }}>
          <table className="matrix-table">
            <thead>
              <tr>
                <th style={{ width: '55%' }}>Notification Event</th>
                <th style={{ textAlign: 'center', width: '22%' }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.4rem' }}>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
                      <polyline points="22,6 12,13 2,6" />
                    </svg>
                    <span>Email</span>
                  </div>
                </th>
                <th style={{ textAlign: 'center', width: '23%' }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.4rem' }}>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <rect x="5" y="2" width="14" height="20" rx="2" ry="2" />
                      <line x1="12" y1="18" x2="12.01" y2="18" />
                    </svg>
                    <span>Push Notification</span>
                  </div>
                </th>
              </tr>
            </thead>
            <tbody>
              {NOTIFIABLE_EVENTS.map((event) => {
                const emailEnabled = matrix[`${event.key}:EMAIL`] ?? true
                const pushEnabled = matrix[`${event.key}:PUSH`] ?? true

                return (
                  <tr key={event.key}>
                    <td>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                          <span style={{ fontWeight: 600 }}>{event.label}</span>
                          <span
                            style={{
                              fontSize: '0.7rem',
                              padding: '0.1rem 0.4rem',
                              borderRadius: '3px',
                              backgroundColor: 'var(--bg-subtle, #161b22)',
                              color: 'var(--text-muted, #8b949e)',
                              border: '1px solid var(--border-subtle, #30363d)',
                            }}
                          >
                            {event.category}
                          </span>
                        </div>
                        <span className="field__hint" style={{ fontSize: '0.8125rem' }}>
                          {event.description}
                        </span>
                      </div>
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      <div style={{ display: 'inline-flex', justifyContent: 'center' }}>
                        <Switch
                          checked={emailEnabled}
                          onChange={() => toggleChannel(event.key, 'EMAIL')}
                          label={`Toggle Email for ${event.label}`}
                        />
                      </div>
                    </td>
                    <td style={{ textAlign: 'center' }}>
                      <div style={{ display: 'inline-flex', justifyContent: 'center' }}>
                        <Switch
                          checked={pushEnabled}
                          onChange={() => toggleChannel(event.key, 'PUSH')}
                          label={`Toggle Push for ${event.label}`}
                        />
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  )
}
