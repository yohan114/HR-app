package com.hr.config.forms.internal

import com.hr.config.forms.CustomFields
import com.hr.config.forms.FormSchema
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Implements the module's published [CustomFields] port.
 *
 * Thin by design: it loads the definitions once and hands them to the pieces
 * that already know what to do with them. Its only job is to keep
 * [FieldDefinitionRepository] and [FieldDefinition] inside this module.
 */
@Service
class CustomFieldsAdapter(
    private val fieldDefinitions: FieldDefinitionRepository,
    private val validator: CustomFieldValidator,
    private val formSchemas: FormSchemaService,
) : CustomFields {
    @Transactional(readOnly = true)
    override fun validate(
        entityType: String,
        values: Map<String, Any?>,
        partial: Boolean,
    ) = validator.validate(values, definitions(entityType), partial)

    @Transactional(readOnly = true)
    override fun activeFieldKeys(entityType: String): Set<String> =
        definitions(entityType).mapTo(mutableSetOf()) { it.fieldKey }

    @Transactional(readOnly = true)
    override fun schemaFor(
        entityType: String,
        locale: String,
        visibleFields: Set<String>?,
        editableFields: Set<String>?,
    ): FormSchema =
        formSchemas.schemaFor(
            entityType = entityType,
            locale = locale,
            editableFields = editableFields,
            visibleFields = visibleFields,
        )

    private fun definitions(entityType: String): List<FieldDefinition> =
        fieldDefinitions.findByEntityTypeAndActiveOrderByPositionAsc(entityType)
}
