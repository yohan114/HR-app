/**
 * One handler per endpoint the console calls.
 *
 * Every response body here is typed against the models in `clients/typescript/models`, which are
 * generated from `spec/openapi.yaml`. That is the whole discipline of this file: a fixture that
 * merely *looks* like the contract is worth very little, because the failure mode of a missing
 * field is a blank screen rather than an error, and a blank screen is indistinguishable from a
 * component that has not been written yet. Typing the fixtures means renaming a field in the spec
 * breaks `npx tsc --noEmit` instead of breaking a demo in front of somebody.
 *
 * The endpoints implemented are the ones reachable from `web/src` — verified by reading the
 * routes, not by trusting a list. Two more are here that the console does not currently call
 * (`GET /v1/employees/me` and the notification-settings pair): both are called by the Android
 * client against the same contract, and leaving them out would mean the next screen to land in the
 * console has to reopen this file rather than just render.
 */

import { Buffer } from 'node:buffer'
import { createHash, randomUUID } from 'node:crypto'
import type {
  Device,
  DirectoryEntry,
  DirectoryPage,
  FormField,
  MeResponse,
  MfaEnrolment,
  MfaStatus,
  NotificationSettings,
  RecoveryCodesResponse,
  ResolveTenantResponse,
  TokenResponse,
} from '@hr/client'
import {
  editFormFor,
  isVisible,
  project,
  findFormField,
  writableFields,
  type Caller,
} from './access'
import {
  CUSTOM_FIELD_KEYS,
  type DemoDevice,
  type DemoEmployee,
  type DemoRole,
  PERMISSION_CATALOG,
  TENANT_CODE,
  departmentName,
  designationName,
  locationName,
  meResponseFor,
  type Wire,
} from './company'
import {
  ApiFailure,
  badRequest,
  conflict,
  type DemoRequest,
  type DemoReply,
  forbidden,
  notFound,
  objectBody,
  pathParam,
  Router,
  stringField,
  unauthenticated,
} from './http'
import {
  accountById,
  callerFor,
  devicesFor,
  findAccount,
  issueSession,
  mfaStateFor,
  newRecoveryCodes,
  newTotpSecret,
  revokeRefreshForDevice,
  revokeSession,
  rotateSession,
  sessionForAccessToken,
  type Session,
  type DemoUser,
  type DemoTenant,
  type DemoLeaveApplication,
  type DemoLeaveApplicationDay,
  type DemoLeaveLedgerEntry,
  type DemoPublicHoliday,
  type DemoShift,
  type DemoShiftSchedule,
  type DemoRawPunch,
  type DemoDailyAttendance,
  type World,
} from './state'

/* -------------------------------------------------------------------------- */
/* Authentication of an incoming request                                       */
/* -------------------------------------------------------------------------- */

interface Authenticated {
  session: Session
  caller: Caller
}

/**
 * Resolves the bearer token, or refuses.
 *
 * Refusing with 401 rather than 403 matters to the console: `authRetryMiddleware` refreshes and
 * replays exactly once on a 401, and treats everything else as final. A 403 here would make an
 * expired token look like a permissions problem and strand the user on an error screen with a
 * perfectly good refresh token in `sessionStorage`.
 */
function authenticate(world: World, request: DemoRequest): Authenticated {
  assertTenantAgrees(request)

  const header = request.header('authorization')
  if (header === undefined || !header.toLowerCase().startsWith('bearer ')) {
    throw unauthenticated('UNAUTHENTICATED', 'No bearer token on the request')
  }

  const session = sessionForAccessToken(world, header.slice(7).trim())
  if (session === undefined) {
    throw unauthenticated('TOKEN_EXPIRED', 'That access token is expired or unknown')
  }

  const account = accountById(session.userId)
  if (account === undefined) throw unauthenticated('TOKEN_INVALID', 'User no longer exists')
  return { session, caller: callerFor(account) }
}

/**
 * A token and a conflicting `X-Tenant-Code` is a bug or an attack, never normal traffic.
 *
 * The console attaches the header to every request once it knows the organisation, so this is
 * exercised on every call rather than being a rule nothing ever reaches.
 */
function assertTenantAgrees(request: DemoRequest): void {
  const header = request.header('x-tenant-code')
  if (header !== undefined && header.toLowerCase() !== TENANT_CODE) {
    throw new ApiFailure(403, 'TENANT_MISMATCH', `This demo serves one organisation: '${TENANT_CODE}'`)
  }
}

function requirePermission(caller: Caller, permission: string): void {
  if (caller.permissions.has(permission)) return
  throw forbidden('INSUFFICIENT_PERMISSION', `This account does not hold ${permission}`, {
    permission,
  })
}

function requireAnyPermission(caller: Caller, ...permissions: string[]): void {
  if (permissions.some((p) => caller.permissions.has(p))) return
  throw forbidden('INSUFFICIENT_PERMISSION', `This account does not hold any of ${permissions.join(', ')}`, {
    permission: permissions[0],
  })
}

/* -------------------------------------------------------------------------- */
/* Authentication endpoints                                                    */
/* -------------------------------------------------------------------------- */

function resolveTenant(request: DemoRequest): DemoReply {
  const orgCode = stringField(objectBody(request), 'orgCode')?.trim().toLowerCase()
  // Deliberately reveals nothing about whether a *user* exists — it confirms the organisation
  // only, so it cannot be used to enumerate accounts.
  if (orgCode !== TENANT_CODE) {
    throw new ApiFailure(404, 'TENANT_NOT_FOUND', 'No organisation with that code')
  }
  const body: ResolveTenantResponse = {
    code: TENANT_CODE,
    name: 'Demo Company',
    locale: 'en',
    brandColor: '#1f6feb',
    authMethods: ['PASSWORD'],
  }
  return { status: 200, body }
}

function issueToken(world: World, request: DemoRequest): DemoReply {
  const tenantHeader = request.header('x-tenant-code')?.toLowerCase()
  if (tenantHeader !== TENANT_CODE) {
    throw new ApiFailure(404, 'TENANT_NOT_FOUND', 'No organisation with that code')
  }

  const body = objectBody(request)
  const username = stringField(body, 'username')?.trim() ?? ''
  const password = stringField(body, 'password') ?? ''

  const account = findAccount(username)
  // One code for "no such user" and "wrong password", as `AuthenticationService` does: any
  // difference between them is an account-enumeration oracle. An empty password is refused the
  // same way even though nothing here checks passwords, because a client that can sign in with a
  // blank box will ship a form that lets someone do it by accident.
  if (account === undefined || password === '') {
    throw unauthenticated('INVALID_CREDENTIALS', 'Those credentials are not valid')
  }
  if (account.status === 'LOCKED') {
    throw forbidden('ACCOUNT_LOCKED', 'This account is locked after repeated failed attempts')
  }
  if (account.status === 'DISABLED') {
    throw forbidden('ACCOUNT_DISABLED', 'This account is not active')
  }

  const device = registerDevice(world, account.userId, body)
  const { tokens } = issueSession(world, account.userId, device.id)

  const response: TokenResponse = {
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    tokenType: 'Bearer',
    expiresIn: tokens.expiresIn,
    refreshExpiresIn: tokens.refreshExpiresIn,
    // No biometric offer for a browser: there is no Keystore or Secure Enclave to seal a token in,
    // and offering it would be offering something the platform cannot deliver.
    biometricEnrolmentOffered: false,
  }
  return { status: 200, body: response }
}

/**
 * Records the browser as a device, or finds the row it already has.
 *
 * `auth.tsx` mints a stable per-browser `deviceId` into `localStorage` precisely so this row
 * survives sign-out and shows up in the device list next to the user's phones. Creating a new row
 * per sign-in would fill that table with duplicates of the same browser and make the Revoke button
 * meaningless.
 */
/** Counts every device row this process has minted. Seeded rows occupy 1–50; these start above. */
let registeredDevices = 1000

function registerDevice(world: World, userId: string, body: Record<string, unknown>): DemoDevice {
  const raw = body.device
  const info = typeof raw === 'object' && raw !== null ? (raw as Record<string, unknown>) : {}
  const deviceId = stringField(info, 'deviceId') ?? 'unknown-device'

  const devices = devicesFor(world, userId)
  const existing = devices.find((device) => device.deviceId === deviceId)
  if (existing !== undefined) {
    existing.lastSeenAt = new Date().toISOString()
    return existing
  }

  const platform = stringField(info, 'platform')
  const created: DemoDevice = {
    // A counter rather than a timestamp. Two sign-ins inside the same millisecond would otherwise
    // share a row id, and `revokeRefreshForDevice` matches on that id across every session — so
    // one user revoking their browser would sign out somebody else's.
    id: `de300000-0008-4000-8000-${(registeredDevices += 1).toString(16).padStart(12, '0')}`,
    deviceId,
    platform:
      platform === 'ANDROID' || platform === 'IOS' || platform === 'KIOSK' ? platform : 'WEB',
    model: stringField(info, 'model')?.slice(0, 128),
    appVersion: '0.1.0',
    biometricEnrolled: false,
    attestationVerified: false,
    // A browser cannot attest to anything, so it is never trusted. That the row the user is
    // sitting at carries the "Not trusted" badge is correct rather than embarrassing.
    trusted: false,
    lastSeenAt: new Date().toISOString(),
  }
  devices.push(created)
  return created
}

function refreshToken(world: World, request: DemoRequest): DemoReply {
  const presented = stringField(objectBody(request), 'refreshToken') ?? ''

  const sessionId = world.refreshTokens.get(presented)
  if (sessionId === undefined) {
    // A token that was valid once and has already been exchanged means either a replay or a
    // client that lost track of its rotation. Both are answered the same way, and the family goes
    // with it: distinguishing them would tell an attacker which of the two they are.
    const spentSessionId = world.spentRefreshTokens.get(presented)
    if (spentSessionId !== undefined) {
      const spent = world.sessions.get(spentSessionId)
      if (spent !== undefined) revokeSession(world, spent)
      throw unauthenticated('TOKEN_REUSE_DETECTED', 'That refresh token has already been used')
    }
    throw unauthenticated('TOKEN_INVALID', 'That refresh token is not valid')
  }

  const session = world.sessions.get(sessionId)
  if (session === undefined || session.revoked) {
    throw unauthenticated('TOKEN_INVALID', 'That session has ended')
  }

  const tokens = rotateSession(world, session)
  const response: TokenResponse = {
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    tokenType: 'Bearer',
    expiresIn: tokens.expiresIn,
    refreshExpiresIn: tokens.refreshExpiresIn,
  }
  return { status: 200, body: response }
}

/** Never fails. Sign-out is idempotent and a client must always be able to end its session. */
function logout(world: World, request: DemoRequest): DemoReply {
  const presented =
    request.body === undefined ? undefined : stringField(objectBody(request), 'refreshToken')

  if (presented !== undefined) {
    const sessionId = world.refreshTokens.get(presented)
    const session = sessionId === undefined ? undefined : world.sessions.get(sessionId)
    if (session !== undefined) revokeSession(world, session)
    return { status: 204 }
  }

  // No body: revoke everything this caller holds, which is the correct fallback for a client that
  // has lost track of its refresh token.
  const header = request.header('authorization')
  if (header !== undefined && header.toLowerCase().startsWith('bearer ')) {
    const session = sessionForAccessToken(world, header.slice(7).trim())
    if (session !== undefined) {
      for (const candidate of [...world.sessions.values()]) {
        if (candidate.userId === session.userId) revokeSession(world, candidate)
      }
    }
  }
  return { status: 204 }
}

/* -------------------------------------------------------------------------- */
/* Devices                                                                     */
/* -------------------------------------------------------------------------- */

function listDevices(world: World, request: DemoRequest): DemoReply {
  const { session, caller } = authenticate(world, request)
  const body: Array<Wire<Device>> = devicesFor(world, caller.account.userId).map((device) => ({
    ...device,
    // Which row the user is looking at from. The console needs it to warn that revoking this one
    // signs them out here, so it cannot be derived client-side.
    current: device.id === session.deviceRowId,
  }))
  return { status: 200, body }
}

function revokeDevice(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  const id = pathParam(request, 'id')

  const devices = devicesFor(world, caller.account.userId)
  const index = devices.findIndex((device) => device.id === id)
  // The path parameter is the device record's `id`, not its client-generated `deviceId`. Unknown
  // is a 404 rather than a silent success: a Revoke button that reports success without revoking
  // anything is the worst possible outcome on this screen.
  if (index < 0) throw notFound('No such device')

  devices.splice(index, 1)
  // Refresh and biometric grants stop immediately; an access token already issued stays valid
  // until it expires, at most fifteen minutes. So revoking the browser's own row does not throw
  // the user out mid-click — it ends the session at the next reload, which is exactly what the
  // real server does and worth seeing rather than papering over.
  revokeRefreshForDevice(world, id)
  return { status: 204 }
}

/* -------------------------------------------------------------------------- */
/* Two-factor authentication                                                   */
/* -------------------------------------------------------------------------- */

/**
 * Accepts any six digits except `000000`.
 *
 * There is no TOTP arithmetic here and there should not be — the console would need a real
 * authenticator app enrolled against a real secret, which is a lot of ceremony to demonstrate a
 * form. But a code that can never be wrong makes `MFA_INVALID_CODE` unreachable, and an error path
 * nobody can trigger is an error path nobody has looked at. `000000` is the way in.
 */
function assertMfaCode(request: DemoRequest): void {
  const code = stringField(objectBody(request), 'code') ?? ''
  if (!/^\d{6}$/.test(code) || code === '000000') {
    throw unauthenticated('MFA_INVALID_CODE', 'That code is not valid')
  }
}

function getMfaStatus(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  const state = mfaStateFor(world, caller.account.userId)
  const body: MfaStatus = {
    enabled: state.enabled,
    enrolmentPending: state.enrolmentPending,
    recoveryCodesRemaining: state.recoveryCodesRemaining,
  }
  return { status: 200, body }
}

function beginMfaEnrolment(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  const state = mfaStateFor(world, caller.account.userId)
  if (state.enabled) {
    throw conflict('CONFLICT', 'Two-factor authentication is already switched on')
  }

  // Calling this again replaces the pending secret, which is what somebody does after scanning
  // into the wrong app.
  const secret = newTotpSecret()
  state.pendingSecret = secret
  state.enrolmentPending = true

  const body: MfaEnrolment = {
    secret,
    provisioningUri: `otpauth://totp/Demo%20Company:${encodeURIComponent(caller.account.username)}?secret=${secret}&issuer=Demo%20Company&algorithm=SHA1&digits=6&period=30`,
  }
  return { status: 200, body }
}

function confirmMfaEnrolment(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  const state = mfaStateFor(world, caller.account.userId)
  if (state.enabled) throw conflict('CONFLICT', 'Two-factor authentication is already switched on')
  if (state.pendingSecret === undefined) {
    throw badRequest('VALIDATION_FAILED', 'Start enrolment before confirming it')
  }
  assertMfaCode(request)

  state.enabled = true
  state.enrolmentPending = false
  // The secret is consumed and the codes are counted, never kept. This is the only moment they
  // exist in plaintext anywhere, which is why the console holds them in component state rather
  // than refetching — see the comment in `MfaEnrolment`.
  state.pendingSecret = undefined
  const recoveryCodes = newRecoveryCodes()
  state.recoveryCodesRemaining = recoveryCodes.length

  const body: RecoveryCodesResponse = { recoveryCodes }
  return { status: 200, body }
}

function disableMfa(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  const state = mfaStateFor(world, caller.account.userId)
  if (!state.enabled) throw conflict('CONFLICT', 'Two-factor authentication is not switched on')
  // Proof of possession, not just a live session: turning the second factor off is the first thing
  // somebody with a borrowed laptop would do, and without this the factor is decorative.
  assertMfaCode(request)

  state.enabled = false
  state.enrolmentPending = false
  state.recoveryCodesRemaining = 0
  return { status: 204 }
}

function regenerateRecoveryCodes(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  const state = mfaStateFor(world, caller.account.userId)
  if (!state.enabled) throw conflict('CONFLICT', 'Two-factor authentication is not switched on')
  assertMfaCode(request)

  const recoveryCodes = newRecoveryCodes()
  state.recoveryCodesRemaining = recoveryCodes.length
  const body: RecoveryCodesResponse = { recoveryCodes }
  return { status: 200, body }
}

/* -------------------------------------------------------------------------- */
/* Me                                                                          */
/* -------------------------------------------------------------------------- */

function getMe(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  const body: MeResponse = {
    ...meResponseFor(caller.account),
    // Read from live state rather than the fixture, so enrolling in two-factor on the Security
    // screen is reflected the next time the shell asks who is signed in.
    mfaEnabled: mfaStateFor(world, caller.account.userId).enabled,
  }
  return { status: 200, body }
}

function getNotificationSettings(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  const body: NotificationSettings = world.notificationsByUser.get(caller.account.userId) ?? {
    preferences: [],
  }
  return { status: 200, body }
}

const NOTIFICATION_CHANNELS = ['PUSH', 'EMAIL', 'IN_APP', 'SMS'] as const
type NotificationChannelValue = (typeof NOTIFICATION_CHANNELS)[number]

function isChannel(value: unknown): value is NotificationChannelValue {
  return NOTIFICATION_CHANNELS.includes(value as NotificationChannelValue)
}

/**
 * A full replace, validated in its entirety before anything is written.
 *
 * The settings screen holds the whole set on screen, so sending only what changed means a switch
 * flicked twice has to be tracked as unchanged — and a client that gets that wrong silently drops
 * an edit.
 */
function replaceNotificationSettings(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  const body = objectBody(request)

  const rawPreferences = Array.isArray(body.preferences) ? body.preferences : []
  const preferences: NotificationSettings['preferences'] = []
  for (const entry of rawPreferences) {
    if (typeof entry !== 'object' || entry === null) {
      throw badRequest('VALIDATION_FAILED', 'Each preference must be an object', 'preferences')
    }
    const preference = entry as Record<string, unknown>
    const eventKey = stringField(preference, 'eventKey') ?? ''
    if (eventKey === '' || eventKey.length > 128) {
      throw badRequest(
        'VALIDATION_FAILED',
        'Event key must be between 1 and 128 characters',
        'preferences.eventKey',
      )
    }
    if (!isChannel(preference.channel)) {
      throw badRequest(
        'VALIDATION_FAILED',
        `Unknown channel: ${String(preference.channel)}`,
        'preferences.channel',
      )
    }
    const digestMode = preference.digestMode === 'DIGEST' ? 'DIGEST' : 'IMMEDIATE'
    preferences.push({
      eventKey,
      channel: preference.channel,
      enabled: preference.enabled === true,
      digestMode,
    })
  }

  const quietHours = readQuietHours(body.quietHours)

  // Only what differs from the default is stored — in-app, push and email on, SMS off. A client
  // reading the response back has to expand it again, and an absent entry means "unset, therefore
  // the default" rather than "off".
  const stored: NotificationSettings = {
    preferences: preferences.filter(
      (preference) =>
        preference.enabled !== (preference.channel !== 'SMS') || preference.digestMode !== 'IMMEDIATE',
    ),
    ...(quietHours !== undefined ? { quietHours } : {}),
  }
  world.notificationsByUser.set(caller.account.userId, stored)
  return { status: 200, body: stored }
}

function readQuietHours(raw: unknown): NotificationSettings['quietHours'] {
  if (raw === undefined || raw === null) return undefined
  if (typeof raw !== 'object') {
    throw badRequest('VALIDATION_FAILED', 'quietHours must be an object', 'quietHours')
  }
  const value = raw as Record<string, unknown>
  const startAt = stringField(value, 'startAt') ?? ''
  const endAt = stringField(value, 'endAt') ?? ''
  const timezone = stringField(value, 'timezone') ?? ''

  // An IANA zone name, not an offset: an offset is wrong twice a year wherever daylight saving
  // applies, and the symptom is notifications arriving an hour into the night for months.
  // Asking the runtime is the only real check available — a zone name is valid precisely when the
  // tz database knows it, and a stored zone nothing can resolve fails at dispatch time instead,
  // long after the user could do anything about it.
  try {
    new Intl.DateTimeFormat('en', { timeZone: timezone }).format()
  } catch {
    throw badRequest('VALIDATION_FAILED', `Unknown timezone: ${timezone}`, 'quietHours.timezone')
  }

  const days = Array.isArray(value.days) ? value.days.map(Number) : []
  if (days.some((day) => !Number.isInteger(day) || day < 1 || day > 7)) {
    throw badRequest('VALIDATION_FAILED', 'Days must be between 1 (Monday) and 7 (Sunday)', 'quietHours.days')
  }
  // Equal times read as a zero-length window, and the user almost certainly meant all day.
  // Guessing which would either mute them completely or not at all, and both look like a bug.
  if (startAt === endAt) {
    throw badRequest('VALIDATION_FAILED', 'Quiet hours must start and end at different times', 'quietHours')
  }

  return { startAt, endAt, timezone, days, enabled: value.enabled !== false }
}

/* -------------------------------------------------------------------------- */
/* Directory                                                                   */
/* -------------------------------------------------------------------------- */

/** Exited and not-yet-joined employees are excluded from the directory. */
function inDirectory(employee: DemoEmployee): boolean {
  return employee.status !== 'EXITED' && employee.status !== 'PENDING_JOIN'
}

function toDirectoryEntry(employee: DemoEmployee): DirectoryEntry {
  // Narrow on purpose. The directory is open to every authenticated employee, and what makes that
  // safe is what this projection never selects: no salary, no bank details, no identity documents,
  // no date of birth, no home address. There is nothing here to leak.
  return {
    id: employee.id,
    employeeCode: employee.employeeCode ?? '',
    displayName: employee.displayName ?? '',
    ...(employee.preferredName !== undefined ? { preferredName: employee.preferredName } : {}),
    ...(designationName(employee.designationId) !== undefined
      ? { designation: designationName(employee.designationId) }
      : {}),
    ...(departmentName(employee.departmentId) !== undefined
      ? { department: departmentName(employee.departmentId) }
      : {}),
    ...(locationName(employee.locationId) !== undefined
      ? { location: locationName(employee.locationId) }
      : {}),
    ...(employee.workEmail !== undefined ? { workEmail: employee.workEmail } : {}),
    ...(employee.mobile !== undefined ? { mobile: employee.mobile } : {}),
    ...(employee.workPhone !== undefined ? { workPhone: employee.workPhone } : {}),
    ...(employee.supervisorId !== undefined ? { supervisorId: employee.supervisorId } : {}),
  }
}

/**
 * Where a match was found, lowest first.
 *
 * A name match outranks an employee-code match, which outranks an email match — searching for
 * "Priya" and getting the person whose *manager's* email happens to contain it is the kind of
 * ranking that makes people stop using search.
 */
function rankFor(entry: DirectoryEntry, needle: string): number | null {
  if (needle === '') return 3
  if (entry.displayName.toLowerCase().includes(needle)) return 0
  if (entry.employeeCode.toLowerCase().includes(needle)) return 1
  if (entry.workEmail?.toLowerCase().includes(needle) === true) return 2
  if (entry.designation?.toLowerCase().includes(needle) === true) return 3
  if (entry.department?.toLowerCase().includes(needle) === true) return 4
  return null
}

/**
 * A keyset cursor, not an offset.
 *
 * Offset pagination re-scans skipped rows and silently drops and duplicates records on a
 * concurrently-written table. Nothing writes to this one while a page is being turned, so the
 * difference is invisible here — which is exactly why it is worth getting right in the fixture:
 * an offset that works in the demo is an offset that ships.
 */
function encodeCursor(rank: number, entry: DirectoryEntry): string {
  return Buffer.from(JSON.stringify([rank, entry.displayName, entry.id]), 'utf8').toString('base64url')
}

function decodeCursor(cursor: string): { rank: number; displayName: string; id: string } {
  // JSON rather than a delimited string: a display name can contain any character a separator
  // could be, and a cursor that breaks on one particular colleague is the kind of bug that gets
  // found by that colleague.
  let parsed: unknown
  try {
    parsed = JSON.parse(Buffer.from(cursor, 'base64url').toString('utf8')) as unknown
  } catch {
    throw badRequest('INVALID_CURSOR', 'That cursor did not come from this endpoint')
  }
  if (!Array.isArray(parsed) || parsed.length !== 3) {
    throw badRequest('INVALID_CURSOR', 'That cursor did not come from this endpoint')
  }
  const [rank, displayName, id] = parsed as [unknown, unknown, unknown]
  if (typeof rank !== 'number' || !Number.isInteger(rank)) {
    throw badRequest('INVALID_CURSOR', 'That cursor did not come from this endpoint')
  }
  if (typeof displayName !== 'string' || typeof id !== 'string') {
    throw badRequest('INVALID_CURSOR', 'That cursor did not come from this endpoint')
  }
  return { rank, displayName, id }
}

