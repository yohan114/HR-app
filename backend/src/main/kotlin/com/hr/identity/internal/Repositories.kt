package com.hr.identity.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Repositories for the identity module.
 *
 * None of these methods filter by `tenant_id` explicitly. That is not an oversight — PostgreSQL
 * row-level security appends the predicate to every query, and `TenantAwareDataSource` binds the
 * tenant on connection checkout. A query that "forgets" the tenant returns zero rows rather than
 * another customer's data. See ADR 0002.
 */
@Repository
interface AppUserRepository : JpaRepository<AppUser, UUID> {
    @Query("SELECT u FROM AppUser u WHERE lower(u.username) = lower(:username)")
    fun findByUsername(
        @Param("username") username: String,
    ): AppUser?

    @Query("SELECT u FROM AppUser u WHERE lower(u.email) = lower(:email)")
    fun findByEmail(
        @Param("email") email: String,
    ): AppUser?

    /**
     * Resolves a login identifier that may be either a username or an email address.
     *
     * Users do not reliably remember which one they registered with, and forcing them to choose is
     * needless friction on the single most-used screen in the product.
     */
    @Query(
        """
        SELECT u FROM AppUser u
        WHERE lower(u.username) = lower(:identifier)
           OR lower(u.email) = lower(:identifier)
        """,
    )
    fun findByUsernameOrEmail(
        @Param("identifier") identifier: String,
    ): AppUser?
}

@Repository
interface UserDeviceRepository : JpaRepository<UserDevice, UUID> {
    fun findByUserIdAndDeviceId(
        userId: UUID,
        deviceId: String,
    ): UserDevice?

    @Query("SELECT d FROM UserDevice d WHERE d.userId = :userId AND d.revokedAt IS NULL ORDER BY d.lastSeenAt DESC NULLS LAST")
    fun findActiveByUserId(
        @Param("userId") userId: UUID,
    ): List<UserDevice>
}

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, UUID> {
    fun findByTokenHash(tokenHash: String): RefreshTokenEntity?

    /**
     * Revokes an entire token family in one statement.
     *
     * Deliberately a bulk update rather than a load-modify-save loop: this runs on the reuse
     * detection path, where an attacker may hold a token right now, and the window between
     * detection and revocation should be as short as possible.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE RefreshTokenEntity t
           SET t.revokedAt = :now, t.revokedReason = :reason
        WHERE t.familyId = :familyId AND t.revokedAt IS NULL
        """,
    )
    fun revokeFamily(
        @Param("familyId") familyId: UUID,
        @Param("reason") reason: String,
        @Param("now") now: Instant,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE RefreshTokenEntity t
           SET t.revokedAt = :now, t.revokedReason = :reason
        WHERE t.userId = :userId AND t.revokedAt IS NULL
        """,
    )
    fun revokeAllForUser(
        @Param("userId") userId: UUID,
        @Param("reason") reason: String,
        @Param("now") now: Instant,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE RefreshTokenEntity t
           SET t.revokedAt = :now, t.revokedReason = :reason
        WHERE t.deviceId = :deviceId AND t.revokedAt IS NULL
        """,
    )
    fun revokeAllForDevice(
        @Param("deviceId") deviceId: UUID,
        @Param("reason") reason: String,
        @Param("now") now: Instant,
    ): Int
}

@Repository
interface LoginEventRepository : JpaRepository<LoginEvent, UUID>

/**
 * Effective permissions for a user.
 *
 * A native query because it spans three join tables and filters on role validity dates — the JPQL
 * equivalent is markedly less readable for no benefit. RLS still applies: these are tenant-scoped
 * tables and the connection is bound.
 */
@Repository
interface PermissionRepository : JpaRepository<AppUser, UUID> {
    @Query(
        value = """
        SELECT DISTINCT rp.permission_key
        FROM user_role ur
        JOIN role r            ON r.id = ur.role_id
        JOIN role_permission rp ON rp.role_id = r.id
        WHERE ur.user_id = :userId
          AND ur.valid_from <= CURRENT_DATE
          AND (ur.valid_to IS NULL OR ur.valid_to >= CURRENT_DATE)
        """,
        nativeQuery = true,
    )
    fun findPermissionKeysForUser(
        @Param("userId") userId: UUID,
    ): List<String>

    @Query(
        value = """
        SELECT r.key
        FROM user_role ur
        JOIN role r ON r.id = ur.role_id
        WHERE ur.user_id = :userId
          AND ur.valid_from <= CURRENT_DATE
          AND (ur.valid_to IS NULL OR ur.valid_to >= CURRENT_DATE)
        """,
        nativeQuery = true,
    )
    fun findRoleKeysForUser(
        @Param("userId") userId: UUID,
    ): List<String>
}
