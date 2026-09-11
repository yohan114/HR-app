/**
 * Who may see which record, and which of its fields.
 *
 * A faithful port of three server files — `EmployeeService.assertVisible`, `FieldPermissionResolver`
 * and `FormSchemaService` — rather than an approximation of them. That matters more here than
 * anywhere else in the demo: the console has no client-side permission logic at all, by design. It
 * renders what it is given and hides what it is not. So the *only* thing that decides whether the
 * profile screen shows a date of birth, offers an editable input, or refuses a save is this file.
 * A demo that is permissive where the server is strict would make the console look like it works
 * and hide the exact class of bug these rules exist to catch.
 *
 * Ported deliberately, and it is worth naming the cost: two implementations of one rule will drift.
 * The mitigation is that this one is only ever reachable from a dev server — nothing here can be
 * mistaken for the enforcement point, because the enforcement point cannot be a browser.
 */

import type { FormField, FormSchema, FormSection } from '@hr/client'
import {
  CUSTOM_FIELDS,
  CUSTOM_FIELD_KEYS,
  type DemoAccount,
  type DemoEmployee,
  type ProfileFields,
} from './company'

export type FieldAccess = 'HIDDEN' | 'MASKED' | 'READ' | 'WRITE'

/** The caller, reduced to the four things any access decision here depends on. */
export interface Caller {
  account: DemoAccount
  employeeId: string
  permissions: ReadonlySet<string>
}

/**
 * Fields hidden from everyone except their owner unless explicitly granted.
 *
 * `FieldPermissionResolver.ALWAYS_SENSITIVE`, restricted to the keys these fixtures carry. The
 * list lives in code on the server so a tenant cannot make identity numbers world-readable by
 * clearing a configuration row; it lives in code here so the demo cannot accidentally be kinder
 * than the server about pay and identity documents.
 */
const ALWAYS_SENSITIVE: ReadonlySet<string> = new Set([
  'dateOfBirth',
  'personalEmail',
  'permanentAddress',
  'currentAddress',
  'maritalStatusId',
  'bloodGroupId',
  'salaryGradeId',
  'resignDate',
  'lastWorkingDate',
])

/**
 * Fields a person may change on their own record without any grant.
 *
 * `FieldPermissionResolver.SELF_WRITABLE`. Short on purpose: everything in it is something the
 * employee is the authoritative source for and that carries no statutory consequence. Name, date
 * of birth and join date are notably absent — those appear on statutory filings, so changing one
 * is a request with evidence attached rather than a text box.
 */
const SELF_WRITABLE: ReadonlySet<string> = new Set([
  'preferredName',
  'personalEmail',
  'mobile',
  'currentAddress',
  'photoKey',
  'bloodGroupId',
])

/**
 * Every readable profile field, in the order a profile screen reads top to bottom.
 *
 * `EmployeeProjection.FIELDS`. The order is load-bearing: the projection builds an ordered object
 * and clients that render fields in payload order get a sensible layout for nothing.
 */
export const PROFILE_FIELDS: ReadonlyArray<keyof ProfileFields> = [
  'employeeCode',
  'status',
  'displayName',
  'firstName',
  'middleName',
  'lastName',
  'preferredName',
  'photoKey',
  'dateOfBirth',
  'genderTypeId',
  'maritalStatusId',
  'bloodGroupId',
  'nationalityId',
  'workEmail',
  'personalEmail',
  'mobile',
  'workPhone',
  'permanentAddress',
  'currentAddress',
  'companyId',
  'joinDate',
  'confirmationDate',
  'probationEndDate',
  'resignDate',
  'lastWorkingDate',
  'employmentTypeId',
  'employeeCategoryId',
  'departmentId',
  'designationId',
  'salaryGradeId',
  'locationId',
  'supervisorId',
  'yearsOfService',
]

/**
 * Access for one field, with defaults applied.
 *
 * The branch order is copied exactly, including the one that looks redundant: self-writable is
 * checked *before* sensitivity, because a personal email address is sensitive to a colleague and
 * routine to its owner — and someone who cannot update their own phone number telephones HR to do
 * it for them.
 */
export function accessFor(caller: Caller, subject: DemoEmployee, fieldKey: string): FieldAccess {
  // An explicit grant is a deliberate decision by whoever configured the role, so it wins outright
  // — including over the sensitive-field default, which exists precisely to force that decision.
  const granted = caller.account.fieldGrants?.[fieldKey]
  if (granted !== undefined) return granted

  const isSelf = caller.employeeId === subject.id
  const sensitive = ALWAYS_SENSITIVE.has(fieldKey)
  const canManage = caller.permissions.has('employee.manage')

  if (isSelf && SELF_WRITABLE.has(fieldKey)) return 'WRITE'
  if (isSelf && sensitive) return 'READ'
  if (sensitive) return 'HIDDEN'
  if (canManage) return 'WRITE'
  return 'READ'
}

function canRead(access: FieldAccess): boolean {
  return access !== 'HIDDEN'
}