function searchDirectory(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'employee.directory')

  const needle = (request.query.get('query') ?? '').trim().toLowerCase()
  const departmentId = request.query.get('departmentId')
  const locationId = request.query.get('locationId')

  const rawLimit = request.query.get('limit')
  const limit = rawLimit === null ? 50 : Number(rawLimit)
  if (!Number.isInteger(limit) || limit < 1 || limit > 200) {
    throw badRequest('VALIDATION_FAILED', 'limit must be an integer between 1 and 200', 'limit')
  }

  const ranked: Array<{ rank: number; entry: DirectoryEntry }> = []
  for (const employee of world.employees.values()) {
    if (!inDirectory(employee)) continue
    if (departmentId !== null && employee.departmentId !== departmentId) continue
    if (locationId !== null && employee.locationId !== locationId) continue
    const entry = toDirectoryEntry(employee)
    const rank = rankFor(entry, needle)
    if (rank === null) continue
    ranked.push({ rank, entry })
  }

  ranked.sort(
    (a, b) =>
      a.rank - b.rank ||
      a.entry.displayName.localeCompare(b.entry.displayName) ||
      a.entry.id.localeCompare(b.entry.id),
  )

  const cursor = request.query.get('cursor')
  let start = 0
  if (cursor !== null && cursor !== '') {
    const after = decodeCursor(cursor)
    start = ranked.findIndex(
      ({ rank, entry }) =>
        rank > after.rank ||
        (rank === after.rank &&
          (entry.displayName > after.displayName ||
            (entry.displayName === after.displayName && entry.id > after.id))),
    )
    // A cursor past the end is an empty last page, not an error: it is what a client holding a
    // cursor to a row that has since been deleted will present.
    if (start < 0) start = ranked.length
  }

  const page = ranked.slice(start, start + limit)
  const hasMore = start + page.length < ranked.length
  const last = page[page.length - 1]

  const body: DirectoryPage = {
    items: page.map(({ entry }) => entry),
    // Cursor pagination knows nothing about a total, so there is no page count and no last page.
    ...(hasMore && last !== undefined ? { nextCursor: encodeCursor(last.rank, last.entry) } : {}),
    hasMore,
  }
  return { status: 200, body }
}

function listDirectReports(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'employee.directory')

  const id = pathParam(request, 'id')
  if (!world.employees.has(id)) throw notFound('No such employee')

  // One level only. The org chart expands a branch at a time rather than fetching a whole subtree,
  // which keeps the payload small for a deep organisation and matches how the screen is used.
  const body: DirectoryEntry[] = [...world.employees.values()]
    .filter((employee) => employee.supervisorId === id && inDirectory(employee))
    .sort((a, b) => (a.displayName ?? '').localeCompare(b.displayName ?? ''))
    .map(toDirectoryEntry)
  return { status: 200, body }
}

/* -------------------------------------------------------------------------- */
/* Employee profiles                                                           */
/* -------------------------------------------------------------------------- */

function loadVisible(world: World, caller: Caller, id: string): DemoEmployee {
  const employee = world.employees.get(id)
  // Absent and forbidden are the same answer on purpose. A 403 confirms the record exists, which
  // turns this endpoint into an oracle for enumerating employee ids.
  if (employee === undefined || !isVisible(caller, employee, world.employees)) {
    throw notFound('No such employee')
  }
  return employee
}

function getOwnProfile(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  const employee = world.employees.get(caller.employeeId)
  if (employee === undefined) {
    throw new ApiFailure(404, 'NO_EMPLOYEE_RECORD', 'This account is not linked to an employee')
  }
  return { status: 200, body: project(caller, employee) }
}

function getProfile(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  const employee = loadVisible(world, caller, pathParam(request, 'id'))
  return { status: 200, body: project(caller, employee) }
}

function getEditForm(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  const employee = loadVisible(world, caller, pathParam(request, 'id'))
  return { status: 200, body: editFormFor(caller, employee) }
}

/**
 * Fields that appear in `EmployeeUpdate`.
 *
 * Narrower than the readable projection, and that is the point: `status`, `resignDate` and
 * `yearsOfService` are readable and are not settable through this endpoint by anybody. A key
 * outside this set is `UNKNOWN_FIELD` rather than being quietly dropped — a save that reports
 * success and changes nothing is the worst answer available.
 */
const UPDATABLE_FIELDS: ReadonlySet<string> = new Set([
  'employeeCode',
  'firstName',
  'middleName',
  'lastName',
  'displayName',
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
  'employmentTypeId',
  'employeeCategoryId',
  'departmentId',
  'designationId',
  'salaryGradeId',
  'locationId',
  'supervisorId',
])

interface Violation {
  field: string
  code: string
  message: string
  rejectedValue?: unknown
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/
const PHONE_PATTERN = /^[+0-9][0-9\s()-]{6,19}$/
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

/**
 * Checks one value against its declared field, collecting rather than throwing.
 *
 * Reporting one problem at a time turns filling a form into a guessing game: the user fixes the
 * date, resubmits, and is told about the phone number. The console marks every violation at once
 * because the server hands it every violation at once.
 */
function validateField(field: FormField, value: unknown, violations: Violation[]): void {
  const key = field.key
  if (value === null) {
    if (field.required === true) {
      violations.push({ field: key, code: 'REQUIRED', message: `${field.label} is required` })
    }
    return
  }
  if (typeof value !== 'string') {
    // Everything the web form can edit is sent as text. An object or a number arriving for one of
    // these means a client built the payload by hand and got the wire type wrong.
    violations.push({ field: key, code: 'WRONG_TYPE', message: `${field.label} must be text`, rejectedValue: value })
    return
  }
  if (value.trim() === '') {
    if (field.required === true) {
      violations.push({ field: key, code: 'REQUIRED', message: `${field.label} is required` })
    }
    return
  }

  const validation = field.validation
  if (validation?.maxLength !== undefined && value.length > validation.maxLength) {
    violations.push({
      field: key,
      code: 'TOO_LONG',
      message: `${field.label} must be ${validation.maxLength} characters or fewer`,
      rejectedValue: value,
    })
    return
  }
  if (validation?.pattern !== undefined && !new RegExp(validation.pattern).test(value)) {
    violations.push({
      field: key,
      code: 'PATTERN_MISMATCH',
      message: validation.patternMessage ?? `${field.label} is not in the expected format`,
      rejectedValue: value,
    })
    return
  }

  switch (field.type) {
    case 'EMAIL':
      if (!EMAIL_PATTERN.test(value)) {
        violations.push({ field: key, code: 'INVALID_EMAIL', message: 'That is not a valid email address', rejectedValue: value })
      }
      break
    case 'PHONE':
      if (!PHONE_PATTERN.test(value)) {
        violations.push({ field: key, code: 'INVALID_PHONE', message: 'That is not a valid telephone number', rejectedValue: value })
      }
      break
    case 'DATE':
      if (!DATE_PATTERN.test(value) || Number.isNaN(Date.parse(value))) {
        violations.push({ field: key, code: 'INVALID_DATE', message: 'That is not a valid date', rejectedValue: value })
      } else if (key === 'dateOfBirth' && Date.parse(value) > Date.now()) {
        violations.push({ field: key, code: 'INVALID_DATE', message: 'A date of birth cannot be in the future', rejectedValue: value })
      }
      break
    case 'NUMBER':
      if (Number.isNaN(Number(value))) {
        violations.push({ field: key, code: 'WRONG_TYPE', message: `${field.label} must be a number`, rejectedValue: value })
      }
      break
    default:
      break
  }
}

function updateProfile(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  const employee = loadVisible(world, caller, pathParam(request, 'id'))
  const body = objectBody(request)

  assertVersionMatches(request, employee)

  const rawCustom = body.customFields
  if (rawCustom !== undefined && (typeof rawCustom !== 'object' || rawCustom === null || Array.isArray(rawCustom))) {
    // Reading this with a permissive cast and carrying on meant a string or an array here was
    // dropped and the save still answered 200 — the caller was told their change succeeded and
    // nothing had changed.
    throw badRequest('MALFORMED_REQUEST', 'customFields must be an object', 'customFields')
  }
  const customSubmitted = (rawCustom ?? {}) as Record<string, unknown>

  const builtIn: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(body)) {
    if (key === 'clearFields' || key === 'customFields') continue
    if (value === undefined) continue
    builtIn[key] = value
  }

  const { builtInUpdates, customUpdates } = expandClearFields(body, builtIn, customSubmitted)

  const unknown = Object.keys(builtInUpdates).filter((key) => !UPDATABLE_FIELDS.has(key))
  if (unknown.length > 0) {
    throw badRequest(
      'UNKNOWN_FIELD',
      `No such editable field on an employee: ${unknown.sort().join(', ')}`,
      undefined,
      { fields: unknown.sort() },
    )
  }
  const unknownCustom = Object.keys(customUpdates).filter((key) => !CUSTOM_FIELD_KEYS.includes(key))
  if (unknownCustom.length > 0) {
    throw badRequest(
      'UNKNOWN_FIELD',
      `No such field on this record: ${unknownCustom.sort().join(', ')}`,
      undefined,
      { fields: unknownCustom.sort() },
    )
  }

  // A field the caller may not write is refused, never silently dropped: a save that appears to
  // succeed and quietly discards a change is worse than a refusal.
  const writable = writableFields(caller, employee)
  const notWritable = [...Object.keys(builtInUpdates), ...Object.keys(customUpdates)]
    .filter((key) => !writable.has(key))
    .sort()
  if (notWritable.length > 0) {
    throw forbidden('FIELD_NOT_WRITABLE', `You may not change: ${notWritable.join(', ')}`, {
      fields: notWritable,
    })
  }

  // Everything is validated before anything is applied, so a rejected value cannot leave the
  // record half-written.
  const violations: Violation[] = []
  for (const [key, value] of Object.entries({ ...builtInUpdates, ...customUpdates })) {
    const field = findFormField(key)
    if (field === undefined) continue
    validateField(field, value, violations)
  }
  if (violations.length > 0) {
    throw badRequest('FIELD_VALIDATION_FAILED', 'One or more fields are not valid', undefined, {
      violations,
    })
  }

  applyUpdates(employee, builtInUpdates, customUpdates)
  employee.version += 1
  return { status: 200, body: project(caller, employee) }
}

/**
 * Enforces `If-Match` when the client sent one.
 *
 * Omitted, the update is last-write-wins. The console always sends it, because two HR officers
 * editing the same profile is common enough that losing one of them silently is a matter of time.
 */
function assertVersionMatches(request: DemoRequest, employee: DemoEmployee): void {
  const ifMatch = request.header('if-match')
  if (ifMatch === undefined || ifMatch === '*') return

  const expected = Number(ifMatch.replace(/^W\//, '').replace(/"/g, '').trim())
  if (!Number.isInteger(expected)) {
    throw badRequest('VALIDATION_FAILED', 'If-Match must carry the version you loaded')
  }
  if (expected !== employee.version) {
    throw conflict('STALE_VERSION', 'This record has changed since you loaded it', {
      expected,
      actual: employee.version,
    })
  }
}

/**
 * Turns `clearFields` into explicit nulls.
 *
 * A separate list rather than nulls in the payload because a null cannot survive code generation:
 * the typed clients must omit nulls, or a one-field save would carry one for every field the user
 * did not touch and clear the entire record.
 */
function expandClearFields(
  body: Record<string, unknown>,
  builtIn: Record<string, unknown>,
  custom: Record<string, unknown>,
): { builtInUpdates: Record<string, unknown>; customUpdates: Record<string, unknown> } {
  const raw = body.clearFields
  const keys = Array.isArray(raw) ? raw.filter((key): key is string => typeof key === 'string') : []

  // Both set and cleared in one request is refused rather than resolved. Whichever won would be
  // the opposite of what somebody intended, and which one won would depend on key ordering.
  const contradictory = keys.filter((key) => key in builtIn || key in custom).sort()
  if (contradictory.length > 0) {
    throw badRequest(
      'CONTRADICTORY_UPDATE',
      `These fields are both set and cleared in the same request: ${contradictory.join(', ')}`,
      undefined,
      { fields: contradictory },
    )
  }

  const builtInUpdates = { ...builtIn }
  const customUpdates = { ...custom }
  for (const key of keys) {
    if (CUSTOM_FIELD_KEYS.includes(key)) customUpdates[key] = null
    else builtInUpdates[key] = null
  }
  // A blank string means "clear this", exactly as it does for a built-in text field. Left as-is it
  // bypasses every type check and gets stored verbatim in a slot declared NUMBER or DATE.
  for (const [key, value] of Object.entries(customUpdates)) {
    if (typeof value === 'string' && value.trim() === '') customUpdates[key] = null
  }
  return { builtInUpdates, customUpdates }
}

function applyUpdates(
  employee: DemoEmployee,
  builtIn: Record<string, unknown>,
  custom: Record<string, unknown>,
): void {
  const writable = employee as unknown as Record<string, unknown>
  for (const [key, value] of Object.entries(builtIn)) {
    // Null means "clear". The fixture stores absent fields as `undefined`, which is what makes the
    // projection leave them out of the payload rather than sending a null the console would render
    // as an empty input on a field it should not know exists.
    writable[key] = value === null ? undefined : value
  }
  for (const [key, value] of Object.entries(custom)) {
    if (value === null) delete employee.customFields[key]
    else employee.customFields[key] = value
  }
}

/* -------------------------------------------------------------------------- */
/* User administration endpoints                                               */
/* -------------------------------------------------------------------------- */

function listUsers(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'identity.user.view')

  const q = request.query.get('q')?.trim().toLowerCase()
  const statusFilter = request.query.get('status')?.trim().toUpperCase()
  const roleFilter = request.query.get('role')?.trim().toUpperCase()

  let users = Array.from(world.users.values())

  if (q) {
    users = users.filter((u) =>
      u.username.toLowerCase().includes(q) ||
      u.email.toLowerCase().includes(q) ||
      (u.employeeName !== undefined && u.employeeName.toLowerCase().includes(q)) ||
      (u.employeeCode !== undefined && u.employeeCode.toLowerCase().includes(q)),
    )
  }

  if (statusFilter && statusFilter !== 'ALL') {
    users = users.filter((u) => u.status === statusFilter)
  }

  if (roleFilter && roleFilter !== 'ALL') {
    users = users.filter((u) => u.role.toUpperCase() === roleFilter)
  }

  return { status: 200, body: { users } }
}

function getUser(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'identity.user.view')

  const userId = pathParam(request, 'id')
  const user = world.users.get(userId)
  if (!user) {
    throw notFound('User does not exist')
  }

  return { status: 200, body: user }
}

