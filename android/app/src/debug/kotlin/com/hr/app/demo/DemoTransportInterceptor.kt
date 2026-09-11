package com.hr.app.demo

import com.hr.client.infrastructure.Serializer
import com.hr.client.model.ApiError
import com.hr.client.model.ApiErrorResponse
import com.hr.client.model.AttendancePunchItem
import com.hr.client.model.AttendancePunchRequest
import com.hr.client.model.AttendancePunchResponse
import com.hr.client.model.NotificationSettings
import com.hr.client.model.ApprovalDecisionRequest
import com.hr.client.model.EmployeeDocumentItem
import com.hr.client.model.RenewDocumentRequest
import com.hr.client.model.RenewDocumentResponse
import com.hr.client.model.ShiftRosterResponse
import com.hr.client.model.ShiftRosterDayItem
import com.hr.client.model.TeamCoverageResponse
import com.hr.client.model.TeamCoverageMemberItem
import com.hr.client.model.ShiftSwapRequest
import com.hr.client.model.ShiftSwapResponse
import com.hr.client.model.AttendanceRegulariseRequest
import com.hr.client.model.AttendanceRegulariseResponse
import com.hr.client.model.LeaveBalanceItem
import com.hr.client.model.LeaveBalancesResponse
import com.hr.client.model.LeaveApplicationItem
import com.hr.client.model.LeaveApplicationsResponse
import com.hr.client.model.LeaveApplicationDayItem
import com.hr.client.model.LeaveEligibilityRequest
import com.hr.client.model.LeaveEligibilityResponse
import com.hr.client.model.LeaveApplicationRequest
import com.hr.client.model.LeaveLedgerEntryItem
import com.hr.client.model.LeaveLedgerResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.hr.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers the app's HTTP calls from fixtures, without a server.
 *
 * ## Why this exists
 *
 * The Spring backend needs PostgreSQL, and there is none on the machine this is developed on — so
 * the app has never been clicked through end to end. Every screen past the sign-in form has been
 * reasoned about rather than seen. This closes that gap: install the debug APK on any handset,
 * type anything into the sign-in form, and the directory, profile and settings screens all render
 * from data that matches the server's own `LocalDemoSeeder`.
 *
 * ## Why it cannot reach production
 *
 * This file lives in `app/src/debug/`. AGP compiles a build type's source set **only** into that
 * build type, so the class is not merely disabled in the release APK — it is not in it. There is
 * no flag, no `BuildConfig` check and no `if (demo)` anywhere in the application code, because all
 * three of those are things a person can get wrong. The check a reviewer needs to make is
 * "is the file under `src/debug`?", and the answer is verifiable by unzipping the APK.
 *
 * The one concession in shared code is a Dagger multibound `Set<Interceptor>` that
 * `AppModule`/`AuthModule` add to their clients. In the release variant nothing contributes to it,
 * so it is an empty set and the loop over it does nothing. See `AppModule.NetworkInterceptorsModule`.
 *
 * ## Why an interceptor rather than a fake API
 *
 * The alternative — binding a hand-written `MeApi`, `DirectoryApi` and so on — would skip Retrofit,
 * the kotlinx-serialization converter, `AuthInterceptor` and the generated models' decoding. Those
 * are precisely the layers with nothing else exercising them. Short-circuiting at the OkHttp layer
 * means the request really is built by Retrofit from the generated interface, the bearer token
 * really is attached by `AuthInterceptor`, and the response really is decoded by the generated
 * model — a missing required field fails here exactly as it would against the real server.
 *
 * ## What it does not do
 *
 * It is not a second implementation of the backend. Requests it does not recognise are passed
 * straight through to the network, where — with no server — they fail as they would have anyway.
 */
