package com.hr.app.demo

import com.hr.client.model.ChannelPreference
import com.hr.client.model.CountedEntity
import com.hr.client.model.Device
import com.hr.client.model.DirectoryEntry
import com.hr.client.model.DirectoryPage
import com.hr.client.model.EmployeeProfile
import com.hr.client.model.ExpiringDocumentItem
import com.hr.client.model.EmployeeDocumentItem
import com.hr.client.model.RenewDocumentResponse
import com.hr.client.model.FieldValidation
import com.hr.client.model.FormField
import com.hr.client.model.FormSchema
import com.hr.client.model.FormSection
import com.hr.client.model.HomeCard
import com.hr.client.model.MeResponse
import com.hr.client.model.MilestoneItem
import com.hr.client.model.MobileHomeResponse
import com.hr.client.model.NotificationChannel
import com.hr.client.model.NotificationSettings
import com.hr.client.model.PendingApprovalsPayload
import com.hr.client.model.QuickActionItem
import com.hr.client.model.QuietHoursSettings
import com.hr.client.model.ResolveTenantResponse
import com.hr.client.model.TenantSummary
import com.hr.client.model.TokenResponse
import com.hr.client.model.AnnouncementItem
import com.hr.client.model.AttendancePunchItem
import com.hr.client.model.AttendanceShiftInfo
import com.hr.client.model.BranchGeofenceInfo
import com.hr.client.model.TodayAttendanceResponse
import com.hr.client.model.ApprovalItem
import com.hr.client.model.LeaveApprovalDetails
import com.hr.client.model.AttendanceApprovalDetails
import com.hr.client.model.PendingApprovalsResponse
import com.hr.client.model.ApprovalDecisionResponse
import com.hr.client.model.LeaveBalanceItem
import com.hr.client.model.LeaveBalancesResponse
import com.hr.client.model.LeaveApplicationItem
import com.hr.client.model.LeaveApplicationsResponse
import com.hr.client.model.LeaveEligibilityResponse
import com.hr.client.model.LeaveLedgerEntryItem
import com.hr.client.model.LeaveLedgerResponse
import com.hr.client.model.LoanTypeItem
import com.hr.client.model.LoanTypesResponse
import com.hr.client.model.EmployeeLoanItem
import com.hr.client.model.EmployeeLoansResponse
import com.hr.client.model.EmployeeLoanDetailResponse
import com.hr.client.model.LoanRepaymentScheduleItem
import com.hr.client.model.LoanEligibilityResponse
import com.hr.client.model.LoanSettlementResponse
import com.hr.client.infrastructure.Serializer

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * The payloads the demo transport answers with.
 *
 * ## Built as models, not as JSON text
 *
 * Every function here returns an instance of the **generated** kotlinx-serialization model from
 * `clients/kotlin`, which the transport then encodes with the generated `Serializer`. That is a
 * deliberate choice over hand-written JSON strings.
 *
 * A required property that a hand-written fixture forgot decodes into a `MissingFieldException`
 * at runtime — on a device, inside a coroutine, surfacing as "Could not load the directory" with
 * no clue why. Constructing the model makes the same mistake a compile error, and encoding it with
 * the same `Json` instance Retrofit decodes with means the round trip cannot be lossy.
 *
 * It is still canned JSON on the wire; it simply cannot be canned *wrongly*.
 *
 * ## What is deliberately not modelled
 *
 * Field-level permissions are applied ([EmployeeProfile] fields are dropped, and the form schema
 * omits them) because that behaviour is visible on screen and worth demonstrating. Everything
 * else — optimistic concurrency, cursors past the first page, masked values — is left at its
 * simplest correct answer. This is a fixture for clicking through the app, not a second
 * implementation of the server.
 */
internal object DemoResponses {
    // ------------------------------------------------------------------------
    // Authentication
    // ------------------------------------------------------------------------

    fun resolveTenant(): ResolveTenantResponse =
        ResolveTenantResponse(
            code = DemoCompany.TENANT_CODE,
            name = DemoCompany.NAME,
            // Password only. Offering SSO would put the sign-in screen into a branch that needs a
            // browser and an identity provider, neither of which a demo build has.
            authMethods = listOf(ResolveTenantResponse.AuthMethods.PASSWORD),
            brandColor = "#1F6FEB",
            locale = DemoCompany.LOCALE,
        )

    fun tokens(
        identity: DemoIdentity,
        nonce: Long,
    ): TokenResponse =
        TokenResponse(
            accessToken = identity.token(DEMO_ACCESS, nonce),
            refreshToken = identity.token(DEMO_REFRESH, nonce),
            tokenType = TokenResponse.TokenType.BEARER,
            // Fifteen minutes, as the real server issues. Long enough that a demo session never
            // trips `SessionStore`'s refresh margin mid-tap, short enough to stay honest.
            expiresIn = ACCESS_TOKEN_SECONDS,
            refreshExpiresIn = REFRESH_TOKEN_SECONDS,
            // Offered so the enrolment prompt after sign-in is reachable on a device that has a
            // fingerprint registered. It skips itself where biometrics are unavailable.
            biometricEnrolmentOffered = true,
        )

    fun device(
        deviceId: String,
        model: String?,
        osVersion: String?,
        appVersion: String?,
    ): Device =
        Device(
            id = demoId("device", deviceId),
            deviceId = deviceId,
            platform = Device.Platform.ANDROID,
            trusted = true,
            model = model,
            osVersion = osVersion,
            appVersion = appVersion,
            biometricEnrolled = false,
            attestationVerified = false,
            current = true,
        )

