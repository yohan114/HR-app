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
          // Custom fields land in a free-form JSONB column and the server validates them from
          // their declared type, so a string is what it expects.
          customEdits[key] = raw
        } else {
          builtIn[key] = toWireValue(field, raw)
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
  //
  // A type this form cannot yet edit is shown the same way, for a different reason — see
  // [EDITABLE_AS_TEXT].
  if (field.editable === false || !EDITABLE_AS_TEXT.has(field.type)) {
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
        <dd>
          {display(value)}
          {/*
            Only when the *server* said it was editable and this form cannot oblige. Saying so is
            better than a silently read-only field: an HR officer who has been told they can change
            someone's department needs to know why they cannot do it here, rather than concluding
            their permissions are wrong.
          */}
          {field.editable !== false && (
            <span className="field__hint"> Needs a picker — not editable here yet.</span>
          )}
        </dd>
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
 * Field types this form can edit with a plain input.
 *
 * Everything else is rendered read-only **even when the server says it is editable**, and that is
 * a deliberate choice rather than an oversight.
 *
 * The form sends every value as a string. For these types the server accepts one:
 * `EmployeeWriter`'s coercions parse a UUID, a date and a number from text, and
 * `CustomFieldValidator` accepts a numeric string for `NUMBER`. For the rest it does not, and the
 * save is guaranteed to fail:
 *
 * - `REFERENCE` and `EMPLOYEE` store a uuid. The input would show the raw id, and any edit that
 *   was not itself a valid uuid comes back `INVALID_REFERENCE`. Showing someone
 *   `a3f1…` in a box labelled "Department" and inviting them to type into it is worse than
 *   showing nothing.
 * - `DROPDOWN` and `RADIO` are checked against their option list — free text is `INVALID_OPTION`.
 * - `MULTI_SELECT` expects a list and `CHECKBOX` expects a boolean; a string is `WRONG_TYPE`.
 * - `ATTACHMENT` needs an upload.
 *
 * So an editable-looking control for any of these would offer the user a save that cannot succeed.
 * They become editable when the reference and employee pickers land, not before.
 */
const EDITABLE_AS_TEXT = new Set<FormField['type']>([
  'TEXT',
  'MULTILINE_TEXT',
  'NUMBER',
  'DATE',
  'EMAIL',
  'PHONE',
])

/**
 * Converts an input's string value into what the generated client expects.
 *
 * Not cosmetic. `EmployeeUpdate.dateOfBirth` is typed `Date`, and the generated serialiser calls
 * `.toISOString()` on whatever it is given — so handing it the raw `"1990-05-02"` from a date input
 * throws `toISOString is not a function` before the request is ever sent. Every date field on the
 * profile was unsaveable.
 *
 * It was invisible to the compiler because the payload is assembled as `Record<string, unknown>`
 * and spread into the typed model, which erases exactly the mismatch TypeScript would have caught.
 * Keeping the conversion here, keyed off the schema's own field type, is what makes the assembly
 * safe despite that.
 */
function toWireValue(field: FormField | undefined, raw: string): unknown {
  switch (field?.type) {
    case 'DATE':
      return new Date(raw)
    case 'NUMBER': {
      const parsed = Number(raw)
      // A non-numeric string reaching a number field is the server's to reject, with a message
      // naming the field. Sending NaN instead would serialise as null and read as "clear it".
      return Number.isNaN(parsed) ? raw : parsed
    }
    default:
      return raw
  }
}

/** Maps a schema type onto an input type. Only called for types in [EDITABLE_AS_TEXT]. */
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

/**
 * Renders a JSON value as text for an input.
 *
 * Narrowed by hand rather than falling through to `String(value)`, which would produce
 * `[object Object]` for anything the earlier branches missed — a placeholder that looks like data
 * and would be sent straight back to the server on the next save.
 */
function stringify(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  // The API sends dates as ISO strings, but the generated client parses some into `Date`.
  if (value instanceof Date) return value.toISOString().slice(0, 10)
  // An address or a structured custom field. Shown as JSON because these are read-only here —
  // `EDITABLE_AS_TEXT` excludes every type that would arrive as an object.
  return JSON.stringify(value) ?? ''
}
