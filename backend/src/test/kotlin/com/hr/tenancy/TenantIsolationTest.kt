package com.hr.tenancy

import com.hr.support.PostgresTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import javax.sql.DataSource

/**
 * **The most important test in the codebase.**
 *
 * Everything else in this product is a feature. This is the thing that, if it breaks, ends the
 * company: one customer reading another customer's salaries, national identity numbers, or
 * disciplinary records.
 *
 * It asserts three separate layers, because any one of them can be defeated on its own:
 *
 *  1. **The connection is bound.** `TenantAwareDataSource` sets `app.tenant_id` on every checkout.
 *  2. **The database enforces it.** RLS policies filter rows regardless of what the SQL says —
 *     so a query with a forgotten `WHERE tenant_id = ?` returns nothing rather than everything.
 *  3. **The runtime role cannot bypass it.** PostgreSQL exempts table owners from RLS. If the
 *     application ever connects as the owner, layers 1 and 2 become decorative and every other
 *     assertion here would still pass. That is why `runtime role is not the table owner` exists.
 *
 * Phase 1 extends this to sweep every registered HTTP endpoint automatically (P0-QA-03).
 */
@SpringBootTest
@DisplayName("Tenant isolation")
class TenantIsolationTest : PostgresTestBase() {
    @Autowired
    private lateinit var dataSource: DataSource

    private lateinit var jdbc: JdbcTemplate

    private val tenantA = UUID.randomUUID()
    private val tenantB = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        jdbc = JdbcTemplate(dataSource)

        // Seed outside tenant context — `tenant` itself is not tenant-scoped.
        TenantContext.runWithoutTenant {
            jdbc.update(
                """
                INSERT INTO tenant (id, code, name, country_code, default_currency, status)
                VALUES (?, ?, ?, 'LK', 'LKR', 'ACTIVE')
                ON CONFLICT (id) DO NOTHING
                """.trimIndent(),
                tenantA, "alpha-${tenantA.toString().take(8)}", "Alpha Ltd",
            )
            jdbc.update(
                """
                INSERT INTO tenant (id, code, name, country_code, default_currency, status)
                VALUES (?, ?, ?, 'LK', 'LKR', 'ACTIVE')
                ON CONFLICT (id) DO NOTHING
                """.trimIndent(),
                tenantB, "beta-${tenantB.toString().take(8)}", "Beta Ltd",
            )
        }

