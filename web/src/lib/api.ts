import {
  AuthenticationApi,
  Configuration,
  DirectoryApi,
  EmployeesApi,
  FormsApi,
  LoansApi,
  MeApi,
  ReferenceApi,
  ResponseError,
  TrainingApi,
  type ApiErrorResponse,
  type Middleware,
  type RequestContext,
  type ResponseContext,
} from '@hr/client'
import { tokens } from './tokens'

/**
 * The API base path.
 *
 * Empty in development so requests are same-origin and handled by the Vite proxy — the browser
 * never sees a cross-origin request, so no CORS configuration is needed and the dev setup matches
 * production, where the console is served behind the same host.
 */
const BASE_PATH = import.meta.env.VITE_API_BASE_URL ?? ''

/** Attaches the tenant header to the unauthenticated endpoints that need it. */
const tenantHeaderMiddleware: Middleware = {
  // The Middleware interface requires a Promise return. `async` is the idiomatic way to satisfy
  // it, and there is genuinely nothing here to await.
  // eslint-disable-next-line @typescript-eslint/require-await
  async pre(context: RequestContext) {
    const tenantCode = tokens.getTenantCode()
    if (tenantCode !== null) {
      context.init.headers = {
        ...context.init.headers,
        'X-Tenant-Code': tenantCode,
      }
    }
    return context
  },
}

/**
 * Refreshes the access token once when a request comes back 401, then replays it.
 *
 * Concurrent 401s share a single in-flight refresh via [refreshInFlight]. Without that, a page
 * that fires six queries on mount would issue six refreshes — and because refresh tokens are
 * single-use and rotate, five of them would present an already-spent token. The server treats
 * that as theft and revokes the entire family, signing the user out. So this is not an
 * optimisation; it is required for correctness against a rotating-token server.
 *
 * ## Why the refresh goes through its own client
 *
 * The refresh call must not pass through [authRetryMiddleware]. If it did, a refresh that itself
 * came back 401 — an expired or revoked refresh token, which is simply what an old session looks
 * like — would re-enter the middleware, call [ensureFreshToken], and be handed back the very
 * promise it is currently inside. That awaits itself and never settles: the console hangs
 * forever instead of returning the user to the sign-in page.
 *
 * The Android client solves this the same way, with an `@UnauthenticatedApi` qualifier that makes
 * the wrong injection a compile error. TypeScript has no equivalent, so it is a naming convention
 * and this comment.
 */
let refreshInFlight: Promise<boolean> | null = null

async function refreshAccessToken(): Promise<boolean> {
  const refreshToken = tokens.getRefreshToken()
  const tenantCode = tokens.getTenantCode()
  if (refreshToken === null || tenantCode === null) return false

  try {
    const response = await unauthenticatedAuthApi.refreshToken({
      xTenantCode: tenantCode,
      refreshTokenRequest: { refreshToken },
    })
    tokens.store(response, tenantCode)
    return true
  } catch {
    // A failed refresh means the session is over — expired, revoked, or reuse detected.
    tokens.clear()
    return false
  }
}

