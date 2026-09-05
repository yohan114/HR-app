package com.hr.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TemplateRendererTest {
    @Test
    fun `placeholders are filled from the values`() {
        val rendered =
            TemplateRenderer.render(
                "{approverName} approved your leave from {startDate}.",
                mapOf("approverName" to "Nimali", "startDate" to "2026-04-01"),
            )

        assertEquals("Nimali approved your leave from 2026-04-01.", rendered)
    }

    /**
     * A visible `{approverName}` reads as a broken system to everyone who receives it. A missing
     * name reads as a slightly terse sentence.
     */
    @Test
    fun `an unknown placeholder renders empty rather than as its own name`() {
        val rendered = TemplateRenderer.render("Hello {missing}!", emptyMap())

        assertEquals("Hello !", rendered)
        assertFalse(rendered.contains("{"), "the placeholder syntax leaked to the recipient")
    }

    @Test
    fun `a null value renders empty`() {
        assertEquals("Hi .", TemplateRenderer.render("Hi {name}.", mapOf("name" to null)))
    }

    /**
     * Templates are tenant-editable content. Anything that evaluates expressions in a string an
     * administrator can type is a server-side template injection hole; substitution has no
     * expression syntax to break out of, and this pins that down.
     */
    @Test
    fun `substituted values are not themselves interpreted`() {
        val rendered =
            TemplateRenderer.render("Hello {name}.", mapOf("name" to "{other}", "other" to "BAD"))

        assertEquals("Hello {other}.", rendered, "a value must not be re-expanded")
    }

    @Test
    fun `expression-like input is inert`() {
        val rendered =
            TemplateRenderer.render(
                "\${T(java.lang.Runtime).getRuntime()} #{2*2} {name}",
                mapOf("name" to "ok"),
            )

        assertTrue(rendered.contains("\${T(java.lang.Runtime).getRuntime()}"), "SpEL was altered")
        assertTrue(rendered.contains("#{2*2}"), "the expression was evaluated")
        assertTrue(rendered.endsWith("ok"))
    }

    @Test
    fun `placeholders can be listed for validation at save time`() {
        assertEquals(
            setOf("approverName", "startDate"),
            TemplateRenderer.placeholdersIn("{approverName} approved leave from {startDate}."),
        )
    }

    @Test
    fun `braces that are not placeholders are left alone`() {
        val text = "Use { and } freely, and {1invalid} too."

        assertEquals(text, TemplateRenderer.render(text, emptyMap()))
        assertEquals(emptySet<String>(), TemplateRenderer.placeholdersIn(text))
    }

    @Test
    fun `an exact locale wins`() {
        val available = mapOf("en" to "English", "si" to "Sinhala", "ta" to "Tamil")

        assertEquals("Sinhala", TemplateRenderer.selectLocale(available, "si"))
    }

    @Test
    fun `a regional locale falls back to its base language`() {
        val available = mapOf("en" to "English", "si" to "Sinhala")

        assertEquals("Sinhala", TemplateRenderer.selectLocale(available, "si-LK"))
        assertEquals("Sinhala", TemplateRenderer.selectLocale(available, "si_LK"))
    }

    @Test
    fun `an untranslated locale falls back to english`() {
        val available = mapOf("en" to "English", "si" to "Sinhala")

        assertEquals("English", TemplateRenderer.selectLocale(available, "fr"))
    }

    /**
     * Something in the wrong language is legible; a blank notification is not. A tenant that has
     * translated only into Sinhala must still reach a user whose locale is French.
     */
    @Test
    fun `a tenant with no english template still sends something`() {
        val available = mapOf("si" to "Sinhala")

        assertEquals("Sinhala", TemplateRenderer.selectLocale(available, "fr"))
    }

    @Test
    fun `no templates at all yields nothing to send`() {
        assertNull(TemplateRenderer.selectLocale(emptyMap<String, String>(), "en"))
    }

    @Test
    fun `a null requested locale falls back`() {
        assertEquals("English", TemplateRenderer.selectLocale(mapOf("en" to "English"), null))
    }
}
