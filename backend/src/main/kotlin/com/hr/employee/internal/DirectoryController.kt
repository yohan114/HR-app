package com.hr.employee.internal

import com.hr.shared.api.CursorPage
import com.hr.shared.api.CursorRequest
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Employee directory.
 *
 * Available to every authenticated employee — finding a colleague's extension
 * is not a privileged operation, and gating it behind a permission would mean
 * most of the workforce could not use the feature they open most often.
 *
 * The narrowness of [DirectoryEntry] is what makes that safe: the query never
 * selects salary, bank details or identity documents, so there is nothing to
 * leak. Anything more sensitive goes through the profile endpoint, which
 * applies `employee.view` plus a data-scope check plus field-level permissions.
 */
@RestController
@RequestMapping("/v1/directory")
class DirectoryController(
    private val directoryService: DirectoryService,
) {
    @GetMapping("/search")
    fun search(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) departmentId: UUID?,
        @RequestParam(required = false) locationId: UUID?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) limit: Int,
    ): CursorPage<DirectoryEntry> =
        directoryService.search(
            query = query,
            departmentId = departmentId,
            locationId = locationId,
            page = CursorRequest(cursor = cursor, limit = limit),
        )

    @GetMapping("/employees/{id}/reports")
    fun directReports(
        @PathVariable id: UUID,
    ): List<DirectoryEntry> = directoryService.directReports(id)
}
