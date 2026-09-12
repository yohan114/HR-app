/**
 * The fictional company the demo dev server serves.
 *
 * Deliberately the *same* company as `LocalDemoSeeder.kt` rather than a second invented one: the
 * first nine employees, their codes, names, departments, designations and reporting lines are
 * copied from it verbatim, as are the three sign-in accounts and the `demo` organisation code.
 * Someone who has run the backend locally should recognise this data, and a screenshot taken
 * against one should be indistinguishable from a screenshot taken against the other. Two
 * different demo companies is how "it worked in the demo" stops meaning anything.
 *
 * What is added on top is size. The seeder stops at nine because nine is enough to exercise the
 * *server's* reporting-line rules. The console paginates the directory at 25 rows a page, so nine
 * people leave the Previous/Next buttons permanently disabled and the cursor code untested. There
 * are 32 here for that reason, and one of them has left, so the "exited employees are excluded"
 * rule is visible rather than merely written down.
 *
 * Nothing in this file is imported by the React application. It is reachable only from
 * `vite.config.ts`, which is Node configuration and never part of a bundle — see `plugin.ts`.
 */

import type { EmployeeProfile, FormField, MeResponse, TenantSummary } from '@hr/client'

/* -------------------------------------------------------------------------- */
/* Wire types                                                                  */
/* -------------------------------------------------------------------------- */

/**
 * The wire form of a generated model.
 *
 * The generated models describe the **parsed** shape, not the transmitted one: `Device.lastSeenAt`
 * is typed `Date` because `DeviceFromJSON` calls `new Date()` on it, and `EmployeeProfile.joinDate`
 * likewise. What actually travels is the ISO string those constructors parse. Typing a fixture as
 * `Device` would therefore be wrong in exactly the place a mistake costs most — a `Date` object
 * survives `JSON.stringify` as a full ISO timestamp, which the client would happily re-parse, and
 * the error would only show up as a date rendering an hour out in a different timezone.
 *
 * Mapping `Date` back to `string` and leaving everything else alone is what lets `tsc` check these
 * fixtures against the generated contract at all. A field the client expects and does not get is a
 * blank screen; a field named slightly wrong is the same thing with more confidence behind it.
 */
export type Wire<T> = {
  [K in keyof T]: Date extends T[K] ? Exclude<T[K], Date> | string : T[K]
}

/**
 * Every profile field the fixtures carry, named and typed by the generated model.
 *
 * A `Pick` rather than a hand-written interface so that renaming a field in the OpenAPI spec
 * breaks this file at compile time instead of silently producing a payload the console cannot
 * read. The fields left out — `titleId`, `religionId`, `costCentreId` and the rest — are simply
 * absent from the payload, which is legal: every one of them is optional, and the console renders
 * only what the form schema names.
 */
export type ProfileFields = Pick<
  Wire<EmployeeProfile>,
  | 'employeeCode'
  | 'status'
  | 'displayName'
  | 'firstName'
  | 'middleName'
  | 'lastName'
  | 'preferredName'
  | 'photoKey'
  | 'dateOfBirth'
  | 'genderTypeId'
  | 'maritalStatusId'
  | 'bloodGroupId'
  | 'nationalityId'
  | 'workEmail'
  | 'personalEmail'
  | 'mobile'
  | 'workPhone'
  | 'permanentAddress'
  | 'currentAddress'
  | 'companyId'
  | 'joinDate'
  | 'confirmationDate'
  | 'probationEndDate'
  | 'resignDate'
  | 'lastWorkingDate'
  | 'employmentTypeId'
  | 'employeeCategoryId'
  | 'departmentId'
  | 'designationId'
  | 'salaryGradeId'
  | 'locationId'
  | 'supervisorId'
  | 'yearsOfService'
>

/** One employee as the fixtures hold them, before any field permission has been applied. */
export interface DemoEmployee extends ProfileFields {
  id: string
  version: number
  customFields: Record<string, unknown>
}

/* -------------------------------------------------------------------------- */
/* Identifiers                                                                 */
/* -------------------------------------------------------------------------- */

const ID_GROUP = {
  tenant: 1,
  company: 2,
  location: 3,
  department: 4,
  designation: 5,
  employee: 6,
  user: 7,
  device: 8,
} as const

/**
 * A stable, UUID-shaped identifier.
 *
 * Derived rather than random, because the console puts the employee id in the URL. A profile that
 * someone bookmarked, or a tab left open across a dev-server restart, has to keep resolving to the
 * same person — with `crypto.randomUUID()` every restart would turn every open profile into a 404,
 * and the first guess would be that the demo server is broken rather than that the ids moved.
 *
 * The `de30…` prefix is not meaningful to anything; it is just recognisable in a log line.
 */
function demoId(group: number, index: number): string {
  const g = group.toString(16).padStart(4, '0')
  const i = index.toString(16).padStart(12, '0')
  return `de300000-${g}-4000-8000-${i}`
}

/* -------------------------------------------------------------------------- */
/* Dates                                                                       */
/* -------------------------------------------------------------------------- */

/**
 * Today, at UTC midnight.
 *
 * Every fixture date is derived from this for the same reason `LocalDemoSeeder` computes birthdays
 * from `LocalDate.now()`: seed data with fixed dates is correct on the day it is written and
 * gradually becomes a demo of an organisation where nobody has had a birthday since 2026.
 *
 * UTC rather than local because these become `YYYY-MM-DD` strings via `toISOString`, and building
 * them in a `+05:30` local zone would shift roughly a quarter of them a day backwards in the
 * conversion. A join date that is one day out is the kind of wrongness nobody notices and
 * everybody eventually trips over.
 */
const TODAY = (() => {
  const now = new Date()
  return Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())
})()

const DAY_MS = 86_400_000

function isoDate(epochMs: number): string {
  return new Date(epochMs).toISOString().slice(0, 10)
}

function plusDays(epochMs: number, days: number): number {
  return epochMs + days * DAY_MS
}

function minusYears(epochMs: number, years: number): number {
  const date = new Date(epochMs)
  return Date.UTC(date.getUTCFullYear() - years, date.getUTCMonth(), date.getUTCDate())
}

/**
 * A date of birth whose anniversary falls [daysFromToday] from now.
 *
 * Copied from `LocalDemoSeeder.birthday`, including the leap-day handling: a birthday seeded on
 * 29 February disappears for three years out of four, which is a worse demo than being one day out
 * on that single date.
 */
function birthday(daysFromToday: number): string {
  const target = new Date(plusDays(TODAY, daysFromToday))
  const age = 24 + (daysFromToday % 20)
  const safe =
    target.getUTCMonth() === 1 && target.getUTCDate() === 29
      ? plusDays(target.getTime(), -1)
      : target.getTime()
  return isoDate(minusYears(safe, age))
}

/* -------------------------------------------------------------------------- */
/* Organisation                                                                */
/* -------------------------------------------------------------------------- */

export const TENANT_CODE = 'demo'

