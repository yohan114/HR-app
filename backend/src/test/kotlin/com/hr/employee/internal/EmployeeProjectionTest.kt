package com.hr.employee.internal

import com.hr.identity.FieldAccess
import com.hr.identity.FieldAccessContext
import com.hr.identity.FieldPermissions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

/**
 * What reaches the wire.
 *
 * The resolver decides the policy; this decides whether the policy is actually
 * applied to the bytes a client receives. Both need testing — a correct policy
 * serialised past is the same leak as an incorrect one.
 */
@DisplayName("Employee projection")
class EmployeeProjectionTest {
    private val viewer = UUID.randomUUID()

    // ------------------------------------------------------------------------
    @Nested
    @DisplayName("Hidden fields")
    inner class Hidden {
        /**
         * Absent, not null. A null tells the caller the field exists and they
         * are not allowed it — which is itself information, and makes a client
         * render a disabled input for something they should not know about.
         */
        @Test
        fun `are absent from the payload rather than null`() {
            val payload = project(access = mapOf("dateOfBirth" to FieldAccess.HIDDEN))

            assertThat(payload).doesNotContainKey("dateOfBirth")
            assertThat(payload).containsKey("displayName")
        }

        @Test
        fun `leave no trace of their value anywhere in the payload`() {
            val employee = employee().apply { personalEmail = "alice.private@gmail.com" }
            val payload = project(employee, mapOf("personalEmail" to FieldAccess.HIDDEN))

            assertThat(payload.values.map { it.toString() })
                .noneMatch { it.contains("alice.private") }
        }
    }

    // ------------------------------------------------------------------------
    @Nested
    @DisplayName("Masked fields")
    inner class Masked {
        @Test
        fun `are present but redacted`() {
            val employee = employee().apply { mobile = "0771234567" }
            val payload = project(employee, mapOf("mobile" to FieldAccess.MASKED))

            assertThat(payload["mobile"]).isEqualTo("••••4567")
        }

        /**
         * The whole point of masking is that the true value never leaves the
         * process. If the real number is in the payload and the client renders
         * dots, it is still in the HTTP cache, the client database and any log
         * that captures bodies.
         */
        @Test
        fun `never carry the true value`() {
            val employee = employee().apply { mobile = "0771234567" }
            val payload = project(employee, mapOf("mobile" to FieldAccess.MASKED))

            assertThat(payload.values.map { it.toString() }).noneMatch { it == "0771234567" }
        }
    }

    // ------------------------------------------------------------------------
    @Nested
    @DisplayName("Identity of the record")
    inner class RecordIdentity {
        /**
         * `id` and `version` are never permission-controlled: without them the
         * payload cannot be addressed, cached or safely updated. Neither is
         * sensitive — you already had to be allowed to fetch this record.
         */
        @Test
        fun `id and version survive a policy that hides everything`() {
            val payload = project(access = EmployeeProjection.FIELDS.keys.associateWith { FieldAccess.HIDDEN })

            assertThat(payload.keys).containsExactlyInAnyOrder("id", "version")
        }
    }

    // ------------------------------------------------------------------------
    @Nested
    @DisplayName("Custom fields")
    inner class Custom {
        @Test
        fun `are permission-checked under their own keys`() {
            val employee =
                employee().apply {
                    customFields["tshirtSize"] = "L"
                    customFields["disciplinaryNotes"] = "Verbal warning 2026-01"
                }

            val payload = project(employee, mapOf("disciplinaryNotes" to FieldAccess.HIDDEN))

            @Suppress("UNCHECKED_CAST")
            val custom = payload["customFields"] as Map<String, Any?>
            assertThat(custom).containsEntry("tshirtSize", "L")
            assertThat(custom).doesNotContainKey("disciplinaryNotes")
        }

        /**
         * An empty `customFields: {}` on every response is noise on a payload
         * the mobile app fetches constantly, and it makes "this tenant has no
         * custom fields" indistinguishable from "you may see none of them".
         */
        @Test
        fun `the container is omitted when nothing is visible`() {
            val employee = employee().apply { customFields["disciplinaryNotes"] = "…" }
            val payload = project(employee, mapOf("disciplinaryNotes" to FieldAccess.HIDDEN))

            assertThat(payload).doesNotContainKey("customFields")
        }
    }

    // ------------------------------------------------------------------------
    @Nested
    @DisplayName("Context construction")
    inner class Context {
        @Test
        fun `recognises the caller's own record`() {
            val employee = employee()
            val context =
                projectionWith(emptyMap()).contextFor(
                    viewerUserId = viewer,
                    viewerEmployeeId = employee.id,
                    employee = employee,
                    canManage = false,
                )

            assertThat(context.subjectIsSelf).isTrue()
        }

        /**
         * An account with no employee record — a platform operator, an
         * integration — must never match the record it happens to be reading.
         */
        @Test
        fun `an account with no employee record is never self`() {
            val employee = employee()
            val context =
                projectionWith(emptyMap()).contextFor(
                    viewerUserId = viewer,
                    viewerEmployeeId = null,
                    employee = employee,
                    canManage = false,
                )

            assertThat(context.subjectIsSelf).isFalse()
        }
    }

    // ------------------------------------------------------------------------

    /** The encrypted national id is never a response value, at any access level. */
    @Test
    fun `the national id ciphertext is not a projectable field`() {
        assertThat(EmployeeProjection.FIELDS).doesNotContainKey("nationalIdEnc")

        val employee = employee().apply { nationalIdEnc = "ENCRYPTED-PAYLOAD" }
        val payload = project(employee, emptyMap())

        assertThat(payload.values.map { it.toString() }).noneMatch { it.contains("ENCRYPTED") }
    }

    // ------------------------------------------------------------------------

    private fun project(
        employee: Employee = employee(),
        access: Map<String, FieldAccess> = emptyMap(),
    ): Map<String, Any?> =
        projectionWith(access).project(
            employee,
            FieldAccessContext(viewer, EmployeeProjection.ENTITY_TYPE, subjectIsSelf = false, canManage = false),
        )

    /**
     * A stub rather than a mock: the policy is tested in
     * `FieldPermissionResolverTest`, and what matters here is only that
     * whatever the policy says is honoured.
     */
    private fun projectionWith(access: Map<String, FieldAccess>) =
        EmployeeProjection(
            object : FieldPermissions {
                override fun accessFor(
                    context: FieldAccessContext,
                    fieldKey: String,
                ) = access[fieldKey] ?: FieldAccess.READ

                override fun readableFields(
                    context: FieldAccessContext,
                    candidateFields: Collection<String>,
                ) = candidateFields.filterTo(mutableSetOf()) { accessFor(context, it).canRead }

                override fun writableFields(
                    context: FieldAccessContext,
                    candidateFields: Collection<String>,
                ) = candidateFields.filterTo(mutableSetOf()) { accessFor(context, it).canWrite }
            },
        )

    private fun employee() =
        Employee(
            employeeCode = "E001",
            companyId = UUID.randomUUID(),
            firstName = "Alice",
            lastName = "Perera",
            displayName = "Alice Perera",
            joinDate = LocalDate.of(2020, 1, 6),
        ).apply {
            // `id` is assigned by BaseEntity (UUIDv7) and has a protected setter,
            // so tests use whatever it generated rather than substituting one.
            dateOfBirth = LocalDate.of(1990, 5, 2)
        }
}
