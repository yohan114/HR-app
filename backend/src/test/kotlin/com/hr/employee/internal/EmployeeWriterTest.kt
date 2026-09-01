package com.hr.employee.internal

import com.hr.shared.api.ApiException
import com.hr.shared.api.FieldViolation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.util.UUID

@DisplayName("Employee updates")
class EmployeeWriterTest {
    private val writer = EmployeeWriter()

    // ------------------------------------------------------------------------
    @Nested
    @DisplayName("Authorisation")
    inner class Authorisation {
        /**
         * Rejected, not silently skipped. Dropping the field means the user
         * watches the save succeed, comes back later, and finds their change
         * gone with no explanation.
         */
        @Test
        fun `a field the caller may not write is refused`() {
            val employee = employee()

            assertThatThrownBy { writer.apply(employee, mapOf("joinDate" to "2021-01-01"), setOf("mobile")) }
                .isInstanceOfSatisfying(ApiException::class.java) {
                    assertThat(it.status).isEqualTo(HttpStatus.FORBIDDEN)
                    assertThat(it.code).isEqualTo("FIELD_NOT_WRITABLE")
                }

            assertThat(employee.joinDate).isEqualTo(LocalDate.of(2020, 1, 6))
        }

        /**
         * A rejected update must not apply the permitted half. Otherwise a
         * caller learns which fields they may write by watching which of them
         * changed, and the record is left in a state nobody asked for.
         */
        @Test
        fun `nothing is applied when any field is refused`() {
            val employee = employee()

            assertThatThrownBy {
                writer.apply(
                    employee,
                    mapOf("mobile" to "0771111111", "joinDate" to "2021-01-01"),
                    setOf("mobile"),
                )
            }.isInstanceOf(ApiException::class.java)

            assertThat(employee.mobile).isNull()
        }

        @Test
        fun `an unknown field is refused before any permission check`() {
            assertThatThrownBy { writer.apply(employee(), mapOf("nonsense" to "x"), setOf("nonsense")) }
                .isInstanceOfSatisfying(ApiException::class.java) {
                    assertThat(it.status).isEqualTo(HttpStatus.BAD_REQUEST)
                    assertThat(it.code).isEqualTo("UNKNOWN_FIELD")
                }
        }
    }

    // ------------------------------------------------------------------------
    @Nested
    @DisplayName("Fields that are not writable at all")
    inner class NotWritable {
        /**
         * `status`, `resignDate` and `lastWorkingDate` are outcomes of the
         * leaver process, which has approvals and side effects — payroll
         * cut-off, access revocation, final settlement. A profile PATCH that
         * could set `status = EXITED` would skip all of it and leave someone
         * paid but locked out, or the reverse.
         */
        @Test
        fun `the leaver fields are refused even with a write grant`() {
            listOf("status", "resignDate", "lastWorkingDate").forEach { field ->
                assertThatThrownBy { writer.apply(employee(), mapOf(field to "EXITED"), setOf(field)) }
                    .describedAs(field)
                    .isInstanceOfSatisfying(ApiException::class.java) {
                        assertThat(it.code).isEqualTo("UNKNOWN_FIELD")
                    }
            }
        }

        @Test
        fun `yearsOfService is readable but not settable`() {
            assertThat(EmployeeProjection.FIELDS).containsKey("yearsOfService")

            assertThatThrownBy { writer.apply(employee(), mapOf("yearsOfService" to 5), setOf("yearsOfService")) }
                .isInstanceOf(ApiException::class.java)
        }
    }