/**
 * The password every account is documented with.
 *
 * The demo transport accepts **any** non-empty password — there are no hashes here to check one
 * against, and a demo that can be locked out of itself by a typo is a demo nobody runs twice. This
 * constant exists so the startup banner and the README quote the same string as the backend
 * seeder, not because it is enforced.
 */
export const DEMO_PASSWORD = 'DemoPassw0rd!'

export const TENANT: TenantSummary = {
  id: demoId(ID_GROUP.tenant, 1),
  code: TENANT_CODE,
  name: 'Demo Company',
  defaultCurrency: 'LKR',
  timezone: 'Asia/Colombo',
}

export const COMPANY_ID = demoId(ID_GROUP.company, 1)

interface NamedRecord {
  id: string
  code: string
  name: string
}

function catalogue(group: number, entries: ReadonlyArray<readonly [string, string]>): Map<string, NamedRecord> {
  return new Map(
    entries.map(([code, name], index) => [code, { id: demoId(group, index + 1), code, name }]),
  )
}

/**
 * Two sites rather than one.
 *
 * `LocalDemoSeeder` has only Colombo, which leaves the directory's Location column showing the
 * same string 32 times — a column that cannot vary is a column nobody notices is broken.
 */
export const LOCATIONS = catalogue(ID_GROUP.location, [
  ['CMB', 'Colombo Head Office'],
  ['KDY', 'Kandy Branch'],
])

/** The seeder's three departments, plus the two the extra headcount belongs to. */
export const DEPARTMENTS = catalogue(ID_GROUP.department, [
  ['ENG', 'Engineering'],
  ['HR', 'People & Culture'],
  ['FIN', 'Finance'],
  ['SLS', 'Sales'],
  ['OPS', 'Operations'],
])

export const DESIGNATIONS = catalogue(ID_GROUP.designation, [
  ['CEO', 'Chief Executive Officer'],
  ['ENG_MGR', 'Engineering Manager'],
  ['SNR_SE', 'Senior Software Engineer'],
  ['SE', 'Software Engineer'],
  ['QA', 'Quality Assurance Engineer'],
  ['HR_MGR', 'HR Manager'],
  ['HR_EXEC', 'HR Executive'],
  ['ACC', 'Accountant'],
  ['SLS_MGR', 'Sales Manager'],
  ['SLS_EXEC', 'Sales Executive'],
  ['OPS_MGR', 'Operations Manager'],
  ['OPS_EXEC', 'Operations Executive'],
])

function lookup(catalogueOf: Map<string, NamedRecord>, code: string): NamedRecord {
  const found = catalogueOf.get(code)
  // Thrown at module load, so a typo in the workforce table below fails the dev server on startup
  // rather than rendering as a blank column somebody notices three screens later.
  if (found === undefined) throw new Error(`Demo fixture references unknown code '${code}'`)
  return found
}

export function departmentName(id: string | undefined): string | undefined {
  return [...DEPARTMENTS.values()].find((entry) => entry.id === id)?.name
}

export function designationName(id: string | undefined): string | undefined {
  return [...DESIGNATIONS.values()].find((entry) => entry.id === id)?.name
}

export function locationName(id: string | undefined): string | undefined {
  return [...LOCATIONS.values()].find((entry) => entry.id === id)?.name
}

/* -------------------------------------------------------------------------- */
/* Workforce                                                                   */
/* -------------------------------------------------------------------------- */

interface PersonSpec {
  code: string
  firstName: string
  lastName: string
  designation: string
  department: string
  location: string
  supervisor: string | null
  /** Days from today the birthday falls, or null for an employee with no date of birth recorded. */
  birthdayIn: number | null
  joinedYearsAgo: number
  joinedDaysOffset: number
  status?: ProfileFields['status']
  hasWorkPhone?: boolean
}

/**
 * Thirty-two people, four levels deep.
 *
 * `E001`–`E009` are `LocalDemoSeeder.workforce`, unchanged down to the mobile numbers. The rest
 * continue the same pattern: Sinhala, Tamil and Burgher names, because the backend indexes the
 * directory search vector with the `simple` dictionary rather than `english` precisely so these
 * tokenise correctly, and a demo full of Anglophone names would leave that decision unexercised.
 *
 * Finance still reports to the People & Culture manager, as it does in the seeder. That looks odd
 * on an org chart and it is deliberate — inventing a finance director to tidy it up would mean the
 * two demo datasets no longer agree about who `E007` reports to, and the reporting line is the
 * thing the manager account exists to demonstrate.
 */
