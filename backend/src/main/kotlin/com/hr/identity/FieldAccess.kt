package com.hr.identity

/**
 * How much of a field a caller may see.
 *
 * Four levels rather than a boolean, because "can see it" and "can change it"
 * are genuinely different questions, and because some fields are useful in
 * redacted form.
 */
enum class FieldAccess {
    /**
     * Absent from the response entirely.
     *
     * Not null, not empty — absent. A null tells the caller the field exists
     * and they are not allowed it, which is itself information, and invites a
     * client to render a disabled input for something they should not know
     * about.
     */
    HIDDEN,

    /**
     * Present but redacted, e.g. a bank account as `••••1234`.
     *
     * Exists because "is this the right account?" is a question a payroll
     * clerk legitimately needs answered without being shown the number. A
     * binary hidden/visible forces a choice between exposing the whole value
     * and making the task impossible.
     */
    MASKED,

    READ,

    WRITE,
    ;

    val canRead: Boolean get() = this != HIDDEN
    val canWrite: Boolean get() = this == WRITE

    companion object {
        /**
         * The more permissive of two grants.
         *
         * Roles are additive: holding two roles gives the union of what each
         * allows, which is how every other permission in the system behaves.
         * A role that *removes* access would be surprising — an administrator
         * would have to reason about the interaction of every role a user
         * holds to predict what they can see.
         *
         * The safety property comes from [FieldPermissionResolver]'s
         * deny-by-default treatment of sensitive fields, not from letting one
         * role veto another.
         */
        fun mostPermissive(
            a: FieldAccess,
            b: FieldAccess,
        ): FieldAccess = if (a.ordinal >= b.ordinal) a else b
    }
}