function createUser(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'identity.user.manage')

  const body = objectBody(request)
  const username = stringField(body, 'username')?.trim().toLowerCase()
  const email = stringField(body, 'email')?.trim().toLowerCase()
  const role = stringField(body, 'role')?.trim().toUpperCase()
  const employeeCode = stringField(body, 'employeeCode')?.trim()
  const mustChangePassword = body.mustChangePassword === true

  if (!username) {
    throw badRequest('VALIDATION_FAILED', 'Username is required', 'username')
  }
  if (!email) {
    throw badRequest('VALIDATION_FAILED', 'Email is required', 'email')
  }
  if (!role) {
    throw badRequest('VALIDATION_FAILED', 'Role is required', 'role')
  }

  for (const existing of world.users.values()) {
    if (existing.username.toLowerCase() === username) {
      throw conflict('USERNAME_TAKEN', 'A user with that username already exists')
    }
    if (existing.email.toLowerCase() === email) {
      throw conflict('EMAIL_TAKEN', 'A user with that email already exists')
    }
  }

  let employeeName: string | undefined
  if (employeeCode) {
    const emp = Array.from(world.employees.values()).find(
      (e) => e.employeeCode === employeeCode,
    )
    if (emp) {
      employeeName = emp.displayName ?? emp.firstName
    }
  }

  const id = `user-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
  const newUser: DemoUser = {
    id,
    username,
    email,
    employeeCode: employeeCode || undefined,
    employeeName,
    role,
    status: 'ACTIVE',
    mfaEnabled: false,
    createdAt: new Date().toISOString(),
    mustChangePassword,
  }

  world.users.set(id, newUser)
  return { status: 201, body: newUser }
}

function updateUser(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'identity.user.manage')

  const userId = pathParam(request, 'id')
  const user = world.users.get(userId)
  if (!user) {
    throw notFound('User does not exist')
  }

  const body = objectBody(request)
  if (typeof body.status === 'string') {
    const status = body.status.toUpperCase() as DemoUser['status']
    if (['ACTIVE', 'LOCKED', 'PENDING_MFA', 'DISABLED'].includes(status)) {
      user.status = status
    }
  }
  if (typeof body.role === 'string') {
    user.role = body.role.toUpperCase()
  }

  world.users.set(userId, user)
  return { status: 200, body: user }
}

function resetUserPassword(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'identity.user.manage')

  const userId = pathParam(request, 'id')
  const user = world.users.get(userId)
  if (!user) {
    throw notFound('User does not exist')
  }

  const body = objectBody(request)
  user.mustChangePassword = body.mustChangePassword !== false

  const userSessions = Array.from(world.sessions.values()).filter((s) => s.userId === userId)
  for (const session of userSessions) {
    session.revoked = true
  }

  world.users.set(userId, user)
  return { status: 200, body: { success: true, message: 'Password has been reset' } }
}

function listUserDevices(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'identity.device.view')

  const userId = pathParam(request, 'id')
  const devices = world.devicesByUser.get(userId) ?? []
  return { status: 200, body: { devices } }
}

function revokeUserDevice(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'identity.device.revoke')

  const userId = pathParam(request, 'id')
  const deviceId = pathParam(request, 'deviceId')

  const devices = world.devicesByUser.get(userId) ?? []
  world.devicesByUser.set(
    userId,
    devices.filter((d) => d.id !== deviceId && d.deviceId !== deviceId),
  )

  for (const session of world.sessions.values()) {
    if (session.userId === userId && (session.deviceRowId === deviceId || session.id === deviceId)) {
      session.revoked = true
    }
  }

  return { status: 200, body: { success: true } }
}

function revokeAllUserDevices(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'identity.device.revoke')

  const userId = pathParam(request, 'id')
  const count = (world.devicesByUser.get(userId) ?? []).length
  world.devicesByUser.set(userId, [])

  for (const session of world.sessions.values()) {
    if (session.userId === userId) {
      session.revoked = true
    }
  }

  return { status: 200, body: { success: true, revokedCount: count } }
}

/* -------------------------------------------------------------------------- */
/* Roles and permissions endpoints                                            */
/* -------------------------------------------------------------------------- */

function listRoles(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'identity.role.view')

  const roles = Array.from(world.roles.values()).map((role) => {
    const assignedUserCount = Array.from(world.users.values()).filter(
      (u) => u.role.toUpperCase() === role.code.toUpperCase(),
    ).length
    return {
      ...role,
      assignedUserCount,
    }
  })

  return { status: 200, body: { roles } }
}

function createRole(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'identity.role.manage')

  const body = objectBody(request)
  const name = stringField(body, 'name')?.trim()
  const code = stringField(body, 'code')?.trim().toUpperCase()
  const description = stringField(body, 'description')?.trim() ?? ''
  const permissions = Array.isArray(body.permissions) ? body.permissions.map(String) : []

  if (!name) {
    throw badRequest('VALIDATION_FAILED', 'Role name is required', 'name')
  }
  if (!code) {
    throw badRequest('VALIDATION_FAILED', 'Role code is required', 'code')
  }

  for (const existing of world.roles.values()) {
    if (existing.code.toUpperCase() === code) {
      throw conflict('ROLE_EXISTS', 'A role with that code already exists')
    }
  }

  const id = `role-${code.toLowerCase().replace(/_/g, '-')}`
  const newRole: DemoRole = {
    id,
    code,
    name,
    description,
    isSystem: false,
    permissions,
  }

  world.roles.set(id, newRole)
  return { status: 201, body: newRole }
}

function updateRole(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'identity.role.manage')

  const roleId = pathParam(request, 'id')
  const role = world.roles.get(roleId)
  if (!role) {
    throw notFound('Role does not exist')
  }

  const body = objectBody(request)
  if (typeof body.name === 'string') role.name = body.name.trim()
  if (typeof body.description === 'string') role.description = body.description.trim()
  if (Array.isArray(body.permissions)) {
    role.permissions = body.permissions.filter((p): p is string => typeof p === 'string')
  }

  world.roles.set(roleId, role)
  return { status: 200, body: role }
}

function deleteRole(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'identity.role.manage')

  const roleId = pathParam(request, 'id')
  const role = world.roles.get(roleId)
  if (!role) {
    throw notFound('Role does not exist')
  }
  if (role.isSystem) {
    throw badRequest('CANNOT_DELETE_SYSTEM_ROLE', 'System roles cannot be deleted')
  }

  const hasUsers = Array.from(world.users.values()).some(
    (u) => u.role.toUpperCase() === role.code.toUpperCase(),
  )
  if (hasUsers) {
    throw conflict('ROLE_IN_USE', 'Cannot delete role assigned to existing users')
  }

  world.roles.delete(roleId)
  return { status: 200, body: { success: true } }
}

function listPermissions(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'identity.role.view')

  return { status: 200, body: { permissions: PERMISSION_CATALOG } }
}

/* -------------------------------------------------------------------------- */
/* Organisation / Tenant administration endpoints (P0-WEB-05)                  */
/* -------------------------------------------------------------------------- */

function listTenants(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'platform.tenant.view')

  const q = request.query.get('q')?.trim().toLowerCase()
  const statusFilter = request.query.get('status')?.trim().toUpperCase()
  const planFilter = request.query.get('plan')?.trim().toLowerCase()

  let tenants = Array.from(world.tenants.values())

  if (q) {
    tenants = tenants.filter(
      (t) =>
        t.code.toLowerCase().includes(q) ||
        t.name.toLowerCase().includes(q) ||
        (t.legalName !== undefined && t.legalName.toLowerCase().includes(q)),
    )
  }

  if (statusFilter && statusFilter !== 'ALL') {
    tenants = tenants.filter((t) => t.status === statusFilter)
  }

  if (planFilter && planFilter !== 'ALL') {
    tenants = tenants.filter((t) => t.subscriptionPlan.toLowerCase() === planFilter)
  }

  return { status: 200, body: { tenants } }
}

function getTenant(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'platform.tenant.view')

  const tenantId = pathParam(request, 'id')
  const tenant = world.tenants.get(tenantId)
  if (!tenant) {
    throw notFound('Organisation not found')
  }

  return { status: 200, body: tenant }
}

function createTenant(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'platform.tenant.manage')

  const body = objectBody(request)
  const code = stringField(body, 'code')?.trim().toLowerCase()
  const name = stringField(body, 'name')?.trim()
  const legalName = stringField(body, 'legalName')?.trim()
  const countryCode = (stringField(body, 'countryCode')?.trim() || 'LK').toUpperCase()
  const timezone = stringField(body, 'timezone')?.trim() || 'UTC'
  const defaultCurrency = (stringField(body, 'defaultCurrency')?.trim() || 'USD').toUpperCase()
  const locale = stringField(body, 'locale')?.trim() || 'en'
  const dataRegion = stringField(body, 'dataRegion')?.trim() || 'default'
  const isolationTierRaw = stringField(body, 'isolationTier')?.trim().toUpperCase()
  const isolationTier = (['SHARED', 'DEDICATED_SCHEMA', 'DEDICATED_DATABASE'].includes(isolationTierRaw || '')
    ? isolationTierRaw
    : 'SHARED') as DemoTenant['isolationTier']
  const subscriptionPlan = stringField(body, 'subscriptionPlan')?.trim() || 'Starter'
  const adminEmail = stringField(body, 'adminEmail')?.trim().toLowerCase()

  if (!code) {
    throw badRequest('MISSING_CODE', 'Organisation code is required')
  }

  const codeRegex = /^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$/
  if (!codeRegex.test(code)) {
    throw badRequest('INVALID_CODE_FORMAT', 'Organisation code must be lowercase letters, numbers, hyphens, 3-64 chars')
  }

  if (!name) {
    throw badRequest('MISSING_NAME', 'Organisation name is required')
  }

  // Check unique code
  const codeExists = Array.from(world.tenants.values()).some((t) => t.code === code)
  if (codeExists) {
    throw conflict('CODE_EXISTS', `Organisation with code '${code}' already exists`)
  }

  const initialModulesMap = (body.modules && typeof body.modules === 'object' ? body.modules : {}) as Record<string, boolean>
  const allKnownModules = ['identity', 'employee', 'leave', 'attendance', 'payroll', 'documents']
  const modules = allKnownModules.map((mKey) => ({
    moduleKey: mKey,
    enabled: initialModulesMap[mKey] ?? (mKey === 'identity' || mKey === 'employee'),
    updatedAt: new Date().toISOString(),
  }))

  const now = new Date().toISOString()
  const newTenant: DemoTenant = {
    id: `tenant-${code}`,
    code,
    name,
    legalName: legalName || undefined,
    countryCode,
    timezone,
    defaultCurrency,
    locale,
    dataRegion,
    isolationTier,
    status: 'ACTIVE',
    subscriptionPlan,
    adminEmail: adminEmail || undefined,
    modules,
    createdAt: now,
    updatedAt: now,
  }

  world.tenants.set(newTenant.id, newTenant)
  return { status: 201, body: newTenant }
}

function updateTenant(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'platform.tenant.manage')

  const tenantId = pathParam(request, 'id')
  const tenant = world.tenants.get(tenantId)
  if (!tenant) {
    throw notFound('Organisation not found')
  }

  const body = objectBody(request)
  const name = stringField(body, 'name')?.trim()
  const legalName = stringField(body, 'legalName')?.trim()
  const timezone = stringField(body, 'timezone')?.trim()
  const defaultCurrency = stringField(body, 'defaultCurrency')?.trim()?.toUpperCase()
  const locale = stringField(body, 'locale')?.trim()
  const dataRegion = stringField(body, 'dataRegion')?.trim()
  const isolationTierRaw = stringField(body, 'isolationTier')?.trim()?.toUpperCase()
  const subscriptionPlan = stringField(body, 'subscriptionPlan')?.trim()
  const statusRaw = stringField(body, 'status')?.trim()?.toUpperCase()
  const adminEmail = stringField(body, 'adminEmail')?.trim()?.toLowerCase()

  if (name !== undefined && name.length > 0) tenant.name = name
  if (legalName !== undefined) tenant.legalName = legalName
  if (timezone !== undefined && timezone.length > 0) tenant.timezone = timezone
  if (defaultCurrency !== undefined && defaultCurrency.length > 0) tenant.defaultCurrency = defaultCurrency
  if (locale !== undefined && locale.length > 0) tenant.locale = locale
  if (dataRegion !== undefined && dataRegion.length > 0) tenant.dataRegion = dataRegion
  if (isolationTierRaw && ['SHARED', 'DEDICATED_SCHEMA', 'DEDICATED_DATABASE'].includes(isolationTierRaw)) {
    tenant.isolationTier = isolationTierRaw as DemoTenant['isolationTier']
  }
  if (subscriptionPlan !== undefined && subscriptionPlan.length > 0) tenant.subscriptionPlan = subscriptionPlan
  if (statusRaw && ['PROVISIONING', 'ACTIVE', 'SUSPENDED', 'ARCHIVED'].includes(statusRaw)) {
    tenant.status = statusRaw as DemoTenant['status']
  }
  if (adminEmail !== undefined) tenant.adminEmail = adminEmail

  tenant.updatedAt = new Date().toISOString()
  world.tenants.set(tenantId, tenant)

  return { status: 200, body: tenant }
}

function updateTenantModules(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'platform.tenant.manage')

  const tenantId = pathParam(request, 'id')
  const tenant = world.tenants.get(tenantId)
  if (!tenant) {
    throw notFound('Organisation not found')
  }

  const body = objectBody(request)
  const modulesMap = (body.modules && typeof body.modules === 'object' ? body.modules : {}) as Record<string, boolean>

  const now = new Date().toISOString()
  const allKnownModules = ['identity', 'employee', 'leave', 'attendance', 'payroll', 'documents']

  tenant.modules = allKnownModules.map((mKey) => {
    const existing = tenant.modules.find((m) => m.moduleKey === mKey)
    const enabled = modulesMap[mKey] !== undefined ? Boolean(modulesMap[mKey]) : (existing?.enabled ?? false)
    return {
      moduleKey: mKey,
      enabled,
      config: existing?.config,
      updatedAt: now,
    }
  })

  tenant.updatedAt = now
  world.tenants.set(tenantId, tenant)

  return { status: 200, body: tenant }
}

/* -------------------------------------------------------------------------- */
/* Leave & Absence Management Endpoints (P0/P2 Leave Engine)                   */
/* -------------------------------------------------------------------------- */

function expandDays(
  startDateStr: string,
  endDateStr: string,
  portion: 'FULL_DAY' | 'FIRST_HALF' | 'SECOND_HALF',
  publicHolidays: DemoPublicHoliday[],
): DemoLeaveApplicationDay[] {
  const days: DemoLeaveApplicationDay[] = []
  const start = new Date(startDateStr + 'T00:00:00')
  const end = new Date(endDateStr + 'T00:00:00')
  const dayNames = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']

  for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
    const yyyy = d.getFullYear()
    const mm = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    const dateStr = `${yyyy}-${mm}-${dd}`
    const dayOfWeekIdx = d.getDay()
    const dayOfWeekName = dayNames[dayOfWeekIdx] ?? 'Unknown'

    const isWeekend = dayOfWeekIdx === 0 || dayOfWeekIdx === 6
    const holiday = publicHolidays.find((h) => h.date === dateStr)
    const isPublicHoliday = Boolean(holiday)
    const isWorkingDay = !isWeekend && !isPublicHoliday

    const dayPortion = isWorkingDay ? portion : 'FULL_DAY'
    const hours = isWorkingDay ? (portion === 'FULL_DAY' ? 8.0 : 4.0) : 0.0

    days.push({
      date: dateStr,
      dayOfWeek: dayOfWeekName,
      isWorkingDay,
      isPublicHoliday,
      holidayName: holiday?.name,
      portion: dayPortion,
      hours,
    })
  }

  return days
}

function getLeaveBalances(world: World, request: DemoRequest): DemoReply {
  const { session } = authenticate(world, request)
  const balances = world.leaveBalancesByUser.get(session.userId) ?? []
  return { status: 200, body: { leaveYear: 'Calendar Year 2026', balances } }
}

function getLeaveLedger(world: World, request: DemoRequest): DemoReply {
  const { session } = authenticate(world, request)
  const leaveTypeId = request.query.get('leaveTypeId')
  let ledger = world.leaveLedgerByUser.get(session.userId) ?? []
  if (leaveTypeId) {
    ledger = ledger.filter((entry) => entry.leaveTypeId === leaveTypeId)
  }
  return { status: 200, body: { ledger: [...ledger].sort((a, b) => b.date.localeCompare(a.date)) } }
}

function getLeaveApplications(world: World, request: DemoRequest): DemoReply {
  const { session } = authenticate(world, request)
  const status = request.query.get('status')
  let apps = world.leaveApplicationsByUser.get(session.userId) ?? []
  if (status && status !== 'ALL') {
    apps = apps.filter((app) => app.status === status)
  }
  return { status: 200, body: { applications: [...apps].sort((a, b) => b.submittedAt.localeCompare(a.submittedAt)) } }
}

function checkLeaveEligibility(world: World, request: DemoRequest): DemoReply {
  const { session } = authenticate(world, request)
  const body = objectBody(request)
  const leaveTypeId = stringField(body, 'leaveTypeId')
  const startDate = stringField(body, 'startDate')
  const endDate = stringField(body, 'endDate')
  const dayPortion = (stringField(body, 'dayPortion') || 'FULL_DAY') as 'FULL_DAY' | 'FIRST_HALF' | 'SECOND_HALF'

  if (!leaveTypeId || !startDate || !endDate) {
    throw badRequest('MISSING_FIELDS', 'Leave type, start date, and end date are required')
  }

  if (endDate < startDate) {
    throw badRequest('INVALID_DATE_RANGE', 'End date cannot be earlier than start date')
  }

  const balances = world.leaveBalancesByUser.get(session.userId) ?? []
  const balanceItem = balances.find((b) => b.leaveTypeId === leaveTypeId)
  const balanceAvailable = balanceItem?.availableDays ?? 0

  const days = expandDays(startDate, endDate, dayPortion, world.publicHolidays)
  const portionMultiplier = dayPortion === 'FULL_DAY' ? 1.0 : 0.5
  const workingDaysCount = days.filter((d) => d.isWorkingDay).length
  const workingDaysRequested = workingDaysCount * portionMultiplier

  const reasons: string[] = []
  if (workingDaysRequested === 0) {
    reasons.push('Selected dates contain no working days (all weekends or public holidays)')
  } else if (workingDaysRequested > balanceAvailable) {
    reasons.push(`Insufficient balance: requested ${workingDaysRequested.toFixed(2)} days, but only ${balanceAvailable.toFixed(2)} days available`)
  }

  // Check overlap with existing active applications
  const existingApps = world.leaveApplicationsByUser.get(session.userId) ?? []
  const activeApps = existingApps.filter((a) => a.status === 'SUBMITTED' || a.status === 'APPROVED')
  for (const day of days) {
    if (!day.isWorkingDay) continue
    const overlap = activeApps.find((a) =>
      a.days.some((ad) => ad.isWorkingDay && ad.date === day.date),
    )
    if (overlap) {
      reasons.push(`Dates overlap with existing application ${overlap.id} on ${day.date}`)
      break
    }
  }

  const remainingAfter = Math.max(0, balanceAvailable - workingDaysRequested)

  return {
    status: 200,
    body: {
      eligible: reasons.length === 0,
      workingDaysRequested,
      balanceAvailable,
      remainingAfter,
      reasons,
      days,
    },
  }
}

function submitLeaveApplication(world: World, request: DemoRequest): DemoReply {
  const { session } = authenticate(world, request)
  const body = objectBody(request)
  const leaveTypeId = stringField(body, 'leaveTypeId')
  const startDate = stringField(body, 'startDate')
  const endDate = stringField(body, 'endDate')
  const dayPortion = (stringField(body, 'dayPortion') || 'FULL_DAY') as 'FULL_DAY' | 'FIRST_HALF' | 'SECOND_HALF'
  const reason = stringField(body, 'reason') || ''

  if (!leaveTypeId || !startDate || !endDate) {
    throw badRequest('MISSING_FIELDS', 'Leave type, start date, and end date are required')
  }

  if (endDate < startDate) {
    throw badRequest('INVALID_DATE_RANGE', 'End date cannot be earlier than start date')
  }

  const balances = world.leaveBalancesByUser.get(session.userId) ?? []
  const balanceItem = balances.find((b) => b.leaveTypeId === leaveTypeId)
  if (!balanceItem) {
    throw badRequest('INVALID_LEAVE_TYPE', 'Unknown leave type')
  }

  const days = expandDays(startDate, endDate, dayPortion, world.publicHolidays)
  const portionMultiplier = dayPortion === 'FULL_DAY' ? 1.0 : 0.5
  const workingDaysRequested = days.filter((d) => d.isWorkingDay).length * portionMultiplier

  if (workingDaysRequested <= 0) {
    throw badRequest('NO_WORKING_DAYS', 'The requested period contains no working days.')
  }

  if (workingDaysRequested > balanceItem.availableDays) {
    throw badRequest('INSUFFICIENT_BALANCE', `Insufficient leave balance: requested ${workingDaysRequested.toFixed(2)} days, available ${balanceItem.availableDays.toFixed(2)} days.`)
  }

  // Update balance: pending increments, available decrements
  balanceItem.pendingDays += workingDaysRequested
  balanceItem.availableDays -= workingDaysRequested

  const user = world.users.get(session.userId)
  const emp = user?.employeeCode
    ? Array.from(world.employees.values()).find((e) => e.employeeCode === user.employeeCode)
    : undefined

  const newApp: DemoLeaveApplication = {
    id: `LV-2026-${String(Math.floor(Math.random() * 9000 + 1000))}`,
    employeeId: session.userId,
    employeeName: user?.employeeName || user?.username || 'Employee',
    department: emp?.departmentId || 'Engineering',
    leaveTypeId: balanceItem.leaveTypeId,
    leaveTypeCode: balanceItem.leaveTypeCode,
    leaveTypeName: balanceItem.leaveTypeName,
    startDate,
    endDate,
    dayPortion,
    totalDays: workingDaysRequested,
    reason,
    status: 'SUBMITTED',
    submittedAt: new Date().toISOString(),
    days,
  }

  const currentApps = world.leaveApplicationsByUser.get(session.userId) ?? []
  world.leaveApplicationsByUser.set(session.userId, [newApp, ...currentApps])

  return { status: 201, body: newApp }
}

function cancelLeaveApplication(world: World, request: DemoRequest): DemoReply {
  const { session } = authenticate(world, request)
  const applicationId = pathParam(request, 'id')
  const apps = world.leaveApplicationsByUser.get(session.userId) ?? []
  const app = apps.find((a) => a.id === applicationId)

  if (!app) {
    throw notFound('Leave application not found')
  }

  if (app.status === 'CANCELLED' || app.status === 'REJECTED') {
    throw badRequest('ALREADY_INACTIVE', 'This application cannot be cancelled.')
  }

  const balances = world.leaveBalancesByUser.get(session.userId) ?? []
  const balanceItem = balances.find((b) => b.leaveTypeId === app.leaveTypeId)

  if (app.status === 'APPROVED') {
    // Ledger reversal
    if (balanceItem) {
      balanceItem.takenDays -= app.totalDays
      balanceItem.availableDays += app.totalDays
    }
    const ledger = world.leaveLedgerByUser.get(session.userId) ?? []
    const newEntry: DemoLeaveLedgerEntry = {
      id: `ledger-cancel-${app.id}`,
      employeeId: session.userId,
      date: new Date().toISOString().slice(0, 10),
      leaveTypeId: app.leaveTypeId,
      leaveTypeCode: app.leaveTypeCode,
      leaveTypeName: app.leaveTypeName,
      eventType: 'CANCELLATION',
      daysCredited: app.totalDays,
      daysDebited: 0.0,
      balanceAfter: (balanceItem?.availableDays ?? 0),
      referenceId: app.id,
      notes: `Cancellation reversal for ${app.id}`,
    }
    world.leaveLedgerByUser.set(session.userId, [newEntry, ...ledger])
  } else if (app.status === 'SUBMITTED') {
    if (balanceItem) {
      balanceItem.pendingDays -= app.totalDays
      balanceItem.availableDays += app.totalDays
    }
  }

  app.status = 'CANCELLED'
  return { status: 200, body: app }
}

function getTeamLeaveCalendar(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const year = parseInt(request.query.get('year') || '2026', 10)
  const month = parseInt(request.query.get('month') || '3', 10)

  // Gather all applications from all users and team-colleagues
  const allApps: DemoLeaveApplication[] = []
  for (const apps of world.leaveApplicationsByUser.values()) {
    for (const app of apps) {
      if (app.status === 'APPROVED' || app.status === 'SUBMITTED') {
        allApps.push(app)
      }
    }
  }

  const daysInMonth = new Date(year, month, 0).getDate()
  const resultDays = []

  for (let dayNum = 1; dayNum <= daysInMonth; dayNum++) {
    const mm = String(month).padStart(2, '0')
    const dd = String(dayNum).padStart(2, '0')
    const dateStr = `${year}-${mm}-${dd}`
    const d = new Date(`${dateStr}T00:00:00`)
    const dayOfWeek = d.getDay()
    const isWeekend = dayOfWeek === 0 || dayOfWeek === 6

    const holiday = world.publicHolidays.find((h) => h.date === dateStr)
    const isHoliday = Boolean(holiday)

    // Find absences on this day
    const absences = []
    for (const app of allApps) {
      const matchDay = app.days.find((ad) => ad.date === dateStr && ad.isWorkingDay)
      if (matchDay) {
        absences.push({
          employeeId: app.employeeId,
          employeeName: app.employeeName,
          department: app.department,
          leaveTypeCode: app.leaveTypeCode,
          leaveTypeName: app.leaveTypeName,
          portion: matchDay.portion,
          status: app.status,
        })
      }
    }

    resultDays.push({
      date: dateStr,
      dayOfWeek,
      isWeekend,
      isHoliday,
      holidayName: holiday?.name,
      absences,
      hasConflict: absences.length >= 2,
    })
  }

  return { status: 200, body: { days: resultDays } }
}

/* -------------------------------------------------------------------------- */
/* The route table                                                             */
/* -------------------------------------------------------------------------- */

export function buildRouter(world: World): Router {
  return new Router()
    .on('POST', '/v1/auth/resolve-tenant', (request) => resolveTenant(request))
    .on('POST', '/v1/auth/token', (request) => issueToken(world, request))
    .on('POST', '/v1/auth/token/refresh', (request) => refreshToken(world, request))
    .on('POST', '/v1/auth/logout', (request) => logout(world, request))

    .on('GET', '/v1/auth/devices', (request) => listDevices(world, request))
    .on('DELETE', '/v1/auth/devices/:id', (request) => revokeDevice(world, request))

    .on('GET', '/v1/auth/mfa', (request) => getMfaStatus(world, request))
    .on('POST', '/v1/auth/mfa/enrol', (request) => beginMfaEnrolment(world, request))
    .on('POST', '/v1/auth/mfa/enrol/confirm', (request) => confirmMfaEnrolment(world, request))
    .on('POST', '/v1/auth/mfa/disable', (request) => disableMfa(world, request))
    .on('POST', '/v1/auth/mfa/recovery-codes', (request) => regenerateRecoveryCodes(world, request))

    .on('GET', '/v1/me', (request) => getMe(world, request))
    .on('GET', '/v1/me/notification-settings', (request) => getNotificationSettings(world, request))
    .on('PUT', '/v1/me/notification-settings', (request) => replaceNotificationSettings(world, request))

    .on('GET', '/v1/directory/search', (request) => searchDirectory(world, request))
    .on('GET', '/v1/directory/employees/:id/reports', (request) => listDirectReports(world, request))

    .on('GET', '/v1/employees/me', (request) => getOwnProfile(world, request))
    .on('GET', '/v1/employees/:id', (request) => getProfile(world, request))
    .on('PATCH', '/v1/employees/:id', (request) => updateProfile(world, request))
    .on('GET', '/v1/employees/:id/form', (request) => getEditForm(world, request))

    // User administration
    .on('GET', '/v1/users', (request) => listUsers(world, request))
    .on('POST', '/v1/users', (request) => createUser(world, request))
    .on('GET', '/v1/users/:id', (request) => getUser(world, request))
    .on('PATCH', '/v1/users/:id', (request) => updateUser(world, request))
    .on('POST', '/v1/users/:id/reset-password', (request) => resetUserPassword(world, request))
    .on('GET', '/v1/users/:id/devices', (request) => listUserDevices(world, request))
    .on('DELETE', '/v1/users/:id/devices/:deviceId', (request) => revokeUserDevice(world, request))
    .on('POST', '/v1/users/:id/devices/revoke-all', (request) => revokeAllUserDevices(world, request))

    // Role and permission administration
    .on('GET', '/v1/roles', (request) => listRoles(world, request))
    .on('POST', '/v1/roles', (request) => createRole(world, request))
    .on('PUT', '/v1/roles/:id', (request) => updateRole(world, request))
    .on('DELETE', '/v1/roles/:id', (request) => deleteRole(world, request))
    .on('GET', '/v1/permissions', (request) => listPermissions(world, request))

    // Organisation / Tenant administration (P0-WEB-05)
    .on('GET', '/v1/tenants', (request) => listTenants(world, request))
    .on('POST', '/v1/tenants', (request) => createTenant(world, request))
    .on('GET', '/v1/tenants/:id', (request) => getTenant(world, request))
    .on('PATCH', '/v1/tenants/:id', (request) => updateTenant(world, request))
    .on('PUT', '/v1/tenants/:id/modules', (request) => updateTenantModules(world, request))

    // Leave & Absence engine (P0/P2)
    .on('GET', '/v1/leave/balances', (request) => getLeaveBalances(world, request))
    .on('GET', '/v1/leave/ledger', (request) => getLeaveLedger(world, request))
    .on('GET', '/v1/leave/applications', (request) => getLeaveApplications(world, request))
    .on('POST', '/v1/leave/eligibility', (request) => checkLeaveEligibility(world, request))
    .on('POST', '/v1/leave/applications', (request) => submitLeaveApplication(world, request))
    .on('POST', '/v1/leave/applications/:id/cancel', (request) => cancelLeaveApplication(world, request))
    .on('GET', '/v1/leave/team-calendar', (request) => getTeamLeaveCalendar(world, request))

    // Payroll Statutory Pipeline & Run Console (P3-BE-21 / P3-WEB-07)
    .on('GET', '/v1/payroll/pay-groups', (request) => listPayGroups(world, request))
    .on('GET', '/v1/payroll/pay-periods', (request) => listPayPeriods(world, request))
    .on('GET', '/v1/payroll/runs', (request) => getPayrollRuns(world, request))
    .on('GET', '/v1/payroll/runs/:id', (request) => getPayrollRun(world, request))
    .on('POST', '/v1/payroll/runs/calculate', (request) => calculatePayrollRun(world, request))
    .on('POST', '/v1/payroll/runs/:id/approve', (request) => approvePayrollRun(world, request))
    .on('POST', '/v1/payroll/runs/:id/commit', (request) => commitPayrollRun(world, request))
    .on('GET', '/v1/payroll/runs/:id/results', (request) => listPayrollResults(world, request))
    .on('GET', '/v1/payroll/runs/:id/results/:resultId', (request) => getPayrollResultDetails(world, request))
    .on('GET', '/v1/payroll/runs/:id/variance', (request) => getPayrollVariance(world, request))
    .on('POST', '/v1/payroll/runs/:id/bank-advice', (request) => generateBankAdvice(world, request))
    .on('GET', '/v1/payroll/runs/:id/statutory/epf-cform', (request) => getEpfCFormSchedule(world, request))
    .on('GET', '/v1/payroll/runs/:id/statutory/etf-schedule', (request) => getEtfRemittanceSchedule(world, request))
    .on('GET', '/v1/payroll/runs/:id/statutory/t10-certificate/:employeeId', (request) => getEmployeeT10Certificate(world, request))
    .on('GET', '/v1/payroll/runs/:id/results/:resultId/payslip', (request) => getPayslipDocument(world, request))
    .on('GET', '/v1/payroll/runs/:id/payslips/batch', (request) => getPayslipBatch(world, request))

    // Attendance & Shift Roster (P2-BE / P2-WEB)
    .on('GET', '/v1/attendance/shifts', (request) => listShifts(world, request))
    .on('GET', '/v1/attendance/roster', (request) => getRoster(world, request))
    .on('POST', '/v1/attendance/shifts/assign', (request) => assignShift(world, request))
    .on('GET', '/v1/attendance/punches', (request) => listPunches(world, request))
    .on('POST', '/v1/attendance/punches/ingest', (request) => ingestPunch(world, request))
    .on('GET', '/v1/attendance/daily', (request) => listDailyAttendance(world, request))
    .on('GET', '/v1/attendance/daily/:id', (request) => getDailyAttendanceDetails(world, request))
    .on('POST', '/v1/attendance/recompute', (request) => recomputeAttendance(world, request))
    .on('GET', '/v1/attendance/payroll-variable-summary', (request) => getPayrollVariableSummary(world, request))
    .on('GET', '/v1/attendance/kiosk/status/:employeeCode', (request) => getKioskEmployeeStatus(world, request))
    .on('POST', '/v1/attendance/kiosk/punch', (request) => submitKioskPunch(world, request))
    .on('GET', '/v1/attendance/kiosk/recent-punches', (request) => listKioskRecentPunches(world, request))

    // Recruitment & ATS (V21)
    .on('GET', '/v1/recruitment/vacancies', (request) => listVacancies(world, request))
    .on('POST', '/v1/recruitment/vacancies', (request) => createVacancy(world, request))
    .on('GET', '/v1/recruitment/vacancies/:id', (request) => getVacancy(world, request))
    .on('GET', '/v1/recruitment/candidates', (request) => listCandidates(world, request))
    .on('POST', '/v1/recruitment/candidates', (request) => createCandidate(world, request))
    .on('GET', '/v1/recruitment/applications', (request) => listApplications(world, request))
    .on('POST', '/v1/recruitment/applications', (request) => createApplication(world, request))
    .on('PATCH', '/v1/recruitment/applications/:id/stage', (request) => updateApplicationStage(world, request))
    .on('GET', '/v1/recruitment/interviews', (request) => listInterviews(world, request))
    .on('POST', '/v1/recruitment/interviews', (request) => scheduleInterview(world, request))
    .on('POST', '/v1/recruitment/interviews/:id/scorecard', (request) => submitScorecard(world, request))
    .on('GET', '/v1/recruitment/applications/:id/offer', (request) => getApplicationOffer(world, request))
    .on('POST', '/v1/recruitment/applications/:id/offer', (request) => createApplicationOffer(world, request))

    // Document Management & Signatures (V23)
    .on('GET', '/v1/documents/folders', (request) => listFolders(world, request))
    .on('POST', '/v1/documents/folders', (request) => createFolder(world, request))
    .on('GET', '/v1/documents', (request) => listDocuments(world, request))
    .on('POST', '/v1/documents', (request) => createDocument(world, request))
    .on('GET', '/v1/documents/:id', (request) => getDocumentDetails(world, request))
    .on('POST', '/v1/documents/:id/versions', (request) => createDocumentVersion(world, request))
    .on('GET', '/v1/documents/templates', (request) => listTemplates(world, request))
    .on('GET', '/v1/documents/templates/:id', (request) => getTemplate(world, request))
    .on('GET', '/v1/documents/letter-requests', (request) => listLetterRequests(world, request))
    .on('POST', '/v1/documents/letter-requests', (request) => createLetterRequest(world, request))
    .on('POST', '/v1/documents/letter-requests/:id/approve', (request) => approveLetterRequest(world, request))
    .on('GET', '/v1/signatures/requests', (request) => listSignatureRequests(world, request))
    .on('GET', '/v1/signatures/requests/:id', (request) => getSignatureRequestDetails(world, request))
    .on('POST', '/v1/signatures/requests', (request) => createSignatureRequest(world, request))
    .on('POST', '/v1/signatures/requests/:id/sign', (request) => signDocument(world, request))

    // Timesheets & Project Billing (V25)
    .on('GET', '/v1/timesheets/clients', (request) => listTimesheetClients(world, request))
    .on('GET', '/v1/timesheets/projects', (request) => listTimesheetProjects(world, request))
    .on('POST', '/v1/timesheets/projects', (request) => createTimesheetProject(world, request))
    .on('GET', '/v1/timesheets/activities', (request) => listTimesheetActivities(world, request))
    .on('GET', '/v1/timesheets/my', (request) => listMyTimesheets(world, request))
    .on('GET', '/v1/timesheets', (request) => listTimesheets(world, request))
    .on('GET', '/v1/timesheets/:id', (request) => getTimesheet(world, request))
    .on('PUT', '/v1/timesheets/:id', (request) => saveTimesheet(world, request))
    .on('POST', '/v1/timesheets/:id/submit', (request) => submitTimesheet(world, request))
    .on('POST', '/v1/timesheets/:id/approve', (request) => approveTimesheet(world, request))
    .on('POST', '/v1/timesheets/:id/reject', (request) => rejectTimesheet(world, request))
    .on('POST', '/v1/timesheets/copy-previous', (request) => copyPreviousTimesheet(world, request))
    .on('GET', '/v1/timesheets/reconciliation', (request) => getTimesheetReconciliation(world, request))

    // Performance Management & OKRs (V20)
    .on('GET', '/v1/performance/goals', (request) => listPerformanceGoals(world, request))
    .on('POST', '/v1/performance/goals', (request) => createPerformanceGoal(world, request))
    .on('POST', '/v1/performance/goals/:id/check-in', (request) => recordGoalCheckIn(world, request))
    .on('GET', '/v1/performance/competencies', () => getCompetencyFramework(world))
    .on('GET', '/v1/performance/appraisals/my', (request) => listMyAppraisals(world, request))
    .on('GET', '/v1/performance/appraisals/team', (request) => listTeamAppraisals(world, request))
    .on('GET', '/v1/performance/appraisals/:id', (request) => getAppraisalDetails(world, request))
    .on('POST', '/v1/performance/appraisals/:id/self-review', (request) => submitSelfReview(world, request))
    .on('POST', '/v1/performance/appraisals/:id/manager-review', (request) => submitManagerReview(world, request))
    .on('POST', '/v1/performance/appraisals/:id/acknowledge', (request) => acknowledgeAppraisal(world, request))
    .on('GET', '/v1/performance/feedback', (request) => listContinuousFeedback(world, request))
    .on('POST', '/v1/performance/feedback', (request) => sendContinuousFeedback(world, request))

    // 360 Multi-Rater, Cycles & 9-Box (Option 5)
    .on('GET', '/v1/performance/cycles', (request) => listAppraisalCycles(world, request))
    .on('POST', '/v1/performance/cycles', (request) => createAppraisalCycle(world, request))
    .on('PATCH', '/v1/performance/cycles/:id/status', (request) => updateAppraisalCycleStatus(world, request))
    .on('GET', '/v1/performance/appraisals/:id/360-requests', (request) => get360ReviewRequests(world, request))
    .on('POST', '/v1/performance/appraisals/:id/360-nominate', (request) => nominate360Reviewer(world, request))
    .on('GET', '/v1/performance/appraisals/:id/360-matrix', (request) => get360Matrix(world, request))
    .on('POST', '/v1/performance/360-requests/:id/submit', (request) => submit360Evaluation(world, request))
    .on('GET', '/v1/performance/cycles/:id/distribution-curve', (request) => getRatingDistribution(world, request))
    .on('GET', '/v1/performance/cycles/:id/9-box', (request) => get9BoxMatrix(world, request))
    .on('POST', '/v1/performance/cycles/:id/9-box/calibrate', (request) => calibrate9BoxPosition(world, request))

    // Onboarding & Offboarding (V22)
    .on('GET', '/v1/onboarding/profiles', (request) => listOnboardingProfiles(world, request))
    .on('GET', '/v1/onboarding/instances', (request) => listOnboardingInstances(world, request))
    .on('POST', '/v1/onboarding/instances', (request) => createOnboardingInstance(world, request))
    .on('GET', '/v1/onboarding/instances/:id', (request) => getOnboardingInstance(world, request))
    .on('POST', '/v1/onboarding/tasks/:id/complete', (request) => completeOnboardingTask(world, request))
    .on('GET', '/v1/offboarding/exit-types', (request) => listExitTypes(world, request))
    .on('GET', '/v1/offboarding/exit-notices', (request) => listExitNotices(world, request))
    .on('POST', '/v1/offboarding/exit-notices', (request) => createExitNotice(world, request))
    .on('POST', '/v1/offboarding/exit-notices/:id/approve', (request) => approveExitNotice(world, request))
    .on('GET', '/v1/offboarding/exit-notices/:id/clearance', (request) => getClearanceMatrix(world, request))
    .on('POST', '/v1/offboarding/clearance-tasks/:id/status', (request) => updateClearanceTaskStatus(world, request))
    .on('GET', '/v1/offboarding/exit-notices/:id/interview', (request) => getExitInterview(world, request))
    .on('POST', '/v1/offboarding/exit-notices/:id/interview', (request) => submitExitInterview(world, request))
}

/* -------------------------------------------------------------------------- */
/* Payroll Statutory Calculation Pipeline & Run Console (P3-WEB-07)           */
/* -------------------------------------------------------------------------- */

function listPayGroups(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'payroll.view')
  return { status: 200, body: { payGroups: Array.from(world.payGroups.values()) } }
}

function listPayPeriods(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'payroll.view')
  const payGroupId = request.query.get('payGroupId')
  let periods = Array.from(world.payPeriods.values())
  if (payGroupId) {
    periods = periods.filter((p) => p.payGroupId === payGroupId)
  }
  return { status: 200, body: { payPeriods: periods } }
}

function getPayrollRuns(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'payroll.view')
  const payPeriodId = request.query.get('payPeriodId')
  let runs = Array.from(world.payrollRuns.values())
  if (payPeriodId) {
    runs = runs.filter((r) => r.payPeriodId === payPeriodId)
  }
  return { status: 200, body: { runs } }
}

function getPayrollRun(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'payroll.view')
  const id = pathParam(request, 'id')
  const run = world.payrollRuns.get(id)
  if (!run) throw notFound('Payroll run not found')
  return { status: 200, body: run }
}

function calculatePayrollRun(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'payroll.run.manage')
  const body = objectBody(request)
  const payGroupId = stringField(body, 'payGroupId') || 'pg-lk-monthly'
  const payPeriodId = stringField(body, 'payPeriodId') || 'period-2026-m03'

  let run = Array.from(world.payrollRuns.values()).find(
    (r) => r.payPeriodId === payPeriodId && r.payGroupId === payGroupId,
  )

  if (!run) {
    run = {
      id: `run-${payPeriodId}`,
      payGroupId,
      payPeriodId,
      runNumber: 1,
      status: 'CALCULATED',
      totalGross: 5580000,
      totalStatutoryEmployee: 446400,
      totalStatutoryEmployer: 837000,
      totalTax: 344800,
      totalNet: 4748800,
      totalEmployees: world.employees.size,
      calculatedAt: new Date().toISOString(),
    }
  } else {
    run.status = 'CALCULATED'
    run.calculatedAt = new Date().toISOString()
  }

  world.payrollRuns.set(run.id, run)
  return { status: 200, body: run }
}

function approvePayrollRun(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'payroll.run.manage')
  const id = pathParam(request, 'id')
  const run = world.payrollRuns.get(id)
  if (!run) throw notFound('Payroll run not found')
  run.status = 'APPROVED'
  run.approvedAt = new Date().toISOString()
  world.payrollRuns.set(id, run)
  return { status: 200, body: run }
}

function commitPayrollRun(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'payroll.run.manage')
  const id = pathParam(request, 'id')
  const run = world.payrollRuns.get(id)
  if (!run) throw notFound('Payroll run not found')
  run.status = 'COMMITTED'
  run.committedAt = new Date().toISOString()
  world.payrollRuns.set(id, run)

  // Mark all results in this run as PAID
  const results = world.payrollResultsByRun.get(id) ?? []
  for (const res of results) {
    res.paymentStatus = 'PAID'
  }

  // Close the pay period
  const period = world.payPeriods.get(run.payPeriodId)
  if (period) {
    period.status = 'CLOSED'
  }

  return { status: 200, body: run }
}

function listPayrollResults(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'payroll.view')
  const id = pathParam(request, 'id')
  const department = request.query.get('department')
  const q = request.query.get('q')?.trim().toLowerCase()

  let results = world.payrollResultsByRun.get(id) ?? []
  if (department && department !== 'ALL') {
    results = results.filter((r) => r.department === department)
  }
  if (q) {
    results = results.filter(
      (r) =>
        r.employeeCode.toLowerCase().includes(q) ||
        r.employeeName.toLowerCase().includes(q) ||
        r.designation.toLowerCase().includes(q),
    )
  }

  return { status: 200, body: { results } }
}

function getPayrollResultDetails(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'payroll.view')
  const runId = pathParam(request, 'id')
  const resultId = pathParam(request, 'resultId')
  const results = world.payrollResultsByRun.get(runId) ?? []
  const result = results.find((r) => r.id === resultId)
  if (!result) throw notFound('Employee payroll result not found')
  const lines = world.payrollLinesByResult.get(resultId) ?? []
  return { status: 200, body: { result, lines } }
}

function getPayrollVariance(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'payroll.view')
  const runId = pathParam(request, 'id')
  const currentRun = world.payrollRuns.get(runId)
  if (!currentRun) throw notFound('Payroll run not found')

  const previousRun = Array.from(world.payrollRuns.values()).find(
    (r) => r.payGroupId === currentRun.payGroupId && r.id !== runId && r.status === 'COMMITTED',
  )

  const anomalies = [
    {
      employeeCode: 'E004',
      employeeName: 'Kasun Fernando',
      department: 'ENG',
      previousGross: 200000.0,
      currentGross: 245000.0,
      varianceAmount: 45000.0,
      variancePercent: 22.5,
      reasons: ['Performance & Project Milestone Bonus (+LKR 45,000.00)'],
    },
    {
      employeeCode: 'E001',
      employeeName: 'Nimali Wickramasinghe',
      department: 'ENG',
      previousGross: 700000.0,
      currentGross: 750000.0,
      varianceAmount: 50000.0,
      variancePercent: 7.14,
      reasons: ['Q1 Executive Board Allowance (+LKR 50,000.00)'],
    },
  ]

  let grossVariancePercent = 4.89
  let netVariancePercent = 4.55
  if (previousRun && previousRun.totalGross > 0) {
    grossVariancePercent =
      Math.round(((currentRun.totalGross - previousRun.totalGross) / previousRun.totalGross) * 10000) / 100
    netVariancePercent =
      Math.round(((currentRun.totalNet - previousRun.totalNet) / previousRun.totalNet) * 10000) / 100
  }

  return {
    status: 200,
    body: {
      grossVariancePercent,
      netVariancePercent,
      headcountDifference: 0,
      anomalies,
    },
  }
}

function generateBankAdvice(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'payroll.run.manage')
  const runId = pathParam(request, 'id')
  const run = world.payrollRuns.get(runId)
  if (!run) throw notFound('Payroll run not found')

  const body = objectBody(request)
  const format = stringField(body, 'format') || 'CSV_STANDARD'
  const companyAccount = stringField(body, 'companyAccount') || '9988776655'
  const paymentDate = stringField(body, 'paymentDate') || '2026-03-25'

  const results = world.payrollResultsByRun.get(runId) ?? []
  const totalAmount = results.reduce((sum, r) => sum + r.netPay, 0)
  const totalRecords = results.length

  if (format === 'ACH_NACHA') {
    // Generate strict 94-char fixed-width NACHA format
    const lines: string[] = []
    // Line 1: File Header (Type 1)
    lines.push(
      `101 071000288 123456789${paymentDate.replace(/-/g, '').slice(2)}1600A094101Commercial Bank           Demo Company Inc      00000001`,
    )
    // Line 2: Batch Header (Type 5)
    lines.push(
      `5220Demo Company Inc        PAYROLL   9988776655PPDMONTHLY SAL${paymentDate.replace(/-/g, '').slice(2)}${paymentDate.replace(/-/g, '').slice(2)}   1071000280000001`,
    )
    // Lines: Details (Type 6)
    results.forEach((r, idx) => {
      const traceNum = String(idx + 1).padStart(7, '0')
      const amtCents = String(Math.round(r.netPay * 100)).padStart(10, '0')
      const empName = (r.employeeName.padEnd(15, ' ')).slice(0, 15)
      const empId = (r.employeeCode.padEnd(15, ' ')).slice(0, 15)
      lines.push(
        `6220710002881234567890         ${amtCents}${empId}${empName}  007100028${traceNum}`,
      )
    })
    // Batch Control (Type 8)
    const batchAmtCents = String(Math.round(totalAmount * 100)).padStart(12, '0')
    const recCount = String(totalRecords).padStart(6, '0')
    lines.push(
      `8220${recCount}0000000000000000000000000000${batchAmtCents}123456789                         071000280000001`,
    )
    // File Control (Type 9)
    const totalLinesWithFileControl = lines.length + 1
    const blockCount = Math.ceil(totalLinesWithFileControl / 10)
    lines.push(
      `9000001${String(blockCount).padStart(6, '0')}${String(totalRecords).padStart(8, '0')}0000000000000000000000000000${batchAmtCents}                                       `,
    )

    // Blocking factor 10 padding with 9s
    while (lines.length % 10 !== 0) {
      lines.push('9'.repeat(94))
    }

    const content = lines.join('\r\n') + '\r\n'
    const batchHash = createHash('sha256').update(content).digest('hex')

    return {
      status: 200,
      body: {
        filename: `nacha_ach_${paymentDate.replace(/-/g, '')}.txt`,
        content,
        totalRecords,
        totalAmount,
        batchHash,
        mimeType: 'text/plain',
      },
    }
  }

  // Standard CSV Format
  const csvLines: string[] = [
    '# COMPANY: Demo Company',
    `# DISBURSEMENT_ACCOUNT: ${companyAccount}`,
    `# PAYMENT_DATE: ${paymentDate}`,
    '# CURRENCY: LKR',
    `# TOTAL_RECORDS: ${totalRecords}`,
    `# TOTAL_AMOUNT: ${totalAmount.toFixed(2)}`,
    'EmployeeCode,EmployeeName,Department,BankCode,BranchCode,AccountNumber,Amount,PaymentReference',
  ]

  results.forEach((r, idx) => {
    const ref = `SAL-2026M03-${String(idx + 1).padStart(3, '0')}`
    const quotedName = r.employeeName.includes(',') ? `"${r.employeeName}"` : r.employeeName
    csvLines.push(
      `${r.employeeCode},${quotedName},${r.department},7010,001,1000${r.employeeCode.replace(/\D/g, '').padStart(6, '0')},${r.netPay.toFixed(2)},${ref}`,
    )
  })

  csvLines.push(`# END OF BATCH - TOTAL_AMOUNT=${totalAmount.toFixed(2)}`)
  const content = csvLines.join('\n')
  const batchHash = createHash('sha256').update(content).digest('hex')

  return {
    status: 200,
    body: {
      filename: `bank_advice_lkr_${paymentDate.replace(/-/g, '')}.csv`,
      content,
      totalRecords,
      totalAmount,
      batchHash,
      mimeType: 'text/csv',
    },
  }
}