const WORKFORCE: readonly PersonSpec[] = [
  // --- The seeder's nine, verbatim -----------------------------------------
  { code: 'E001', firstName: 'Nimali', lastName: 'Wickramasinghe', designation: 'CEO', department: 'ENG', location: 'CMB', supervisor: null, birthdayIn: 0, joinedYearsAgo: 12, joinedDaysOffset: -40, hasWorkPhone: true },
  { code: 'E002', firstName: 'Ruwan', lastName: 'Jayasuriya', designation: 'ENG_MGR', department: 'ENG', location: 'CMB', supervisor: 'E001', birthdayIn: 3, joinedYearsAgo: 5, joinedDaysOffset: 0, hasWorkPhone: true },
  { code: 'E003', firstName: 'Priya', lastName: 'Balasubramaniam', designation: 'HR_MGR', department: 'HR', location: 'CMB', supervisor: 'E001', birthdayIn: 21, joinedYearsAgo: 7, joinedDaysOffset: -3, hasWorkPhone: true },
  { code: 'E004', firstName: 'Kasun', lastName: 'Fernando', designation: 'SE', department: 'ENG', location: 'CMB', supervisor: 'E002', birthdayIn: 1, joinedYearsAgo: 3, joinedDaysOffset: 2 },
  { code: 'E005', firstName: 'Dilani', lastName: 'Perera', designation: 'SE', department: 'ENG', location: 'CMB', supervisor: 'E002', birthdayIn: 45, joinedYearsAgo: 2, joinedDaysOffset: 5 },
  { code: 'E006', firstName: 'Thivanka', lastName: 'Rajapaksa', designation: 'SE', department: 'ENG', location: 'CMB', supervisor: 'E002', birthdayIn: 120, joinedYearsAgo: 1, joinedDaysOffset: -60 },
  { code: 'E007', firstName: 'Anusha', lastName: 'Sivakumar', designation: 'ACC', department: 'FIN', location: 'CMB', supervisor: 'E003', birthdayIn: 6, joinedYearsAgo: 4, joinedDaysOffset: -120 },
  { code: 'E008', firstName: 'Malith', lastName: 'Gunawardena', designation: 'SE', department: 'ENG', location: 'CMB', supervisor: 'E002', birthdayIn: null, joinedYearsAgo: 10, joinedDaysOffset: 4 },
  { code: 'E009', firstName: 'Shanika', lastName: 'de Silva', designation: 'ACC', department: 'FIN', location: 'CMB', supervisor: 'E003', birthdayIn: 200, joinedYearsAgo: 6, joinedDaysOffset: -200 },

  // --- Enough headcount to make the directory paginate ----------------------
  { code: 'E010', firstName: 'Chathura', lastName: 'Bandara', designation: 'SLS_MGR', department: 'SLS', location: 'CMB', supervisor: 'E001', birthdayIn: 14, joinedYearsAgo: 6, joinedDaysOffset: -18, hasWorkPhone: true },
  { code: 'E011', firstName: 'Nadeesha', lastName: 'Ekanayake', designation: 'OPS_MGR', department: 'OPS', location: 'KDY', supervisor: 'E001', birthdayIn: 33, joinedYearsAgo: 8, joinedDaysOffset: -75, hasWorkPhone: true },

  { code: 'E012', firstName: 'Sanduni', lastName: 'Weerasinghe', designation: 'SNR_SE', department: 'ENG', location: 'CMB', supervisor: 'E002', birthdayIn: 9, joinedYearsAgo: 4, joinedDaysOffset: 11 },
  { code: 'E013', firstName: 'Isuru', lastName: 'Hettiarachchi', designation: 'SNR_SE', department: 'ENG', location: 'CMB', supervisor: 'E002', birthdayIn: 62, joinedYearsAgo: 5, joinedDaysOffset: -22 },
  { code: 'E014', firstName: 'Amara', lastName: 'Dissanayake', designation: 'SE', department: 'ENG', location: 'CMB', supervisor: 'E002', birthdayIn: 88, joinedYearsAgo: 2, joinedDaysOffset: 30 },
  { code: 'E015', firstName: 'Roshan', lastName: 'Kumarasinghe', designation: 'QA', department: 'ENG', location: 'CMB', supervisor: 'E002', birthdayIn: 150, joinedYearsAgo: 3, joinedDaysOffset: -9 },
  { code: 'E016', firstName: 'Hasini', lastName: 'Abeywardena', designation: 'QA', department: 'ENG', location: 'KDY', supervisor: 'E002', birthdayIn: 2, joinedYearsAgo: 1, joinedDaysOffset: 46 },
  { code: 'E017', firstName: 'Janaka', lastName: 'Senanayake', designation: 'SE', department: 'ENG', location: 'CMB', supervisor: 'E002', birthdayIn: 240, joinedYearsAgo: 7, joinedDaysOffset: -140 },
  // Recently joined, so the profile shows a probation end date — a field that is null for
  // everybody else and would otherwise never be seen rendered.
  { code: 'E018', firstName: 'Tharushi', lastName: 'Liyanage', designation: 'SE', department: 'ENG', location: 'CMB', supervisor: 'E002', birthdayIn: 71, joinedYearsAgo: 0, joinedDaysOffset: -50, status: 'PROBATION' },

  { code: 'E019', firstName: 'Menaka', lastName: 'Rathnayake', designation: 'HR_EXEC', department: 'HR', location: 'CMB', supervisor: 'E003', birthdayIn: 27, joinedYearsAgo: 3, joinedDaysOffset: -66 },
  { code: 'E020', firstName: 'Chamara', lastName: 'Wijesinghe', designation: 'HR_EXEC', department: 'HR', location: 'KDY', supervisor: 'E003', birthdayIn: 104, joinedYearsAgo: 2, joinedDaysOffset: 17 },
  { code: 'E021', firstName: 'Lakmini', lastName: 'Herath', designation: 'ACC', department: 'FIN', location: 'CMB', supervisor: 'E003', birthdayIn: 5, joinedYearsAgo: 5, joinedDaysOffset: -31 },
  { code: 'E022', firstName: 'Suresh', lastName: 'Thangarajah', designation: 'ACC', department: 'FIN', location: 'CMB', supervisor: 'E003', birthdayIn: 190, joinedYearsAgo: 9, joinedDaysOffset: -8 },

  { code: 'E023', firstName: 'Dinesh', lastName: 'Alwis', designation: 'SLS_EXEC', department: 'SLS', location: 'CMB', supervisor: 'E010', birthdayIn: 18, joinedYearsAgo: 4, joinedDaysOffset: 25 },
  { code: 'E024', firstName: 'Ishara', lastName: 'Gamage', designation: 'SLS_EXEC', department: 'SLS', location: 'CMB', supervisor: 'E010', birthdayIn: 55, joinedYearsAgo: 1, joinedDaysOffset: -13 },
  { code: 'E025', firstName: 'Nuwan', lastName: 'Karunaratne', designation: 'SLS_EXEC', department: 'SLS', location: 'KDY', supervisor: 'E010', birthdayIn: 130, joinedYearsAgo: 3, joinedDaysOffset: 7 },
  { code: 'E026', firstName: 'Sachini', lastName: 'Madushani', designation: 'SLS_EXEC', department: 'SLS', location: 'KDY', supervisor: 'E010', birthdayIn: 210, joinedYearsAgo: 2, joinedDaysOffset: -95 },
  { code: 'E027', firstName: 'Ravindu', lastName: 'Peiris', designation: 'SLS_EXEC', department: 'SLS', location: 'CMB', supervisor: 'E010', birthdayIn: 41, joinedYearsAgo: 6, joinedDaysOffset: 38 },

  { code: 'E028', firstName: 'Kavindi', lastName: 'Samarasinghe', designation: 'OPS_EXEC', department: 'OPS', location: 'KDY', supervisor: 'E011', birthdayIn: 11, joinedYearsAgo: 4, joinedDaysOffset: -47 },
  { code: 'E029', firstName: 'Tharindu', lastName: 'Wanigasekara', designation: 'OPS_EXEC', department: 'OPS', location: 'KDY', supervisor: 'E011', birthdayIn: 77, joinedYearsAgo: 2, joinedDaysOffset: 21 },
  { code: 'E030', firstName: 'Buddhika', lastName: 'Ranasinghe', designation: 'OPS_EXEC', department: 'OPS', location: 'KDY', supervisor: 'E011', birthdayIn: 165, joinedYearsAgo: 7, joinedDaysOffset: -110 },
  { code: 'E031', firstName: 'Nilanka', lastName: 'Amarasooriya', designation: 'OPS_EXEC', department: 'OPS', location: 'CMB', supervisor: 'E011', birthdayIn: 96, joinedYearsAgo: 1, joinedDaysOffset: 3 },
  // A leaver. The directory excludes exited employees, so this record is reachable by id and not
  // by search — which is the only way to see that exclusion actually happening.
  { code: 'E032', firstName: 'Yasas', lastName: 'Munasinghe', designation: 'OPS_EXEC', department: 'OPS', location: 'KDY', supervisor: 'E011', birthdayIn: 250, joinedYearsAgo: 3, joinedDaysOffset: -220, status: 'EXITED' },
]

function workEmail(person: PersonSpec): string {
  return `${person.firstName.toLowerCase()}.${person.lastName.replaceAll(' ', '').toLowerCase()}@demo.local`
}

/** `0771000001`, `0771000002`, … — the seeder's numbering, continued. */
function mobileFor(index: number): string {
  return `07710000${(index + 1).toString().padStart(2, '0')}`
}