@Singleton
internal class DemoTransportInterceptor
    @Inject
    constructor() : Interceptor {
        /**
         * The one piece of mutable state, so the notification settings screen can save and reload.
         *
         * A screen whose save button does nothing observable is worse than one that is greyed out:
         * it teaches whoever is demonstrating the app that the write path works when nothing has
         * been proved. Held per process, not per user — a demo has one person using it.
         */
        private val notificationSettings =
            AtomicReference(DemoResponses.defaultNotificationSettings())

        private val demoPunches = AtomicReference<List<AttendancePunchItem>>(emptyList())
        private val decidedApprovalIds = AtomicReference<Set<String>>(emptySet())
        private val demoDocuments = AtomicReference<List<EmployeeDocumentItem>>(DemoResponses.defaultEmployeeDocuments())
        private val demoBalances = AtomicReference(DemoResponses.defaultLeaveBalances())
        private val demoApplications = AtomicReference(DemoResponses.defaultLeaveApplications())
        private val demoLedger = AtomicReference(DemoResponses.defaultLeaveLedger())

        /** Makes each issued token distinct, so a rotation is visibly a rotation. */
        private val tokenNonce = AtomicLong(0)

        override fun intercept(chain: Interceptor.Chain): Response {
            // Built with `-PdemoTransport=false` when the debug build should talk to a real
            // server. Without this the debug variant — the only installable one — could never
            // reach a backend, which stopped being hypothetical the moment one existed to reach.
            if (!BuildConfig.DEMO_TRANSPORT) return chain.proceed(chain.request())

            val request = chain.request()
            val path = request.url.encodedPath
            val route = "${request.method} $path"

            val response =
                when {
                    route == "POST /v1/auth/resolve-tenant" -> resolveTenant(request)
                    route == "POST /v1/auth/token" -> issueToken(request)
                    route == "POST /v1/auth/token/refresh" -> refreshToken(request)
                    route == "POST /v1/auth/token/biometric" -> biometricToken(request)
                    route == "POST /v1/auth/devices" -> registerDevice(request)

                    route == "GET /v1/me" -> me(request)
                    route == "GET /v1/me/notification-settings" -> readSettings(request)
                    route == "PUT /v1/me/notification-settings" -> writeSettings(request)

                    route == "GET /v1/mobile/home" -> mobileHome(request)

                    route == "GET /v1/directory/search" -> directory(request)

                    route == "GET /v1/employees/me" -> ownProfile(request)
                    route == "GET /v1/employees/me/documents" -> getOwnDocuments(request)
                    route == "POST /v1/employees/me/documents/renew" -> renewOwnDocument(request)

                    route == "GET /v1/attendance/me/today" -> todayAttendance(request)
                    route == "POST /v1/attendance/punches" -> ingestPunch(request)
                    route == "GET /v1/attendance/me/roster" -> shiftRoster(request)
                    route == "GET /v1/attendance/team/coverage" -> teamCoverage(request)
                    route == "POST /v1/attendance/roster/swap-request" -> swapRequest(request)
                    route == "POST /v1/attendance/me/regularise" -> regulariseRequest(request)
                    route == "GET /v1/leave/balances" -> leaveBalances(request)
                    route == "GET /v1/leave/applications" -> leaveApplications(request)
                    route == "POST /v1/leave/eligibility" -> leaveEligibility(request)
                    route == "POST /v1/leave/applications" -> submitLeave(request)
                    request.method == "POST" && LEAVE_CANCEL_PATH.matches(path) -> {
                        val id = LEAVE_CANCEL_PATH.matchEntire(path)!!.groupValues[1]
                        cancelLeave(request, id)
                    }
                    route == "GET /v1/leave/ledger" -> leaveLedger(request)

                    route == "GET /v1/approvals/pending" -> pendingApprovals(request)
                    request.method == "POST" && APPROVAL_DECISION_PATH.matches(path) -> {
                        val id = APPROVAL_DECISION_PATH.matchEntire(path)!!.groupValues[1]
                        decideApproval(request, id)
                    }

                    route == "GET /v1/loans/types" -> request.respond(DemoResponses.defaultLoanTypes())
                    route == "GET /v1/loans/me" -> request.respond(DemoResponses.defaultEmployeeLoans())
                    route == "POST /v1/loans/eligibility" -> request.respond(DemoResponses.defaultLoanEligibility(BigDecimal("30000.00"), 6))
                    route == "POST /v1/loans/applications" -> request.respond(DemoResponses.defaultEmployeeLoans().loans.first(), code = 201)
                    request.method == "POST" && LOAN_SETTLE_PATH.matches(path) -> {
                        val id = LOAN_SETTLE_PATH.matchEntire(path)!!.groupValues[1]
                        request.respond(DemoResponses.defaultLoanSettlement(id))
                    }
                    request.method == "GET" && LOAN_DETAIL_PATH.matches(path) -> {
                        val id = LOAN_DETAIL_PATH.matchEntire(path)!!.groupValues[1]
                        request.respond(DemoResponses.defaultLoanDetail(id))
                    }

                    request.method == "GET" ->
                        EMPLOYEE_FORM_PATH.matchEntire(path)?.let { employeeForm(request, it.groupValues[1]) }
                            ?: EMPLOYEE_PATH.matchEntire(path)?.let { employeeProfile(request, it.groupValues[1]) }

                    else -> null

                }

            // Anything not recognised goes to the network. On a device with no server that fails,
            // which is the honest answer — a fixture that invented a 200 for an endpoint nobody
            // wrote would be the most expensive kind of green.
            if (response == null) return chain.proceed(request)

            // A fixture that answers instantly hides every loading state and makes the directory's
            // debounce untestable by hand. This is not a simulation of a network, only enough
            // latency that the spinners the screens draw are actually seen.
            Thread.sleep(LATENCY_MILLIS)
            return response
        }

        // --------------------------------------------------------------------
        // Authentication. These arrive on the *unauthenticated* client, so there is no bearer
        // token to read an identity from — it comes out of the request body instead.
        // --------------------------------------------------------------------

        private fun resolveTenant(request: Request): Response = request.respond(DemoResponses.resolveTenant())

        /**
         * Password sign-in, which always succeeds.
         *
         * The password is not read at all — see [resolveDemoSignIn] for why, and for what the
         * username does select.
         */
        private fun issueToken(request: Request): Response {
            val username = request.jsonBody()?.stringField("username").orEmpty()
            return request.respond(DemoResponses.tokens(resolveDemoSignIn(username), tokenNonce.incrementAndGet()))
        }

        private fun refreshToken(request: Request): Response {
            val presented = request.jsonBody()?.stringField("refreshToken")
            // Refuse anything that is not a refresh token we issued. Accepting an access token here
            // would let a bug in `SessionStore` — presenting the wrong half of the pair — pass
            // unnoticed in the demo and fail only against the real server.
            if (!isDemoToken(presented, DEMO_REFRESH)) return request.unauthorised("TOKEN_INVALID")

            val identity = demoIdentityFromToken(presented) ?: return request.unauthorised("TOKEN_INVALID")
            return request.respond(DemoResponses.tokens(identity, tokenNonce.incrementAndGet()))
        }

        private fun biometricToken(request: Request): Response {
            val sealed = request.jsonBody()?.stringField("sealedRefreshToken")
            val identity = demoIdentityFromToken(sealed) ?: return request.unauthorised("TOKEN_INVALID")
            return request.respond(DemoResponses.tokens(identity, tokenNonce.incrementAndGet()))
        }

        private fun registerDevice(request: Request): Response {
            val body = request.jsonBody()
            val deviceId = body?.stringField("deviceId") ?: return request.badRequest("VALIDATION_FAILED")
            return request.respond(
                DemoResponses.device(
                    deviceId = deviceId,
                    model = body.stringField("model"),
                    osVersion = body.stringField("osVersion"),
                    appVersion = body.stringField("appVersion"),
                ),
                code = 201,
            )
        }

        // --------------------------------------------------------------------
        // Authenticated endpoints. Identity comes from the Authorization header, which is what
        // makes `AuthInterceptor` a participant in the demo rather than something taken on trust.
        // --------------------------------------------------------------------

        private fun me(request: Request): Response {
            val identity = request.identity() ?: return request.unauthorised()
            return request.respond(DemoResponses.me(identity))
        }

        private fun readSettings(request: Request): Response {
            request.identity() ?: return request.unauthorised()
            return request.respond(notificationSettings.get())
        }

        private fun writeSettings(request: Request): Response {
            request.identity() ?: return request.unauthorised()

            val raw = request.bodyText() ?: return request.badRequest("VALIDATION_FAILED")
            val submitted =
                runCatching { DEMO_JSON.decodeFromString<NotificationSettings>(raw) }
                    .getOrElse { return request.badRequest("VALIDATION_FAILED") }

            // A full replace, as the endpoint documents. Echoing back what was stored rather than
            // what was sent is what lets the screen's "saved" state mean something.
            notificationSettings.set(submitted)
            return request.respond(submitted)
        }

        private fun mobileHome(request: Request): Response {
            val identity = request.identity() ?: return request.unauthorised()
            return request.respond(DemoResponses.mobileHome(identity))
        }

        private fun directory(request: Request): Response {
            request.identity() ?: return request.unauthorised()

            val limit = request.url.queryParameter("limit")?.toIntOrNull() ?: DEFAULT_PAGE_SIZE
            return request.respond(DemoResponses.directory(request.url.queryParameter("query"), limit))
        }

        private fun ownProfile(request: Request): Response {
            val identity = request.identity() ?: return request.unauthorised()
            return request.respond(DemoResponses.profile(identity.person, identity, LocalDate.now()))
        }

        private fun getOwnDocuments(request: Request): Response {
            request.identity() ?: return request.unauthorised()
            return request.respond(demoDocuments.get())
        }

        private fun renewOwnDocument(request: Request): Response {
            request.identity() ?: return request.unauthorised()
            val raw = request.bodyText() ?: return request.badRequest("VALIDATION_FAILED")
            val req = runCatching { DEMO_JSON.decodeFromString<RenewDocumentRequest>(raw) }
                .getOrElse { return request.badRequest("VALIDATION_FAILED") }

            val prevId = req.previousDocumentId
            val existing = prevId?.let { id -> demoDocuments.get().find { it.id == id } }

            val masked = if (req.docNumber.length > 4) {
                "****" + req.docNumber.takeLast(4)
            } else {
                req.docNumber
            }

            val today = LocalDate.now()
            val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(today, req.expiryDate).toInt()
            val status = if (req.expiryDate.isBefore(today)) {
                "EXPIRED"
            } else if (daysRemaining <= 30) {
                "EXPIRING"
            } else {
                "VALID"
            }

            val newDocId = UUID.randomUUID()
            val newDoc = EmployeeDocumentItem(
                id = newDocId,
                docType = req.docType,
                docNumberMasked = masked,
                status = status,
                hasAttachment = req.attachmentKey != null || existing?.hasAttachment == true,
                issueDate = req.issueDate ?: existing?.issueDate,
                expiryDate = req.expiryDate,
                issuingCountry = req.issuingCountry ?: existing?.issuingCountry ?: "LK",
                daysRemaining = daysRemaining,
                attachmentKey = req.attachmentKey ?: existing?.attachmentKey,
            )

            demoDocuments.updateAndGet { docs ->
                val list = if (prevId != null) docs.filter { it.id != prevId }.toMutableList() else docs.toMutableList()
                list.add(0, newDoc)
                list
            }

            val resp = RenewDocumentResponse(
                document = newDoc,
                message = "Document renewed successfully",
            )
            return request.respond(resp, code = 200)
        }

        private fun todayAttendance(request: Request): Response {
            val identity = request.identity() ?: return request.unauthorised()
            return request.respond(DemoResponses.todayAttendance(identity.person.id, demoPunches.get()))
        }

        private fun ingestPunch(request: Request): Response {
            request.identity() ?: return request.unauthorised()
            val raw = request.bodyText() ?: return request.badRequest("VALIDATION_FAILED")
            val req = runCatching { DEMO_JSON.decodeFromString<AttendancePunchRequest>(raw) }
                .getOrElse { return request.badRequest("VALIDATION_FAILED") }

            val dist = if (req.geoLat != null && req.geoLng != null) {
                haversine(6.9271, 79.8612, req.geoLat.toDouble(), req.geoLng.toDouble())
            } else 0.0

            val geofenceStatus = if (dist <= 200.0) "INSIDE" else "OUTSIDE"
            val punchId = UUID.randomUUID().toString()
            val punchTypeItem = AttendancePunchItem.PunchType.valueOf(req.punchType.value)
            val geofenceItemStatus = AttendancePunchItem.GeofenceStatus.valueOf(geofenceStatus)
            val isMock = req.isMockLocation == true

            val punchItem = AttendancePunchItem(
                id = punchId,
                punchType = punchTypeItem,
                punchedAt = req.punchedAt,
                source = req.source.value,
                geofenceStatus = geofenceItemStatus,
                isMockLocation = isMock,
                locationName = req.locationName ?: if (geofenceStatus == "INSIDE") "Colombo HQ - Main Entrance" else "Outside Office Premises",
            )

            demoPunches.updateAndGet { it + punchItem }

            val resp = AttendancePunchResponse(
                id = punchId,
                punchType = req.punchType.value,
                punchedAt = req.punchedAt,
                geofenceStatus = geofenceStatus,
                isMockLocation = isMock,
                message = if (isMock) "Punch recorded with MOCK GPS security flag" else "Punch recorded successfully",
            )
            return request.respond(resp, code = 201)
        }

        private fun shiftRoster(request: Request): Response {
            request.identity() ?: return request.unauthorised()
            val today = LocalDate.now()
            val startStr = request.url.queryParameter("startDate")
            val endStr = request.url.queryParameter("endDate")
            val startDate = startStr?.let { LocalDate.parse(it) } ?: today.with(java.time.DayOfWeek.MONDAY)
            val endDate = endStr?.let { LocalDate.parse(it) } ?: startDate.plusDays(6)

            val days = mutableListOf<ShiftRosterDayItem>()
            var curr = startDate
            while (!curr.isAfter(endDate)) {
                val isRest = curr.dayOfWeek.value >= 6
                val dayStatus = when {
                    isRest -> "REST_DAY"
                    curr.isBefore(today) -> "COMPLETED"
                    curr == today -> "ON_DUTY"
                    else -> "SCHEDULED"
                }
                days.add(
                    ShiftRosterDayItem(
                        date = curr,
                        dayOfWeek = curr.dayOfWeek.name.take(3),
                        shiftCode = if (isRest) "REST" else "GEN_0830",
                        shiftName = if (isRest) "Rest Day" else "General Day (08:30 - 17:30)",
                        startTime = if (isRest) "--:--" else "08:30",
                        endTime = if (isRest) "--:--" else "17:30",
                        isRestDay = isRest,
                        isHoliday = false,
                        dayStatus = dayStatus,
                        teamMembersOnDuty = if (isRest) 1 else 4,
                    )
                )
                curr = curr.plusDays(1)
            }
            return request.respond(ShiftRosterResponse(startDate = startDate, endDate = endDate, days = days))
        }

        private fun teamCoverage(request: Request): Response {
            request.identity() ?: return request.unauthorised()
            val dateStr = request.url.queryParameter("date")
            val date = dateStr?.let { LocalDate.parse(it) } ?: LocalDate.now()
            val isWeekend = date.dayOfWeek.value >= 6
            val today = LocalDate.now()
            val members = listOf(
                TeamCoverageMemberItem(
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000010"),
                    employeeCode = "LK010",
                    employeeName = "Kasun Mendis",
                    jobTitle = "Senior Systems Engineer",
                    departmentName = "Engineering & Operations",
                    shiftCode = if (isWeekend) "REST" else "GEN_0830",
                    shiftName = if (isWeekend) "Rest Day" else "General Day (08:30 - 17:30)",
                    startTime = if (isWeekend) "--:--" else "08:30",
                    endTime = if (isWeekend) "--:--" else "17:30",
                    status = if (isWeekend) "REST_DAY" else (if (date == today) "ON_DUTY" else "SCHEDULED"),
                ),
                TeamCoverageMemberItem(
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000011"),
                    employeeCode = "LK011",
                    employeeName = "Amara Perera",
                    jobTitle = "Lead Database Architect",
                    departmentName = "Engineering & Operations",
                    shiftCode = if (isWeekend) "REST" else "MORNING_0700",
                    shiftName = if (isWeekend) "Rest Day" else "Early Morning (07:00 - 15:30)",
                    startTime = if (isWeekend) "--:--" else "07:00",
                    endTime = if (isWeekend) "--:--" else "15:30",
                    status = if (isWeekend) "REST_DAY" else (if (date == today) "ON_DUTY" else "SCHEDULED"),
                ),
                TeamCoverageMemberItem(
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000012"),
                    employeeCode = "LK012",
                    employeeName = "Nuwan Silva",
                    jobTitle = "Operations Specialist",
                    departmentName = "Engineering & Operations",
                    shiftCode = if (isWeekend) "REST" else "EVENING_1400",
                    shiftName = if (isWeekend) "Rest Day" else "Evening Shift (14:00 - 22:30)",
                    startTime = if (isWeekend) "--:--" else "14:00",
                    endTime = if (isWeekend) "--:--" else "22:30",
                    status = if (isWeekend) "REST_DAY" else (if (date == today) "ON_DUTY" else "SCHEDULED"),
                ),
                TeamCoverageMemberItem(
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000013"),
                    employeeCode = "LK013",
                    employeeName = "Dilani Fernando",
                    jobTitle = "Security & Compliance Officer",
                    departmentName = "Engineering & Operations",
                    shiftCode = if (isWeekend) "REST" else "GEN_0830",
                    shiftName = if (isWeekend) "Rest Day" else "General Day (08:30 - 17:30)",
                    startTime = if (isWeekend) "--:--" else "08:30",
                    endTime = if (isWeekend) "--:--" else "17:30",
                    status = if (isWeekend) "REST_DAY" else (if (date == today) "ON_DUTY" else "SCHEDULED"),
                ),
            )
            val resp = TeamCoverageResponse(
                date = date,
                totalScheduled = members.count { it.status != "REST_DAY" },
                totalOnDuty = members.count { it.status == "ON_DUTY" },
                members = members,
            )
            return request.respond(resp)
        }

        private fun swapRequest(request: Request): Response {
            request.identity() ?: return request.unauthorised()
            val raw = request.bodyText() ?: return request.badRequest("VALIDATION_FAILED")
            val req = runCatching { DEMO_JSON.decodeFromString<ShiftSwapRequest>(raw) }
                .getOrElse { return request.badRequest("VALIDATION_FAILED") }
            val resp = ShiftSwapResponse(
                requestId = UUID.randomUUID().toString(),
                status = "PENDING_APPROVAL",
                message = "Shift swap request for ${req.workDate} submitted successfully.",
            )
            return request.respond(resp, code = 200)
        }

        private fun regulariseRequest(request: Request): Response {
            request.identity() ?: return request.unauthorised()
            val raw = request.bodyText() ?: return request.badRequest("VALIDATION_FAILED")
            val req = runCatching { DEMO_JSON.decodeFromString<AttendanceRegulariseRequest>(raw) }
                .getOrElse { return request.badRequest("VALIDATION_FAILED") }
            val resp = AttendanceRegulariseResponse(
                requestId = UUID.randomUUID().toString(),
                status = "PENDING_APPROVAL",
                message = "Attendance regularisation for ${req.workDate} submitted to manager inbox.",
            )
            return request.respond(resp, code = 200)
        }

        private fun pendingApprovals(request: Request): Response {
            val identity = request.identity() ?: return request.unauthorised()
            return request.respond(DemoResponses.pendingApprovals(identity, decidedApprovalIds.get()))
        }

        private fun decideApproval(
            request: Request,
            id: String,
        ): Response {
            val identity = request.identity() ?: return request.unauthorised()
            val req = request.bodyText()?.let {
                runCatching { DEMO_JSON.decodeFromString<ApprovalDecisionRequest>(it) }.getOrNull()
            } ?: ApprovalDecisionRequest(decision = ApprovalDecisionRequest.Decision.APPROVE)

            decidedApprovalIds.updateAndGet { it + id }
            return request.respond(DemoResponses.decideApproval(id, req.decision.value, req.remarks))
        }

        private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return r * c
        }

        private fun employeeProfile(
            request: Request,
            rawId: String,
        ): Response {
            val identity = request.identity() ?: return request.unauthorised()
            val subject = personOrNull(rawId) ?: return request.notFound()

            // 404 rather than 403 for a record the caller may not open, so the endpoint cannot be
            // walked to enumerate employee ids. The same answer as an id that does not exist.
            if (!identity.mayOpen(subject)) return request.notFound()

            return request.respond(DemoResponses.profile(subject, identity, LocalDate.now()))
        }

        private fun employeeForm(
            request: Request,
            rawId: String,
        ): Response {
            val identity = request.identity() ?: return request.unauthorised()
            val subject = personOrNull(rawId) ?: return request.notFound()
            if (!identity.mayOpen(subject)) return request.notFound()

            return request.respond(DemoResponses.employeeForm(subject, identity))
        }

        // --------------------------------------------------------------------

        private fun personOrNull(rawId: String): DemoPerson? =
            runCatching { UUID.fromString(rawId) }.getOrNull()?.let(DemoWorkforce::byId)

        private fun Request.identity(): DemoIdentity? {
            val bearer = header("Authorization")?.removePrefix("Bearer ")?.trim()
            if (!isDemoToken(bearer, DEMO_ACCESS)) return null
            return demoIdentityFromToken(bearer)
        }

        private fun leaveBalances(request: Request): Response {
            request.identity() ?: return request.unauthorised()
            return request.respond(demoBalances.get())
        }

        private fun leaveApplications(request: Request): Response {
            request.identity() ?: return request.unauthorised()
            val status = request.url.queryParameter("status")
            val all = demoApplications.get().applications
            val filtered = if (!status.isNullOrBlank()) {
                all.filter { it.status.value.equals(status, ignoreCase = true) }
            } else all
            return request.respond(LeaveApplicationsResponse(applications = filtered))
        }

        private fun leaveEligibility(request: Request): Response {
            request.identity() ?: return request.unauthorised()
            val raw = request.bodyText() ?: return request.badRequest("VALIDATION_FAILED")
            val req = runCatching { DEMO_JSON.decodeFromString<LeaveEligibilityRequest>(raw) }
                .getOrElse { return request.badRequest("VALIDATION_FAILED") }

            val balances = demoBalances.get().balances
            val balance = balances.firstOrNull { it.leaveTypeId == req.leaveTypeId }
            val available = balance?.availableDays ?: BigDecimal("14.0")

            var workingDays = BigDecimal.ZERO
            val daysList = mutableListOf<LeaveApplicationDayItem>()
            var curr = req.startDate
            val portion = req.dayPortion ?: LeaveEligibilityRequest.DayPortion.FULL_DAY
            val weight = if (portion == LeaveEligibilityRequest.DayPortion.FULL_DAY) BigDecimal.ONE else BigDecimal("0.5")
            val hours = if (portion == LeaveEligibilityRequest.DayPortion.FULL_DAY) BigDecimal("8.0") else BigDecimal("4.0")
            val portionItem = runCatching { LeaveApplicationDayItem.Portion.valueOf(portion.value) }.getOrDefault(LeaveApplicationDayItem.Portion.FULL_DAY)

            while (!curr.isAfter(req.endDate)) {
                val isWeekend = curr.dayOfWeek == java.time.DayOfWeek.SATURDAY || curr.dayOfWeek == java.time.DayOfWeek.SUNDAY
                val isWorking = !isWeekend
                if (isWorking) workingDays += weight
                daysList.add(
                    LeaveApplicationDayItem(
                        date = curr,
                        dayOfWeek = curr.dayOfWeek.name,
                        isWorkingDay = isWorking,
                        isPublicHoliday = false,
                        holidayName = null,
                        portion = portionItem,
                        hours = if (isWorking) hours else BigDecimal.ZERO,
                    )
                )
                curr = curr.plusDays(1)
            }

            val remaining = available - workingDays
            val reasons = mutableListOf<String>()
            if (req.endDate.isBefore(req.startDate)) reasons.add("End date cannot precede start date")
            if (workingDays <= BigDecimal.ZERO) reasons.add("All selected dates fall on weekends")
            if (remaining < BigDecimal.ZERO) reasons.add("Insufficient leave balance")

            val response = LeaveEligibilityResponse(
                eligible = reasons.isEmpty(),
                workingDaysRequested = workingDays,
                balanceAvailable = available,
                remainingAfter = remaining,
                reasons = reasons,
                days = daysList,
            )
            return request.respond(response)
        }

        private fun submitLeave(request: Request): Response {
            request.identity() ?: return request.unauthorised()
            val raw = request.bodyText() ?: return request.badRequest("VALIDATION_FAILED")
            val req = runCatching { DEMO_JSON.decodeFromString<LeaveApplicationRequest>(raw) }
                .getOrElse { return request.badRequest("VALIDATION_FAILED") }

            val balances = demoBalances.get().balances
            val balance = balances.firstOrNull { it.leaveTypeId == req.leaveTypeId }

            var workingDays = BigDecimal.ZERO
            var curr = req.startDate
            val portion = req.dayPortion ?: LeaveApplicationRequest.DayPortion.FULL_DAY
            val weight = if (portion == LeaveApplicationRequest.DayPortion.FULL_DAY) BigDecimal.ONE else BigDecimal("0.5")
            while (!curr.isAfter(req.endDate)) {
                if (curr.dayOfWeek != java.time.DayOfWeek.SATURDAY && curr.dayOfWeek != java.time.DayOfWeek.SUNDAY) {
                    workingDays += weight
                }
                curr = curr.plusDays(1)
            }

            val dayPortionItem = runCatching { LeaveApplicationItem.DayPortion.valueOf(portion.value) }.getOrDefault(LeaveApplicationItem.DayPortion.FULL_DAY)

            val newApp = LeaveApplicationItem(
                id = UUID.randomUUID(),
                employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                employeeName = "Kasun Perera",
                leaveTypeId = req.leaveTypeId,
                leaveTypeCode = balance?.leaveTypeCode ?: "ANNUAL",
                leaveTypeName = balance?.leaveTypeName ?: "Annual Leave",
                startDate = req.startDate,
                endDate = req.endDate,
                dayPortion = dayPortionItem,
                totalDays = workingDays,
                reason = req.reason,
                status = LeaveApplicationItem.Status.SUBMITTED,
                submittedAt = java.time.OffsetDateTime.now(),
                approvedAt = null,
                days = emptyList(),
            )

            demoApplications.set(
                LeaveApplicationsResponse(applications = listOf(newApp) + demoApplications.get().applications)
            )

            val updatedBalances = balances.map { b ->
                if (b.leaveTypeId == req.leaveTypeId) {
                    b.copy(
                        pendingDays = b.pendingDays + workingDays,
                        availableDays = (b.availableDays - workingDays).coerceAtLeast(BigDecimal.ZERO),
                    )
                } else b
            }
            demoBalances.set(LeaveBalancesResponse(leaveYear = "2026", balances = updatedBalances))

            return request.respond(newApp, code = 201)
        }

        private fun cancelLeave(request: Request, id: String): Response {
            request.identity() ?: return request.unauthorised()
            val uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: return request.notFound()
            val current = demoApplications.get().applications.find { it.id == uuid } ?: return request.notFound()

            val updatedApp = current.copy(status = LeaveApplicationItem.Status.CANCELLED)
            demoApplications.set(
                LeaveApplicationsResponse(applications = demoApplications.get().applications.map {
                    if (it.id == uuid) updatedApp else it
                })
            )

            val updatedBalances = demoBalances.get().balances.map { b ->
                if (b.leaveTypeId == current.leaveTypeId) {
                    b.copy(
                        pendingDays = (b.pendingDays - current.totalDays).coerceAtLeast(BigDecimal.ZERO),
                        availableDays = b.availableDays + current.totalDays,
                    )
                } else b
            }
            demoBalances.set(LeaveBalancesResponse(leaveYear = "2026", balances = updatedBalances))

            return request.respond(updatedApp)
        }

        private fun leaveLedger(request: Request): Response {
            request.identity() ?: return request.unauthorised()
            val leaveTypeId = request.url.queryParameter("leaveTypeId")
            val all = demoLedger.get().ledger
            val filtered = if (!leaveTypeId.isNullOrBlank()) {
                all.filter { it.leaveTypeId == leaveTypeId }
            } else all
            return request.respond(LeaveLedgerResponse(ledger = filtered))
        }

        private companion object {
            /**
             * Long enough that a loading state is visible and the directory's 250 ms debounce is
             * observable by hand; short enough that nobody waits on it.
             */
            const val LATENCY_MILLIS = 140L

            const val DEFAULT_PAGE_SIZE = 50

            val EMPLOYEE_PATH = Regex("""^/v1/employees/([^/]+)$""")
            val EMPLOYEE_FORM_PATH = Regex("""^/v1/employees/([^/]+)/form$""")
            val APPROVAL_DECISION_PATH = Regex("""^/v1/approvals/([^/]+)/decision$""")
            val LEAVE_CANCEL_PATH = Regex("""^/v1/leave/applications/([^/]+)/cancel$""")
            val LOAN_DETAIL_PATH = Regex("""^/v1/loans/me/([^/]+)$""")
            val LOAN_SETTLE_PATH = Regex("""^/v1/loans/me/([^/]+)/settle$""")
        }
    }


