package com.hr.identity.internal

import com.hr.tenancy.IsolationTier
import com.hr.tenancy.TenantContext
import com.hr.tenancy.TenantHandle
import com.hr.tenancy.TenantStatus
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.util.UUID

/**
 * Creates a demo tenant, an organisation, a workforce and three sign-in-able
 * accounts on startup.
 *
 * **Local profile only.** Guarded by `@Profile("local")` rather than a
 * configuration flag, because a flag can be set by accident in a deployed
 * environment and this creates accounts with known passwords. There is no
 * combination of environment variables that turns this on in staging or
 * production.
 *
 * ## Why it seeds a whole workforce rather than one login
 *
 * It used to create a tenant and a single ADMIN user with no employee record.
 * That is enough to get past the login screen and not enough to exercise
 * anything behind it. Three consequences, all of which cost real time:
 *
 * - **Every screen demos empty.** The directory, the org chart and the
 *   milestone cards all render as blank states, which is indistinguishable
 *   from broken.
 * - **The `employeeId == null` branch was the only path anyone ran locally.**
 *   Self-service, "my team", and the whole `subjectIsSelf` half of field
 *   permissions were unreachable in development.
 * - **Field permissions could not be demonstrated at all.** Showing that one
 *   colleague cannot see another's date of birth requires two people and two
 *   accounts. With one account there is nothing to show.
 *
 * ## Dates are relative to today, deliberately
 *
 * Birthdays and work anniversaries are computed from [LocalDate.now] so that
 * something always lands today and something always lands later this week. Seed
 * data with fixed dates makes the milestone cards correct and empty for most of
 * the year, which is the same demo problem in slower motion.
 */
@Configuration
@Profile("local")
class LocalDemoSeeder {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun seedLocalDemoData(
        jdbc: JdbcTemplate,
        transactionTemplate: TransactionTemplate,
        passwordEncoder: PasswordEncoder,
    ) = ApplicationRunner {
        transactionTemplate.execute {
            // The tenant registry is not tenant-scoped, so this runs unbound.
            val tenantId =
                TenantContext.runWithoutTenant {
                    jdbc.query(
                        "SELECT id FROM tenant WHERE code = ?",
                        { rs, _ -> rs.getObject("id", UUID::class.java) },
                        DEMO_TENANT_CODE,
                    ).firstOrNull() ?: createTenant(jdbc)
                }

            // Everything below is tenant-scoped, so it must run bound or RLS returns nothing.
            TenantContext.runAs(handleFor(tenantId)) {
                jdbc.update("SELECT provision_tenant_defaults(?)", tenantId)

                if (alreadySeeded(jdbc)) {
                    log.info("Demo data already present for tenant '{}'", DEMO_TENANT_CODE)
                    return@runAs
                }

                val org = seedOrganisation(jdbc, tenantId)
                val employees = seedEmployees(jdbc, tenantId, org)
                seedUsers(jdbc, passwordEncoder, tenantId, employees)
                announce()
            }
        }
    }

    /**
     * Keyed on the admin account rather than on any employee row.
     *
     * The admin user is the last thing created, so its presence means the whole
     * seed completed. Checking for an employee instead would let a run that
     * failed midway look complete on the next start.
     */
    private fun alreadySeeded(jdbc: JdbcTemplate): Boolean =
        jdbc.queryForObject("SELECT count(*) FROM app_user WHERE lower(username) = ?", Int::class.java, ADMIN_USERNAME)
            ?.let { it > 0 } ?: false