function canWrite(access: FieldAccess): boolean {
  return access === 'WRITE'
}

export function readableFields(caller: Caller, subject: DemoEmployee): Set<string> {
  return new Set(
    [...PROFILE_FIELDS, ...CUSTOM_FIELD_KEYS].filter((key) =>
      canRead(accessFor(caller, subject, key)),
    ),
  )
}

export function writableFields(caller: Caller, subject: DemoEmployee): Set<string> {
  return new Set(
    [...PROFILE_FIELDS, ...CUSTOM_FIELD_KEYS].filter((key) =>
      canWrite(accessFor(caller, subject, key)),
    ),
  )
}

/**
 * Whether this caller may open this record at all.
 *
 * `EmployeeService.assertVisible`. The caller-side consequence is that a refusal is a 404 and never
 * a 403: a 403 confirms the record exists, which turns the endpoint into an oracle for enumerating
 * employee ids.
 *
 * "My team" means the whole subtree rather than direct reports only — a department head who cannot
 * open the record of someone two levels down has to ask a middle manager to do it for them.
 */
export function isVisible(
  caller: Caller,
  subject: DemoEmployee,
  employees: ReadonlyMap<string, DemoEmployee>,
): boolean {
  if (caller.employeeId === subject.id) return true
  if (caller.permissions.has('employee.manage') || caller.permissions.has('employee.view.all')) {
    return true
  }
  if (!caller.permissions.has('employee.view')) return false
  return isManagerOf(caller.employeeId, subject, employees)
}

/** Walks up the reporting line. Depth-capped so a cycle in edited data cannot hang the server. */
function isManagerOf(
  managerId: string,
  subject: DemoEmployee,
  employees: ReadonlyMap<string, DemoEmployee>,
): boolean {
  let current = subject
  for (let depth = 0; depth < 16; depth++) {
    const supervisorId = current.supervisorId
    if (supervisorId === undefined) return false
    if (supervisorId === managerId) return true
    const next = employees.get(supervisorId)
    if (next === undefined) return false
    current = next
  }
  return false
}

/**
 * The employee as this caller is allowed to see it.
 *
 * `HIDDEN` fields are **absent** from the object, not null. A null would tell the caller the field
 * exists and they are not allowed it, and would have the console rendering a disabled input for a
 * value it should not know about. `EmployeeProfile.tsx` relies on this: it has no permission check
 * anywhere in it, because it cannot render what it did not receive.
 */
export function project(caller: Caller, subject: DemoEmployee): Record<string, unknown> {
  // Identity of the record is never permission-controlled: without it the payload cannot be
  // addressed, cached or updated, and you already had to be allowed to fetch this employee.
  const payload: Record<string, unknown> = { id: subject.id, version: subject.version }

  for (const key of PROFILE_FIELDS) {
    const access = accessFor(caller, subject, key)
    const value = subject[key]
    if (access === 'HIDDEN') continue
    if (access === 'MASKED') {
      // Only a string can carry a mask. Masking a date or a uuid would put `••••` where the client
      // expects a parseable value, and the mobile clients abort the whole response on that — one
      // masked date would blank an entire profile. Where the mask cannot fit the type, the field
      // is hidden instead: strictly more restrictive, and a fully-masked date conveyed nothing.
      if (typeof value === 'string') payload[key] = '••••'
      continue
    }
    if (value !== undefined) payload[key] = value
  }

  const custom: Record<string, unknown> = {}
  for (const key of CUSTOM_FIELD_KEYS) {
    const value = subject.customFields[key]
    if (value === undefined) continue
    const access = accessFor(caller, subject, key)
    if (access === 'HIDDEN') continue
    custom[key] = access === 'MASKED' ? '••••' : value
  }
  if (Object.keys(custom).length > 0) payload.customFields = custom

  return payload
}

/* -------------------------------------------------------------------------- */
/* The form schema                                                             */
/* -------------------------------------------------------------------------- */

interface BuiltInSection {
  key: string
  label: string
  fields: readonly FormField[]
}

/**
 * The built-in employee form, from `FormSchemaService.BUILT_IN_FORMS`.
 *
 * Declared rather than derived from the employee shape, for the same reason the server declares it:
 * the order, grouping and labels are product decisions, and reflecting over the fields would
 * silently expose a new column the moment someone added one.
 *
 * `REFERENCE` fields name their taxonomy instead of inlining options — the clients cache those,
 * and a nationality list runs to several hundred entries.
 */
