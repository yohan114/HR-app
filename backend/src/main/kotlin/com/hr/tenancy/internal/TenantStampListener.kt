package com.hr.tenancy.internal

import com.hr.shared.persistence.TenantScopedEntity
import com.hr.tenancy.TenantContext
import jakarta.annotation.PostConstruct
import jakarta.persistence.EntityManagerFactory
import org.hibernate.event.service.spi.EventListenerRegistry
import org.hibernate.event.spi.EventType
import org.hibernate.event.spi.PreInsertEvent
import org.hibernate.event.spi.PreInsertEventListener
import org.hibernate.internal.SessionFactoryImpl
import org.springframework.stereotype.Component

/**
 * Stamps `tenant_id` onto every tenant-scoped entity as it is inserted.
 *
 * [TenantScopedEntity] has always documented this as automatic. It was not: nothing populated the
 * column, so every insert wrote NULL and row-level security rejected it. Found on the first run
 * against a real database, where signing in failed because `user_device` could not be written.
 *
 * ## Why a Hibernate listener rather than `@PrePersist`
 *
 * `TenantScopedEntity` lives in the `shared` module, and `tenancy` already depends on `shared`.
 * A `@PrePersist` reading [TenantContext] would therefore point `shared` at `tenancy` and close a
 * module cycle, which `ModuleStructureTest` correctly refuses. A listener registered from the
 * tenancy module can see both without inverting anything.
 *
 * ## Why the state array is written as well as the field
 *
 * By `PRE_INSERT` Hibernate has already flattened the entity into `event.state`, and that array —
 * not the object — is what becomes the SQL parameters. Setting only the property leaves the insert
 * unchanged, which looks like the fix working right up until the constraint fires.
 *
 * Defence in depth, not the control itself: RLS is the boundary. This exists so that a service
 * which forgets to set the tenant produces a correct row instead of a failed one, and so the
 * documented contract is finally true.
 */
@Component
class TenantStampListener(
    private val entityManagerFactory: EntityManagerFactory,
) : PreInsertEventListener {
    @PostConstruct
    fun register() {
        entityManagerFactory
            .unwrap(SessionFactoryImpl::class.java)
            .serviceRegistry
            .requireService(EventListenerRegistry::class.java)
            .appendListeners(EventType.PRE_INSERT, this)
    }

    override fun onPreInsert(event: PreInsertEvent): Boolean {
        val entity = event.entity
        if (entity !is TenantScopedEntity || entity.tenantId != null) return false

        // No bound tenant means genuinely cross-tenant work — the tenant registry, a migration,
        // the operator console. Leaving the column NULL lets the NOT NULL constraint object,
        // which is the right outcome: inventing a tenant here would write the row into whichever
        // customer happened to be in context.
        val tenantId = TenantContext.currentIdOrNull() ?: return false

        entity.tenantId = tenantId
        val index = event.persister.entityMetamodel.propertyNames.indexOf(TENANT_ID_PROPERTY)
        if (index >= 0) event.state[index] = tenantId

        return false
    }

    private companion object {
        const val TENANT_ID_PROPERTY = "tenantId"
    }
}