    // ------------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------------

    fun me(identity: DemoIdentity): MeResponse =
        MeResponse(
            userId = demoId("user", "${identity.account.username}:${identity.person.code}"),
            username = identity.account.username,
            tenant =
                TenantSummary(
                    id = DemoCompany.tenantId,
                    code = DemoCompany.TENANT_CODE,
                    name = DemoCompany.NAME,
                    defaultCurrency = DemoCompany.CURRENCY,
                    timezone = DemoCompany.TIMEZONE,
                ),
            roles = listOf(identity.account.roleKey),
            permissions = identity.account.permissions.sorted(),
            // The server returns exactly this until the tenant module registry lands, so the demo
            // returns it too rather than inventing modules the app has no screens for.
            enabledModules = listOf("identity"),
            employeeId = identity.person.id,
            displayName = identity.person.displayName,
            email = "${identity.account.username}@${DemoCompany.EMAIL_DOMAIN}",
            locale = DemoCompany.LOCALE,
            timezone = DemoCompany.TIMEZONE,
            mustChangePassword = false,
            // False so the demo never reaches the second-factor step. There is no authenticator app
            // paired with a fixture, so requiring a code would be a dead end rather than a demo.
            mfaEnabled = false,
        )

    // ------------------------------------------------------------------------
    // Directory
    // ------------------------------------------------------------------------

    /**
     * The directory, filtered by free text.
     *
     * Ranked name first, then employee code, then email — the same ordering the server's search
     * vector produces, arrived at by a much stupider route. Matching is a case-insensitive
     * substring rather than a tokenised search: a fixture of nine people does not need a search
     * engine, and pretending otherwise would invite someone to trust it.
     */
    fun directory(
        query: String?,
        limit: Int,
    ): DirectoryPage {
        val needle = query?.trim()?.lowercase().orEmpty()

        val ranked =
            DemoWorkforce.people
                .mapNotNull { person ->
                    val rank =
                        when {
                            needle.isEmpty() -> 0
                            person.displayName.lowercase().contains(needle) -> 0
                            person.designationName.lowercase().contains(needle) -> 1
                            person.departmentName.lowercase().contains(needle) -> 1
                            person.code.lowercase().contains(needle) -> 2
                            person.workEmail.lowercase().contains(needle) -> 3
                            person.mobile.contains(needle) -> 3
                            else -> null
                        }
                    rank?.let { it to person }
                }
                .sortedWith(compareBy<Pair<Int, DemoPerson>>({ it.first }, { it.second.displayName }))
                .map { it.second }

        val page = ranked.take(limit.coerceAtLeast(1))

        return DirectoryPage(
            items = page.map(::directoryEntry),
            // One page, always. Nine people cannot fill the smallest page size the app asks for,
            // so a cursor would be an untested code path pretending to be exercised.
            hasMore = false,
        )
    }

    private fun directoryEntry(person: DemoPerson): DirectoryEntry =
        DirectoryEntry(
            id = person.id,
            employeeCode = person.code,
            displayName = person.displayName,
            designation = person.designationName,
            department = person.departmentName,
            location = DemoCompany.LOCATION_NAME,
            workEmail = person.workEmail,
            mobile = person.mobile,
            supervisorId = person.supervisorId,
        )

    // ------------------------------------------------------------------------
    // Employee profiles
    // ------------------------------------------------------------------------

    /**
     * One profile, filtered to what [viewer] may see.
     *
     * The filtering is the point. A field the caller may not see is **absent** from the payload,
     * not null — `ProfileFields.valueFor` and the schema-driven screen above it are built on that
     * distinction, and a fixture that returned everything would leave the one interesting bit of
     * this screen unexercised.
     *
     * `EmployeeProfile` has no field-presence tracking of its own, so "absent" is expressed the
     * only way the generated model allows: the property is left null, and the *form schema* omits
     * the field so no row is ever drawn for it.
     */
    fun profile(
        subject: DemoPerson,
        viewer: DemoIdentity,
        today: LocalDate,
    ): EmployeeProfile {
        val isSelf = subject.code == viewer.person.code

        return EmployeeProfile(
            id = subject.id,
            // Every demo record is at version 1: nothing here is ever written back, so a version
            // that moved would be theatre.
            version = 1L,
            employeeCode = subject.code,
            status = EmployeeProfile.Status.ACTIVE,
            displayName = subject.displayName,
            firstName = subject.firstName,
            lastName = subject.lastName,
            // Sensitive on the server (`FieldPermissionResolver.ALWAYS_SENSITIVE`), and sensitive
            // here: readable on your own record, withheld on anyone else's — including from an
            // administrator, because maintaining records and handling identity documents are
            // separable duties.
            dateOfBirth = subject.dateOfBirth(today).takeIf { isSelf },
            workEmail = subject.workEmail,
            mobile = subject.mobile,
            companyId = DemoCompany.companyId,
            joinDate = subject.joinDate(today),
            departmentId = subject.departmentId,
            designationId = subject.designationId,
            locationId = DemoCompany.locationId,
            supervisorId = subject.supervisorId,
            yearsOfService = subject.yearsOfService(today),
        )
    }