function buildEmployee(person: PersonSpec, index: number, idsByCode: ReadonlyMap<string, string>): DemoEmployee {
  const joinDate = plusDays(minusYears(TODAY, person.joinedYearsAgo), person.joinedDaysOffset)
  const status = person.status ?? 'ACTIVE'
  const supervisorId = person.supervisor === null ? undefined : idsByCode.get(person.supervisor)

  return {
    id: idsByCode.get(person.code) ?? demoId(ID_GROUP.employee, index + 1),
    // Everyone starts at version 1; a save bumps it, which is what makes the If-Match conflict
    // reachable from two browser tabs.
    version: 1,
    employeeCode: person.code,
    status,
    displayName: `${person.firstName} ${person.lastName}`,
    firstName: person.firstName,
    lastName: person.lastName,
    dateOfBirth: person.birthdayIn === null ? undefined : birthday(person.birthdayIn),
    nationalityId: undefined,
    workEmail: workEmail(person),
    personalEmail: `${person.firstName.toLowerCase()}${person.code.toLowerCase()}@example.lk`,
    mobile: mobileFor(index),
    workPhone: person.hasWorkPhone === true ? `01123450${(index + 1).toString().padStart(2, '0')}` : undefined,
    currentAddress: {
      line1: `${(index % 90) + 10} Galle Road`,
      city: person.location === 'KDY' ? 'Kandy' : 'Colombo',
      postcode: person.location === 'KDY' ? '20000' : '00300',
      country: 'LK',
    },
    companyId: COMPANY_ID,
    joinDate: isoDate(joinDate),
    // Confirmed six months after joining, except for the one person still on probation.
    confirmationDate: status === 'PROBATION' ? undefined : isoDate(plusDays(joinDate, 182)),
    probationEndDate: status === 'PROBATION' ? isoDate(plusDays(joinDate, 182)) : undefined,
    resignDate: status === 'EXITED' ? isoDate(plusDays(TODAY, -75)) : undefined,
    lastWorkingDate: status === 'EXITED' ? isoDate(plusDays(TODAY, -45)) : undefined,
    departmentId: lookup(DEPARTMENTS, person.department).id,
    designationId: lookup(DESIGNATIONS, person.designation).id,
    locationId: lookup(LOCATIONS, person.location).id,
    supervisorId,
    yearsOfService: Math.max(0, Math.floor((TODAY - joinDate) / (DAY_MS * 365.25))),
    customFields: {
      // Sri Lankan statutory identifiers. Every employer here files against both, so they are the
      // most plausible thing a real tenant would add as a custom field.
      epfNumber: `A/${12000 + index}`,
      tshirtSize: (['S', 'M', 'L', 'XL'] as const)[index % 4],
      emergencyContact: `${person.lastName} household — ${mobileFor(index + 40)}`,
    },
  }
}

/**
 * The workforce, keyed by id.
 *
 * Built once at module load and then mutated in place by `PATCH /v1/employees/{id}`. That the
 * store is a plain `Map` and not a database is the whole point of the exercise; what it must do is
 * remember an edit for as long as the dev server is up, so a save and a re-read disagree the way
 * they would against a real server if the save had not worked.
 */
export function buildWorkforce(): Map<string, DemoEmployee> {
  const idsByCode = new Map(
    WORKFORCE.map((person, index) => [person.code, demoId(ID_GROUP.employee, index + 1)]),
  )
  const employees = WORKFORCE.map((person, index) => buildEmployee(person, index, idsByCode))
  return new Map(employees.map((employee) => [employee.id, employee]))
}

/** Employee id for a seeder employee code, for wiring accounts to people. */
export function employeeIdFor(code: string): string {
  const index = WORKFORCE.findIndex((person) => person.code === code)
  if (index < 0) throw new Error(`Demo fixture references unknown employee code '${code}'`)
  return demoId(ID_GROUP.employee, index + 1)
}

/* -------------------------------------------------------------------------- */
/* Roles and permissions                                                       */
/* -------------------------------------------------------------------------- */

/**
 * The permission catalogue, as `V2`, `V5`, `V6`, `V7` and `V8` insert it.
 *
 * Reproduced rather than summarised because the Overview screen renders this list verbatim and the
 * navigation is filtered by three specific keys. A plausible-looking made-up permission would
 * demo a console that grants access the real server does not.
 */
export interface PermissionDefinition {
  key: string
  domain: string
  label: string
  description: string
}

