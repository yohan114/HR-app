package com.hr.tenancy.internal

import com.hr.tenancy.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.jdbc.datasource.DelegatingDataSource
import java.sql.Connection
import javax.sql.DataSource

/**
 * Binds the current tenant to every database connection so that PostgreSQL row-level security
 * can enforce isolation.
 *
 * ## Why this exists
 *
 * The RLS policies in `V1__platform_tenancy.sql` read `current_setting('app.tenant_id', true)`.
 * That setting has to be established on the *connection* before any query runs. Application code
 * must not be responsible for remembering to do it — that is exactly the kind of thing that gets
 * forgotten once and leaks data forever. So we do it here, on every checkout, unconditionally.
 *
 * ## Why every checkout, not just when a tenant is present
 *
 * Connections come from a pool and are reused across requests belonging to different tenants. If
 * we only set the value when a tenant is bound, a connection could retain the *previous*
 * request's tenant id and serve another tenant's rows. So the value is always written: either to
 * the current tenant id, or explicitly reset to empty.
 *
 * An empty setting makes `current_setting('app.tenant_id', true)::uuid` evaluate to NULL, every
 * policy predicate evaluate to NULL, and every query return zero rows. Failing closed is the
 * correct default.
 *
 * ## Bypass
 *
 * Genuinely cross-tenant work (the tenant registry, migrations, the operator console) runs inside
 * [TenantContext.runWithoutTenant] and uses a database role that is exempt from the policies.
 * See `app_platform` in the migration.
 */
class TenantAwareDataSource(
    delegate: DataSource,
) : DelegatingDataSource(delegate) {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun getConnection(): Connection = super.getConnection().also(::bindTenant)

    override fun getConnection(
        username: String,
        password: String,
    ): Connection = super.getConnection(username, password).also(::bindTenant)

    private fun bindTenant(connection: Connection) {
        val tenantId = TenantContext.currentIdOrNull()
        try {
            // `SET` does not accept bind parameters, but the `set_config()` function does. Using
            // it means the tenant id travels as a bound value and can never be parsed as SQL,
            // regardless of what ends up in the context. is_local=false so the setting persists
            // for the session — i.e. until this pooled connection is checked out and rebound.
            connection.prepareStatement("SELECT set_config('app.tenant_id', ?, false)").use { statement ->
                statement.setString(1, tenantId?.toString() ?: "")
                statement.execute()
            }
        } catch (e: Exception) {
            // If we cannot establish the tenant binding we must not hand back a usable
            // connection — an unbound connection under a bypass role would see everything.
            runCatching { connection.close() }
            log.error("Failed to bind tenant {} to connection", tenantId, e)
            throw IllegalStateException("Unable to establish tenant context on database connection", e)
        }
    }
}