    /**
     * The edit form for one record, as `GET /v1/employees/{id}/form` would build it.
     *
     * Transcribed from `FormSchemaService.BUILT_IN_FORMS`, then narrowed the same two ways the
     * server narrows it: fields the caller may not see are dropped entirely, and fields they may
     * see but not change come back with `editable = false`. A section left with nothing in it is
     * not rendered as an empty heading.
     */
    fun employeeForm(
        subject: DemoPerson,
        viewer: DemoIdentity,
    ): FormSchema {
        val isSelf = subject.code == viewer.person.code
        val manages = "employee.manage" in viewer.account.permissions

        val sections =
            BUILT_IN_EMPLOYEE_FORM
                .map { section ->
                    section.copy(
                        fields =
                            section.fields
                                .filter { isSelf || it.key !in ALWAYS_SENSITIVE }
                                .map { field ->
                                    val writable = manages || (isSelf && field.key in SELF_WRITABLE)
                                    if (writable) field else field.copy(editable = false)
                                },
                    )
                }
                .filter { it.fields.isNotEmpty() }

        return FormSchema(
            entityType = "employee",
            // "base" is what the server returns when a tenant has defined no custom fields, and
            // the demo tenant has none.
            version = "base",
            sections = sections,
        )
    }

    // ------------------------------------------------------------------------
    // Notification settings
    // ------------------------------------------------------------------------

    /**
     * The starting point for the notification settings screen.
     *
     * Deliberately **not** empty. The response is sparse — only what differs from the default is
     * stored — so an empty set would render every switch in its default position and leave the
     * sparse/dense expansion in `NotificationSettingsState` looking correct whether or not it is.
     * Two overrides and a quiet-hours window mean the screen has something to show and something
     * to get wrong.
     */
    fun defaultNotificationSettings(): NotificationSettings =
        NotificationSettings(
            preferences =
                listOf(
                    ChannelPreference(
                        eventKey = "attendance.missing",
                        channel = NotificationChannel.PUSH,
                        enabled = false,
                    ),
                    ChannelPreference(
                        eventKey = "document.expiring",
                        channel = NotificationChannel.EMAIL,
                        enabled = false,
                    ),
                ),
            quietHours =
                QuietHoursSettings(
                    startAt = "22:00",
                    endAt = "07:00",
                    timezone = DemoCompany.TIMEZONE,
                    enabled = true,
                ),
        )

    // ------------------------------------------------------------------------

    /** Fifteen minutes, matching `AuthenticationService`. */
    private const val ACCESS_TOKEN_SECONDS = 900

    /** Thirty days, likewise. */
    private const val REFRESH_TOKEN_SECONDS = 2_592_000

    /**
     * Hidden from everyone except their owner, per `FieldPermissionResolver.ALWAYS_SENSITIVE`.
     *
     * Only the keys the built-in employee form actually contains are listed; the rest of the
     * server's set covers columns this client has no field for.
     */
    private val ALWAYS_SENSITIVE =
        setOf(
            "dateOfBirth",
            "personalEmail",
            "maritalStatusId",
            "bloodGroupId",
        )

    /** What a person may change on their own record without any grant. `FieldPermissionResolver.SELF_WRITABLE`. */
    private val SELF_WRITABLE =
        setOf(
            "preferredName",
            "personalEmail",
            "mobile",
            "bloodGroupId",
        )

    private val BUILT_IN_EMPLOYEE_FORM: List<FormSection> =
        listOf(
            FormSection(
                key = "personal",
                label = "Personal",
                fields =
                    listOf(
                        FormField(
                            key = "firstName", label = "First name", type = FormField.Type.TEXT,
                            required = true, validation = FieldValidation(maxLength = 128),
                        ),
                        FormField(
                            key = "middleName", label = "Middle name", type = FormField.Type.TEXT,
                            validation = FieldValidation(maxLength = 128),
                        ),
                        FormField(
                            key = "lastName", label = "Last name", type = FormField.Type.TEXT,
                            required = true, validation = FieldValidation(maxLength = 128),
                        ),
                        FormField(
                            key = "displayName", label = "Display name", type = FormField.Type.TEXT,
                            required = true,
                            helpText = "How this person's name is shown throughout the app.",
                        ),
                        FormField(key = "preferredName", label = "Preferred name", type = FormField.Type.TEXT),
                        FormField(key = "dateOfBirth", label = "Date of birth", type = FormField.Type.DATE),
                        FormField(
                            key = "genderTypeId", label = "Gender", type = FormField.Type.REFERENCE,
                            referenceTable = "gender-type",
                        ),
                        FormField(
                            key = "maritalStatusId", label = "Marital status", type = FormField.Type.REFERENCE,
                            referenceTable = "marital-status",
                        ),
                        FormField(
                            key = "nationalityId", label = "Nationality", type = FormField.Type.REFERENCE,
                            referenceTable = "nationality",
                        ),
                        FormField(
                            key = "bloodGroupId", label = "Blood group", type = FormField.Type.REFERENCE,
                            referenceTable = "blood-group",
                        ),
                    ),
            ),
            FormSection(
                key = "contact",
                label = "Contact",
                fields =
                    listOf(
                        FormField(key = "workEmail", label = "Work email", type = FormField.Type.EMAIL),
                        FormField(key = "personalEmail", label = "Personal email", type = FormField.Type.EMAIL),
                        FormField(key = "mobile", label = "Mobile", type = FormField.Type.PHONE),
                        FormField(key = "workPhone", label = "Work phone", type = FormField.Type.PHONE),
                    ),
            ),
            FormSection(
                key = "employment",
                label = "Employment",
                fields =
                    listOf(
                        FormField(
                            key = "employeeCode", label = "Employee code", type = FormField.Type.TEXT,
                            required = true,
                        ),
                        FormField(
                            key = "joinDate", label = "Join date", type = FormField.Type.DATE,
                            required = true,
                        ),
                        FormField(key = "confirmationDate", label = "Confirmation date", type = FormField.Type.DATE),
                        FormField(
                            key = "employmentTypeId", label = "Employment type", type = FormField.Type.REFERENCE,
                            referenceTable = "employment-type",
                        ),
                        FormField(
                            key = "employeeCategoryId", label = "Category", type = FormField.Type.REFERENCE,
                            referenceTable = "employee-category",
                        ),
                    ),
            ),
            FormSection(
                key = "workstation",
                label = "Workstation",
                fields =
                    listOf(
                        FormField(
                            key = "departmentId", label = "Department", type = FormField.Type.REFERENCE,
                            referenceTable = "department",
                        ),
                        FormField(
                            key = "designationId", label = "Designation", type = FormField.Type.REFERENCE,
                            referenceTable = "designation",
                        ),
                        FormField(
                            key = "locationId", label = "Location", type = FormField.Type.REFERENCE,
                            referenceTable = "location",
                        ),
                        FormField(
                            key = "supervisorId", label = "Reports to", type = FormField.Type.EMPLOYEE,
                            helpText = "The solid reporting line. Approvals route to this person.",
                        ),
                    ),
            ),
        )