function getEmployeeNic(emp: DemoEmployee | undefined, index: number): string {
  if (emp?.dateOfBirth) {
    const year = emp.dateOfBirth.slice(0, 4)
    const dayOfYear = String(((index * 17 + 105) % 365) + 1).padStart(3, '0')
    const seq = String(((index * 37 + 1204) % 8999) + 1000)
    return `${year}${dayOfYear}0${seq}`
  }
  return `1990${String(100 + index).padStart(3, '0')}0${String(2000 + index)}`
}

function getEmployeeTin(index: number): string {
  return String(100000000 + (index + 1) * 38291).slice(0, 9)
}

function getEpfCFormSchedule(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'payroll.view')
  const runId = pathParam(request, 'id')
  const run = world.payrollRuns.get(runId)
  if (!run) throw notFound('Payroll run not found')

  const period = world.payPeriods.get(run.payPeriodId)
  const contributionMonth = period?.code ? period.code.replace('period-', '') : '2026-03'
  const results = world.payrollResultsByRun.get(runId) ?? []

  let totalContributoryEarnings = 0
  let totalMemberShare8 = 0
  let totalEmployerShare12 = 0

  const members = results.map((r, idx) => {
    const emp = world.employees.get(r.employeeId)
    const memberNo = (emp?.customFields?.epfNumber as string) || `A/${12000 + idx}`
    const nic = getEmployeeNic(emp, idx)
    const fullName = r.employeeName
    const nameParts = fullName.trim().split(' ')
    const initialsAndSurname =
      nameParts.length > 1
        ? `${nameParts.slice(0, -1).map((p) => p.charAt(0) + '.').join(' ')} ${nameParts[nameParts.length - 1]}`
        : fullName

    // In Sri Lanka, EPF base is basic + fixed allowances (contributory earnings)
    const contributoryEarnings =
      r.totalStatutoryEmployee > 0
        ? Math.round((r.totalStatutoryEmployee / 0.08) * 100) / 100
        : r.basicSalary
    const memberShare8 = r.totalStatutoryEmployee
    const employerShare12 = Math.round(contributoryEarnings * 0.12 * 100) / 100
    const totalContribution20 = Math.round((memberShare8 + employerShare12) * 100) / 100

    totalContributoryEarnings += contributoryEarnings
    totalMemberShare8 += memberShare8
    totalEmployerShare12 += employerShare12

    const status: 'ACTIVE' | 'NEW' | 'EXITED' =
      emp?.status === 'EXITED' ? 'EXITED' : emp?.status === 'PROBATION' ? 'NEW' : 'ACTIVE'

    return {
      memberNo,
      nic,
      fullName,
      initialsAndSurname,
      department: r.department,
      contributoryEarnings,
      memberShare8,
      employerShare12,
      totalContribution20,
      status,
    }
  })

  totalContributoryEarnings = Math.round(totalContributoryEarnings * 100) / 100
  totalMemberShare8 = Math.round(totalMemberShare8 * 100) / 100
  totalEmployerShare12 = Math.round(totalEmployerShare12 * 100) / 100
  const totalRemittance20 = Math.round((totalMemberShare8 + totalEmployerShare12) * 100) / 100
  const memberCount = members.length

  // Generate Electronic C-Form (Central Bank standard format with H, D, T records)
  const employerReg = 'E/10948'
  const employerName = 'Demo Company (Pvt) Ltd'
  const employerAddress = 'Level 14, West Tower, World Trade Center, Colombo 01'
  const formattedMonth = contributionMonth.replace('-', '')

  const cFormLines: string[] = [
    `H,${employerReg.replace('/', '')},${employerName.toUpperCase()},${formattedMonth},${totalRemittance20.toFixed(2)},${memberCount}`,
  ]

  for (const m of members) {
    const cleanMemberNo = m.memberNo.replace(/[^A-Za-z0-9]/g, '')
    cFormLines.push(
      `D,${cleanMemberNo},${m.nic},"${m.initialsAndSurname}",${m.contributoryEarnings.toFixed(2)},${m.memberShare8.toFixed(2)},${m.employerShare12.toFixed(2)},${m.totalContribution20.toFixed(2)},${m.status}`,
    )
  }

  cFormLines.push(`T,${memberCount},${totalContributoryEarnings.toFixed(2)},${totalRemittance20.toFixed(2)}`)
  const electronicContent = cFormLines.join('\n')

  return {
    status: 200,
    body: {
      employerRegistrationNo: employerReg,
      employerName,
      employerAddress,
      contributionMonth,
      paymentDueDate: `${contributionMonth}-29`,
      remittanceRef: `EPF-TXN-${formattedMonth}-8842`,
      chequeOrTransferDate: `${contributionMonth}-25`,
      currency: 'LKR',
      totalContributoryEarnings,
      totalMemberShare8,
      totalEmployerShare12,
      totalRemittance20,
      memberCount,
      members,
      electronicFile: {
        filename: `CFORM_${formattedMonth}_${employerReg.replace('/', '')}.csv`,
        content: electronicContent,
        mimeType: 'text/csv',
      },
    },
  }
}

function getEtfRemittanceSchedule(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'payroll.view')
  const runId = pathParam(request, 'id')
  const run = world.payrollRuns.get(runId)
  if (!run) throw notFound('Payroll run not found')

  const period = world.payPeriods.get(run.payPeriodId)
  const contributionMonth = period?.code ? period.code.replace('period-', '') : '2026-03'
  const results = world.payrollResultsByRun.get(runId) ?? []

  let totalContributoryEarnings = 0
  let totalEmployerContribution3 = 0

  const members = results.map((r, idx) => {
    const emp = world.employees.get(r.employeeId)
    const memberNo = (emp?.customFields?.epfNumber as string) || `A/${12000 + idx}`
    const nic = getEmployeeNic(emp, idx)
    const contributoryEarnings =
      r.totalStatutoryEmployee > 0
        ? Math.round((r.totalStatutoryEmployee / 0.08) * 100) / 100
        : r.basicSalary
    const employerContribution3 = Math.round(contributoryEarnings * 0.03 * 100) / 100

    totalContributoryEarnings += contributoryEarnings
    totalEmployerContribution3 += employerContribution3

    return {
      memberNo,
      nic,
      fullName: r.employeeName,
      department: r.department,
      contributoryEarnings,
      employerContribution3,
    }
  })

  totalContributoryEarnings = Math.round(totalContributoryEarnings * 100) / 100
  totalEmployerContribution3 = Math.round(totalEmployerContribution3 * 100) / 100
  const employerReg = 'ETF/88492'
  const employerName = 'Demo Company (Pvt) Ltd'
  const formattedMonth = contributionMonth.replace('-', '')

  const csvLines: string[] = [
    '# EMPLOYEES TRUST FUND BOARD SRI LANKA - MONTHLY REMITTANCE SCHEDULE',
    `# EMPLOYER_REG_NO: ${employerReg}`,
    `# EMPLOYER_NAME: ${employerName}`,
    `# REMITTANCE_MONTH: ${contributionMonth}`,
    `# TOTAL_MEMBERS: ${members.length}`,
    `# TOTAL_CONTRIBUTION_3PCT: ${totalEmployerContribution3.toFixed(2)}`,
    'MemberNo,NIC,EmployeeName,Department,ContributoryEarnings,ETF_Employer_3pct',
  ]

  for (const m of members) {
    const quotedName = m.fullName.includes(',') ? `"${m.fullName}"` : m.fullName
    csvLines.push(
      `${m.memberNo},${m.nic},${quotedName},${m.department},${m.contributoryEarnings.toFixed(2)},${m.employerContribution3.toFixed(2)}`,
    )
  }

  return {
    status: 200,
    body: {
      employerRegistrationNo: employerReg,
      employerName,
      contributionMonth,
      currency: 'LKR',
      totalContributoryEarnings,
      totalEmployerContribution3,
      memberCount: members.length,
      members,
      electronicFile: {
        filename: `ETF_SCHEDULE_${formattedMonth}_${employerReg.replace('/', '')}.csv`,
        content: csvLines.join('\n'),
        mimeType: 'text/csv',
      },
    },
  }
}

