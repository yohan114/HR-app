package com.hr.app.demo

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * The organisation the debug build pretends to be talking to.
 *
 * ## Why this is a transcription rather than something new
 *
 * `backend/src/main/kotlin/com/hr/identity/internal/LocalDemoSeeder.kt` already decides what a
 * demo of this product looks like: a Sri Lankan company in Colombo, nine people over three
 * reporting levels, three accounts chosen to make three different authorisation rules visible.
 * Inventing a second cast here would mean a screenshot from the app and a screenshot from the web
 * console showed different companies, and every conversation about "the demo data" would need to
 * ask which one.
 *
 * So the people, departments, designations, organisation code, currency and timezone below are
 * copied from the seeder deliberately, down to the mobile numbers. When the seeder changes, this
 * changes with it — they are two renderings of one fixture, not two fixtures.
 *
 * ## Debug source set only
 *
 * Nothing in this package is compiled into the release variant. See `DemoTransport` for the
 * mechanism and why it is a build-graph property rather than a runtime flag.
 */
internal object DemoCompany {
    const val TENANT_CODE = "demo"
    const val NAME = "Demo Company"
    const val TIMEZONE = "Asia/Colombo"
    const val CURRENCY = "LKR"
    const val LOCALE = "en"
    const val EMAIL_DOMAIN = "demo.local"

    const val LOCATION_NAME = "Colombo Head Office"

    val tenantId: UUID = demoId("tenant", TENANT_CODE)
    val companyId: UUID = demoId("company", "DEMO")
    val locationId: UUID = demoId("location", "CMB")

    /** Department code to display name, as `LocalDemoSeeder.seedOrganisation` creates them. */
    val departments: Map<String, String> =
        linkedMapOf(
            "ENG" to "Engineering",
            "HR" to "People & Culture",
            "FIN" to "Finance",
        )

    /** Designation code to display name, likewise. */
    val designations: Map<String, String> =
        linkedMapOf(
            "CEO" to "Chief Executive Officer",
            "ENG_MGR" to "Engineering Manager",
            "SE" to "Software Engineer",
            "HR_MGR" to "HR Manager",
            "ACC" to "Accountant",
        )

    fun departmentId(code: String): UUID = demoId("department", code)

    fun designationId(code: String): UUID = demoId("designation", code)
}

/**
 * A stable identifier for a demo record.
 *
 * Derived from the record's kind and business key rather than randomised, because the ids are
 * handed to the app and come back on the next request: the directory returns an employee id and
 * the profile screen immediately asks for `/v1/employees/{that id}`. A random id would work within
 * one process and break the moment anything cached one across a restart — and, more practically,
 * it would make a failing test impossible to read.
 *
 * `nameUUIDFromBytes` is a type-3 UUID, which is exactly the "same name, same id" property wanted
 * here. It is not a security boundary and is not used as one.
 */
internal fun demoId(
    kind: String,
    key: String,
): UUID = UUID.nameUUIDFromBytes("hr.demo:$kind:$key".toByteArray(StandardCharsets.UTF_8))

/**
 * One of the nine people in the demo workforce.
 *
 * @param birthdayIn days from today until this person's birthday, or null for no date of birth
 * @param joinedYearsAgo whole years of service
 * @param joinedDaysOffset shifts the anniversary either side of today, so the milestone cards a
 *   later phase will draw always have something in them
 */
internal class DemoPerson(
    val code: String,
    val firstName: String,
    val lastName: String,
    val displayName: String,
    val designation: String,
    val department: String,
    val supervisorCode: String?,
    val birthdayIn: Long?,
    val joinedYearsAgo: Long,
    val joinedDaysOffset: Long,
    val mobile: String,
) {
    val id: UUID get() = demoId("employee", code)

    val supervisorId: UUID? get() = supervisorCode?.let { demoId("employee", it) }

    val departmentId: UUID get() = DemoCompany.departmentId(department)

    val designationId: UUID get() = DemoCompany.designationId(designation)

    val departmentName: String get() = DemoCompany.departments.getValue(department)

    val designationName: String get() = DemoCompany.designations.getValue(designation)

    /** Built the same way the seeder builds it, so the two data sets agree character for character. */
    val workEmail: String
        get() = "${firstName.lowercase()}.${lastName.replace(" ", "").lowercase()}@${DemoCompany.EMAIL_DOMAIN}"

    fun joinDate(today: LocalDate): LocalDate = today.minusYears(joinedYearsAgo).plusDays(joinedDaysOffset)

    fun yearsOfService(today: LocalDate): Int = ChronoUnit.YEARS.between(joinDate(today), today).toInt()

    /**
     * A date of birth whose anniversary falls [birthdayIn] days from today.
     *
     * The age is varied only so the demo does not show nine people born in the same year. 29
     * February is stepped back to the 28th rather than handled properly: a birthday seeded on a
     * leap day disappears from the milestone card three years in four, which is a worse demo than
     * being one day out on that single date. Both decisions are the seeder's — copied, not
     * re-derived.
     */
    fun dateOfBirth(today: LocalDate): LocalDate? {
        val days = birthdayIn ?: return null
        val target = today.plusDays(days)
        val age = 24 + (days % 20)
        val safe = if (target.monthValue == 2 && target.dayOfMonth == 29) target.minusDays(1) else target
        return safe.minusYears(age)
    }
}