    fun mobileHome(currentUser: DemoIdentity): MobileHomeResponse {
        val cards = mutableListOf<HomeCard>()

        // 1. Pending Approvals (visible if user has approval authority)
        if (currentUser.account.approves) {
            val pendingPayload = PendingApprovalsPayload(
                totalCount = 3,
                countIsApproximate = false,
                countedEntities = listOf(
                    CountedEntity(entityType = "leaveApplication", entityId = "demo-leave-001"),
                    CountedEntity(entityType = "leaveApplication", entityId = "demo-leave-002"),
                    CountedEntity(entityType = "attendanceRegularisation", entityId = "demo-att-001"),
                ),
                leaveCount = 2,
                attendanceCount = 1,
                claimsCount = 0,
                actionUri = "hrapp://approvals",
            )
            val jsonMap = Serializer.kotlinxSerializationJson.encodeToJsonElement(
                PendingApprovalsPayload.serializer(),
                pendingPayload,
            ).jsonObject
            cards.add(
                HomeCard(
                    id = "card-pending-approvals",
                    type = "PENDING_APPROVALS",
                    title = "Pending Approvals",
                    priority = 10,
                    payload = jsonMap,
                ),
            )
        }

        // 2. Expiring Documents (e.g. Visa/Passport alert)
        val expiringItems = listOf(
            ExpiringDocumentItem(
                id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                docType = "PASSPORT",
                expiryDate = LocalDate.now().plusDays(25),
                daysRemaining = 25,
                status = "EXPIRING",
                actionUri = "hrapp://documents/00000000-0000-0000-0000-000000000001",
            ),
        )
        val expiringMap = mapOf(
            "items" to Serializer.kotlinxSerializationJson.encodeToJsonElement(
                ListSerializer(ExpiringDocumentItem.serializer()),
                expiringItems,
            ),
        )
        cards.add(
            HomeCard(
                id = "card-expiring-docs",
                type = "EXPIRING_DOCUMENTS",
                title = "Expiring Documents",
                priority = 20,
                payload = expiringMap,
            ),
        )

        // 3. Quick Actions Grid
        val quickActions = listOf(
            QuickActionItem(key = "clock_in", label = "Clock In", icon = "access_time", actionUri = "hrapp://time"),
            QuickActionItem(key = "request_leave", label = "Apply Leave", icon = "beach_access", actionUri = "hrapp://leave"),
            QuickActionItem(key = "directory", label = "Directory", icon = "people", actionUri = "hrapp://directory"),
            QuickActionItem(key = "my_profile", label = "My Profile", icon = "person", actionUri = "hrapp://profile"),
        )
        val quickActionsMap = mapOf(
            "actions" to Serializer.kotlinxSerializationJson.encodeToJsonElement(
                ListSerializer(QuickActionItem.serializer()),
                quickActions,
            ),
        )
        cards.add(
            HomeCard(
                id = "card-quick-actions",
                type = "QUICK_ACTIONS",
                title = "Quick Actions",
                priority = 30,
                payload = quickActionsMap,
            ),
        )

        // 4. Announcements
        val announcements = listOf(
            AnnouncementItem(
                id = UUID.fromString("00000000-0000-0000-0000-000000000010"),
                title = "Annual Company Retreat 2026",
                summary = "Join us next month for our annual team gathering in Ella. Please confirm dietary preferences by Friday.",
                publishedAt = java.time.OffsetDateTime.now().minusDays(5),
                priority = "HIGH",
                authorName = "People Experience",
                actionUri = null,
            ),
            AnnouncementItem(
                id = UUID.fromString("00000000-0000-0000-0000-000000000011"),
                title = "New Medical Outpatient Claims Limit",
                summary = "Effective this month, outpatient specialist coverage has been increased across all employee tiers.",
                publishedAt = java.time.OffsetDateTime.now().minusDays(8),
                priority = "NORMAL",
                authorName = "HR Operations",
                actionUri = null,
            ),
        )
        val announcementsMap = mapOf(
            "items" to Serializer.kotlinxSerializationJson.encodeToJsonElement(
                ListSerializer(AnnouncementItem.serializer()),
                announcements,
            ),
        )
        cards.add(
            HomeCard(
                id = "card-announcements",
                type = "ANNOUNCEMENTS",
                title = "Announcements",
                priority = 40,
                payload = announcementsMap,
            ),
        )

        // 5. Milestones (colleague birthdays and work anniversaries today)
        val ruwan = DemoWorkforce.byCode("E002")!!
        val kasun = DemoWorkforce.byCode("E004")!!
        val milestones = listOf(
            MilestoneItem(
                employeeId = ruwan.id,
                displayName = ruwan.displayName,
                designation = ruwan.designationName,
                photoKey = null,
                type = "WORK_ANNIVERSARY",
                eventDate = LocalDate.now(),
                yearsCount = 5,
                actionUri = "hrapp://employee/${ruwan.id}",
            ),
            MilestoneItem(
                employeeId = kasun.id,
                displayName = kasun.displayName,
                designation = kasun.designationName,
                photoKey = null,
                type = "BIRTHDAY",
                eventDate = LocalDate.now(),
                yearsCount = null,
                actionUri = "hrapp://employee/${kasun.id}",
            ),
        )
        val milestonesMap = mapOf(
            "items" to Serializer.kotlinxSerializationJson.encodeToJsonElement(
                ListSerializer(MilestoneItem.serializer()),
                milestones,
            ),
        )
        cards.add(
            HomeCard(
                id = "card-milestones",
                type = "MILESTONES",
                title = "Milestones Today",
                priority = 50,
                payload = milestonesMap,
            ),
        )

        return MobileHomeResponse(
            syncCursor = "cursor_home_demo_01",
            cards = cards,
        )
    }