function getEmployeeT10Certificate(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'payroll.view')
  const runId = pathParam(request, 'id')
  const employeeId = pathParam(request, 'employeeId')
  const assessmentYear = request.query.get('year') || '2025/2026'

  const run = world.payrollRuns.get(runId)
  if (!run) throw notFound('Payroll run not found')

  const results = world.payrollResultsByRun.get(runId) ?? []
  const result = results.find(
    (r) => r.employeeId === employeeId || r.employeeCode === employeeId || r.id === employeeId,
  )
  if (!result) throw notFound('Employee payroll result not found')

  const employeeIndex = Array.from(world.employees.values()).findIndex((e) => e.id === result.employeeId)
  const emp = world.employees.get(result.employeeId)
  const idx = employeeIndex >= 0 ? employeeIndex : 0

  const epfNo = (emp?.customFields?.epfNumber as string) || `A/${12000 + idx}`
  const nic = getEmployeeNic(emp, idx)
  const employeeTin = getEmployeeTin(idx)

  // Build 12 months for Sri Lankan assessment year (April 2025 to March 2026)
  const months = [
    { name: 'April 2025', code: '2025-04', remDate: '2025-05-14' },
    { name: 'May 2025', code: '2025-05', remDate: '2025-06-13' },
    { name: 'June 2025', code: '2025-06', remDate: '2025-07-14' },
    { name: 'July 2025', code: '2025-07', remDate: '2025-08-14' },
    { name: 'August 2025', code: '2025-08', remDate: '2025-09-12' },
    { name: 'September 2025', code: '2025-09', remDate: '2025-10-14' },
    { name: 'October 2025', code: '2025-10', remDate: '2025-11-14' },
    { name: 'November 2025', code: '2025-11', remDate: '2025-12-12' },
    { name: 'December 2025', code: '2025-12', remDate: '2026-01-14' },
    { name: 'January 2026', code: '2026-01', remDate: '2026-02-13' },
    { name: 'February 2026', code: '2026-02', remDate: '2026-03-13' },
    { name: 'March 2026', code: '2026-03', remDate: '2026-04-14' },
  ]

  let annualGrossRemuneration = 0
  let annualNonCashBenefits = 0
  let annualAssessableRemuneration = 0
  let annualApitTaxDeducted = 0

  const monthlySchedule = months.map((m, mIdx) => {
    const isCurrentMonth = m.code === '2026-03'
    const gross = isCurrentMonth ? result.grossPay : Math.round(result.basicSalary * 1.15)
    const nonCash = mIdx % 3 === 0 ? 5000 : 0
    const assessable = gross + nonCash
    const tax = isCurrentMonth ? result.taxWithheld : Math.round(result.taxWithheld * 0.95 * 100) / 100

    annualGrossRemuneration += gross
    annualNonCashBenefits += nonCash
    annualAssessableRemuneration += assessable
    annualApitTaxDeducted += tax

    return {
      monthName: m.name,
      periodCode: m.code,
      grossRemuneration: gross,
      nonCashBenefits: nonCash,
      totalAssessableRemuneration: assessable,
      apitTaxDeducted: tax,
      remittanceDate: m.remDate,
      remittanceRef: `IRD-APIT-${m.code.replace('-', '')}-${String(idx + 1).padStart(3, '0')}`,
    }
  })

  annualGrossRemuneration = Math.round(annualGrossRemuneration * 100) / 100
  annualNonCashBenefits = Math.round(annualNonCashBenefits * 100) / 100
  annualAssessableRemuneration = Math.round(annualAssessableRemuneration * 100) / 100
  annualApitTaxDeducted = Math.round(annualApitTaxDeducted * 100) / 100
  const statutoryReliefThreshold = 1200000.0 // LKR 1.2M standard personal relief threshold under Inland Revenue Act
  const taxableRemuneration = Math.max(0, annualAssessableRemuneration - statutoryReliefThreshold)
  const annualNetPaid = Math.round((annualGrossRemuneration - annualApitTaxDeducted) * 100) / 100

  const digitalSealPayload = `${result.employeeCode}|${nic}|${annualAssessableRemuneration}|${annualApitTaxDeducted}|${assessmentYear}`
  const digitalSealHash = createHash('sha256').update(digitalSealPayload).digest('hex')

  return {
    status: 200,
    body: {
      employer: {
        name: 'Demo Company (Pvt) Ltd',
        tin: '102938475',
        address: 'Level 14, West Tower, World Trade Center, Echelon Square, Colombo 01',
        employerEpfNo: 'E/10948',
      },
      employee: {
        id: result.employeeId,
        code: result.employeeCode,
        fullName: result.employeeName,
        nic,
        tin: employeeTin,
        designation: result.designation,
        department: result.department,
        epfNo,
      },
      assessmentYear,
      periodCovered: '01st April 2025 to 31st March 2026',
      monthlySchedule,
      totals: {
        annualGrossRemuneration,
        annualNonCashBenefits,
        annualAssessableRemuneration,
        statutoryReliefThreshold,
        taxableRemuneration,
        annualApitTaxDeducted,
        annualNetPaid,
      },
      declaration: {
        statement:
          'I certify that the particulars furnished in this certificate are true, correct, and complete in terms of Section 83 of the Inland Revenue Act No. 24 of 2017, and the tax deducted has been duly remitted to the Commissioner General of Inland Revenue.',
        signatoryName: 'Anusha Sivakumar',
        signatoryTitle: 'Head of Finance & Compliance',
        issuedDate: '2026-03-25',
        digitalSealHash,
      },
    },
  }
}

function numberToWordsLKR(amount: number): string {
  const singleDigits = ['', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine']
  const teens = [
    'Ten',
    'Eleven',
    'Twelve',
    'Thirteen',
    'Fourteen',
    'Fifteen',
    'Sixteen',
    'Seventeen',
    'Eighteen',
    'Nineteen',
  ]
  const tens = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety']

  function convertChunk(n: number): string {
    let str = ''
    if (n >= 100) {
      str += singleDigits[Math.floor(n / 100)] + ' Hundred '
      n %= 100
    }
    if (n >= 10 && n <= 19) {
      str += teens[n - 10] + ' '
    } else if (n >= 20) {
      str += tens[Math.floor(n / 10)] + ' '
      if (n % 10 > 0) {
        str += singleDigits[n % 10] + ' '
      }
    } else if (n > 0) {
      str += singleDigits[n] + ' '
    }
    return str.trim()
  }

  const integerPart = Math.floor(Math.abs(amount))
  const cents = Math.round((Math.abs(amount) - integerPart) * 100)

  if (integerPart === 0 && cents === 0) {
    return 'Zero Sri Lankan Rupees Only'
  }

  let result = ''
  const billions = Math.floor(integerPart / 1_000_000_000)
  const millions = Math.floor((integerPart % 1_000_000_000) / 1_000_000)
  const thousands = Math.floor((integerPart % 1_000_000) / 1_000)
  const remainder = integerPart % 1_000

  if (billions > 0) result += convertChunk(billions) + ' Billion '
  if (millions > 0) result += convertChunk(millions) + ' Million '
  if (thousands > 0) result += convertChunk(thousands) + ' Thousand '
  if (remainder > 0) result += convertChunk(remainder) + ' '

  result = result.trim() + ' Sri Lankan Rupees'
  if (cents > 0) {
    result += ` and ${convertChunk(cents)} Cents`
  }
  return result + ' Only'
}

function generateSvgQrCode(hash: string): string {
  const size = 25
  const grid: boolean[][] = []
  for (let r = 0; r < size; r++) {
    const row: boolean[] = []
    for (let c = 0; c < size; c++) {
      row.push(false)
    }
    grid.push(row)
  }

  function drawFinder(r: number, c: number) {
    for (let i = 0; i < 7; i++) {
      for (let j = 0; j < 7; j++) {
        if (i === 0 || i === 6 || j === 0 || j === 6 || (i >= 2 && i <= 4 && j >= 2 && j <= 4)) {
          const row = grid[r + i]
          if (row) row[c + j] = true
        }
      }
    }
  }

  drawFinder(0, 0)
  drawFinder(0, size - 7)
  drawFinder(size - 7, 0)

  for (let i = 8; i < size - 8; i++) {
    if (i % 2 === 0) {
      const row6 = grid[6]
      if (row6) row6[i] = true
      const rowI = grid[i]
      if (rowI) rowI[6] = true
    }
  }

  let hashIdx = 0
  for (let r = 0; r < size; r++) {
    const row = grid[r]
    if (!row) continue
    for (let c = 0; c < size; c++) {
      const inFinder1 = r < 8 && c < 8
      const inFinder2 = r < 8 && c >= size - 8
      const inFinder3 = r >= size - 8 && c < 8
      if (!inFinder1 && !inFinder2 && !inFinder3 && row[c] === false) {
        const charCode = hash.charCodeAt(hashIdx % hash.length)
        row[c] = (charCode + r * 7 + c * 13) % 3 === 0
        hashIdx++
      }
    }
  }

  const rects: string[] = []
  for (let r = 0; r < size; r++) {
    const row = grid[r]
    if (!row) continue
    for (let c = 0; c < size; c++) {
      if (row[c]) {
        rects.push(`<rect x="${c * 4}" y="${r * 4}" width="4" height="4" fill="#0f172a" />`)
      }
    }
  }

  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size * 4} ${size * 4}" width="90" height="90"><rect width="${size * 4}" height="${size * 4}" fill="#ffffff"/>${rects.join('')}</svg>`
}

function buildPayslipDocument(world: World, runId: string, resultId: string) {
  const run = world.payrollRuns.get(runId)
  if (!run) throw notFound('Payroll run not found')

  const results = world.payrollResultsByRun.get(runId) ?? []
  const result = results.find((r) => r.id === resultId || r.employeeId === resultId || r.employeeCode === resultId)
  if (!result) throw notFound('Employee payroll result not found')

  const employeeIndex = Array.from(world.employees.values()).findIndex((e) => e.id === result.employeeId)
  const emp = world.employees.get(result.employeeId)
  const idx = employeeIndex >= 0 ? employeeIndex : 0

  const period = world.payPeriods.get(run.payPeriodId)
  const rawLines = world.payrollLinesByResult.get(result.id) ?? []

  let earnings = rawLines.filter((l) => l.lineCategory === 'EARNING')
  let deductions = rawLines.filter(
    (l) =>
      l.lineCategory === 'STATUTORY_DEDUCTION' ||
      l.lineCategory === 'VOLUNTARY_DEDUCTION' ||
      l.lineCategory === 'TAX',
  )

  if (earnings.length === 0) {
    const allowance = Math.max(0, result.grossPay - result.basicSalary)
    earnings = [
      {
        id: `earn-basic-${result.id}`,
        payrollResultId: result.id,
        lineCategory: 'EARNING',
        itemCode: 'BASIC',
        itemName: 'Basic Salary',
        amount: result.basicSalary,
        isStatutory: true,
        calculationTrace: 'Standard base pay contract tier',
      },
    ]
    if (allowance > 0) {
      earnings.push({
        id: `earn-allow-${result.id}`,
        payrollResultId: result.id,
        lineCategory: 'EARNING',
        itemCode: 'FIXED_ALLOW',
        itemName: 'Fixed Operational & Transport Allowance',
        amount: allowance,
        isStatutory: false,
        calculationTrace: 'Monthly executive allowance entitlement',
      })
    }
  }

  if (deductions.length === 0) {
    deductions = []
    if (result.totalStatutoryEmployee > 0) {
      deductions.push({
        id: `ded-epf-${result.id}`,
        payrollResultId: result.id,
        lineCategory: 'STATUTORY_DEDUCTION',
        itemCode: 'EPF_EE_8',
        itemName: 'Employees Provident Fund (EPF 8%)',
        amount: result.totalStatutoryEmployee,
        isStatutory: true,
        calculationTrace: '8% statutory employee pension contribution',
      })
    }
    if (result.taxWithheld > 0) {
      deductions.push({
        id: `ded-tax-${result.id}`,
        payrollResultId: result.id,
        lineCategory: 'TAX',
        itemCode: 'APIT_TAX',
        itemName: 'Advance Personal Income Tax (APIT/PAYE)',
        amount: result.taxWithheld,
        isStatutory: true,
        calculationTrace: 'Inland Revenue Act No. 24 of 2017 monthly bracket deduction',
      })
    }
    if (result.totalVoluntaryDeductions > 0) {
      deductions.push({
        id: `ded-vol-${result.id}`,
        payrollResultId: result.id,
        lineCategory: 'VOLUNTARY_DEDUCTION',
        itemCode: 'WELFARE',
        itemName: 'Staff Welfare Association & Benevolent Fund',
        amount: result.totalVoluntaryDeductions,
        isStatutory: false,
        calculationTrace: 'Voluntary monthly welfare payroll deduction authorization',
      })
    }
  }

  const employerEpf =
    result.totalStatutoryEmployer > 0
      ? Math.round((result.totalStatutoryEmployer * 12 / 15) * 100) / 100
      : Math.round(result.basicSalary * 0.12 * 100) / 100
  const employerEtf =
    result.totalStatutoryEmployer > 0
      ? Math.round((result.totalStatutoryEmployer * 3 / 15) * 100) / 100
      : Math.round(result.basicSalary * 0.03 * 100) / 100
  const totalEmployerContributions = Math.round((employerEpf + employerEtf) * 100) / 100
  const totalCostToCompany = Math.round((result.grossPay + totalEmployerContributions) * 100) / 100

  const epfNo = (emp?.customFields?.epfNumber as string) || `A/${12000 + idx}`
  const nic = getEmployeeNic(emp, idx)
  const bankAccountMasked = `•••• •••• ${String(1100 + idx * 77).slice(-4)}`

  const hashContent = `${result.employeeCode}:${result.netPay}:${run.id}:${period?.code || '2026-03'}`
  let rawHash = 0
  for (let i = 0; i < hashContent.length; i++) {
    rawHash = (rawHash << 5) - rawHash + hashContent.charCodeAt(i)
    rawHash |= 0
  }
  const verificationHash = `SEC-${Math.abs(rawHash).toString(16).toUpperCase().padStart(8, '0')}-SHA256-${result.employeeCode}`
  const verificationUrl = `https://hr.company.internal/verify/payslip/${verificationHash}`
  const qrCodeSvg = generateSvgQrCode(verificationHash)

  return {
    id: `ps-${run.id}-${result.id}`,
    company: {
      legalName: 'Antigravity Global Technologies (Pvt) Ltd',
      tradingName: 'Antigravity Enterprise HR',
      registrationNumber: 'PV-00289192',
      taxIdentificationNumber: '109283741-0000',
      epfEmployerNumber: 'E/10948',
      etfEmployerNumber: 'ETF/88492',
      registeredAddress: 'Level 14, West Tower, World Trade Center, Echelon Square',
      cityCountry: 'Colombo 01, Sri Lanka',
      contactPhone: '+94 11 234 5678',
      contactEmail: 'payroll@company.com',
      website: 'www.antigravity.company',
      currency: 'LKR',
    },
    employee: {
      employeeId: result.employeeId,
      employeeCode: result.employeeCode,
      fullName: result.employeeName,
      designation: result.designation,
      department: result.department,
      dateOfJoining: '2022-04-15',
      nicPassportNumber: nic,
      epfMemberNumber: epfNo,
      bankName: 'Commercial Bank of Ceylon PLC',
      bankBranch: 'Colombo Main Branch (001)',
      bankAccountNumberMasked: bankAccountMasked,
      paymentMethod: 'Electronic Bank Transfer (SLIPS/ACH)',
    },
    period: {
      payPeriodCode: period?.code || 'period-2026-03',
      payPeriodName: period?.code ? `March 2026 Monthly Cycle` : 'March 2026 Monthly Cycle',
      startDate: period?.startDate || '2026-03-01',
      endDate: period?.endDate || '2026-03-31',
      paymentDate: period?.paymentDate || '2026-03-25',
      payrollRunId: run.id,
      payrollStatus: result.paymentStatus,
    },
    earnings: earnings.map((e) => ({
      id: e.id,
      category: e.lineCategory,
      code: e.itemCode,
      description: e.itemName,
      amount: e.amount,
      isStatutory: e.isStatutory,
      calculationTrace: e.calculationTrace,
    })),
    deductions: deductions.map((d) => ({
      id: d.id,
      category: d.lineCategory,
      code: d.itemCode,
      description: d.itemName,
      amount: d.amount,
      isStatutory: d.isStatutory,
      calculationTrace: d.calculationTrace,
    })),
    totals: {
      basicSalary: result.basicSalary,
      allowancesTotal: Math.max(0, result.grossPay - result.basicSalary),
      overtimeTotal: earnings.find((e) => e.itemCode.includes('OT'))?.amount || 0,
      grossEarnings: result.grossPay,
      statutoryEmployeeEpf: result.totalStatutoryEmployee,
      apitTaxWithheld: result.taxWithheld,
      voluntaryDeductionsTotal: result.totalVoluntaryDeductions,
      totalDeductions: result.totalStatutoryEmployee + result.taxWithheld + result.totalVoluntaryDeductions,
      netPay: result.netPay,
      netPayInWords: numberToWordsLKR(result.netPay),
      employerEpf,
      employerEtf,
      totalEmployerContributions,
      totalCostToCompany,
    },
    ytd: {
      ytdGrossPay: Math.round(result.grossPay * 3 * 100) / 100,
      ytdTaxWithheld: Math.round(result.taxWithheld * 3 * 100) / 100,
      ytdEmployeeEpf: Math.round(result.totalStatutoryEmployee * 3 * 100) / 100,
      ytdEmployerEpf: Math.round(employerEpf * 3 * 100) / 100,
      ytdNetPay: Math.round(result.netPay * 3 * 100) / 100,
    },
    security: {
      confidentialWatermark: 'CONFIDENTIAL · STRICTLY PRIVATE & PERSONAL',
      verificationHash,
      verificationUrl,
      qrCodeSvg,
      generatedAt: new Date().toISOString(),
      authorizedSignatory: 'Anusha Sivakumar',
      signatoryTitle: 'Head of Finance & Compliance',
    },
  }
}

function getPayslipDocument(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'payroll.view')
  const runId = pathParam(request, 'id')
  const resultId = pathParam(request, 'resultId')
  const payslip = buildPayslipDocument(world, runId, resultId)
  return { status: 200, body: payslip }
}

function getPayslipBatch(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'payroll.view')
  const runId = pathParam(request, 'id')
  const department = request.query.get('department')
  const results = world.payrollResultsByRun.get(runId) ?? []

  let filtered = results
  if (department && department !== 'ALL') {
    filtered = filtered.filter((r) => r.department === department)
  }

  const payslips = filtered.map((r) => buildPayslipDocument(world, runId, r.id))
  return { status: 200, body: { payslips } }
}

/* -------------------------------------------------------------------------- */
/* Attendance & Shift Roster Engine (P2-BE / P2-WEB)                           */
/* -------------------------------------------------------------------------- */

function listShifts(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  return { status: 200, body: { shifts: Array.from(world.shifts.values()) } }
}

function getRoster(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const department = request.query.get('department')
  const days = [
    { date: '2026-03-09', dayName: 'Mon', dayOfMonth: 9, isWeekend: false, isHoliday: false },
    { date: '2026-03-10', dayName: 'Tue', dayOfMonth: 10, isWeekend: false, isHoliday: false },
    { date: '2026-03-11', dayName: 'Wed', dayOfMonth: 11, isWeekend: false, isHoliday: true, holidayName: 'Maha Shivaratri Day' },
    { date: '2026-03-12', dayName: 'Thu', dayOfMonth: 12, isWeekend: false, isHoliday: false },
    { date: '2026-03-13', dayName: 'Fri', dayOfMonth: 13, isWeekend: false, isHoliday: false },
    { date: '2026-03-14', dayName: 'Sat', dayOfMonth: 14, isWeekend: true, isHoliday: false },
    { date: '2026-03-15', dayName: 'Sun', dayOfMonth: 15, isWeekend: true, isHoliday: false },
  ]
  let emps = Array.from(world.employees.values())
  if (department && department !== 'ALL') {
    emps = emps.filter((e) => (e.departmentId ?? 'Engineering') === department)
  }
  const employees = emps.map((e) => ({
    id: e.id,
    code: e.employeeCode ?? 'EMP',
    name: e.displayName ?? e.firstName ?? 'Employee',
    department: e.departmentId ?? 'Engineering',
  }))
  const schedules: Record<string, DemoShiftSchedule[]> = {}
  emps.forEach((e) => {
    schedules[e.id] = world.shiftSchedules.get(e.id) ?? []
  })
  return { status: 200, body: { days, schedules, employees } }
}

function assignShift(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const body = objectBody(request)
  const employeeId = stringField(body, 'employeeId')
  if (!employeeId) throw badRequest('VALIDATION_ERROR', 'employeeId is required')
  const workDate = stringField(body, 'workDate')
  if (!workDate) throw badRequest('VALIDATION_ERROR', 'workDate is required')
  const shiftId = typeof body.shiftId === 'string' ? body.shiftId : undefined
  const isRestDay = Boolean(body.isRestDay)

  const emp = world.employees.get(employeeId)
  if (!emp) throw notFound('Employee not found')

  const empName = emp.displayName ?? emp.firstName ?? 'Employee'
  const dept = emp.departmentId ?? 'Engineering'
  const shift = shiftId ? world.shifts.get(shiftId) : undefined

  let list = world.shiftSchedules.get(employeeId)
  if (!list) {
    list = []
    world.shiftSchedules.set(employeeId, list)
  }

  const existingIdx = list.findIndex((s) => s.workDate === workDate)
  const existingItem = existingIdx >= 0 ? list[existingIdx] : undefined
  const item: DemoShiftSchedule = {
    id: existingItem ? existingItem.id : `sched-${employeeId}-${workDate}`,
    employeeId,
    employeeCode: emp.employeeCode ?? 'EMP',
    employeeName: empName,
    department: dept,
    workDate,
    shiftId: isRestDay ? undefined : shift?.id,
    shiftCode: isRestDay ? undefined : shift?.code,
    shiftName: isRestDay ? undefined : shift?.name,
    shiftColor: isRestDay ? '#64748b' : shift?.color ?? '#2563eb',
    startTime: isRestDay ? undefined : shift?.startTime,
    endTime: isRestDay ? undefined : shift?.endTime,
    isRestDay,
    isHoliday: false,
    source: 'MANUAL',
  }

  if (existingIdx >= 0) {
    list[existingIdx] = item
  } else {
    list.push(item)
  }

  return { status: 200, body: item }
}

function listPunches(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const date = request.query.get('date')
  const employeeId = request.query.get('employeeId')
  const flag = request.query.get('flag')
  const source = request.query.get('source')

  let results = [...world.rawPunches]
  if (date) {
    results = results.filter((p) => p.punchedAt.startsWith(date))
  }
  if (employeeId) {
    results = results.filter((p) => p.employeeId === employeeId)
  }
  if (source && source !== 'ALL') {
    results = results.filter((p) => p.source === source)
  }
  if (flag === 'SPOOFED') {
    results = results.filter((p) => p.isMockLocation)
  } else if (flag === 'OUTSIDE_GEOFENCE') {
    results = results.filter((p) => p.geofenceStatus === 'OUTSIDE')
  } else if (flag === 'ANOMALIES_ONLY') {
    results = results.filter((p) => p.isMockLocation || p.geofenceStatus === 'OUTSIDE')
  }

  results.sort((a, b) => b.punchedAt.localeCompare(a.punchedAt))
  return { status: 200, body: { punches: results } }
}

function ingestPunch(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const body = objectBody(request)
  const employeeId = stringField(body, 'employeeId')
  if (!employeeId) throw badRequest('VALIDATION_ERROR', 'employeeId is required')
  const punchedAt = stringField(body, 'punchedAt') || new Date().toISOString()
  const punchType = (stringField(body, 'punchType') || 'IN') as 'IN' | 'OUT' | 'BREAK_IN' | 'BREAK_OUT' | 'AUTO'
  const source = (stringField(body, 'source') || 'WEB_PORTAL') as 'BIOMETRIC_DEVICE' | 'MOBILE_APP' | 'KIOSK' | 'WEB_PORTAL' | 'MANUAL_IMPORT'
  const deviceId = typeof body.deviceId === 'string' ? body.deviceId : undefined
  const locationName = typeof body.locationName === 'string' ? body.locationName : undefined
  const isMockLocation = Boolean(body.isMockLocation)
  const geofenceStatus = (typeof body.geofenceStatus === 'string' ? body.geofenceStatus : 'INSIDE') as 'INSIDE' | 'OUTSIDE' | 'UNKNOWN' | 'NOT_APPLICABLE'

  const emp = world.employees.get(employeeId)
  if (!emp) throw notFound('Employee not found')

  const newPunch: DemoRawPunch = {
    id: `punch-${randomUUID().slice(0, 8)}`,
    employeeId,
    employeeCode: emp.employeeCode ?? 'EMP',
    employeeName: emp.displayName ?? emp.firstName ?? 'Employee',
    department: emp.departmentId ?? 'Engineering',
    punchedAt,
    punchType,
    source,
    deviceId: deviceId ?? (source === 'MOBILE_APP' ? 'MOB-APP-PORTAL' : 'WEB-PORTAL-01'),
    locationName: locationName ?? (source === 'MOBILE_APP' ? 'HQ Mobile Geofence (Colombo)' : 'Corporate Web Console'),
    geofenceStatus,
    isMockLocation,
    recordedOffline: false,
    syncedAt: new Date().toISOString(),
  }

  world.rawPunches.unshift(newPunch)
  return { status: 201, body: newPunch }
}

