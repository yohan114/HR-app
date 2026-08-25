package com.hr.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import java.util.Optional
import java.util.UUID

/**
 * Cross-cutting persistence configuration.
 *
 * The tenant-aware DataSource is *not* configured here — it belongs to the tenancy module. See
 * [com.hr.tenancy.internal.TenancyDataSourceConfig].
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
class PersistenceConfig {
    /**
     * Supplies `created_by` / `updated_by` for [com.hr.shared.persistence.BaseEntity].
     *
     * Returns empty for unauthenticated writes — migrations, scheduled jobs, and the login flow
     * itself. That leaves the column NULL rather than inventing a synthetic "system" user id,
     * which would be indistinguishable from a real one in an audit review.
     */
    @Bean
    fun auditorAware(): AuditorAware<UUID> =
        AuditorAware {
            val principal = SecurityContextHolder.getContext().authentication?.principal
            val userId =
                if (principal is Jwt) {
                    principal.getClaimAsString("sub")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                } else {
                    null
                }
            Optional.ofNullable(userId)
        }
}