    private fun createTenant(jdbc: JdbcTemplate): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO tenant (id, code, name, legal_name, country_code, timezone,
                                default_currency, locale, status, email_domains)
            VALUES (?, ?, 'Demo Company', 'Demo Company (Pvt) Ltd', 'LK', 'Asia/Colombo',
                    'LKR', 'en', 'ACTIVE', ARRAY['demo.local'])
            """.trimIndent(),
            id,
            DEMO_TENANT_CODE,
        )
        log.info("Created demo tenant '{}' ({})", DEMO_TENANT_CODE, id)
        return id
    }

    // ------------------------------------------------------------------------
    // Organisation
    // ------------------------------------------------------------------------

    private class Organisation(
        val companyId: UUID,
        val locationId: UUID,
        val departments: Map<String, UUID>,
        val designations: Map<String, UUID>,
    )

    private fun seedOrganisation(
        jdbc: JdbcTemplate,
        tenantId: UUID,
    ): Organisation {
        val companyId = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO company (id, tenant_id, code, name, legal_name, country_code, currency)
            VALUES (?, ?, 'DEMO', 'Demo Company', 'Demo Company (Pvt) Ltd', 'LK', 'LKR')
            """.trimIndent(),
            companyId,
            tenantId,
        )

        val locationId = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO location (id, tenant_id, company_id, code, name, address, timezone,
                                  geo_lat, geo_lng, geofence_radius_m)
            VALUES (?, ?, ?, 'CMB', 'Colombo Head Office',
                    '{"line1":"42 Galle Road","city":"Colombo","postcode":"00300"}'::jsonb,
                    'Asia/Colombo', 6.927079, 79.861244, 150)
            """.trimIndent(),
            locationId,
            tenantId,
            companyId,
        )

        val departments =
            listOf("ENG" to "Engineering", "HR" to "People & Culture", "FIN" to "Finance")
                .associate { (code, name) ->
                    val id = UUID.randomUUID()
                    jdbc.update(
                        """
                        INSERT INTO department (id, tenant_id, company_id, code, name)
                        VALUES (?, ?, ?, ?, ?)
                        """.trimIndent(),
                        id, tenantId, companyId, code, name,
                    )
                    code to id
                }

        val designations =
            listOf(
                "CEO" to "Chief Executive Officer",
                "ENG_MGR" to "Engineering Manager",
                "SE" to "Software Engineer",
                "HR_MGR" to "HR Manager",
                "ACC" to "Accountant",
            ).associate { (code, name) ->
                val id = UUID.randomUUID()
                jdbc.update(
                    """
                    INSERT INTO designation (id, tenant_id, code, name)
                    VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                    id, tenantId, code, name,
                )
                code to id
            }

        log.info("Seeded organisation: 1 company, 1 location, {} departments", departments.size)
        return Organisation(companyId, locationId, departments, designations)
    }

    // ------------------------------------------------------------------------
    // Workforce
    // ------------------------------------------------------------------------

    /**
     * @param birthdayIn days from today, or null for no date of birth
     * @param joinedYearsAgo whole years, so the anniversary lands on [joinedDaysOffset] from today
     */
    internal class Person(
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
    )

    /**
     * Nine people, three levels deep.
     *
     * The names are Sinhala, Tamil and Burgher, matching the demo tenant's
     * country. Not decoration: `V6` indexes the search vector with the `simple`
     * dictionary rather than `english` precisely so these tokenise correctly,
     * and seed data of Anglophone names would leave that decision unexercised.
     *
     * Three levels rather than two because "my team" means the whole subtree —
     * a department head who cannot see someone two levels down is the bug the
     * ltree hierarchy exists to prevent, and a two-level tree cannot show it.
     */
    internal val workforce =
        listOf(
            Person("E001", "Nimali", "Wickramasinghe", "Nimali Wickramasinghe", "CEO", "ENG", null,
                birthdayIn = 0, joinedYearsAgo = 12, joinedDaysOffset = -40, mobile = "0771000001"),
            Person("E002", "Ruwan", "Jayasuriya", "Ruwan Jayasuriya", "ENG_MGR", "ENG", "E001",
                birthdayIn = 3, joinedYearsAgo = 5, joinedDaysOffset = 0, mobile = "0771000002"),
            Person("E003", "Priya", "Balasubramaniam", "Priya Balasubramaniam", "HR_MGR", "HR", "E001",
                birthdayIn = 21, joinedYearsAgo = 7, joinedDaysOffset = -3, mobile = "0771000003"),
            Person("E004", "Kasun", "Fernando", "Kasun Fernando", "SE", "ENG", "E002",
                birthdayIn = 1, joinedYearsAgo = 3, joinedDaysOffset = 2, mobile = "0771000004"),
            Person("E005", "Dilani", "Perera", "Dilani Perera", "SE", "ENG", "E002",
                birthdayIn = 45, joinedYearsAgo = 2, joinedDaysOffset = 5, mobile = "0771000005"),
            Person("E006", "Thivanka", "Rajapaksa", "Thivanka Rajapaksa", "SE", "ENG", "E002",
                birthdayIn = 120, joinedYearsAgo = 1, joinedDaysOffset = -60, mobile = "0771000006"),
            Person("E007", "Anusha", "Sivakumar", "Anusha Sivakumar", "ACC", "FIN", "E003",
                birthdayIn = 6, joinedYearsAgo = 4, joinedDaysOffset = -120, mobile = "0771000007"),
            Person("E008", "Malith", "Gunawardena", "Malith Gunawardena", "SE", "ENG", "E002",
                birthdayIn = null, joinedYearsAgo = 10, joinedDaysOffset = 4, mobile = "0771000008"),
            Person("E009", "Shanika", "de Silva", "Shanika de Silva", "ACC", "FIN", "E003",
                birthdayIn = 200, joinedYearsAgo = 6, joinedDaysOffset = -200, mobile = "0771000009"),
        )

    private fun seedEmployees(
        jdbc: JdbcTemplate,
        tenantId: UUID,
        org: Organisation,
    ): Map<String, UUID> {
        val today = LocalDate.now()
        val ids = workforce.associate { it.code to UUID.randomUUID() }

        // Inserted in declaration order so a supervisor always exists before the
        // people reporting to them. The hierarchy trigger cascades to reports on
        // update, so out-of-order inserts would also resolve — but relying on
        // that would make the seed depend on behaviour nothing else depends on.
        for (person in workforce) {
            jdbc.update(
                """
                INSERT INTO employee (id, tenant_id, employee_code, company_id, status,
                                      first_name, last_name, display_name, date_of_birth,
                                      join_date, work_email, mobile,
                                      department_id, designation_id, location_id, supervisor_id)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                ids.getValue(person.code),
                tenantId,
                person.code,
                org.companyId,
                person.firstName,
                person.lastName,
                person.displayName,
                person.birthdayIn?.let { birthday(today, it) },
                today.minusYears(person.joinedYearsAgo).plusDays(person.joinedDaysOffset),
                "${person.firstName.lowercase()}.${person.lastName.replace(" ", "").lowercase()}@demo.local",
                person.mobile,
                org.departments[person.department],
                org.designations[person.designation],
                org.locationId,
                person.supervisorCode?.let { ids.getValue(it) },
            )
        }

        log.info("Seeded {} employees across 3 reporting levels", workforce.size)
        return ids
    }

    /**
     * A date of birth whose anniversary falls [daysFromToday] from now.
     *
     * The age is arbitrary and varied only so the demo does not show nine
     * people born in the same year.
     *
     * 29 February is stepped back to the 28th rather than handled: a birthday
     * seeded on a leap day would disappear from the card for three years out of
     * four, which is a worse demo than being one day out on that one date.
     */
    private fun birthday(
        today: LocalDate,
        daysFromToday: Long,
    ): LocalDate {
        val target = today.plusDays(daysFromToday)
        val age = 24 + (daysFromToday % 20)
        val safe = if (target.monthValue == 2 && target.dayOfMonth == 29) target.minusDays(1) else target
        return safe.minusYears(age)
    }

    // ------------------------------------------------------------------------
    // Accounts
    // ------------------------------------------------------------------------

    /**
     * Three accounts, one per authorisation path.
     *
     * Not three copies of the same thing: each exists to make a different rule
     * visible without reading the code.
     *
     * - `admin` holds every permission, and is linked to the CEO's record so
     *   that `Caller.employeeId` is populated. An admin with no employee record
     *   silently exercises the "not a person in the org chart" branch, which is
     *   the rarer case in production and was the only case reachable locally.
     * - `manager` holds `employee.view` **without** `employee.view.all`, so
     *   whose records they can open is decided by the reporting line. They have
     *   three direct reports and see nobody else.
     * - `employee` holds only the directory permission. Everything they can do
     *   with their own record is authorised by ownership, not by a grant —
     *   which is the property that is easiest to break and hardest to notice.
     *
     * Signing in as `employee` and fetching a colleague is the fastest way to
     * see field permissions working: the date of birth is absent from the
     * payload, not null.
     */
    private fun seedUsers(
        jdbc: JdbcTemplate,
        passwordEncoder: PasswordEncoder,
        tenantId: UUID,
        employees: Map<String, UUID>,
    ) {
        val accounts =
            listOf(
                Triple(ADMIN_USERNAME, "ADMIN", "E001"),
                Triple("manager", "MANAGER", "E002"),
                Triple("employee", "EMPLOYEE", "E004"),
            )

        for ((username, roleKey, employeeCode) in accounts) {
            val userId = UUID.randomUUID()
            jdbc.update(
                """
                INSERT INTO app_user (id, tenant_id, username, email, employee_id, password_hash,
                                      password_changed_at, status, locale, timezone)
                VALUES (?, ?, ?, ?, ?, ?, now(), 'ACTIVE', 'en', 'Asia/Colombo')
                """.trimIndent(),
                userId,
                tenantId,
                username,
                "$username@demo.local",
                employees.getValue(employeeCode),
                passwordEncoder.encode(DEMO_PASSWORD),
            )

            jdbc.update(
                """
                INSERT INTO user_role (tenant_id, user_id, role_id)
                SELECT ?, ?, r.id FROM role r WHERE r.tenant_id = ? AND r.key = ?
                """.trimIndent(),
                tenantId,
                userId,
                tenantId,
                roleKey,
            )
        }

        log.info("Seeded {} demo accounts", accounts.size)
    }

    private fun announce() {
        log.warn(
            """

            ┌──────────────────────────────────────────────────────────────────────┐
            │  LOCAL DEMO DATA CREATED  (local profile only)                        │
            │                                                                       │
            │    X-Tenant-Code: {}
            │    password (all three accounts): {}
            │                                                                       │
            │    admin     — everything; linked to the CEO's record                 │
            │    manager   — employee.view without view.all; three direct reports   │
            │    employee  — directory only; self-service by ownership              │
            │                                                                       │
            │  9 employees, 3 reporting levels. Birthdays and work anniversaries    │
            │  are relative to today, so the milestone cards always have content.   │
            │                                                                       │
            │  curl -X POST http://localhost:8080/v1/auth/token \\                   │
            │    -H 'Content-Type: application/json' \\                              │
            │    -H 'X-Tenant-Code: {}' \\
            │    -d '{{"username":"employee","password":"{}",
            │         "device":{{"deviceId":"dev-1","platform":"ANDROID"}}}}'
            │                                                                       │
            │  Then compare, as `employee`:                                         │
            │    GET /v1/employees/me           → dateOfBirth present               │
            │    GET /v1/employees/{{id-of-E005}} → dateOfBirth absent, not null      │
            └──────────────────────────────────────────────────────────────────────┘
            """.trimIndent(),
            DEMO_TENANT_CODE, DEMO_PASSWORD, DEMO_TENANT_CODE, DEMO_PASSWORD,
        )
    }

    private fun handleFor(tenantId: UUID) =
        TenantHandle(
            id = tenantId,
            code = DEMO_TENANT_CODE,
            name = "Demo Company",
            dataRegion = "default",
            defaultCurrency = "LKR",
            timezone = "Asia/Colombo",
            locale = "en",
            isolationTier = IsolationTier.SHARED,
            status = TenantStatus.ACTIVE,
        )

    private companion object {
        const val DEMO_TENANT_CODE = "demo"
        const val ADMIN_USERNAME = "admin"
        const val DEMO_PASSWORD = "DemoPassw0rd!"
    }
}