/**
 * Nine people, three levels deep.
 *
 * Three levels rather than two because "my team" means the whole subtree, and a manager who can
 * open someone two levels below them is the thing the reporting-line check has to get right. A
 * two-level tree cannot demonstrate it either way.
 */
internal object DemoWorkforce {
    val people: List<DemoPerson> =
        listOf(
            DemoPerson(
                "E001", "Nimali", "Wickramasinghe", "Nimali Wickramasinghe", "CEO", "ENG", null,
                birthdayIn = 0, joinedYearsAgo = 12, joinedDaysOffset = -40, mobile = "0771000001",
            ),
            DemoPerson(
                "E002", "Ruwan", "Jayasuriya", "Ruwan Jayasuriya", "ENG_MGR", "ENG", "E001",
                birthdayIn = 3, joinedYearsAgo = 5, joinedDaysOffset = 0, mobile = "0771000002",
            ),
            DemoPerson(
                "E003", "Priya", "Balasubramaniam", "Priya Balasubramaniam", "HR_MGR", "HR", "E001",
                birthdayIn = 21, joinedYearsAgo = 7, joinedDaysOffset = -3, mobile = "0771000003",
            ),
            DemoPerson(
                "E004", "Kasun", "Fernando", "Kasun Fernando", "SE", "ENG", "E002",
                birthdayIn = 1, joinedYearsAgo = 3, joinedDaysOffset = 2, mobile = "0771000004",
            ),
            DemoPerson(
                "E005", "Dilani", "Perera", "Dilani Perera", "SE", "ENG", "E002",
                birthdayIn = 45, joinedYearsAgo = 2, joinedDaysOffset = 5, mobile = "0771000005",
            ),
            DemoPerson(
                "E006", "Thivanka", "Rajapaksa", "Thivanka Rajapaksa", "SE", "ENG", "E002",
                birthdayIn = 120, joinedYearsAgo = 1, joinedDaysOffset = -60, mobile = "0771000006",
            ),
            DemoPerson(
                "E007", "Anusha", "Sivakumar", "Anusha Sivakumar", "ACC", "FIN", "E003",
                birthdayIn = 6, joinedYearsAgo = 4, joinedDaysOffset = -120, mobile = "0771000007",
            ),
            DemoPerson(
                "E008", "Malith", "Gunawardena", "Malith Gunawardena", "SE", "ENG", "E002",
                birthdayIn = null, joinedYearsAgo = 10, joinedDaysOffset = 4, mobile = "0771000008",
            ),
            DemoPerson(
                "E009", "Shanika", "de Silva", "Shanika de Silva", "ACC", "FIN", "E003",
                birthdayIn = 200, joinedYearsAgo = 6, joinedDaysOffset = -200, mobile = "0771000009",
            ),
        )

    fun byCode(code: String): DemoPerson? = people.firstOrNull { it.code == code }

    fun byId(id: UUID): DemoPerson? = people.firstOrNull { it.id == id }

    /**
     * Whether [candidate] is anywhere beneath [supervisor] in the solid reporting line.
     *
     * Walks upwards rather than downwards because a person has one supervisor and a supervisor has
     * many reports — and because the server's own check is an ancestry test on an ltree path, which
     * this is the small, in-memory equivalent of.
     */
    fun reportsTo(
        candidate: DemoPerson,
        supervisor: DemoPerson,
    ): Boolean {
        var current: String? = candidate.supervisorCode
        // Bounded by the number of people: a cycle in the fixture would otherwise hang the app,
        // and a fixture is exactly the sort of thing someone edits without thinking about cycles.
        for (unused in people.indices) {
            val code = current ?: return false
            if (code == supervisor.code) return true
            current = byCode(code)?.supervisorCode
        }
        return false
    }
}
