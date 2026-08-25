package com.hr.organisation.internal

import com.hr.organisation.ReferenceItem
import com.hr.organisation.ReferenceTable
import com.hr.shared.api.ErrorCode
import com.hr.shared.api.NotFoundException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Reference taxonomies.
 *
 * The mobile clients sync these as a unit and cache them indefinitely — they
 * change perhaps twice a year. Serving them individually would mean four round
 * trips to render one form, which is the chattiness that makes an app feel
 * slow regardless of how fast each call is.
 */
@RestController
@RequestMapping("/v1/reference")
class ReferenceDataController(
    private val referenceDataService: ReferenceDataService,
) {
    @GetMapping
    fun listAll(
        @RequestParam(required = false) tables: List<String>?,
    ): Map<String, List<ReferenceItem>> {
        val requested =
            tables?.map { name ->
                ReferenceTable.fromApiName(name)
                    ?: throw NotFoundException(ErrorCode.NOT_FOUND, "Unknown reference table: $name")
            } ?: ReferenceTable.entries

        return referenceDataService.listAll(requested)
    }

    @GetMapping("/{table}")
    fun list(
        @PathVariable table: String,
        @RequestParam(defaultValue = "false") includeInactive: Boolean,
    ): List<ReferenceItem> {
        val resolved =
            ReferenceTable.fromApiName(table)
                ?: throw NotFoundException(ErrorCode.NOT_FOUND, "Unknown reference table: $table")

        return referenceDataService.list(resolved, includeInactive)
    }
}
