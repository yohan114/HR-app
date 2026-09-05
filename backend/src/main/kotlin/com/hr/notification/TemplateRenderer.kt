package com.hr.notification

/**
 * Fills `{placeholder}` slots in a notification template.
 *
 * Deliberately not a general template engine. Templates here are tenant-editable content, and a
 * Velocity or Thymeleaf template reachable from a text box that HR administrators can type into is
 * a server-side template injection hole — the class of bug that turns "change the wording of the
 * leave email" into arbitrary code execution. Placeholder substitution has no expression evaluation
 * to escape into, so there is nothing to reach.
 */
object TemplateRenderer {
    private val PLACEHOLDER = Regex("""\{([a-zA-Z][a-zA-Z0-9_]*)\}""")

    /**
     * Substitutes placeholders from [values].
     *
     * An unknown placeholder renders as empty rather than as its own literal text. `{approverName}`
     * left visible in a notification reads as a broken system to the recipient, while a missing name
     * usually reads as a slightly terse sentence. Neither is good; one is embarrassing in front of
     * a customer's whole workforce.
     */
    fun render(
        template: String,
        values: Map<String, String?>,
    ): String = PLACEHOLDER.replace(template) { match -> values[match.groupValues[1]].orEmpty() }

    /**
     * The placeholders a template refers to, for validating one at save time.
     *
     * Catching `{aproverName}` when an administrator saves the template is the difference between a
     * typo and a month of notifications with a hole in the middle of the sentence.
     */
    fun placeholdersIn(template: String): Set<String> =
        PLACEHOLDER.findAll(template).map { it.groupValues[1] }.toSet()

    /**
     * Picks the best available locale from those a tenant has actually written.
     *
     * Falls back `si-LK` → `si` → `en` → any. The final "any" is what keeps a tenant that has
     * translated its templates into Sinhala only, and has one user whose locale is French, from
     * getting a blank notification: something in the wrong language is legible, and nothing is not.
     */
    fun <T> selectLocale(
        available: Map<String, T>,
        requested: String?,
        fallback: String = "en",
    ): T? {
        if (available.isEmpty()) return null

        val candidates =
            buildList {
                requested?.let {
                    add(it)
                    // `si-LK` and `si_LK` both appear in the wild; normalise before shortening.
                    val base = it.replace('_', '-').substringBefore('-')
                    if (base != it) add(base)
                }
                add(fallback)
            }

        candidates.forEach { candidate ->
            available.entries
                .firstOrNull { it.key.equals(candidate, ignoreCase = true) }
                ?.let { return it.value }
        }
        return available.values.first()
    }
}
