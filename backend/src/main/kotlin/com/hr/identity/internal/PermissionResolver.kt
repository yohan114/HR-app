package com.hr.identity.internal

import com.hr.tenancy.TenantContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves a user's effective permission keys.
 *
 * Called on every authenticated request — the JWT carries roles, and this turns roles into the
 * granted authorities Spring Security checks. So it must not be a database round trip per request.
 *
 * ## Cache design
 *
 * A short TTL (60s) rather than explicit invalidation only. Explicit invalidation is also wired up
 * (see [evictUser]) and handles the common case immediately; the TTL is the backstop for the cases
 * that are easy to miss — a role edited in one application instance, a permission granted by a
 * direct database change during support work, a nightly job adjusting role assignments.
 *
 * Sixty seconds is a deliberate trade-off: it bounds how long a *revoked* permission can linger.
 * For immediate revocation of a whole session we revoke the refresh token family instead, which
 * takes effect within the 15-minute access token window at worst — and instantly for anything
 * that re-authenticates.
 */
@Service
class PermissionResolver(
    private val permissionRepository: PermissionRepository,
) {
    private val cache = ConcurrentHashMap<CacheKey, CachedPermissions>()

    /**
     * Effective permission keys for a user.
     *
     * Keyed by tenant *and* user. The same person may hold accounts in more than one tenant, and
     * caching by user id alone would let one tenant's permissions answer for another's — a
     * cross-tenant authorisation bug that RLS would not catch, because it happens in memory.
     */
    @Transactional(readOnly = true)
    fun permissionsFor(userId: UUID): Set<String> {
        val key = CacheKey(TenantContext.currentId(), userId)
        val cached = cache[key]
        if (cached != null && cached.isFresh) return cached.permissions

        val permissions = permissionRepository.findPermissionKeysForUser(userId).toSet()
        cache[key] = CachedPermissions(permissions, Instant.now())
        return permissions
    }

    @Transactional(readOnly = true)
    fun rolesFor(userId: UUID): List<String> = permissionRepository.findRoleKeysForUser(userId)

    fun evictUser(userId: UUID) {
        cache.remove(CacheKey(TenantContext.currentId(), userId))
    }

    fun evictAll() = cache.clear()

    private data class CacheKey(
        val tenantId: UUID,
        val userId: UUID,
    )

    private class CachedPermissions(
        val permissions: Set<String>,
        val loadedAt: Instant,
    ) {
        val isFresh: Boolean
            get() = Duration.between(loadedAt, Instant.now()) < TTL
    }

    private companion object {
        val TTL: Duration = Duration.ofSeconds(60)
    }
}
