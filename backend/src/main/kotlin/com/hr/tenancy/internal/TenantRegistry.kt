package com.hr.tenancy.internal

import com.hr.tenancy.TenantContext
import com.hr.tenancy.TenantHandle
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Repository
interface TenantJpaRepository : JpaRepository<Tenant, UUID> {
    fun findByCode(code: String): Tenant?
}

/**
 * Read-through cache over the tenant table.
 *
 * Every single request resolves a tenant, so this lookup is on the hottest path in the system —
 * it must not become a database round trip per request. Tenant records change roughly never, so
 * an in-process cache is appropriate.
 *
 * The cache is intentionally simple (a `ConcurrentHashMap`, no TTL) because entries are
 * explicitly invalidated on write. If this grows to thousands of tenants across many instances,
 * replace it with a Redis-backed cache plus a pub/sub invalidation channel — but not before.
 *
 * All reads run outside tenant context: the registry itself is cross-tenant by definition.
 */
@Service
class TenantRegistry(
    private val repository: TenantJpaRepository,
) {
    private val byId = ConcurrentHashMap<UUID, TenantHandle>()
    private val byCode = ConcurrentHashMap<String, TenantHandle>()

    @Transactional(readOnly = true)
    fun findById(id: UUID): TenantHandle? =
        byId[id] ?: TenantContext.runWithoutTenant {
            repository.findById(id).orElse(null)?.toHandle()?.also(::cache)
        }

    @Transactional(readOnly = true)
    fun findByCode(code: String): TenantHandle? {
        val normalised = code.lowercase()
        return byCode[normalised] ?: TenantContext.runWithoutTenant {
            repository.findByCode(normalised)?.toHandle()?.also(::cache)
        }
    }

    /** Call after any mutation to a tenant record. */
    fun evict(id: UUID) {
        byId.remove(id)?.let { byCode.remove(it.code) }
    }

    fun evictAll() {
        byId.clear()
        byCode.clear()
    }

    private fun cache(handle: TenantHandle) {
        byId[handle.id] = handle
        byCode[handle.code] = handle
    }
}
