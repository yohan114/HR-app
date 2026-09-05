package com.hr.notification

/**
 * The `hrapp://` links notifications carry.
 *
 * A notification that opens the app to its home screen has wasted the tap: the user came to see the
 * leave request, and now has to find it. Every notification the product sends names its destination.
 *
 * These routes are duplicated in the mobile clients, which is unavoidable — the server builds the
 * link and the client parses it, in different languages. `scripts/deeplink-check.mjs` compares the
 * two sets on every build, because the failure mode of drift is silent: an unrecognised link opens
 * the app to nowhere in particular, and nobody files that as a bug.
 */
object DeepLink {
    const val SCHEME = "hrapp"

    fun approvals(): String = "$SCHEME://approvals"

    fun leave(id: String): String = "$SCHEME://leave/$id"

    fun payslip(periodId: String): String = "$SCHEME://payslip/$periodId"

    fun employee(id: String): String = "$SCHEME://employee/$id"

    /** The route templates, in the `{placeholder}` form the mobile clients register. */
    val ROUTES: Set<String> =
        setOf(
            "$SCHEME://approvals",
            "$SCHEME://leave/{id}",
            "$SCHEME://payslip/{periodId}",
            "$SCHEME://employee/{id}",
        )

    /**
     * Whether a link is one the apps can actually open.
     *
     * Templates carry a tenant-editable `deep_link_pattern`, so this is the guard between "an
     * administrator typed a URL into a text box" and a notification that lands on a dead end. It is
     * also the guard against a link leaving the scheme entirely: a template pointing at `https://`
     * would send the recipient to a browser, and an attacker with template-edit rights could
     * point the whole workforce's notifications wherever they liked.
     */
    fun isSupported(link: String): Boolean {
        if (!link.startsWith("$SCHEME://")) return false
        val path = link.removePrefix("$SCHEME://").substringBefore('?')
        val segments = path.split('/').filter { it.isNotEmpty() }

        return ROUTES.any { route ->
            val routeSegments =
                route.removePrefix("$SCHEME://").split('/').filter { it.isNotEmpty() }
            routeSegments.size == segments.size &&
                routeSegments.zip(segments).all { (expected, actual) ->
                    expected.startsWith('{') || expected == actual
                }
        }
    }
}
