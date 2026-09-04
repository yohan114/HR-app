package com.hr.app.ui.profile

import com.hr.client.model.EmployeeProfile
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/**
 * The profile field lookup.
 *
 * Small, but it sits on the seam where the typed client erases a distinction the server is careful
 * about: `EmployeeProfile` makes every property nullable, so "you may not see this" and "nobody
 * filled it in" arrive identical. The screen compensates by taking its field *list* from the form
 * schema; this checks the value lookup behind that.
 */
class ProfileFieldsTest {
    private fun profile(
        block: EmployeeProfile.() -> EmployeeProfile = { this },
    ): EmployeeProfile =
        EmployeeProfile(id = UUID.randomUUID(), version = 1).block()

    @Test
    fun `reads plain fields by their schema key`() {
        val subject =
            profile {
                copy(
                    employeeCode = "E001",
                    displayName = "Nimali Wickramasinghe",
                    workEmail = "nimali@demo.local",
                    mobile = "0771234567",
                )
            }

        assertEquals("E001", subject.valueFor("employeeCode"))
        assertEquals("Nimali Wickramasinghe", subject.valueFor("displayName"))
        assertEquals("nimali@demo.local", subject.valueFor("workEmail"))
        assertEquals("0771234567", subject.valueFor("mobile"))
    }

    @Test
    fun `dates render in ISO form`() {
        val subject = profile { copy(joinDate = LocalDate.of(2020, 1, 6)) }

        assertEquals("2020-01-06", subject.valueFor("joinDate"))
    }

    @Test
    fun `a field the server omitted reads as null`() {
        assertNull(profile().valueFor("dateOfBirth"))
        assertNull(profile().valueFor("mobile"))
    }

    /**
     * A uuid under a heading that says "Department" is worse than nothing — it looks like data,
     * cannot be acted on, and would be the only thing on the row. These stay blank until the
     * pickers that resolve them to names land.
     */
    @Test
    fun `reference and employee ids are not shown as raw uuids`() {
        val subject =
            profile {
                copy(
                    departmentId = UUID.randomUUID(),
                    designationId = UUID.randomUUID(),
                    supervisorId = UUID.randomUUID(),
                    nationalityId = UUID.randomUUID(),
                )
            }

        listOf("departmentId", "designationId", "supervisorId", "nationalityId").forEach { key ->
            assertNull("$key leaked a uuid into the UI", subject.valueFor(key))
        }
    }

    /** Tenant-defined fields arrive in the JSONB bag and are found by their own key. */
    @Test
    fun `custom fields are read from the bag`() {
        val subject = profile { copy(customFields = mapOf("tshirtSize" to JsonPrimitive("L"))) }

        assertEquals("\"L\"", subject.valueFor("tshirtSize"))
    }

    /**
     * An unknown key must not throw. The schema is server-driven, so a field type or name this
     * build has never heard of is expected — and a crash on the profile screen would be a poor
     * response to the server adding something.
     */
    @Test
    fun `an unknown key is null rather than an error`() {
        assertNull(profile().valueFor("somethingAddedLater"))
    }

    /** An address is an object; flattening it into a line of text would produce JSON on screen. */
    @Test
    fun `addresses are not flattened into text`() {
        val subject =
            profile { copy(permanentAddress = mapOf("line1" to JsonPrimitive("42 Galle Road"))) }

        assertNull(subject.valueFor("permanentAddress"))
    }
}
