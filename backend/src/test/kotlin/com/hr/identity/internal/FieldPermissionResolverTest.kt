package com.hr.identity.internal

import com.hr.identity.FieldAccess
import com.hr.identity.FieldAccessContext
import com.hr.tenancy.IsolationTier
import com.hr.tenancy.TenantContext
import com.hr.tenancy.TenantHandle
import com.hr.tenancy.TenantStatus
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.util.UUID

/**
 * The default access matrix.
 *
 * These tests exist because the defaults, not the configured rules, decide what
 * happens to the overwhelming majority of fields. A rule someone typed into an
 * admin screen was at least considered once; a default is what applies to every
 * field nobody has thought about, which is nearly all of them.
 *
 * Each test below states an outcome someone would complain about if it were
 * wrong — either "why can my colleague see my date of birth" or "why can't I
 * change my own phone number".
 */
@DisplayName("Field permission defaults")
class FieldPermissionResolverTest {
    private val jdbc = mockk<JdbcTemplate>()
    private val resolver = FieldPermissionResolver(jdbc)

    private val user = UUID.randomUUID()

    init {
        TenantContext.set(
            TenantHandle(
                id = UUID.randomUUID(),
                code = "test",
                name = "Test",
                dataRegion = "default",
                defaultCurrency = "LKR",
                timezone = "Asia/Colombo",
                locale = "en",
                isolationTier = IsolationTier.SHARED,
                status = TenantStatus.ACTIVE,
            ),
        )
        noExplicitGrants()
    }

    // ------------------------------------------------------------------------
    @Nested
    @DisplayName("Sensitive fields")
    inner class Sensitive {
        @Test
        fun `are hidden from a colleague with no grant`() {
            assertThat(access("dateOfBirth", ctx())).isEqualTo(FieldAccess.HIDDEN)
            assertThat(access("permanentAddress", ctx())).isEqualTo(FieldAccess.HIDDEN)
            assertThat(access("personalEmail", ctx())).isEqualTo(FieldAccess.HIDDEN)
        }

        /**
         * The one that would otherwise be missed. `employee.manage` authorises
         * maintaining records; it is not a reason to be shown someone's home
         * address, and a customer must be able to separate the two duties.
         */
        @Test
        fun `stay hidden from someone who can manage employees`() {
            assertThat(access("dateOfBirth", ctx(canManage = true))).isEqualTo(FieldAccess.HIDDEN)
            assertThat(access("permanentAddress", ctx(canManage = true))).isEqualTo(FieldAccess.HIDDEN)
        }

        @Test
        fun `are readable on your own record`() {
            assertThat(access("dateOfBirth", ctx(self = true))).isEqualTo(FieldAccess.READ)
            assertThat(access("permanentAddress", ctx(self = true))).isEqualTo(FieldAccess.READ)
        }

        /**
         * Reading your own date of birth is fine; changing it is a statutory
         * matter that belongs in a request with evidence attached.
         */
        @Test
        fun `are not writable on your own record`() {
            assertThat(access("dateOfBirth", ctx(self = true)).canWrite).isFalse()
        }
    }

    // ------------------------------------------------------------------------
    @Nested
    @DisplayName("Ordinary fields")
    inner class Ordinary {
        @Test
        fun `are readable by any colleague`() {
            assertThat(access("departmentId", ctx())).isEqualTo(FieldAccess.READ)
            assertThat(access("workEmail", ctx())).isEqualTo(FieldAccess.READ)
        }

        /**
         * The hole an allow-by-default policy would leave: without this, an
         * employee with no configured rules could rewrite their own join date,
         * which feeds gratuity and leave accrual.
         */
        @Test
        fun `are not writable just because you can read them`() {
            assertThat(access("joinDate", ctx()).canWrite).isFalse()
            assertThat(access("joinDate", ctx(self = true)).canWrite).isFalse()
            assertThat(access("departmentId", ctx(self = true)).canWrite).isFalse()
            assertThat(access("employeeCode", ctx(self = true)).canWrite).isFalse()
        }

        @Test
        fun `become writable with the manage permission`() {
            assertThat(access("joinDate", ctx(canManage = true))).isEqualTo(FieldAccess.WRITE)
            assertThat(access("departmentId", ctx(canManage = true))).isEqualTo(FieldAccess.WRITE)
        }
    }