    fun todayAttendance(
        employeeId: UUID = DemoWorkforce.byCode("E004")?.id ?: demoId("person", "E004"),
        punches: List<AttendancePunchItem> = emptyList(),
    ): TodayAttendanceResponse {
        val hasIn = punches.any { it.punchType == AttendancePunchItem.PunchType.IN }
        val lastPunch = punches.lastOrNull()
        val currentStatus = when (lastPunch?.punchType) {
            AttendancePunchItem.PunchType.IN -> "CHECKED_IN"
            AttendancePunchItem.PunchType.BREAK_OUT -> "ON_BREAK"
            AttendancePunchItem.PunchType.BREAK_IN -> "CHECKED_IN"
            AttendancePunchItem.PunchType.OUT -> "CHECKED_OUT"
            else -> "NOT_CHECKED_IN"
        }
        val dayStatus = if (hasIn) "PRESENT" else "NOT_CHECKED_IN"
        val firstIn = punches.firstOrNull { it.punchType == AttendancePunchItem.PunchType.IN }?.punchedAt
        val lastOut = punches.lastOrNull { it.punchType == AttendancePunchItem.PunchType.OUT }?.punchedAt

        return TodayAttendanceResponse(
            employeeId = employeeId,
            workDate = LocalDate.now(),
            dayStatus = dayStatus,
            currentStatus = currentStatus,
            workedMinutes = if (hasIn) 210 else 0,
            punches = punches,
            geofence = BranchGeofenceInfo(
                branchName = "Colombo Head Office",
                latitude = java.math.BigDecimal.valueOf(6.9271),
                longitude = java.math.BigDecimal.valueOf(79.8612),
                radiusMeters = java.math.BigDecimal.valueOf(200.0),
            ),
            shift = AttendanceShiftInfo(
                id = "shift-gen-0830",
                code = "GEN_0830",
                name = "General Day Shift",
                startTime = "08:30:00",
                endTime = "17:30:00",
                graceInMinutes = 15,
                breakMinutes = 60,
            ),
            firstInAt = firstIn,
            lastOutAt = lastOut,
        )
    }

