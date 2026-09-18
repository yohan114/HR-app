/**
 * Everything the demo transport remembers.
 *
 * In-memory and process-lifetime: restart the dev server and the company is back as it was seeded.
 * That is a deliberate boundary rather than a shortcut. Persisting it would mean a demo whose
 * behaviour depends on what somebody did to it three days ago, and the first symptom of that is a
 * bug report nobody can reproduce.
 *
 * What it *does* have to do is hold state long enough to be worth clicking through. A save that
 * does not survive to the next read, a revoked device that comes back on refresh, or an MFA
 * enrolment that never completes would each leave the console looking like it works while its most
 * interesting paths went untested.
 */

import { randomBytes, randomUUID } from 'node:crypto'
import type { NotificationSettings } from '@hr/client'
import {
  ACCOUNTS,
  buildWorkforce,
  type DemoAccount,
  type DemoDevice,
  type DemoEmployee,
  type DemoRole,
  employeeIdFor,
  INITIAL_ROLES,
  permissionsFor,
  seedDevicesFor,
  TENANT,
} from './company'
import type { Caller } from './access'

/* -------------------------------------------------------------------------- */
/* Sessions                                                                    */
/* -------------------------------------------------------------------------- */

/** Fifteen minutes, matching the access-token lifetime the spec documents. */
const ACCESS_TOKEN_TTL_SECONDS = 900

/** Thirty days, matching the refresh lifetime the mobile clients are built around. */
const REFRESH_TOKEN_TTL_SECONDS = 30 * 24 * 3600

export interface Session {
  id: string
  userId: string
  /** The device row this session was issued to. Revoking that device ends this session. */
  deviceRowId: string
  refreshToken: string
  accessToken: string
  accessTokenExpiresAt: number
  revoked: boolean
}

export interface MfaState {
  enabled: boolean
  enrolmentPending: boolean
  recoveryCodesRemaining: number
  /** Held only between `enrol` and `enrol/confirm`. The plaintext codes are never kept. */
  pendingSecret?: string
}

export interface DemoUser {
  id: string
  username: string
  email: string
  employeeCode?: string
  employeeName?: string
  role: string
  status: 'ACTIVE' | 'LOCKED' | 'PENDING_MFA' | 'DISABLED'
  mfaEnabled: boolean
  lastLoginAt?: string
  createdAt: string
  mustChangePassword?: boolean
}

export interface DemoTenantModule {
  moduleKey: string
  enabled: boolean
  config?: Record<string, unknown>
  updatedAt?: string
}

export interface DemoTenant {
  id: string
  code: string
  name: string
  legalName?: string
  countryCode: string
  timezone: string
  defaultCurrency: string
  locale: string
  dataRegion: string
  isolationTier: 'SHARED' | 'DEDICATED_SCHEMA' | 'DEDICATED_DATABASE'
  status: 'PROVISIONING' | 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED'
  subscriptionPlan: string
  adminEmail?: string
  modules: DemoTenantModule[]
  createdAt: string
  updatedAt: string
}

export interface DemoLeaveBalance {
  leaveTypeId: string
  leaveTypeCode: string
  leaveTypeName: string
  color: string
  entitledDays: number
  accruedDays: number
  takenDays: number
  pendingDays: number
  availableDays: number
}

export interface DemoLeaveApplicationDay {
  date: string
  dayOfWeek: string
  isWorkingDay: boolean
  isPublicHoliday: boolean
  holidayName?: string
  portion: 'FULL_DAY' | 'FIRST_HALF' | 'SECOND_HALF'
  hours: number
}

export interface DemoLeaveApplication {
  id: string
  employeeId: string
  employeeName: string
  department: string
  leaveTypeId: string
  leaveTypeCode: string
  leaveTypeName: string
  startDate: string
  endDate: string
  dayPortion: 'FULL_DAY' | 'FIRST_HALF' | 'SECOND_HALF'
  totalDays: number
  reason: string
  status: 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'WITHDRAWN'
  submittedAt: string
  approvedAt?: string
  days: DemoLeaveApplicationDay[]
}

export interface DemoLeaveLedgerEntry {
  id: string
  employeeId: string
  date: string
  leaveTypeId: string
  leaveTypeCode: string
  leaveTypeName: string
  eventType: 'OPENING' | 'ACCRUAL' | 'TAKEN' | 'ADJUSTMENT' | 'CANCELLATION' | 'FORFEITURE'
  daysCredited: number
  daysDebited: number
  balanceAfter: number
  referenceId?: string
  notes?: string
}

export interface DemoPublicHoliday {
  date: string
  name: string
  isFullDay: boolean
}

export interface DemoShift {
  id: string
  code: string
  name: string
  shiftType: 'FIXED' | 'ROTATING' | 'SPLIT' | 'NIGHT' | 'FLEXIBLE' | 'OPEN'
  startTime: string
  endTime: string
  breakMinutes: number
  workingMinutes: number
  graceInMinutes: number
  graceOutMinutes: number
  halfDayThresholdMinutes: number
  otEligible: boolean
  otStartAfterMinutes: number
  minOtMinutes: number
  color: string
  isActive: boolean
}

export interface DemoShiftSchedule {
  id: string
  employeeId: string
  employeeCode: string
  employeeName: string
  department: string
  workDate: string
  shiftId?: string
  shiftCode?: string
  shiftName?: string
  shiftColor?: string
  startTime?: string
  endTime?: string
  isRestDay: boolean
  isHoliday: boolean
  holidayName?: string
  source: 'DEFAULT_SHIFT' | 'ROSTER' | 'MANUAL' | 'SWAP'
}

export interface DemoRawPunch {
  id: string
  employeeId: string
  employeeCode: string
  employeeName: string
  department: string
  punchedAt: string
  punchType: 'IN' | 'OUT' | 'BREAK_IN' | 'BREAK_OUT' | 'AUTO'
  source: 'BIOMETRIC_DEVICE' | 'MOBILE_APP' | 'KIOSK' | 'WEB_PORTAL' | 'MANUAL_IMPORT'
  deviceId?: string
  locationName?: string
  geoLat?: number
  geoLng?: number
  geoAccuracyM?: number
  geofenceStatus: 'INSIDE' | 'OUTSIDE' | 'UNKNOWN' | 'NOT_APPLICABLE'
  isMockLocation: boolean
  clientIdempotencyKey?: string
  recordedOffline: boolean
  syncedAt: string
}

export interface DemoDailyAttendance {
  id: string
  employeeId: string
  employeeCode: string
  employeeName: string
  department: string
  workDate: string
  shiftId?: string
  shiftCode?: string
  shiftName?: string
  firstInAt?: string
  lastOutAt?: string
  grossDurationMinutes: number
  breakMinutes: number
  netWorkedMinutes: number
  lateMinutes: number
  earlyLeaveMinutes: number
  overtimeMinutesNormal: number
  overtimeMinutesRestDay: number
  overtimeMinutesHoliday: number
  dayStatus: 'PRESENT' | 'HALF_DAY' | 'ABSENT' | 'ON_LEAVE' | 'REST_DAY' | 'HOLIDAY' | 'NO_SHOW'
  leaveTypeName?: string
  leaveDays?: number
  anomalyFlags: string[]
  calculationTrace?: string
  computedAt: string
}

export interface World {
  employees: Map<string, DemoEmployee>
  users: Map<string, DemoUser>
  roles: Map<string, DemoRole>
  tenants: Map<string, DemoTenant>
  devicesByUser: Map<string, DemoDevice[]>
  mfaByUser: Map<string, MfaState>
  notificationsByUser: Map<string, NotificationSettings>
  sessions: Map<string, Session>
  /** Live refresh token → session id. */
  refreshTokens: Map<string, string>
  spentRefreshTokens: Map<string, string>
  accessTokens: Map<string, string>
  // Leave & Absence engine
  leaveBalancesByUser: Map<string, DemoLeaveBalance[]>
  leaveLedgerByUser: Map<string, DemoLeaveLedgerEntry[]>
  leaveApplicationsByUser: Map<string, DemoLeaveApplication[]>
  publicHolidays: DemoPublicHoliday[]
  // Payroll Engine (P3-BE-21 / P3-WEB-07)
  payGroups: Map<string, DemoPayGroup>
  payPeriods: Map<string, DemoPayPeriod>
  payrollRuns: Map<string, DemoPayrollRun>
  payrollResultsByRun: Map<string, DemoPayrollResult[]>
  payrollLinesByResult: Map<string, DemoPayrollResultLine[]>
  // Attendance & Shift Roster Engine (P2-BE / P2-WEB)
  shifts: Map<string, DemoShift>
  shiftSchedules: Map<string, DemoShiftSchedule[]>
  rawPunches: DemoRawPunch[]
  dailyAttendance: Map<string, DemoDailyAttendance[]>
  // Recruitment & ATS (V21)
  vacancies: Map<string, any>
  candidates: Map<string, any>
  applications: Map<string, any>
  interviews: Map<string, any>
  scorecards: Map<string, any[]>
  offers: Map<string, any>
  // Document Management & Signatures (V23)
  documentFolders: Map<string, any>
  companyDocuments: Map<string, any>
  documentVersions: Map<string, any[]>
  documentTemplates: Map<string, any>
  letterRequests: Map<string, any>
  signatureRequests: Map<string, any>
  signatureAuditLogs: Map<string, any[]>
  // Timesheets & Project Billing (V25)
  timesheetClients: Map<string, any>
  timesheetProjects: Map<string, any>
  timesheetActivities: Map<string, any>
  timesheets: Map<string, any>
  timesheetEntries: Map<string, any[]>
  // Performance Management & OKRs (V20)
  goalCycles: Map<string, any>
  goals: Map<string, any>
  goalCheckIns: Map<string, any[]>
  competencyGroups: Map<string, any>
  competencies: Map<string, any>
  evaluationCycles: Map<string, any>
  appraisals: Map<string, any>
  mraReviewRequests: Map<string, any>
  nineBoxOverrides: Map<string, any>
  continuousFeedback: Map<string, any>
  // Onboarding & Offboarding (V22)
  onboardingStages: Map<string, any>
  onboardingProfiles: Map<string, any>
  onboardingInstances: Map<string, any>
  onboardingTasks: Map<string, any[]>
  exitTypes: Map<string, any>
  exitReasons: Map<string, any>
  exitNotices: Map<string, any>
  clearanceTasks: Map<string, any[]>
  exitInterviews: Map<string, any>
}

export interface DemoPayGroup {
  id: string
  code: string
  name: string
  countryCode: string
  currency: string
  payFrequency: 'MONTHLY' | 'SEMI_MONTHLY' | 'BI_WEEKLY' | 'WEEKLY'
  standardDaysPerMonth: number
  isActive: boolean
}

export interface DemoPayPeriod {
  id: string
  payGroupId: string
  code: string
  startDate: string
  endDate: string
  paymentDate: string
  status: 'OPEN' | 'PROCESSING' | 'APPROVED' | 'CLOSED'
}

export interface DemoPayrollRun {
  id: string
  payGroupId: string
  payPeriodId: string
  runNumber: number
  status: 'DRAFT' | 'CALCULATING' | 'CALCULATED' | 'APPROVED' | 'COMMITTED'
  totalGross: number
  totalStatutoryEmployee: number
  totalStatutoryEmployer: number
  totalTax: number
  totalNet: number
  totalEmployees: number
  calculatedAt?: string
  approvedAt?: string
  committedAt?: string
}

export interface DemoPayrollResult {
  id: string
  payrollRunId: string
  employeeId: string
  employeeCode: string
  employeeName: string
  department: string
  designation: string
  currency: string
  basicSalary: number
  grossPay: number
  totalStatutoryEmployee: number
  totalStatutoryEmployer: number
  taxWithheld: number
  totalVoluntaryDeductions: number
  netPay: number
  paymentStatus: 'PENDING' | 'PROCESSING' | 'PAID' | 'FAILED'
  linesCount: number
}

export interface DemoPayrollResultLine {
  id: string
  payrollResultId: string
  lineCategory: 'EARNING' | 'STATUTORY_DEDUCTION' | 'EMPLOYER_CONTRIBUTION' | 'VOLUNTARY_DEDUCTION' | 'TAX'
  itemCode: string
  itemName: string
  amount: number
  isStatutory: boolean
  calculationTrace?: string
}

/* -------------------------------------------------------------------------- */
/* Seeding                                                                     */
/* -------------------------------------------------------------------------- */

/**
 * The four two-factor states, spread one per account.
 *
 * Only one user is signed in at a time, so a single starting state would make three of the four
 * branches in `Security.tsx` reachable only by walking through the others first — and the
 * "enrolment started but not finished" copy would be reachable only by closing the tab at exactly
 * the right moment.
 */
function seedMfa(account: DemoAccount): MfaState {
  switch (account.username) {
    case 'hr':
      // Enabled and nearly out of codes, so the "generate a new set" warning is on screen at once.
      return { enabled: true, enrolmentPending: false, recoveryCodesRemaining: 3 }
    case 'manager':
      return { enabled: false, enrolmentPending: true, recoveryCodesRemaining: 0 }
    default:
      return { enabled: false, enrolmentPending: false, recoveryCodesRemaining: 0 }
  }
}

/**
 * Notification settings, stored sparsely.
 *
 * Only what differs from the default is kept — in-app, push and email on, SMS off — because
 * persisting every switch a settings screen shows would be a row per user per event per channel,
 * 1.6 million of them in a ten-thousand-person tenant, every one restating a default. The clients
 * expand the sparse set back out; an empty list means "no opinion", not "everything off".
 */
function seedNotifications(account: DemoAccount): NotificationSettings {
  if (account.username !== 'admin') return { preferences: [] }
  return {
    preferences: [
      { eventKey: 'leave.requested', channel: 'EMAIL', enabled: false },
      { eventKey: 'payroll.payslip.published', channel: 'SMS', enabled: true },
      { eventKey: 'attendance.missing', channel: 'PUSH', enabled: true, digestMode: 'DIGEST' },
    ],
    quietHours: {
      startAt: '22:00:00',
      endAt: '07:00:00',
      timezone: 'Asia/Colombo',
      days: [1, 2, 3, 4, 5],
      enabled: true,
    },
  }
}

