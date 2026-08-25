package com.hr.employee.internal

import com.hr.config.forms.FormSchema
import com.hr.identity.Caller
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.Locale
import java.util.UUID

/**
 * Employee profiles.
 *
 * No `@PreAuthorize` here, deliberately. Every authenticated employee may call
 * these endpoints — for their own record — so a blanket permission check would
 * either lock out self-service or be so weak it authorises nothing. The real
 * check is per-record and lives in [EmployeeService], where it has the record in
 * hand to compare against.
 *
 * The payload is a map rather than a fixed DTO because its shape depends on the
 * caller: fields they may not see are absent, and tenant-defined fields appear
 * under `customFields`. A DTO would have to declare every field nullable and
 * would lose the distinction between "absent because forbidden" and "null
 * because unset" — which is exactly the distinction the field permissions exist
 * to draw.
 */
@RestController
@RequestMapping("/v1/employees")
class EmployeeController(
    private val employeeService: EmployeeService,
) {
    @GetMapping("/{id}")
    fun profile(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): Map<String, Any?> = employeeService.profile(Caller.from(jwt), id)

    /**
     * The caller's own profile.
     *
     * A separate path rather than making the client substitute its own id: the
     * app opens this on launch, before it necessarily knows the employee id,
     * and `/me` is cacheable per token in a way that `/{id}` is not.
     */
    @GetMapping("/me")
    fun ownProfile(
        @AuthenticationPrincipal jwt: Jwt,
    ): Map<String, Any?> {
        val caller = Caller.from(jwt)
        val employeeId =
            caller.employeeId
                ?: throw com.hr.shared.api.NotFoundException(
                    code = "NO_EMPLOYEE_RECORD",
                    message = "This account is not linked to an employee record",
                )
        return employeeService.profile(caller, employeeId)
    }

    @GetMapping("/{id}/form")
    fun editForm(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader(name = "Accept-Language", required = false) acceptLanguage: String?,
    ): FormSchema = employeeService.editForm(Caller.from(jwt), id, resolveLocale(acceptLanguage))

    /**
     * Partial update.
     *
     * PATCH rather than PUT because the caller may not be able to read every
     * field: a PUT means "here is the whole record", which someone who cannot
     * see the sensitive half is not in a position to send. They would have to
     * echo back fields they never received, and the ones they omitted would be
     * cleared.
     */
    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestBody updates: Map<String, Any?>,
        @AuthenticationPrincipal jwt: Jwt,
        @RequestHeader(name = "If-Match", required = false) ifMatch: String?,
    ): Map<String, Any?> =
        employeeService.update(
            caller = Caller.from(jwt),
            employeeId = id,
            updates = updates,
            expectedVersion = parseVersion(ifMatch),
        )

    /**
     * `If-Match: "7"` — a weak ETag carrying the row version.
     *
     * An unparseable value is treated as absent rather than rejected. It means
     * the client is not participating in optimistic locking, which is the same
     * situation as not sending the header, and failing the request would break
     * clients over a header they got slightly wrong.
     */
    private fun parseVersion(ifMatch: String?): Long? = ifMatch?.trim()?.trim('"', 'W', '/')?.toLongOrNull()

    private fun resolveLocale(acceptLanguage: String?): String =
        acceptLanguage
            ?.let { runCatching { Locale.LanguageRange.parse(it) }.getOrNull() }
            ?.firstOrNull()
            ?.range
            ?.takeIf { it.isNotBlank() && it != "*" }
            ?: "en"
}