    fun defaultPendingApprovals(): List<ApprovalItem> {
        val kasun = DemoWorkforce.byCode("E004") ?: return emptyList()
        val dilani = DemoWorkforce.byCode("E003") ?: return emptyList()
        return listOf(
            ApprovalItem(
                id = "demo-leave-001",
                type = "LEAVE",
                requesterId = kasun.id,
                requesterName = kasun.displayName,
                requesterDesignation = kasun.designationName,
                departmentName = kasun.departmentName,
                submittedAt = java.time.OffsetDateTime.now().minusHours(3),
                title = "Annual Leave (2.0 days)",
                summary = "Family function in Kandy",
                status = "PENDING",
                urgency = ApprovalItem.Urgency.URGENT,
                leaveDetails = LeaveApprovalDetails(
                    leaveTypeName = "Annual Leave",
                    startDate = LocalDate.now().plusDays(5),
                    endDate = LocalDate.now().plusDays(6),
                    workingDays = java.math.BigDecimal.valueOf(2.0),
                    reason = "Family function in Kandy",
                    employeeBalanceDays = java.math.BigDecimal.valueOf(14.0),
                    teamCoverageWarning = "1 other team member on leave during this period",
                ),
            ),
            ApprovalItem(
                id = "demo-leave-002",
                type = "LEAVE",
                requesterId = dilani.id,
                requesterName = dilani.displayName,
                requesterDesignation = dilani.designationName,
                departmentName = dilani.departmentName,
                submittedAt = java.time.OffsetDateTime.now().minusHours(18),
                title = "Casual Leave (1.0 day)",
                summary = "Personal matter / medical appointment",
                status = "PENDING",
                urgency = ApprovalItem.Urgency.NORMAL,
                leaveDetails = LeaveApprovalDetails(
                    leaveTypeName = "Casual Leave",
                    startDate = LocalDate.now().plusDays(10),
                    endDate = LocalDate.now().plusDays(10),
                    workingDays = java.math.BigDecimal.valueOf(1.0),
                    reason = "Personal matter / medical appointment",
                    employeeBalanceDays = java.math.BigDecimal.valueOf(6.0),
                    teamCoverageWarning = null,
                ),
            ),
            ApprovalItem(
                id = "demo-att-001",
                type = "ATTENDANCE",
                requesterId = kasun.id,
                requesterName = kasun.displayName,
                requesterDesignation = kasun.designationName,
                departmentName = kasun.departmentName,
                submittedAt = java.time.OffsetDateTime.now().minusHours(1),
                title = "Missed Punch Regularisation",
                summary = "Biometric turnstile was undergoing maintenance at Colombo HQ entrance",
                status = "PENDING",
                urgency = ApprovalItem.Urgency.URGENT,
                attendanceDetails = AttendanceApprovalDetails(
                    workDate = LocalDate.now(),
                    requestedPunchType = "CLOCK_IN",
                    requestedTime = "08:42:00",
                    shiftName = "General Day Shift (08:30 - 17:30)",
                    reason = "Biometric turnstile was undergoing maintenance at Colombo HQ entrance",
                ),
            ),
        )
    }

    fun pendingApprovals(currentUser: DemoIdentity, decidedIds: Set<String>): PendingApprovalsResponse {
        val all = if (currentUser.account.approves) defaultPendingApprovals() else emptyList()
        val remaining = all.filterNot { it.id in decidedIds }
        return PendingApprovalsResponse(
            items = remaining,
            totalCount = remaining.size,
        )
    }

    fun decideApproval(id: String, decision: String, remarks: String?): ApprovalDecisionResponse =
        ApprovalDecisionResponse(
            id = id,
            status = if (decision.uppercase() == "APPROVE") "APPROVED" else "REJECTED",
            decidedAt = java.time.OffsetDateTime.now(),
            message = if (decision.uppercase() == "APPROVE") "Approved successfully" else "Rejected: ${remarks.orEmpty()}",
        )

    fun defaultEmployeeDocuments(): List<EmployeeDocumentItem> = listOf(
        EmployeeDocumentItem(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            docType = "PASSPORT",
            docNumberMasked = "****6543",
            issueDate = LocalDate.of(2021, 10, 5),
            expiryDate = LocalDate.now().plusDays(25),
            issuingCountry = "LK",
            daysRemaining = 25,
            status = "EXPIRING",
            hasAttachment = true,
            attachmentKey = "passport_scan.pdf",
        ),
        EmployeeDocumentItem(
            id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            docType = "VISA",
            docNumberMasked = "****9912",
            issueDate = LocalDate.of(2024, 2, 1),
            expiryDate = LocalDate.now().plusDays(365),
            issuingCountry = "LK",
            daysRemaining = 365,
            status = "VALID",
            hasAttachment = true,
            attachmentKey = "resident_visa.pdf",
        ),
        EmployeeDocumentItem(
            id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
            docType = "DRIVING_LICENCE",
            docNumberMasked = "****4321",
            issueDate = LocalDate.of(2022, 6, 15),
            expiryDate = LocalDate.now().plusDays(800),
            issuingCountry = "LK",
            daysRemaining = 800,
            status = "VALID",
            hasAttachment = false,
            attachmentKey = null,
        ),
    )

    fun defaultLeaveBalances(): LeaveBalancesResponse =
        LeaveBalancesResponse(
            leaveYear = "2026",
            balances = listOf(
                LeaveBalanceItem(
                    leaveTypeId = "annual-id",
                    leaveTypeCode = "ANNUAL",
                    leaveTypeName = "Annual Leave",
                    color = "#0284c7",
                    entitledDays = BigDecimal("14.0"),
                    accruedDays = BigDecimal("14.0"),
                    takenDays = BigDecimal("3.0"),
                    pendingDays = BigDecimal.ZERO,
                    availableDays = BigDecimal("11.0"),
                ),
                LeaveBalanceItem(
                    leaveTypeId = "casual-id",
                    leaveTypeCode = "CASUAL",
                    leaveTypeName = "Casual Leave",
                    color = "#16a34a",
                    entitledDays = BigDecimal("7.0"),
                    accruedDays = BigDecimal("7.0"),
                    takenDays = BigDecimal("2.0"),
                    pendingDays = BigDecimal.ZERO,
                    availableDays = BigDecimal("5.0"),
                ),
                LeaveBalanceItem(
                    leaveTypeId = "medical-id",
                    leaveTypeCode = "MEDICAL",
                    leaveTypeName = "Medical Leave",
                    color = "#d97706",
                    entitledDays = BigDecimal("14.0"),
                    accruedDays = BigDecimal("14.0"),
                    takenDays = BigDecimal("1.0"),
                    pendingDays = BigDecimal.ZERO,
                    availableDays = BigDecimal("13.0"),
                ),
            ),
        )

