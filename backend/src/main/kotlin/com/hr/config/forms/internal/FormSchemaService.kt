package com.hr.config.forms.internal

import com.hr.config.forms.FieldOption
import com.hr.config.forms.FieldType
import com.hr.config.forms.FieldValidation
import com.hr.config.forms.FormField
import com.hr.config.forms.FormSchema
import com.hr.config.forms.FormSection
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Assembles the form schema a client renders.
 *
 * Built-in fields and tenant-defined fields are merged into one ordered list of
 * sections. The alternative — a hardcoded form with a "custom fields" lump at
 * the bottom — produces a visibly second-class experience: tenant fields cannot
 * be interleaved with the built-in ones they relate to, and a customer's
 * mandatory field sits below the save button.
 */
@Service
class FormSchemaService(
    private val fieldDefinitions: FieldDefinitionRepository,
) {
    @Transactional(readOnly = true)
    fun schemaFor(
        entityType: String,
        locale: String = "en",
        editableFields: Set<String>? = null,
        visibleFields: Set<String>? = null,
    ): FormSchema {
        val builtIn = BUILT_IN_FORMS[entityType].orEmpty()
        val custom = fieldDefinitions.findByEntityTypeAndActiveOrderByPositionAsc(entityType)

        val customBySection = custom.groupBy { it.section }

        val sections =
            (builtIn.map { it.key } + customBySection.keys)
                .distinct()
                .mapNotNull { sectionKey ->
                    val builtInSection = builtIn.firstOrNull { it.key == sectionKey }

                    val fields =
                        buildList {
                            builtInSection?.fields?.let(::addAll)
                            customBySection[sectionKey]?.forEach { add(it.toFormField(locale)) }
                        }
                            // Fields the caller may not see are absent, not
                            // flagged. A client cannot leak what it never
                            // received.
                            //
                            // Custom fields are filtered on the same terms as
                            // built-in ones. Exempting them would defeat the
                            // check for exactly the fields a tenant is most
                            // likely to put something sensitive in — a
                            // "Disciplinary notes" field must be restrictable.
                            // Callers therefore have to include custom keys in
                            // `visibleFields`; `CustomFields.activeFieldKeys`
                            // exists to make that easy to get right.
                            .filter { visibleFields == null || it.key in visibleFields }
                            .map { field ->
                                if (editableFields == null || field.key in editableFields) {
                                    field
                                } else {
                                    field.copy(editable = false)
                                }
                            }

                    // A section whose fields are all hidden is not rendered as
                    // an empty heading.
                    if (fields.isEmpty()) {
                        null
                    } else {
                        FormSection(
                            key = sectionKey,
                            label = builtInSection?.label ?: sectionKey.replaceFirstChar(Char::uppercase),
                            fields = fields,
                        )
                    }
                }

        return FormSchema(
            entityType = entityType,
            // Derived from the definitions, so a client can compare it against
            // what it last rendered and skip rebuilding an unchanged form.
            version = schemaVersion(custom),
            sections = sections,
        )
    }

    /**
     * A stable hash of the custom definitions.
     *
     * Deliberately not a timestamp: two servers must produce the same version
     * for the same configuration, or a client behind a load balancer would see
     * the schema "change" on every other request.
     */
    private fun schemaVersion(definitions: List<FieldDefinition>): String {
        if (definitions.isEmpty()) return "base"
        val fingerprint =
            definitions
                .sortedBy { it.fieldKey }
                .joinToString("|") { "${it.fieldKey}:${it.dataType}:${it.position}:${it.version}" }
        return Integer.toHexString(fingerprint.hashCode())
    }

    private fun FieldDefinition.toFormField(locale: String): FormField =
        FormField(
            key = fieldKey,
            label = label(locale),
            type = dataType.toRenderType(),
            required = isRequired(),
            helpText = helpText,
            validation = toFieldValidation(),
            options =
                options?.mapNotNull { option ->
                    val value = option["value"] as? String ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val labels = option["label"] as? Map<String, String>
                    FieldOption(
                        value = value,
                        label = labels?.get(locale) ?: labels?.get("en") ?: option["label"] as? String ?: value,
                    )
                },
            custom = true,
        )

    private fun FieldDefinition.toFieldValidation(): FieldValidation? {
        val validation =
            FieldValidation(
                minLength = intRule("minLength"),
                maxLength = intRule("maxLength"),
                min = decimalRule("min"),
                max = decimalRule("max"),
                pattern = stringRule("pattern"),
                patternMessage = stringRule("patternMessage"),
            )
        // Omit the object entirely when there is nothing in it, rather than
        // sending a shape full of nulls to every client.
        return validation.takeIf {
            it.minLength != null || it.maxLength != null || it.min != null ||
                it.max != null || it.pattern != null
        }
    }

    private fun CustomFieldType.toRenderType(): FieldType =
        when (this) {
            CustomFieldType.TEXT -> FieldType.TEXT
            CustomFieldType.NUMBER -> FieldType.NUMBER
            CustomFieldType.DROPDOWN -> FieldType.DROPDOWN
            CustomFieldType.MULTI_SELECT -> FieldType.MULTI_SELECT
            CustomFieldType.DATE -> FieldType.DATE
            CustomFieldType.RADIO -> FieldType.RADIO
            CustomFieldType.CHECKBOX -> FieldType.CHECKBOX
            CustomFieldType.ATTACHMENT -> FieldType.ATTACHMENT
            CustomFieldType.EMPLOYEE -> FieldType.EMPLOYEE
        }

    private data class BuiltInSection(
        val key: String,
        val label: String,
        val fields: List<FormField>,
    )

    private companion object {
        /**
         * The built-in employee form.
         *
         * Declared here rather than reflected from the entity: the order,
         * grouping and labels are product decisions, not a consequence of
         * field declaration order in a Kotlin file. Reflection would also
         * silently expose a new column the moment someone added one.
         *
         * `REFERENCE` fields name their taxonomy instead of inlining options —
         * the client already caches those, and a nationality list is several
         * hundred entries.
         */
        val BUILT_IN_FORMS: Map<String, List<BuiltInSection>> =
            mapOf(
                "employee" to
                    listOf(
                        BuiltInSection(
                            key = "personal",
                            label = "Personal",
                            fields =
                                listOf(
                                    FormField("firstName", "First name", FieldType.TEXT, required = true,
                                        validation = FieldValidation(maxLength = 128)),
                                    FormField("middleName", "Middle name", FieldType.TEXT,
                                        validation = FieldValidation(maxLength = 128)),
                                    FormField("lastName", "Last name", FieldType.TEXT, required = true,
                                        validation = FieldValidation(maxLength = 128)),
                                    FormField(
                                        "displayName", "Display name", FieldType.TEXT, required = true,
                                        helpText = "How this person's name is shown throughout the app.",
                                    ),
                                    FormField("preferredName", "Preferred name", FieldType.TEXT),
                                    FormField("dateOfBirth", "Date of birth", FieldType.DATE),
                                    FormField("genderTypeId", "Gender", FieldType.REFERENCE, referenceTable = "gender-type"),
                                    FormField("maritalStatusId", "Marital status", FieldType.REFERENCE, referenceTable = "marital-status"),
                                    FormField("nationalityId", "Nationality", FieldType.REFERENCE, referenceTable = "nationality"),
                                    FormField("bloodGroupId", "Blood group", FieldType.REFERENCE, referenceTable = "blood-group"),
                                ),
                        ),
                        BuiltInSection(
                            key = "contact",
                            label = "Contact",
                            fields =
                                listOf(
                                    FormField("workEmail", "Work email", FieldType.EMAIL),
                                    FormField("personalEmail", "Personal email", FieldType.EMAIL),
                                    FormField("mobile", "Mobile", FieldType.PHONE),
                                    FormField("workPhone", "Work phone", FieldType.PHONE),
                                ),
                        ),
                        BuiltInSection(
                            key = "employment",
                            label = "Employment",
                            fields =
                                listOf(
                                    FormField("employeeCode", "Employee code", FieldType.TEXT, required = true),
                                    FormField("joinDate", "Join date", FieldType.DATE, required = true),
                                    FormField("confirmationDate", "Confirmation date", FieldType.DATE),
                                    FormField("employmentTypeId", "Employment type", FieldType.REFERENCE, referenceTable = "employment-type"),
                                    FormField("employeeCategoryId", "Category", FieldType.REFERENCE, referenceTable = "employee-category"),
                                ),
                        ),
                        BuiltInSection(
                            key = "workstation",
                            label = "Workstation",
                            fields =
                                listOf(
                                    FormField("departmentId", "Department", FieldType.REFERENCE, referenceTable = "department"),
                                    FormField("designationId", "Designation", FieldType.REFERENCE, referenceTable = "designation"),
                                    FormField("locationId", "Location", FieldType.REFERENCE, referenceTable = "location"),
                                    FormField(
                                        "supervisorId", "Reports to", FieldType.EMPLOYEE,
                                        helpText = "The solid reporting line. Approvals route to this person.",
                                    ),
                                ),
                        ),
                    ),
            )
    }
}
