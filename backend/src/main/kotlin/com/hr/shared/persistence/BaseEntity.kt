package com.hr.shared.persistence

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.Version
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

/**
 * Base class for all persistent entities.
 *
 * Ids are assigned in application code (UUIDv7) rather than by the database. That is deliberate:
 * the mobile clients generate ids offline for records that do not exist server-side yet, and the
 * outbox needs a stable identity from the moment the user taps, not from the moment the row is
 * finally inserted.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID = Uuid7.generate()
        protected set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
        protected set

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    var createdBy: UUID? = null
        protected set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null
        protected set

    @LastModifiedBy
    @Column(name = "updated_by")
    var updatedBy: UUID? = null
        protected set

    /** Optimistic locking. A stale write surfaces to the client as `STALE_VERSION` (409). */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        protected set

    @PrePersist
    fun ensureId() {
        // Defensive: JPA may instantiate via the no-arg constructor path in some flows.
        if (id.version() != 7) id = Uuid7.generate()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BaseEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "${this::class.simpleName}(id=$id)"
}

/**
 * Base class for entities that belong to a tenant — which is almost all of them.
 *
 * `tenantId` is populated automatically from [com.hr.tenancy.TenantContext] on persist. It is
 * never settable from a request payload: a client that sends a `tenantId` is either confused or
 * hostile, and in both cases we ignore it.
 *
 * Note this is defence in depth, not the primary control. Postgres row-level security is the
 * real boundary (see `V1__platform_tenancy.sql`); this class exists so that a forgotten filter
 * in application code produces an empty result rather than a leak.
 */
@MappedSuperclass
abstract class TenantScopedEntity : BaseEntity() {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    var tenantId: UUID? = null
        internal set
}