export const PERMISSION_CATALOG: readonly PermissionDefinition[] = [
  // Employee Domain
  { key: 'employee.view', domain: 'Employee', label: 'View Own & Team Profiles', description: 'View assigned team members and direct reports' },
  { key: 'employee.view.all', domain: 'Employee', label: 'View All Profiles', description: 'Unrestricted visibility across all company employees' },
  { key: 'employee.manage', domain: 'Employee', label: 'Manage Employees', description: 'Create, update, and manage employee profiles and transitions' },
  { key: 'employee.directory', domain: 'Employee', label: 'Access Directory', description: 'Search and browse employee phonebook and directory' },
  { key: 'employee.document.view', domain: 'Employee', label: 'View Employee Documents', description: 'Access uploaded compliance and identity documents' },
  { key: 'employee.bank.view', domain: 'Employee', label: 'View Bank Details', description: 'View sensitive banking and disbursement information' },
  { key: 'employee.salary.view', domain: 'Employee', label: 'View Compensation', description: 'View salary grades, pay rates, and compensation packages' },

  // Identity Domain
  { key: 'identity.user.view', domain: 'Identity', label: 'View Users', description: 'List and inspect user accounts and authentication statuses' },
  { key: 'identity.user.manage', domain: 'Identity', label: 'Manage Users', description: 'Create users, reset passwords, lock/unlock accounts' },
  { key: 'identity.role.view', domain: 'Identity', label: 'View Roles', description: 'View roles and granted permission matrices' },
  { key: 'identity.role.manage', domain: 'Identity', label: 'Manage Roles', description: 'Create, edit and assign custom roles and permission bundles' },
  { key: 'identity.device.view', domain: 'Identity', label: 'View Devices', description: 'View registered mobile and web devices per user' },
  { key: 'identity.device.revoke', domain: 'Identity', label: 'Revoke Devices & Sessions', description: 'Revoke active refresh tokens, sessions, and device trust' },

  // Organisation Domain
  { key: 'org.structure.view', domain: 'Organisation', label: 'View Org Structure', description: 'View company departments, designations, and hierarchy' },
  { key: 'org.structure.manage', domain: 'Organisation', label: 'Manage Org Structure', description: 'Create and update departments, designations, and locations' },
  { key: 'org.reference.view', domain: 'Organisation', label: 'View Reference Data', description: 'Access standard dropdowns, banks, and employment types' },
  { key: 'org.reference.manage', domain: 'Organisation', label: 'Manage Reference Data', description: 'Configure custom reference tables and dropdown options' },

  // Platform Domain
  { key: 'platform.tenant.view', domain: 'Platform', label: 'View Organisations / Tenants', description: 'Inspect tenant settings, modules, and billing' },
  { key: 'platform.tenant.manage', domain: 'Platform', label: 'Manage Organisations', description: 'Provision tenants, toggle module licenses, and quotas' },
  { key: 'platform.audit.view', domain: 'Platform', label: 'View Audit Logs', description: 'Inspect immutable tenant audit trail and access logs' },

  // Configuration Domain
  { key: 'config.field.view', domain: 'Configuration', label: 'View Custom Fields', description: 'View tenant-configured profile fields and schemas' },
  { key: 'config.field.manage', domain: 'Configuration', label: 'Manage Custom Fields', description: 'Add, reorder, or edit custom profile fields and validation' },
  { key: 'config.label.manage', domain: 'Configuration', label: 'Manage Custom Labels', description: 'Localise and customize UI terminology and labels' },

  // Leave Domain
  { key: 'leave.policy.view', domain: 'Leave', label: 'View Leave Policies', description: 'View leave policies and types' },
  { key: 'leave.policy.manage', domain: 'Leave', label: 'Manage Leave Policies', description: 'Create and modify leave policies' },
  { key: 'leave.balance.view', domain: 'Leave', label: 'View Leave Balances', description: 'View leave balances' },
  { key: 'leave.balance.manage', domain: 'Leave', label: 'Manage Leave Balances', description: 'Allocate and adjust leave balances' },
  { key: 'leave.request.view', domain: 'Leave', label: 'View Leave Requests', description: 'View leave applications' },
  { key: 'leave.request.create', domain: 'Leave', label: 'Apply for Leave', description: 'Submit a leave application' },
  { key: 'leave.request.approve', domain: 'Leave', label: 'Approve Leave', description: 'Approve or reject leave applications' },

  // Payroll Domain
  { key: 'payroll.view', domain: 'Payroll', label: 'View Payroll Overview', description: 'View payroll overview and pay groups' },
  { key: 'payroll.config.view', domain: 'Payroll', label: 'View Pay Groups', description: 'View pay groups and salary structures' },
  { key: 'payroll.config.manage', domain: 'Payroll', label: 'Manage Pay Groups', description: 'Manage pay groups and formula items' },
  { key: 'payroll.run.view', domain: 'Payroll', label: 'View Payroll Runs', description: 'View payroll runs and calculation details' },
  { key: 'payroll.run.manage', domain: 'Payroll', label: 'Process Payroll', description: 'Execute, process, and finalize payroll runs' },
  { key: 'payroll.payslip.view', domain: 'Payroll', label: 'View Payslips', description: 'View employee payslips' },
  { key: 'payroll.payslip.manage', domain: 'Payroll', label: 'Publish Payslips', description: 'Publish and manage payslips' },
  { key: 'payroll.report.view', domain: 'Payroll', label: 'View Payroll Reports', description: 'View and export payroll reports and bank advice' },

  // Attendance Domain
  { key: 'attendance.record.view', domain: 'Attendance', label: 'View Attendance Records', description: 'View attendance punches and daily summaries' },
  { key: 'attendance.record.manage', domain: 'Attendance', label: 'Manage Attendance', description: 'Adjust attendance records and resolve exceptions' },
  { key: 'attendance.punch.create', domain: 'Attendance', label: 'Clock In/Out', description: 'Submit clock-in and clock-out punches' },
  { key: 'attendance.shift.view', domain: 'Attendance', label: 'View Shifts', description: 'View shift rosters and schedules' },
  { key: 'attendance.shift.manage', domain: 'Attendance', label: 'Manage Shifts', description: 'Configure shifts and assign rosters' },
  { key: 'attendance.device.view', domain: 'Attendance', label: 'View Terminals', description: 'View biometric attendance devices' },
  { key: 'attendance.device.manage', domain: 'Attendance', label: 'Manage Terminals', description: 'Register and configure biometric devices' },

  // Loans Domain
  { key: 'loan.type.view', domain: 'Loans', label: 'View Loan Types', description: 'View loan products and terms' },
  { key: 'loan.type.manage', domain: 'Loans', label: 'Manage Loan Types', description: 'Configure loan products' },
  { key: 'loan.request.view', domain: 'Loans', label: 'View Loans', description: 'View loan applications and active loans' },
  { key: 'loan.request.create', domain: 'Loans', label: 'Apply for Loan', description: 'Apply for an employee loan' },
  { key: 'loan.request.approve', domain: 'Loans', label: 'Approve Loans', description: 'Approve or reject loan applications' },
  { key: 'loan.settle', domain: 'Loans', label: 'Settle Loans', description: 'Process early loan settlements' },

  // Expenses Domain
  { key: 'expense.type.view', domain: 'Expenses', label: 'View Expense Types', description: 'View expense categories and policies' },
  { key: 'expense.type.manage', domain: 'Expenses', label: 'Manage Expense Types', description: 'Configure expense categories' },
  { key: 'expense.claim.view', domain: 'Expenses', label: 'View Expense Claims', description: 'View expense claims' },
  { key: 'expense.claim.create', domain: 'Expenses', label: 'Submit Expense Claim', description: 'Submit an expense claim' },
  { key: 'expense.claim.approve', domain: 'Expenses', label: 'Approve Expense Claims', description: 'Approve or reject expense claims' },
  { key: 'expense.claim.reimburse', domain: 'Expenses', label: 'Reimburse Claims', description: 'Process reimbursement payments' },

  // Benefits Domain
  { key: 'benefit.plan.view', domain: 'Benefits', label: 'View Benefit Plans', description: 'View benefit plans' },
  { key: 'benefit.plan.manage', domain: 'Benefits', label: 'Manage Benefit Plans', description: 'Configure benefit plans and eligibility' },
  { key: 'benefit.enrolment.view', domain: 'Benefits', label: 'View Benefit Enrolments', description: 'View benefit enrolments and claims' },
  { key: 'benefit.enrolment.manage', domain: 'Benefits', label: 'Manage Benefit Claims', description: 'Manage benefit enrolments and adjudicate claims' },

  // Employee Lifecycle Domain
  { key: 'lifecycle.movement.view', domain: 'Lifecycle', label: 'View Movements', description: 'View employee promotions, transfers, and status changes' },
  { key: 'lifecycle.movement.manage', domain: 'Lifecycle', label: 'Manage Movements', description: 'Initiate and process employee movements' },
  { key: 'lifecycle.probation.view', domain: 'Lifecycle', label: 'View Probations', description: 'View probation statuses and reviews' },
  { key: 'lifecycle.probation.manage', domain: 'Lifecycle', label: 'Manage Probations', description: 'Complete and confirm probation evaluations' },

  // Disciplinary & Grievance Domain
  { key: 'disciplinary.case.view', domain: 'Disciplinary', label: 'View Disciplinary Cases', description: 'View disciplinary cases' },
  { key: 'disciplinary.case.manage', domain: 'Disciplinary', label: 'Manage Disciplinary Cases', description: 'Open, investigate, and close disciplinary cases' },
  { key: 'disciplinary.grievance.view', domain: 'Disciplinary', label: 'View Grievances', description: 'View employee grievances' },
  { key: 'disciplinary.grievance.manage', domain: 'Disciplinary', label: 'Manage Grievances', description: 'Manage and resolve grievance submissions' },

  // Performance & Goals Domain
  { key: 'performance.cycle.view', domain: 'Performance', label: 'View Appraisal Cycles', description: 'View performance appraisal cycles' },
  { key: 'performance.cycle.manage', domain: 'Performance', label: 'Manage Appraisal Cycles', description: 'Configure appraisal cycles, templates, and scales' },
  { key: 'performance.review.view', domain: 'Performance', label: 'View Performance Reviews', description: 'View performance reviews and ratings' },
  { key: 'performance.review.manage', domain: 'Performance', label: 'Conduct Reviews', description: 'Submit and finalize performance reviews' },
  { key: 'performance.goal.view', domain: 'Performance', label: 'View Goals', description: 'View performance goals and OKRs' },
  { key: 'performance.goal.manage', domain: 'Performance', label: 'Manage Goals', description: 'Assign and track goals' },

  // Recruitment & ATS Domain
  { key: 'recruitment.job.view', domain: 'Recruitment', label: 'View Job Requisitions', description: 'View job requisitions and postings' },
  { key: 'recruitment.job.manage', domain: 'Recruitment', label: 'Manage Requisitions', description: 'Create and publish job requisitions' },
  { key: 'recruitment.candidate.view', domain: 'Recruitment', label: 'View Candidates', description: 'View candidate applications and resumes' },
  { key: 'recruitment.candidate.manage', domain: 'Recruitment', label: 'Manage Candidates', description: 'Manage applicant pipeline, stages, and interviews' },
  { key: 'recruitment.offer.manage', domain: 'Recruitment', label: 'Issue Offers', description: 'Issue job offers and compensation packages' },

  // Onboarding & Offboarding Domain
  { key: 'onboarding.task.view', domain: 'Onboarding', label: 'View Onboarding Tasks', description: 'View onboarding workflows and tasks' },
  { key: 'onboarding.task.manage', domain: 'Onboarding', label: 'Manage Onboarding', description: 'Assign and complete onboarding task lists' },
  { key: 'offboarding.task.view', domain: 'Onboarding', label: 'View Offboarding Tasks', description: 'View offboarding workflows and clearance' },
  { key: 'offboarding.task.manage', domain: 'Onboarding', label: 'Manage Offboarding', description: 'Process employee exit clearance' },

  // Documents & Signatures Domain
  { key: 'document.template.view', domain: 'Documents', label: 'View Document Templates', description: 'View document templates' },
  { key: 'document.template.manage', domain: 'Documents', label: 'Manage Templates', description: 'Create and edit document templates' },
  { key: 'document.employee.view', domain: 'Documents', label: 'View Employee Documents', description: 'View employee documents and files' },
  { key: 'document.employee.manage', domain: 'Documents', label: 'Manage Documents', description: 'Upload, verify, and delete employee documents' },
  { key: 'document.signature.view', domain: 'Documents', label: 'View Signatures', description: 'View e-signature requests and status' },
  { key: 'document.signature.manage', domain: 'Documents', label: 'Sign Documents', description: 'Initiate and sign electronic documents' },

  // Training & Development Domain
  { key: 'training.course.view', domain: 'Training', label: 'View Training Courses', description: 'View training course catalogue' },
  { key: 'training.course.manage', domain: 'Training', label: 'Manage Courses', description: 'Manage courses, competencies, and materials' },
  { key: 'training.schedule.view', domain: 'Training', label: 'View Training Schedules', description: 'View training schedules and sessions' },
  { key: 'training.schedule.manage', domain: 'Training', label: 'Manage Schedules', description: 'Schedule training sessions and trainers' },
  { key: 'training.enrolment.view', domain: 'Training', label: 'View Enrolments', description: 'View training enrolments and completions' },
  { key: 'training.enrolment.manage', domain: 'Training', label: 'Manage Enrolments', description: 'Nominate, enroll, and mark course completion' },

  // Timesheets & Billing Domain
  { key: 'timesheet.record.view', domain: 'Timesheets', label: 'View Timesheets', description: 'View timesheets and project time entries' },
  { key: 'timesheet.record.manage', domain: 'Timesheets', label: 'Manage Projects', description: 'Manage time tracking projects and tasks' },
  { key: 'timesheet.submit', domain: 'Timesheets', label: 'Submit Timesheet', description: 'Submit timesheets for approval' },
  { key: 'timesheet.approve', domain: 'Timesheets', label: 'Approve Timesheet', description: 'Approve or reject timesheets' },

  // Biometric Terminals Domain
  { key: 'biometric.device.view', domain: 'Attendance', label: 'View Biometric Terminals', description: 'View biometric devices' },
  { key: 'biometric.device.manage', domain: 'Attendance', label: 'Manage Biometric Terminals', description: 'Register and configure biometric devices' },
  { key: 'biometric.command.manage', domain: 'Attendance', label: 'Command Terminals', description: 'Queue commands to biometric devices' },

  // Dashboard Domain
  { key: 'dashboard.view', domain: 'Dashboard', label: 'View Dashboard', description: 'View role-based dashboard widgets and analytics' },
]

