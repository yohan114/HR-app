package com.hr.shared.api

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Base64

/**
 * Cursor pagination. Offset pagination is banned on tenant-scoped data.
 *
 * Reason: offset pagination re-scans skipped rows on every page, and on a table that is being
 * written to concurrently it silently drops and duplicates rows across pages. On attendance and
 * audit tables — which are append-heavy and can hold millions of rows per tenant — both problems
 * are guaranteed, not theoretical.
 *
 * A cursor encodes the sort key of the last row returned. Paging is then a range scan on an
 * index, which stays O(page size) regardless of depth.
 */
data class CursorPage<T>(
    val items: List<T>,
    /** Opaque cursor for the next page. `null` when there are no more results. */
    val nextCursor: String? = null,
    val hasMore: Boolean = nextCursor != null,
)

data class CursorRequest(
    val cursor: String? = null,
    val limit: Int = DEFAULT_LIMIT,
) {
    init {
        require(limit in 1..MAX_LIMIT) { "limit must be between 1 and $MAX_LIMIT" }
    }

    companion object {
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 200
    }
}

/**
 * Encodes and decodes opaque cursors.
 *
 * The payload is base64url JSON. It is deliberately *opaque to clients* but not secret — it
 * carries only sort-key values the caller already received. It is not signed, so never put an
 * authorisation decision in a cursor: always re-apply tenant and data-scope filters on read.
 */
object Cursors {
    private val mapper = ObjectMapper()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(values: Map<String, Any?>): String = encoder.encodeToString(mapper.writeValueAsBytes(values))

    @Suppress("UNCHECKED_CAST")
    fun decode(cursor: String): Map<String, Any?> =
        try {
            mapper.readValue(decoder.decode(cursor), Map::class.java) as Map<String, Any?>
        } catch (e: Exception) {
            throw BadRequestException(
                code = ErrorCode.INVALID_CURSOR,
                message = "Cursor is not valid. Restart pagination without a cursor.",
                field = "cursor",
            )
        }
}
