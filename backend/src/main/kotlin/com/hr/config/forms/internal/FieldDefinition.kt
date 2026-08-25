package com.hr.config.forms.internal

import com.hr.shared.persistence.TenantScopedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * A tenant-defined field.
 *
 * Values live in the owning entity's `custom_fields` JSONB column; this
 * describes what is allowed there and how to render it.
 */
@Entity
@Table(name = "field_definition")
class FieldDefinition(
    @Column(name = "entity_type", nullable = false, length = 64)
    var entityType: String,
    /**
     * Must be a legal identifier in Kotlin, Swift and TypeScript — it becomes
     * one in generated client code — and must not shadow a built-in column.
     * Both enforced by constraint and trigger in V7.
     */
    @Column(name = "field_key", nullable = false, length = 64)
    var fieldKey: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 32)
    var dataType: CustomFieldType,
) : TenantScopedEntity() {
    /** Per-locale labels. We ship in six languages. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "label_i18n", columnDefinition = "jsonb", nullable = false)
    var labelI18n: MutableMap<String, String> = mutableMapOf()

    @Column(name = "help_text")
    var helpText: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation", columnDefinition = "jsonb", nullable = false)
    var validation: MutableMap<String, Any?> = mutableMapOf()

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", columnDefinition = "jsonb")
    var options: MutableList<MutableMap<String, Any?>>? = null

    @Column(name = "section", nullable = false, length = 64)
    var section: String = "other"

    @Column(name = "position", nullable = false)
    var position: Int = 0

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permissions", columnDefinition = "jsonb", nullable = false)
    var permissions: MutableMap<String, Any?> = mutableMapOf()

    @Column(name = "active", nullable = false)
    var active: Boolean = true

    /**
     * The label for a locale, falling back through the request locale, then
     * English, then the field key.
     *
     * Falling back to the key rather than an empty string is deliberate: a form
     * with a blank label is unusable, whereas one showing `tshirtSize` is ugly
     * but tells the user — and whoever they complain to — exactly what is
     * missing.
     */
    fun label(locale: String): String =
        labelI18n[locale]
            ?: labelI18n[locale.substringBefore('-')]
            ?: labelI18n["en"]
            ?: fieldKey
}

/**
 * Storage-level types.
 *
 * Distinct from [com.hr.config.forms.FieldType], which is a *rendering*
 * concern. The two differ deliberately: `EMAIL` and `PHONE` are rendered
 * differently but stored as text, and collapsing them would mean either
 * losing the keyboard hint on mobile or inventing storage types that carry no
 * storage meaning.
 */
enum class CustomFieldType {
    TEXT,
    NUMBER,
    DROPDOWN,
    MULTI_SELECT,
    DATE,
    RADIO,
    CHECKBOX,
    ATTACHMENT,
    EMPLOYEE,
}

@Repository
interface FieldDefinitionRepository : JpaRepository<FieldDefinition, UUID> {
    fun findByEntityTypeAndActiveOrderByPositionAsc(
        entityType: String,
        active: Boolean = true,
    ): List<FieldDefinition>

    fun findByEntityTypeAndFieldKey(
        entityType: String,
        fieldKey: String,
    ): FieldDefinition?
}