    fun defaultLeaveApplications(): LeaveApplicationsResponse =
        LeaveApplicationsResponse(
            applications = listOf(
                LeaveApplicationItem(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000101"),
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    employeeName = "Kasun Perera",
                    leaveTypeId = "annual-id",
                    leaveTypeCode = "ANNUAL",
                    leaveTypeName = "Annual Leave",
                    startDate = LocalDate.now().minusDays(15),
                    endDate = LocalDate.now().minusDays(14),
                    dayPortion = LeaveApplicationItem.DayPortion.FULL_DAY,
                    totalDays = BigDecimal("2.0"),
                    reason = "Family vacation to Nuwara Eliya",
                    status = LeaveApplicationItem.Status.APPROVED,
                    submittedAt = java.time.OffsetDateTime.now().minusDays(20),
                    approvedAt = java.time.OffsetDateTime.now().minusDays(18),
                    days = emptyList(),
                ),
                LeaveApplicationItem(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000102"),
                    employeeId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    employeeName = "Kasun Perera",
                    leaveTypeId = "casual-id",
                    leaveTypeCode = "CASUAL",
                    leaveTypeName = "Casual Leave",
                    startDate = LocalDate.now().plusDays(5),
                    endDate = LocalDate.now().plusDays(5),
                    dayPortion = LeaveApplicationItem.DayPortion.FULL_DAY,
                    totalDays = BigDecimal("1.0"),
                    reason = "Home bank paperwork",
                    status = LeaveApplicationItem.Status.SUBMITTED,
                    submittedAt = java.time.OffsetDateTime.now().minusDays(2),
                    approvedAt = null,
                    days = emptyList(),
                ),
            ),
        )

    fun defaultLeaveLedger(): LeaveLedgerResponse =
        LeaveLedgerResponse(
            ledger = listOf(
                LeaveLedgerEntryItem(
                    id = "ledger-1",
                    date = LocalDate.of(2026, 1, 1),
                    leaveTypeId = "annual-id",
                    leaveTypeCode = "ANNUAL",
                    leaveTypeName = "Annual Leave",
                    eventType = LeaveLedgerEntryItem.EventType.OPENING,
                    daysCredited = BigDecimal("14.0"),
                    daysDebited = BigDecimal.ZERO,
                    balanceAfter = BigDecimal("14.0"),
                    referenceId = "2026",
                    notes = "Annual upfront entitlement 2026",
                ),
                LeaveLedgerEntryItem(
                    id = "ledger-2",
                    date = LocalDate.of(2026, 1, 1),
                    leaveTypeId = "casual-id",
                    leaveTypeCode = "CASUAL",
                    leaveTypeName = "Casual Leave",
                    eventType = LeaveLedgerEntryItem.EventType.OPENING,
                    daysCredited = BigDecimal("7.0"),
                    daysDebited = BigDecimal.ZERO,
                    balanceAfter = BigDecimal("7.0"),
                    referenceId = "2026",
                    notes = "Casual upfront entitlement 2026",
                ),
                LeaveLedgerEntryItem(
                    id = "ledger-3",
                    date = LocalDate.now().minusDays(15),
                    leaveTypeId = "annual-id",
                    leaveTypeCode = "ANNUAL",
                    leaveTypeName = "Annual Leave",
                    eventType = LeaveLedgerEntryItem.EventType.TAKEN,
                    daysCredited = BigDecimal.ZERO,
                    daysDebited = BigDecimal("2.0"),
                    balanceAfter = BigDecimal("12.0"),
                    referenceId = "APP-101",
                    notes = "Approved leave debit",
                ),
            ),
        )

    fun defaultLoanTypes(): LoanTypesResponse =
        LoanTypesResponse(
            types = listOf(
                LoanTypeItem(
                    id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    code = "FESTIVAL_ADVANCE",
                    name = "Festival Advance",
                    description = "Zero-interest seasonal advance for cultural and religious festivals.",
                    interestMethod = LoanTypeItem.InterestMethod.ZERO_INTEREST,
                    annualInterestRate = BigDecimal("0.00"),
                    minTenureMonths = 1,
                    maxTenureMonths = 10,
                    minPrincipal = BigDecimal("5000.00"),
                    maxPrincipal = BigDecimal("100000.00"),
                    salaryMultipleLimit = BigDecimal("1.00"),
                    minServiceMonths = 3,
                    maxActiveLoans = 1,
                ),
                LoanTypeItem(
                    id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
                    code = "DISTRESS_LOAN",
                    name = "Emergency Distress Loan",
                    description = "Low-interest financial relief for medical emergencies or personal hardship.",
                    interestMethod = LoanTypeItem.InterestMethod.REDUCING_BALANCE,
                    annualInterestRate = BigDecimal("4.50"),
                    minTenureMonths = 6,
                    maxTenureMonths = 36,
                    minPrincipal = BigDecimal("10000.00"),
                    maxPrincipal = BigDecimal("300000.00"),
                    salaryMultipleLimit = BigDecimal("3.00"),
                    minServiceMonths = 6,
                    maxActiveLoans = 1,
                ),
                LoanTypeItem(
                    id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
                    code = "EDUCATION_LOAN",
                    name = "Higher Education Loan",
                    description = "Higher education and professional qualification support.",
                    interestMethod = LoanTypeItem.InterestMethod.FLAT_RATE,
                    annualInterestRate = BigDecimal("6.00"),
                    minTenureMonths = 12,
                    maxTenureMonths = 48,
                    minPrincipal = BigDecimal("20000.00"),
                    maxPrincipal = BigDecimal("500000.00"),
                    salaryMultipleLimit = BigDecimal("4.00"),
                    minServiceMonths = 12,
                    maxActiveLoans = 1,
                ),
            ),
        )