    // ------------------------------------------------------------------------
    @Nested
    @DisplayName("Self-service")
    inner class SelfService {
        @Test
        fun `you may change your own contact details`() {
            assertThat(access("mobile", ctx(self = true))).isEqualTo(FieldAccess.WRITE)
            assertThat(access("preferredName", ctx(self = true))).isEqualTo(FieldAccess.WRITE)
            assertThat(access("photoKey", ctx(self = true))).isEqualTo(FieldAccess.WRITE)
        }

        /**
         * Both sensitive *and* self-writable. Sensitive wins for everyone else,
         * self-service wins for the owner — a personal email address is private
         * from a colleague and routine to its owner.
         */
        @Test
        fun `a sensitive field can still be self-writable`() {
            assertThat(access("personalEmail", ctx(self = true))).isEqualTo(FieldAccess.WRITE)
            assertThat(access("currentAddress", ctx(self = true))).isEqualTo(FieldAccess.WRITE)

            assertThat(access("personalEmail", ctx())).isEqualTo(FieldAccess.HIDDEN)
            assertThat(access("currentAddress", ctx())).isEqualTo(FieldAccess.HIDDEN)
        }

        @Test
        fun `self-service does not extend to someone else's record`() {
            assertThat(access("mobile", ctx(self = false)).canWrite).isFalse()
        }

        /**
         * A user account with no linked employee record — a platform operator or
         * an integration — must never be treated as the subject of the record it
         * happens to be reading.
         */
        @Test
        fun `an account with no employee record is never self`() {
            val context = FieldAccessContext(user, "employee", subjectIsSelf = false, canManage = false)
            assertThat(resolver.accessFor(context, "personalEmail")).isEqualTo(FieldAccess.HIDDEN)
        }
    }

    // ------------------------------------------------------------------------
    @Nested
    @DisplayName("Explicit grants")
    inner class Grants {
        @Test
        fun `override the sensitive default`() {
            grants("dateOfBirth" to FieldAccess.READ)
            assertThat(access("dateOfBirth", ctx())).isEqualTo(FieldAccess.READ)
        }

        @Test
        fun `can mask rather than reveal`() {
            grants("permanentAddress" to FieldAccess.MASKED)

            val access = access("permanentAddress", ctx())
            assertThat(access).isEqualTo(FieldAccess.MASKED)
            assertThat(access.canRead).isTrue()
            assertThat(access.canWrite).isFalse()
        }

        /**
         * Roles are additive everywhere else in the system, so two roles
         * granting different levels give the union. An administrator should not
         * have to reason about the interaction of every role a user holds to
         * predict what they can see.
         */
        @Test
        fun `combine across roles by taking the most permissive`() {
            grantRows(
                "dateOfBirth" to FieldAccess.READ,
                "dateOfBirth" to FieldAccess.HIDDEN,
            )
            assertThat(access("dateOfBirth", ctx())).isEqualTo(FieldAccess.READ)
        }

        /**
         * An explicit HIDDEN is still a decision someone made, so it applies to
         * a field that would otherwise be ordinary.
         */
        @Test
        fun `can restrict an ordinary field`() {
            grants("departmentId" to FieldAccess.HIDDEN)
            assertThat(access("departmentId", ctx(canManage = true))).isEqualTo(FieldAccess.HIDDEN)
        }
    }

    // ------------------------------------------------------------------------
    @Nested
    @DisplayName("Bulk queries")
    inner class Bulk {
        @Test
        fun `readableFields drops what is hidden`() {
            val fields = listOf("displayName", "departmentId", "dateOfBirth", "permanentAddress")

            assertThat(resolver.readableFields(ctx(), fields))
                .containsExactlyInAnyOrder("displayName", "departmentId")
        }

        @Test
        fun `writableFields is empty for an ordinary colleague`() {
            val fields = listOf("displayName", "departmentId", "mobile")
            assertThat(resolver.writableFields(ctx(), fields)).isEmpty()
        }

        @Test
        fun `writableFields on your own record is only the self-service set`() {
            val fields = listOf("displayName", "departmentId", "mobile", "preferredName", "joinDate")

            assertThat(resolver.writableFields(ctx(self = true), fields))
                .containsExactlyInAnyOrder("mobile", "preferredName")
        }

        /**
         * A custom field is not in ALWAYS_SENSITIVE, so it follows the ordinary
         * default. That is deliberate — a tenant field holding something
         * sensitive is restricted by configuring a rule, which is the only
         * mechanism available for a field the codebase has never heard of.
         */
        @Test
        fun `custom field keys follow the ordinary default`() {
            assertThat(access("tshirtSize", ctx())).isEqualTo(FieldAccess.READ)
            assertThat(access("tshirtSize", ctx(canManage = true))).isEqualTo(FieldAccess.WRITE)

            grants("disciplinaryNotes" to FieldAccess.HIDDEN)
            assertThat(access("disciplinaryNotes", ctx(canManage = true))).isEqualTo(FieldAccess.HIDDEN)
        }
    }

    // ------------------------------------------------------------------------

    private fun access(
        field: String,
        context: FieldAccessContext,
    ) = resolver.accessFor(context, field)

    private fun ctx(
        self: Boolean = false,
        canManage: Boolean = false,
    ) = FieldAccessContext(user, "employee", subjectIsSelf = self, canManage = canManage)

    private fun noExplicitGrants() = grantRows()

    private fun grants(vararg rows: Pair<String, FieldAccess>) = grantRows(*rows)

    /**
     * Stubs the query result. The cache is keyed by tenant, user and entity
     * type, so a fresh resolver per test is what keeps these independent —
     * hence the field initialisation rather than a shared instance.
     */
    private fun grantRows(vararg rows: Pair<String, FieldAccess>) {
        resolver.evictAll()
        every {
            jdbc.query(any<String>(), any<RowMapper<Pair<String, FieldAccess>>>(), *anyVararg())
        } returns rows.toList()
    }
}