export const ALL_PERMISSIONS: readonly string[] = PERMISSION_CATALOG.map((p) => p.key)

/** The system roles from provision_tenant_defaults (V28) with their grants. */
export const ROLE_PERMISSIONS = {
  ADMIN: ALL_PERMISSIONS,
  HR_ADMIN: [
    'identity.user.view',
    'identity.user.manage',
    'identity.device.view',
    'identity.device.revoke',
    'platform.audit.view',
    'org.structure.view',
    'org.structure.manage',
    'org.reference.view',
    'org.reference.manage',
    'config.field.view',
    'config.field.manage',
    'config.label.manage',
    'employee.view',
    'employee.view.all',
    'employee.manage',
    'employee.directory',
    'employee.document.view',
    'leave.policy.view',
    'leave.policy.manage',
    'leave.balance.view',
    'leave.balance.manage',
    'leave.request.view',
    'leave.request.approve',
    'attendance.record.view',
    'attendance.record.manage',
    'attendance.shift.view',
    'attendance.shift.manage',
    'attendance.device.view',
    'attendance.device.manage',
    'biometric.device.view',
    'biometric.device.manage',
    'biometric.command.manage',
    'benefit.plan.view',
    'benefit.plan.manage',
    'benefit.enrolment.view',
    'benefit.enrolment.manage',
    'lifecycle.movement.view',
    'lifecycle.movement.manage',
    'lifecycle.probation.view',
    'lifecycle.probation.manage',
    'disciplinary.case.view',
    'disciplinary.case.manage',
    'disciplinary.grievance.view',
    'disciplinary.grievance.manage',
    'performance.cycle.view',
    'performance.cycle.manage',
    'performance.review.view',
    'performance.review.manage',
    'performance.goal.view',
    'performance.goal.manage',
    'recruitment.job.view',
    'recruitment.job.manage',
    'recruitment.candidate.view',
    'recruitment.candidate.manage',
    'recruitment.offer.manage',
    'onboarding.task.view',
    'onboarding.task.manage',
    'offboarding.task.view',
    'offboarding.task.manage',
    'document.template.view',
    'document.template.manage',
    'document.employee.view',
    'document.employee.manage',
    'document.signature.view',
    'document.signature.manage',
    'training.course.view',
    'training.course.manage',
    'training.schedule.view',
    'training.schedule.manage',
    'training.enrolment.view',
    'training.enrolment.manage',
    'timesheet.record.view',
    'timesheet.approve',
    'loan.request.view',
    'loan.request.approve',
    'dashboard.view',
  ],
  MANAGER: [
    'identity.user.view',
    'org.structure.view',
    'org.reference.view',
    'employee.view',
    'employee.directory',
    'leave.request.view',
    'leave.request.approve',
    'attendance.record.view',
    'attendance.shift.view',
    'expense.claim.view',
    'expense.claim.approve',
    'timesheet.record.view',
    'timesheet.approve',
    'performance.review.view',
    'performance.review.manage',
    'performance.goal.view',
    'performance.goal.manage',
    'dashboard.view',
  ],
  EMPLOYEE: [
    'employee.directory',
    'org.reference.view',
    'leave.request.create',
    'leave.request.view',
    'expense.claim.create',
    'expense.claim.view',
    'loan.request.create',
    'loan.request.view',
    'attendance.punch.create',
    'attendance.record.view',
    'timesheet.submit',
    'timesheet.record.view',
    'training.course.view',
    'training.enrolment.view',
    'document.signature.manage',
    'performance.goal.view',
    'benefit.enrolment.view',
    'dashboard.view',
  ],
  FINANCE: [
    'org.reference.view',
    'employee.directory',
    'employee.salary.view',
    'employee.bank.view',
    'payroll.view',
    'payroll.config.view',
    'payroll.config.manage',
    'payroll.run.view',
    'payroll.run.manage',
    'payroll.payslip.view',
    'payroll.payslip.manage',
    'payroll.report.view',
    'expense.claim.view',
    'expense.claim.approve',
    'expense.claim.reimburse',
    'loan.type.view',
    'loan.type.manage',
    'loan.request.view',
    'loan.request.approve',
    'loan.settle',
    'timesheet.record.view',
    'benefit.plan.view',
    'benefit.enrolment.view',
    'dashboard.view',
  ],
  RECRUITER: [
    'org.structure.view',
    'org.reference.view',
    'employee.directory',
    'recruitment.job.view',
    'recruitment.job.manage',
    'recruitment.candidate.view',
    'recruitment.candidate.manage',
    'recruitment.offer.manage',
    'onboarding.task.view',
    'dashboard.view',
  ],
  AUDITOR: [
    'org.structure.view',
    'org.reference.view',
    'platform.audit.view',
    'employee.directory',
    'employee.view',
    'leave.policy.view',
    'leave.request.view',
    'attendance.record.view',
    'payroll.view',
    'payroll.run.view',
    'payroll.report.view',
    'loan.request.view',
    'expense.claim.view',
    'benefit.plan.view',
    'performance.review.view',
    'recruitment.job.view',
    'dashboard.view',
  ],
}