function getKioskEmployeeStatus(world: World, request: DemoRequest): DemoReply {
  const employeeCode = pathParam(request, 'employeeCode').trim().toUpperCase()
  const emp = Array.from(world.employees.values()).find(
    (e) => (e.employeeCode ?? '').toUpperCase() === employeeCode || e.id === employeeCode,
  )
  if (!emp) throw notFound(`Employee with code "${employeeCode}" not found`)

  const today = '2026-03-09'
  const schedules = world.shiftSchedules.get(emp.id) ?? []
  const todaySched = schedules.find((s) => s.workDate === today) || schedules[0]

  const shift = todaySched
    ? {
        id: todaySched.shiftId ?? 'shift-gen',
        code: todaySched.shiftCode ?? 'GEN_0830',
        name: todaySched.shiftName ?? 'General Day (08:30 - 17:30)',
        startTime: todaySched.startTime ?? '08:30',
        endTime: todaySched.endTime ?? '17:30',
        color: todaySched.shiftColor ?? '#2563eb',
        isRestDay: todaySched.isRestDay,
        isHoliday: todaySched.isHoliday,
      }
    : {
        id: 'shift-gen',
        code: 'GEN_0830',
        name: 'General Day (08:30 - 17:30)',
        startTime: '08:30',
        endTime: '17:30',
        color: '#2563eb',
        isRestDay: false,
        isHoliday: false,
      }

  const empPunches = world.rawPunches.filter((p) => p.employeeId === emp.id)
  const lastPunch = empPunches[0]
  const isClockedIn = lastPunch
    ? lastPunch.punchType === 'IN' || lastPunch.punchType === 'BREAK_OUT'
    : false

  const nextSuggestedAction: 'IN' | 'OUT' | 'BREAK_IN' | 'BREAK_OUT' =
    !lastPunch || lastPunch.punchType === 'OUT'
      ? 'IN'
      : lastPunch.punchType === 'BREAK_IN'
        ? 'BREAK_OUT'
        : 'OUT'

  const avatarInitials = `${emp.firstName?.charAt(0) ?? ''}${emp.lastName?.charAt(0) ?? ''}`.toUpperCase()

  return {
    status: 200,
    body: {
      employee: {
        id: emp.id,
        code: emp.employeeCode ?? 'EMP',
        name: emp.displayName ?? `${emp.firstName} ${emp.lastName}`,
        department: emp.departmentId ?? 'Operations',
        designation: emp.designationId ?? 'Staff',
        avatarInitials: avatarInitials || 'EM',
      },
      shift,
      currentStatus: {
        isClockedIn,
        lastPunchType: lastPunch?.punchType ?? null,
        lastPunchAt: lastPunch?.punchedAt ?? null,
        nextSuggestedAction,
      },
    },
  }
}

function submitKioskPunch(world: World, request: DemoRequest): DemoReply {
  const body = objectBody(request)
  const employeeCode = stringField(body, 'employeeCode')?.trim().toUpperCase()
  if (!employeeCode) throw badRequest('VALIDATION_ERROR', 'employeeCode is required')

  const pin = stringField(body, 'pin')
  if (!pin) throw badRequest('VALIDATION_ERROR', '4-digit security PIN is required')

  const punchType = (stringField(body, 'punchType') || 'IN') as
    | 'IN'
    | 'OUT'
    | 'BREAK_IN'
    | 'BREAK_OUT'
  const kioskDeviceId = stringField(body, 'kioskDeviceId') || 'KIOSK-FACT-01'
  const kioskLocation = stringField(body, 'kioskLocation') || 'Factory Floor - Gate 3 Entrance'
  const photoSnapshot = typeof body.photoSnapshot === 'string' ? body.photoSnapshot : undefined

  const emp = Array.from(world.employees.values()).find(
    (e) => (e.employeeCode ?? '').toUpperCase() === employeeCode || e.id === employeeCode,
  )
  if (!emp) throw notFound(`Employee with code "${employeeCode}" not found`)

  // Validate 4-digit PIN: accepts default '1234' or code numeric digits
  const numericCode = (emp.employeeCode ?? '').replace(/\D/g, '').padStart(4, '0')
  const isValidPin = pin === '1234' || pin === numericCode
  if (!isValidPin) {
    throw badRequest('INVALID_PIN', 'Invalid 4-digit security PIN. Please try again.')
  }

  const now = new Date().toISOString()
  const confirmationId = `kiosk-ack-${randomUUID().slice(0, 8)}`
  const actionLabel =
    punchType === 'IN'
      ? 'Clock In'
      : punchType === 'OUT'
        ? 'Clock Out'
        : punchType === 'BREAK_IN'
          ? 'Break Started'
          : 'Break Ended'

  const newPunch: DemoRawPunch = {
    id: `punch-kiosk-${randomUUID().slice(0, 8)}`,
    employeeId: emp.id,
    employeeCode: emp.employeeCode ?? 'EMP',
    employeeName: emp.displayName ?? `${emp.firstName} ${emp.lastName}`,
    department: emp.departmentId ?? 'Operations',
    punchedAt: now,
    punchType,
    source: 'KIOSK',
    deviceId: kioskDeviceId,
    locationName: kioskLocation,
    geofenceStatus: 'INSIDE',
    isMockLocation: false,
    recordedOffline: false,
    syncedAt: now,
  }

  world.rawPunches.unshift(newPunch)

  const timeString = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  const greeting = `${actionLabel} verified! Welcome ${emp.firstName}, your attendance was logged at ${timeString}.`

  return {
    status: 201,
    body: {
      confirmationId,
      employee: {
        id: emp.id,
        code: emp.employeeCode ?? 'EMP',
        name: newPunch.employeeName,
        department: newPunch.department,
      },
      punchedAt: now,
      punchType,
      shiftName: 'General Day (08:30 - 17:30)',
      greeting,
      photoCaptured: Boolean(photoSnapshot),
      rawPunch: newPunch,
    },
  }
}

function listKioskRecentPunches(world: World, request: DemoRequest): DemoReply {
  const deviceId = request.query.get('deviceId')
  let punches = world.rawPunches
  if (deviceId) {
    punches = punches.filter((p) => p.deviceId === deviceId || p.source === 'KIOSK')
  }
  return { status: 200, body: { punches: punches.slice(0, 15) } }
}

function listDailyAttendance(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const date = request.query.get('date') ?? '2026-03-09'
  const department = request.query.get('department')
  const status = request.query.get('status')
  const anomaly = request.query.get('anomaly')

  const allRecords: DemoDailyAttendance[] = []
  for (const list of world.dailyAttendance.values()) {
    allRecords.push(...list)
  }

  let records = allRecords.filter((r) => r.workDate === date)
  if (records.length === 0 && allRecords.length > 0) {
    records = allRecords
  }

  if (department && department !== 'ALL') {
    records = records.filter((r) => r.department === department)
  }
  if (status && status !== 'ALL') {
    records = records.filter((r) => r.dayStatus === status)
  }
  if (anomaly && anomaly !== 'ALL') {
    records = records.filter((r) => r.anomalyFlags.includes(anomaly))
  }

  const totalEmployees = world.employees.size
  const presentCount = records.filter((r) => r.dayStatus === 'PRESENT').length
  const halfDayCount = records.filter((r) => r.dayStatus === 'HALF_DAY').length
  const absentCount = records.filter((r) => r.dayStatus === 'ABSENT').length
  const restDayCount = records.filter((r) => r.dayStatus === 'REST_DAY').length
  const holidayCount = records.filter((r) => r.dayStatus === 'HOLIDAY').length
  const anomaliesCount = records.filter((r) => r.anomalyFlags.length > 0).length
  const totalOtHours = Math.round(
    (records.reduce((acc, r) => acc + (r.overtimeMinutesNormal + r.overtimeMinutesRestDay + r.overtimeMinutesHoliday), 0) / 60) * 10,
  ) / 10
  const totalLateMinutes = records.reduce((acc, r) => acc + r.lateMinutes, 0)

  return {
    status: 200,
    body: {
      records,
      summary: {
        totalEmployees,
        presentCount,
        halfDayCount,
        absentCount,
        restDayCount,
        holidayCount,
        anomaliesCount,
        totalOtHours,
        totalLateMinutes,
      },
    },
  }
}

function getDailyAttendanceDetails(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const id = pathParam(request, 'id')
  for (const list of world.dailyAttendance.values()) {
    const found = list.find((r) => r.id === id)
    if (found) return { status: 200, body: found }
  }
  throw notFound('Daily attendance record not found')
}

function recomputeAttendance(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  let count = 0
  for (const list of world.dailyAttendance.values()) {
    count += list.length
  }
  return { status: 200, body: { recomputedCount: count || 12 } }
}

function getPayrollVariableSummary(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const payPeriodId = request.query.get('payPeriodId') ?? 'period-2026m03'

  const items: any[] = []
  let totalNormalOt = 0
  let totalRestDayOt = 0
  let totalHolidayOt = 0
  let totalLatePenalty = 0
  let totalOtGross = 0
  let totalUnpaidAbsenceDays = 0

  let empWithOt = 0
  let empWithLate = 0
  let empWithAbsence = 0

  for (const [empId, list] of world.dailyAttendance.entries()) {
    const emp = world.employees.get(empId)
    const empName = emp?.displayName ?? emp?.firstName ?? 'Employee'
    const dept = emp?.departmentId ?? 'Engineering'
    const code = emp?.employeeCode ?? 'EMP'

    let nOt = 0
    let rOt = 0
    let hOt = 0
    let lateM = 0
    let unpaidDays = 0

    list.forEach((r) => {
      nOt += r.overtimeMinutesNormal
      rOt += r.overtimeMinutesRestDay
      hOt += r.overtimeMinutesHoliday
      lateM += r.lateMinutes
      if (r.dayStatus === 'ABSENT') unpaidDays += 1
      else if (r.dayStatus === 'HALF_DAY') unpaidDays += 0.5
    })

    const hourlyRate = 1250 // demo hourly rate
    const otPay = Math.round(((nOt * 1.5 + rOt * 2.0 + hOt * 2.5) / 60) * hourlyRate)
    const lateDeduction = Math.round((lateM / 60) * hourlyRate)

    totalNormalOt += nOt
    totalRestDayOt += rOt
    totalHolidayOt += hOt
    totalOtGross += otPay
    totalLatePenalty += lateDeduction
    totalUnpaidAbsenceDays += unpaidDays

    if (nOt + rOt + hOt > 0) empWithOt++
    if (lateM > 0) empWithLate++
    if (unpaidDays > 0) empWithAbsence++

    items.push({
      employeeId: empId,
      employeeCode: code,
      employeeName: empName,
      department: dept,
      normalOtMinutes: nOt,
      restDayOtMinutes: rOt,
      holidayOtMinutes: hOt,
      otGrossEarnings: otPay,
      lateMinutesTotal: lateM,
      latePenaltyDeduction: lateDeduction,
      unpaidAbsenceDays: unpaidDays,
    })
  }

  return {
    status: 200,
    body: {
      payPeriodId,
      totalNormalOtHours: Math.round((totalNormalOt / 60) * 10) / 10,
      totalRestDayOtHours: Math.round((totalRestDayOt / 60) * 10) / 10,
      totalHolidayOtHours: Math.round((totalHolidayOt / 60) * 10) / 10,
      totalOtEarningsAmount: totalOtGross,
      totalLatePenaltyDeductionAmount: totalLatePenalty,
      totalUnpaidAbsenceDays,
      employeeCountWithOt: empWithOt,
      employeeCountWithLatePenalty: empWithLate,
      employeeCountWithUnpaidAbsence: empWithAbsence,
      items,
    },
  }
}

/* -------------------------------------------------------------------------- */
/* Recruitment & ATS (V21)                                                    */
/* -------------------------------------------------------------------------- */

const reqStr = (src: Record<string, unknown>, key: string, fallback = ''): string =>
  src[key] !== undefined && src[key] !== null ? String(src[key]) : fallback

function listVacancies(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'recruitment.job.view')
  const status = request.query.get('status')
  const department = request.query.get('department')
  let list = Array.from(world.vacancies.values())
  if (status) list = list.filter((v: any) => v.status === status)
  if (department) list = list.filter((v: any) => v.department === department)
  return { status: 200, body: { vacancies: list } }
}

function createVacancy(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'recruitment.job.manage')
  const body = objectBody(request)
  const id = `vac-${Date.now().toString(36)}`
  const vacancy = {
    id,
    vacancyCode: `VAC-${String(world.vacancies.size + 1).padStart(3, '0')}`,
    title: reqStr(body, 'title'),
    department: reqStr(body, 'department'),
    location: body.location ? String(body.location) : 'Colombo HQ',
    employmentType: body.employmentType ? String(body.employmentType) : 'FULL_TIME',
    targetHireCount: Number(body.targetHireCount ?? 1),
    status: body.status ? String(body.status) : 'OPEN',
    jobDescription: body.jobDescription ? String(body.jobDescription) : '',
    minSalary: body.minSalary ? Number(body.minSalary) : undefined,
    maxSalary: body.maxSalary ? Number(body.maxSalary) : undefined,
    currency: body.currency ? String(body.currency) : 'LKR',
    openedDate: new Date().toISOString().split('T')[0],
    createdAt: new Date().toISOString(),
  }
  world.vacancies.set(id, vacancy)
  return { status: 201, body: { vacancy } }
}

function getVacancy(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'recruitment.job.view')
  const id = pathParam(request, 'id')
  const vacancy = world.vacancies.get(id)
  if (!vacancy) throw notFound(`Vacancy ${id} not found`)
  return { status: 200, body: { vacancy } }
}

function listCandidates(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'recruitment.candidate.view')
  const search = request.query.get('search')?.toLowerCase()
  let list = Array.from(world.candidates.values())
  if (search) {
    list = list.filter(
      (c: any) =>
        c.firstName?.toLowerCase().includes(search) ||
        c.lastName?.toLowerCase().includes(search) ||
        c.email?.toLowerCase().includes(search) ||
        c.skills?.some((s: string) => s.toLowerCase().includes(search))
    )
  }
  return { status: 200, body: { candidates: list } }
}

function createCandidate(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'recruitment.candidate.manage')
  const body = objectBody(request)
  const id = `cand-${Date.now().toString(36)}`
  const candidate = {
    id,
    firstName: reqStr(body, 'firstName'),
    lastName: reqStr(body, 'lastName'),
    email: reqStr(body, 'email'),
    phone: body.phone ? String(body.phone) : undefined,
    skills: Array.isArray(body.skills) ? body.skills : [],
    experienceYears: body.experienceYears ? Number(body.experienceYears) : undefined,
    currentCompany: body.currentCompany ? String(body.currentCompany) : undefined,
    source: body.source ? String(body.source) : 'DIRECT_APPLICATION',
    createdAt: new Date().toISOString(),
  }
  world.candidates.set(id, candidate)
  return { status: 201, body: { candidate } }
}

function listApplications(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'recruitment.candidate.view')
  const vacancyId = request.query.get('vacancyId')
  const candidateId = request.query.get('candidateId')
  const stage = request.query.get('stage')
  let list = Array.from(world.applications.values())
  if (vacancyId) list = list.filter((a: any) => a.vacancyId === vacancyId)
  if (candidateId) list = list.filter((a: any) => a.candidateId === candidateId)
  if (stage) list = list.filter((a: any) => a.stage === stage)
  return { status: 200, body: { applications: list } }
}

function createApplication(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'recruitment.candidate.manage')
  const body = objectBody(request)
  const vacancyId = reqStr(body, 'vacancyId')
  const candidateId = reqStr(body, 'candidateId')
  const vacancy = world.vacancies.get(vacancyId)
  const candidate = world.candidates.get(candidateId)
  const id = `app-${Date.now().toString(36)}`
  const application = {
    id,
    vacancyId,
    vacancyTitle: vacancy?.title ?? 'Role',
    candidateId,
    candidateName: candidate ? `${candidate.firstName} ${candidate.lastName}` : 'Candidate',
    candidateEmail: candidate?.email ?? '',
    stage: 'APPLIED',
    appliedDate: new Date().toISOString().split('T')[0],
    source: body.source ? String(body.source) : 'CAREERS_PORTAL',
    createdAt: new Date().toISOString(),
  }
  world.applications.set(id, application)
  return { status: 201, body: { application } }
}

function updateApplicationStage(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'recruitment.candidate.manage')
  const id = pathParam(request, 'id')
  const app = world.applications.get(id)
  if (!app) throw notFound(`Application ${id} not found`)
  const body = objectBody(request)
  const stage = reqStr(body, 'stage')
  app.stage = stage
  if (body.rejectionReason) app.rejectionReason = String(body.rejectionReason)
  app.updatedAt = new Date().toISOString()
  world.applications.set(id, app)
  return { status: 200, body: { application: app } }
}

function listInterviews(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'recruitment.candidate.view')
  const applicationId = request.query.get('applicationId')
  let list = Array.from(world.interviews.values())
  if (applicationId) list = list.filter((i: any) => i.applicationId === applicationId)
  return { status: 200, body: { interviews: list } }
}

function scheduleInterview(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'recruitment.candidate.manage')
  const body = objectBody(request)
  const applicationId = reqStr(body, 'applicationId')
  const app = world.applications.get(applicationId)
  const id = `int-${Date.now().toString(36)}`
  const interview = {
    id,
    applicationId,
    candidateName: app?.candidateName ?? 'Candidate',
    vacancyTitle: app?.vacancyTitle ?? 'Role',
    interviewType: body.interviewType ?? 'TECHNICAL_ASSESSMENT',
    scheduledAt: reqStr(body, 'scheduledAt'),
    durationMinutes: body.durationMinutes ? Number(body.durationMinutes) : 45,
    interviewerName: body.interviewerName ? String(body.interviewerName) : 'Lead Interviewer',
    meetingLink: body.meetingLink ? String(body.meetingLink) : 'https://meet.google.com/hrc-demo-sync',
    status: 'SCHEDULED',
    createdAt: new Date().toISOString(),
  }
  world.interviews.set(id, interview)
  return { status: 201, body: { interview } }
}

function submitScorecard(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'recruitment.candidate.manage')
  const interviewId = pathParam(request, 'id')
  const interview = world.interviews.get(interviewId)
  if (!interview) throw notFound(`Interview ${interviewId} not found`)
  const body = objectBody(request)
  const scorecardId = `sc-${Date.now().toString(36)}`
  const scorecard = {
    id: scorecardId,
    interviewId,
    interviewerName: body.interviewerName ? String(body.interviewerName) : 'Evaluator',
    overallRating: Number(body.overallRating ?? 4),
    recommendation: body.recommendation ?? 'STRONG_HIRE',
    feedback: body.feedback ? String(body.feedback) : '',
    criteriaRatings: Array.isArray(body.criteriaRatings) ? body.criteriaRatings : [],
    submittedAt: new Date().toISOString(),
  }
  const existing = world.scorecards.get(interviewId) ?? []
  world.scorecards.set(interviewId, [...existing, scorecard])
  interview.status = 'COMPLETED'
  interview.overallScore = scorecard.overallRating
  world.interviews.set(interviewId, interview)
  return { status: 201, body: { scorecard } }
}

function getApplicationOffer(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'recruitment.offer.manage')
  const id = pathParam(request, 'id')
  const offer = world.offers.get(id)
  return { status: 200, body: { offer: offer ?? null } }
}

function createApplicationOffer(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const applicationId = pathParam(request, 'id')
  const app = world.applications.get(applicationId)
  const body = objectBody(request)
  const offerId = `off-${Date.now().toString(36)}`
  const offer = {
    id: offerId,
    applicationId,
    candidateName: app?.candidateName ?? 'Candidate',
    vacancyTitle: app?.vacancyTitle ?? 'Role',
    offeredSalary: Number(body.offeredSalary ?? 350000),
    currency: body.currency ? String(body.currency) : 'LKR',
    joiningDate: body.joiningDate ? String(body.joiningDate) : '2026-04-01',
    validUntil: body.validUntil ? String(body.validUntil) : '2026-03-25',
    status: body.status ?? 'SENT',
    createdAt: new Date().toISOString(),
  }
  world.offers.set(applicationId, offer)
  if (app) {
    app.stage = 'OFFER'
    world.applications.set(applicationId, app)
  }
  return { status: 201, body: { offer } }
}

/* -------------------------------------------------------------------------- */
/* Document Management & Signatures (V23)                                     */
/* -------------------------------------------------------------------------- */

function listFolders(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const parentId = request.query.get('parentId')
  let list = Array.from(world.documentFolders.values())
  if (parentId !== undefined && parentId !== null) {
    list = list.filter((f: any) => f.parentId === (parentId === '' ? null : parentId))
  }
  return { status: 200, body: { folders: list } }
}

function createFolder(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const body = objectBody(request)
  const id = `fld-${Date.now().toString(36)}`
  const folder = {
    id,
    name: reqStr(body, 'name'),
    parentId: body.parentId ? String(body.parentId) : null,
    accessLevel: body.accessLevel ?? 'ALL_EMPLOYEES',
    documentCount: 0,
    createdAt: new Date().toISOString(),
  }
  world.documentFolders.set(id, folder)
  return { status: 201, body: { folder } }
}

function listDocuments(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const folderId = request.query.get('folderId')
  const employeeId = request.query.get('employeeId')
  const search = request.query.get('search')?.toLowerCase()
  let list = Array.from(world.companyDocuments.values())
  if (folderId) list = list.filter((d: any) => d.folderId === folderId)
  if (employeeId) list = list.filter((d: any) => d.employeeId === employeeId)
  if (search) {
    list = list.filter(
      (d: any) =>
        d.title?.toLowerCase().includes(search) ||
        d.category?.toLowerCase().includes(search) ||
        d.tags?.some((t: string) => t.toLowerCase().includes(search))
    )
  }
  return { status: 200, body: { documents: list } }
}

function createDocument(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const body = objectBody(request)
  const id = `doc-${Date.now().toString(36)}`
  const doc = {
    id,
    title: reqStr(body, 'title'),
    folderId: body.folderId ? String(body.folderId) : 'fld-root-policies',
    category: body.category ? String(body.category) : 'POLICY',
    tags: Array.isArray(body.tags) ? body.tags : [],
    currentVersion: 1,
    fileSizeBytes: body.fileSizeBytes ? Number(body.fileSizeBytes) : 256000,
    mimeType: body.mimeType ? String(body.mimeType) : 'application/pdf',
    fileName: body.fileName ? String(body.fileName) : 'document.pdf',
    isConfidential: Boolean(body.isConfidential),
    uploadedBy: 'Nimali Wickramasinghe',
    uploadedAt: new Date().toISOString(),
  }
  world.companyDocuments.set(id, doc)
  world.documentVersions.set(id, [
    {
      versionNumber: 1,
      fileName: doc.fileName,
      fileSizeBytes: doc.fileSizeBytes,
      mimeType: doc.mimeType,
      uploadedAt: doc.uploadedAt,
      uploadedBy: doc.uploadedBy,
      changeSummary: 'Initial document upload',
    },
  ])
  return { status: 201, body: { document: doc } }
}

function getDocumentDetails(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const id = pathParam(request, 'id')
  const doc = world.companyDocuments.get(id)
  if (!doc) throw notFound(`Document ${id} not found`)
  const versions = world.documentVersions.get(id) ?? []
  return { status: 200, body: { document: doc, versions } }
}

function createDocumentVersion(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const id = pathParam(request, 'id')
  const doc = world.companyDocuments.get(id)
  if (!doc) throw notFound(`Document ${id} not found`)
  const body = objectBody(request)
  const newVerNumber = (doc.currentVersion || 1) + 1
  const version = {
    versionNumber: newVerNumber,
    fileName: body.fileName ? String(body.fileName) : doc.fileName,
    fileSizeBytes: body.fileSizeBytes ? Number(body.fileSizeBytes) : doc.fileSizeBytes,
    mimeType: body.mimeType ? String(body.mimeType) : doc.mimeType,
    uploadedAt: new Date().toISOString(),
    uploadedBy: 'Nimali Wickramasinghe',
    changeSummary: body.changeSummary ? String(body.changeSummary) : `Version ${newVerNumber}`,
  }
  const versions = world.documentVersions.get(id) ?? []
  versions.unshift(version)
  world.documentVersions.set(id, versions)
  doc.currentVersion = newVerNumber
  doc.uploadedAt = version.uploadedAt
  world.companyDocuments.set(id, doc)
  return { status: 201, body: { version, document: doc } }
}

function listTemplates(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  return { status: 200, body: { templates: Array.from(world.documentTemplates.values()) } }
}

function getTemplate(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const id = pathParam(request, 'id')
  const template = world.documentTemplates.get(id)
  if (!template) throw notFound(`Template ${id} not found`)
  return { status: 200, body: { template } }
}

function listLetterRequests(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const status = request.query.get('status')
  let list = Array.from(world.letterRequests.values())
  if (status) list = list.filter((r: any) => r.status === status)
  return { status: 200, body: { letterRequests: list } }
}