const INITIAL_TENANTS: DemoTenant[] = [
  {
    id: TENANT.id,
    code: TENANT.code,
    name: 'Demo Company',
    legalName: 'Demo Enterprises Private Limited',
    countryCode: 'LK',
    timezone: 'Asia/Colombo',
    defaultCurrency: 'LKR',
    locale: 'en',
    dataRegion: 'ap-south-1',
    isolationTier: 'SHARED',
    status: 'ACTIVE',
    subscriptionPlan: 'Enterprise',
    adminEmail: 'admin@example.com',
    modules: [
      { moduleKey: 'identity', enabled: true, updatedAt: '2026-01-01T00:00:00Z' },
      { moduleKey: 'employee', enabled: true, updatedAt: '2026-01-01T00:00:00Z' },
      { moduleKey: 'leave', enabled: true, updatedAt: '2026-01-01T00:00:00Z' },
      { moduleKey: 'attendance', enabled: true, updatedAt: '2026-01-01T00:00:00Z' },
      { moduleKey: 'payroll', enabled: true, updatedAt: '2026-01-01T00:00:00Z' },
      { moduleKey: 'documents', enabled: true, updatedAt: '2026-01-01T00:00:00Z' },
    ],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
  {
    id: 'tenant-acme-ph',
    code: 'acme-ph',
    name: 'Acme Global Philippines',
    legalName: 'Acme Global Business Services Inc.',
    countryCode: 'PH',
    timezone: 'Asia/Manila',
    defaultCurrency: 'PHP',
    locale: 'en',
    dataRegion: 'ap-southeast-1',
    isolationTier: 'SHARED',
    status: 'ACTIVE',
    subscriptionPlan: 'Growth',
    adminEmail: 'maria.santos@acmeglobal.ph',
    modules: [
      { moduleKey: 'identity', enabled: true, updatedAt: '2026-02-10T08:30:00Z' },
      { moduleKey: 'employee', enabled: true, updatedAt: '2026-02-10T08:30:00Z' },
      { moduleKey: 'leave', enabled: true, updatedAt: '2026-02-10T08:30:00Z' },
      { moduleKey: 'attendance', enabled: false, updatedAt: '2026-02-10T08:30:00Z' },
      { moduleKey: 'payroll', enabled: false, updatedAt: '2026-02-10T08:30:00Z' },
      { moduleKey: 'documents', enabled: true, updatedAt: '2026-02-10T08:30:00Z' },
    ],
    createdAt: '2026-02-10T08:30:00Z',
    updatedAt: '2026-02-10T08:30:00Z',
  },
  {
    id: 'tenant-apex-gulf',
    code: 'apex-gulf',
    name: 'Apex Logistics FZ-LLC',
    legalName: 'Apex Logistics Free Zone LLC',
    countryCode: 'AE',
    timezone: 'Asia/Dubai',
    defaultCurrency: 'AED',
    locale: 'en',
    dataRegion: 'me-central-1',
    isolationTier: 'DEDICATED_SCHEMA',
    status: 'ACTIVE',
    subscriptionPlan: 'Enterprise',
    adminEmail: 'tariq.mansoor@apexlogistics.ae',
    modules: [
      { moduleKey: 'identity', enabled: true, updatedAt: '2026-03-01T10:00:00Z' },
      { moduleKey: 'employee', enabled: true, updatedAt: '2026-03-01T10:00:00Z' },
      { moduleKey: 'leave', enabled: true, updatedAt: '2026-03-01T10:00:00Z' },
      { moduleKey: 'attendance', enabled: true, updatedAt: '2026-03-01T10:00:00Z' },
      { moduleKey: 'payroll', enabled: true, updatedAt: '2026-03-01T10:00:00Z' },
      { moduleKey: 'documents', enabled: true, updatedAt: '2026-03-01T10:00:00Z' },
    ],
    createdAt: '2026-03-01T10:00:00Z',
    updatedAt: '2026-03-01T10:00:00Z',
  },
  {
    id: 'tenant-cloudscale-sg',
    code: 'cloudscale-sg',
    name: 'CloudScale Technologies',
    legalName: 'CloudScale Technologies Pte. Ltd.',
    countryCode: 'SG',
    timezone: 'Asia/Singapore',
    defaultCurrency: 'SGD',
    locale: 'en',
    dataRegion: 'ap-southeast-1',
    isolationTier: 'SHARED',
    status: 'PROVISIONING',
    subscriptionPlan: 'Starter',
    adminEmail: 'devops@cloudscale.sg',
    modules: [
      { moduleKey: 'identity', enabled: true, updatedAt: '2026-08-25T14:15:00Z' },
      { moduleKey: 'employee', enabled: true, updatedAt: '2026-08-25T14:15:00Z' },
      { moduleKey: 'leave', enabled: false, updatedAt: '2026-08-25T14:15:00Z' },
      { moduleKey: 'attendance', enabled: false, updatedAt: '2026-08-25T14:15:00Z' },
      { moduleKey: 'payroll', enabled: false, updatedAt: '2026-08-25T14:15:00Z' },
      { moduleKey: 'documents', enabled: false, updatedAt: '2026-08-25T14:15:00Z' },
    ],
    createdAt: '2026-08-25T14:15:00Z',
    updatedAt: '2026-08-25T14:15:00Z',
  },
  {
    id: 'tenant-legacy-retail',
    code: 'legacy-retail',
    name: 'Legacy Retail Holdings',
    legalName: 'Legacy Retail Operations LLC',
    countryCode: 'US',
    timezone: 'America/New_York',
    defaultCurrency: 'USD',
    locale: 'en',
    dataRegion: 'us-east-1',
    isolationTier: 'SHARED',
    status: 'SUSPENDED',
    subscriptionPlan: 'Starter',
    adminEmail: 'billing@legacyretail.com',
    modules: [
      { moduleKey: 'identity', enabled: true, updatedAt: '2025-11-12T16:40:00Z' },
      { moduleKey: 'employee', enabled: false, updatedAt: '2025-11-12T16:40:00Z' },
      { moduleKey: 'leave', enabled: false, updatedAt: '2025-11-12T16:40:00Z' },
      { moduleKey: 'attendance', enabled: false, updatedAt: '2025-11-12T16:40:00Z' },
      { moduleKey: 'payroll', enabled: false, updatedAt: '2025-11-12T16:40:00Z' },
      { moduleKey: 'documents', enabled: false, updatedAt: '2025-11-12T16:40:00Z' },
    ],
    createdAt: '2025-11-12T16:40:00Z',
    updatedAt: '2026-07-01T09:00:00Z',
  },
]

export const PUBLIC_HOLIDAYS_2026: DemoPublicHoliday[] = [
  { date: '2026-01-03', name: 'Duruthu Full Moon Poya Day', isFullDay: true },
  { date: '2026-01-15', name: 'Tamil Thai Pongal Day', isFullDay: true },
  { date: '2026-02-01', name: 'Navam Full Moon Poya Day', isFullDay: true },
  { date: '2026-02-04', name: 'National Day (Independence Day)', isFullDay: true },
  { date: '2026-03-03', name: 'Medin Full Moon Poya Day', isFullDay: true },
  { date: '2026-03-11', name: 'Maha Shivaratri Day', isFullDay: true },
  { date: '2026-03-20', name: 'Id-Ul-Fitr (Ramazan Festival Day)', isFullDay: true },
  { date: '2026-04-01', name: 'Bak Full Moon Poya Day', isFullDay: true },
  { date: '2026-04-03', name: 'Good Friday', isFullDay: true },
  { date: '2026-04-13', name: 'Day prior to Sinhala & Tamil New Year', isFullDay: true },
  { date: '2026-04-14', name: 'Sinhala & Tamil New Year Day', isFullDay: true },
  { date: '2026-05-01', name: "May Day (International Workers' Day)", isFullDay: true },
  { date: '2026-05-02', name: 'Vesak Full Moon Poya Day', isFullDay: true },
  { date: '2026-05-03', name: 'Day following Vesak Full Moon Poya Day', isFullDay: true },
  { date: '2026-05-27', name: 'Id-Ul-Alha (Hadji Festival Day)', isFullDay: true },
  { date: '2026-05-31', name: 'Poson Full Moon Poya Day', isFullDay: true },
]

export function createWorld(): World {
  const world: World = {
    employees: buildWorkforce(),
    users: new Map(),
    roles: new Map(INITIAL_ROLES.map((role) => [role.id, { ...role }])),
    tenants: new Map(INITIAL_TENANTS.map((t) => [t.id, { ...t, modules: t.modules.map((m) => ({ ...m })) }])),
    devicesByUser: new Map(),
    mfaByUser: new Map(),
    notificationsByUser: new Map(),
    sessions: new Map(),
    refreshTokens: new Map(),
    spentRefreshTokens: new Map(),
    accessTokens: new Map(),
    leaveBalancesByUser: new Map(),
    leaveLedgerByUser: new Map(),
    leaveApplicationsByUser: new Map(),
    publicHolidays: [...PUBLIC_HOLIDAYS_2026],
    payGroups: new Map(),
    payPeriods: new Map(),
    payrollRuns: new Map(),
    payrollResultsByRun: new Map(),
    payrollLinesByResult: new Map(),
    shifts: new Map(),
    shiftSchedules: new Map(),
    rawPunches: [],
    dailyAttendance: new Map(),
    vacancies: new Map(),
    candidates: new Map(),
    applications: new Map(),
    interviews: new Map(),
    scorecards: new Map(),
    offers: new Map(),
    documentFolders: new Map(),
    companyDocuments: new Map(),
    documentVersions: new Map(),
    documentTemplates: new Map(),
    letterRequests: new Map(),
    signatureRequests: new Map(),
    signatureAuditLogs: new Map(),
    timesheetClients: new Map(),
    timesheetProjects: new Map(),
    timesheetActivities: new Map(),
    timesheets: new Map(),
    timesheetEntries: new Map(),
    goalCycles: new Map(),
    goals: new Map(),
    goalCheckIns: new Map(),
    competencyGroups: new Map(),
    competencies: new Map(),
    evaluationCycles: new Map(),
    appraisals: new Map(),
    mraReviewRequests: new Map(),
    nineBoxOverrides: new Map(),
    continuousFeedback: new Map(),
    onboardingStages: new Map(),
    onboardingProfiles: new Map(),
    onboardingInstances: new Map(),
    onboardingTasks: new Map(),
    exitTypes: new Map(),
    exitReasons: new Map(),
    exitNotices: new Map(),
    clearanceTasks: new Map(),
    exitInterviews: new Map(),
  }

  ACCOUNTS.forEach((account, index) => {
    world.devicesByUser.set(account.userId, seedDevicesFor(account, index))
    const mfa = seedMfa(account)
    world.mfaByUser.set(account.userId, mfa)
    world.notificationsByUser.set(account.userId, seedNotifications(account))

    const emp = Array.from(world.employees.values()).find(
      (e) => e.employeeCode === account.employeeCode,
    )
    const status: DemoUser['status'] =
      account.status === 'LOCKED'
        ? 'LOCKED'
        : mfa.enrolmentPending
          ? 'PENDING_MFA'
          : 'ACTIVE'

    world.users.set(account.userId, {
      id: account.userId,
      username: account.username,
      email: account.email,
      employeeCode: account.employeeCode,
      employeeName: emp?.displayName ?? emp?.firstName ?? account.username,
      role: account.role,
      status,
      mfaEnabled: mfa.enabled,
      createdAt: '2026-01-15T09:00:00Z',
      lastLoginAt: new Date(Date.now() - (index + 1) * 3600 * 1000 * 4).toISOString(),
    })

    // Seed realistic leave balances
    const balances: DemoLeaveBalance[] = [
      {
        leaveTypeId: 'lt-annual',
        leaveTypeCode: 'ANNUAL',
        leaveTypeName: 'Annual Leave',
        color: '#3B82F6',
        entitledDays: 14.0,
        accruedDays: 14.0,
        takenDays: 4.0,
        pendingDays: 1.0,
        availableDays: 9.0,
      },
      {
        leaveTypeId: 'lt-casual',
        leaveTypeCode: 'CASUAL',
        leaveTypeName: 'Casual Leave',
        color: '#10B981',
        entitledDays: 7.0,
        accruedDays: 7.0,
        takenDays: 2.0,
        pendingDays: 0.0,
        availableDays: 5.0,
      },
      {
        leaveTypeId: 'lt-sick',
        leaveTypeCode: 'SICK',
        leaveTypeName: 'Medical / Sick Leave',
        color: '#F59E0B',
        entitledDays: 14.0,
        accruedDays: 14.0,
        takenDays: 1.0,
        pendingDays: 0.0,
        availableDays: 13.0,
      },
      {
        leaveTypeId: 'lt-special',
        leaveTypeCode: 'SPECIAL',
        leaveTypeName: 'Special / Compensatory',
        color: '#8B5CF6',
        entitledDays: 5.0,
        accruedDays: 5.0,
        takenDays: 0.0,
        pendingDays: 0.0,
        availableDays: 5.0,
      },
    ]
    world.leaveBalancesByUser.set(account.userId, balances)

    // Seed ledger history
    const ledger: DemoLeaveLedgerEntry[] = [
      {
        id: `ledger-open-ann-${account.userId}`,
        employeeId: account.userId,
        date: '2026-01-01',
        leaveTypeId: 'lt-annual',
        leaveTypeCode: 'ANNUAL',
        leaveTypeName: 'Annual Leave',
        eventType: 'OPENING',
        daysCredited: 14.0,
        daysDebited: 0.0,
        balanceAfter: 14.0,
        notes: 'Statutory 2026 Annual Leave Entitlement',
      },
      {
        id: `ledger-open-cas-${account.userId}`,
        employeeId: account.userId,
        date: '2026-01-01',
        leaveTypeId: 'lt-casual',
        leaveTypeCode: 'CASUAL',
        leaveTypeName: 'Casual Leave',
        eventType: 'OPENING',
        daysCredited: 7.0,
        daysDebited: 0.0,
        balanceAfter: 7.0,
        notes: 'Statutory 2026 Casual Leave Entitlement',
      },
      {
        id: `ledger-open-sick-${account.userId}`,
        employeeId: account.userId,
        date: '2026-01-01',
        leaveTypeId: 'lt-sick',
        leaveTypeCode: 'SICK',
        leaveTypeName: 'Medical / Sick Leave',
        eventType: 'OPENING',
        daysCredited: 14.0,
        daysDebited: 0.0,
        balanceAfter: 14.0,
        notes: 'Company Medical Benefit Entitlement',
      },
      {
        id: `ledger-take-cas-${account.userId}`,
        employeeId: account.userId,
        date: '2026-01-20',
        leaveTypeId: 'lt-casual',
        leaveTypeCode: 'CASUAL',
        leaveTypeName: 'Casual Leave',
        eventType: 'TAKEN',
        daysCredited: 0.0,
        daysDebited: 2.0,
        balanceAfter: 5.0,
        referenceId: `LV-2026-001-${account.userId.slice(-4)}`,
        notes: 'Approved family commitment leave',
      },
      {
        id: `ledger-take-sick-${account.userId}`,
        employeeId: account.userId,
        date: '2026-02-05',
        leaveTypeId: 'lt-sick',
        leaveTypeCode: 'SICK',
        leaveTypeName: 'Medical / Sick Leave',
        eventType: 'TAKEN',
        daysCredited: 0.0,
        daysDebited: 1.0,
        balanceAfter: 13.0,
        referenceId: `LV-2026-002-${account.userId.slice(-4)}`,
        notes: 'Medical outpatient visit consultation',
      },
      {
        id: `ledger-take-ann-${account.userId}`,
        employeeId: account.userId,
        date: '2026-02-19',
        leaveTypeId: 'lt-annual',
        leaveTypeCode: 'ANNUAL',
        leaveTypeName: 'Annual Leave',
        eventType: 'TAKEN',
        daysCredited: 0.0,
        daysDebited: 4.0,
        balanceAfter: 10.0,
        referenceId: `LV-2026-003-${account.userId.slice(-4)}`,
        notes: 'Approved annual break (Feb 16-19)',
      },
    ]
    world.leaveLedgerByUser.set(account.userId, ledger)

    // Seed applications
    const applications: DemoLeaveApplication[] = [
      {
        id: `LV-2026-001-${account.userId.slice(-4)}`,
        employeeId: account.userId,
        employeeName: emp?.displayName ?? emp?.firstName ?? account.username,
        department: emp?.departmentId ?? 'Engineering',
        leaveTypeId: 'lt-casual',
        leaveTypeCode: 'CASUAL',
        leaveTypeName: 'Casual Leave',
        startDate: '2026-01-20',
        endDate: '2026-01-21',
        dayPortion: 'FULL_DAY',
        totalDays: 2.0,
        reason: 'Personal family commitment',
        status: 'APPROVED',
        submittedAt: '2026-01-14T08:30:00Z',
        approvedAt: '2026-01-15T10:00:00Z',
        days: [
          { date: '2026-01-20', dayOfWeek: 'Tuesday', isWorkingDay: true, isPublicHoliday: false, portion: 'FULL_DAY', hours: 8.0 },
          { date: '2026-01-21', dayOfWeek: 'Wednesday', isWorkingDay: true, isPublicHoliday: false, portion: 'FULL_DAY', hours: 8.0 },
        ],
      },
      {
        id: `LV-2026-002-${account.userId.slice(-4)}`,
        employeeId: account.userId,
        employeeName: emp?.displayName ?? emp?.firstName ?? account.username,
        department: emp?.departmentId ?? 'Engineering',
        leaveTypeId: 'lt-sick',
        leaveTypeCode: 'SICK',
        leaveTypeName: 'Medical / Sick Leave',
        startDate: '2026-02-05',
        endDate: '2026-02-05',
        dayPortion: 'FULL_DAY',
        totalDays: 1.0,
        reason: 'Medical outpatient appointment',
        status: 'APPROVED',
        submittedAt: '2026-02-04T17:20:00Z',
        approvedAt: '2026-02-05T08:15:00Z',
        days: [
          { date: '2026-02-05', dayOfWeek: 'Thursday', isWorkingDay: true, isPublicHoliday: false, portion: 'FULL_DAY', hours: 8.0 },
        ],
      },
      {
        id: `LV-2026-003-${account.userId.slice(-4)}`,
        employeeId: account.userId,
        employeeName: emp?.displayName ?? emp?.firstName ?? account.username,
        department: emp?.departmentId ?? 'Engineering',
        leaveTypeId: 'lt-annual',
        leaveTypeCode: 'ANNUAL',
        leaveTypeName: 'Annual Leave',
        startDate: '2026-02-16',
        endDate: '2026-02-19',
        dayPortion: 'FULL_DAY',
        totalDays: 4.0,
        reason: 'Annual family holiday break',
        status: 'APPROVED',
        submittedAt: '2026-02-01T09:00:00Z',
        approvedAt: '2026-02-02T11:30:00Z',
        days: [
          { date: '2026-02-16', dayOfWeek: 'Monday', isWorkingDay: true, isPublicHoliday: false, portion: 'FULL_DAY', hours: 8.0 },
          { date: '2026-02-17', dayOfWeek: 'Tuesday', isWorkingDay: true, isPublicHoliday: false, portion: 'FULL_DAY', hours: 8.0 },
          { date: '2026-02-18', dayOfWeek: 'Wednesday', isWorkingDay: true, isPublicHoliday: false, portion: 'FULL_DAY', hours: 8.0 },
          { date: '2026-02-19', dayOfWeek: 'Thursday', isWorkingDay: true, isPublicHoliday: false, portion: 'FULL_DAY', hours: 8.0 },
        ],
      },
      {
        id: `LV-2026-004-${account.userId.slice(-4)}`,
        employeeId: account.userId,
        employeeName: emp?.displayName ?? emp?.firstName ?? account.username,
        department: emp?.departmentId ?? 'Engineering',
        leaveTypeId: 'lt-annual',
        leaveTypeCode: 'ANNUAL',
        leaveTypeName: 'Annual Leave',
        startDate: '2026-03-23',
        endDate: '2026-03-23',
        dayPortion: 'FULL_DAY',
        totalDays: 1.0,
        reason: 'Personal administration errand',
        status: 'SUBMITTED',
        submittedAt: '2026-03-02T09:15:00Z',
        days: [
          { date: '2026-03-23', dayOfWeek: 'Monday', isWorkingDay: true, isPublicHoliday: false, portion: 'FULL_DAY', hours: 8.0 },
        ],
      },
    ]
    world.leaveApplicationsByUser.set(account.userId, applications)
  })

  // Seed team colleagues' leaves for Team Calendar display
  const teamColleaguesLeaves: DemoLeaveApplication[] = [
    {
      id: 'LV-TEAM-001',
      employeeId: 'emp-ruwan',
      employeeName: 'Ruwan Jayasuriya',
      department: 'Engineering',
      leaveTypeId: 'lt-annual',
      leaveTypeCode: 'ANNUAL',
      leaveTypeName: 'Annual Leave',
      startDate: '2026-03-27',
      endDate: '2026-03-27',
      dayPortion: 'FULL_DAY',
      totalDays: 1.0,
      reason: 'Long weekend leave',
      status: 'APPROVED',
      submittedAt: '2026-03-01T10:00:00Z',
      approvedAt: '2026-03-01T14:00:00Z',
      days: [
        { date: '2026-03-27', dayOfWeek: 'Friday', isWorkingDay: true, isPublicHoliday: false, portion: 'FULL_DAY', hours: 8.0 },
      ],
    },
    {
      id: 'LV-TEAM-002',
      employeeId: 'emp-dilani',
      employeeName: 'Dilani Perera',
      department: 'Engineering',
      leaveTypeId: 'lt-annual',
      leaveTypeCode: 'ANNUAL',
      leaveTypeName: 'Annual Leave',
      startDate: '2026-03-12',
      endDate: '2026-03-13',
      dayPortion: 'FULL_DAY',
      totalDays: 2.0,
      reason: 'Post-holiday family visit',
      status: 'APPROVED',
      submittedAt: '2026-03-02T11:00:00Z',
      approvedAt: '2026-03-03T09:00:00Z',
      days: [
        { date: '2026-03-12', dayOfWeek: 'Thursday', isWorkingDay: true, isPublicHoliday: false, portion: 'FULL_DAY', hours: 8.0 },
        { date: '2026-03-13', dayOfWeek: 'Friday', isWorkingDay: true, isPublicHoliday: false, portion: 'FULL_DAY', hours: 8.0 },
      ],
    },
    {
      id: 'LV-TEAM-003',
      employeeId: 'emp-thivanka',
      employeeName: 'Thivanka Rajapaksa',
      department: 'Engineering',
      leaveTypeId: 'lt-casual',
      leaveTypeCode: 'CASUAL',
      leaveTypeName: 'Casual Leave',
      startDate: '2026-03-13',
      endDate: '2026-03-13',
      dayPortion: 'FIRST_HALF',
      totalDays: 0.5,
      reason: 'Home maintenance morning',
      status: 'APPROVED',
      submittedAt: '2026-03-04T08:30:00Z',
      approvedAt: '2026-03-04T12:00:00Z',
      days: [
        { date: '2026-03-13', dayOfWeek: 'Friday', isWorkingDay: true, isPublicHoliday: false, portion: 'FIRST_HALF', hours: 4.0 },
      ],
    },
    {
      id: 'LV-TEAM-004',
      employeeId: 'emp-anusha',
      employeeName: 'Anusha Sivakumar',
      department: 'Finance',
      leaveTypeId: 'lt-casual',
      leaveTypeCode: 'CASUAL',
      leaveTypeName: 'Casual Leave',
      startDate: '2026-03-18',
      endDate: '2026-03-19',
      dayPortion: 'FULL_DAY',
      totalDays: 2.0,
      reason: 'Personal urgent errand',
      status: 'APPROVED',
      submittedAt: '2026-03-05T09:00:00Z',
      approvedAt: '2026-03-05T15:00:00Z',
      days: [
        { date: '2026-03-18', dayOfWeek: 'Wednesday', isWorkingDay: true, isPublicHoliday: false, portion: 'FULL_DAY', hours: 8.0 },
        { date: '2026-03-19', dayOfWeek: 'Thursday', isWorkingDay: true, isPublicHoliday: false, portion: 'FULL_DAY', hours: 8.0 },
      ],
    },
  ]
  world.leaveApplicationsByUser.set('team-colleagues', teamColleaguesLeaves)

  seedPayrollData(world)
  seedAttendance(world)
  seedRecruitmentData(world)
  seedDocumentsData(world)
  seedTimesheetsData(world)
  seedPerformanceData(world)
  seedOnboardingData(world)

  return world
}

function calculateApitTax(taxablePay: number): { tax: number; trace: string } {
  if (taxablePay <= 100000) {
    return { tax: 0, trace: 'Taxable pay <= LKR 100,000 (Exempt under APIT)' }
  }

  let remaining = taxablePay - 100000
  let tax = 0
  const slabs = [
    { cap: 50000, rate: 0.06 },
    { cap: 50000, rate: 0.12 },
    { cap: 50000, rate: 0.18 },
    { cap: 50000, rate: 0.24 },
    { cap: 50000, rate: 0.30 },
  ]
  const traceParts: string[] = ['Exempt: LKR 100,000 @ 0%']

  for (const slab of slabs) {
    if (remaining <= 0) break
    const chunk = Math.min(remaining, slab.cap)
    const slabTax = chunk * slab.rate
    tax += slabTax
    traceParts.push(`LKR ${chunk.toLocaleString()} @ ${(slab.rate * 100).toFixed(0)}% = LKR ${slabTax.toFixed(2)}`)
    remaining -= chunk
  }

  if (remaining > 0) {
    const topTax = remaining * 0.36
    tax += topTax
    traceParts.push(`Excess LKR ${remaining.toLocaleString()} @ 36% = LKR ${topTax.toFixed(2)}`)
  }

  return { tax: Math.round(tax * 100) / 100, trace: traceParts.join(' | ') }
}

function seedPayrollData(world: World): void {
  const lkPayGroup: DemoPayGroup = {
    id: 'pg-lk-monthly',
    code: 'PG-LK-EXEC',
    name: 'Sri Lanka Monthly Executive & Engineering',
    countryCode: 'LK',
    currency: 'LKR',
    payFrequency: 'MONTHLY',
    standardDaysPerMonth: 22.0,
    isActive: true,
  }
  const phPayGroup: DemoPayGroup = {
    id: 'pg-ph-semimonthly',
    code: 'PG-PH-OPS',
    name: 'Philippines Semi-Monthly Operations',
    countryCode: 'PH',
    currency: 'PHP',
    payFrequency: 'SEMI_MONTHLY',
    standardDaysPerMonth: 11.0,
    isActive: true,
  }
  world.payGroups.set(lkPayGroup.id, lkPayGroup)
  world.payGroups.set(phPayGroup.id, phPayGroup)

  const periodFeb: DemoPayPeriod = {
    id: 'period-2026-m02',
    payGroupId: lkPayGroup.id,
    code: '2026-M02',
    startDate: '2026-02-01',
    endDate: '2026-02-28',
    paymentDate: '2026-02-25',
    status: 'CLOSED',
  }
  const periodMar: DemoPayPeriod = {
    id: 'period-2026-m03',
    payGroupId: lkPayGroup.id,
    code: '2026-M03',
    startDate: '2026-03-01',
    endDate: '2026-03-31',
    paymentDate: '2026-03-25',
    status: 'OPEN',
  }
  world.payPeriods.set(periodFeb.id, periodFeb)
  world.payPeriods.set(periodMar.id, periodMar)

  // Build payroll results for February (Committed) and March (Calculated)
  for (const isMarch of [false, true]) {
    const period = isMarch ? periodMar : periodFeb
    const runId = isMarch ? 'run-2026-m03' : 'run-2026-m02'
    const results: DemoPayrollResult[] = []

    let sumGross = 0
    let sumStatEmployee = 0
    let sumStatEmployer = 0
    let sumTax = 0
    let sumNet = 0

    const employeesList = Array.from(world.employees.values())

    for (const emp of employeesList) {
      // Basic salary assignment by designation code
      let basic = 135000
      let fixedAllowance = 25000
      let bonus = 0

      switch (emp.designationId) {
        case 'CEO':
          basic = 650000
          fixedAllowance = 50000
          if (isMarch) bonus = 50000
          break
        case 'ENG_MGR':
          basic = 380000
          fixedAllowance = 35000
          break
        case 'HR_MGR':
          basic = 320000
          fixedAllowance = 30000
          break
        case 'SLS_MGR':
        case 'OPS_MGR':
          basic = 290000
          fixedAllowance = 25000
          break
        case 'SNR_SE':
          basic = 240000
          fixedAllowance = 25000
          break
        case 'SE':
          if (emp.employeeCode === 'E004') {
            // Kasun Fernando: 180k basic + 20k allowance, in March receives 45,000 performance bonus (+22.5% variance)
            basic = 180000
            fixedAllowance = 20000
            if (isMarch) bonus = 45000
          } else {
            basic = 170000
            fixedAllowance = 20000
          }
          break
        case 'QA':
          basic = 150000
          fixedAllowance = 20000
          break
        case 'ACC':
          basic = 160000
          fixedAllowance = 20000
          break
        default:
          basic = 120000
          fixedAllowance = 15000
          break
      }

      const gross = basic + fixedAllowance + bonus
      const epfBase = basic + fixedAllowance
      const epf8 = Math.round(epfBase * 0.08 * 100) / 100
      const epf12 = Math.round(epfBase * 0.12 * 100) / 100
      const etf3 = Math.round(epfBase * 0.03 * 100) / 100

      const { tax: apitTax, trace: taxTrace } = calculateApitTax(gross)
      const voluntary = 2500.0
      const net = Math.round((gross - epf8 - apitTax - voluntary) * 100) / 100

      sumGross += gross
      sumStatEmployee += epf8
      sumStatEmployer += epf12 + etf3
      sumTax += apitTax
      sumNet += net

      const empCode = emp.employeeCode ?? 'E000'
      const resultId = `res-${runId}-${empCode}`
      const result: DemoPayrollResult = {
        id: resultId,
        payrollRunId: runId,
        employeeId: emp.id,
        employeeCode: empCode,
        employeeName: emp.displayName ?? `${emp.firstName} ${emp.lastName}`,
        department: emp.departmentId ?? 'Engineering',
        designation: emp.designationId ?? 'Staff',
        currency: 'LKR',
        basicSalary: basic,
        grossPay: gross,
        totalStatutoryEmployee: epf8,
        totalStatutoryEmployer: epf12 + etf3,
        taxWithheld: apitTax,
        totalVoluntaryDeductions: voluntary,
        netPay: net,
        paymentStatus: isMarch ? 'PENDING' : 'PAID',
        linesCount: bonus > 0 ? 8 : 7,
      }
      results.push(result)

      // Itemized Result Lines with calculation traces
      const lines: DemoPayrollResultLine[] = [
        {
          id: `line-${resultId}-basic`,
          payrollResultId: resultId,
          lineCategory: 'EARNING',
          itemCode: 'BASIC',
          itemName: 'Basic Monthly Salary',
          amount: basic,
          isStatutory: false,
          calculationTrace: `Contracted salary grade allocation for ${emp.designationId}`,
        },
        {
          id: `line-${resultId}-fixed-allowance`,
          payrollResultId: resultId,
          lineCategory: 'EARNING',
          itemCode: 'FIXED_ALLOWANCE',
          itemName: 'Cost of Living & Fixed Allowance',
          amount: fixedAllowance,
          isStatutory: false,
          calculationTrace: 'Standard executive fixed monthly allowance',
        },
      ]

      if (bonus > 0) {
        lines.push({
          id: `line-${resultId}-bonus`,
          payrollResultId: resultId,
          lineCategory: 'EARNING',
          itemCode: 'PERF_BONUS',
          itemName: 'Performance & Project Milestone Bonus',
          amount: bonus,
          isStatutory: false,
          calculationTrace: 'Management approved Q1 milestone accomplishment incentive',
        })
      }

      lines.push(
        {
          id: `line-${resultId}-epf8`,
          payrollResultId: resultId,
          lineCategory: 'STATUTORY_DEDUCTION',
          itemCode: 'EPF_EE_08',
          itemName: 'Employees\' Provident Fund (EPF 8%)',
          amount: epf8,
          isStatutory: true,
          calculationTrace: `Base: LKR ${epfBase.toLocaleString()} * 8.00% = LKR ${epf8.toFixed(2)}`,
        },
        {
          id: `line-${resultId}-tax`,
          payrollResultId: resultId,
          lineCategory: 'TAX',
          itemCode: 'APIT_TAX',
          itemName: 'Advance Personal Income Tax (APIT)',
          amount: apitTax,
          isStatutory: true,
          calculationTrace: taxTrace,
        },
        {
          id: `line-${resultId}-welfare`,
          payrollResultId: resultId,
          lineCategory: 'VOLUNTARY_DEDUCTION',
          itemCode: 'STAFF_WELFARE',
          itemName: 'Employee Welfare Society Membership',
          amount: voluntary,
          isStatutory: false,
          calculationTrace: 'Voluntary monthly welfare payroll deduction authorization',
        },
        {
          id: `line-${resultId}-epf12`,
          payrollResultId: resultId,
          lineCategory: 'EMPLOYER_CONTRIBUTION',
          itemCode: 'EPF_ER_12',
          itemName: 'Employer EPF Contribution (12%)',
          amount: epf12,
          isStatutory: true,
          calculationTrace: `Base: LKR ${epfBase.toLocaleString()} * 12.00% = LKR ${epf12.toFixed(2)}`,
        },
        {
          id: `line-${resultId}-etf3`,
          payrollResultId: resultId,
          lineCategory: 'EMPLOYER_CONTRIBUTION',
          itemCode: 'ETF_ER_03',
          itemName: 'Employees\' Trust Fund (ETF 3%)',
          amount: etf3,
          isStatutory: true,
          calculationTrace: `Base: LKR ${epfBase.toLocaleString()} * 3.00% = LKR ${etf3.toFixed(2)}`,
        },
      )

      world.payrollLinesByResult.set(resultId, lines)
    }

    world.payrollResultsByRun.set(runId, results)

    const payrollRun: DemoPayrollRun = {
      id: runId,
      payGroupId: lkPayGroup.id,
      payPeriodId: period.id,
      runNumber: 1,
      status: isMarch ? 'CALCULATED' : 'COMMITTED',
      totalGross: Math.round(sumGross * 100) / 100,
      totalStatutoryEmployee: Math.round(sumStatEmployee * 100) / 100,
      totalStatutoryEmployer: Math.round(sumStatEmployer * 100) / 100,
      totalTax: Math.round(sumTax * 100) / 100,
      totalNet: Math.round(sumNet * 100) / 100,
      totalEmployees: employeesList.length,
      calculatedAt: isMarch ? '2026-03-24T16:00:00Z' : '2026-02-23T15:30:00Z',
      approvedAt: isMarch ? undefined : '2026-02-24T10:00:00Z',
      committedAt: isMarch ? undefined : '2026-02-25T09:00:00Z',
    }
    world.payrollRuns.set(runId, payrollRun)
  }
}

function seedAttendance(world: World): void {
  const shifts: DemoShift[] = [
    {
      id: 'shift-gen',
      code: 'GEN_0830',
      name: 'General Day (08:30 - 17:30)',
      shiftType: 'FIXED',
      startTime: '08:30',
      endTime: '17:30',
      breakMinutes: 60,
      workingMinutes: 480,
      graceInMinutes: 15,
      graceOutMinutes: 10,
      halfDayThresholdMinutes: 240,
      otEligible: true,
      otStartAfterMinutes: 480,
      minOtMinutes: 30,
      color: '#2563eb',
      isActive: true,
    },
    {
      id: 'shift-early',
      code: 'EARLY_0600',
      name: 'Early Morning (06:00 - 14:30)',
      shiftType: 'FIXED',
      startTime: '06:00',
      endTime: '14:30',
      breakMinutes: 30,
      workingMinutes: 480,
      graceInMinutes: 10,
      graceOutMinutes: 10,
      halfDayThresholdMinutes: 240,
      otEligible: true,
      otStartAfterMinutes: 480,
      minOtMinutes: 30,
      color: '#059669',
      isActive: true,
    },
    {
      id: 'shift-night',
      code: 'NIGHT_2000',
      name: 'Night Operations (20:00 - 05:00)',
      shiftType: 'NIGHT',
      startTime: '20:00',
      endTime: '05:00',
      breakMinutes: 60,
      workingMinutes: 480,
      graceInMinutes: 15,
      graceOutMinutes: 15,
      halfDayThresholdMinutes: 240,
      otEligible: true,
      otStartAfterMinutes: 480,
      minOtMinutes: 30,
      color: '#7c3aed',
      isActive: true,
    },
    {
      id: 'shift-flex',
      code: 'FLEX_CORE',
      name: 'Flexible Core (10:00 - 19:00)',
      shiftType: 'FLEXIBLE',
      startTime: '10:00',
      endTime: '19:00',
      breakMinutes: 60,
      workingMinutes: 480,
      graceInMinutes: 30,
      graceOutMinutes: 15,
      halfDayThresholdMinutes: 240,
      otEligible: true,
      otStartAfterMinutes: 480,
      minOtMinutes: 30,
      color: '#d97706',
      isActive: true,
    },
  ]

  shifts.forEach((s) => world.shifts.set(s.id, s))

  // 2. Rosters / Shift Schedules for 2026-03-09 to 2026-03-15
  const weekDates = [
    { date: '2026-03-09', isWeekend: false, isHoliday: false },
    { date: '2026-03-10', isWeekend: false, isHoliday: false },
    { date: '2026-03-11', isWeekend: false, isHoliday: true, holidayName: 'Maha Shivaratri Day' },
    { date: '2026-03-12', isWeekend: false, isHoliday: false },
    { date: '2026-03-13', isWeekend: false, isHoliday: false },
    { date: '2026-03-14', isWeekend: true, isHoliday: false },
    { date: '2026-03-15', isWeekend: true, isHoliday: false },
  ]

  const employees = Array.from(world.employees.values())
  employees.forEach((emp, empIdx) => {
    const defaultShiftId = empIdx % 3 === 0 ? 'shift-flex' : empIdx % 3 === 1 ? 'shift-gen' : 'shift-early'
    const defaultShift = (world.shifts.get(defaultShiftId) ?? shifts[0])!

    const empSchedules: DemoShiftSchedule[] = []
    const empName = emp.displayName ?? emp.firstName ?? 'Employee'
    const dept = emp.departmentId ?? 'Engineering'

    weekDates.forEach((wd) => {
      const isRest = wd.isWeekend
      const isHol = wd.isHoliday
      const item: DemoShiftSchedule = {
        id: `sched-${emp.id}-${wd.date}`,
        employeeId: emp.id,
        employeeCode: emp.employeeCode ?? 'EMP',
        employeeName: empName,
        department: dept,
        workDate: wd.date,
        shiftId: isRest ? undefined : defaultShift.id,
        shiftCode: isRest ? undefined : defaultShift.code,
        shiftName: isRest ? undefined : defaultShift.name,
        shiftColor: isRest ? '#64748b' : defaultShift.color,
        startTime: isRest ? undefined : defaultShift.startTime,
        endTime: isRest ? undefined : defaultShift.endTime,
        isRestDay: isRest,
        isHoliday: isHol,
        holidayName: wd.holidayName,
        source: 'ROSTER',
      }
      empSchedules.push(item)
    })
    world.shiftSchedules.set(emp.id, empSchedules)
  })

  // 3. Raw Clock Punches (2026-03-09)
  const punches: DemoRawPunch[] = [
    // E001 (Nimali Wickramasinghe) - On time arrival, evening OT
    {
      id: 'punch-001',
      employeeId: 'de300000-0001-4000-8000-000000000001',
      employeeCode: 'E001',
      employeeName: 'Nimali Wickramasinghe',
      department: 'Executive Board',
      punchedAt: '2026-03-09T08:25:00Z',
      punchType: 'IN',
      source: 'BIOMETRIC_DEVICE',
      deviceId: 'BIO-MAIN-01',
      locationName: 'HQ Main Entrance',
      geofenceStatus: 'INSIDE',
      isMockLocation: false,
      clientIdempotencyKey: 'BIO-001-20260309-IN',
      recordedOffline: false,
      syncedAt: '2026-03-09T08:25:04Z',
    },
    {
      id: 'punch-002',
      employeeId: 'de300000-0001-4000-8000-000000000001',
      employeeCode: 'E001',
      employeeName: 'Nimali Wickramasinghe',
      department: 'Executive Board',
      punchedAt: '2026-03-09T18:30:00Z',
      punchType: 'OUT',
      source: 'BIOMETRIC_DEVICE',
      deviceId: 'BIO-MAIN-01',
      locationName: 'HQ Main Entrance',
      geofenceStatus: 'INSIDE',
      isMockLocation: false,
      clientIdempotencyKey: 'BIO-001-20260309-OUT',
      recordedOffline: false,
      syncedAt: '2026-03-09T18:30:02Z',
    },

    // E002 (Kasun Fernando) - Grace period arrival (08:40) -> 0 late minutes
    {
      id: 'punch-003',
      employeeId: 'de300000-0001-4000-8000-000000000002',
      employeeCode: 'E002',
      employeeName: 'Kasun Fernando',
      department: 'Engineering',
      punchedAt: '2026-03-09T08:40:00Z',
      punchType: 'IN',
      source: 'BIOMETRIC_DEVICE',
      deviceId: 'BIO-ENG-02',
      locationName: 'Engineering Floor Turnstile',
      geofenceStatus: 'INSIDE',
      isMockLocation: false,
      clientIdempotencyKey: 'BIO-002-20260309-IN',
      recordedOffline: false,
      syncedAt: '2026-03-09T08:40:05Z',
    },
    {
      id: 'punch-004',
      employeeId: 'de300000-0001-4000-8000-000000000002',
      employeeCode: 'E002',
      employeeName: 'Kasun Fernando',
      department: 'Engineering',
      punchedAt: '2026-03-09T17:35:00Z',
      punchType: 'OUT',
      source: 'BIOMETRIC_DEVICE',
      deviceId: 'BIO-ENG-02',
      locationName: 'Engineering Floor Turnstile',
      geofenceStatus: 'INSIDE',
      isMockLocation: false,
      clientIdempotencyKey: 'BIO-002-20260309-OUT',
      recordedOffline: false,
      syncedAt: '2026-03-09T17:35:03Z',
    },

    // E004 (Dilani Perera) - Late arrival (09:12) -> 42 late minutes
    {
      id: 'punch-005',
      employeeId: 'de300000-0001-4000-8000-000000000004',
      employeeCode: 'E004',
      employeeName: 'Dilani Perera',
      department: 'Engineering',
      punchedAt: '2026-03-09T09:12:00Z',
      punchType: 'IN',
      source: 'BIOMETRIC_DEVICE',
      deviceId: 'BIO-ENG-02',
      locationName: 'Engineering Floor Turnstile',
      geofenceStatus: 'INSIDE',
      isMockLocation: false,
      clientIdempotencyKey: 'BIO-004-20260309-IN',
      recordedOffline: false,
      syncedAt: '2026-03-09T09:12:08Z',
    },
    {
      id: 'punch-006',
      employeeId: 'de300000-0001-4000-8000-000000000004',
      employeeCode: 'E004',
      employeeName: 'Dilani Perera',
      department: 'Engineering',
      punchedAt: '2026-03-09T17:30:00Z',
      punchType: 'OUT',
      source: 'BIOMETRIC_DEVICE',
      deviceId: 'BIO-ENG-02',
      locationName: 'Engineering Floor Turnstile',
      geofenceStatus: 'INSIDE',
      isMockLocation: false,
      clientIdempotencyKey: 'BIO-004-20260309-OUT',
      recordedOffline: false,
      syncedAt: '2026-03-09T17:30:01Z',
    },

    // E005 (Ruwan Silva) - Heavy weekday overtime (until 20:00) -> 165m OT (1.5x)
    {
      id: 'punch-007',
      employeeId: 'de300000-0001-4000-8000-000000000005',
      employeeCode: 'E005',
      employeeName: 'Ruwan Silva',
      department: 'Engineering',
      punchedAt: '2026-03-09T08:15:00Z',
      punchType: 'IN',
      source: 'BIOMETRIC_DEVICE',
      deviceId: 'BIO-TECH-03',
      locationName: 'Tech Lab Entrance',
      geofenceStatus: 'INSIDE',
      isMockLocation: false,
      clientIdempotencyKey: 'BIO-005-20260309-IN',
      recordedOffline: false,
      syncedAt: '2026-03-09T08:15:02Z',
    },
    {
      id: 'punch-008',
      employeeId: 'de300000-0001-4000-8000-000000000005',
      employeeCode: 'E005',
      employeeName: 'Ruwan Silva',
      department: 'Engineering',
      punchedAt: '2026-03-09T20:00:00Z',
      punchType: 'OUT',
      source: 'BIOMETRIC_DEVICE',
      deviceId: 'BIO-TECH-03',
      locationName: 'Tech Lab Entrance',
      geofenceStatus: 'INSIDE',
      isMockLocation: false,
      clientIdempotencyKey: 'BIO-005-20260309-OUT',
      recordedOffline: false,
      syncedAt: '2026-03-09T20:00:04Z',
    },

    // E006 (Anushka Bandara) - Mobile Punch with Mock Location GPS spoofing flag!
    {
      id: 'punch-009',
      employeeId: 'de300000-0001-4000-8000-000000000006',
      employeeCode: 'E006',
      employeeName: 'Anushka Bandara',
      department: 'Quality Assurance',
      punchedAt: '2026-03-09T08:30:00Z',
      punchType: 'IN',
      source: 'MOBILE_APP',
      deviceId: 'MOB-ANDROID-QA',
      locationName: 'Field Inspection GPS (Colombo 03)',
      geoLat: 6.9271,
      geoLng: 79.8612,
      geoAccuracyM: 12.5,
      geofenceStatus: 'INSIDE',
      isMockLocation: true,
      clientIdempotencyKey: 'MOB-006-20260309-IN',
      recordedOffline: false,
      syncedAt: '2026-03-09T08:30:08Z',
    },
    {
      id: 'punch-010',
      employeeId: 'de300000-0001-4000-8000-000000000006',
      employeeCode: 'E006',
      employeeName: 'Anushka Bandara',
      department: 'Quality Assurance',
      punchedAt: '2026-03-09T17:30:00Z',
      punchType: 'OUT',
      source: 'MOBILE_APP',
      deviceId: 'MOB-ANDROID-QA',
      locationName: 'Field Inspection GPS (Colombo 03)',
      geoLat: 6.9272,
      geoLng: 79.8615,
      geoAccuracyM: 10.0,
      geofenceStatus: 'INSIDE',
      isMockLocation: true,
      clientIdempotencyKey: 'MOB-006-20260309-OUT',
      recordedOffline: false,
      syncedAt: '2026-03-09T17:30:05Z',
    },

    // E007 (Suresh Mendis) - Mobile punch Outside Designated Branch Geofence!
    {
      id: 'punch-011',
      employeeId: 'de300000-0001-4000-8000-000000000007',
      employeeCode: 'E007',
      employeeName: 'Suresh Mendis',
      department: 'Operations',
      punchedAt: '2026-03-09T08:35:00Z',
      punchType: 'IN',
      source: 'MOBILE_APP',
      deviceId: 'MOB-IOS-OPS',
      locationName: 'Colombo Suburban Boundary (1.8km outside)',
      geoLat: 6.9114,
      geoLng: 79.8821,
      geoAccuracyM: 18.0,
      geofenceStatus: 'OUTSIDE',
      isMockLocation: false,
      clientIdempotencyKey: 'MOB-007-20260309-IN',
      recordedOffline: false,
      syncedAt: '2026-03-09T08:35:10Z',
    },
    {
      id: 'punch-012',
      employeeId: 'de300000-0001-4000-8000-000000000007',
      employeeCode: 'E007',
      employeeName: 'Suresh Mendis',
      department: 'Operations',
      punchedAt: '2026-03-09T17:30:00Z',
      punchType: 'OUT',
      source: 'MOBILE_APP',
      deviceId: 'MOB-IOS-OPS',
      locationName: 'HQ Operations Annex',
      geoLat: 6.9271,
      geoLng: 79.8612,
      geoAccuracyM: 8.0,
      geofenceStatus: 'INSIDE',
      isMockLocation: false,
      clientIdempotencyKey: 'MOB-007-20260309-OUT',
      recordedOffline: false,
      syncedAt: '2026-03-09T17:30:06Z',
    },

    // E008 (Kavindi Jayawardena) - Early Departure (16:30 vs 17:30)
    {
      id: 'punch-013',
      employeeId: 'de300000-0001-4000-8000-000000000008',
      employeeCode: 'E008',
      employeeName: 'Kavindi Jayawardena',
      department: 'Human Resources',
      punchedAt: '2026-03-09T08:20:00Z',
      punchType: 'IN',
      source: 'BIOMETRIC_DEVICE',
      deviceId: 'BIO-HR-04',
      locationName: 'HR Administration Wing',
      geofenceStatus: 'INSIDE',
      isMockLocation: false,
      clientIdempotencyKey: 'BIO-008-20260309-IN',
      recordedOffline: false,
      syncedAt: '2026-03-09T08:20:02Z',
    },
    {
      id: 'punch-014',
      employeeId: 'de300000-0001-4000-8000-000000000008',
      employeeCode: 'E008',
      employeeName: 'Kavindi Jayawardena',
      department: 'Human Resources',
      punchedAt: '2026-03-09T16:30:00Z',
      punchType: 'OUT',
      source: 'BIOMETRIC_DEVICE',
      deviceId: 'BIO-HR-04',
      locationName: 'HR Administration Wing',
      geofenceStatus: 'INSIDE',
      isMockLocation: false,
      clientIdempotencyKey: 'BIO-008-20260309-OUT',
      recordedOffline: false,
      syncedAt: '2026-03-09T16:30:03Z',
    },

    // E009 (Tharindu Peiris) - Missing Checkout Punch!
    {
      id: 'punch-015',
      employeeId: 'de300000-0001-4000-8000-000000000009',
      employeeCode: 'E009',
      employeeName: 'Tharindu Peiris',
      department: 'Customer Support',
      punchedAt: '2026-03-09T08:30:00Z',
      punchType: 'IN',
      source: 'BIOMETRIC_DEVICE',
      deviceId: 'BIO-CS-05',
      locationName: 'Support Service Center',
      geofenceStatus: 'INSIDE',
      isMockLocation: false,
      clientIdempotencyKey: 'BIO-009-20260309-IN',
      recordedOffline: false,
      syncedAt: '2026-03-09T08:30:04Z',
    },
  ]

  world.rawPunches.push(...punches)

  // 4. Daily Attendance Records for 2026-03-09
  const dailyRecords: DemoDailyAttendance[] = [
    {
      id: 'att-20260309-e001',
      employeeId: 'de300000-0001-4000-8000-000000000001',
      employeeCode: 'E001',
      employeeName: 'Nimali Wickramasinghe',
      department: 'Executive Board',
      workDate: '2026-03-09',
      shiftId: 'shift-gen',
      shiftCode: 'GEN_0830',
      shiftName: 'General Day (08:30 - 17:30)',
      firstInAt: '2026-03-09T08:25:00Z',
      lastOutAt: '2026-03-09T18:30:00Z',
      grossDurationMinutes: 605,
      breakMinutes: 60,
      netWorkedMinutes: 545,
      lateMinutes: 0,
      earlyLeaveMinutes: 0,
      overtimeMinutesNormal: 65,
      overtimeMinutesRestDay: 0,
      overtimeMinutesHoliday: 0,
      dayStatus: 'PRESENT',
      anomalyFlags: [],
      calculationTrace: [
        '✅ On-time check-in: Arrived at 08:25 (within 15m grace window).',
        '✅ Shift completion: Checked out at 18:30.',
        '⏱️ Duration: 605 gross minutes - 60 break minutes = 545 net worked minutes.',
        '⚡ Overtime: 65 minutes exceeded shift duration (>= 30m threshold). Credited at 1.5x regular rate.',
      ].join('\n'),
      computedAt: '2026-03-09T19:00:00Z',
    },
    {
      id: 'att-20260309-e002',
      employeeId: 'de300000-0001-4000-8000-000000000002',
      employeeCode: 'E002',
      employeeName: 'Kasun Fernando',
      department: 'Engineering',
      workDate: '2026-03-09',
      shiftId: 'shift-gen',
      shiftCode: 'GEN_0830',
      shiftName: 'General Day (08:30 - 17:30)',
      firstInAt: '2026-03-09T08:40:00Z',
      lastOutAt: '2026-03-09T17:35:00Z',
      grossDurationMinutes: 535,
      breakMinutes: 60,
      netWorkedMinutes: 475,
      lateMinutes: 0,
      earlyLeaveMinutes: 0,
      overtimeMinutesNormal: 0,
      overtimeMinutesRestDay: 0,
      overtimeMinutesHoliday: 0,
      dayStatus: 'PRESENT',
      anomalyFlags: [],
      calculationTrace: [
        '✅ On-time check-in: Arrived at 08:40 (within 15m grace window, limit 08:45). Late minutes: 0.',
        '✅ Shift completion: Checked out at 17:35.',
        '⏱️ Duration: 535 gross minutes - 60 break minutes = 475 net worked minutes.',
        '✨ Status: PRESENT (core shift completed with grace allowance).',
      ].join('\n'),
      computedAt: '2026-03-09T18:00:00Z',
    },
    {
      id: 'att-20260309-e004',
      employeeId: 'de300000-0001-4000-8000-000000000004',
      employeeCode: 'E004',
      employeeName: 'Dilani Perera',
      department: 'Engineering',
      workDate: '2026-03-09',
      shiftId: 'shift-gen',
      shiftCode: 'GEN_0830',
      shiftName: 'General Day (08:30 - 17:30)',
      firstInAt: '2026-03-09T09:12:00Z',
      lastOutAt: '2026-03-09T17:30:00Z',
      grossDurationMinutes: 498,
      breakMinutes: 60,
      netWorkedMinutes: 438,
      lateMinutes: 42,
      earlyLeaveMinutes: 0,
      overtimeMinutesNormal: 0,
      overtimeMinutesRestDay: 0,
      overtimeMinutesHoliday: 0,
      dayStatus: 'HALF_DAY',
      anomalyFlags: ['LATE_ARRIVAL'],
      calculationTrace: [
        '⏰ Late check-in: Arrived at 09:12 (Shift start: 08:30, Grace limit: 08:45). Late duration: 42 minutes.',
        '✅ Shift completion: Checked out at 17:30.',
        '⏱️ Duration: 498 gross minutes - 60 break minutes = 438 net worked minutes.',
        '⚠️ Status: HALF_DAY (< 480m shift requirement, >= 240m threshold). Late penalty applied.',
      ].join('\n'),
      computedAt: '2026-03-09T18:00:00Z',
    },
    {
      id: 'att-20260309-e005',
      employeeId: 'de300000-0001-4000-8000-000000000005',
      employeeCode: 'E005',
      employeeName: 'Ruwan Silva',
      department: 'Engineering',
      workDate: '2026-03-09',
      shiftId: 'shift-gen',
      shiftCode: 'GEN_0830',
      shiftName: 'General Day (08:30 - 17:30)',
      firstInAt: '2026-03-09T08:15:00Z',
      lastOutAt: '2026-03-09T20:00:00Z',
      grossDurationMinutes: 705,
      breakMinutes: 60,
      netWorkedMinutes: 645,
      lateMinutes: 0,
      earlyLeaveMinutes: 0,
      overtimeMinutesNormal: 165,
      overtimeMinutesRestDay: 0,
      overtimeMinutesHoliday: 0,
      dayStatus: 'PRESENT',
      anomalyFlags: [],
      calculationTrace: [
        '✅ On-time check-in: Arrived at 08:15.',
        '✅ Shift completion: Checked out at 20:00.',
        '⏱️ Duration: 705 gross minutes - 60 break minutes = 645 net worked minutes.',
        '⚡ Overtime: 165 minutes (2.75 hours) exceeded shift duration (>= 30m threshold). Credited at 1.5x regular rate.',
      ].join('\n'),
      computedAt: '2026-03-09T20:30:00Z',
    },
    {
      id: 'att-20260309-e006',
      employeeId: 'de300000-0001-4000-8000-000000000006',
      employeeCode: 'E006',
      employeeName: 'Anushka Bandara',
      department: 'Quality Assurance',
      workDate: '2026-03-09',
      shiftId: 'shift-gen',
      shiftCode: 'GEN_0830',
      shiftName: 'General Day (08:30 - 17:30)',
      firstInAt: '2026-03-09T08:30:00Z',
      lastOutAt: '2026-03-09T17:30:00Z',
      grossDurationMinutes: 540,
      breakMinutes: 60,
      netWorkedMinutes: 480,
      lateMinutes: 0,
      earlyLeaveMinutes: 0,
      overtimeMinutesNormal: 0,
      overtimeMinutesRestDay: 0,
      overtimeMinutesHoliday: 0,
      dayStatus: 'PRESENT',
      anomalyFlags: ['MOCK_LOCATION'],
      calculationTrace: [
        '⚠️ Mock/spoofed GPS provider flagged on mobile clock events.',
        '✅ Shift completion: 480 net worked minutes recorded.',
        '🛡️ Security action: Flagged for supervisor biometric audit.',
      ].join('\n'),
      computedAt: '2026-03-09T18:00:00Z',
    },
    {
      id: 'att-20260309-e007',
      employeeId: 'de300000-0001-4000-8000-000000000007',
      employeeCode: 'E007',
      employeeName: 'Suresh Mendis',
      department: 'Operations',
      workDate: '2026-03-09',
      shiftId: 'shift-gen',
      shiftCode: 'GEN_0830',
      shiftName: 'General Day (08:30 - 17:30)',
      firstInAt: '2026-03-09T08:35:00Z',
      lastOutAt: '2026-03-09T17:30:00Z',
      grossDurationMinutes: 535,
      breakMinutes: 60,
      netWorkedMinutes: 475,
      lateMinutes: 0,
      earlyLeaveMinutes: 0,
      overtimeMinutesNormal: 0,
      overtimeMinutesRestDay: 0,
      overtimeMinutesHoliday: 0,
      dayStatus: 'PRESENT',
      anomalyFlags: ['GEOFENCE_VIOLATION'],
      calculationTrace: [
        '⚠️ Punch recorded outside designated branch geofence boundary (1.8km breach).',
        '✅ Shift completion: 475 net worked minutes.',
      ].join('\n'),
      computedAt: '2026-03-09T18:00:00Z',
    },
    {
      id: 'att-20260309-e008',
      employeeId: 'de300000-0001-4000-8000-000000000008',
      employeeCode: 'E008',
      employeeName: 'Kavindi Jayawardena',
      department: 'Human Resources',
      workDate: '2026-03-09',
      shiftId: 'shift-gen',
      shiftCode: 'GEN_0830',
      shiftName: 'General Day (08:30 - 17:30)',
      firstInAt: '2026-03-09T08:20:00Z',
      lastOutAt: '2026-03-09T16:30:00Z',
      grossDurationMinutes: 490,
      breakMinutes: 60,
      netWorkedMinutes: 430,
      lateMinutes: 0,
      earlyLeaveMinutes: 60,
      overtimeMinutesNormal: 0,
      overtimeMinutesRestDay: 0,
      overtimeMinutesHoliday: 0,
      dayStatus: 'HALF_DAY',
      anomalyFlags: ['EARLY_LEAVE'],
      calculationTrace: [
        '✅ On-time check-in: Arrived at 08:20.',
        '🚪 Early departure: Checked out at 16:30 (Shift end: 17:30, Grace limit: 17:20). Early duration: 60 minutes.',
        '⏱️ Duration: 490 gross minutes - 60 break minutes = 430 net worked minutes.',
        '⚠️ Status: HALF_DAY (< 480m working minutes requirement).',
      ].join('\n'),
      computedAt: '2026-03-09T17:00:00Z',
    },
    {
      id: 'att-20260309-e009',
      employeeId: 'de300000-0001-4000-8000-000000000009',
      employeeCode: 'E009',
      employeeName: 'Tharindu Peiris',
      department: 'Customer Support',
      workDate: '2026-03-09',
      shiftId: 'shift-gen',
      shiftCode: 'GEN_0830',
      shiftName: 'General Day (08:30 - 17:30)',
      firstInAt: '2026-03-09T08:30:00Z',
      lastOutAt: undefined,
      grossDurationMinutes: 480,
      breakMinutes: 60,
      netWorkedMinutes: 420,
      lateMinutes: 0,
      earlyLeaveMinutes: 0,
      overtimeMinutesNormal: 0,
      overtimeMinutesRestDay: 0,
      overtimeMinutesHoliday: 0,
      dayStatus: 'PRESENT',
      anomalyFlags: ['MISSING_OUT_PUNCH'],
      calculationTrace: [
        '⚠️ Single clock event detected (08:30). Missing paired checkout punch.',
        'ℹ️ Single punch: defaulting to scheduled shift duration pending regularization.',
      ].join('\n'),
      computedAt: '2026-03-09T18:00:00Z',
    },
    {
      id: 'att-20260309-e003',
      employeeId: 'de300000-0001-4000-8000-000000000003',
      employeeCode: 'E003',
      employeeName: 'Mohamed Rizwan',
      department: 'Engineering',
      workDate: '2026-03-09',
      shiftId: 'shift-gen',
      shiftCode: 'GEN_0830',
      shiftName: 'General Day (08:30 - 17:30)',
      firstInAt: undefined,
      lastOutAt: undefined,
      grossDurationMinutes: 0,
      breakMinutes: 0,
      netWorkedMinutes: 0,
      lateMinutes: 0,
      earlyLeaveMinutes: 0,
      overtimeMinutesNormal: 0,
      overtimeMinutesRestDay: 0,
      overtimeMinutesHoliday: 0,
      dayStatus: 'ABSENT',
      anomalyFlags: ['UNEXPLAINED_ABSENCE'],
      calculationTrace: [
        '❌ No clock events recorded for scheduled working day. Marked ABSENT.',
        '📉 Loss of pay (LOP) deduction candidate for payroll batch.',
      ].join('\n'),
      computedAt: '2026-03-09T18:00:00Z',
    },
  ]

  world.dailyAttendance.set('2026-03-09', dailyRecords)
}

/* -------------------------------------------------------------------------- */
/* Recruitment, ATS & Pipeline Seed Data (V21)                                */
/* -------------------------------------------------------------------------- */

function seedRecruitmentData(world: World): void {
  const vacanciesList = [
    {
      id: 'vac-01',
      jobCode: 'ENG-2026-001',
      title: 'Senior Kotlin / Spring Monolith Engineer',
      departmentId: 'dept-eng',
      departmentName: 'Engineering',
      location: 'Colombo, Sri Lanka (Hybrid)',
      employmentType: 'FULL_TIME',
      experienceLevel: 'SENIOR_LEVEL' as const,
      minSalary: 380000,
      maxSalary: 500000,
      currency: 'LKR',
      description: 'Lead backend micro-modules, high-performance RLS multi-tenant database access and event streaming.',
      requirements: '5+ years Kotlin/Java, PostgreSQL Row-Level Security, Spring Modulith, Docker.',
      openPositions: 3,
      filledPositions: 1,
      targetHireDate: '2026-04-15',
      closingDate: '2026-04-30',
      status: 'OPEN' as const,
      applicantCount: 8,
    },
    {
      id: 'vac-02',
      jobCode: 'MOB-2026-002',
      title: 'Lead Mobile Engineer (Android Jetpack Compose)',
      departmentId: 'dept-eng',
      departmentName: 'Engineering',
      location: 'Colombo, Sri Lanka (On-site)',
      employmentType: 'FULL_TIME',
      experienceLevel: 'LEAD' as const,
      minSalary: 450000,
      maxSalary: 600000,
      currency: 'LKR',
      description: 'Architect offline-first Room database sync, biometric keystore encryption, and Material 3 design system.',
      requirements: 'Extensive Kotlin Coroutines/Flow, Room persistence, Retrofit, Jetpack Compose.',
      openPositions: 2,
      filledPositions: 0,
      targetHireDate: '2026-04-01',
      closingDate: '2026-04-20',
      status: 'OPEN' as const,
      applicantCount: 5,
    },
    {
      id: 'vac-03',
      jobCode: 'HR-2026-003',
      title: 'HR Business Partner & Talent Operations',
      departmentId: 'dept-hr',
      departmentName: 'Human Resources',
      location: 'Colombo, Sri Lanka (Hybrid)',
      employmentType: 'FULL_TIME',
      experienceLevel: 'MID_LEVEL' as const,
      minSalary: 220000,
      maxSalary: 300000,
      currency: 'LKR',
      description: 'Manage talent sourcing, structured candidate interviews, employee onboarding lifecycles, and grievance resolution.',
      requirements: '3+ years corporate HR, ATS proficiency, Sri Lankan labour law knowledge.',
      openPositions: 1,
      filledPositions: 0,
      targetHireDate: '2026-04-10',
      closingDate: '2026-04-25',
      status: 'OPEN' as const,
      applicantCount: 4,
    },
    {
      id: 'vac-04',
      jobCode: 'PRD-2026-004',
      title: 'Lead Product Designer (Design Tokens & Systems)',
      departmentId: 'dept-prod',
      departmentName: 'Product',
      location: 'Colombo, Sri Lanka (Hybrid)',
      employmentType: 'FULL_TIME',
      experienceLevel: 'SENIOR_LEVEL' as const,
      minSalary: 320000,
      maxSalary: 420000,
      currency: 'LKR',
      description: 'Unify mobile & web design tokens, accessibility standards (WCAG 2.2 AAA), and Figma component libraries.',
      requirements: 'Figma Mastery, cross-platform token generation (Android XML, Compose, SwiftUI, CSS).',
      openPositions: 1,
      filledPositions: 1,
      targetHireDate: '2026-03-01',
      closingDate: '2026-03-15',
      status: 'FILLED' as const,
      applicantCount: 12,
    },
  ]

  for (const v of vacanciesList) world.vacancies.set(v.id, v)

  const candidatesList = [
    {
      id: 'cand-01',
      firstName: 'Samantha',
      lastName: 'De Silva',
      email: 'samantha.desilva@example.com',
      phone: '+94 77 123 4567',
      currentCompany: 'Virtusa PolarS',
      currentTitle: 'Senior Software Engineer',
      yearsOfExperience: 6.5,
      skills: ['Kotlin', 'Spring Boot', 'PostgreSQL', 'Kafka', 'Docker'],
      rating: 4.8,
      notes: 'Exceptional background in high-throughput payment and banking architectures.',
    },
    {
      id: 'cand-02',
      firstName: 'Dinesh',
      lastName: 'Jayawardena',
      email: 'dinesh.j@example.com',
      phone: '+94 71 987 6543',
      currentCompany: 'WSO2 Sri Lanka',
      currentTitle: 'Lead Mobile Architect',
      yearsOfExperience: 8.0,
      skills: ['Kotlin', 'Jetpack Compose', 'Room', 'Biometrics', 'Swift'],
      rating: 4.9,
      notes: 'Top-tier mobile architect. Handled offline caching for enterprise mobility products.',
    },
    {
      id: 'cand-03',
      firstName: 'Fathima',
      lastName: 'Rameez',
      email: 'fathima.r@example.com',
      phone: '+94 76 555 4321',
      currentCompany: 'Sysco LABS',
      currentTitle: 'HR Specialist',
      yearsOfExperience: 4.5,
      skills: ['Talent Sourcing', 'Onboarding Workflows', 'Performance Cycles', 'Labour Law'],
      rating: 4.6,
      notes: 'Strong candidate for HR Operations; demonstrated solid understanding of statutory compliance.',
    },
    {
      id: 'cand-04',
      firstName: 'Rukshan',
      lastName: 'Bandara',
      email: 'rukshan.b@example.com',
      phone: '+94 70 333 2211',
      currentCompany: 'IFS R&D',
      currentTitle: 'Software Engineer',
      yearsOfExperience: 3.0,
      skills: ['Java', 'Spring Boot', 'REST APIs', 'SQL'],
      rating: 3.9,
      notes: 'Good core fundamentals; needs additional mentorship on distributed transaction boundaries.',
    },
  ]

  for (const c of candidatesList) world.candidates.set(c.id, c)

  const applicationsList = [
    {
      id: 'app-01',
      applicationNumber: 'APP-2026-0081',
      vacancyId: 'vac-01',
      vacancyTitle: 'Senior Kotlin / Spring Monolith Engineer',
      candidateId: 'cand-01',
      candidateName: 'Samantha De Silva',
      candidateEmail: 'samantha.desilva@example.com',
      stage: 'FINAL_INTERVIEW' as const,
      status: 'ACTIVE' as const,
      rating: 4.8,
      source: 'LINKEDIN',
      appliedDate: '2026-02-28',
    },
    {
      id: 'app-02',
      applicationNumber: 'APP-2026-0082',
      vacancyId: 'vac-02',
      vacancyTitle: 'Lead Mobile Engineer (Android Jetpack Compose)',
      candidateId: 'cand-02',
      candidateName: 'Dinesh Jayawardena',
      candidateEmail: 'dinesh.j@example.com',
      stage: 'OFFER_EXTENDED' as const,
      status: 'ACTIVE' as const,
      rating: 4.9,
      source: 'DIRECT_REFERRAL',
      appliedDate: '2026-02-20',
    },
    {
      id: 'app-03',
      applicationNumber: 'APP-2026-0083',
      vacancyId: 'vac-03',
      vacancyTitle: 'HR Business Partner & Talent Operations',
      candidateId: 'cand-03',
      candidateName: 'Fathima Rameez',
      candidateEmail: 'fathima.r@example.com',
      stage: 'INTERVIEW_ROUND_2' as const,
      status: 'ACTIVE' as const,
      rating: 4.6,
      source: 'CAREERS_PORTAL',
      appliedDate: '2026-03-02',
    },
    {
      id: 'app-04',
      applicationNumber: 'APP-2026-0084',
      vacancyId: 'vac-01',
      vacancyTitle: 'Senior Kotlin / Spring Monolith Engineer',
      candidateId: 'cand-04',
      candidateName: 'Rukshan Bandara',
      candidateEmail: 'rukshan.b@example.com',
      stage: 'TECHNICAL_ASSESSMENT' as const,
      status: 'ACTIVE' as const,
      rating: 3.9,
      source: 'CAREERS_PORTAL',
      appliedDate: '2026-03-05',
    },
  ]

  for (const a of applicationsList) world.applications.set(a.id, a)

  const interviewsList = [
    {
      id: 'int-01',
      applicationId: 'app-01',
      candidateName: 'Samantha De Silva',
      vacancyTitle: 'Senior Kotlin / Spring Monolith Engineer',
      interviewRound: 2,
      title: 'System Architecture & Database Partitioning Round',
      interviewType: 'TECHNICAL' as const,
      scheduledStart: '2026-03-12T10:00:00Z',
      scheduledEnd: '2026-03-12T11:30:00Z',
      locationOrLink: 'HQ Conf Room 4A / Google Meet',
      meetingLink: 'https://meet.google.com/acme-eng-arch',
      status: 'COMPLETED' as const,
      notes: 'Focus on multi-tenant RLS guarantees, cursor pagination, and Spring Modulith event boundaries.',
      panelMembers: [
        { employeeId: 'de300000-0001-4000-8000-000000000001', name: 'Nimali Wickramasinghe', isLead: true },
        { employeeId: 'de300000-0001-4000-8000-000000000002', name: 'Kasun Fernando', isLead: false },
      ],
    },
    {
      id: 'int-02',
      applicationId: 'app-03',
      candidateName: 'Fathima Rameez',
      vacancyTitle: 'HR Business Partner & Talent Operations',
      interviewRound: 2,
      title: 'Employee Relations & Conflict Resolution Panel',
      interviewType: 'HR' as const,
      scheduledStart: '2026-03-13T14:30:00Z',
      scheduledEnd: '2026-03-13T15:30:00Z',
      locationOrLink: 'Executive Boardroom',
      meetingLink: 'https://meet.google.com/acme-hr-ops',
      status: 'SCHEDULED' as const,
      notes: 'Assess grievance handling, disciplinary procedures, and employee exit interviews.',
      panelMembers: [
        { employeeId: 'de300000-0001-4000-8000-000000000001', name: 'Nimali Wickramasinghe', isLead: true },
      ],
    },
  ]

  for (const i of interviewsList) world.interviews.set(i.id, i)

  world.scorecards.set('int-01', [
    {
      id: 'sc-01',
      interviewId: 'int-01',
      interviewerEmployeeId: 'de300000-0001-4000-8000-000000000002',
      interviewerName: 'Kasun Fernando',
      overallRecommendation: 'STRONG_HIRE',
      technicalSkillRating: 5.0,
      communicationRating: 4.5,
      problemSolvingRating: 5.0,
      culturalFitRating: 4.8,
      strengths: 'Deep mastery of PostgreSQL RLS policies, index design, and virtual thread concurrency.',
      weaknesses: 'Minor familiarity with Swift/SPM on client generators, but eager to learn.',
      summaryNotes: 'Outstanding candidate. Strong recommendation to proceed to offer extension.',
      submittedAt: '2026-03-12T12:00:00Z',
    },
  ])

  world.offers.set('app-02', {
    id: 'off-01',
    applicationId: 'app-02',
    candidateName: 'Dinesh Jayawardena',
    vacancyTitle: 'Lead Mobile Engineer (Android Jetpack Compose)',
    offerNumber: 'OFF-2026-0042',
    basicSalary: 520000,
    allowances: 45000,
    currency: 'LKR',
    joiningDate: '2026-04-15',
    expiryDate: '2026-03-25',
    status: 'EXTENDED',
    terms: 'Annual performance bonus up to 2 months basic, complete medical insurance for family, gym membership.',
  })
}

/* -------------------------------------------------------------------------- */
/* Document Management, Templates & Digital Signatures Seed Data (V23)        */
/* -------------------------------------------------------------------------- */

function seedDocumentsData(world: World): void {
  const folders = [
    {
      id: 'f-policies',
      folderName: 'Company Policies & SOPs',
      path: '/Company Policies',
      accessScope: 'PUBLIC' as const,
      isSystem: true,
      documentCount: 3,
    },
    {
      id: 'f-contracts',
      folderName: 'Executive & Employment Agreements',
      path: '/Employment Agreements',
      accessScope: 'CONFIDENTIAL' as const,
      isSystem: true,
      documentCount: 2,
    },
    {
      id: 'f-letters',
      folderName: 'Official HR Verification Letters',
      path: '/HR Letters',
      accessScope: 'ROLE_RESTRICTED' as const,
      isSystem: true,
      documentCount: 4,
    },
    {
      id: 'f-vault',
      folderName: 'Employee Credential Vault',
      path: '/Employee Vault',
      accessScope: 'ROLE_RESTRICTED' as const,
      isSystem: true,
      documentCount: 6,
    },
  ]

  for (const f of folders) world.documentFolders.set(f.id, f)

  const docs = [
    {
      id: 'doc-01',
      folderId: 'f-policies',
      folderName: 'Company Policies & SOPs',
      title: 'Acme Global Code of Conduct & Business Ethics 2026',
      description: 'Comprehensive guidelines on confidentiality, anti-corruption, conflict of interest, and IT security standards.',
      documentCategory: 'POLICY' as const,
      currentVersionNumber: 2,
      fileName: 'Acme_Code_of_Conduct_2026_v2.pdf',
      fileSizeBytes: 1420500,
      mimeType: 'application/pdf',
      storageKey: 's3://hr-documents/policies/code-of-conduct-v2.pdf',
      status: 'PUBLISHED' as const,
      isConfidential: false,
      createdByName: 'Amanda De Silva (HR Lead)',
      createdAt: '2026-01-10T09:00:00Z',
    },
    {
      id: 'doc-02',
      folderId: 'f-policies',
      folderName: 'Company Policies & SOPs',
      title: 'Hybrid Workplace, Geofence Attendance & Information Security SOP',
      description: 'Policy governing remote clock-in, VPN requirements, customer data encryption and clean desk rules.',
      documentCategory: 'POLICY' as const,
      currentVersionNumber: 1,
      fileName: 'Remote_Security_SOP_2026.pdf',
      fileSizeBytes: 875200,
      mimeType: 'application/pdf',
      storageKey: 's3://hr-documents/policies/remote-security-sop.pdf',
      status: 'PUBLISHED' as const,
      isConfidential: false,
      createdByName: 'Nimali Wickramasinghe',
      createdAt: '2026-01-15T11:30:00Z',
    },
    {
      id: 'doc-03',
      folderId: 'f-contracts',
      folderName: 'Executive & Employment Agreements',
      title: 'Standard Senior Engineering Employment Contract Template',
      description: 'Master legal template containing standard IP assignment, non-compete, and confidentiality covenants.',
      documentCategory: 'CONTRACT' as const,
      currentVersionNumber: 3,
      fileName: 'Engineering_Contract_Master_v3.pdf',
      fileSizeBytes: 540300,
      mimeType: 'application/pdf',
      storageKey: 's3://hr-documents/contracts/eng-contract-master-v3.pdf',
      status: 'PUBLISHED' as const,
      isConfidential: true,
      createdByName: 'Legal Counsel / Nimali Wickramasinghe',
      createdAt: '2026-02-01T14:00:00Z',
    },
    {
      id: 'doc-04',
      folderId: 'f-policies',
      folderName: 'Company Policies & SOPs',
      title: 'Group Medical, Surgical & Hospitalization Benefits Schedule',
      description: 'Schedule of limits for inpatient, outpatient, optical, and dental claims under Ceylinco Insurance.',
      documentCategory: 'GENERAL' as const,
      currentVersionNumber: 1,
      fileName: 'Medical_Insurance_Schedule_2026.pdf',
      fileSizeBytes: 2150000,
      mimeType: 'application/pdf',
      storageKey: 's3://hr-documents/policies/medical-schedule-2026.pdf',
      status: 'PUBLISHED' as const,
      isConfidential: false,
      createdByName: 'Amanda De Silva',
      createdAt: '2026-01-05T08:00:00Z',
    },
  ]

  for (const d of docs) world.companyDocuments.set(d.id, d)

  world.documentVersions.set('doc-01', [
    {
      id: 'ver-01-2',
      documentId: 'doc-01',
      versionNumber: 2,
      fileName: 'Acme_Code_of_Conduct_2026_v2.pdf',
      fileSizeBytes: 1420500,
      changelog: 'Updated Section 8 with generative AI usage guidelines and customer IP protocols.',
      uploadedByName: 'Amanda De Silva',
      createdAt: '2026-01-10T09:00:00Z',
    },
    {
      id: 'ver-01-1',
      documentId: 'doc-01',
      versionNumber: 1,
      fileName: 'Acme_Code_of_Conduct_2025_v1.pdf',
      fileSizeBytes: 1380000,
      changelog: 'Initial baseline approval by board of directors.',
      uploadedByName: 'Nimali Wickramasinghe',
      createdAt: '2025-01-02T10:00:00Z',
    },
  ])

  world.documentVersions.set('doc-03', [
    {
      id: 'ver-03-3',
      documentId: 'doc-03',
      versionNumber: 3,
      fileName: 'Engineering_Contract_Master_v3.pdf',
      fileSizeBytes: 540300,
      changelog: 'Incorporated digital e-signature clause under Electronic Transactions Act.',
      uploadedByName: 'Nimali Wickramasinghe',
      createdAt: '2026-02-01T14:00:00Z',
    },
  ])

  const templates = [
    {
      id: 'tmpl-01',
      templateCode: 'EMP_VERIFY_LETTER',
      title: 'Certificate of Employment & Service Verification',
      category: 'HR_LETTER' as const,
      contentTemplate: `To Whom It May Concern,

This is to certify that {{employee_name}} (Employee Number: {{employee_code}}) is an active full-time employee with Acme Corp holding the designation of {{designation}} in the {{department}} department, having joined our services on {{join_date}}.

This confirmation is issued upon the employee's request for the specific purpose of {{purpose}}.

Yours faithfully,
Acme Corp Human Resources Department`,
      placeholders: ['employee_name', 'employee_code', 'designation', 'department', 'join_date', 'purpose'],
      requiresSignature: true,
      isActive: true,
    },
    {
      id: 'tmpl-02',
      templateCode: 'SALARY_CONFIRM_LETTER',
      title: 'Salary & Remuneration Confirmation Letter (Bank / Visa)',
      category: 'HR_LETTER' as const,
      contentTemplate: `Dear Sir / Madam,

RE: CONFIRMATION OF REMUNERATION - {{employee_name}}

We confirm that {{employee_name}} is currently employed as {{designation}} at Acme Corp. The current monthly compensation structure is as follows:

• Basic Monthly Salary: {{currency}} {{basic_salary}}
• Fixed Allowances: {{currency}} {{allowances}}
• Net Monthly Approximate: {{currency}} {{net_salary}}

Certified true and correct for {{purpose}}.

Yours sincerely,
Nimali Wickramasinghe, Managing Director`,
      placeholders: ['employee_name', 'designation', 'currency', 'basic_salary', 'allowances', 'net_salary', 'purpose'],
      requiresSignature: true,
      isActive: true,
    },
  ]

  for (const t of templates) world.documentTemplates.set(t.id, t)

  const letterRequests = [
    {
      id: 'lreq-01',
      requestNumber: 'LR-2026-0019',
      employeeId: 'de300000-0001-4000-8000-000000000002',
      employeeName: 'Kasun Fernando',
      templateId: 'tmpl-01',
      templateTitle: 'Certificate of Employment & Service Verification',
      purpose: 'Housing mortgage facility application with Commercial Bank of Ceylon',
      requiredDate: '2026-03-18',
      status: 'PENDING' as const,
      requestedAt: '2026-03-09T08:30:00Z',
    },
    {
      id: 'lreq-02',
      requestNumber: 'LR-2026-0018',
      employeeId: 'de300000-0001-4000-8000-000000000001',
      employeeName: 'Nimali Wickramasinghe',
      templateId: 'tmpl-02',
      templateTitle: 'Salary & Remuneration Confirmation Letter (Bank / Visa)',
      purpose: 'Schengen Business Delegation Visa Application',
      requiredDate: '2026-03-14',
      status: 'APPROVED' as const,
      requestedAt: '2026-03-04T10:00:00Z',
      approvedAt: '2026-03-05T09:15:00Z',
      generatedDocumentId: 'doc-gen-018',
    },
  ]

  for (const lr of letterRequests) world.letterRequests.set(lr.id, lr)

  const signatureRequests = [
    {
      id: 'sig-01',
      documentId: 'doc-03',
      documentTitle: 'Standard Senior Engineering Employment Contract Template',
      title: 'Executive Non-Disclosure & IP Assignment Sign-off (Q1 2026)',
      workflowType: 'PARALLEL' as const,
      status: 'PARTIALLY_SIGNED' as const,
      dueDate: '2026-03-25',
      requestedByName: 'Amanda De Silva',
      createdAt: '2026-03-08T09:00:00Z',
      signers: [
        {
          id: 'sgn-01',
          requestId: 'sig-01',
          signerEmployeeId: 'de300000-0001-4000-8000-000000000002',
          signerName: 'Kasun Fernando',
          signerEmail: 'kasun.fernando@acme.test',
          signingOrder: 1,
          status: 'SIGNED' as const,
          signatureMethod: 'DRAWN' as const,
          signedAt: '2026-03-08T14:22:15Z',
        },
        {
          id: 'sgn-02',
          requestId: 'sig-01',
          signerEmployeeId: 'de300000-0001-4000-8000-000000000001',
          signerName: 'Nimali Wickramasinghe',
          signerEmail: 'admin@acme.test',
          signingOrder: 2,
          status: 'PENDING' as const,
        },
      ],
    },
    {
      id: 'sig-02',
      documentId: 'doc-01',
      documentTitle: 'Acme Global Code of Conduct & Business Ethics 2026',
      title: 'Annual Ethics Affirmation & Compliance Sign-off',
      workflowType: 'PARALLEL' as const,
      status: 'PENDING' as const,
      dueDate: '2026-03-31',
      requestedByName: 'HR Compliance Office',
      createdAt: '2026-03-05T10:00:00Z',
      signers: [
        {
          id: 'sgn-03',
          requestId: 'sig-02',
          signerEmployeeId: 'de300000-0001-4000-8000-000000000003',
          signerName: 'Mohamed Rizwan',
          signerEmail: 'mohamed.rizwan@acme.test',
          signingOrder: 1,
          status: 'PENDING' as const,
        },
        {
          id: 'sgn-04',
          requestId: 'sig-02',
          signerEmployeeId: 'de300000-0001-4000-8000-000000000004',
          signerName: 'Dilani Perera',
          signerEmail: 'dilani.perera@acme.test',
          signingOrder: 2,
          status: 'PENDING' as const,
        },
      ],
    },
  ]

  for (const sr of signatureRequests) world.signatureRequests.set(sr.id, sr)

  world.signatureAuditLogs.set('sig-01', [
    {
      id: 'log-01',
      requestId: 'sig-01',
      eventType: 'CREATED',
      actorName: 'Amanda De Silva',
      ipAddress: '192.168.1.10',
      documentHashSha256: '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08',
      details: { signersCount: 2, workflow: 'PARALLEL' },
      createdAt: '2026-03-08T09:00:00Z',
    },
    {
      id: 'log-02',
      requestId: 'sig-01',
      eventType: 'VIEWED',
      actorName: 'Kasun Fernando',
      ipAddress: '192.168.1.45',
      documentHashSha256: '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08',
      createdAt: '2026-03-08T14:15:02Z',
    },
    {
      id: 'log-03',
      requestId: 'sig-01',
      eventType: 'SIGNED',
      actorName: 'Kasun Fernando',
      ipAddress: '192.168.1.45',
      documentHashSha256: '9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08',
      details: { method: 'DRAWN', timestampUtc: '2026-03-08T14:22:15Z' },
      createdAt: '2026-03-08T14:22:15Z',
    },
  ])
}

/* -------------------------------------------------------------------------- */
/* Timesheets, Activities & Project Billing Seed Data (V25)                   */
/* -------------------------------------------------------------------------- */

function seedTimesheetsData(world: World): void {
  const clients = [
    {
      id: 'cl-01',
      clientCode: 'CL-ACME-US',
      name: 'Acme North America Inc.',
      currency: 'USD',
      contactEmail: 'jmiller@acme-us.test',
      contactPerson: 'John Miller, VP Engineering',
      isActive: true,
    },
    {
      id: 'cl-02',
      clientCode: 'CL-BARCLAYS',
      name: 'Barclays Capital Services UK',
      currency: 'GBP',
      contactEmail: 's.jenkins@barclays.test',
      contactPerson: 'Sarah Jenkins, Delivery Lead',
      isActive: true,
    },
    {
      id: 'cl-03',
      clientCode: 'CL-SINGTEL',
      name: 'Singtel Digital Enterprise Pte.',
      currency: 'SGD',
      contactEmail: 'ken.tan@singtel.test',
      contactPerson: 'Ken Tan, Technology Director',
      isActive: true,
    },
  ]

  for (const c of clients) world.timesheetClients.set(c.id, c)

  const projects = [
    {
      id: 'proj-01',
      clientId: 'cl-01',
      clientName: 'Acme North America Inc.',
      projectCode: 'PRJ-HR-MOB',
      name: 'Enterprise HR Mobile Platform 2.0',
      description: 'Native Android and iOS mobile suites matching and exceeding PeoplesHR capabilities.',
      startDate: '2026-01-15',
      endDate: '2026-07-31',
      budgetAmount: 185000,
      budgetHours: 1200,
      isBillable: true,
      status: 'ACTIVE' as const,
    },
    {
      id: 'proj-02',
      clientId: 'cl-02',
      clientName: 'Barclays Capital Services UK',
      projectCode: 'PRJ-RISK-API',
      name: 'Real-Time Credit Risk Calculation Gateway',
      description: 'Distributed event processing pipeline for streaming credit exposure scoring.',
      startDate: '2026-02-01',
      endDate: '2026-08-31',
      budgetAmount: 240000,
      budgetHours: 1600,
      isBillable: true,
      status: 'ACTIVE' as const,
    },
    {
      id: 'proj-03',
      clientId: 'cl-01',
      clientName: 'Acme North America Inc.',
      projectCode: 'PRJ-INT-TOOL',
      name: 'Internal Developer Tools & CI/CD Pipelines',
      description: 'Internal developer productivity tooling and testcontainer automation.',
      startDate: '2026-01-01',
      budgetAmount: 50000,
      budgetHours: 400,
      isBillable: false,
      status: 'ACTIVE' as const,
    },
  ]

  for (const p of projects) world.timesheetProjects.set(p.id, p)

  const activities = [
    {
      id: 'act-01',
      projectId: 'proj-01',
      projectName: 'Enterprise HR Mobile Platform 2.0',
      activityCode: 'ACT-DEV',
      name: 'Architecture & Core Feature Development',
      description: 'Writing backend domain modules, OpenAPI clients, and Compose/SwiftUI views.',
      isBillable: true,
      defaultRate: 85.0,
      isActive: true,
    },
    {
      id: 'act-02',
      projectId: 'proj-01',
      projectName: 'Enterprise HR Mobile Platform 2.0',
      activityCode: 'ACT-CODE-REV',
      name: 'Technical Peer Code Review & Testing',
      description: 'Reviewing pull requests, writing unit tests, and verifying contract parity.',
      isBillable: true,
      defaultRate: 75.0,
      isActive: true,
    },
    {
      id: 'act-03',
      projectId: 'proj-02',
      projectName: 'Real-Time Credit Risk Calculation Gateway',
      activityCode: 'ACT-CLIENT-SYNC',
      name: 'Sprint Planning & Client Architecture Sync',
      description: 'Weekly client stakeholder meetings, requirement grooming, and sprint demos.',
      isBillable: true,
      defaultRate: 95.0,
      isActive: true,
    },
    {
      id: 'act-04',
      projectId: 'proj-03',
      projectName: 'Internal Developer Tools & CI/CD Pipelines',
      activityCode: 'ACT-DEVOPS',
      name: 'Environment Setup & Gradle Optimization',
      description: 'Optimizing build caching, docker-compose dev setups, and lint scripts.',
      isBillable: false,
      defaultRate: 0.0,
      isActive: true,
    },
  ]

  for (const a of activities) world.timesheetActivities.set(a.id, a)

  const kasunTimesheet = {
    id: 'ts-2026-w11-kasun',
    employeeId: 'de300000-0001-4000-8000-000000000002',
    employeeName: 'Kasun Fernando',
    employeeCode: 'E002',
    periodStart: '2026-03-09',
    periodEnd: '2026-03-15',
    status: 'SUBMITTED' as const,
    totalHours: 40.0,
    billableHours: 35.0,
    submittedAt: '2026-03-15T18:00:00Z',
  }

  world.timesheets.set(kasunTimesheet.id, kasunTimesheet)

  world.timesheetEntries.set(kasunTimesheet.id, [
    {
      id: 'tse-01',
      timesheetId: kasunTimesheet.id,
      workDate: '2026-03-09',
      projectId: 'proj-01',
      projectName: 'Enterprise HR Mobile Platform 2.0',
      activityId: 'act-01',
      activityName: 'Architecture & Core Feature Development',
      hours: 7.0,
      description: 'Implemented Timesheet domain models and database migrations V25.',
      isBillable: true,
      rate: 85.0,
      amount: 595.0,
    },
    {
      id: 'tse-02',
      timesheetId: kasunTimesheet.id,
      workDate: '2026-03-09',
      projectId: 'proj-03',
      projectName: 'Internal Developer Tools & CI/CD Pipelines',
      activityId: 'act-04',
      activityName: 'Environment Setup & Gradle Optimization',
      hours: 1.0,
      description: 'Investigated embedded-postgres binary configuration for Windows.',
      isBillable: false,
      rate: 0.0,
      amount: 0.0,
    },
    {
      id: 'tse-03',
      timesheetId: kasunTimesheet.id,
      workDate: '2026-03-10',
      projectId: 'proj-01',
      projectName: 'Enterprise HR Mobile Platform 2.0',
      activityId: 'act-01',
      activityName: 'Architecture & Core Feature Development',
      hours: 8.0,
      description: 'Engineered digital e-signatures cryptographic hash verification.',
      isBillable: true,
      rate: 85.0,
      amount: 680.0,
    },
    {
      id: 'tse-04',
      timesheetId: kasunTimesheet.id,
      workDate: '2026-03-11',
      projectId: 'proj-01',
      projectName: 'Enterprise HR Mobile Platform 2.0',
      activityId: 'act-02',
      activityName: 'Technical Peer Code Review & Testing',
      hours: 6.0,
      description: 'Peer code review on leave eligibility checker algorithms.',
      isBillable: true,
      rate: 75.0,
      amount: 450.0,
    },
    {
      id: 'tse-05',
      timesheetId: kasunTimesheet.id,
      workDate: '2026-03-11',
      projectId: 'proj-03',
      projectName: 'Internal Developer Tools & CI/CD Pipelines',
      activityId: 'act-04',
      activityName: 'Environment Setup & Gradle Optimization',
      hours: 2.0,
      description: 'Tuned Vite client build bundle chunk splitting.',
      isBillable: false,
      rate: 0.0,
      amount: 0.0,
    },
    {
      id: 'tse-06',
      timesheetId: kasunTimesheet.id,
      workDate: '2026-03-12',
      projectId: 'proj-01',
      projectName: 'Enterprise HR Mobile Platform 2.0',
      activityId: 'act-01',
      activityName: 'Architecture & Core Feature Development',
      hours: 7.0,
      description: 'Built recruitment candidate stage progression engine.',
      isBillable: true,
      rate: 85.0,
      amount: 595.0,
    },
    {
      id: 'tse-07',
      timesheetId: kasunTimesheet.id,
      workDate: '2026-03-13',
      projectId: 'proj-01',
      projectName: 'Enterprise HR Mobile Platform 2.0',
      activityId: 'act-01',
      activityName: 'Architecture & Core Feature Development',
      hours: 7.0,
      description: 'Added biometric attendance vs timesheet reconciliation queries.',
      isBillable: true,
      rate: 85.0,
      amount: 595.0,
    },
    {
      id: 'tse-08',
      timesheetId: kasunTimesheet.id,
      workDate: '2026-03-13',
      projectId: 'proj-03',
      projectName: 'Internal Developer Tools & CI/CD Pipelines',
      activityId: 'act-04',
      activityName: 'Environment Setup & Gradle Optimization',
      hours: 2.0,
      description: 'Automated test suite execution and OpenAPI spec sync.',
      isBillable: false,
      rate: 0.0,
      amount: 0.0,
    },
  ])

  const nimaliTimesheet = {
    id: 'ts-2026-w11-nimali',
    employeeId: 'de300000-0001-4000-8000-000000000001',
    employeeName: 'Nimali Wickramasinghe',
    employeeCode: 'E001',
    periodStart: '2026-03-09',
    periodEnd: '2026-03-15',
    status: 'APPROVED' as const,
    totalHours: 42.0,
    billableHours: 40.0,
    submittedAt: '2026-03-15T19:00:00Z',
    approvedAt: '2026-03-16T09:30:00Z',
  }

  world.timesheets.set(nimaliTimesheet.id, nimaliTimesheet)
}

/* -------------------------------------------------------------------------- */
/* Performance, OKRs & 360 Appraisal Seed Data (V20)                         */
/* -------------------------------------------------------------------------- */

function seedPerformanceData(world: World): void {
  const cycles = [
    {
      id: 'cyc-2026',
      code: 'CYC-2026',
      name: '2026 Annual Performance & OKR Cycle',
      startDate: '2026-01-01',
      endDate: '2026-12-31',
      status: 'ACTIVE' as const,
    },
    {
      id: 'cyc-2026-q1',
      code: 'CYC-2026-Q1',
      name: '2026 Q1 Quarterly Objectives',
      startDate: '2026-01-01',
      endDate: '2026-03-31',
      status: 'ACTIVE' as const,
    },
  ]
  for (const c of cycles) world.goalCycles.set(c.id, c)

  const groups = [
    {
      id: 'cg-01',
      code: 'TECH',
      name: 'Technical Mastery & Architecture',
      description: 'System design, reliability, performance optimization, and clean code.',
    },
    {
      id: 'cg-02',
      code: 'LEAD',
      name: 'Leadership, Mentorship & Collaboration',
      description: 'Peer coaching, technical delegation, knowledge sharing, and team guidance.',
    },
    {
      id: 'cg-03',
      code: 'CORE',
      name: 'Core Cultural Values',
      description: 'Customer empathy, delivery ownership, bias for action, and integrity.',
    },
  ]
  for (const g of groups) world.competencyGroups.set(g.id, g)

  const competencies = [
    {
      id: 'comp-01',
      groupId: 'cg-01',
      groupName: 'Technical Mastery & Architecture',
      code: 'ARCH',
      name: 'Modular System Architecture',
      description: 'Designs resilient modular boundaries and zero-drift database schemas.',
      targetLevel: 4,
    },
    {
      id: 'comp-02',
      groupId: 'cg-01',
      groupName: 'Technical Mastery & Architecture',
      code: 'PERF',
      name: 'Database & Query Performance',
      description: 'Tunes PostgreSQL indexes, partitions, and avoids N+1 query traps.',
      targetLevel: 4,
    },
    {
      id: 'comp-03',
      groupId: 'cg-02',
      groupName: 'Leadership, Mentorship & Collaboration',
      code: 'MENTOR',
      name: 'Engineering Mentorship',
      description: 'Active code reviewer, provides constructive architectural feedback.',
      targetLevel: 3,
    },
    {
      id: 'comp-04',
      groupId: 'cg-03',
      groupName: 'Core Cultural Values',
      code: 'OWNER',
      name: 'Extreme Delivery Ownership',
      description: 'Owns production stability, incident resolution, and zero-defect deployments.',
      targetLevel: 4,
    },
  ]
  for (const comp of competencies) world.competencies.set(comp.id, comp)

  const goals = [
    {
      id: 'goal-01',
      employeeId: 'de300000-0001-4000-8000-000000000002',
      employeeName: 'Kasun Fernando',
      cycleId: 'cyc-2026-q1',
      cycleName: '2026 Q1 Quarterly Objectives',
      title: 'Achieve 99.95% Offline Sync Reliability in Mobile Client',
      description: 'Implement cursor delta-sync with room conflict resolution and background reconciliation queue.',
      category: 'INDIVIDUAL' as const,
      weight: 40,
      targetValue: 99.95,
      currentValue: 99.2,
      unit: '%',
      startDate: '2026-01-05',
      dueDate: '2026-03-31',
      status: 'ON_TRACK' as const,
      progressPercentage: 92,
    },
    {
      id: 'goal-02',
      employeeId: 'de300000-0001-4000-8000-000000000002',
      employeeName: 'Kasun Fernando',
      cycleId: 'cyc-2026-q1',
      cycleName: '2026 Q1 Quarterly Objectives',
      title: 'Reduce Core Spring Modulith Startup Time Under 4.5s',
      description: 'Profile lazy bean initialisation and eliminate circular component dependencies in backend context.',
      category: 'INDIVIDUAL' as const,
      weight: 30,
      targetValue: 4.5,
      currentValue: 5.1,
      unit: 's',
      startDate: '2026-01-10',
      dueDate: '2026-03-31',
      status: 'ON_TRACK' as const,
      progressPercentage: 75,
    },
    {
      id: 'goal-03',
      employeeId: 'de300000-0001-4000-8000-000000000002',
      employeeName: 'Kasun Fernando',
      cycleId: 'cyc-2026-q1',
      cycleName: '2026 Q1 Quarterly Objectives',
      title: 'Publish 100% Validated OpenAPI 3.1 Spec Coverage',
      description: 'Generate typesafe contracts for all recruitment, documents and timesheets endpoints.',
      category: 'DEPARTMENTAL' as const,
      weight: 30,
      targetValue: 100,
      currentValue: 100,
      unit: '%',
      startDate: '2026-01-15',
      dueDate: '2026-03-15',
      status: 'COMPLETED' as const,
      progressPercentage: 100,
    },
    {
      id: 'goal-04',
      employeeId: 'de300000-0001-4000-8000-000000000004',
      employeeName: 'Dilani Perera',
      cycleId: 'cyc-2026-q1',
      cycleName: '2026 Q1 Quarterly Objectives',
      title: 'Automate Gross-to-Net Payroll Calculation Test Matrix',
      description: 'Build 50+ country tax edge cases including overtime, APIT, and loan deductions.',
      category: 'INDIVIDUAL' as const,
      weight: 50,
      targetValue: 100,
      currentValue: 60,
      unit: '%',
      startDate: '2026-01-20',
      dueDate: '2026-03-31',
      status: 'AT_RISK' as const,
      progressPercentage: 60,
    },
  ]
  for (const g of goals) world.goals.set(g.id, g)

  world.goalCheckIns.set('goal-01', [
    {
      id: 'chk-01',
      goalId: 'goal-01',
      previousValue: 95.0,
      newValue: 99.2,
      progressPercentage: 92,
      note: 'Benchmarked offline database queue over 10,000 synthetic mutations.',
      checkedInBy: 'de300000-0001-4000-8000-000000000002',
      checkedInByName: 'Kasun Fernando',
      createdAt: '2026-03-08T10:00:00Z',
    },
  ])

  const evalCycles = [
    {
      id: 'eval-2026-annual',
      code: 'EVAL-2026-ANNUAL',
      name: 'FY2025/2026 Annual Performance & 360 Review',
      startDate: '2025-04-01',
      endDate: '2026-03-31',
      goalWeight: 50.0,
      competencyWeight: 30.0,
      mraWeight: 20.0,
      selfReviewDeadline: '2026-03-15',
      peerReviewDeadline: '2026-03-22',
      managerReviewDeadline: '2026-03-28',
      calibrationDeadline: '2026-03-31',
      status: 'CALIBRATION' as const,
      totalEligibleEmployees: 12,
      completedAppraisals: 10,
      inProgressAppraisals: 2,
    },
    {
      id: 'eval-2026-h1',
      code: 'EVAL-2026-H1',
      name: '2026 H1 Mid-Year Appraisal Cycle',
      startDate: '2026-01-01',
      endDate: '2026-06-30',
      goalWeight: 60.0,
      competencyWeight: 30.0,
      mraWeight: 10.0,
      selfReviewDeadline: '2026-06-15',
      peerReviewDeadline: '2026-06-20',
      managerReviewDeadline: '2026-06-25',
      calibrationDeadline: '2026-06-30',
      status: 'SELF_REVIEW' as const,
      totalEligibleEmployees: 12,
      completedAppraisals: 3,
      inProgressAppraisals: 9,
    },
    {
      id: 'eval-2025-annual',
      code: 'EVAL-2025-ANNUAL',
      name: 'FY2024/2025 Annual Performance Cycle',
      startDate: '2024-04-01',
      endDate: '2025-03-31',
      goalWeight: 50.0,
      competencyWeight: 40.0,
      mraWeight: 10.0,
      selfReviewDeadline: '2025-03-15',
      peerReviewDeadline: '2025-03-20',
      managerReviewDeadline: '2025-03-28',
      calibrationDeadline: '2025-03-31',
      status: 'CLOSED' as const,
      totalEligibleEmployees: 12,
      completedAppraisals: 12,
      inProgressAppraisals: 0,
    },
  ]
  for (const ec of evalCycles) world.evaluationCycles.set(ec.id, ec)

  const appraisals = [
    {
      id: 'appr-01',
      cycleId: 'eval-2026-h1',
      cycleName: '2026 H1 Mid-Year Appraisal Cycle',
      employeeId: 'de300000-0001-4000-8000-000000000002',
      employeeName: 'Kasun Fernando',
      employeeTitle: 'Staff Software Architect',
      departmentName: 'Engineering',
      managerId: 'de300000-0001-4000-8000-000000000001',
      managerName: 'Nimali Wickramasinghe',
      status: 'MANAGER_REVIEW_PENDING' as const,
      finalScore: 4.35,
      finalRating: 'Exceeds Expectations',
      selfReviewDeadline: '2026-06-15',
      managerReviewDeadline: '2026-06-25',
      selfOverallComments: 'Delivered high-performance offline mobile sync and zero-drift database migrations.',
      managerOverallComments: 'Outstanding leadership on platform architecture. Need to focus on delegating ops tasks.',
      calibrationNotes: 'Recommended for Staff Grade II progression.',
      goals: [
        {
          goalId: 'goal-01',
          title: 'Achieve 99.95% Offline Sync Reliability in Mobile Client',
          category: 'INDIVIDUAL',
          weight: 40,
          progressPercentage: 92,
          selfRating: 4.5,
          selfComments: 'Exceeded latency thresholds by 30%.',
          managerRating: 4.5,
          managerComments: 'Exceptional technical execution.',
          weightedScore: 1.8,
        },
        {
          goalId: 'goal-02',
          title: 'Reduce Core Spring Modulith Startup Time Under 4.5s',
          category: 'INDIVIDUAL',
          weight: 30,
          progressPercentage: 75,
          selfRating: 4.0,
          selfComments: 'Startup down from 9s to 5.1s.',
          managerRating: 4.0,
          managerComments: 'Great work so far; push for 4.5s.',
          weightedScore: 1.2,
        },
      ],
      competencies: [
        {
          competencyId: 'comp-01',
          code: 'ARCH',
          name: 'Modular System Architecture',
          groupName: 'Technical Mastery & Architecture',
          targetLevel: 4,
          selfProficiencyLevel: 5,
          managerProficiencyLevel: 4,
          selfComments: 'Authored architectural blueprints.',
          managerComments: 'Industry standard design patterns.',
        },
        {
          competencyId: 'comp-04',
          code: 'OWNER',
          name: 'Extreme Delivery Ownership',
          groupName: 'Core Cultural Values',
          targetLevel: 4,
          selfProficiencyLevel: 5,
          managerProficiencyLevel: 5,
          selfComments: 'Directly supported client demo environments.',
          managerComments: 'Takes complete accountability.',
        },
      ],
      mraSummary: {
        totalRequests: 4,
        completedRequests: 4,
        averageScore: 4.6,
        relationshipBreakdown: [
          { relationship: 'PEER', respondentCount: 2, averageScore: 4.7 },
          { relationship: 'SUBORDINATE', respondentCount: 2, averageScore: 4.5 },
        ],
      },
    },
    {
      id: 'appr-02',
      cycleId: 'eval-2026-h1',
      cycleName: '2026 H1 Mid-Year Appraisal Cycle',
      employeeId: 'de300000-0001-4000-8000-000000000004',
      employeeName: 'Dilani Perera',
      employeeTitle: 'Senior Payroll Specialist',
      departmentName: 'Finance',
      managerId: 'de300000-0001-4000-8000-000000000001',
      managerName: 'Nimali Wickramasinghe',
      status: 'SELF_REVIEW_PENDING' as const,
      selfReviewDeadline: '2026-06-15',
      managerReviewDeadline: '2026-06-25',
      goals: [],
      competencies: [],
    },
    {
      id: 'appr-03',
      cycleId: 'eval-2026-h1',
      cycleName: '2026 H1 Mid-Year Appraisal Cycle',
      employeeId: 'de300000-0001-4000-8000-000000000003',
      employeeName: 'Mohamed Rizwan',
      employeeTitle: 'Lead QA Automation Engineer',
      departmentName: 'Engineering',
      managerId: 'de300000-0001-4000-8000-000000000002',
      managerName: 'Kasun Fernando',
      status: 'IN_CALIBRATION' as const,
      finalScore: 4.6,
      finalRating: 'Role Model',
      selfReviewDeadline: '2026-06-15',
      managerReviewDeadline: '2026-06-25',
      goals: [],
      competencies: [],
    },
  ]
  for (const a of appraisals) world.appraisals.set(a.id, a)

  const feedbackItems = [
    {
      id: 'fb-01',
      senderEmployeeId: 'de300000-0001-4000-8000-000000000001',
      senderEmployeeName: 'Nimali Wickramasinghe',
      recipientEmployeeId: 'de300000-0001-4000-8000-000000000002',
      recipientEmployeeName: 'Kasun Fernando',
      feedbackType: 'PRAISE' as const,
      title: 'Incredible speed on Document Vault & Digital Signatures',
      content: 'Shipped cryptographic SHA-256 audit trails with zero regression in our sprint timelines. Truly stellar engineering work!',
      isPrivate: false,
      sharedWithManager: true,
      createdAt: '2026-03-09T09:30:00Z',
    },
    {
      id: 'fb-02',
      senderEmployeeId: 'de300000-0001-4000-8000-000000000002',
      senderEmployeeName: 'Kasun Fernando',
      recipientEmployeeId: 'de300000-0001-4000-8000-000000000004',
      recipientEmployeeName: 'Dilani Perera',
      feedbackType: 'COACHING' as const,
      title: 'Edge-case handling in APIT tax brackets',
      content: 'Great breakdown of statutory slabs. Let us also ensure employee loan interest offsets are verified with integration tests.',
      isPrivate: true,
      sharedWithManager: true,
      createdAt: '2026-03-08T16:45:00Z',
    },
  ]
  for (const fb of feedbackItems) world.continuousFeedback.set(fb.id, fb)
}

/* -------------------------------------------------------------------------- */
/* Onboarding & Offboarding Seed Data (V22)                                    */
/* -------------------------------------------------------------------------- */

function seedOnboardingData(world: World): void {
  const stages = [
    { id: 'stg-pre', code: 'PRE_BOARDING', name: 'Pre-boarding (Before Day 1)', sequence: 1, daysOffset: -7 },
    { id: 'stg-d1', code: 'DAY_ONE', name: 'Day One: Welcome & Induction', sequence: 2, daysOffset: 0 },
    { id: 'stg-w1', code: 'WEEK_ONE', name: 'Week One: Foundations & Tooling', sequence: 3, daysOffset: 7 },
    { id: 'stg-m1', code: 'MONTH_ONE', name: 'Month One: Milestone Review', sequence: 4, daysOffset: 30 },
  ]
  for (const s of stages) world.onboardingStages.set(s.id, s)

  const profiles = [
    {
      id: 'prof-eng',
      code: 'ENG_DEV',
      name: 'Software Engineering Onboarding Track',
      departmentName: 'Engineering',
      description: 'Comprehensive engineering induction covering Git repos, dev sandbox, AWS IAM, and architecture reviews.',
      active: true,
    },
    {
      id: 'prof-ops',
      code: 'CORP_OPS',
      name: 'Corporate & HR Operations Track',
      departmentName: 'Human Resources',
      description: 'Standard enterprise onboarding covering compliance, benefit enrollments, and workspace setup.',
      active: true,
    },
  ]
  for (const p of profiles) world.onboardingProfiles.set(p.id, p)

  const instances = [
    {
      id: 'onb-01',
      employeeId: 'de300000-0001-4000-8000-000000000005',
      employeeName: 'Dilum Senanayake',
      employeeCode: 'E005',
      departmentName: 'Engineering',
      jobTitle: 'Senior Full Stack Engineer',
      profileId: 'prof-eng',
      profileName: 'Software Engineering Onboarding Track',
      joinDate: '2026-03-01',
      status: 'IN_PROGRESS' as const,
      progressPct: 60.0,
      buddyEmployeeId: 'de300000-0001-4000-8000-000000000002',
      buddyName: 'Kasun Fernando',
      totalTasks: 5,
      completedTasks: 3,
    },
    {
      id: 'onb-02',
      employeeId: 'de300000-0001-4000-8000-000000000006',
      employeeName: 'Rashmi Jayasuriya',
      employeeCode: 'E006',
      departmentName: 'Engineering',
      jobTitle: 'Senior Frontend Engineer',
      profileId: 'prof-eng',
      profileName: 'Software Engineering Onboarding Track',
      joinDate: '2026-03-15',
      status: 'NOT_STARTED' as const,
      progressPct: 0.0,
      buddyEmployeeId: 'de300000-0001-4000-8000-000000000002',
      buddyName: 'Kasun Fernando',
      totalTasks: 5,
      completedTasks: 0,
    },
  ]
  for (const inst of instances) world.onboardingInstances.set(inst.id, inst)

  world.onboardingTasks.set('onb-01', [
    {
      id: 'tsk-01',
      instanceId: 'onb-01',
      title: 'Sign Digital Employment Contract & Confidentiality NDA',
      description: 'Execute legal contracts via the HR Document Vault with e-signatures.',
      ownerRole: 'NEW_HIRE' as const,
      dueDate: '2026-03-01',
      status: 'COMPLETED' as const,
      completedAt: '2026-03-01T10:15:00Z',
      notes: 'Executed via cryptographic digital signature.',
    },
    {
      id: 'tsk-02',
      instanceId: 'onb-01',
      title: 'Provision MacBook Pro M3 & AWS Developer Credentials',
      description: 'Issue encrypted laptop, 2FA hardware YubiKey, and GitHub team permissions.',
      ownerRole: 'IT_OPS' as const,
      assigneeName: 'IT Infrastructure Operations',
      dueDate: '2026-03-01',
      status: 'COMPLETED' as const,
      completedAt: '2026-03-01T14:30:00Z',
    },
    {
      id: 'tsk-03',
      instanceId: 'onb-01',
      title: 'Initial Welcome Coffee & Team Architecture Walkthrough',
      description: 'Pairing session with assigned engineering buddy to review codebase structure.',
      ownerRole: 'BUDDY' as const,
      assigneeName: 'Kasun Fernando',
      dueDate: '2026-03-03',
      status: 'COMPLETED' as const,
      completedAt: '2026-03-03T11:00:00Z',
    },
    {
      id: 'tsk-04',
      instanceId: 'onb-01',
      title: 'Local Modulith & Docker Test Suite Execution',
      description: 'Spin up local PostgreSQL test database and run all unit tests green.',
      ownerRole: 'NEW_HIRE' as const,
      dueDate: '2026-03-10',
      status: 'IN_PROGRESS' as const,
      notes: 'Gradle dependencies cached; running test suite.',
    },
    {
      id: 'tsk-05',
      instanceId: 'onb-01',
      title: '30-Day Probation & Expectation Alignment Review',
      description: 'First milestone performance review with Engineering Manager.',
      ownerRole: 'MANAGER' as const,
      assigneeName: 'Nimali Wickramasinghe',
      dueDate: '2026-03-31',
      status: 'PENDING' as const,
    },
  ])

  const exitTypes = [
    {
      id: 'exit-res',
      code: 'RESIGNATION',
      name: 'Voluntary Resignation',
      voluntary: true,
      noticeDays: 30,
      requiresInterview: true,
      requiresClearance: true,
      reasons: [
        { id: 'rsn-01', code: 'CAREER_GROWTH', name: 'Higher Studies & Career Growth', category: 'CAREER' },
        { id: 'rsn-02', code: 'RELOCATION', name: 'International Relocation', category: 'PERSONAL' },
      ],
    },
    {
      id: 'exit-mut',
      code: 'MUTUAL_SEPARATION',
      name: 'Mutual Separation Agreement',
      voluntary: true,
      noticeDays: 14,
      requiresInterview: true,
      requiresClearance: true,
      reasons: [
        { id: 'rsn-03', code: 'CONTRACT_COMPLETION', name: 'Special Project Handover Complete', category: 'OPERATIONAL' },
      ],
    },
  ]
  for (const et of exitTypes) world.exitTypes.set(et.id, et)

  const exitNotices = [
    {
      id: 'not-01',
      noticeNumber: 'EX-2026-001',
      employeeId: 'de300000-0001-4000-8000-000000000003',
      employeeName: 'Mohamed Rizwan',
      employeeCode: 'E003',
      departmentName: 'Engineering',
      jobTitle: 'Lead QA Automation Engineer',
      exitTypeId: 'exit-res',
      exitTypeCode: 'RESIGNATION',
      exitTypeName: 'Voluntary Resignation',
      exitReasonId: 'rsn-01',
      exitReasonName: 'Higher Studies & Career Growth',
      noticeDate: '2026-03-01',
      requestedLastWorkingDate: '2026-03-31',
      approvedLastWorkingDate: '2026-03-31',
      remarks: 'Admitted to Master of Computer Science program in Melbourne starting April 2026.',
      status: 'APPROVED' as const,
      approvedBy: 'de300000-0001-4000-8000-000000000001',
      approvedByName: 'Nimali Wickramasinghe',
      approvedAt: '2026-03-03T10:00:00Z',
    },
    {
      id: 'not-02',
      noticeNumber: 'EX-2026-002',
      employeeId: 'de300000-0001-4000-8000-000000000004',
      employeeName: 'Dilani Perera',
      employeeCode: 'E004',
      departmentName: 'Finance',
      jobTitle: 'Senior Payroll Specialist',
      exitTypeId: 'exit-mut',
      exitTypeCode: 'MUTUAL_SEPARATION',
      exitTypeName: 'Mutual Separation Agreement',
      noticeDate: '2026-03-08',
      requestedLastWorkingDate: '2026-03-25',
      remarks: 'Transitioning to external consulting role.',
      status: 'SUBMITTED' as const,
    },
  ]
  for (const en of exitNotices) world.exitNotices.set(en.id, en)

  world.clearanceTasks.set('not-01', [
    {
      id: 'clr-01',
      exitNoticeId: 'not-01',
      employeeId: 'de300000-0001-4000-8000-000000000003',
      department: 'IT_INFRASTRUCTURE' as const,
      title: 'Revoke AWS/GitHub Access & Collect MacBook Pro M2 + YubiKey',
      assigneeName: 'IT Operations Team',
      status: 'CLEARED' as const,
      clearedAt: '2026-03-08T11:00:00Z',
      remarks: 'Hardware received in immaculate condition and inventoried.',
      recoverableAmount: 0,
    },
    {
      id: 'clr-02',
      exitNoticeId: 'not-01',
      employeeId: 'de300000-0001-4000-8000-000000000003',
      department: 'FINANCE_PAYROLL' as const,
      title: 'Settle Corporate Expense Cards & Compute Final Salary Adjustments',
      assigneeName: 'Finance & Payroll Unit',
      status: 'PENDING' as const,
      remarks: 'Outstanding mobile subsidy advance to be deducted from final pay.',
      recoverableAmount: 24000,
    },
    {
      id: 'clr-03',
      exitNoticeId: 'not-01',
      employeeId: 'de300000-0001-4000-8000-000000000003',
      department: 'HR_OPERATIONS' as const,
      title: 'Conduct Exit Interview & Prepare EPF/ETF Form B Certificate',
      assigneeName: 'HR Operations Lead',
      status: 'CLEARED' as const,
      clearedAt: '2026-03-08T15:30:00Z',
      remarks: 'Exit interview conducted and recorded.',
      recoverableAmount: 0,
    },
    {
      id: 'clr-04',
      exitNoticeId: 'not-01',
      employeeId: 'de300000-0001-4000-8000-000000000003',
      department: 'ADMIN_FACILITIES' as const,
      title: 'Return Office RFID Keycard, Parking Pass & Pedestal Locker Keys',
      assigneeName: 'Facilities Manager',
      status: 'CLEARED' as const,
      clearedAt: '2026-03-07T16:00:00Z',
      recoverableAmount: 0,
    },
    {
      id: 'clr-05',
      exitNoticeId: 'not-01',
      employeeId: 'de300000-0001-4000-8000-000000000003',
      department: 'LINE_MANAGER' as const,
      title: 'Test Automation Repository Handover & Test Suite Runbook Transfer',
      assigneeName: 'Kasun Fernando',
      status: 'CLEARED' as const,
      clearedAt: '2026-03-08T14:00:00Z',
      remarks: 'Comprehensive runbooks committed and approved.',
      recoverableAmount: 0,
    },
  ])

  world.exitInterviews.set('not-01', {
    id: 'exi-01',
    exitNoticeId: 'not-01',
    employeeId: 'de300000-0001-4000-8000-000000000003',
    employeeName: 'Mohamed Rizwan',
    interviewerEmployeeId: 'de300000-0001-4000-8000-000000000001',
    interviewerName: 'Nimali Wickramasinghe',
    conductedAt: '2026-03-08T15:00:00Z',
    overallExperienceRating: 5,
    managementRating: 5,
    cultureRating: 5,
    reasonDetails: 'Leaving purely to pursue full-time graduate degree overseas in Melbourne.',
    suggestions: 'Continue investing in automated CI/CD and developer testing infrastructure. The team culture is top notch!',
    wouldRecommend: true,
  })
}

/* -------------------------------------------------------------------------- */
/* Accounts and callers                                                        */
/* -------------------------------------------------------------------------- */

/**
 * Resolves a sign-in identifier.
 *
 * Username or email, because the sign-in field is labelled "Username or email" and a console that
 * offers both and accepts one is a bug report waiting to be filed.
 */
export function findAccount(identifier: string): DemoAccount | undefined {
  const needle = identifier.trim().toLowerCase()
  return ACCOUNTS.find(
    (account) => account.username === needle || account.email.toLowerCase() === needle,
  )
}

export function accountById(userId: string): DemoAccount | undefined {
  return ACCOUNTS.find((account) => account.userId === userId)
}

export function callerFor(account: DemoAccount): Caller {
  return {
    account,
    employeeId: employeeIdFor(account.employeeCode),
    permissions: new Set(permissionsFor(account)),
  }
}

/* -------------------------------------------------------------------------- */
/* Tokens                                                                      */
/* -------------------------------------------------------------------------- */

function opaqueToken(prefix: string): string {
  return `${prefix}_${randomBytes(24).toString('hex')}`
}

export interface IssuedTokens {
  accessToken: string
  refreshToken: string
  expiresIn: number
  refreshExpiresIn: number
}

/**
 * Mints a session for a user on a device.
 *
 * The tokens are opaque random strings rather than signed JWTs. Nothing in the console inspects
 * them — `tokens.ts` stores the access token and reads `expiresIn` from the response body — and a
 * hand-rolled JWT would invite exactly the misreading this whole exercise is guarding against:
 * that this is an authentication server rather than a stand-in for one.
 */
export function issueSession(world: World, userId: string, deviceRowId: string): { session: Session; tokens: IssuedTokens } {
  const session: Session = {
    id: randomUUID(),
    userId,
    deviceRowId,
    refreshToken: opaqueToken('demo_rt'),
    accessToken: opaqueToken('demo_at'),
    accessTokenExpiresAt: Date.now() + ACCESS_TOKEN_TTL_SECONDS * 1000,
    revoked: false,
  }
  world.sessions.set(session.id, session)
  world.refreshTokens.set(session.refreshToken, session.id)
  world.accessTokens.set(session.accessToken, session.id)
  return { session, tokens: tokensFor(session) }
}

/**
 * Rotates the pair on the way back out.
 *
 * Refresh tokens are single-use here for the same reason they are on the server: it is what makes
 * theft detectable at all. The console funnels every refresh through one in-flight promise
 * precisely so that six queries firing on mount do not present the same spent token five times.
 */
export function rotateSession(world: World, session: Session): IssuedTokens {
  world.accessTokens.delete(session.accessToken)
  world.refreshTokens.delete(session.refreshToken)
  world.spentRefreshTokens.set(session.refreshToken, session.id)

  session.refreshToken = opaqueToken('demo_rt')
  session.accessToken = opaqueToken('demo_at')
  session.accessTokenExpiresAt = Date.now() + ACCESS_TOKEN_TTL_SECONDS * 1000

  world.refreshTokens.set(session.refreshToken, session.id)
  world.accessTokens.set(session.accessToken, session.id)
  return tokensFor(session)
}

function tokensFor(session: Session): IssuedTokens {
  return {
    accessToken: session.accessToken,
    refreshToken: session.refreshToken,
    expiresIn: Math.max(0, Math.round((session.accessTokenExpiresAt - Date.now()) / 1000)),
    refreshExpiresIn: REFRESH_TOKEN_TTL_SECONDS,
  }
}

/** Ends a session and both its tokens at once. Sign-out, where the caller asked to be gone now. */
export function revokeSession(world: World, session: Session): void {
  session.revoked = true
  world.accessTokens.delete(session.accessToken)
  world.refreshTokens.delete(session.refreshToken)
  world.sessions.delete(session.id)
}

/**
 * Revoking a device, which is deliberately *not* the same thing.
 *
 * The contract is explicit: revoking a device takes effect immediately for refresh and biometric
 * grants, and an access token already issued stays valid until it expires — at most fifteen
 * minutes. Killing the access token here as well would be a demo that is stricter than the server
 * and would hide the one case worth seeing: revoke the browser's own row and the console keeps
 * working until the next reload, then lands on the sign-in page.
 */
export function revokeRefreshForDevice(world: World, deviceRowId: string): void {
  for (const session of world.sessions.values()) {
    if (session.deviceRowId !== deviceRowId) continue
    session.revoked = true
    world.refreshTokens.delete(session.refreshToken)
  }
}

/**
 * The session an access token belongs to, or nothing.
 *
 * Presence in `accessTokens` is the authority rather than `session.revoked`, for the reason above:
 * a revoked family still honours an access token it already issued until that token expires.
 */
export function sessionForAccessToken(world: World, accessToken: string): Session | undefined {
  const sessionId = world.accessTokens.get(accessToken)
  if (sessionId === undefined) return undefined
  const session = world.sessions.get(sessionId)
  if (session === undefined) return undefined
  if (Date.now() >= session.accessTokenExpiresAt) return undefined
  return session
}

/* -------------------------------------------------------------------------- */
/* Two-factor material                                                         */
/* -------------------------------------------------------------------------- */

const BASE32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'

/** A 32-character base32 secret, which is what every authenticator app expects to be handed. */
export function newTotpSecret(): string {
  const bytes = randomBytes(32)
  let secret = ''
  for (const byte of bytes) secret += BASE32.charAt(byte % BASE32.length)
  return secret
}

/**
 * Ten single-use codes.
 *
 * Grouped with a hyphen because these get written down on paper, and an unbroken sixteen-character
 * string is transcribed wrongly more often than it is transcribed correctly.
 */
export function newRecoveryCodes(): string[] {
  return Array.from({ length: 10 }, () => {
    const raw = randomBytes(4).toString('hex').toUpperCase()
    return `${raw.slice(0, 4)}-${raw.slice(4, 8)}`
  })
}

export function mfaStateFor(world: World, userId: string): MfaState {
  const existing = world.mfaByUser.get(userId)
  if (existing !== undefined) return existing
  const fresh: MfaState = { enabled: false, enrolmentPending: false, recoveryCodesRemaining: 0 }
  world.mfaByUser.set(userId, fresh)
  return fresh
}

export function devicesFor(world: World, userId: string): DemoDevice[] {
  const existing = world.devicesByUser.get(userId)
  if (existing !== undefined) return existing
  const fresh: DemoDevice[] = []
  world.devicesByUser.set(userId, fresh)
  return fresh
}
