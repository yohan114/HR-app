import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Badge, Button, Card, LoadingState } from '@/components/ui'
import { QueryErrorState } from '@/components/QueryErrorState'
import { employeesApi, extractApiError, extractFieldViolations, humaniseError } from '@/lib/api'
import type { EmployeeProfile as Profile, FormField, FormSchema } from '@hr/client'

/**
 * One employee, rendered entirely from the server's form schema.
 *
 * ## Why there is no hardcoded field list here
 *
 * The schema decides which sections exist, which fields are in them, their order, their labels and
 * whether each is editable. This page walks it. That is what lets a tenant add a custom field in
 * the admin console and have it appear here — and on Android and iOS — with no code change and no
 * release.
 *
 * ## Why absent fields are simply not rendered
 *
 * The server omits fields the caller may not see, rather than sending them as null
 * (see ADR 0006). So "not in the schema" and "not in the payload" both mean *do not draw anything*,
 * and this page never has to ask whether the user is allowed to see something — it cannot render
 * what it did not receive. There is deliberately no client-side permission check anywhere in this
 * file; adding one would be duplicating a decision the server has already made and would drift.
 */
export function EmployeeProfile() {
  const { id = '' } = useParams<{ id: string }>()
  const queryClient = useQueryClient()

  const profile = useQuery({
    queryKey: ['employee', id],
    queryFn: () => employeesApi.getEmployeeProfile({ id }),
  })

  const form = useQuery({
    queryKey: ['employee-form', id],
    queryFn: () => employeesApi.getEmployeeEditForm({ id }),
  })

  if (profile.error !== null) return <QueryErrorState error={profile.error} />
  if (form.error !== null) return <QueryErrorState error={form.error} />
  if (profile.isPending || form.isPending) return <LoadingState label="Loading profile…" />

  return (
    <ProfileForm
      profile={profile.data}
      schema={form.data}
      onSaved={() => {
        void queryClient.invalidateQueries({ queryKey: ['employee', id] })
        // The schema is permission-dependent and cheap, and a save can change what is editable
        // (a supervisor change moves someone in or out of your reporting line).
        void queryClient.invalidateQueries({ queryKey: ['employee-form', id] })
        void queryClient.invalidateQueries({ queryKey: ['directory'] })
      }}
    />
  )
}

function ProfileForm({
  profile,
  schema,
  onSaved,
}: {
  profile: Profile
  schema: FormSchema
  onSaved: () => void
}) {
  const values = profile as unknown as Record<string, unknown>
  const custom = (profile.customFields ?? {}) as Record<string, unknown>

  const [edits, setEdits] = useState<Record<string, string>>({})
  const [violations, setViolations] = useState<Record<string, string>>({})
  const [banner, setBanner] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: async () => {
      const builtIn: Record<string, unknown> = {}
      const customEdits: Record<string, unknown> = {}
      const clearFields: string[] = []

      for (const [key, raw] of Object.entries(edits)) {
        const field = findField(schema, key)
        // An empty input means "remove this value". It is sent as `clearFields` rather than as
        // null because a null cannot survive code generation: the typed clients must omit nulls,
        // or a one-field save would carry one for every field the user did not touch.
        if (raw === '') {
          clearFields.push(key)
        } else if (field?.custom === true) {
          customEdits[key] = raw
        } else {
          builtIn[key] = raw
        }
      }

      return employeesApi.updateEmployeeProfile({
        id: profile.id,
        // The version we loaded. The server rejects the save if someone else has changed the
        // record since — two HR officers editing the same profile is common, and last-write-wins
        // loses one of them silently.
        ifMatch: `"${profile.version}"`,
        employeeUpdate: {
          ...builtIn,
          ...(clearFields.length > 0 ? { clearFields } : {}),
          ...(Object.keys(customEdits).length > 0 ? { customFields: customEdits } : {}),
        },
      })
    },
    onSuccess: () => {
      setEdits({})
      setViolations({})
      setBanner(null)
      onSaved()
    },
    onError: async (error) => {
      // Field-level violations mark the inputs; anything else becomes a banner. The server
      // reports every violation at once, so the form marks them all rather than revealing them
      // one save at a time.
      const fieldErrors = await extractFieldViolations(error)
      setViolations(fieldErrors)

      const api = await extractApiError(error)
      setBanner(
        Object.keys(fieldErrors).length > 0
          ? null
          : api !== null
            ? humaniseError(api.code, api.requestId)
            : 'Something went wrong saving those changes.',
      )
    },
  })

  const dirty = Object.keys(edits).length > 0

  return (
    <div className="page">
      <div className="page__header">
        <h1 className="page__title">{(values.displayName as string | undefined) ?? 'Employee'}</h1>
        <Link to="/directory" className="link">
          Back to directory
        </Link>
      </div>

      {banner !== null && (
        <div className="banner banner--danger" role="alert">
          {banner}
        </div>
      )}

      <form
        onSubmit={(event) => {
          event.preventDefault()
          save.mutate()
        }}
      >
        {schema.sections.map((section) => (
          <Card key={section.key} title={section.label}>
            <dl className="detail-list">
              {section.fields.map((field) => (
                <FieldRow
                  key={field.key}
                  field={field}
                  value={field.custom === true ? custom[field.key] : values[field.key]}
                  edited={edits[field.key]}
                  violation={violations[field.key]}
                  onChange={(next) => {
                    setEdits((current) => ({ ...current, [field.key]: next }))
                    setViolations(({ [field.key]: _removed, ...rest }) => rest)
                  }}
                />
              ))}
            </dl>
          </Card>
        ))}

        {/*
          The save bar only appears once something has changed. A permanently visible disabled
          button invites clicking and explains nothing.
        */}
        {dirty && (
          <div className="page__actions">
            <Button type="submit" disabled={save.isPending}>
              {save.isPending ? 'Saving…' : 'Save changes'}
            </Button>
            <Button
              type="button"
              variant="secondary"
              disabled={save.isPending}
              onClick={() => {
                setEdits({})
                setViolations({})
                setBanner(null)
              }}
            >
              Discard
            </Button>
          </div>
        )}
      </form>
    </div>
  )
}