    fun defaultEmployeeLoans(): EmployeeLoansResponse =
        EmployeeLoansResponse(
            totalOutstandingBalance = BigDecimal("20000.00"),
            activeLoansCount = 1,
            loans = listOf(
                EmployeeLoanItem(
                    id = UUID.fromString("44444444-4444-4444-4444-444444444444"),
                    loanCode = "LN-2026-0042",
                    loanTypeId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    loanTypeCode = "FESTIVAL_ADVANCE",
                    loanTypeName = "Festival Advance 2026",
                    principalAmount = BigDecimal("30000.00"),
                    interestMethod = EmployeeLoanItem.InterestMethod.ZERO_INTEREST,
                    annualInterestRate = BigDecimal("0.00"),
                    tenureMonths = 6,
                    monthlyInstallment = BigDecimal("5000.00"),
                    totalInterest = BigDecimal("0.00"),
                    totalRepayable = BigDecimal("30000.00"),
                    totalRepaid = BigDecimal("10000.00"),
                    remainingBalance = BigDecimal("20000.00"),
                    reason = "Annual New Year festival advance",
                    status = EmployeeLoanItem.Status.ACTIVE,
                    disbursedDate = LocalDate.of(2026, 1, 15),
                ),
                EmployeeLoanItem(
                    id = UUID.fromString("55555555-5555-5555-5555-555555555555"),
                    loanCode = "LN-2025-0108",
                    loanTypeId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
                    loanTypeCode = "DISTRESS_LOAN",
                    loanTypeName = "Emergency Medical Relief",
                    principalAmount = BigDecimal("50000.00"),
                    interestMethod = EmployeeLoanItem.InterestMethod.REDUCING_BALANCE,
                    annualInterestRate = BigDecimal("4.50"),
                    tenureMonths = 10,
                    monthlyInstallment = BigDecimal("5103.74"),
                    totalInterest = BigDecimal("1037.40"),
                    totalRepayable = BigDecimal("51037.40"),
                    totalRepaid = BigDecimal("51037.40"),
                    remainingBalance = BigDecimal("0.00"),
                    reason = "Inpatient medical treatment",
                    status = EmployeeLoanItem.Status.SETTLED,
                    disbursedDate = LocalDate.of(2025, 3, 1),
                    settledDate = LocalDate.of(2025, 12, 1),
                ),
            ),
        )

    fun defaultLoanDetail(id: String): EmployeeLoanDetailResponse {
        val loan = defaultEmployeeLoans().loans.find { it.id.toString() == id }
            ?: defaultEmployeeLoans().loans.first()

        val schedule = (1..loan.tenureMonths).map { idx ->
            val isDeducted = idx <= 2
            LoanRepaymentScheduleItem(
                installmentNumber = idx,
                dueDate = LocalDate.now().minusMonths(2).plusMonths(idx.toLong()),
                principalAmount = loan.monthlyInstallment,
                interestAmount = BigDecimal.ZERO,
                totalInstallment = loan.monthlyInstallment,
                status = if (isDeducted) LoanRepaymentScheduleItem.Status.DEDUCTED else LoanRepaymentScheduleItem.Status.PENDING,
                deductedDate = if (isDeducted) LocalDate.now().minusMonths(2).plusMonths(idx.toLong()) else null,
            )
        }

        return EmployeeLoanDetailResponse(loan = loan, schedule = schedule)
    }

    fun defaultLoanEligibility(principal: BigDecimal, tenure: Int): LoanEligibilityResponse {
        val emi = if (tenure > 0) principal.divide(BigDecimal(tenure), 2, java.math.RoundingMode.HALF_UP) else principal
        return LoanEligibilityResponse(
            eligible = true,
            maxAllowedPrincipal = BigDecimal("150000.00"),
            projectedMonthlyInstallment = emi,
            projectedTotalInterest = BigDecimal.ZERO,
            projectedTotalRepayable = principal,
            reasons = emptyList(),
        )
    }

    fun defaultLoanSettlement(id: String): LoanSettlementResponse =
        LoanSettlementResponse(
            loanId = runCatching { UUID.fromString(id) }.getOrElse { UUID.randomUUID() },
            settledDate = LocalDate.now(),
            settlementAmountPaid = BigDecimal("20000.00"),
            remainingBalance = BigDecimal("0.00"),
            status = "SETTLED",
        )
}


/**
 * Whether [viewer] may open [subject] at all.
 *
 * Mirrors `EmployeeService`: your own record always; anyone's with `employee.view.all` or
 * `employee.manage`; your reporting line at any depth with `employee.view`. Otherwise the endpoint
 * answers **404, not 403** — a 403 confirms the record exists, which turns the endpoint into an
 * oracle for enumerating employee ids.
 */
internal fun DemoIdentity.mayOpen(subject: DemoPerson): Boolean =
    subject.code == person.code ||
        account.seesEveryone ||
        (account.seesReportingLine && DemoWorkforce.reportsTo(subject, person))