function createLetterRequest(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const body = objectBody(request)
  const id = `lr-${Date.now().toString(36)}`
  const templateId = reqStr(body, 'templateId')
  const template = world.documentTemplates.get(templateId)
  const req = {
    id,
    templateId,
    templateTitle: template?.title ?? 'Document Letter',
    employeeId: body.employeeId ? String(body.employeeId) : 'de300000-0001-4000-8000-000000000002',
    employeeName: body.employeeName ? String(body.employeeName) : 'Kasun Fernando',
    purpose: body.purpose ? String(body.purpose) : 'Official Requirement',
    status: 'REQUESTED',
    createdAt: new Date().toISOString(),
  }
  world.letterRequests.set(id, req)
  return { status: 201, body: { letterRequest: req } }
}

function approveLetterRequest(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const id = pathParam(request, 'id')
  const req = world.letterRequests.get(id)
  if (!req) throw notFound(`Letter request ${id} not found`)
  req.status = 'GENERATED'
  req.generatedDocumentId = `doc-generated-${Date.now().toString(36)}`
  req.approvedAt = new Date().toISOString()
  world.letterRequests.set(id, req)
  return { status: 200, body: { letterRequest: req } }
}

function listSignatureRequests(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const status = request.query.get('status')
  let list = Array.from(world.signatureRequests.values())
  if (status) list = list.filter((s: any) => s.status === status)
  return { status: 200, body: { signatureRequests: list } }
}

function getSignatureRequestDetails(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const id = pathParam(request, 'id')
  const req = world.signatureRequests.get(id)
  if (!req) throw notFound(`Signature request ${id} not found`)
  const auditTrail = world.signatureAuditLogs.get(id) ?? []
  return { status: 200, body: { signatureRequest: req, auditTrail } }
}

function createSignatureRequest(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const body = objectBody(request)
  const id = `sig-${Date.now().toString(36)}`
  const signers = Array.isArray(body.signers)
    ? body.signers.map((s: any, idx: number) => ({
        id: `sgn-${id}-${idx + 1}`,
        signerName: s.signerName,
        signerEmail: s.signerEmail,
        status: 'PENDING',
      }))
    : [
        {
          id: `sgn-${id}-1`,
          signerName: 'Kasun Fernando',
          signerEmail: 'kasun.fernando@acme.test',
          status: 'PENDING',
        },
      ]

  const req = {
    id,
    title: reqStr(body, 'title'),
    documentId: body.documentId ? String(body.documentId) : 'doc-employment-kasun',
    documentTitle: body.documentTitle ? String(body.documentTitle) : 'Employment Agreement',
    status: 'PENDING',
    signers,
    deadline: body.deadline ? String(body.deadline) : '2026-03-31',
    createdAt: new Date().toISOString(),
  }
  world.signatureRequests.set(id, req)
  world.signatureAuditLogs.set(id, [
    {
      id: `log-${Date.now().toString(36)}`,
      requestId: id,
      eventType: 'CREATED',
      actorName: 'Nimali Wickramasinghe',
      ipAddress: '192.168.1.10',
      documentHashSha256: createHash('sha256').update(id).digest('hex'),
      createdAt: new Date().toISOString(),
    },
  ])
  return { status: 201, body: { signatureRequest: req } }
}

function signDocument(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const id = pathParam(request, 'id')
  const req = world.signatureRequests.get(id)
  if (!req) throw notFound(`Signature request ${id} not found`)
  const body = objectBody(request)
  const signerEmail = body.signerEmail ? String(body.signerEmail) : req.signers[0]?.signerEmail
  const signatureType = body.signatureType ? String(body.signatureType) : 'DRAWN'

  let allDone = true
  for (const s of req.signers) {
    if (s.signerEmail === signerEmail) {
      s.status = 'SIGNED'
      s.signedAt = new Date().toISOString()
      s.signatureType = signatureType
    }
    if (s.status !== 'SIGNED') allDone = false
  }
  if (allDone) req.status = 'COMPLETED'
  world.signatureRequests.set(id, req)

  const auditEntry = {
    id: `log-${Date.now().toString(36)}`,
    requestId: id,
    eventType: 'SIGNED',
    actorName: signerEmail,
    ipAddress: '192.168.1.55',
    documentHashSha256: createHash('sha256').update(`${id}-${Date.now()}`).digest('hex'),
    details: { method: signatureType, timestampUtc: new Date().toISOString() },
    createdAt: new Date().toISOString(),
  }
  const trail = world.signatureAuditLogs.get(id) ?? []
  trail.push(auditEntry)
  world.signatureAuditLogs.set(id, trail)

  return { status: 200, body: { signatureRequest: req, auditTrail: trail } }
}

/* -------------------------------------------------------------------------- */
/* Timesheets & Project Billing (V25)                                         */
/* -------------------------------------------------------------------------- */

function listTimesheetClients(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  return { status: 200, body: { clients: Array.from(world.timesheetClients.values()) } }
}

function listTimesheetProjects(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const clientId = request.query.get('clientId')
  let list = Array.from(world.timesheetProjects.values())
  if (clientId) list = list.filter((p: any) => p.clientId === clientId)
  return { status: 200, body: { projects: list } }
}

function createTimesheetProject(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const body = objectBody(request)
  const clientId = reqStr(body, 'clientId')
  const client = world.timesheetClients.get(clientId)
  const id = `proj-${Date.now().toString(36)}`
  const project = {
    id,
    clientId,
    clientName: client?.name ?? 'Client',
    projectCode: reqStr(body, 'projectCode'),
    name: reqStr(body, 'name'),
    description: body.description ? String(body.description) : '',
    startDate: body.startDate ? String(body.startDate) : new Date().toISOString().split('T')[0],
    endDate: body.endDate ? String(body.endDate) : undefined,
    budgetAmount: body.budgetAmount ? Number(body.budgetAmount) : undefined,
    budgetHours: body.budgetHours ? Number(body.budgetHours) : undefined,
    isBillable: Boolean(body.isBillable),
    status: 'ACTIVE',
  }
  world.timesheetProjects.set(id, project)
  return { status: 201, body: { project } }
}

function listTimesheetActivities(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const projectId = request.query.get('projectId')
  let list = Array.from(world.timesheetActivities.values())
  if (projectId) list = list.filter((a: any) => a.projectId === projectId)
  return { status: 200, body: { activities: list } }
}

function listMyTimesheets(world: World, request: DemoRequest): DemoReply {
  const auth = authenticate(world, request)
  const empId = auth.caller.employeeId
  let list = Array.from(world.timesheets.values())
  if (empId) list = list.filter((t: any) => t.employeeId === empId)
  return { status: 200, body: { timesheets: list } }
}

function listTimesheets(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const status = request.query.get('status')
  const employeeId = request.query.get('employeeId')
  let list = Array.from(world.timesheets.values())
  if (status) list = list.filter((t: any) => t.status === status)
  if (employeeId) list = list.filter((t: any) => t.employeeId === employeeId)
  return { status: 200, body: { timesheets: list } }
}

function getTimesheet(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const id = pathParam(request, 'id')
  const ts = world.timesheets.get(id)
  if (!ts) throw notFound(`Timesheet ${id} not found`)
  const entries = world.timesheetEntries.get(id) ?? []
  return { status: 200, body: { timesheet: ts, entries } }
}

function saveTimesheet(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const id = pathParam(request, 'id')
  const body = objectBody(request)
  let ts = world.timesheets.get(id)
  if (!ts) {
    ts = {
      id,
      employeeId: body.employeeId ? String(body.employeeId) : 'de300000-0001-4000-8000-000000000002',
      employeeName: 'Kasun Fernando',
      employeeCode: 'E002',
      periodStart: body.periodStart ? String(body.periodStart) : '2026-03-09',
      periodEnd: body.periodEnd ? String(body.periodEnd) : '2026-03-15',
      status: 'DRAFT',
      totalHours: 0,
      billableHours: 0,
    }
  }

  const entries = Array.isArray(body.entries) ? body.entries : []
  let totalHours = 0
  let billableHours = 0
  entries.forEach((e: any) => {
    totalHours += Number(e.hours || 0)
    if (e.isBillable) billableHours += Number(e.hours || 0)
  })

  ts.totalHours = totalHours
  ts.billableHours = billableHours
  ts.updatedAt = new Date().toISOString()
  world.timesheets.set(id, ts)
  world.timesheetEntries.set(id, entries)

  return { status: 200, body: { timesheet: ts, entries } }
}

function submitTimesheet(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const id = pathParam(request, 'id')
  const ts = world.timesheets.get(id)
  if (!ts) throw notFound(`Timesheet ${id} not found`)
  ts.status = 'SUBMITTED'
  ts.submittedAt = new Date().toISOString()
  world.timesheets.set(id, ts)
  return { status: 200, body: { timesheet: ts } }
}

function approveTimesheet(world: World, request: DemoRequest): DemoReply {
  const auth = authenticate(world, request)
  const id = pathParam(request, 'id')
  const ts = world.timesheets.get(id)
  if (!ts) throw notFound(`Timesheet ${id} not found`)
  ts.status = 'APPROVED'
  ts.approvedAt = new Date().toISOString()
  ts.approverName = auth.caller.account.username || 'Manager'
  world.timesheets.set(id, ts)
  return { status: 200, body: { timesheet: ts } }
}

function rejectTimesheet(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const id = pathParam(request, 'id')
  const ts = world.timesheets.get(id)
  if (!ts) throw notFound(`Timesheet ${id} not found`)
  const body = objectBody(request)
  ts.status = 'REJECTED'
  ts.rejectionReason = body.rejectionReason ? String(body.rejectionReason) : 'Incomplete task logs'
  world.timesheets.set(id, ts)
  return { status: 200, body: { timesheet: ts } }
}

function copyPreviousTimesheet(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const body = objectBody(request)
  const sourceId = reqStr(body, 'sourceTimesheetId')
  const targetPeriodStart = reqStr(body, 'targetPeriodStart', '2026-03-09')
  const targetPeriodEnd = reqStr(body, 'targetPeriodEnd', '2026-03-15')
  const sourceTs = world.timesheets.get(sourceId)
  if (!sourceTs) throw notFound(`Source timesheet ${sourceId} not found`)
  const sourceEntries = world.timesheetEntries.get(sourceId) ?? []

  const newId = `ts-${targetPeriodStart}-${sourceTs.employeeCode?.toLowerCase() || 'emp'}`
  const newTs = {
    ...sourceTs,
    id: newId,
    periodStart: targetPeriodStart,
    periodEnd: targetPeriodEnd,
    status: 'DRAFT',
    submittedAt: undefined,
    approvedAt: undefined,
    rejectionReason: undefined,
  }

  const sourceStartDate = new Date(sourceTs.periodStart || targetPeriodStart).getTime()
  const targetStartDate = new Date(targetPeriodStart).getTime()
  const diffTime = targetStartDate - sourceStartDate

  const newEntries = sourceEntries.map((e: any, idx: number) => {
    const entryDate = new Date(e.workDate).getTime()
    const newEntryDate = new Date(entryDate + diffTime).toISOString().split('T')[0]
    return {
      ...e,
      id: `tse-${newId}-${idx + 1}`,
      timesheetId: newId,
      workDate: newEntryDate,
    }
  })

  world.timesheets.set(newId, newTs)
  world.timesheetEntries.set(newId, newEntries)

  return { status: 201, body: { timesheet: newTs, entries: newEntries } }
}

function getTimesheetReconciliation(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const periodStart = request.query.get('periodStart') || '2026-03-09'
  const periodEnd = request.query.get('periodEnd') || '2026-03-15'

  const biometricByEmpDate = new Map<string, number>()
  for (const [empId, days] of world.dailyAttendance.entries()) {
    for (const d of days) {
      if (d.workDate >= periodStart && d.workDate <= periodEnd) {
        const key = `${empId}_${d.workDate}`
        biometricByEmpDate.set(key, (d.netWorkedMinutes || 0) / 60)
      }
    }
  }

  const timesheetByEmpDate = new Map<string, number>()
  for (const [tsId, entries] of world.timesheetEntries.entries()) {
    const ts = world.timesheets.get(tsId)
    if (!ts) continue
    for (const e of entries) {
      if (e.workDate >= periodStart && e.workDate <= periodEnd) {
        const key = `${ts.employeeId}_${e.workDate}`
        const curr = timesheetByEmpDate.get(key) || 0
        timesheetByEmpDate.set(key, curr + (e.hours || 0))
      }
    }
  }

  const items: any[] = []
  const allKeys = new Set([...biometricByEmpDate.keys(), ...timesheetByEmpDate.keys()])
  for (const key of allKeys) {
    const [empId, workDate] = key.split('_')
    if (!empId || !workDate) continue
    const emp = world.employees.get(empId)
    const empName = emp?.displayName ?? emp?.firstName ?? 'Employee'
    const bioHours = Math.round((biometricByEmpDate.get(key) || 0) * 10) / 10
    const tsHours = Math.round((timesheetByEmpDate.get(key) || 0) * 10) / 10
    const variance = Math.round((tsHours - bioHours) * 10) / 10

    let status = 'MATCH'
    if (bioHours > 0 && tsHours === 0) status = 'MISSING_TIMESHEET'
    else if (variance > 0.5) status = 'OVER_REPORTED'
    else if (variance < -0.5) status = 'UNDER_REPORTED'

    items.push({
      employeeId: empId,
      employeeName: empName,
      workDate,
      biometricHours: bioHours,
      timesheetHours: tsHours,
      varianceHours: variance,
      status,
    })
  }

  return {
    status: 200,
    body: {
      periodStart,
      periodEnd,
      totalBiometricHours: items.reduce((acc, i) => acc + i.biometricHours, 0),
      totalTimesheetHours: items.reduce((acc, i) => acc + i.timesheetHours, 0),
      reconciliationItems: items.sort((a, b) => b.workDate.localeCompare(a.workDate)),
    },
  }
}

/* -------------------------------------------------------------------------- */
/* Performance Management, OKRs & 360 Appraisals Handlers (V20)               */
/* -------------------------------------------------------------------------- */

function listPerformanceGoals(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const employeeId = request.query.get('employeeId')
  const cycleId = request.query.get('cycleId')
  const status = request.query.get('status')

  let goals = Array.from(world.goals.values())
  if (employeeId) goals = goals.filter((g) => g.employeeId === employeeId)
  if (cycleId) goals = goals.filter((g) => g.cycleId === cycleId)
  if (status && status !== 'ALL') goals = goals.filter((g) => g.status === status)

  const goalsWithCheckIns = goals.map((g) => ({
    ...g,
    checkIns: world.goalCheckIns.get(g.id) || [],
  }))

  return {
    status: 200,
    body: {
      goals: goalsWithCheckIns,
      totalCount: goalsWithCheckIns.length,
      completedCount: goalsWithCheckIns.filter((g) => g.status === 'COMPLETED').length,
    },
  }
}

function createPerformanceGoal(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  const body = objectBody(request)
  const id = `goal-${Date.now()}`
  const emp = world.employees.get(body.employeeId as string) || Array.from(world.employees.values())[0]
  const cycle = world.goalCycles.get(body.cycleId as string) || Array.from(world.goalCycles.values())[0]

  const newGoal = {
    id,
    employeeId: emp?.id || caller.employeeId,
    employeeName: emp?.displayName ?? emp?.firstName ?? 'Employee',
    cycleId: cycle?.id || 'cyc-2026',
    cycleName: cycle?.name || '2026 Annual Performance Cycle',
    title: (body.title as string) || 'New Performance Goal',
    description: (body.description as string) || '',
    category: (body.category as string) || 'INDIVIDUAL',
    weight: Number(body.weight) || 25,
    targetValue: Number(body.targetValue) || 100,
    currentValue: 0,
    unit: (body.unit as string) || '%',
    startDate: (body.startDate as string) || new Date().toISOString().split('T')[0],
    dueDate: (body.dueDate as string) || '2026-12-31',
    status: 'NOT_STARTED',
    progressPercentage: 0,
  }

  world.goals.set(id, newGoal)
  return { status: 201, body: newGoal }
}

function recordGoalCheckIn(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  const goalId = pathParam(request, 'id')
  const goal = world.goals.get(goalId)
  if (!goal) throw notFound('Goal not found')

  const body = objectBody(request)
  const newValue = Number(body.newValue) || 0
  const progressPct = goal.targetValue > 0 ? Math.min(100, Math.round((newValue / goal.targetValue) * 100)) : 100

  const emp = Array.from(world.employees.values()).find((e) => e.id === caller.employeeId)
  const checkIn = {
    id: `chk-${Date.now()}`,
    goalId,
    previousValue: goal.currentValue,
    newValue,
    progressPercentage: progressPct,
    note: (body.note as string) || '',
    checkedInBy: caller.employeeId,
    checkedInByName: emp?.displayName ?? caller.account.username,
    createdAt: new Date().toISOString(),
  }

  const existingCheckIns = world.goalCheckIns.get(goalId) || []
  world.goalCheckIns.set(goalId, [checkIn, ...existingCheckIns])

  goal.currentValue = newValue
  goal.progressPercentage = progressPct
  if (progressPct >= 100) goal.status = 'COMPLETED'
  else if (progressPct > 0) goal.status = 'ON_TRACK'
  world.goals.set(goalId, goal)

  return { status: 200, body: { ...goal, checkIns: world.goalCheckIns.get(goalId) } }
}

function getCompetencyFramework(world: World): DemoReply {
  return {
    status: 200,
    body: {
      groups: Array.from(world.competencyGroups.values()),
      competencies: Array.from(world.competencies.values()),
    },
  }
}

function listMyAppraisals(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const appraisals = Array.from(world.appraisals.values())
  return { status: 200, body: { appraisals } }
}

function listTeamAppraisals(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const appraisals = Array.from(world.appraisals.values())
  return { status: 200, body: { appraisals } }
}

function getAppraisalDetails(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const id = pathParam(request, 'id')
  const appraisal = world.appraisals.get(id)
  if (!appraisal) throw notFound('Appraisal not found')
  return { status: 200, body: { appraisal, ...appraisal } }
}

function submitSelfReview(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const id = pathParam(request, 'id')
  const appraisal = world.appraisals.get(id)
  if (!appraisal) throw notFound('Appraisal not found')

  const body = objectBody(request)
  appraisal.status = 'SELF_REVIEW_SUBMITTED'
  appraisal.selfOverallComments = (body.overallComments as string) || appraisal.selfOverallComments
  world.appraisals.set(id, appraisal)
  return { status: 200, body: { appraisal, ...appraisal } }
}

function submitManagerReview(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const id = pathParam(request, 'id')
  const appraisal = world.appraisals.get(id)
  if (!appraisal) throw notFound('Appraisal not found')

  const body = objectBody(request)
  appraisal.status = 'MANAGER_REVIEW_SUBMITTED'
  appraisal.managerOverallComments = (body.overallComments as string) || appraisal.managerOverallComments
  appraisal.finalScore = 4.4
  appraisal.finalRating = 'Exceeds Expectations'
  world.appraisals.set(id, appraisal)
  return { status: 200, body: { appraisal, ...appraisal } }
}

function acknowledgeAppraisal(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const id = pathParam(request, 'id')
  const appraisal = world.appraisals.get(id)
  if (!appraisal) throw notFound('Appraisal not found')

  appraisal.status = 'ACKNOWLEDGED'
  appraisal.employeeAcknowledgedAt = new Date().toISOString()
  world.appraisals.set(id, appraisal)
  return { status: 200, body: { appraisal, ...appraisal } }
}

function listContinuousFeedback(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const items = Array.from(world.continuousFeedback.values()).sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  return { status: 200, body: { items } }
}

function sendContinuousFeedback(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  const body = objectBody(request)
  const id = `fb-${Date.now()}`
  const recipient = world.employees.get(body.recipientEmployeeId as string) || Array.from(world.employees.values())[1]

  const item = {
    id,
    senderEmployeeId: caller.employeeId,
    senderEmployeeName: caller.account.username,
    recipientEmployeeId: recipient?.id || 'emp-02',
    recipientEmployeeName: recipient?.displayName ?? recipient?.firstName ?? 'Colleague',
    feedbackType: (body.feedbackType as any) || 'PRAISE',
    title: (body.title as string) || 'Recognition',
    content: (body.content as string) || 'Great contribution!',
    isPrivate: Boolean(body.isPrivate),
    sharedWithManager: Boolean(body.sharedWithManager ?? true),
    createdAt: new Date().toISOString(),
  }

  world.continuousFeedback.set(id, item)
  return { status: 201, body: item }
}

/* -------------------------------------------------------------------------- */
/* 360 Multi-Rater Matrix, Cycles & 9-Box Grid Handlers (Option 5)             */
/* -------------------------------------------------------------------------- */

function listAppraisalCycles(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const cycles = Array.from(world.evaluationCycles.values()).map((c) => {
    const apps = Array.from(world.appraisals.values()).filter((a) => a.cycleId === c.id)
    const completed = apps.filter((a) => a.status === 'CLOSED' || a.status === 'ACKNOWLEDGED').length
    return {
      id: c.id,
      code: c.code,
      name: c.name,
      status: c.status,
      startDate: c.startDate,
      endDate: c.endDate,
      selfReviewDeadline: c.selfReviewDeadline,
      peerReviewDeadline: c.peerReviewDeadline || c.selfReviewDeadline,
      managerReviewDeadline: c.managerReviewDeadline,
      calibrationDate: c.calibrationDeadline,
      totalEligibleEmployees: world.employees.size,
      completedAppraisals: completed,
      inProgressAppraisals: Math.max(0, world.employees.size - completed),
    }
  })
  return { status: 200, body: { cycles } }
}

function createAppraisalCycle(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'performance.manage')
  const body = objectBody(request)
  const id = `eval-${Date.now()}`
  const cycle = {
    id,
    code: (body.code as string) || `EVAL-${new Date().getFullYear()}-Q${Math.floor(new Date().getMonth() / 3) + 1}`,
    name: (body.name as string) || 'New Appraisal Cycle',
    status: 'DRAFT',
    startDate: (body.startDate as string) || new Date().toISOString().slice(0, 10),
    endDate: (body.endDate as string) || '2026-12-31',
    selfReviewDeadline: (body.selfReviewDeadline as string) || '2026-11-15',
    peerReviewDeadline: (body.peerReviewDeadline as string) || '2026-11-20',
    managerReviewDeadline: (body.managerReviewDeadline as string) || '2026-11-25',
    calibrationDeadline: (body.calibrationDate as string) || '2026-11-30',
  }
  world.evaluationCycles.set(id, cycle)
  return { status: 201, body: cycle }
}

function updateAppraisalCycleStatus(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'performance.manage')
  const id = pathParam(request, 'id')
  const cycle = world.evaluationCycles.get(id)
  if (!cycle) throw notFound('Appraisal cycle not found')
  const body = objectBody(request)
  if (body.status) {
    cycle.status = body.status
  }
  world.evaluationCycles.set(id, cycle)
  return { status: 200, body: cycle }
}

function get360ReviewRequests(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const appraisalId = pathParam(request, 'id')
  let requests = Array.from(world.mraReviewRequests.values()).filter((r) => r.appraisalId === appraisalId)

  if (requests.length === 0) {
    const emps = Array.from(world.employees.values())
    const defaultNominations = [
      {
        id: `mra-${appraisalId}-01`,
        appraisalId,
        reviewerEmployeeId: emps[2]?.id || 'emp-03',
        reviewerEmployeeName: emps[2]?.displayName ?? 'Mohamed Rizwan',
        reviewerTitle: 'Lead QA Automation Engineer',
        department: departmentName(emps[2]?.departmentId) || 'Engineering',
        relationship: 'PEER',
        status: 'COMPLETED',
        anonymous: false,
        invitedAt: '2026-03-01T10:00:00Z',
        submittedAt: '2026-03-08T14:30:00Z',
      },
      {
        id: `mra-${appraisalId}-02`,
        appraisalId,
        reviewerEmployeeId: emps[3]?.id || 'emp-04',
        reviewerEmployeeName: emps[3]?.displayName ?? 'Dilani Perera',
        reviewerTitle: 'Senior Payroll Specialist',
        department: departmentName(emps[3]?.departmentId) || 'Finance',
        relationship: 'PEER',
        status: 'COMPLETED',
        anonymous: true,
        invitedAt: '2026-03-01T10:00:00Z',
        submittedAt: '2026-03-07T11:20:00Z',
      },
      {
        id: `mra-${appraisalId}-03`,
        appraisalId,
        reviewerEmployeeId: emps[4]?.id || 'emp-05',
        reviewerEmployeeName: emps[4]?.displayName ?? 'Saman Perera',
        reviewerTitle: 'Senior Assembly Specialist',
        department: departmentName(emps[4]?.departmentId) || 'Operations',
        relationship: 'SUBORDINATE',
        status: 'COMPLETED',
        anonymous: true,
        invitedAt: '2026-03-02T09:15:00Z',
        submittedAt: '2026-03-09T16:00:00Z',
      },
      {
        id: `mra-${appraisalId}-04`,
        appraisalId,
        reviewerEmployeeId: emps[1]?.id || 'emp-02',
        reviewerEmployeeName: emps[1]?.displayName ?? 'Priya Balasubramaniam',
        reviewerTitle: 'Lead Talent Partner',
        department: departmentName(emps[1]?.departmentId) || 'People & Culture',
        relationship: 'CROSS_FUNCTIONAL',
        status: 'PENDING',
        anonymous: false,
        invitedAt: '2026-03-03T11:00:00Z',
      },
    ]
    for (const req of defaultNominations) {
      world.mraReviewRequests.set(req.id, req)
    }
    requests = defaultNominations
  }

  return { status: 200, body: { requests } }
}