function FieldRow({
  field,
  value,
  edited,
  violation,
  onChange,
}: {
  field: FormField
  value: unknown
  edited: string | undefined
  violation: string | undefined
  onChange: (next: string) => void
}) {
  const inputId = `field-${field.key}`

  // `editable: false` means the caller may see it but not change it — a read-only salary grade
  // still belongs on the profile, it simply is not theirs to edit. Distinct from the field being
  // absent, which is why this renders text rather than a disabled input: a disabled input looks
  // like something you could gain permission to use, and reads as an unfilled form.
  if (field.editable === false) {
    return (
      <div>
        <dt>
          {field.label}
          {field.custom === true && (
            <>
              {' '}
              <Badge tone="neutral">Custom</Badge>
            </>
          )}
        </dt>
        <dd>{display(value)}</dd>
      </div>
    )
  }

  return (
    <div>
      <dt>
        <label htmlFor={inputId}>
          {field.label}
          {field.required === true && (
            <span aria-hidden="true" className="field__required">
              *
            </span>
          )}
        </label>
      </dt>
      <dd>
        <input
          id={inputId}
          className={violation !== undefined ? 'field__input field__input--error' : 'field__input'}
          type={inputType(field)}
          value={edited ?? stringify(value)}
          required={field.required === true}
          aria-describedby={
            violation !== undefined
              ? `${inputId}-error`
              : field.helpText !== undefined
                ? `${inputId}-hint`
                : undefined
          }
          aria-invalid={violation !== undefined}
          onChange={(event) => onChange(event.target.value)}
        />
        {violation !== undefined ? (
          <p id={`${inputId}-error`} className="field__error" role="alert">
            {violation}
          </p>
        ) : (
          field.helpText !== undefined && (
            <p id={`${inputId}-hint`} className="field__hint">
              {field.helpText}
            </p>
          )
        )}
      </dd>
    </div>
  )
}

/**
 * Maps a schema type onto an input type.
 *
 * Deliberately conservative: anything not obviously a date, number, email or phone falls back to
 * text. `REFERENCE`, `EMPLOYEE`, `DROPDOWN` and the rest need pickers backed by their own queries,
 * and rendering them as free text would let someone type a department name into a field that
 * stores a uuid. Those land with the reference-data pickers — until then they arrive as
 * `editable: false` from the server for most callers anyway.
 */
function inputType(field: FormField): string {
  switch (field.type) {
    case 'DATE':
      return 'date'
    case 'NUMBER':
      return 'number'
    case 'EMAIL':
      return 'email'
    case 'PHONE':
      return 'tel'
    default:
      return 'text'
  }
}

function findField(schema: FormSchema, key: string): FormField | undefined {
  for (const section of schema.sections) {
    const found = section.fields.find((field) => field.key === key)
    if (found !== undefined) return found
  }
  return undefined
}

/** An absent value shows an em dash, not an empty cell — so the row still reads as a row. */
function display(value: unknown): string {
  const text = stringify(value)
  return text === '' ? '—' : text
}

function stringify(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (value instanceof Date) return value.toISOString().slice(0, 10)
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