export async function ensureFreshToken(): Promise<boolean> {
  if (refreshInFlight !== null) return refreshInFlight

  refreshInFlight = refreshAccessToken().finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

const authRetryMiddleware: Middleware = {
  async post(context: ResponseContext) {
    if (context.response.status !== 401) return context.response

    const refreshed = await ensureFreshToken()
    if (!refreshed) {
      onSessionExpired()
      return context.response
    }

    // Replay with the new token, through the global `fetch` rather than `context.fetch`.
    // `context.fetch` is the runtime's middleware-wrapping fetch, so replaying through it runs
    // this same `post` hook again — and a request that is still 401 after a successful refresh
    // (a revoked session, a permission change mid-flight) would recurse until the stack gave out.
    // One retry is the whole policy; going through plain `fetch` is what makes it one.
    const accessToken = tokens.getAccessToken()
    return fetch(context.url, {
      ...context.init,
      headers: {
        ...context.init.headers,
        ...(accessToken !== null ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
    })
  },
}

/**
 * Called when the session cannot be recovered.
 *
 * A callback rather than a direct navigation because this module has no router. The auth provider
 * registers a handler on mount.
 */
let sessionExpiredHandler: (() => void) | null = null

export function onSessionExpiredHandler(handler: () => void): void {
  sessionExpiredHandler = handler
}

function onSessionExpired(): void {
  tokens.clear()
  sessionExpiredHandler?.()
}

const configuration = new Configuration({
  basePath: BASE_PATH,
  accessToken: () => tokens.getAccessToken() ?? '',
  middleware: [tenantHeaderMiddleware, authRetryMiddleware],
})

export const authApi = new AuthenticationApi(configuration)

/**
 * The same endpoints without the 401 retry, used only for the token refresh itself.
 *
 * Carries the tenant header, because `/v1/auth/token/refresh` requires it, but nothing that could
 * re-enter the refresh path. See [refreshInFlight] for what happens without this separation.
 */
const unauthenticatedAuthApi = new AuthenticationApi(
  new Configuration({ basePath: BASE_PATH, middleware: [tenantHeaderMiddleware] }),
)
export const meApi = new MeApi(configuration)
export const directoryApi = new DirectoryApi(configuration)
export const employeesApi = new EmployeesApi(configuration)
export const formsApi = new FormsApi(configuration)
export const referenceApi = new ReferenceApi(configuration)
export const loansApi = new LoansApi(configuration)
export const trainingApi = new TrainingApi(configuration)

export interface DashboardWidget {
  key: string
  title: string
  category: 'METRIC' | 'APPROVALS' | 'ACTION' | 'ALERT'
  value: string
  subtext: string
  trend?: string | null
  status?: 'NORMAL' | 'WARNING' | 'CRITICAL' | 'SUCCESS' | null
  deepLink: string
  permission?: string | null
}

export interface DashboardResponse {
  asOf: string
  greeting: string
  widgets: DashboardWidget[]
  quickActions: Array<{
    key: string
    label: string
    icon: string
    actionUri: string
  }>
}

export async function fetchDashboard(): Promise<DashboardResponse> {
  const token = tokens.getAccessToken()
  const res = await fetch(`${BASE_PATH}/v1/dashboard`, {
    headers: {
      Accept: 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(tokens.getTenantCode() ? { 'X-Tenant-Code': tokens.getTenantCode()! } : {}),
    },
  })
  if (!res.ok) {
    throw new Error(`Failed to fetch dashboard: ${res.status}`)
  }
  return (await res.json()) as DashboardResponse
}

/**
 * Extracts the machine-readable code from the standard error envelope.
 *
 * Clients localise from `code`, never from `message` — the server cannot reliably know the
 * caller's locale, and we ship in six languages (docs/03-architecture.md §9).
 */
export async function extractApiError(
  error: unknown,
): Promise<{ code: string; message: string; requestId?: string } | null> {
  if (!(error instanceof ResponseError)) return null
  try {
    // `clone()` because a response body can only be read once, and a caller may reasonably want
    // both the code and the per-field violations from the same failure.
    const body = (await error.response.clone().json()) as ApiErrorResponse
    return {
      code: body.error.code,
      message: body.error.message,
      ...(body.error.requestId !== undefined ? { requestId: body.error.requestId } : {}),
    }
  } catch {
    return { code: 'UNKNOWN', message: error.message }
  }
}

/**
 * Maps an error code to text for the user.
 *
 * A deliberate allow-list rather than a fallthrough to the server's `message`: server messages are
 * developer-facing English and sometimes carry internal detail. An unrecognised code gets a
 * generic line plus the request id, which is what support actually needs.
 */
const ERROR_MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: 'That username or password is not correct.',
  ACCOUNT_LOCKED: 'This account is locked after repeated failed attempts. Try again shortly.',
  ACCOUNT_DISABLED: 'This account is not active. Contact your administrator.',
  TENANT_NOT_FOUND: 'We could not find that organisation.',
  TOKEN_EXPIRED: 'Your session has expired. Please sign in again.',
  TOKEN_REUSE_DETECTED: 'Your session was ended for security reasons. Please sign in again.',
  INSUFFICIENT_PERMISSION: 'You do not have permission to do that.',
  RATE_LIMITED: 'Too many attempts. Please wait a moment and try again.',
  VALIDATION_FAILED: 'Some of the details are not valid. Check the highlighted fields.',
  FIELD_NOT_WRITABLE: 'You do not have permission to change one of those fields.',
  STALE_VERSION: 'Someone else changed this record while you were editing. Reload to see their changes.',
  CONTRADICTORY_UPDATE: 'A field was both set and cleared in the same save. Reload and try again.',
  CUSTOM_FIELD_VALIDATION_FAILED: 'Some of the details are not valid. Check the highlighted fields.',
  FIELD_VALIDATION_FAILED: 'Some of the details are not valid. Check the highlighted fields.',
  NOT_FOUND: 'That record does not exist, or you do not have access to it.',
}

/**
 * Per-field violations from a rejected save.
 *
 * The server reports **every** violation rather than stopping at the first, so the form can mark
 * all of them at once — fixing one at a time turns filling a form into a guessing game
 * (`CustomFieldValidator`). Returns an empty map when the error carries no field detail.
 */
export async function extractFieldViolations(error: unknown): Promise<Record<string, string>> {
  if (!(error instanceof ResponseError)) return {}
  try {
    const body = (await error.response.clone().json()) as {
      error?: { details?: { violations?: Array<{ field?: string; message?: string }> } }
    }
    const violations = body.error?.details?.violations ?? []
    return Object.fromEntries(
      violations
        .filter((v): v is { field: string; message: string } => Boolean(v.field) && Boolean(v.message))
        .map((v) => [v.field, v.message]),
    )
  } catch {
    return {}
  }
}

export function humaniseError(code: string, requestId?: string): string {
  const known = ERROR_MESSAGES[code]
  if (known !== undefined) return known
  return requestId !== undefined
    ? `Something went wrong. Quote reference ${requestId} to support.`
    : 'Something went wrong. Please try again.'
}

/* -------------------------------------------------------------------------- */
/* Users and Roles Administration APIs                                         */
/* -------------------------------------------------------------------------- */

export interface UserSummary {
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

export interface RoleSummary {
  id: string
  code: string
  name: string
  description: string
  isSystem: boolean
  permissions: string[]
  assignedUserCount?: number
}

export interface PermissionItem {
  key: string
  domain: string
  label: string
  description: string
}

export interface UserDevice {
  id: string
  deviceType?: string
  name?: string
  ipAddress?: string
  lastSeenAt?: string
  isCurrent?: boolean
  mfaEnrolled?: boolean
}

async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = tokens.getAccessToken()
  const tenantCode = tokens.getTenantCode()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(tenantCode ? { 'X-Tenant-Code': tenantCode } : {}),
    ...(options.headers as Record<string, string> | undefined),
  }

  let response = await fetch(`${BASE_PATH}${path}`, {
    ...options,
    headers,
  })

  if (response.status === 401) {
    const refreshed = await ensureFreshToken()
    if (refreshed) {
      const newToken = tokens.getAccessToken()
      response = await fetch(`${BASE_PATH}${path}`, {
        ...options,
        headers: {
          ...headers,
          ...(newToken ? { Authorization: `Bearer ${newToken}` } : {}),
        },
      })
    } else {
      onSessionExpired()
      throw new Error('Session expired')
    }
  }

  if (!response.ok) {
    let errorMessage = response.statusText
    try {
      const errorJson = (await response.clone().json()) as ApiErrorResponse
      if (errorJson.error?.message) {
        errorMessage = errorJson.error.message
      }
    } catch {
      // Not JSON response
    }
    throw new ResponseError(response, errorMessage || 'API request failed')
  }

  if (response.status === 204) {
    return undefined as unknown as T
  }

  return (await response.json()) as T
}

export const usersApi = {
  async listUsers(params?: { q?: string; status?: string; role?: string }): Promise<{ users: UserSummary[] }> {
    const search = new URLSearchParams()
    if (params?.q) search.set('q', params.q)
    if (params?.status) search.set('status', params.status)
    if (params?.role) search.set('role', params.role)
    const qs = search.toString()
    return apiFetch<{ users: UserSummary[] }>(`/v1/users${qs ? `?${qs}` : ''}`)
  },

  async getUser(id: string): Promise<UserSummary> {
    return apiFetch<UserSummary>(`/v1/users/${id}`)
  },

  async createUser(data: {
    username: string
    email: string
    role: string
    employeeCode?: string
    mustChangePassword?: boolean
  }): Promise<UserSummary> {
    return apiFetch<UserSummary>('/v1/users', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async updateUser(
    id: string,
    data: { status?: 'ACTIVE' | 'LOCKED' | 'PENDING_MFA' | 'DISABLED'; role?: string },
  ): Promise<UserSummary> {
    return apiFetch<UserSummary>(`/v1/users/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(data),
    })
  },

  async resetPassword(
    id: string,
    data?: { temporaryPassword?: string; mustChangePassword?: boolean },
  ): Promise<{ success: boolean; message: string }> {
    return apiFetch<{ success: boolean; message: string }>(`/v1/users/${id}/reset-password`, {
      method: 'POST',
      body: JSON.stringify(data ?? {}),
    })
  },

  async listDevices(userId: string): Promise<{ devices: UserDevice[] }> {
    return apiFetch<{ devices: UserDevice[] }>(`/v1/users/${userId}/devices`)
  },

  async revokeDevice(userId: string, deviceId: string): Promise<{ success: boolean }> {
    return apiFetch<{ success: boolean }>(`/v1/users/${userId}/devices/${deviceId}`, {
      method: 'DELETE',
    })
  },

  async revokeAllDevices(userId: string): Promise<{ success: boolean; revokedCount: number }> {
    return apiFetch<{ success: boolean; revokedCount: number }>(`/v1/users/${userId}/devices/revoke-all`, {
      method: 'POST',
    })
  },
}

export const rolesApi = {
  async listRoles(): Promise<{ roles: RoleSummary[] }> {
    return apiFetch<{ roles: RoleSummary[] }>('/v1/roles')
  },

  async createRole(data: {
    name: string
    code: string
    description: string
    permissions: string[]
  }): Promise<RoleSummary> {
    return apiFetch<RoleSummary>('/v1/roles', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async updateRole(
    id: string,
    data: { name?: string; description?: string; permissions?: string[] },
  ): Promise<RoleSummary> {
    return apiFetch<RoleSummary>(`/v1/roles/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    })
  },

  async deleteRole(id: string): Promise<{ success: boolean }> {
    return apiFetch<{ success: boolean }>(`/v1/roles/${id}`, {
      method: 'DELETE',
    })
  },

  async listPermissions(): Promise<{ permissions: PermissionItem[] }> {
    return apiFetch<{ permissions: PermissionItem[] }>('/v1/permissions')
  },
}

/* -------------------------------------------------------------------------- */
/* Organisation / Tenant Administration APIs (P0-WEB-05)                       */
/* -------------------------------------------------------------------------- */

export interface TenantModuleStatus {
  moduleKey: string
  enabled: boolean
  config?: Record<string, unknown>
  updatedAt?: string
}

export interface TenantDetail {
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
  modules: TenantModuleStatus[]
  createdAt: string
  updatedAt: string
}

export interface CreateTenantPayload {
  code: string
  name: string
  legalName?: string
  countryCode: string
  timezone: string
  defaultCurrency: string
  locale?: string
  dataRegion?: string
  isolationTier?: 'SHARED' | 'DEDICATED_SCHEMA' | 'DEDICATED_DATABASE'
  subscriptionPlan?: string
  adminEmail?: string
  modules?: Record<string, boolean>
}

export interface UpdateTenantPayload {
  name?: string
  legalName?: string
  timezone?: string
  defaultCurrency?: string
  locale?: string
  dataRegion?: string
  isolationTier?: 'SHARED' | 'DEDICATED_SCHEMA' | 'DEDICATED_DATABASE'
  subscriptionPlan?: string
  status?: 'PROVISIONING' | 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED'
  adminEmail?: string
}

export const tenantsApi = {
  async listTenants(params?: { q?: string; status?: string; plan?: string }): Promise<{ tenants: TenantDetail[] }> {
    const search = new URLSearchParams()
    if (params?.q) search.set('q', params.q)
    if (params?.status) search.set('status', params.status)
    if (params?.plan) search.set('plan', params.plan)
    const qs = search.toString()
    return apiFetch<{ tenants: TenantDetail[] }>(`/v1/tenants${qs ? `?${qs}` : ''}`)
  },

  async getTenant(id: string): Promise<TenantDetail> {
    return apiFetch<TenantDetail>(`/v1/tenants/${id}`)
  },

  async createTenant(data: CreateTenantPayload): Promise<TenantDetail> {
    return apiFetch<TenantDetail>('/v1/tenants', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async updateTenant(id: string, data: UpdateTenantPayload): Promise<TenantDetail> {
    return apiFetch<TenantDetail>(`/v1/tenants/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(data),
    })
  },

  async updateTenantModules(id: string, modules: Record<string, boolean>): Promise<TenantDetail> {
    return apiFetch<TenantDetail>(`/v1/tenants/${id}/modules`, {
      method: 'PUT',
      body: JSON.stringify({ modules }),
    })
  },
}

/* -------------------------------------------------------------------------- */
/* Leave & Absence Management APIs (P0/P2 Leave Engine)                        */
/* -------------------------------------------------------------------------- */

export type DayPortion = 'FULL_DAY' | 'FIRST_HALF' | 'SECOND_HALF'

export type LeaveApplicationStatus = 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'WITHDRAWN'

export type LeaveEventType = 'OPENING' | 'ACCRUAL' | 'TAKEN' | 'ADJUSTMENT' | 'CANCELLATION' | 'FORFEITURE'

export interface LeaveBalance {
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

export interface LeaveApplicationDayItem {
  date: string
  dayOfWeek: string
  isWorkingDay: boolean
  isPublicHoliday: boolean
  holidayName?: string
  portion: DayPortion
  hours: number
}

export interface LeaveApplicationItem {
  id: string
  employeeId: string
  employeeName: string
  leaveTypeId: string
  leaveTypeCode: string
  leaveTypeName: string
  startDate: string
  endDate: string
  dayPortion: DayPortion
  totalDays: number
  reason: string
  status: LeaveApplicationStatus
  submittedAt: string
  approvedAt?: string
  days: LeaveApplicationDayItem[]
}

export interface LeaveLedgerEntry {
  id: string
  date: string
  leaveTypeId: string
  leaveTypeCode: string
  leaveTypeName: string
  eventType: LeaveEventType
  daysCredited: number
  daysDebited: number
  balanceAfter: number
  referenceId?: string
  notes?: string
}

export interface LeaveEligibility {
  eligible: boolean
  workingDaysRequested: number
  balanceAvailable: number
  remainingAfter: number
  reasons: string[]
  days: LeaveApplicationDayItem[]
}

export interface LeaveApplicationRequest {
  leaveTypeId: string
  startDate: string
  endDate: string
  dayPortion?: DayPortion
  reason: string
}

export interface TeamMemberLeave {
  employeeId: string
  employeeName: string
  department: string
  leaveTypeCode: string
  leaveTypeName: string
  portion: DayPortion
  status: LeaveApplicationStatus
}

export interface TeamCalendarDay {
  date: string
  dayOfWeek: number
  isWeekend: boolean
  isHoliday: boolean
  holidayName?: string
  absences: TeamMemberLeave[]
  hasConflict: boolean
}

export const leaveApi = {
  async getBalances(): Promise<{ leaveYear: string; balances: LeaveBalance[] }> {
    return apiFetch<{ leaveYear: string; balances: LeaveBalance[] }>('/v1/leave/balances')
  },

  async getLedger(leaveTypeId?: string): Promise<{ ledger: LeaveLedgerEntry[] }> {
    const qs = leaveTypeId ? `?leaveTypeId=${encodeURIComponent(leaveTypeId)}` : ''
    return apiFetch<{ ledger: LeaveLedgerEntry[] }>(`/v1/leave/ledger${qs}`)
  },

  async getApplications(status?: string): Promise<{ applications: LeaveApplicationItem[] }> {
    const qs = status ? `?status=${encodeURIComponent(status)}` : ''
    return apiFetch<{ applications: LeaveApplicationItem[] }>(`/v1/leave/applications${qs}`)
  },

  async checkEligibility(data: LeaveApplicationRequest): Promise<LeaveEligibility> {
    return apiFetch<LeaveEligibility>('/v1/leave/eligibility', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async submitApplication(data: LeaveApplicationRequest): Promise<LeaveApplicationItem> {
    return apiFetch<LeaveApplicationItem>('/v1/leave/applications', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async cancelApplication(id: string, reason?: string): Promise<LeaveApplicationItem> {
    return apiFetch<LeaveApplicationItem>(`/v1/leave/applications/${id}/cancel`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    })
  },

  async getTeamCalendar(year: number, month: number): Promise<{ days: TeamCalendarDay[] }> {
    return apiFetch<{ days: TeamCalendarDay[] }>(`/v1/leave/team-calendar?year=${year}&month=${month}`)
  },

  async getPendingApprovals(): Promise<{ items: ApprovalItem[]; totalCount: number }> {
    return apiFetch<{ items: ApprovalItem[]; totalCount: number }>('/v1/approvals/pending')
  },

  async approveApplication(id: string, remarks?: string): Promise<{ id: string; status: string; message: string }> {
    return apiFetch<{ id: string; status: string; message: string }>(`/v1/approvals/${id}/decision`, {
      method: 'POST',
      body: JSON.stringify({ decision: 'APPROVE', remarks }),
    })
  },

  async rejectApplication(id: string, remarks: string): Promise<{ id: string; status: string; message: string }> {
    return apiFetch<{ id: string; status: string; message: string }>(`/v1/approvals/${id}/decision`, {
      method: 'POST',
      body: JSON.stringify({ decision: 'REJECT', remarks }),
    })
  },
}

export interface ApprovalItem {
  id: string
  type: string
  requesterId: string
  requesterName: string
  requesterDesignation: string
  departmentName: string
  submittedAt: string
  title: string
  summary: string
  status: string
  urgency: 'NORMAL' | 'URGENT'
  leaveDetails?: {
    leaveTypeName: string
    startDate: string
    endDate: string
    workingDays: number
    reason: string
    employeeBalanceDays: number
    teamCoverageWarning?: string
  }
}

/* -------------------------------------------------------------------------- */
/* Payroll Statutory Pipeline & Run Console APIs (P3-BE-21 / P3-WEB-07)        */
/* -------------------------------------------------------------------------- */

export interface PayGroup {
  id: string
  code: string
  name: string
  countryCode: string
  currency: string
  payFrequency: 'MONTHLY' | 'SEMI_MONTHLY' | 'BI_WEEKLY' | 'WEEKLY'
  standardDaysPerMonth: number
  isActive: boolean
}

export interface PayPeriod {
  id: string
  payGroupId: string
  code: string
  startDate: string
  endDate: string
  paymentDate: string
  status: 'OPEN' | 'PROCESSING' | 'APPROVED' | 'CLOSED'
}

export interface PayrollRun {
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

export interface PayrollResult {
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
  linesCount?: number
}

export interface PayrollResultLine {
  id: string
  payrollResultId: string
  lineCategory: 'EARNING' | 'STATUTORY_DEDUCTION' | 'EMPLOYER_CONTRIBUTION' | 'VOLUNTARY_DEDUCTION' | 'TAX'
  itemCode: string
  itemName: string
  amount: number
  isStatutory: boolean
  calculationTrace?: string
}

export interface PayrollVarianceItem {
  employeeCode: string
  employeeName: string
  department: string
  previousGross: number
  currentGross: number
  varianceAmount: number
  variancePercent: number
  reasons: string[]
}

export interface PayrollVarianceSummary {
  grossVariancePercent: number
  netVariancePercent: number
  headcountDifference: number
  anomalies: PayrollVarianceItem[]
}

export interface CalculatePayrollPayload {
  payGroupId: string
  payPeriodId: string
  includeLossOfPay?: boolean
}

export interface BankAdvicePayload {
  format: 'CSV_STANDARD' | 'ACH_NACHA'
  companyAccount: string
  paymentDate: string
}

export interface BankAdviceResponse {
  filename: string
  content: string
  totalRecords: number
  totalAmount: number
  batchHash: string
  mimeType: string
}

export interface EpfMemberRecord {
  memberNo: string
  nic: string
  fullName: string
  initialsAndSurname: string
  department: string
  contributoryEarnings: number
  memberShare8: number
  employerShare12: number
  totalContribution20: number
  status: 'ACTIVE' | 'NEW' | 'EXITED'
}

export interface EpfCFormResponse {
  employerRegistrationNo: string
  employerName: string
  employerAddress: string
  contributionMonth: string
  paymentDueDate: string
  remittanceRef: string
  chequeOrTransferDate: string
  currency: string
  totalContributoryEarnings: number
  totalMemberShare8: number
  totalEmployerShare12: number
  totalRemittance20: number
  memberCount: number
  members: EpfMemberRecord[]
  electronicFile: {
    filename: string
    content: string
    mimeType: string
  }
}

export interface EtfMemberRecord {
  memberNo: string
  nic: string
  fullName: string
  department: string
  contributoryEarnings: number
  employerContribution3: number
}

export interface EtfScheduleResponse {
  employerRegistrationNo: string
  employerName: string
  contributionMonth: string
  currency: string
  totalContributoryEarnings: number
  totalEmployerContribution3: number
  memberCount: number
  members: EtfMemberRecord[]
  electronicFile: {
    filename: string
    content: string
    mimeType: string
  }
}

export interface T10MonthlyBreakdown {
  monthName: string
  periodCode: string
  grossRemuneration: number
  nonCashBenefits: number
  totalAssessableRemuneration: number
  apitTaxDeducted: number
  remittanceDate: string
  remittanceRef: string
}

export interface T10CertificateData {
  employer: {
    name: string
    tin: string
    address: string
    employerEpfNo: string
  }
  employee: {
    id: string
    code: string
    fullName: string
    nic: string
    tin: string
    designation: string
    department: string
    epfNo: string
  }
  assessmentYear: string
  periodCovered: string
  monthlySchedule: T10MonthlyBreakdown[]
  totals: {
    annualGrossRemuneration: number
    annualNonCashBenefits: number
    annualAssessableRemuneration: number
    statutoryReliefThreshold: number
    taxableRemuneration: number
    annualApitTaxDeducted: number
    annualNetPaid: number
  }
  declaration: {
    statement: string
    signatoryName: string
    signatoryTitle: string
    issuedDate: string
    digitalSealHash: string
  }
}

export const payrollApi = {
  async listPayGroups(): Promise<{ payGroups: PayGroup[] }> {
    return apiFetch<{ payGroups: PayGroup[] }>('/v1/payroll/pay-groups')
  },

  async listPayPeriods(payGroupId?: string): Promise<{ payPeriods: PayPeriod[] }> {
    const qs = payGroupId ? `?payGroupId=${encodeURIComponent(payGroupId)}` : ''
    return apiFetch<{ payPeriods: PayPeriod[] }>(`/v1/payroll/pay-periods${qs}`)
  },

  async getRuns(payPeriodId?: string): Promise<{ runs: PayrollRun[] }> {
    const qs = payPeriodId ? `?payPeriodId=${encodeURIComponent(payPeriodId)}` : ''
    return apiFetch<{ runs: PayrollRun[] }>(`/v1/payroll/runs${qs}`)
  },

  async getRun(runId: string): Promise<PayrollRun> {
    return apiFetch<PayrollRun>(`/v1/payroll/runs/${runId}`)
  },

  async calculateRun(data: CalculatePayrollPayload): Promise<PayrollRun> {
    return apiFetch<PayrollRun>('/v1/payroll/runs/calculate', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async approveRun(runId: string): Promise<PayrollRun> {
    return apiFetch<PayrollRun>(`/v1/payroll/runs/${runId}/approve`, {
      method: 'POST',
    })
  },

  async rejectRun(runId: string, reason?: string): Promise<PayrollRun> {
    return apiFetch<PayrollRun>(`/v1/payroll/runs/${runId}/reject`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    })
  },

  async commitRun(runId: string): Promise<PayrollRun> {
    return apiFetch<PayrollRun>(`/v1/payroll/runs/${runId}/commit`, {
      method: 'POST',
    })
  },

  async listResults(
    runId: string,
    params?: { department?: string; q?: string },
  ): Promise<{ results: PayrollResult[] }> {
    const search = new URLSearchParams()
    if (params?.department) search.set('department', params.department)
    if (params?.q) search.set('q', params.q)
    const qs = search.toString()
    return apiFetch<{ results: PayrollResult[] }>(`/v1/payroll/runs/${runId}/results${qs ? `?${qs}` : ''}`)
  },

  async getResultDetails(
    runId: string,
    resultId: string,
  ): Promise<{ result: PayrollResult; lines: PayrollResultLine[] }> {
    return apiFetch<{ result: PayrollResult; lines: PayrollResultLine[] }>(
      `/v1/payroll/runs/${runId}/results/${resultId}`,
    )
  },

  async getVariance(runId: string): Promise<PayrollVarianceSummary> {
    return apiFetch<PayrollVarianceSummary>(`/v1/payroll/runs/${runId}/variance`)
  },

  async generateBankAdvice(runId: string, data: BankAdvicePayload): Promise<BankAdviceResponse> {
    return apiFetch<BankAdviceResponse>(`/v1/payroll/runs/${runId}/bank-advice`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async getEpfCForm(runId: string): Promise<EpfCFormResponse> {
    return apiFetch<EpfCFormResponse>(`/v1/payroll/runs/${runId}/statutory/epf-cform`)
  },

  async getEtfSchedule(runId: string): Promise<EtfScheduleResponse> {
    return apiFetch<EtfScheduleResponse>(`/v1/payroll/runs/${runId}/statutory/etf-schedule`)
  },

  async getT10Certificate(
    runId: string,
    employeeId: string,
    assessmentYear?: string,
  ): Promise<T10CertificateData> {
    const qs = assessmentYear ? `?year=${encodeURIComponent(assessmentYear)}` : ''
    return apiFetch<T10CertificateData>(
      `/v1/payroll/runs/${runId}/statutory/t10-certificate/${employeeId}${qs}`,
    )
  },
}

// ---------------------------------------------------------------------------
// Attendance & Shift Roster Engine (P2-BE / P2-WEB)
// ---------------------------------------------------------------------------

export type ShiftType = 'FIXED' | 'ROTATING' | 'SPLIT' | 'NIGHT' | 'FLEXIBLE' | 'OPEN'

export interface ShiftItem {
  id: string
  code: string
  name: string
  shiftType: ShiftType
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

export interface ShiftScheduleItem {
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

export interface RosterWeekDay {
  date: string
  dayName: string
  dayOfMonth: number
  isWeekend: boolean
  isHoliday: boolean
  holidayName?: string
}

export type PunchType = 'IN' | 'OUT' | 'BREAK_IN' | 'BREAK_OUT' | 'AUTO'
export type PunchSource = 'BIOMETRIC_DEVICE' | 'MOBILE_APP' | 'KIOSK' | 'WEB_PORTAL' | 'MANUAL_IMPORT'
export type GeofenceStatus = 'INSIDE' | 'OUTSIDE' | 'UNKNOWN' | 'NOT_APPLICABLE'

export interface RawPunchItem {
  id: string
  employeeId: string
  employeeCode: string
  employeeName: string
  department: string
  punchedAt: string
  punchType: PunchType
  source: PunchSource
  deviceId?: string
  locationName?: string
  geoLat?: number
  geoLng?: number
  geoAccuracyM?: number
  geofenceStatus: GeofenceStatus
  isMockLocation: boolean
  clientIdempotencyKey?: string
  recordedOffline: boolean
  syncedAt: string
}

export type DayStatus = 'PRESENT' | 'HALF_DAY' | 'ABSENT' | 'ON_LEAVE' | 'REST_DAY' | 'HOLIDAY' | 'NO_SHOW'

export interface DailyAttendanceItem {
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
  dayStatus: DayStatus
  leaveTypeName?: string
  leaveDays?: number
  anomalyFlags: string[]
  calculationTrace?: string
  computedAt: string
}

export interface AttendancePayrollSummaryItem {
  employeeId: string
  employeeCode: string
  employeeName: string
  department: string
  normalOtMinutes: number
  restDayOtMinutes: number
  holidayOtMinutes: number
  otGrossEarnings: number
  lateMinutesTotal: number
  latePenaltyDeduction: number
  unpaidAbsenceDays: number
}

export interface AttendancePayrollSummary {
  payPeriodId: string
  totalNormalOtHours: number
  totalRestDayOtHours: number
  totalHolidayOtHours: number
  totalOtEarningsAmount: number
  totalLatePenaltyDeductionAmount: number
  totalUnpaidAbsenceDays: number
  employeeCountWithOt: number
  employeeCountWithLatePenalty: number
  employeeCountWithUnpaidAbsence: number
  items: AttendancePayrollSummaryItem[]
}

export const attendanceApi = {
  async listShifts(): Promise<{ shifts: ShiftItem[] }> {
    return apiFetch<{ shifts: ShiftItem[] }>('/v1/attendance/shifts')
  },

  async getRoster(params?: { startDate?: string; endDate?: string; department?: string }): Promise<{
    days: RosterWeekDay[]
    schedules: Record<string, ShiftScheduleItem[]>
    employees: Array<{ id: string; code: string; name: string; department: string }>
  }> {
    const search = new URLSearchParams()
    if (params?.startDate) search.set('startDate', params.startDate)
    if (params?.endDate) search.set('endDate', params.endDate)
    if (params?.department) search.set('department', params.department)
    const qs = search.toString()
    return apiFetch<{
      days: RosterWeekDay[]
      schedules: Record<string, ShiftScheduleItem[]>
      employees: Array<{ id: string; code: string; name: string; department: string }>
    }>(`/v1/attendance/roster${qs ? `?${qs}` : ''}`)
  },

  async assignShift(payload: {
    employeeId: string
    workDate: string
    shiftId?: string
    isRestDay?: boolean
  }): Promise<ShiftScheduleItem> {
    return apiFetch<ShiftScheduleItem>('/v1/attendance/shifts/assign', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  async listPunches(params?: {
    date?: string
    employeeId?: string
    flag?: string
    source?: string
  }): Promise<{ punches: RawPunchItem[] }> {
    const search = new URLSearchParams()
    if (params?.date) search.set('date', params.date)
    if (params?.employeeId) search.set('employeeId', params.employeeId)
    if (params?.flag) search.set('flag', params.flag)
    if (params?.source) search.set('source', params.source)
    const qs = search.toString()
    return apiFetch<{ punches: RawPunchItem[] }>(`/v1/attendance/punches${qs ? `?${qs}` : ''}`)
  },

  async ingestPunch(payload: {
    employeeId: string
    punchedAt: string
    punchType: PunchType
    source: PunchSource
    deviceId?: string
    locationName?: string
    isMockLocation?: boolean
    geofenceStatus?: GeofenceStatus
  }): Promise<RawPunchItem> {
    return apiFetch<RawPunchItem>('/v1/attendance/punches/ingest', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  async listDailyAttendance(params?: {
    date?: string
    department?: string
    status?: string
    anomaly?: string
  }): Promise<{
    records: DailyAttendanceItem[]
    summary: {
      totalEmployees: number
      presentCount: number
      halfDayCount: number
      absentCount: number
      restDayCount: number
      holidayCount: number
      anomaliesCount: number
      totalOtHours: number
      totalLateMinutes: number
    }
  }> {
    const search = new URLSearchParams()
    if (params?.date) search.set('date', params.date)
    if (params?.department) search.set('department', params.department)
    if (params?.status) search.set('status', params.status)
    if (params?.anomaly) search.set('anomaly', params.anomaly)
    const qs = search.toString()
    return apiFetch<{
      records: DailyAttendanceItem[]
      summary: {
        totalEmployees: number
        presentCount: number
        halfDayCount: number
        absentCount: number
        restDayCount: number
        holidayCount: number
        anomaliesCount: number
        totalOtHours: number
        totalLateMinutes: number
      }
    }>(`/v1/attendance/daily${qs ? `?${qs}` : ''}`)
  },

  async getDailyAttendanceDetails(id: string): Promise<DailyAttendanceItem> {
    return apiFetch<DailyAttendanceItem>(`/v1/attendance/daily/${id}`)
  },

  async recomputeAttendance(params?: { date?: string; employeeId?: string }): Promise<{ recomputedCount: number }> {
    return apiFetch<{ recomputedCount: number }>('/v1/attendance/recompute', {
      method: 'POST',
      body: JSON.stringify(params ?? {}),
    })
  },

  async getPayrollVariableInputs(params?: { payPeriodId?: string }): Promise<AttendancePayrollSummary> {
    const search = new URLSearchParams()
    if (params?.payPeriodId) search.set('payPeriodId', params.payPeriodId)
    const qs = search.toString()
    return apiFetch<AttendancePayrollSummary>(`/v1/attendance/payroll-variable-summary${qs ? `?${qs}` : ''}`)
  },

  async listDevices(): Promise<{ devices: BiometricDeviceItem[] }> {
    return apiFetch<{ devices: BiometricDeviceItem[] }>('/v1/attendance/devices')
  },

  async createDevice(payload: {
    name: string
    serialNumber: string
    vendor?: string
    modelName?: string
    ipAddress?: string
    port?: number
    locationName?: string
    direction?: string
  }): Promise<BiometricDeviceItem> {
    return apiFetch<BiometricDeviceItem>('/v1/attendance/devices', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  async updateDevice(
    id: string,
    payload: {
      name?: string
      modelName?: string
      ipAddress?: string
      port?: number
      locationName?: string
      direction?: string
      status?: string
    },
  ): Promise<BiometricDeviceItem> {
    return apiFetch<BiometricDeviceItem>(`/v1/attendance/devices/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    })
  },

  async deleteDevice(id: string): Promise<void> {
    await apiFetch<void>(`/v1/attendance/devices/${id}`, {
      method: 'DELETE',
    })
  },

  async pingDevice(id: string): Promise<BiometricDeviceItem> {
    return apiFetch<BiometricDeviceItem>(`/v1/attendance/devices/${id}/ping`, {
      method: 'POST',
    })
  },

  async simulatePunch(payload: {
    deviceSerialNumber: string
    deviceUserId: string
    punchType?: string
    verificationType?: string
  }): Promise<{
    totalProcessed: number
    totalAccepted: number
    totalDuplicates: number
    totalRejected: number
    message: string
  }> {
    return apiFetch<{
      totalProcessed: number
      totalAccepted: number
      totalDuplicates: number
      totalRejected: number
      message: string
    }>('/v1/attendance/devices/simulate-punch', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  async getLiveStream(limit: number = 20): Promise<{ punches: BiometricLivePunchItem[] }> {
    return apiFetch<{ punches: BiometricLivePunchItem[] }>(`/v1/attendance/devices/live-stream?limit=${limit}`)
  },
}

export interface BiometricDeviceItem {
  id: string
  name: string
  serialNumber: string
  vendor: string
  modelName?: string | null
  ipAddress?: string | null
  port: number
  locationName?: string | null
  direction: string
  status: string
  lastHeartbeatAt?: string | null
  lastSyncAt?: string | null
  totalPunchesLogged: number
  firmwareVersion?: string | null
}

export interface BiometricLivePunchItem {
  id: string
  employeeId: string
  employeeCode: string
  employeeName: string
  department: string
  punchedAt: string
  punchType: string
  verificationType: string
  deviceSerialNumber: string
  deviceName: string
  locationName: string
}


/* -------------------------------------------------------------------------- */
/* Recruitment, ATS & Interview Evaluation APIs (V21)                         */
/* -------------------------------------------------------------------------- */

export type VacancyStatus = 'DRAFT' | 'OPEN' | 'ON_HOLD' | 'CLOSED' | 'FILLED'
export type ExperienceLevel = 'ENTRY_LEVEL' | 'MID_LEVEL' | 'SENIOR_LEVEL' | 'LEAD' | 'EXECUTIVE'
export type ApplicationStage =
  | 'APPLIED'
  | 'SCREENING'
  | 'INTERVIEW'
  | 'INTERVIEW_ROUND_1'
  | 'INTERVIEW_ROUND_2'
  | 'TECHNICAL_ASSESSMENT'
  | 'FINAL_INTERVIEW'
  | 'OFFER'
  | 'OFFER_EXTENDED'
  | 'OFFER_ACCEPTED'
  | 'OFFER_DECLINED'
  | 'HIRED'
  | 'REJECTED'
  | 'WITHDRAWN'

export type ApplicationStatus = 'ACTIVE' | 'ARCHIVED' | 'HIRED' | 'REJECTED'
export type InterviewType = 'PHONE_SCREEN' | 'TECHNICAL' | 'SYSTEM_DESIGN' | 'BEHAVIORAL' | 'HR' | 'EXECUTIVE'
export type InterviewStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'RESCHEDULED'
export type ScorecardRecommendation = 'STRONG_HIRE' | 'HIRE' | 'NEUTRAL' | 'NO_HIRE' | 'STRONG_NO_HIRE'

export interface VacancyItem {
  id: string
  jobCode: string
  vacancyCode?: string
  title: string
  department?: string
  departmentId?: string
  departmentName?: string
  location: string
  employmentType: string
  experienceLevel: ExperienceLevel
  minSalary?: number
  maxSalary?: number
  currency: string
  description: string
  requirements?: string
  openPositions: number
  targetHireCount?: number
  filledPositions: number
  targetHireDate?: string
  openedDate?: string
  closingDate?: string
  status: VacancyStatus
  applicantCount?: number
}

export interface VacancyCreateRequest {
  title: string
  jobCode?: string
  departmentId?: string
  departmentName?: string
  location?: string
  employmentType?: string
  experienceLevel?: ExperienceLevel
  minSalary?: number
  maxSalary?: number
  currency?: string
  description?: string
  jobDescription?: string
  requirements?: string
  openPositions?: number
  targetHireCount?: number
  targetHireDate?: string
  closingDate?: string
}

export interface CandidateItem {
  id: string
  firstName: string
  lastName: string
  email: string
  phone?: string
  currentCompany?: string
  currentTitle?: string
  yearsOfExperience?: number
  resumeUrl?: string
  portfolioUrl?: string
  linkedinUrl?: string
  skills?: string[]
  notes?: string
}

export interface CandidateCreateRequest {
  firstName: string
  lastName: string
  email: string
  phone?: string
  currentCompany?: string
  currentTitle?: string
  yearsOfExperience?: number
  experienceYears?: number
  resumeUrl?: string
  portfolioUrl?: string
  linkedinUrl?: string
  skills?: string[]
  notes?: string
  source?: string
}

export interface ApplicationItem {
  id: string
  applicationNumber?: string
  vacancyId: string
  vacancyTitle?: string
  candidateId: string
  candidateName?: string
  candidateEmail?: string
  stage: ApplicationStage
  status?: ApplicationStatus
  rating?: number
  source: string
  appliedDate: string
  rejectionReason?: string
}

export interface ApplicationCreateRequest {
  vacancyId: string
  candidateId: string
  source?: string
  rating?: number
}

export interface ApplicationStageUpdateRequest {
  stage: ApplicationStage
  notes?: string
  rejectionReason?: string
}

export interface InterviewItem {
  id: string
  applicationId: string
  candidateName?: string
  vacancyTitle?: string
  interviewRound?: number
  title?: string
  interviewType: InterviewType
  scheduledStart?: string
  scheduledEnd?: string
  scheduledAt: string
  durationMinutes?: number
  interviewerName?: string
  locationOrLink?: string
  meetingLink?: string
  status: InterviewStatus
  notes?: string
  overallScore?: number
  panelMembers?: Array<{ employeeId: string; name: string; isLead: boolean }>
}

export interface InterviewScheduleRequest {
  applicationId: string
  interviewRound?: number
  title?: string
  interviewType?: InterviewType | string
  scheduledStart?: string
  scheduledEnd?: string
  scheduledAt?: string
  durationMinutes?: number
  interviewerName?: string
  locationOrLink?: string
  meetingLink?: string
  notes?: string
  panelEmployeeIds?: string[]
}

export interface ScorecardItem {
  id: string
  interviewId: string
  interviewerEmployeeId?: string
  interviewerName?: string
  overallRecommendation?: ScorecardRecommendation
  overallRating?: number
  technicalSkillRating?: number
  communicationRating?: number
  problemSolvingRating?: number
  culturalFitRating?: number
  strengths?: string
  weaknesses?: string
  summaryNotes?: string
  submittedAt: string
}

export interface ScorecardSubmitRequest {
  interviewerName?: string
  overallRecommendation?: ScorecardRecommendation
  recommendation?: ScorecardRecommendation | string
  overallRating?: number
  technicalSkillRating?: number
  communicationRating?: number
  problemSolvingRating?: number
  culturalFitRating?: number
  strengths?: string
  weaknesses?: string
  summaryNotes?: string
  feedback?: string
  criteriaRatings?: Array<{ criteria: string; rating: number }>
}

export interface OfferDetail {
  id: string
  applicationId: string
  candidateName?: string
  vacancyTitle?: string
  offerNumber?: string
  basicSalary?: number
  offeredSalary?: number
  allowances?: number
  currency: string
  joiningDate: string
  expiryDate?: string
  validUntil?: string
  status: 'DRAFT' | 'PENDING_APPROVAL' | 'EXTENDED' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED' | 'SENT'
  terms?: string
}

export interface OfferCreateRequest {
  basicSalary?: number
  offeredSalary?: number
  allowances?: number
  currency?: string
  joiningDate: string
  expiryDate?: string
  validUntil?: string
  status?: string
  terms?: string
}

export const recruitmentApi = {
  async getVacancies(params?: { status?: string; departmentId?: string }): Promise<{ vacancies: VacancyItem[]; items: VacancyItem[] }> {
    const search = new URLSearchParams()
    if (params?.status) search.set('status', params.status)
    if (params?.departmentId) search.set('departmentId', params.departmentId)
    const qs = search.toString()
    const res = await apiFetch<any>(`/v1/recruitment/vacancies${qs ? `?${qs}` : ''}`)
    const list = res.vacancies ?? res.items ?? []
    return { vacancies: list, items: list }
  },

  async createVacancy(data: VacancyCreateRequest): Promise<VacancyItem> {
    return apiFetch<VacancyItem>('/v1/recruitment/vacancies', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async getVacancy(id: string): Promise<VacancyItem> {
    return apiFetch<VacancyItem>(`/v1/recruitment/vacancies/${id}`)
  },

  async getCandidates(params?: { query?: string }): Promise<{ candidates: CandidateItem[]; items: CandidateItem[] }> {
    const search = new URLSearchParams()
    if (params?.query) search.set('query', params.query)
    const qs = search.toString()
    const res = await apiFetch<any>(`/v1/recruitment/candidates${qs ? `?${qs}` : ''}`)
    const list = res.candidates ?? res.items ?? []
    return { candidates: list, items: list }
  },

  async createCandidate(data: CandidateCreateRequest): Promise<CandidateItem> {
    return apiFetch<CandidateItem>('/v1/recruitment/candidates', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async getApplications(params?: {
    vacancyId?: string
    stage?: string
    status?: string
  }): Promise<{ applications: ApplicationItem[]; items: ApplicationItem[] }> {
    const search = new URLSearchParams()
    if (params?.vacancyId) search.set('vacancyId', params.vacancyId)
    if (params?.stage) search.set('stage', params.stage)
    if (params?.status) search.set('status', params.status)
    const qs = search.toString()
    const res = await apiFetch<any>(`/v1/recruitment/applications${qs ? `?${qs}` : ''}`)
    const list = res.applications ?? res.items ?? []
    return { applications: list, items: list }
  },

  async createApplication(data: ApplicationCreateRequest): Promise<ApplicationItem> {
    return apiFetch<ApplicationItem>('/v1/recruitment/applications', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async updateApplicationStage(id: string, data: ApplicationStageUpdateRequest): Promise<ApplicationItem> {
    return apiFetch<ApplicationItem>(`/v1/recruitment/applications/${id}/stage`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async getInterviews(params?: {
    applicationId?: string
    interviewerEmployeeId?: string
    status?: string
  }): Promise<{ interviews: InterviewItem[]; items: InterviewItem[] }> {
    const search = new URLSearchParams()
    if (params?.applicationId) search.set('applicationId', params.applicationId)
    if (params?.interviewerEmployeeId) search.set('interviewerEmployeeId', params.interviewerEmployeeId)
    if (params?.status) search.set('status', params.status)
    const qs = search.toString()
    const res = await apiFetch<any>(`/v1/recruitment/interviews${qs ? `?${qs}` : ''}`)
    const list = res.interviews ?? res.items ?? []
    return { interviews: list, items: list }
  },

  async scheduleInterview(data: InterviewScheduleRequest): Promise<InterviewItem> {
    return apiFetch<InterviewItem>('/v1/recruitment/interviews', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async submitScorecard(interviewId: string, data: ScorecardSubmitRequest): Promise<ScorecardItem> {
    return apiFetch<ScorecardItem>(`/v1/recruitment/interviews/${interviewId}/scorecard`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async getApplicationOffer(applicationId: string): Promise<OfferDetail> {
    return apiFetch<OfferDetail>(`/v1/recruitment/applications/${applicationId}/offer`)
  },

  async createApplicationOffer(applicationId: string, data: OfferCreateRequest): Promise<OfferDetail> {
    return apiFetch<OfferDetail>(`/v1/recruitment/applications/${applicationId}/offer`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },
}

/* -------------------------------------------------------------------------- */
/* Document Management & Digital Signatures APIs (V23)                         */
/* -------------------------------------------------------------------------- */

export interface DocumentFolderItem {
  id: string
  parentId?: string | null
  folderName?: string
  name?: string
  path?: string
  accessScope?: 'PUBLIC' | 'ROLE_RESTRICTED' | 'CONFIDENTIAL'
  accessLevel?: string
  isSystem?: boolean
  documentCount?: number
}

export interface CompanyDocumentItem {
  id: string
  folderId?: string
  folderName?: string
  employeeId?: string
  employeeName?: string
  title: string
  description?: string
  documentCategory?: 'POLICY' | 'CONTRACT' | 'LETTER' | 'CERTIFICATE' | 'FORM' | 'GENERAL' | string
  category?: string
  currentVersionNumber?: number
  currentVersion?: number
  fileName: string
  fileSizeBytes: number
  mimeType?: string
  storageKey?: string
  status?: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED' | 'TRASHED' | string
  isConfidential?: boolean
  retentionUntil?: string
  createdByName?: string
  uploadedBy?: string
  createdAt?: string
  uploadedAt?: string
  tags?: string[]
}

export interface DocumentCreateRequest {
  folderId?: string
  employeeId?: string
  title: string
  description?: string
  documentCategory?: string
  category?: string
  tags?: string[]
  fileName: string
  fileSizeBytes: number
  mimeType?: string
  storageKey?: string
  isConfidential?: boolean
  retentionUntil?: string
}

export interface DocumentVersionItem {
  id?: string
  documentId?: string
  versionNumber: number
  fileName: string
  fileSizeBytes: number
  changelog?: string
  changeSummary?: string
  uploadedByName?: string
  uploadedBy?: string
  createdAt?: string
  uploadedAt?: string
  document?: CompanyDocumentItem
  version?: DocumentVersionItem
}

export interface DocumentDetailResponse {
  document: CompanyDocumentItem
  versions: DocumentVersionItem[]
  tags?: string[]
}

export interface DocumentTemplateItem {
  id: string
  templateCode: string
  title: string
  category: 'HR_LETTER' | 'CONTRACT' | 'POLICY' | 'CERTIFICATE' | string
  contentTemplate?: string
  placeholders?: string[]
  dynamicFields?: string[]
  requiresSignature?: boolean
  isActive: boolean
}

export interface LetterRequestItem {
  id: string
  requestNumber?: string
  employeeId: string
  employeeName?: string
  templateId?: string
  templateTitle?: string
  templateName?: string
  purpose: string
  requiredDate?: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'GENERATED' | 'REQUESTED' | string
  requestedAt?: string
  createdAt?: string
  approvedAt?: string
  generatedDocumentId?: string
}

export interface LetterRequestCreateRequest {
  templateId: string
  employeeId?: string
  employeeName?: string
  purpose: string
  requiredDate?: string
  customVariables?: Record<string, string>
}

export interface SignatureSignerItem {
  id?: string
  requestId?: string
  signerEmployeeId?: string
  signerName: string
  signerEmail: string
  signingOrder?: number
  status: 'PENDING' | 'SIGNED' | 'DECLINED' | string
  signatureMethod?: 'DRAWN' | 'TYPED' | 'CERTIFICATE' | string
  signatureType?: string
  signatureData?: string
  signedAt?: string
  declineReason?: string
}

export interface SignatureRequestItem {
  id: string
  documentId?: string
  documentTitle?: string
  title: string
  workflowType?: 'PARALLEL' | 'SEQUENTIAL' | string
  status: 'PENDING' | 'PARTIALLY_SIGNED' | 'COMPLETED' | 'DECLINED' | 'EXPIRED' | string
  dueDate?: string
  deadline?: string
  requestedByName?: string
  createdAt: string
  completedAt?: string
  signers: SignatureSignerItem[]
}

export interface SignatureAuditLogItem {
  id: string
  requestId: string
  eventType: 'CREATED' | 'VIEWED' | 'SIGNED' | 'DECLINED' | 'COMPLETED' | string
  actorName: string
  ipAddress?: string
  documentHashSha256: string
  details?: Record<string, unknown>
  createdAt: string
}

export interface SignatureRequestDetailResponse {
  request: SignatureRequestItem
  signatureRequest: SignatureRequestItem
  auditLogs: SignatureAuditLogItem[]
  auditTrail: SignatureAuditLogItem[]
}

export interface SignatureRequestCreateRequest {
  documentId?: string
  documentTitle?: string
  title: string
  workflowType?: 'PARALLEL' | 'SEQUENTIAL' | string
  dueDate?: string
  deadline?: string
  signers: Array<{
    name?: string
    signerName?: string
    email?: string
    signerEmail?: string
    employeeId?: string
    signingOrder?: number
  }>
}

export interface SignatureSignRequest {
  signatureMethod?: 'DRAWN' | 'TYPED' | 'CERTIFICATE' | string
  signatureType?: string
  signatureData?: string
  signerEmail?: string
}

export const documentsApi = {
  async listFolders(): Promise<{ folders: DocumentFolderItem[] }> {
    return apiFetch<{ folders: DocumentFolderItem[] }>('/v1/documents/folders')
  },

  async createFolder(data: {
    name?: string
    folderName?: string
    parentId?: string | null
    accessLevel?: string
    accessScope?: 'PUBLIC' | 'ROLE_RESTRICTED' | 'CONFIDENTIAL'
  }): Promise<DocumentFolderItem> {
    return apiFetch<DocumentFolderItem>('/v1/documents/folders', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async listDocuments(params?: {
    folderId?: string
    category?: string
    search?: string
  }): Promise<{ documents: CompanyDocumentItem[] }> {
    const search = new URLSearchParams()
    if (params?.folderId) search.set('folderId', params.folderId)
    if (params?.category) search.set('category', params.category)
    if (params?.search) search.set('search', params.search)
    const qs = search.toString()
    return apiFetch<{ documents: CompanyDocumentItem[] }>(`/v1/documents${qs ? `?${qs}` : ''}`)
  },

  async createDocument(data: DocumentCreateRequest): Promise<CompanyDocumentItem> {
    return apiFetch<CompanyDocumentItem>('/v1/documents', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async getDocumentDetails(id: string): Promise<DocumentDetailResponse> {
    return apiFetch<DocumentDetailResponse>(`/v1/documents/${id}`)
  },

  async createDocumentVersion(
    id: string,
    data: { fileName: string; fileSizeBytes?: number; changelog?: string; changeSummary?: string; mimeType?: string },
  ): Promise<{ version: DocumentVersionItem; document: CompanyDocumentItem }> {
    return apiFetch<{ version: DocumentVersionItem; document: CompanyDocumentItem }>(`/v1/documents/${id}/versions`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async listTemplates(): Promise<{ templates: DocumentTemplateItem[] }> {
    return apiFetch<{ templates: DocumentTemplateItem[] }>('/v1/documents/templates')
  },

  async getTemplate(id: string): Promise<DocumentTemplateItem> {
    return apiFetch<DocumentTemplateItem>(`/v1/documents/templates/${id}`)
  },

  async listLetterRequests(params?: {
    employeeId?: string
    status?: string
  }): Promise<{ requests: LetterRequestItem[]; letterRequests: LetterRequestItem[] }> {
    const search = new URLSearchParams()
    if (params?.employeeId) search.set('employeeId', params.employeeId)
    if (params?.status) search.set('status', params.status)
    const qs = search.toString()
    const res = await apiFetch<any>(`/v1/documents/letter-requests${qs ? `?${qs}` : ''}`)
    const list = res.letterRequests ?? res.requests ?? []
    return { requests: list, letterRequests: list }
  },

  async createLetterRequest(data: LetterRequestCreateRequest): Promise<LetterRequestItem> {
    return apiFetch<LetterRequestItem>('/v1/documents/letter-requests', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async approveLetterRequest(id: string, comments?: string): Promise<LetterRequestItem> {
    return apiFetch<LetterRequestItem>(`/v1/documents/letter-requests/${id}/approve`, {
      method: 'POST',
      body: JSON.stringify({ comments }),
    })
  },

  async rejectLetterRequest(id: string, reason?: string): Promise<LetterRequestItem> {
    return apiFetch<LetterRequestItem>(`/v1/documents/letter-requests/${id}/reject`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    })
  },

  async listSignatureRequests(params?: {
    status?: string
    signerEmployeeId?: string
  }): Promise<{ requests: SignatureRequestItem[]; signatureRequests: SignatureRequestItem[] }> {
    const search = new URLSearchParams()
    if (params?.status) search.set('status', params.status)
    if (params?.signerEmployeeId) search.set('signerEmployeeId', params.signerEmployeeId)
    const qs = search.toString()
    const res = await apiFetch<any>(`/v1/signatures/requests${qs ? `?${qs}` : ''}`)
    const list = res.signatureRequests ?? res.requests ?? []
    return { requests: list, signatureRequests: list }
  },

  async getSignatureRequestDetails(id: string): Promise<SignatureRequestDetailResponse> {
    const res = await apiFetch<any>(`/v1/signatures/requests/${id}`)
    return {
      request: res.signatureRequest ?? res.request,
      signatureRequest: res.signatureRequest ?? res.request,
      auditLogs: res.auditTrail ?? res.auditLogs ?? [],
      auditTrail: res.auditTrail ?? res.auditLogs ?? [],
    }
  },

  async createSignatureRequest(data: SignatureRequestCreateRequest): Promise<SignatureRequestItem> {
    return apiFetch<SignatureRequestItem>('/v1/signatures/requests', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async signDocument(id: string, data: SignatureSignRequest): Promise<SignatureRequestDetailResponse> {
    const res = await apiFetch<any>(`/v1/signatures/requests/${id}/sign`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
    return {
      request: res.signatureRequest ?? res.request,
      signatureRequest: res.signatureRequest ?? res.request,
      auditLogs: res.auditTrail ?? res.auditLogs ?? [],
      auditTrail: res.auditTrail ?? res.auditLogs ?? [],
    }
  },
}

/* -------------------------------------------------------------------------- */
/* Project Timesheets, Activities & Billing APIs (V25)                        */
/* -------------------------------------------------------------------------- */

export interface TimesheetClientItem {
  id: string
  clientCode: string
  name: string
  currency: string
  contactEmail?: string
  contactPerson?: string
  isActive: boolean
}

export interface TimesheetProjectItem {
  id: string
  clientId: string
  clientName?: string
  projectCode: string
  name: string
  description?: string
  startDate: string
  endDate?: string
  budgetAmount?: number
  budgetHours?: number
  isBillable: boolean
  status: 'ACTIVE' | 'ON_HOLD' | 'COMPLETED' | 'ARCHIVED' | string
}

export interface TimesheetActivityItem {
  id: string
  projectId?: string
  projectName?: string
  activityCode: string
  name: string
  description?: string
  isBillable: boolean
  defaultRate: number
  isActive: boolean
}

export interface TimesheetEntryItem {
  id?: string
  timesheetId?: string
  workDate: string
  projectId: string
  projectName?: string
  activityId: string
  activityName?: string
  hours: number
  description?: string
  isBillable?: boolean
  rate?: number
  amount?: number
}

export interface TimesheetItem {
  id: string
  employeeId: string
  employeeName?: string
  employeeCode?: string
  periodStart: string
  periodEnd: string
  status: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | string
  totalHours: number
  billableHours: number
  submittedAt?: string
  approvedAt?: string
  rejectionReason?: string
}

export interface TimesheetDetailResponse {
  timesheet: TimesheetItem
  entries: TimesheetEntryItem[]
}

export interface TimesheetSaveRequest {
  periodStart?: string
  periodEnd?: string
  entries: Partial<TimesheetEntryItem>[]
  submit?: boolean
}

export interface TimesheetReconciliationItem {
  employeeId?: string
  employeeName?: string
  workDate?: string
  date?: string
  attendanceHours?: number
  biometricHours?: number
  timesheetHours: number
  variance?: number
  varianceHours?: number
  status: 'MATCHED' | 'MATCH' | 'UNDER' | 'OVER' | 'OVER_REPORTED' | 'UNDER_REPORTED' | 'MISSING' | 'MISSING_TIMESHEET' | string
}

export interface TimesheetReconciliationResponse {
  employeeId?: string
  periodStart?: string
  periodEnd?: string
  weekStart?: string
  days?: TimesheetReconciliationItem[]
  reconciliationItems: TimesheetReconciliationItem[]
  totalAttendanceHours?: number
  totalBiometricHours: number
  totalTimesheetHours: number
  totalVariance?: number
}

export const timesheetsApi = {
  async listClients(): Promise<{ clients: TimesheetClientItem[] }> {
    return apiFetch<{ clients: TimesheetClientItem[] }>('/v1/timesheets/clients')
  },

  async listProjects(params?: { clientId?: string }): Promise<{ projects: TimesheetProjectItem[] }> {
    const search = new URLSearchParams()
    if (params?.clientId) search.set('clientId', params.clientId)
    const qs = search.toString()
    return apiFetch<{ projects: TimesheetProjectItem[] }>(`/v1/timesheets/projects${qs ? `?${qs}` : ''}`)
  },

  async createProject(data: {
    clientId: string
    projectCode: string
    name: string
    description?: string
    startDate: string
    endDate?: string
    budgetAmount?: number
    budgetHours?: number
    isBillable?: boolean
  }): Promise<TimesheetProjectItem> {
    return apiFetch<TimesheetProjectItem>('/v1/timesheets/projects', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async listActivities(params?: { projectId?: string }): Promise<{ activities: TimesheetActivityItem[] }> {
    const search = new URLSearchParams()
    if (params?.projectId) search.set('projectId', params.projectId)
    const qs = search.toString()
    return apiFetch<{ activities: TimesheetActivityItem[] }>(`/v1/timesheets/activities${qs ? `?${qs}` : ''}`)
  },

  async listMyTimesheets(params?: { employeeId?: string }): Promise<{ timesheets: TimesheetItem[] }> {
    const search = new URLSearchParams()
    if (params?.employeeId) search.set('employeeId', params.employeeId)
    const qs = search.toString()
    return apiFetch<{ timesheets: TimesheetItem[] }>(`/v1/timesheets/my${qs ? `?${qs}` : ''}`)
  },

  async listTimesheets(params?: {
    employeeId?: string
    status?: string
  }): Promise<{ timesheets: TimesheetItem[] }> {
    const search = new URLSearchParams()
    if (params?.employeeId) search.set('employeeId', params.employeeId)
    if (params?.status) search.set('status', params.status)
    const qs = search.toString()
    return apiFetch<{ timesheets: TimesheetItem[] }>(`/v1/timesheets${qs ? `?${qs}` : ''}`)
  },

  async getTimesheet(id: string): Promise<TimesheetDetailResponse> {
    return apiFetch<TimesheetDetailResponse>(`/v1/timesheets/${id}`)
  },

  async saveTimesheet(
    idOrData: string | TimesheetSaveRequest,
    data?: TimesheetSaveRequest,
  ): Promise<TimesheetDetailResponse> {
    const id = typeof idOrData === 'string' ? idOrData : undefined
    const body = typeof idOrData === 'string' ? data : idOrData
    const path = id ? `/v1/timesheets/${id}` : '/v1/timesheets'
    return apiFetch<TimesheetDetailResponse>(path, {
      method: id ? 'PUT' : 'POST',
      body: JSON.stringify(body),
    })
  },

  async submitTimesheet(id: string): Promise<TimesheetDetailResponse> {
    return apiFetch<TimesheetDetailResponse>(`/v1/timesheets/${id}/submit`, {
      method: 'POST',
    })
  },

  async approveTimesheet(id: string, comments?: string): Promise<TimesheetDetailResponse> {
    return apiFetch<TimesheetDetailResponse>(`/v1/timesheets/${id}/approve`, {
      method: 'POST',
      body: JSON.stringify({ comments }),
    })
  },

  async rejectTimesheet(id: string, reason: string): Promise<TimesheetDetailResponse> {
    return apiFetch<TimesheetDetailResponse>(`/v1/timesheets/${id}/reject`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    })
  },

  async copyPreviousTimesheet(
    sourceOrTarget: string | { sourceTimesheetId: string; targetPeriodStart: string; targetPeriodEnd: string },
    targetPeriodStart?: string,
    targetPeriodEnd?: string,
  ): Promise<TimesheetDetailResponse> {
    const payload =
      typeof sourceOrTarget === 'string'
        ? {
            sourceTimesheetId: sourceOrTarget,
            targetPeriodStart: targetPeriodStart ?? '2026-03-16',
            targetPeriodEnd: targetPeriodEnd ?? '2026-03-22',
          }
        : sourceOrTarget

    return apiFetch<TimesheetDetailResponse>('/v1/timesheets/copy-previous', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  async getReconciliation(
    periodStartOrParams?: string | { employeeId?: string; weekStart?: string },
    periodEnd?: string,
  ): Promise<TimesheetReconciliationResponse> {
    let qs = ''
    if (typeof periodStartOrParams === 'string') {
      const search = new URLSearchParams()
      search.set('periodStart', periodStartOrParams)
      if (periodEnd) search.set('periodEnd', periodEnd)
      qs = search.toString()
    } else if (typeof periodStartOrParams === 'object') {
      const search = new URLSearchParams()
      if (periodStartOrParams.employeeId) search.set('employeeId', periodStartOrParams.employeeId)
      if (periodStartOrParams.weekStart) search.set('weekStart', periodStartOrParams.weekStart)
      qs = search.toString()
    }
    const res = await apiFetch<any>(`/v1/timesheets/reconciliation${qs ? `?${qs}` : ''}`)
    const items = res.reconciliationItems ?? res.days ?? []
    return {
      totalBiometricHours: res.totalBiometricHours ?? res.totalAttendanceHours ?? 0,
      totalTimesheetHours: res.totalTimesheetHours ?? 0,
      reconciliationItems: items,
      ...res,
    }
  },
}

/* -------------------------------------------------------------------------- */
/* Performance Management, OKRs & 360° Appraisals APIs (V20)                  */
/* -------------------------------------------------------------------------- */

export type GoalCategory = 'ORGANIZATIONAL' | 'DEPARTMENTAL' | 'INDIVIDUAL' | 'DEVELOPMENTAL'
export type GoalStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'ON_TRACK' | 'AT_RISK' | 'COMPLETED' | 'CANCELLED'
export type EvaluationCycleStatus = 'SETUP' | 'ACTIVE' | 'IN_EVALUATION' | 'CALIBRATION' | 'CLOSED'
export type AppraisalStatus =
  | 'NOT_STARTED'
  | 'SELF_REVIEW_PENDING'
  | 'SELF_REVIEW_SUBMITTED'
  | 'MANAGER_REVIEW_PENDING'
  | 'MANAGER_REVIEW_SUBMITTED'
  | 'IN_CALIBRATION'
  | 'ACKNOWLEDGED'
  | 'CLOSED'
export type FeedbackType = 'PRAISE' | 'COACHING' | 'ONE_ON_ONE_NOTE' | 'CHECK_IN'
export type MraRelationship = 'PEER' | 'SUBORDINATE' | 'STAKEHOLDER' | 'CROSS_FUNCTIONAL'

export interface GoalCheckInItem {
  id: string
  goalId: string
  previousValue: number
  newValue: number
  progressPercentage: number
  note?: string
  checkedInBy: string
  checkedInByName: string
  createdAt: string
}

export interface GoalItem {
  id: string
  employeeId: string
  employeeName: string
  cycleId: string
  cycleName: string
  parentGoalId?: string
  title: string
  description?: string
  category: GoalCategory
  weight: number
  targetValue: number
  currentValue: number
  unit: string
  startDate: string
  dueDate: string
  status: GoalStatus
  progressPercentage: number
  checkIns?: GoalCheckInItem[]
}

export interface GoalListResponse {
  goals: GoalItem[]
  totalCount: number
  completedCount: number
}

export interface GoalCreateRequest {
  employeeId: string
  cycleId: string
  parentGoalId?: string
  title: string
  description?: string
  category: GoalCategory
  weight: number
  targetValue: number
  unit?: string
  startDate: string
  dueDate: string
}

export interface GoalCheckInRequest {
  newValue: number
  note?: string
}

export interface CompetencyGroupItem {
  id: string
  code: string
  name: string
  description?: string
}

export interface CompetencyItem {
  id: string
  groupId: string
  groupName: string
  code: string
  name: string
  description?: string
  targetLevel: number
}

export interface CompetencyFrameworkResponse {
  groups: CompetencyGroupItem[]
  competencies: CompetencyItem[]
}

export interface AppraisalSummaryItem {
  id: string
  cycleId: string
  cycleName: string
  employeeId: string
  employeeName: string
  employeeTitle?: string
  departmentName?: string
  managerId: string
  managerName: string
  status: AppraisalStatus
  finalScore?: number
  finalRating?: string
  selfReviewDeadline: string
  managerReviewDeadline: string
  employeeAcknowledgedAt?: string
}

export interface AppraisalGoalItem {
  goalId: string
  title: string
  category: string
  weight: number
  progressPercentage: number
  selfRating?: number
  selfComments?: string
  managerRating?: number
  managerComments?: string
  weightedScore?: number
}

export interface AppraisalCompetencyItem {
  competencyId: string
  code: string
  name: string
  groupName: string
  targetLevel: number
  selfProficiencyLevel?: number
  managerProficiencyLevel?: number
  selfComments?: string
  managerComments?: string
}

export interface MraRelationshipScoreItem {
  relationship: string
  respondentCount: number
  averageScore: number
}

export interface MraSummaryItem {
  totalRequests: number
  completedRequests: number
  averageScore?: number
  relationshipBreakdown: MraRelationshipScoreItem[]
}

export interface AppraisalDetailResponse {
  appraisal: AppraisalSummaryItem
  goals: AppraisalGoalItem[]
  competencies: AppraisalCompetencyItem[]
  selfOverallComments?: string
  managerOverallComments?: string
  calibrationNotes?: string
  mraSummary?: MraSummaryItem
}

export interface GoalRatingInput {
  goalId: string
  rating: number
  comments?: string
}

export interface CompetencyRatingInput {
  competencyId: string
  proficiencyLevel: number
  comments?: string
}

export interface AppraisalSelfReviewRequest {
  overallComments?: string
  goalRatings: GoalRatingInput[]
  competencyRatings: CompetencyRatingInput[]
}

export interface AppraisalManagerReviewRequest {
  overallComments?: string
  goalRatings: GoalRatingInput[]
  competencyRatings: CompetencyRatingInput[]
}

export interface ContinuousFeedbackItem {
  id: string
  senderEmployeeId: string
  senderEmployeeName: string
  recipientEmployeeId: string
  recipientEmployeeName: string
  feedbackType: FeedbackType
  title: string
  content: string
  isPrivate: boolean
  sharedWithManager: boolean
  createdAt: string
}

export interface SendFeedbackRequest {
  recipientEmployeeId: string
  feedbackType: FeedbackType
  title: string
  content: string
  isPrivate?: boolean
  sharedWithManager?: boolean
}

export const performanceApi = {
  async getGoals(params?: { employeeId?: string; cycleId?: string; status?: string }): Promise<GoalListResponse> {
    const search = new URLSearchParams()
    if (params?.employeeId) search.set('employeeId', params.employeeId)
    if (params?.cycleId) search.set('cycleId', params.cycleId)
    if (params?.status) search.set('status', params.status)
    const qs = search.toString()
    return apiFetch<GoalListResponse>(`/v1/performance/goals${qs ? `?${qs}` : ''}`)
  },

  async createGoal(data: GoalCreateRequest): Promise<GoalItem> {
    return apiFetch<GoalItem>('/v1/performance/goals', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async recordGoalCheckIn(id: string, data: GoalCheckInRequest): Promise<GoalItem> {
    return apiFetch<GoalItem>(`/v1/performance/goals/${id}/check-in`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async getCompetencies(): Promise<CompetencyFrameworkResponse> {
    return apiFetch<CompetencyFrameworkResponse>('/v1/performance/competencies')
  },

  async getMyAppraisals(cycleId?: string): Promise<{ appraisals: AppraisalSummaryItem[] }> {
    const search = new URLSearchParams()
    if (cycleId) search.set('cycleId', cycleId)
    const qs = search.toString()
    return apiFetch<{ appraisals: AppraisalSummaryItem[] }>(`/v1/performance/appraisals/my${qs ? `?${qs}` : ''}`)
  },

  async getTeamAppraisals(cycleId?: string): Promise<{ appraisals: AppraisalSummaryItem[] }> {
    const search = new URLSearchParams()
    if (cycleId) search.set('cycleId', cycleId)
    const qs = search.toString()
    return apiFetch<{ appraisals: AppraisalSummaryItem[] }>(`/v1/performance/appraisals/team${qs ? `?${qs}` : ''}`)
  },

  async getAppraisal(id: string): Promise<AppraisalDetailResponse> {
    return apiFetch<AppraisalDetailResponse>(`/v1/performance/appraisals/${id}`)
  },

  async submitSelfReview(id: string, data: AppraisalSelfReviewRequest): Promise<AppraisalDetailResponse> {
    return apiFetch<AppraisalDetailResponse>(`/v1/performance/appraisals/${id}/self-review`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async submitManagerReview(id: string, data: AppraisalManagerReviewRequest): Promise<AppraisalDetailResponse> {
    return apiFetch<AppraisalDetailResponse>(`/v1/performance/appraisals/${id}/manager-review`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async acknowledgeAppraisal(id: string): Promise<AppraisalDetailResponse> {
    return apiFetch<AppraisalDetailResponse>(`/v1/performance/appraisals/${id}/acknowledge`, {
      method: 'POST',
    })
  },

  async getFeedback(params?: { employeeId?: string; type?: string }): Promise<{ items: ContinuousFeedbackItem[] }> {
    const search = new URLSearchParams()
    if (params?.employeeId) search.set('employeeId', params.employeeId)
    if (params?.type) search.set('type', params.type)
    const qs = search.toString()
    return apiFetch<{ items: ContinuousFeedbackItem[] }>(`/v1/performance/feedback${qs ? `?${qs}` : ''}`)
  },

  async sendFeedback(data: SendFeedbackRequest): Promise<ContinuousFeedbackItem> {
    return apiFetch<ContinuousFeedbackItem>('/v1/performance/feedback', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },
}

/* -------------------------------------------------------------------------- */
/* Employee Onboarding, Checklists & Offboarding Clearance APIs (V22)          */
/* -------------------------------------------------------------------------- */

export type OnboardingOwnerRole = 'NEW_HIRE' | 'BUDDY' | 'MANAGER' | 'IT_OPS' | 'HR_OPS'
export type OnboardingTaskStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED' | 'BLOCKED'
export type OnboardingInstanceStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type ExitNoticeStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | 'REVERSED'
export type ClearanceDepartment =
  | 'IT_INFRASTRUCTURE'
  | 'FINANCE_PAYROLL'
  | 'HR_OPERATIONS'
  | 'ADMIN_FACILITIES'
  | 'LINE_MANAGER'
export type ClearanceTaskStatus = 'PENDING' | 'IN_PROGRESS' | 'CLEARED' | 'WAIVED' | 'REJECTED'

export interface OnboardingProfileItem {
  id: string
  code: string
  name: string
  departmentId?: string
  departmentName?: string
  description?: string
  active: boolean
}

export interface OnboardingTaskItem {
  id: string
  instanceId: string
  actionId?: string
  title: string
  description?: string
  ownerRole: OnboardingOwnerRole
  assigneeEmployeeId?: string
  assigneeName?: string
  dueDate: string
  status: OnboardingTaskStatus
  completedAt?: string
  notes?: string
  attachmentUrl?: string
}

export interface OnboardingInstanceSummary {
  id: string
  employeeId: string
  employeeName: string
  employeeCode?: string
  departmentName?: string
  jobTitle?: string
  profileId: string
  profileName: string
  joinDate: string
  status: OnboardingInstanceStatus
  progressPct: number
  buddyEmployeeId?: string
  buddyName?: string
  totalTasks: number
  completedTasks: number
}

export interface OnboardingInstanceDetail {
  id: string
  candidateId?: string
  employeeId: string
  employeeName: string
  employeeCode?: string
  profileId: string
  profileName: string
  joinDate: string
  status: OnboardingInstanceStatus
  progressPct: number
  buddyEmployeeId?: string
  buddyName?: string
  completedAt?: string
  tasks: OnboardingTaskItem[]
}

export interface OnboardingInstanceCreateRequest {
  employeeId: string
  profileId: string
  joinDate?: string
  buddyEmployeeId?: string
  candidateId?: string
}

export interface ExitReasonItem {
  id: string
  code: string
  name: string
  category: string
}

export interface ExitTypeItem {
  id: string
  code: string
  name: string
  voluntary: boolean
  noticeDays: number
  requiresInterview: boolean
  requiresClearance: boolean
  reasons: ExitReasonItem[]
}

export interface ExitNoticeItem {
  id: string
  noticeNumber: string
  employeeId: string
  employeeName: string
  employeeCode?: string
  departmentName?: string
  jobTitle?: string
  exitTypeId: string
  exitTypeCode: string
  exitTypeName: string
  exitReasonId?: string
  exitReasonName?: string
  noticeDate: string
  requestedLastWorkingDate: string
  approvedLastWorkingDate?: string
  remarks?: string
  status: ExitNoticeStatus
  approvedBy?: string
  approvedByName?: string
  approvedAt?: string
}

export interface ExitNoticeCreateRequest {
  exitTypeId: string
  exitReasonId?: string
  noticeDate?: string
  requestedLastWorkingDate: string
  remarks?: string
}

export interface ExitNoticeApproveRequest {
  approvedLastWorkingDate: string
  remarks?: string
}

export interface ClearanceTaskItem {
  id: string
  exitNoticeId: string
  employeeId: string
  department: ClearanceDepartment
  title: string
  assigneeEmployeeId?: string
  assigneeName?: string
  status: ClearanceTaskStatus
  clearedAt?: string
  remarks?: string
  recoverableAmount: number
}

export interface ClearanceDetailResponse {
  exitNoticeId: string
  employeeId: string
  employeeName: string
  totalTasks: number
  clearedTasks: number
  pendingTasks: number
  totalRecoverableAmount: number
  tasks: ClearanceTaskItem[]
}

export interface ClearanceTaskStatusUpdateRequest {
  status: ClearanceTaskStatus
  remarks?: string
  recoverableAmount?: number
}

export interface ExitInterviewItem {
  id: string
  exitNoticeId: string
  employeeId: string
  employeeName: string
  interviewerEmployeeId?: string
  interviewerName?: string
  conductedAt: string
  overallExperienceRating: number
  managementRating: number
  cultureRating: number
  reasonDetails?: string
  suggestions?: string
  wouldRecommend: boolean
}

export interface ExitInterviewSubmitRequest {
  overallExperienceRating: number
  managementRating: number
  cultureRating: number
  reasonDetails?: string
  suggestions?: string
  wouldRecommend?: boolean
}

export const onboardingApi = {
  async getProfiles(): Promise<{ items: OnboardingProfileItem[]; totalCount: number }> {
    return apiFetch<{ items: OnboardingProfileItem[]; totalCount: number }>('/v1/onboarding/profiles')
  },

  async getInstances(params?: {
    employeeId?: string
    status?: string
  }): Promise<{ items: OnboardingInstanceSummary[]; totalCount: number }> {
    const search = new URLSearchParams()
    if (params?.employeeId) search.set('employeeId', params.employeeId)
    if (params?.status) search.set('status', params.status)
    const qs = search.toString()
    return apiFetch<{ items: OnboardingInstanceSummary[]; totalCount: number }>(
      `/v1/onboarding/instances${qs ? `?${qs}` : ''}`
    )
  },

  async createInstance(data: OnboardingInstanceCreateRequest): Promise<OnboardingInstanceDetail> {
    return apiFetch<OnboardingInstanceDetail>('/v1/onboarding/instances', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async getInstance(id: string): Promise<OnboardingInstanceDetail> {
    return apiFetch<OnboardingInstanceDetail>(`/v1/onboarding/instances/${id}`)
  },

  async completeTask(taskId: string, data?: { notes?: string; attachmentUrl?: string }): Promise<OnboardingTaskItem> {
    return apiFetch<OnboardingTaskItem>(`/v1/onboarding/tasks/${taskId}/complete`, {
      method: 'POST',
      body: JSON.stringify(data ?? {}),
    })
  },
}

export const offboardingApi = {
  async getExitTypes(): Promise<{ items: ExitTypeItem[] }> {
    return apiFetch<{ items: ExitTypeItem[] }>('/v1/offboarding/exit-types')
  },

  async getExitNotices(params?: {
    employeeId?: string
    status?: string
  }): Promise<{ items: ExitNoticeItem[]; totalCount: number }> {
    const search = new URLSearchParams()
    if (params?.employeeId) search.set('employeeId', params.employeeId)
    if (params?.status) search.set('status', params.status)
    const qs = search.toString()
    return apiFetch<{ items: ExitNoticeItem[]; totalCount: number }>(
      `/v1/offboarding/exit-notices${qs ? `?${qs}` : ''}`
    )
  },

  async createExitNotice(data: ExitNoticeCreateRequest): Promise<ExitNoticeItem> {
    return apiFetch<ExitNoticeItem>('/v1/offboarding/exit-notices', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async approveExitNotice(id: string, data: ExitNoticeApproveRequest): Promise<ExitNoticeItem> {
    return apiFetch<ExitNoticeItem>(`/v1/offboarding/exit-notices/${id}/approve`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async rejectExitNotice(id: string, reason?: string): Promise<ExitNoticeItem> {
    return apiFetch<ExitNoticeItem>(`/v1/offboarding/exit-notices/${id}/reject`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    })
  },

  async getClearance(exitNoticeId: string): Promise<ClearanceDetailResponse> {
    return apiFetch<ClearanceDetailResponse>(`/v1/offboarding/exit-notices/${exitNoticeId}/clearance`)
  },

  async updateClearanceTaskStatus(
    taskId: string,
    data: ClearanceTaskStatusUpdateRequest
  ): Promise<ClearanceTaskItem> {
    return apiFetch<ClearanceTaskItem>(`/v1/offboarding/clearance-tasks/${taskId}/status`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async getExitInterview(exitNoticeId: string): Promise<ExitInterviewItem> {
    return apiFetch<ExitInterviewItem>(`/v1/offboarding/exit-notices/${exitNoticeId}/interview`)
  },

  async submitExitInterview(
    exitNoticeId: string,
    data: ExitInterviewSubmitRequest
  ): Promise<ExitInterviewItem> {
    return apiFetch<ExitInterviewItem>(`/v1/offboarding/exit-notices/${exitNoticeId}/interview`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },
}

/* -------------------------------------------------------------------------- */
/* Benefits Management APIs (V17)                                              */
/* -------------------------------------------------------------------------- */

export type BenefitKind = 'CASH' | 'NON_CASH' | 'REIMBURSEMENT'
export type CoverageTier = 'INDIVIDUAL' | 'EMPLOYEE_AND_SPOUSE' | 'FAMILY'
export type EnrollmentStatus = 'ACTIVE' | 'SUSPENDED' | 'EXPIRED' | 'TERMINATED'
export type DependentRelationship = 'SPOUSE' | 'CHILD' | 'PARENT'
export type BenefitClaimStatus = 'DRAFT' | 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'PAID' | 'CANCELLED'

export interface BenefitCategoryItem {
  id: string
  code: string
  name: string
  description?: string
  benefitKind: BenefitKind
  isActive: boolean
}

export interface BenefitPolicyItem {
  id: string
  categoryId: string
  categoryCode: string
  categoryName: string
  code: string
  name: string
  description?: string
  coverageTier: CoverageTier
  annualLimit: number
  currency: string
  coPayPercentage: number
  deductibleAmount: number
  minServiceMonths: number
  eligibleGrades: string
  requiresReceipt: boolean
  isActive: boolean
  isEligible: boolean
  ineligibilityReason?: string
}

export interface BenefitCatalogueResponse {
  categories: BenefitCategoryItem[]
  policies: BenefitPolicyItem[]
}

export interface BenefitDependentItem {
  id: string
  fullName: string
  relationship: DependentRelationship
  dateOfBirth: string
  nationalIdOrPassport?: string
  isCovered: boolean
}

export interface EmployeeBenefitEnrollmentItem {
  id: string
  policyId: string
  policyCode: string
  policyName: string
  categoryName: string
  coverageTier: CoverageTier
  policyNumber: string
  enrollmentYear: number
  startDate: string
  endDate: string
  annualEntitlement: number
  usedAmount: number
  pendingAmount: number
  remainingBalance: number
  currency: string
  status: EnrollmentStatus
  dependents: BenefitDependentItem[]
}

export interface MyBenefitsResponse {
  totalAnnualEntitlement: number
  totalUsedAmount: number
  totalPendingAmount: number
  totalRemainingBalance: number
  enrollments: EmployeeBenefitEnrollmentItem[]
}

export interface BenefitClaimItem {
  id: string
  enrollmentId: string
  policyName: string
  categoryName: string
  claimNumber: string
  claimDate: string
  dependentName?: string
  serviceProvider: string
  diagnosisOrReason: string
  invoiceNumber?: string
  claimedAmount: number
  approvedAmount?: number
  coPayAmount: number
  payableAmount?: number
  currency: string
  status: BenefitClaimStatus
  receiptUrl?: string
  receiptKey?: string
  rejectionReason?: string
  remarks?: string
  createdAt: string
}

export interface BenefitClaimsResponse {
  totalClaimedAmount: number
  totalApprovedAmount: number
  totalPaidAmount: number
  pendingCount: number
  claims: BenefitClaimItem[]
}

export interface BenefitClaimSubmitRequest {
  enrollmentId: string
  claimDate: string
  dependentId?: string
  serviceProvider: string
  diagnosisOrReason: string
  invoiceNumber?: string
  claimedAmount: number
  currency?: string
  receiptUrl?: string
  receiptKey?: string
  remarks?: string
}

export const benefitsApi = {
  async getCatalogue(): Promise<BenefitCatalogueResponse> {
    return apiFetch<BenefitCatalogueResponse>('/v1/benefits/catalogue')
  },

  async getMyBenefits(): Promise<MyBenefitsResponse> {
    return apiFetch<MyBenefitsResponse>('/v1/benefits/my-benefits')
  },

  async getClaims(params?: { status?: string }): Promise<BenefitClaimsResponse> {
    const qs = params?.status ? `?status=${encodeURIComponent(params.status)}` : ''
    return apiFetch<BenefitClaimsResponse>(`/v1/benefits/claims${qs}`)
  },

  async submitClaim(data: BenefitClaimSubmitRequest): Promise<BenefitClaimItem> {
    return apiFetch<BenefitClaimItem>('/v1/benefits/claims', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async cancelClaim(id: string, cancellationReason?: string): Promise<BenefitClaimItem> {
    return apiFetch<BenefitClaimItem>(`/v1/benefits/claims/${id}/cancel`, {
      method: 'POST',
      body: JSON.stringify({ cancellationReason }),
    })
  },
}

/* -------------------------------------------------------------------------- */
/* Disciplinary & Grievance Management APIs (V19)                             */
/* -------------------------------------------------------------------------- */

export type GrievanceSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type GrievanceStatus = 'SUBMITTED' | 'ASSIGNED' | 'UNDER_INVESTIGATION' | 'RESOLVED' | 'APPEALED' | 'CLOSED'
export type AppealStatus = 'PENDING' | 'UPHELD' | 'MODIFIED' | 'DISMISSED'

export type IncidentSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type IncidentStatus = 'REPORTED' | 'UNDER_INVESTIGATION' | 'ACTION_PROPOSED' | 'ACTION_ISSUED' | 'APPEALED' | 'CONCLUDED'
export type CorrectiveActionType =
  | 'ORAL_WARNING'
  | 'WRITTEN_WARNING'
  | 'SHOW_CAUSE'
  | 'CHARGE_SHEET'
  | 'DOMESTIC_INQUIRY'
  | 'SUSPENSION'
  | 'DEMOTION'
  | 'TERMINATION'
export type CorrectiveActionStatus = 'ISSUED' | 'RESPONDED' | 'UNDER_REVIEW' | 'CONFIRMED' | 'REVOKED' | 'APPEALED'
export type DisciplinaryAppealStatus = 'SUBMITTED' | 'UPHELD' | 'REDUCED' | 'OVERTURNED'

export interface GrievanceGroundGroupItem {
  id: string
  code: string
  name: string
  description?: string
}

export interface GrievanceGroundItem {
  id: string
  groupId: string
  groupName?: string
  code: string
  name: string
  description?: string
  severity: GrievanceSeverity
  defaultHandlerRole: string
  slaDays: number
}

export interface GrievanceGroundsResponse {
  groups: GrievanceGroundGroupItem[]
  grounds: GrievanceGroundItem[]
}

export interface GrievanceChannelItem {
  id: string
  code: string
  name: string
  isConfidential: boolean
}

export interface GrievanceItem {
  id: string
  grievanceNumber: string
  title: string
  groundCode: string
  groundName: string
  channelName: string
  severity: GrievanceSeverity
  anonymous: boolean
  status: GrievanceStatus
  raisedAt: string
  targetResolutionDate: string
  resolvedAt?: string
  resolution?: string
  satisfactionRating?: number
}

export interface GrievanceListResponse {
  totalCount: number
  pendingCount: number
  grievances: GrievanceItem[]
}

export interface GrievanceDetailResponse {
  id: string
  grievanceNumber: string
  title: string
  description: string
  ground: GrievanceGroundItem
  channel: GrievanceChannelItem
  anonymous: boolean
  raisedByEmployeeName?: string
  status: GrievanceStatus
  raisedAt: string
  targetResolutionDate: string
  resolvedAt?: string
  resolution?: string
  satisfactionRating?: number
  appeal?: {
    id: string
    reason: string
    appealedAt: string
    status: AppealStatus
    outcome?: string
    reviewedAt?: string
  }
}

export interface GrievanceSubmitRequest {
  groundId: string
  channelId: string
  title: string
  description: string
  anonymous?: boolean
  onBehalfOfEmployeeId?: string
  attachmentKeys?: string[]
}

export interface IncidentSubtypeItem {
  id: string
  typeId: string
  code: string
  name: string
  description?: string
}

export interface IncidentTypeItem {
  id: string
  code: string
  name: string
  description?: string
  severity: IncidentSeverity
  subtypes: IncidentSubtypeItem[]
}

export interface DisciplinaryIncidentItem {
  id: string
  incidentNumber: string
  employeeId: string
  employeeName: string
  reportedByEmployeeId: string
  reportedByEmployeeName: string
  typeCode: string
  typeName: string
  subtypeName?: string
  incidentDate: string
  location?: string
  description: string
  severity: IncidentSeverity
  status: IncidentStatus
  createdAt: string
}

export interface DisciplinaryIncidentListResponse {
  totalCount: number
  openCount: number
  incidents: DisciplinaryIncidentItem[]
}

export interface CorrectiveActionItem {
  id: string
  incidentId: string
  actionType: CorrectiveActionType
  issuedAt: string
  issuedBy: string
  issuedByName: string
  title: string
  details: string
  responseDueDate?: string
  employeeResponse?: string
  respondedAt?: string
  outcome?: string
  effectiveFrom?: string
  effectiveTo?: string
  status: CorrectiveActionStatus
}

export interface IncidentJournalEntryItem {
  id: string
  incidentId: string
  entry: string
  enteredBy: string
  enteredByName: string
  enteredAt: string
}

export interface DisciplinaryIncidentDetailResponse {
  incident: DisciplinaryIncidentItem
  correctiveActions: CorrectiveActionItem[]
  journalEntries: IncidentJournalEntryItem[]
  appeals: Array<{
    id: string
    incidentId: string
    correctiveActionId: string
    reason: string
    appealedAt: string
    outcome?: string
    reviewedAt?: string
    status: DisciplinaryAppealStatus
  }>
}

export interface DisciplinaryIncidentReportRequest {
  employeeId: string
  incidentTypeId: string
  subtypeId?: string
  incidentDate: string
  location?: string
  description: string
  severity?: IncidentSeverity
  witnesses?: string[]
}

export interface IssueCorrectiveActionRequest {
  actionType: CorrectiveActionType
  title: string
  details: string
  responseDueDate?: string
  effectiveFrom?: string
  effectiveTo?: string
}

export const disciplinaryApi = {
  async getIncidentTypes(): Promise<{ types: IncidentTypeItem[] }> {
    return apiFetch<{ types: IncidentTypeItem[] }>('/v1/disciplinary/types')
  },

  async getIncidents(params?: { employeeId?: string; status?: string }): Promise<DisciplinaryIncidentListResponse> {
    const search = new URLSearchParams()
    if (params?.employeeId) search.set('employeeId', params.employeeId)
    if (params?.status) search.set('status', params.status)
    const qs = search.toString()
    return apiFetch<DisciplinaryIncidentListResponse>(`/v1/disciplinary/incidents${qs ? `?${qs}` : ''}`)
  },

  async getIncidentById(id: string): Promise<DisciplinaryIncidentDetailResponse> {
    return apiFetch<DisciplinaryIncidentDetailResponse>(`/v1/disciplinary/incidents/${id}`)
  },

  async reportIncident(data: DisciplinaryIncidentReportRequest): Promise<DisciplinaryIncidentItem> {
    return apiFetch<DisciplinaryIncidentItem>('/v1/disciplinary/incidents', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async issueCorrectiveAction(incidentId: string, data: IssueCorrectiveActionRequest): Promise<CorrectiveActionItem> {
    return apiFetch<CorrectiveActionItem>(`/v1/disciplinary/incidents/${incidentId}/actions`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async addJournalEntry(incidentId: string, data: { entry: string }): Promise<IncidentJournalEntryItem> {
    return apiFetch<IncidentJournalEntryItem>(`/v1/disciplinary/incidents/${incidentId}/journal`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async respondToCorrectiveAction(actionId: string, data: { employeeResponse: string }): Promise<CorrectiveActionItem> {
    return apiFetch<CorrectiveActionItem>(`/v1/disciplinary/actions/${actionId}/respond`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },
}

export const grievanceApi = {
  async getGrounds(): Promise<GrievanceGroundsResponse> {
    return apiFetch<GrievanceGroundsResponse>('/v1/grievance/grounds')
  },

  async getChannels(): Promise<{ channels: GrievanceChannelItem[] }> {
    return apiFetch<{ channels: GrievanceChannelItem[] }>('/v1/grievance/channels')
  },

  async getMyGrievances(params?: { status?: string }): Promise<GrievanceListResponse> {
    const qs = params?.status ? `?status=${encodeURIComponent(params.status)}` : ''
    return apiFetch<GrievanceListResponse>(`/v1/grievance/my-grievances${qs}`)
  },

  async getGrievanceById(id: string): Promise<GrievanceDetailResponse> {
    return apiFetch<GrievanceDetailResponse>(`/v1/grievance/${id}`)
  },

  async submitGrievance(data: GrievanceSubmitRequest): Promise<GrievanceDetailResponse> {
    return apiFetch<GrievanceDetailResponse>('/v1/grievance', {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },

  async appealGrievance(id: string, data: { reason: string }): Promise<GrievanceDetailResponse> {
    return apiFetch<GrievanceDetailResponse>(`/v1/grievance/${id}/appeal`, {
      method: 'POST',
      body: JSON.stringify(data),
    })
  },
}

/* Convenient type aliases for components */
export type JobVacancy = VacancyItem
export type Candidate = CandidateItem
export type CandidateApplication = ApplicationItem
export type InterviewSchedule = InterviewItem
export type ApplicationOffer = OfferDetail
export type DocumentFolder = DocumentFolderItem
export type CompanyDocument = CompanyDocumentItem
export type DocumentVersion = DocumentVersionItem
export type DocumentTemplate = DocumentTemplateItem
export type LetterRequest = LetterRequestItem
export type SignatureRequest = SignatureRequestItem
export type SignatureAuditLog = SignatureAuditLogItem
export type Timesheet = TimesheetItem
export type TimesheetClient = TimesheetClientItem
export type TimesheetProject = TimesheetProjectItem
export type TimesheetActivity = TimesheetActivityItem
export type TimesheetEntry = TimesheetEntryItem
export type TimesheetReconciliation = TimesheetReconciliationResponse
export type Goal = GoalItem
export type AppraisalSummary = AppraisalSummaryItem
export type AppraisalDetail = AppraisalDetailResponse
export type Competency = CompetencyItem
export type ContinuousFeedback = ContinuousFeedbackItem
export type OnboardingInstance = OnboardingInstanceSummary
export type OnboardingTask = OnboardingTaskItem
export type OnboardingProfile = OnboardingProfileItem
export type ExitNotice = ExitNoticeItem
export type ClearanceTask = ClearanceTaskItem
export type ExitInterview = ExitInterviewItem


