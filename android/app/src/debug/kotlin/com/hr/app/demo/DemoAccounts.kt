package com.hr.app.demo

/**
 * Who you become when you sign in to the demo build.
 *
 * ## Three accounts, one per authorisation path
 *
 * Copied from `LocalDemoSeeder.seedUsers`, and for the same reason it gives: each account exists
 * to make a different rule visible without reading any code.
 *
 * - [ADMIN] holds every permission in the catalogue and is linked to the CEO's record, so
 *   `employeeId` is populated. An administrator with no employee record silently exercises the
 *   "not a person in the org chart" branch, which is the rarer case in production.
 * - [MANAGER] holds `employee.view` **without** `employee.view.all`, so whose records they can
 *   open is decided by the reporting line rather than by a grant.
 * - [EMPLOYEE] holds only the directory permission. Everything they can do with their own record
 *   is authorised by ownership.
 *
 * The permission lists are transcribed from `V8__employee_access.sql`, so the Approvals tab
 * appears for exactly the people it would appear for against a real server —
 * `CurrentUserRepository.canApprove` keys off `employee.manage`, which only [ADMIN] holds.
 */
internal enum class DemoAccount(
    val username: String,
    val roleKey: String,
    val employeeCode: String,
    val permissions: List<String>,
) {
    ADMIN(
        username = "admin",
        roleKey = "ADMIN",
        employeeCode = "E001",
        // The whole catalogue, as the ADMIN role is provisioned with. Listed rather than wildcarded
        // because the server lists it too: a permission added by a later migration is an explicit
        // customer decision, not something that silently appears on a role.
        permissions =
            listOf(
                "config.field.manage", "config.field.view", "config.label.manage",
                "employee.bank.view", "employee.directory", "employee.document.view",
                "employee.manage", "employee.salary.view", "employee.view", "employee.view.all",
                "identity.device.revoke", "identity.device.view", "identity.role.manage",
                "identity.role.view", "identity.user.manage", "identity.user.view",
                "notification.send", "notification.template.manage", "notification.template.view",
                "org.reference.manage", "org.reference.view", "org.structure.manage",
                "org.structure.view", "platform.audit.view", "platform.tenant.manage",
                "platform.tenant.view",
            ),
    ),
    MANAGER(
        username = "manager",
        roleKey = "MANAGER",
        employeeCode = "E002",
        permissions =
            listOf(
                "employee.directory", "employee.view", "identity.user.view",
                "org.reference.view", "org.structure.view",
            ),
    ),
    EMPLOYEE(
        username = "employee",
        roleKey = "EMPLOYEE",
        employeeCode = "E004",
        permissions = listOf("employee.directory", "org.reference.view"),
    ),
    ;

    val person: DemoPerson get() = DemoWorkforce.byCode(employeeCode)!!

    /** Mirrors `EmployeeService`: these two are what let a caller open somebody else's record outright. */
    val seesEveryone: Boolean get() = "employee.view.all" in permissions || "employee.manage" in permissions

    /** Without [seesEveryone], this is what makes the reporting line decide instead of a 404. */
    val seesReportingLine: Boolean get() = "employee.view" in permissions

    /** Drives the Approvals tab, via `CurrentUserRepository.APPROVAL_PERMISSIONS`. */
    val approves: Boolean get() = "employee.manage" in permissions
}

/**
 * A signed-in identity: which account's permissions, and whose employee record.
 *
 * The two are separate because the demo lets you sign in as any of the nine people by name — see
 * [resolveDemoSignIn] — and those extra identities all carry the [DemoAccount.EMPLOYEE] permission
 * set. Collapsing them into one enum would mean either nine accounts to maintain or losing the
 * ability to look at the app as someone in Finance.
 */
internal data class DemoIdentity(
    val account: DemoAccount,
    val person: DemoPerson,
) {
    /**
     * The bearer token the demo transport issues.
     *
     * It carries the identity in plain sight rather than being opaque, because the fake `/v1/me`
     * has to answer "who is this?" from the `Authorization` header and nothing else — which also
     * means the header genuinely has to arrive. A token that meant nothing would let
     * `AuthInterceptor` quietly stop attaching it and the demo would still look fine, hiding
     * exactly the bug this app has had before.
     *
     * It is not a JWT, is not signed, and asserts nothing. Anything that treated it as a
     * credential would be wrong, which is why it never leaves the debug variant.
     */
    fun token(
        kind: String,
        nonce: Long,
    ): String = "$DEMO_TOKEN_PREFIX.$kind.${person.code}.${account.name}.$nonce"
}

internal const val DEMO_TOKEN_PREFIX = "demo"
internal const val DEMO_ACCESS = "access"
internal const val DEMO_REFRESH = "refresh"

/**
 * Reads an identity back out of an access or refresh token.
 *
 * Returns null for anything that is not one of ours — including an absent header — so the caller
 * answers 401 rather than silently defaulting to somebody. A demo that hands out data to an
 * unauthenticated request would be demonstrating the opposite of the product.
 */
internal fun demoIdentityFromToken(token: String?): DemoIdentity? {
    val parts = token?.trim()?.split('.') ?: return null
    if (parts.size != 5 || parts[0] != DEMO_TOKEN_PREFIX) return null

    val person = DemoWorkforce.byCode(parts[2]) ?: return null
    val account = DemoAccount.entries.firstOrNull { it.name == parts[3] } ?: return null
    return DemoIdentity(account, person)
}

/** True when the token is one of ours and is of [kind] — `access` or `refresh`. */
internal fun isDemoToken(
    token: String?,
    kind: String,
): Boolean {
    val parts = token?.trim()?.split('.') ?: return false
    return parts.size == 5 && parts[0] == DEMO_TOKEN_PREFIX && parts[1] == kind
}

/**
 * Turns whatever was typed into the username box into an identity.
 *
 * **Any credentials are accepted, and the password is never looked at.** There is no server to
 * check one against, and a demo build that refused a sign-in would be failing at the only thing it
 * exists to do.
 *
 * The username is still read, because it is free navigation:
 *
 * - `admin`, `manager` or `employee` gives that account's permission set — the three-way
 *   comparison the seeder is built around.
 * - any of the nine people, by employee code, first name, display name or work email, signs in as
 *   that person with the ordinary employee permission set. Signing in as `anusha` and looking at
 *   the Finance side of the directory takes no configuration.
 * - anything else — including a blank-ish string someone typed to get past the screen — lands on
 *   [DemoAccount.ADMIN]. That is the deliberate default: someone clicking through the app for the
 *   first time should find every screen reachable, not a directory full of profiles that answer
 *   404 because the reporting line says so.
 */
internal fun resolveDemoSignIn(username: String): DemoIdentity {
    val typed = username.trim().substringBefore('@').lowercase()

    DemoAccount.entries.firstOrNull { it.username == typed }
        ?.let { return DemoIdentity(it, it.person) }

    DemoWorkforce.people
        .firstOrNull { person ->
            typed == person.code.lowercase() ||
                typed == person.firstName.lowercase() ||
                typed == person.displayName.lowercase() ||
                typed == person.workEmail.substringBefore('@')
        }?.let { return DemoIdentity(DemoAccount.EMPLOYEE, it) }

    return DemoIdentity(DemoAccount.ADMIN, DemoAccount.ADMIN.person)
}
