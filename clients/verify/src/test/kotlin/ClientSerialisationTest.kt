import com.hr.client.infrastructure.Serializer
import com.hr.client.model.EmployeeProfile
import com.hr.client.model.EmployeeUpdate
import com.hr.client.model.FormField
import com.hr.client.model.FormSchema
import com.hr.client.model.FormSection
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The generated client can actually decode a response.
 *
 * Compiling the client proves the shape is syntactically valid; it proves
 * nothing about whether kotlinx.serialization can resolve a serialiser for
 * every field at runtime. Those are different failures and only one of them is
 * caught by a build.
 *
 * The specific hazard: the generator maps free-form JSON to
 * `@Contextual Map<String, JsonElement>`, and `@Contextual` tells the plugin to
 * look the serialiser up in the `SerializersModule` at runtime rather than
 * generating one. `Serializer.kotlinxSerializationAdapters` registers adapters
 * for `BigDecimal`, `LocalDate`, `UUID` and friends — and nothing for `Map` or
 * `JsonElement`. Without this test, the first time anyone finds out is a
 * `SerializationException` on a device, on the employee profile screen, which
 * is the app's most-opened screen after home.
 *
 * Every assertion here is about a *runtime* behaviour that compilation cannot
 * reach.
 */
class ClientSerialisationTest {
    private val json = Serializer.kotlinxSerializationJson

    @Test
    fun `a profile with custom fields round-trips`() {
        val original =
            EmployeeProfile(
                id = UUID.randomUUID(),
                version = 7,
                employeeCode = "E001",
                displayName = "Alice Perera",
                joinDate = LocalDate.of(2020, 1, 6),
                customFields =
                    mapOf(
                        "tshirtSize" to JsonPrimitive("L"),
                        "seatNumber" to JsonPrimitive(42),
                        "remoteEligible" to JsonPrimitive(true),
                    ),
            )

        val encoded = json.encodeToString(EmployeeProfile.serializer(), original)
        val decoded = json.decodeFromString(EmployeeProfile.serializer(), encoded)

        assertEquals(original.id, decoded.id)
        assertEquals(original.version, decoded.version)
        assertEquals(LocalDate.of(2020, 1, 6), decoded.joinDate)
        assertEquals("L", decoded.customFields?.get("tshirtSize")?.jsonPrimitive?.content)
        assertEquals(42, decoded.customFields?.get("seatNumber")?.jsonPrimitive?.content?.toInt())
    }

    /**
     * The real server payload omits fields the caller may not see, rather than
     * sending them as null. A client that cannot decode a response with most
     * keys absent cannot read any profile at all.
     */
    @Test
    fun `a profile decodes when every optional field is absent`() {
        val minimal = """{"id":"${UUID.randomUUID()}","version":0}"""

        val decoded = json.decodeFromString(EmployeeProfile.serializer(), minimal)

        assertNull(decoded.dateOfBirth)
        assertNull(decoded.customFields)
        assertNull(decoded.employeeCode)
    }

    /**
     * A newer server adding a field must not break an older client. The
     * generated `Json` is configured with `ignoreUnknownKeys`; this asserts it,
     * because a generator upgrade that dropped the setting would turn every
     * future additive change into a client-side crash.
     */
    @Test
    fun `an unknown field from a newer server is ignored`() {
        val withExtra =
            """{"id":"${UUID.randomUUID()}","version":1,"somethingAddedLater":{"nested":true}}"""

        val decoded = json.decodeFromString(EmployeeProfile.serializer(), withExtra)

        assertEquals(1, decoded.version)
    }

    /**
     * A nested object in a custom field — an address, a structured answer.
     * `JsonPrimitive` values alone would not exercise the recursive branch of
     * the `JsonElement` serialiser.
     */
    @Test
    fun `a nested object inside a custom field survives the round trip`() {
        val payload =
            """
            {"id":"${UUID.randomUUID()}","version":2,
             "customFields":{"emergency":{"name":"Nimali","phone":"0771234567"}}}
            """.trimIndent()

        val decoded = json.decodeFromString(EmployeeProfile.serializer(), payload)
        val emergency = decoded.customFields?.get("emergency")?.jsonObject

        assertEquals("Nimali", emergency?.get("name")?.jsonPrimitive?.content)

        val reencoded = json.encodeToString(EmployeeProfile.serializer(), decoded)
        assertTrue(reencoded.contains("Nimali"), "re-encoding lost the nested value")
    }

    @Test
    fun `an update with custom fields encodes to the shape the server expects`() {
        val update =
            EmployeeUpdate(
                mobile = "0771234567",
                customFields = mapOf("tshirtSize" to JsonPrimitive("M")),
            )

        val encoded = json.encodeToString(EmployeeUpdate.serializer(), update)

        assertTrue(encoded.contains("\"mobile\":\"0771234567\""), "mobile missing from $encoded")
        assertTrue(encoded.contains("\"tshirtSize\":\"M\""), "custom field missing from $encoded")
    }

    /**
     * A PATCH that omits a field must not send `"field":null` — the two mean
     * different things to the server. Absent leaves the value alone; null
     * clears it. If the encoder emits nulls for every unset property, every
     * partial update silently wipes the rest of the record.
     */
    @Test
    fun `an update omits unset fields rather than sending null`() {
        val encoded = json.encodeToString(EmployeeUpdate.serializer(), EmployeeUpdate(mobile = "0771234567"))

        assertTrue(
            !encoded.contains("\"firstName\""),
            "unset fields are being serialised, which would clear them server-side: $encoded",
        )
    }

    /**
     * The counterpart to omitting nulls: with the encoder dropping them, a
     * typed client can no longer say "set this to nothing" by sending null, so
     * `clearFields` is the only spelling left. If it did not survive encoding,
     * clearing a field would be impossible from any generated client.
     */
    @Test
    fun `clearFields survives an encoder that omits nulls`() {
        val encoded =
            json.encodeToString(
                EmployeeUpdate.serializer(),
                EmployeeUpdate(clearFields = listOf("middleName", "workPhone")),
            )

        assertTrue(encoded.contains("\"clearFields\""), "clearFields was dropped: $encoded")
        assertTrue(encoded.contains("middleName"), "field name was dropped: $encoded")
        assertTrue(!encoded.contains("null"), "nulls are still being encoded: $encoded")
    }

    /** The form schema is the other server-driven payload the app depends on. */
    @Test
    fun `a form schema round-trips`() {
        val schema =
            FormSchema(
                entityType = "employee",
                version = "abc123",
                sections =
                    listOf(
                        FormSection(
                            key = "personal",
                            label = "Personal",
                            fields =
                                listOf(
                                    FormField(
                                        key = "firstName",
                                        label = "First name",
                                        type = FormField.Type.TEXT,
                                    ),
                                ),
                        ),
                    ),
            )

        val decoded =
            json.decodeFromString(FormSchema.serializer(), json.encodeToString(FormSchema.serializer(), schema))

        assertEquals("employee", decoded.entityType)
        assertEquals(FormField.Type.TEXT, decoded.sections?.first()?.fields?.first()?.type)
    }
}
