package com.hr.config.forms.internal

import com.hr.config.forms.FormSchema
import com.hr.shared.api.ErrorCode
import com.hr.shared.api.NotFoundException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Server-rendered form schemas.
 *
 * The endpoint that lets a tenant add a field in the admin console and have it
 * appear on Android and iOS on the next sync — no code change, no app release,
 * no store review. Without it, "we need one extra field for this customer" is a
 * two-week round trip for a text box.
 *
 * Clients cache by `version` and re-render only when it changes.
 */
@RestController
@RequestMapping("/v1/forms")
class FormSchemaController(
    private val formSchemaService: FormSchemaService,
) {
    @GetMapping("/{entityType}")
    fun schema(
        @PathVariable entityType: String,
        // Content negotiation rather than a query parameter: the client already
        // sends its locale on every request, and a second place to specify it is
        // a second place for the two to disagree.
        @RequestHeader(value = "Accept-Language", required = false) acceptLanguage: String?,
    ): FormSchema {
        if (entityType !in SUPPORTED_ENTITY_TYPES) {
            throw NotFoundException(ErrorCode.NOT_FOUND, "No form schema for entity type: $entityType")
        }

        return formSchemaService.schemaFor(
            entityType = entityType,
            locale = parseLocale(acceptLanguage),
        )
    }

    /**
     * Takes the first language tag from an Accept-Language header.
     *
     * Deliberately simple: full RFC 4647 negotiation with quality values buys
     * nothing here, because the label map falls back through region, then
     * English, then the field key anyway.
     */
    private fun parseLocale(header: String?): String =
        header
            ?.split(',')
            ?.firstOrNull()
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != "*" }
            ?: "en"

    private companion object {
        val SUPPORTED_ENTITY_TYPES = setOf("employee", "company", "location", "department", "designation")
    }
}