        insertSequenceConfig(tenantA, "EMPLOYEE_CODE", "A")
        insertSequenceConfig(tenantB, "EMPLOYEE_CODE", "B")
    }

    @Test
    fun `a tenant sees only its own rows`() {
        val visibleToA = withTenant(tenantA) { jdbc.queryForList("SELECT prefix FROM sequence_config") }
        val visibleToB = withTenant(tenantB) { jdbc.queryForList("SELECT prefix FROM sequence_config") }

        assertThat(visibleToA).hasSize(1)
        assertThat(visibleToA.first()["prefix"]).isEqualTo("A")

        assertThat(visibleToB).hasSize(1)
        assertThat(visibleToB.first()["prefix"]).isEqualTo("B")
    }

    @Test
    fun `an unfiltered query still cannot cross tenants`() {
        // Note there is no WHERE clause at all. This simulates the realistic failure mode:
        // a developer writes a repository method and forgets the tenant predicate.
        val rows = withTenant(tenantA) { jdbc.queryForList("SELECT tenant_id FROM sequence_config") }

        assertThat(rows).hasSize(1)
        assertThat(rows.first()["tenant_id"]).isEqualTo(tenantA)
    }

    @Test
    fun `explicitly targeting another tenant returns nothing`() {
        // An attacker who controls the parameter cannot read across the boundary, because RLS
        // ANDs its own predicate onto the query.
        val rows =
            withTenant(tenantA) {
                jdbc.queryForList("SELECT * FROM sequence_config WHERE tenant_id = ?", tenantB)
            }

        assertThat(rows).isEmpty()
    }

    @Test
    fun `no tenant bound means no rows`() {
        // Fail closed. An unbound connection must be useless, not omniscient.
        val rows = TenantContext.runWithoutTenant { jdbc.queryForList("SELECT * FROM sequence_config") }

        assertThat(rows).isEmpty()
    }

    @Test
    fun `a tenant cannot write a row belonging to another tenant`() {
        // Guards the WITH CHECK half of the policy. USING alone would allow the insert and simply
        // hide the result — which is arguably worse than rejecting it.
        assertThatThrownBy {
            withTenant(tenantA) {
                jdbc.update(
                    """
                    INSERT INTO sequence_config (tenant_id, sequence_key, prefix, next_value)
                    VALUES (?, 'SMUGGLED', 'X', 1)
                    """.trimIndent(),
                    tenantB,
                )
            }
        }.hasMessageContaining("row-level security")

        val leaked =
            withTenant(tenantB) {
                jdbc.queryForList("SELECT * FROM sequence_config WHERE sequence_key = 'SMUGGLED'")
            }
        assertThat(leaked).isEmpty()
    }

    @Test
    fun `switching tenants on a pooled connection does not leak the previous binding`() {
        // Connections are reused. If the binding were only applied when a tenant is present,
        // a connection could retain the previous request's tenant and serve its rows.
        repeat(20) {
            val a = withTenant(tenantA) { jdbc.queryForList("SELECT prefix FROM sequence_config") }
            assertThat(a.single()["prefix"]).isEqualTo("A")

            val b = withTenant(tenantB) { jdbc.queryForList("SELECT prefix FROM sequence_config") }
            assertThat(b.single()["prefix"]).isEqualTo("B")

            val none = TenantContext.runWithoutTenant { jdbc.queryForList("SELECT prefix FROM sequence_config") }
            assertThat(none).isEmpty()
        }
    }

    @Test
    fun `runtime role is not the table owner`() {
        // PostgreSQL exempts table owners from RLS unless FORCE ROW LEVEL SECURITY is set. If the
        // application connects as the owner, every other assertion in this class passes while
        // enforcing nothing. This asserts the deployment shape, not the code.
        val currentUser = jdbc.queryForObject("SELECT current_user", String::class.java)
        val tableOwner =
            jdbc.queryForObject(
                "SELECT tableowner FROM pg_tables WHERE tablename = 'sequence_config'",
                String::class.java,
            )

        assertThat(currentUser)
            .describedAs("The application must not connect as the table owner — RLS would not apply")
            .isNotEqualTo(tableOwner)
    }

    @Test
    fun `runtime role does not hold BYPASSRLS`() {
        val bypasses =
            jdbc.queryForObject(
                "SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user",
                Boolean::class.java,
            )
        assertThat(bypasses).isFalse()
    }

    @Test
    fun `every tenant-scoped table has row level security enabled`() {
        // Catches the most likely regression by far: someone adds a table in a new migration and
        // forgets to call apply_tenant_rls(). This fails the build the moment that happens.
        val unprotected =
            TenantContext.runWithoutTenant {
                jdbc.queryForList(
                    """
                    SELECT c.relname AS table_name
                    FROM pg_class c
                    JOIN pg_namespace n ON n.oid = c.relnamespace
                    JOIN information_schema.columns col
                      ON col.table_name = c.relname AND col.table_schema = n.nspname
                    WHERE n.nspname = 'public'
                      AND c.relkind IN ('r', 'p')
                      AND col.column_name = 'tenant_id'
                      AND c.relrowsecurity = false
                    """.trimIndent(),
                    String::class.java,
                )
            }

        assertThat(unprotected)
            .describedAs("Tables with a tenant_id column but no RLS policy — call apply_tenant_rls() in the migration")
            .isEmpty()
    }

    private fun insertSequenceConfig(
        tenant: UUID,
        key: String,
        prefix: String,
    ) = withTenant(tenant) {
        jdbc.update(
            """
            INSERT INTO sequence_config (tenant_id, sequence_key, prefix, next_value)
            VALUES (?, ?, ?, 1)
            ON CONFLICT (tenant_id, sequence_key) DO UPDATE SET prefix = EXCLUDED.prefix
            """.trimIndent(),
            tenant, key, prefix,
        )
    }

    private fun <T> withTenant(
        tenantId: UUID,
        block: () -> T,
    ): T =
        TenantContext.runAs(
            TenantHandle(
                id = tenantId,
                code = "test",
                name = "Test",
                dataRegion = "default",
                defaultCurrency = "LKR",
                timezone = "Asia/Colombo",
                locale = "en",
                isolationTier = IsolationTier.SHARED,
                status = TenantStatus.ACTIVE,
            ),
            block,
        )
}