const BUILT_IN_SECTIONS: readonly BuiltInSection[] = [
  {
    key: 'personal',
    label: 'Personal',
    fields: [
      { key: 'firstName', label: 'First name', type: 'TEXT', required: true, validation: { maxLength: 128 } },
      { key: 'middleName', label: 'Middle name', type: 'TEXT', validation: { maxLength: 128 } },
      { key: 'lastName', label: 'Last name', type: 'TEXT', required: true, validation: { maxLength: 128 } },
      {
        key: 'displayName',
        label: 'Display name',
        type: 'TEXT',
        required: true,
        helpText: "How this person's name is shown throughout the app.",
      },
      { key: 'preferredName', label: 'Preferred name', type: 'TEXT' },
      { key: 'dateOfBirth', label: 'Date of birth', type: 'DATE' },
      { key: 'genderTypeId', label: 'Gender', type: 'REFERENCE', referenceTable: 'gender-type' },
      { key: 'maritalStatusId', label: 'Marital status', type: 'REFERENCE', referenceTable: 'marital-status' },
      { key: 'nationalityId', label: 'Nationality', type: 'REFERENCE', referenceTable: 'nationality' },
      { key: 'bloodGroupId', label: 'Blood group', type: 'REFERENCE', referenceTable: 'blood-group' },
    ],
  },
  {
    key: 'contact',
    label: 'Contact',
    fields: [
      { key: 'workEmail', label: 'Work email', type: 'EMAIL' },
      { key: 'personalEmail', label: 'Personal email', type: 'EMAIL' },
      { key: 'mobile', label: 'Mobile', type: 'PHONE' },
      { key: 'workPhone', label: 'Work phone', type: 'PHONE' },
    ],
  },
  {
    key: 'employment',
    label: 'Employment',
    fields: [
      { key: 'employeeCode', label: 'Employee code', type: 'TEXT', required: true },
      { key: 'joinDate', label: 'Join date', type: 'DATE', required: true },
      { key: 'confirmationDate', label: 'Confirmation date', type: 'DATE' },
      { key: 'employmentTypeId', label: 'Employment type', type: 'REFERENCE', referenceTable: 'employment-type' },
      { key: 'employeeCategoryId', label: 'Category', type: 'REFERENCE', referenceTable: 'employee-category' },
    ],
  },
  {
    key: 'workstation',
    label: 'Workstation',
    fields: [
      { key: 'departmentId', label: 'Department', type: 'REFERENCE', referenceTable: 'department' },
      { key: 'designationId', label: 'Designation', type: 'REFERENCE', referenceTable: 'designation' },
      { key: 'locationId', label: 'Location', type: 'REFERENCE', referenceTable: 'location' },
      {
        key: 'supervisorId',
        label: 'Reports to',
        type: 'EMPLOYEE',
        helpText: 'The solid reporting line. Approvals route to this person.',
      },
    ],
  },
]

/**
 * A stable hash of the custom definitions, as `FormSchemaService.schemaVersion` computes it.
 *
 * Deliberately not a timestamp: two servers must produce the same version for the same
 * configuration, or a client behind a load balancer would watch the schema "change" on every other
 * request. Java's `String.hashCode` is reproduced exactly so the two implementations agree.
 */
const SCHEMA_VERSION = (() => {
  const fingerprint = [...CUSTOM_FIELDS]
    .sort((a, b) => a.field.key.localeCompare(b.field.key))
    .map((entry, index) => `${entry.field.key}:${entry.field.type}:${index}:1`)
    .join('|')
  let hash = 0
  for (let i = 0; i < fingerprint.length; i++) {
    hash = (Math.imul(31, hash) + fingerprint.charCodeAt(i)) | 0
  }
  return (hash >>> 0).toString(16)
})()

/**
 * The form for one caller and one record.
 *
 * Fields the caller may not see are absent; fields they may see but not change arrive with
 * `editable: false`. Built from the same access decisions as the payload and the update path, so
 * what the console is offered and what this server will accept cannot drift apart — deriving the
 * form separately is how you end up with an input that saves nothing.
 */
export function editFormFor(caller: Caller, subject: DemoEmployee): FormSchema {
  const visible = readableFields(caller, subject)
  const editable = writableFields(caller, subject)

  const sectionKeys = [
    ...new Set([...BUILT_IN_SECTIONS.map((s) => s.key), ...CUSTOM_FIELDS.map((c) => c.section)]),
  ]

  const sections: FormSection[] = []
  for (const sectionKey of sectionKeys) {
    const builtIn = BUILT_IN_SECTIONS.find((section) => section.key === sectionKey)
    const custom = CUSTOM_FIELDS.filter((entry) => entry.section === sectionKey).map((e) => e.field)

    const fields = [...(builtIn?.fields ?? []), ...custom]
      .filter((field) => visible.has(field.key))
      .map((field) => (editable.has(field.key) ? field : { ...field, editable: false }))

    // A section whose fields are all hidden is not rendered as an empty heading.
    if (fields.length === 0) continue
    sections.push({
      key: sectionKey,
      label: builtIn?.label ?? sectionKey.charAt(0).toUpperCase() + sectionKey.slice(1),
      fields,
    })
  }

  return { entityType: 'employee', version: SCHEMA_VERSION, sections }
}

/** Looks a field up across every section, built-in or custom. Used to validate a save. */
export function findFormField(key: string): FormField | undefined {
  for (const section of BUILT_IN_SECTIONS) {
    const found = section.fields.find((field) => field.key === key)
    if (found !== undefined) return found
  }
  return CUSTOM_FIELDS.find((entry) => entry.field.key === key)?.field
}