export interface DemoRole {
  id: string
  code: string
  name: string
  description: string
  isSystem: boolean
  permissions: string[]
}

export const INITIAL_ROLES: DemoRole[] = [
  {
    id: 'role-admin',
    code: 'ADMIN',
    name: 'Administrator',
    description: 'Full system access across all tenant resources, security, and settings.',
    isSystem: true,
    permissions: [...ALL_PERMISSIONS],
  },
  {
    id: 'role-hr-admin',
    code: 'HR_ADMIN',
    name: 'HR Administrator',
    description: 'Workforce administration, employee profiles, and department structure.',
    isSystem: true,
    permissions: [...ROLE_PERMISSIONS.HR_ADMIN],
  },
  {
    id: 'role-manager',
    code: 'MANAGER',
    name: 'Manager',
    description: 'Supervisory visibility over direct reports, approvals, and team structures.',
    isSystem: true,
    permissions: [...ROLE_PERMISSIONS.MANAGER],
  },
  {
    id: 'role-employee',
    code: 'EMPLOYEE',
    name: 'Employee',
    description: 'Standard self-service profile, attendance, and company directory access.',
    isSystem: true,
    permissions: [...ROLE_PERMISSIONS.EMPLOYEE],
  },
  {
    id: 'role-finance',
    code: 'FINANCE',
    name: 'Finance / Payroll Officer',
    description: 'Executes payroll runs, manages compensation, expense reimbursements, and loans.',
    isSystem: true,
    permissions: [...ROLE_PERMISSIONS.FINANCE],
  },
  {
    id: 'role-recruiter',
    code: 'RECRUITER',
    name: 'Recruitment Specialist',
    description: 'Manages candidate pipelines, job requisitions, and interviews.',
    isSystem: true,
    permissions: [...ROLE_PERMISSIONS.RECRUITER],
  },
  {
    id: 'role-auditor',
    code: 'AUDITOR',
    name: 'Auditor',
    description: 'Read-only compliance and statutory reporting audit access.',
    isSystem: true,
    permissions: [...ROLE_PERMISSIONS.AUDITOR],
  },
]

/* -------------------------------------------------------------------------- */
/* Accounts                                                                    */
/* -------------------------------------------------------------------------- */

export type AccountStatus = 'ACTIVE' | 'LOCKED' | 'DISABLED'

export interface DemoAccount {
  userId: string
  username: string
  email: string
  employeeCode: string
  role: string
  status: AccountStatus
  purpose: string
  fieldGrants?: Record<string, 'HIDDEN' | 'MASKED' | 'READ' | 'WRITE'>
}

