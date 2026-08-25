package com.hr.identity.internal

import com.hr.shared.api.BusinessRuleException
import com.hr.tenancy.TenantContext
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Per-tenant password rules.
 *
 * Keyed directly by `tenant_id` — there is exactly one policy per tenant, so a surrogate key would
 * add nothing. This is why it does not extend `TenantScopedEntity`.
 */
@Entity
@Table(name = "password_policy")
class PasswordPolicy(
    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    var tenantId: UUID,
) {
    @Column(name = "min_length", nullable = false)
    var minLength: Short = 12

    @Column(name = "require_uppercase", nullable = false)
    var requireUppercase: Boolean = true

    @Column(name = "require_lowercase", nullable = false)
    var requireLowercase: Boolean = true

    @Column(name = "require_digit", nullable = false)
    var requireDigit: Boolean = true

    @Column(name = "require_symbol", nullable = false)
    var requireSymbol: Boolean = false

    @Column(name = "history_count", nullable = false)
    var historyCount: Short = 5

    @Column(name = "max_age_days")
    var maxAgeDays: Short? = null

    @Column(name = "lockout_threshold", nullable = false)
    var lockoutThreshold: Short = 5

    @Column(name = "lockout_minutes", nullable = false)
    var lockoutMinutes: Short = 15
}

@Repository
interface PasswordPolicyRepository : JpaRepository<PasswordPolicy, UUID>

/**
 * Validates passwords and supplies lockout parameters.
 *
 * A note on what this deliberately does *not* do: it does not impose mandatory periodic rotation
 * by default (`maxAgeDays` is null unless a customer sets it). Forced rotation is now widely
 * understood to reduce security in practice — people respond by incrementing a digit — and both
 * NIST SP 800-63B and the NCSC advise against it. The setting exists because some customers are
 * contractually obliged to enable it, not because we recommend it.
 */
@Service
class PasswordPolicyService(
    private val repository: PasswordPolicyRepository,
) {
    @Transactional(readOnly = true)
    fun current(): PasswordPolicy =
        repository.findById(TenantContext.currentId()).orElseGet {
            // Absent policy means "tenant has not configured one", so fall back to our defaults
            // rather than failing. The defaults are the strict ones.
            PasswordPolicy(TenantContext.currentId())
        }

    /**
     * Validates a candidate password, collecting *all* failures rather than stopping at the first.
     *
     * Reporting one rule at a time turns password creation into a guessing game — the user fixes
     * the length, resubmits, and is told about the digit. Returning the complete list lets the
     * client show everything at once.
     */
    fun validate(
        password: String,
        policy: PasswordPolicy = current(),
    ) {
        val failures = mutableListOf<String>()

        if (password.length < policy.minLength) failures += "MIN_LENGTH"
        if (policy.requireUppercase && password.none(Char::isUpperCase)) failures += "REQUIRE_UPPERCASE"
        if (policy.requireLowercase && password.none(Char::isLowerCase)) failures += "REQUIRE_LOWERCASE"
        if (policy.requireDigit && password.none(Char::isDigit)) failures += "REQUIRE_DIGIT"
        if (policy.requireSymbol && password.all { it.isLetterOrDigit() }) failures += "REQUIRE_SYMBOL"

        if (failures.isNotEmpty()) {
            throw BusinessRuleException(
                code = "PASSWORD_POLICY_VIOLATION",
                message = "Password does not meet the organisation's requirements",
                field = "password",
                details =
                    mapOf(
                        "failures" to failures,
                        "minLength" to policy.minLength,
                        "requireUppercase" to policy.requireUppercase,
                        "requireLowercase" to policy.requireLowercase,
                        "requireDigit" to policy.requireDigit,
                        "requireSymbol" to policy.requireSymbol,
                    ),
            )
        }
    }
}