    // ------------------------------------------------------------------------
    @Nested
    @DisplayName("Coercion")
    inner class Coercion {
        @Test
        fun `values arrive as JSON strings and are converted`() {
            val employee = employee()
            val supervisor = UUID.randomUUID()

            writer.apply(
                employee,
                mapOf("dateOfBirth" to "1990-05-02", "supervisorId" to supervisor.toString()),
                setOf("dateOfBirth", "supervisorId"),
            )

            assertThat(employee.dateOfBirth).isEqualTo(LocalDate.of(1990, 5, 2))
            assertThat(employee.supervisorId).isEqualTo(supervisor)
        }

        /**
         * A malformed date is the caller's mistake, so they are told which
         * field — rather than a ClassCastException surfacing as a 500 with no
         * indication of what was wrong.
         */
        @Test
        fun `a malformed value names the field it came from`() {
            val violations =
                violationsFrom {
                    writer.apply(employee(), mapOf("dateOfBirth" to "not-a-date"), setOf("dateOfBirth"))
                }

            assertThat(violations).extracting<String> { it.field }.containsExactly("dateOfBirth")
            assertThat(violations).extracting<String> { it.code }.containsExactly("INVALID_DATE")
        }

        @Test
        fun `every malformed value is reported, not just the first`() {
            val violations =
                violationsFrom {
                    writer.apply(
                        employee(),
                        mapOf("dateOfBirth" to "nope", "supervisorId" to "also-nope"),
                        setOf("dateOfBirth", "supervisorId"),
                    )
                }

            assertThat(violations).extracting<String> { it.field }
                .containsExactlyInAnyOrder("dateOfBirth", "supervisorId")
        }

        /**
         * Storing `""` would make "not set" and "set to nothing" two distinct
         * states that every query downstream then has to handle.
         */
        @Test
        fun `an empty string clears an optional field rather than storing it`() {
            val employee = employee().apply { middleName = "Marie" }

            writer.apply(employee, mapOf("middleName" to ""), setOf("middleName"))

            assertThat(employee.middleName).isNull()
        }

        @Test
        fun `a required field cannot be cleared`() {
            val violations =
                violationsFrom { writer.apply(employee(), mapOf("firstName" to ""), setOf("firstName")) }

            assertThat(violations).extracting<String> { it.code }.containsExactly("REQUIRED")
        }

        @Test
        fun `text longer than the column is refused rather than truncated`() {
            val violations =
                violationsFrom {
                    writer.apply(employee(), mapOf("firstName" to "a".repeat(129)), setOf("firstName"))
                }

            assertThat(violations).extracting<String> { it.code }.containsExactly("MAX_LENGTH")
        }

        @Test
        fun `an explicit null clears an optional reference`() {
            val employee = employee().apply { departmentId = UUID.randomUUID() }

            writer.apply(employee, mapOf("departmentId" to null), setOf("departmentId"))

            assertThat(employee.departmentId).isNull()
        }
    }

