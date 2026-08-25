package com.hr.tenancy

import com.hr.shared.api.ApiException
import com.hr.shared.api.ErrorCode
import org.springframework.http.HttpStatus
import java.util.UUID

/**
 * The tenant the current request belongs to.
 *
 * Backed by a [ThreadLocal]. On Java 21 with virtual threads each request runs on its own
 * carrier-independent virtual thread, so a ThreadLocal is correctly scoped per request — but it
 * does **not** propagate across manually-spawned threads or reactive boundaries. Any code that
 * hands work to another thread must capture the tenant explicitly and re-establish it there;
 * [runAs] exists for that.
 *
 * See docs/03-architecture.md §3.
 */
object TenantContext {
    private val current = ThreadLocal<TenantHandle?>()

    /** The current tenant, or null outside a tenant-scoped request (e.g. health checks). */
    fun currentOrNull(): TenantHandle? = current.get()

    /**
     * The current tenant, or throws.
     *
     * A missing tenant here is always a bug or an attack — never normal traffic — so it is a 500,
     * not a 400. Loudly failing is the point: silently defaulting to "no tenant" is how
     * cross-tenant leaks happen.
     */
    fun require(): TenantHandle =
        current.get() ?: throw ApiException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorCode.TENANT_CONTEXT_MISSING,
            "No tenant bound to the current request",
        )

    fun currentId(): UUID = require().id

    fun currentIdOrNull(): UUID? = current.get()?.id

    fun set(handle: TenantHandle) {
        current.set(handle)
    }

    fun clear() {
        current.remove()
    }

    /**
     * Runs [block] bound to [handle], restoring the previous binding afterwards.
     *
     * Use this for background jobs, event handlers, and anything crossing a thread boundary.
     */
    fun <T> runAs(
        handle: TenantHandle,
        block: () -> T,
    ): T {
        val previous = current.get()
        current.set(handle)
        return try {
            block()
        } finally {
            if (previous == null) current.remove() else current.set(previous)
        }
    }

    /**
     * Runs [block] with no tenant bound.
     *
     * Only for genuinely cross-tenant work: the tenant registry itself, platform migrations, and
     * the operator console. Every call site should be obvious and rare.
     */
    fun <T> runWithoutTenant(block: () -> T): T {
        val previous = current.get()
        current.remove()
        return try {
            block()
        } finally {
            if (previous != null) current.set(previous)
        }
    }
}

/**
 * Immutable snapshot of the tenant for the current request.
 *
 * Carries the fields needed on nearly every code path so we do not re-query the registry per
 * request. Anything else is looked up from the tenant service.
 */
data class TenantHandle(
    val id: UUID,
    val code: String,
    val name: String,
    val dataRegion: String,
    val defaultCurrency: String,
    val timezone: String,
    val locale: String,
    val isolationTier: IsolationTier,
    val status: TenantStatus,
)

/**
 * How a tenant's data is physically separated.
 *
 * `SHARED` is the default and covers the overwhelming majority of customers: one schema, RLS
 * enforced by `tenant_id`. The other two exist for customers with contractual or regulatory
 * separation requirements and cost more to operate — see docs/03-architecture.md §3.
 */
enum class IsolationTier { SHARED, DEDICATED_SCHEMA, DEDICATED_DATABASE }

enum class TenantStatus { PROVISIONING, ACTIVE, SUSPENDED, ARCHIVED }