function nominate360Reviewer(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const appraisalId = pathParam(request, 'id')
  const body = objectBody(request)
  const reviewerEmp = world.employees.get(body.reviewerEmployeeId as string)

  const id = `mra-${Date.now()}`
  const nomination = {
    id,
    appraisalId,
    reviewerEmployeeId: body.reviewerEmployeeId as string,
    reviewerEmployeeName: reviewerEmp?.displayName ?? reviewerEmp?.firstName ?? 'Nominated Reviewer',
    reviewerTitle: (reviewerEmp?.customFields?.title as string) || 'Senior Colleague',
    department: departmentName(reviewerEmp?.departmentId) || 'Cross-Functional',
    relationship: body.relationship || 'PEER',
    status: 'PENDING',
    anonymous: Boolean(body.anonymous),
    invitedAt: new Date().toISOString(),
  }

  world.mraReviewRequests.set(id, nomination)
  return { status: 201, body: nomination }
}

function get360Matrix(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const appraisalId = pathParam(request, 'id')
  const appraisal = world.appraisals.get(appraisalId)
  const nominations = Array.from(world.mraReviewRequests.values()).filter((r) => r.appraisalId === appraisalId)

  const competencies = [
    {
      competencyId: 'comp-01',
      code: 'ARCH',
      name: 'Modular System Architecture',
      groupName: 'Technical Mastery',
      targetLevel: 4.0,
      selfScore: 4.8,
      managerScore: 4.5,
      peerScore: 4.6,
      subordinateScore: 4.4,
      crossFunctionalScore: 4.5,
      overallScore: 4.56,
      gap: 0.56,
    },
    {
      competencyId: 'comp-02',
      code: 'OWNER',
      name: 'Extreme Delivery Ownership',
      groupName: 'Core Culture',
      targetLevel: 4.0,
      selfScore: 5.0,
      managerScore: 4.8,
      peerScore: 4.7,
      subordinateScore: 4.6,
      crossFunctionalScore: 4.8,
      overallScore: 4.78,
      gap: 0.78,
    },
    {
      competencyId: 'comp-03',
      code: 'SPEED',
      name: 'Execution Velocity & Rigor',
      groupName: 'Delivery',
      targetLevel: 3.5,
      selfScore: 4.2,
      managerScore: 4.0,
      peerScore: 4.3,
      subordinateScore: 4.1,
      crossFunctionalScore: 4.2,
      overallScore: 4.16,
      gap: 0.66,
    },
    {
      competencyId: 'comp-04',
      code: 'LEAD',
      name: 'Strategic Mentorship & Coaching',
      groupName: 'People Leadership',
      targetLevel: 4.0,
      selfScore: 4.0,
      managerScore: 3.8,
      peerScore: 4.2,
      subordinateScore: 4.5,
      crossFunctionalScore: 4.0,
      overallScore: 4.1,
      gap: 0.1,
    },
    {
      competencyId: 'comp-05',
      code: 'COMM',
      name: 'Cross-Team Communication',
      groupName: 'Collaboration',
      targetLevel: 4.0,
      selfScore: 4.0,
      managerScore: 3.8,
      peerScore: 3.9,
      subordinateScore: 4.0,
      crossFunctionalScore: 3.7,
      overallScore: 3.88,
      gap: -0.12,
    },
  ]

  const strengths = [
    'Deep technical ownership and architectural rigor recognized by all peer raters',
    'Exceptionally high trust and psychological safety reported by direct reports',
    'Proactive production incident resolution and root cause prevention',
  ]

  const developmentAreas = [
    'Active participation in executive product discovery sessions',
    'Proactive delegation of routine operational runbooks to junior engineers',
  ]

  return {
    status: 200,
    body: {
      appraisalId,
      employeeName: appraisal?.employeeName || 'Kasun Fernando',
      department: appraisal?.departmentName || 'Engineering',
      cycleName: appraisal?.cycleName || '2026 H1 Appraisal Cycle',
      nominations,
      competencies,
      strengths,
      developmentAreas,
    },
  }
}

function submit360Evaluation(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const requestId = pathParam(request, 'id')
  const req = world.mraReviewRequests.get(requestId)
  if (!req) throw notFound('Review request not found')

  req.status = 'COMPLETED'
  req.submittedAt = new Date().toISOString()
  world.mraReviewRequests.set(requestId, req)

  return { status: 200, body: { success: true } }
}

function getRatingDistribution(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const cycleId = pathParam(request, 'id')
  const cycle = world.evaluationCycles.get(cycleId)

  const employees = Array.from(world.employees.values())
  const seededScores: number[] = [4.8, 4.4, 4.3, 3.9, 3.8, 3.6, 3.5, 3.4, 3.2, 3.1, 2.7, 2.1]

  const totalCalibrated = employees.length
  let totalScore = 0

  const counts = [0, 0, 0, 0, 0]
  employees.forEach((emp, i) => {
    const override = world.nineBoxOverrides.get(emp.id)
    const score = override?.performanceScore ?? seededScores[i % seededScores.length] ?? 3.5
    totalScore += score

    const idx = score < 2.5 ? 0 : score < 3.0 ? 1 : score < 3.9 ? 2 : score < 4.6 ? 3 : 4
    counts[idx] = (counts[idx] ?? 0) + 1
  })

  const averageScore = Math.round((totalScore / Math.max(1, totalCalibrated)) * 100) / 100

  const bands = [
    {
      bandIndex: 1,
      label: '1 - Unsatisfactory (PIP)',
      scoreRange: '1.0 - 2.4',
      targetPercent: 5,
      actualCount: counts[0] ?? 0,
      actualPercent: Math.round(((counts[0] ?? 0) / totalCalibrated) * 100),
      variancePercent: Math.round((((counts[0] ?? 0) / totalCalibrated) * 100 - 5) * 10) / 10,
      tone: 'danger' as const,
    },
    {
      bandIndex: 2,
      label: '2 - Developing / Needs Growth',
      scoreRange: '2.5 - 2.9',
      targetPercent: 10,
      actualCount: counts[1] ?? 0,
      actualPercent: Math.round(((counts[1] ?? 0) / totalCalibrated) * 100),
      variancePercent: Math.round((((counts[1] ?? 0) / totalCalibrated) * 100 - 10) * 10) / 10,
      tone: 'warning' as const,
    },
    {
      bandIndex: 3,
      label: '3 - Meets Expectations (Solid)',
      scoreRange: '3.0 - 3.8',
      targetPercent: 60,
      actualCount: counts[2] ?? 0,
      actualPercent: Math.round(((counts[2] ?? 0) / totalCalibrated) * 100),
      variancePercent: Math.round((((counts[2] ?? 0) / totalCalibrated) * 100 - 60) * 10) / 10,
      tone: 'neutral' as const,
    },
    {
      bandIndex: 4,
      label: '4 - Exceeds Expectations (Star)',
      scoreRange: '3.9 - 4.5',
      targetPercent: 20,
      actualCount: counts[3] ?? 0,
      actualPercent: Math.round(((counts[3] ?? 0) / totalCalibrated) * 100),
      variancePercent: Math.round((((counts[3] ?? 0) / totalCalibrated) * 100 - 20) * 10) / 10,
      tone: 'success' as const,
    },
    {
      bandIndex: 5,
      label: '5 - Role Model / Top 5%',
      scoreRange: '4.6 - 5.0',
      targetPercent: 5,
      actualCount: counts[4] ?? 0,
      actualPercent: Math.round(((counts[4] ?? 0) / totalCalibrated) * 100),
      variancePercent: Math.round((((counts[4] ?? 0) / totalCalibrated) * 100 - 5) * 10) / 10,
      tone: 'success' as const,
    },
  ]

  const calibrationAlerts: string[] = []
  if ((bands[3]?.actualPercent ?? 0) > 25) {
    calibrationAlerts.push(
      `⚠️ Band 4 ('Exceeds Expectations') is at ${bands[3]?.actualPercent}%, exceeding standard target of 20% by +${bands[3]?.variancePercent}%. Committee calibration review recommended.`,
    )
  }
  if ((bands[0]?.actualPercent ?? 0) === 0) {
    calibrationAlerts.push(
      `ℹ️ Band 1 has 0 evaluations. Verify whether underperforming employees require formal Performance Improvement Plans (PIP).`,
    )
  }

  return {
    status: 200,
    body: {
      cycleId,
      cycleName: cycle?.name || 'FY2025/2026 Annual Performance Cycle',
      totalCalibrated,
      averageScore,
      bands,
      calibrationAlerts,
    },
  }
}

function get9BoxMatrix(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const cycleId = pathParam(request, 'id')
  const cycle = world.evaluationCycles.get(cycleId)
  const department = request.query.get('department')

  const employees = Array.from(world.employees.values())
  let filtered = employees
  if (department && department !== 'ALL') {
    filtered = employees.filter((e) => departmentName(e.departmentId) === department)
  }

  const seededScores = [4.8, 4.4, 4.3, 3.9, 3.8, 3.6, 3.5, 3.4, 3.2, 3.1, 2.7, 2.1]
  const seededPotentials: Array<'LOW' | 'MEDIUM' | 'HIGH'> = [
    'HIGH',
    'HIGH',
    'HIGH',
    'HIGH',
    'MEDIUM',
    'MEDIUM',
    'MEDIUM',
    'LOW',
    'MEDIUM',
    'LOW',
    'HIGH',
    'LOW',
  ]

  const cellDefinitions = [
    {
      boxKey: 'enigma',
      title: 'Enigma / Rough Diamond',
      performance: 'LOW' as const,
      potential: 'HIGH' as const,
      description: 'High potential but currently lagging in output. Needs targeted mentoring.',
      colorTone: '#fbbf24',
    },
    {
      boxKey: 'high_potential',
      title: 'High Potential / Growth',
      performance: 'MEDIUM' as const,
      potential: 'HIGH' as const,
      description: 'Strong potential and solid performance. High-trajectory succession candidate.',
      colorTone: '#38bdf8',
    },
    {
      boxKey: 'star',
      title: 'Star / Future Executive',
      performance: 'HIGH' as const,
      potential: 'HIGH' as const,
      description: 'Top-tier performer with unmatched leadership bandwidth. Retain and reward.',
      colorTone: '#10b981',
    },
    {
      boxKey: 'dilemma',
      title: 'Dilemma / Inconsistent',
      performance: 'LOW' as const,
      potential: 'MEDIUM' as const,
      description: 'Inconsistent execution despite capability. Review role fit and blockers.',
      colorTone: '#f97316',
    },
    {
      boxKey: 'core_contributor',
      title: 'Core Contributor / Backbone',
      performance: 'MEDIUM' as const,
      potential: 'MEDIUM' as const,
      description: 'Reliable, steady team player delivering dependable business outcomes.',
      colorTone: '#6366f1',
    },
    {
      boxKey: 'high_performer',
      title: 'High Performer / Master',
      performance: 'HIGH' as const,
      potential: 'MEDIUM' as const,
      description: 'Delivers at an elite level in current position. Deep domain mastery.',
      colorTone: '#059669',
    },
    {
      boxKey: 'underperformer',
      title: 'Underperformer / Action Plan',
      performance: 'LOW' as const,
      potential: 'LOW' as const,
      description: 'Low performance and low growth potential. Require immediate structured PIP.',
      colorTone: '#ef4444',
    },
    {
      boxKey: 'effective',
      title: 'Effective / Steady Worker',
      performance: 'MEDIUM' as const,
      potential: 'LOW' as const,
      description: 'Meets expectations in structured roles. Value within steady operational scope.',
      colorTone: '#94a3b8',
    },
    {
      boxKey: 'specialist',
      title: 'Trusted Specialist',
      performance: 'HIGH' as const,
      potential: 'LOW' as const,
      description: 'Exceptional individual contributor. Best utilized as dedicated technical authority.',
      colorTone: '#0284c7',
    },
  ]

  const grid = cellDefinitions.map((cell) => ({
    ...cell,
    employees: [] as any[],
  }))

  filtered.forEach((emp, i) => {
    const override = world.nineBoxOverrides.get(emp.id)
    const score = override?.performanceScore ?? seededScores[i % seededScores.length] ?? 3.5
    const potential = override?.potentialLevel ?? seededPotentials[i % seededPotentials.length] ?? 'MEDIUM'

    const perfLevel: 'LOW' | 'MEDIUM' | 'HIGH' = score >= 3.9 ? 'HIGH' : score >= 3.0 ? 'MEDIUM' : 'LOW'

    let targetBoxKey = 'core_contributor'
    if (potential === 'HIGH' && perfLevel === 'HIGH') targetBoxKey = 'star'
    else if (potential === 'HIGH' && perfLevel === 'MEDIUM') targetBoxKey = 'high_potential'
    else if (potential === 'HIGH' && perfLevel === 'LOW') targetBoxKey = 'enigma'
    else if (potential === 'MEDIUM' && perfLevel === 'HIGH') targetBoxKey = 'high_performer'
    else if (potential === 'MEDIUM' && perfLevel === 'MEDIUM') targetBoxKey = 'core_contributor'
    else if (potential === 'MEDIUM' && perfLevel === 'LOW') targetBoxKey = 'dilemma'
    else if (potential === 'LOW' && perfLevel === 'HIGH') targetBoxKey = 'specialist'
    else if (potential === 'LOW' && perfLevel === 'MEDIUM') targetBoxKey = 'effective'
    else targetBoxKey = 'underperformer'

    const cell = grid.find((c) => c.boxKey === targetBoxKey)
    if (cell) {
      const nameParts = (emp.displayName ?? emp.firstName ?? 'Employee').split(' ')
      const initials = (nameParts[0]?.[0] || 'E') + (nameParts[1]?.[0] || '')
      cell.employees.push({
        employeeId: emp.id,
        employeeCode: emp.employeeCode || `E00${i + 1}`,
        fullName: emp.displayName ?? emp.firstName ?? 'Employee',
        designation: (emp.customFields?.title as string) || 'Senior Professional',
        department: departmentName(emp.departmentId) || 'Operations',
        performanceScore: score,
        potentialLevel: potential,
        currentBoxKey: targetBoxKey,
        avatarInitials: initials.toUpperCase(),
      })
    }
  })

  return {
    status: 200,
    body: {
      cycleId,
      cycleName: cycle?.name || 'FY2025/2026 Performance Cycle',
      grid,
      totalEmployees: filtered.length,
    },
  }
}

function calibrate9BoxPosition(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'performance.manage')
  const body = objectBody(request)
  const employeeId = stringField(body, 'employeeId')
  if (!employeeId) throw badRequest('MISSING_FIELD', 'employeeId is required')

  const existing = world.nineBoxOverrides.get(employeeId) || {}
  const updated = {
    ...existing,
    potentialLevel: (body.potentialLevel as 'LOW' | 'MEDIUM' | 'HIGH') || existing.potentialLevel || 'HIGH',
    performanceScore: body.performanceScore !== undefined ? Number(body.performanceScore) : existing.performanceScore,
  }

  world.nineBoxOverrides.set(employeeId, updated)
  return { status: 200, body: { success: true } }
}

/* -------------------------------------------------------------------------- */
/* Onboarding & Offboarding Handlers (V22)                                    */
/* -------------------------------------------------------------------------- */

function listOnboardingProfiles(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'onboarding.task.view')
  const items = Array.from(world.onboardingProfiles.values())
  return { status: 200, body: { items, totalCount: items.length } }
}

function listOnboardingInstances(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'onboarding.task.view')
  const status = request.query.get('status')
  let items = Array.from(world.onboardingInstances.values())
  if (status && status !== 'ALL') items = items.filter((i) => i.status === status)
  return { status: 200, body: { items, totalCount: items.length } }
}

function createOnboardingInstance(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'onboarding.task.manage')
  const body = objectBody(request)
  const id = `onb-${Date.now()}`
  const emp = world.employees.get(body.employeeId as string) || Array.from(world.employees.values())[0]
  const profile = world.onboardingProfiles.get(body.profileId as string) || Array.from(world.onboardingProfiles.values())[0]

  const instance = {
    id,
    employeeId: emp?.id || 'emp-01',
    employeeName: emp?.displayName ?? emp?.firstName ?? 'New Hire',
    employeeCode: emp?.employeeCode,
    departmentName: (emp as any)?.departmentName || 'Engineering',
    jobTitle: (emp as any)?.jobTitle || 'Software Engineer',
    profileId: profile?.id || 'prof-eng',
    profileName: profile?.name || 'Software Engineering Onboarding Track',
    joinDate: (body.joinDate as string) || new Date().toISOString().split('T')[0],
    status: 'IN_PROGRESS' as const,
    progressPct: 0.0,
    totalTasks: 3,
    completedTasks: 0,
  }

  world.onboardingInstances.set(id, instance)
  const defaultTasks = [
    {
      id: `tsk-${id}-1`,
      instanceId: id,
      title: 'Sign Digital Employment Contract & Non-Disclosure NDA',
      ownerRole: 'NEW_HIRE',
      dueDate: instance.joinDate,
      status: 'PENDING',
    },
    {
      id: `tsk-${id}-2`,
      instanceId: id,
      title: 'Provision Laptop & Security Token (YubiKey)',
      ownerRole: 'IT_OPS',
      dueDate: instance.joinDate,
      status: 'PENDING',
    },
    {
      id: `tsk-${id}-3`,
      instanceId: id,
      title: 'Welcome Coffee & Codebase Overview Pairing',
      ownerRole: 'BUDDY',
      dueDate: instance.joinDate,
      status: 'PENDING',
    },
  ]
  world.onboardingTasks.set(id, defaultTasks)

  return { status: 201, body: { ...instance, tasks: defaultTasks } }
}

function getOnboardingInstance(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'onboarding.task.view')
  const id = pathParam(request, 'id')
  const instance = world.onboardingInstances.get(id)
  if (!instance) throw notFound('Onboarding instance not found')
  const tasks = world.onboardingTasks.get(id) || []
  return { status: 200, body: { ...instance, tasks } }
}

function completeOnboardingTask(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'onboarding.task.manage')
  const taskId = pathParam(request, 'id')

  for (const [instId, tasks] of world.onboardingTasks.entries()) {
    const taskIndex = tasks.findIndex((t: any) => t.id === taskId)
    if (taskIndex !== -1) {
      const task = tasks[taskIndex]
      task.status = 'COMPLETED'
      task.completedAt = new Date().toISOString()
      tasks[taskIndex] = task
      world.onboardingTasks.set(instId, tasks)

      const inst = world.onboardingInstances.get(instId)
      if (inst) {
        inst.completedTasks = tasks.filter((t: any) => t.status === 'COMPLETED').length
        inst.progressPct = Math.round((inst.completedTasks / tasks.length) * 100)
        world.onboardingInstances.set(instId, inst)
      }
      return { status: 200, body: task }
    }
  }

  throw notFound('Task not found')
}

function listExitTypes(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'offboarding.task.view')
  const items = Array.from(world.exitTypes.values())
  return { status: 200, body: { items } }
}

function listExitNotices(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'offboarding.task.view')
  const status = request.query.get('status')
  let items = Array.from(world.exitNotices.values())
  if (status && status !== 'ALL') items = items.filter((n) => n.status === status)
  return { status: 200, body: { items, totalCount: items.length } }
}

function createExitNotice(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  const body = objectBody(request)
  const id = `not-${Date.now()}`
  const emp = Array.from(world.employees.values()).find((e) => e.id === caller.employeeId) || Array.from(world.employees.values())[2]
  const exitType = world.exitTypes.get(body.exitTypeId as string) || Array.from(world.exitTypes.values())[0]

  const notice = {
    id,
    noticeNumber: `EX-${Date.now().toString().slice(-4)}`,
    employeeId: emp?.id || caller.employeeId,
    employeeName: emp?.displayName ?? caller.account.username,
    employeeCode: emp?.employeeCode,
    departmentName: (emp as any)?.departmentName || 'Engineering',
    jobTitle: (emp as any)?.jobTitle || 'Engineer',
    exitTypeId: exitType?.id || 'exit-res',
    exitTypeCode: exitType?.code || 'RESIGNATION',
    exitTypeName: exitType?.name || 'Voluntary Resignation',
    noticeDate: (body.noticeDate as string) || new Date().toISOString().split('T')[0],
    requestedLastWorkingDate: (body.requestedLastWorkingDate as string) || '2026-04-30',
    remarks: (body.remarks as string) || '',
    status: 'SUBMITTED' as const,
  }

  world.exitNotices.set(id, notice)
  return { status: 201, body: notice }
}

function approveExitNotice(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'offboarding.task.manage')
  const id = pathParam(request, 'id')
  const notice = world.exitNotices.get(id)
  if (!notice) throw notFound('Exit notice not found')

  const body = objectBody(request)
  notice.status = 'APPROVED'
  notice.approvedLastWorkingDate = (body.approvedLastWorkingDate as string) || notice.requestedLastWorkingDate
  notice.approvedBy = caller.employeeId
  notice.approvedByName = caller.account.username
  notice.approvedAt = new Date().toISOString()
  world.exitNotices.set(id, notice)
  return { status: 200, body: notice }
}

function getClearanceMatrix(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  requirePermission(caller, 'offboarding.task.view')
  const exitNoticeId = pathParam(request, 'id')
  const notice = world.exitNotices.get(exitNoticeId)
  if (!notice) throw notFound('Exit notice not found')

  const tasks = world.clearanceTasks.get(exitNoticeId) || []
  const cleared = tasks.filter((t: any) => t.status === 'CLEARED').length
  const recoverable = tasks.reduce((sum: number, t: any) => sum + (t.recoverableAmount || 0), 0)

  return {
    status: 200,
    body: {
      exitNoticeId,
      employeeId: notice.employeeId,
      employeeName: notice.employeeName,
      totalTasks: tasks.length,
      clearedTasks: cleared,
      pendingTasks: tasks.length - cleared,
      totalRecoverableAmount: recoverable,
      tasks,
    },
  }
}

function updateClearanceTaskStatus(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const taskId = pathParam(request, 'id')
  const body = objectBody(request)

  for (const [noticeId, tasks] of world.clearanceTasks.entries()) {
    const taskIndex = tasks.findIndex((t: any) => t.id === taskId)
    if (taskIndex !== -1) {
      const task = tasks[taskIndex]
      task.status = (body.status as any) || 'CLEARED'
      if (body.remarks) task.remarks = body.remarks as string
      if (typeof body.recoverableAmount === 'number') task.recoverableAmount = body.recoverableAmount
      if (task.status === 'CLEARED') task.clearedAt = new Date().toISOString()
      tasks[taskIndex] = task
      world.clearanceTasks.set(noticeId, tasks)
      return { status: 200, body: task }
    }
  }

  throw notFound('Clearance task not found')
}

function getExitInterview(world: World, request: DemoRequest): DemoReply {
  authenticate(world, request)
  const exitNoticeId = pathParam(request, 'id')
  const interview = world.exitInterviews.get(exitNoticeId)
  if (!interview) throw notFound('Exit interview not found')
  return { status: 200, body: interview }
}

function submitExitInterview(world: World, request: DemoRequest): DemoReply {
  const { caller } = authenticate(world, request)
  const exitNoticeId = pathParam(request, 'id')
  const notice = world.exitNotices.get(exitNoticeId)
  if (!notice) throw notFound('Exit notice not found')

  const body = objectBody(request)
  const interview = {
    id: `exi-${Date.now()}`,
    exitNoticeId,
    employeeId: notice.employeeId,
    employeeName: notice.employeeName,
    interviewerEmployeeId: caller.employeeId,
    interviewerName: caller.account.username,
    conductedAt: new Date().toISOString(),
    overallExperienceRating: Number(body.overallExperienceRating) || 5,
    managementRating: Number(body.managementRating) || 5,
    cultureRating: Number(body.cultureRating) || 5,
    reasonDetails: (body.reasonDetails as string) || '',
    suggestions: (body.suggestions as string) || '',
    wouldRecommend: Boolean(body.wouldRecommend ?? true),
  }

  world.exitInterviews.set(exitNoticeId, interview)
  return { status: 201, body: interview }
}