export const ACCOUNTS: readonly DemoAccount[] = [
  {
    userId: demoId(ID_GROUP.user, 1),
    username: 'admin',
    email: 'admin@demo.local',
    employeeCode: 'E001',
    role: 'ADMIN',
    status: 'ACTIVE',
    purpose: 'everything, linked to the CEO’s record; granted sight of sensitive fields',
    fieldGrants: { dateOfBirth: 'WRITE', personalEmail: 'WRITE', salaryGradeId: 'READ' },
  },
  {
    userId: demoId(ID_GROUP.user, 2),
    username: 'manager',
    email: 'manager@demo.local',
    employeeCode: 'E002',
    role: 'MANAGER',
    status: 'ACTIVE',
    purpose: 'team approvals, direct reports, attendance exceptions',
  },
  {
    userId: demoId(ID_GROUP.user, 3),
    username: 'employee',
    email: 'employee@demo.local',
    employeeCode: 'E004',
    role: 'EMPLOYEE',
    status: 'ACTIVE',
    purpose: 'self-service directory, attendance punch, leave requests, own payslips',
  },
  {
    userId: demoId(ID_GROUP.user, 4),
    username: 'hr',
    email: 'hr@demo.local',
    employeeCode: 'E003',
    role: 'HR_ADMIN',
    status: 'ACTIVE',
    purpose: 'workforce management, onboarding, disciplinary, talent operations',
  },
  {
    userId: demoId(ID_GROUP.user, 5),
    username: 'hr.admin',
    email: 'hr.admin@demo.local',
    employeeCode: 'E003',
    role: 'HR_ADMIN',
    status: 'ACTIVE',
    purpose: 'alias for HR Admin account',
  },
  {
    userId: demoId(ID_GROUP.user, 6),
    username: 'finance',
    email: 'finance@demo.local',
    employeeCode: 'E005',
    role: 'FINANCE',
    status: 'ACTIVE',
    purpose: 'payroll calculation, expense reimbursement disbursement, staff loans',
  },
  {
    userId: demoId(ID_GROUP.user, 7),
    username: 'recruiter',
    email: 'recruiter@demo.local',
    employeeCode: 'E007',
    role: 'RECRUITER',
    status: 'ACTIVE',
    purpose: 'talent acquisition, candidate stages, job requisitions',
  },
  {
    userId: demoId(ID_GROUP.user, 8),
    username: 'auditor',
    email: 'auditor@demo.local',
    employeeCode: 'E008',
    role: 'AUDITOR',
    status: 'ACTIVE',
    purpose: 'statutory and regulatory compliance read-only auditor',
  },
  {
    userId: demoId(ID_GROUP.user, 9),
    username: 'locked',
    email: 'locked@demo.local',
    employeeCode: 'E006',
    role: 'EMPLOYEE',
    status: 'LOCKED',
    purpose: 'always answers 403 ACCOUNT_LOCKED, so the sign-in error path can be seen',
  },
]

export function permissionsFor(account: DemoAccount): string[] {
  const roleGrants = (ROLE_PERMISSIONS as Record<string, readonly string[]>)[account.role] ?? []
  return [...roleGrants].sort()
}

export function meResponseFor(account: DemoAccount): MeResponse {
  return {
    userId: account.userId,
    employeeId: employeeIdFor(account.employeeCode),
    username: account.username,
    email: account.email,
    locale: 'en',
    timezone: 'Asia/Colombo',
    tenant: TENANT,
    roles: [account.role],
    permissions: permissionsFor(account),
    // `MeController` hardcodes this too: module enablement arrives with the tenant module
    // registry, and until then the client sees the Phase 0 surface only.
    enabledModules: ['identity'],
    mustChangePassword: false,
    mfaEnabled: false,
  }
}

/* -------------------------------------------------------------------------- */
/* Devices                                                                     */
/* -------------------------------------------------------------------------- */

export interface DemoDevice {
  id: string
  deviceId: string
  platform: 'ANDROID' | 'IOS' | 'WEB' | 'KIOSK'
  model?: string
  osVersion?: string
  appVersion?: string
  biometricEnrolled?: boolean
  attestationVerified?: boolean
  trusted: boolean
  /** ISO-8601 instant. `DeviceFromJSON` parses this into a `Date`. */
  lastSeenAt?: string
  current?: boolean
}

function hoursAgo(hours: number): string {
  return new Date(Date.now() - hours * 3_600_000).toISOString()
}

/**
 * A phone or two per account, seeded before anyone signs in.
 *
 * An empty device list renders the "sign in to the mobile app" empty state, which is a real state
 * worth having but a poor default: with nothing in the table, the Revoke button, the trust badges
 * and the relative "last used" column are all unreachable. One untrusted device is included on
 * purpose — the "Not trusted" badge is the only reason that column exists, and a table where every
 * row is trusted proves nothing about it.
 */
export function seedDevicesFor(account: DemoAccount, sequence: number): DemoDevice[] {
  const base = sequence * 10
  const devices: DemoDevice[] = [
    {
      id: demoId(ID_GROUP.device, base + 1),
      deviceId: `android-${account.username}-a91f`,
      platform: 'ANDROID',
      model: 'Pixel 8',
      osVersion: '15',
      appVersion: '0.1.0',
      biometricEnrolled: true,
      attestationVerified: true,
      trusted: true,
      lastSeenAt: hoursAgo(5),
    },
  ]

  if (account.username === 'admin' || account.username === 'hr') {
    devices.push({
      id: demoId(ID_GROUP.device, base + 2),
      deviceId: `ios-${account.username}-3d0c`,
      platform: 'IOS',
      model: 'iPhone 15',
      osVersion: '18.2',
      appVersion: '0.1.0',
      biometricEnrolled: false,
      attestationVerified: false,
      // Failed attestation. The one row that is not trusted is the whole point of the column.
      trusted: false,
      lastSeenAt: hoursAgo(24 * 9),
    })
  }

  return devices
}

/* -------------------------------------------------------------------------- */
/* Custom fields                                                               */
/* -------------------------------------------------------------------------- */

/** A tenant-defined field, as `field_definition` rows would describe it. */
export interface CustomFieldDefinition {
  section: string
  field: FormField
}

/**
 * Three tenant-defined fields, interleaved with the built-in ones.
 *
 * Interleaved rather than appended because that is the property the server-driven form exists to
 * provide, and a "Custom fields" lump at the bottom of the page would demo the thing it was
 * designed to avoid. One of them is a `DROPDOWN`, which the web form deliberately renders
 * read-only — see `EDITABLE_AS_TEXT` in `EmployeeProfile.tsx`. That branch has never had anything
 * to render before now.
 */
export const CUSTOM_FIELDS: readonly CustomFieldDefinition[] = [
  {
    section: 'personal',
    field: {
      key: 'tshirtSize',
      label: 'T-shirt size',
      type: 'DROPDOWN',
      custom: true,
      options: [
        { value: 'XS', label: 'XS' },
        { value: 'S', label: 'S' },
        { value: 'M', label: 'M' },
        { value: 'L', label: 'L' },
        { value: 'XL', label: 'XL' },
        { value: 'XXL', label: 'XXL' },
      ],
    },
  },
  {
    section: 'contact',
    field: {
      key: 'emergencyContact',
      label: 'Emergency contact',
      type: 'TEXT',
      custom: true,
      helpText: 'Who to call, and on what number.',
      validation: { maxLength: 160 },
    },
  },
  {
    section: 'employment',
    field: {
      key: 'epfNumber',
      label: 'EPF number',
      type: 'TEXT',
      custom: true,
      required: true,
      helpText: 'Employees’ Provident Fund membership number, as it appears on the return.',
      validation: {
        pattern: '^[A-Z]/\\d{3,7}$',
        patternMessage: 'Format is a letter, a slash, then three to seven digits — for example A/12034.',
        maxLength: 16,
      },
    },
  },
]

export const CUSTOM_FIELD_KEYS: readonly string[] = CUSTOM_FIELDS.map((entry) => entry.field.key)