    // ------------------------------------------------------------------------
    @Nested
    @DisplayName("clearFields")
    inner class ClearFields {
        /**
         * The reason this exists: a generated typed client cannot send a
         * deliberate null, because its encoder must omit nulls or a
         * one-field PATCH carries forty of them.
         */
        @Test
        fun `becomes an explicit null the writer can apply`() {
            val employee = employee().apply { middleName = "Marie" }

            val expanded = writer.expandClearFields(mapOf("clearFields" to listOf("middleName")))
            writer.apply(employee, expanded, setOf("middleName"))

            assertThat(employee.middleName).isNull()
        }

        @Test
        fun `is subject to the same write permission as setting a value`() {
            val expanded = writer.expandClearFields(mapOf("clearFields" to listOf("departmentId")))

            assertThatThrownBy { writer.apply(employee(), expanded, setOf("mobile")) }
                .isInstanceOfSatisfying(ApiException::class.java) {
                    assertThat(it.code).isEqualTo("FIELD_NOT_WRITABLE")
                }
        }

        @Test
        fun `leaves an ordinary update untouched`() {
            val updates = mapOf("mobile" to "0771234567")
            assertThat(writer.expandClearFields(updates)).isEqualTo(updates)
        }

        @Test
        fun `does not leave its own key in the update map`() {
            assertThat(writer.expandClearFields(mapOf("clearFields" to listOf("middleName"))))
                .containsOnlyKeys("middleName")
        }

        /**
         * Two contradictory instructions. Resolving by precedence would make the
         * save silently do the opposite of what was intended, and which one won
         * would depend on map ordering.
         */
        @Test
        fun `a field both set and cleared is rejected`() {
            assertThatThrownBy {
                writer.expandClearFields(mapOf("middleName" to "Marie", "clearFields" to listOf("middleName")))
            }.isInstanceOfSatisfying(ApiException::class.java) {
                assertThat(it.code).isEqualTo("CONTRADICTORY_UPDATE")
                assertThat(it.status).isEqualTo(HttpStatus.BAD_REQUEST)
            }
        }

        /**
         * A cleared custom field belongs inside `customFields`, not at the top level. Before this,
         * `clearFields: ["tshirtSize"]` produced a top-level `tshirtSize: null` — not in SETTERS —
         * so clearing any tenant-defined field came back `400 UNKNOWN_FIELD`, and there was no way
         * to clear one at all.
         */
        @Test
        fun `a custom field is cleared inside customFields, not at the top level`() {
            val expanded =
                writer.expandClearFields(
                    mapOf("clearFields" to listOf("tshirtSize")),
                    customFieldKeys = setOf("tshirtSize"),
                )

            assertThat(expanded).doesNotContainKey("tshirtSize")

            @Suppress("UNCHECKED_CAST")
            val custom = expanded["customFields"] as Map<String, Any?>
            assertThat(custom).containsEntry("tshirtSize", null)
        }

        @Test
        fun `built-in and custom fields can be cleared in the same request`() {
            val expanded =
                writer.expandClearFields(
                    mapOf("clearFields" to listOf("middleName", "tshirtSize")),
                    customFieldKeys = setOf("tshirtSize"),
                )

            assertThat(expanded).containsEntry("middleName", null)

            @Suppress("UNCHECKED_CAST")
            val custom = expanded["customFields"] as Map<String, Any?>
            assertThat(custom).containsEntry("tshirtSize", null)
        }

        @Test
        fun `clearing a custom field preserves others set in the same request`() {
            val expanded =
                writer.expandClearFields(
                    mapOf(
                        "customFields" to mapOf("shoeSize" to "42"),
                        "clearFields" to listOf("tshirtSize"),
                    ),
                    customFieldKeys = setOf("tshirtSize", "shoeSize"),
                )

            @Suppress("UNCHECKED_CAST")
            val custom = expanded["customFields"] as Map<String, Any?>
            assertThat(custom).containsEntry("shoeSize", "42").containsEntry("tshirtSize", null)
        }

        /** The contradiction check has to see inside `customFields` too. */
        @Test
        fun `a custom field both set and cleared is rejected`() {
            assertThatThrownBy {
                writer.expandClearFields(
                    mapOf(
                        "customFields" to mapOf("tshirtSize" to "L"),
                        "clearFields" to listOf("tshirtSize"),
                    ),
                    customFieldKeys = setOf("tshirtSize"),
                )
            }.isInstanceOfSatisfying(ApiException::class.java) {
                assertThat(it.code).isEqualTo("CONTRADICTORY_UPDATE")
            }
        }

        @Test
        fun `a malformed clearFields is rejected rather than ignored`() {
            assertThatThrownBy { writer.expandClearFields(mapOf("clearFields" to "middleName")) }
                .isInstanceOfSatisfying(ApiException::class.java) {
                    assertThat(it.code).isEqualTo("MALFORMED_REQUEST")
                }

            assertThatThrownBy { writer.expandClearFields(mapOf("clearFields" to listOf(42))) }
                .isInstanceOfSatisfying(ApiException::class.java) {
                    assertThat(it.code).isEqualTo("MALFORMED_REQUEST")
                }
        }

        @Test
        fun `clearing a required field still fails validation`() {
            val expanded = writer.expandClearFields(mapOf("clearFields" to listOf("firstName")))

            val violations = violationsFrom { writer.apply(employee(), expanded, setOf("firstName")) }

            assertThat(violations).extracting<String> { it.code }.containsExactly("REQUIRED")
        }
    }

    // ------------------------------------------------------------------------

    @Test
    fun `the applied keys are reported back`() {
        val employee = employee()

        val applied =
            writer.apply(
                employee,
                mapOf("mobile" to "0771234567", "preferredName" to "Ali"),
                setOf("mobile", "preferredName"),
            )

        assertThat(applied).containsExactlyInAnyOrder("mobile", "preferredName")
    }

    /**
     * Read and write share one key space, so a single `field_permission` row
     * governs both. Two key spaces would mean granting WRITE on `dateOfBirth`
     * for reads and `date_of_birth` for writes, and one of them would be
     * forgotten.
     */
    @Test
    fun `every writable field is also a projectable field`() {
        assertThat(EmployeeProjection.FIELDS.keys)
            .containsAll(EmployeeWriter.settableFields())
    }

    // ------------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun violationsFrom(block: () -> Unit): List<FieldViolation> {
        val thrown = runCatching(block).exceptionOrNull()
        assertThat(thrown).isInstanceOf(ApiException::class.java)
        return (thrown as ApiException).details?.get("violations") as List<FieldViolation>
    }

    private fun employee() =
        Employee(
            employeeCode = "E001",
            companyId = UUID.randomUUID(),
            firstName = "Alice",
            lastName = "Perera",
            displayName = "Alice Perera",
            joinDate = LocalDate.of(2020, 1, 6),
        )
}