/**
 * The same `Json` Retrofit decodes with.
 *
 * Encoding a response with a different configuration than the one that will read it is how a
 * fixture ends up passing its own tests and failing in the app — `explicitNulls` alone is enough
 * to change whether an omitted-versus-null field survives the round trip.
 */
private val DEMO_JSON = Serializer.kotlinxSerializationJson

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

private inline fun <reified T> Request.respond(
    body: T,
    code: Int = 200,
): Response = rawResponse(this, code, DEMO_JSON.encodeToString(body))

private fun Request.unauthorised(code: String = "AUTHENTICATION_REQUIRED"): Response =
    rawResponse(
        this,
        401,
        DEMO_JSON.encodeToString(
            ApiErrorResponse(ApiError(code = code, message = "Demo transport: no valid bearer token")),
        ),
    )

private fun Request.notFound(): Response =
    rawResponse(
        this,
        404,
        DEMO_JSON.encodeToString(
            ApiErrorResponse(ApiError(code = "NOT_FOUND", message = "Demo transport: no such record")),
        ),
    )

private fun Request.badRequest(code: String): Response =
    rawResponse(
        this,
        400,
        DEMO_JSON.encodeToString(
            ApiErrorResponse(ApiError(code = code, message = "Demo transport: could not read the request body")),
        ),
    )

private fun rawResponse(
    request: Request,
    code: Int,
    body: String,
): Response =
    Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(
            when (code) {
                200 -> "OK"
                201 -> "Created"
                400 -> "Bad Request"
                401 -> "Unauthorized"
                404 -> "Not Found"
                else -> "OK"
            },
        )
        .body(body.toResponseBody(JSON_MEDIA_TYPE))
        .build()

/**
 * Reads the outgoing request body without consuming it.
 *
 * `writeTo` into a fresh buffer rather than reading the source, because a `RequestBody` built by
 * Retrofit is not necessarily replayable — and the request is handed on unchanged in the cases this
 * interceptor does not answer.
 */
private fun Request.bodyText(): String? {
    val body = body ?: return null
    return runCatching {
        val buffer = Buffer()
        body.writeTo(buffer)
        buffer.readUtf8()
    }.getOrNull()
}

private fun Request.jsonBody(): JsonObject? =
    bodyText()?.let { text ->
        runCatching { DEMO_JSON.parseToJsonElement(text) as? JsonObject }.getOrNull()
    }

private fun JsonObject.stringField(name: String): String? =
    runCatching { this[name]?.jsonPrimitive?.content }.getOrNull()
