package com.hr.support

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Base class for tests that need a real PostgreSQL instance.
 *
 * Testcontainers rather than H2 is not negotiable here. The isolation boundary of this system is
 * PostgreSQL row-level security, `current_setting`, partitioned tables, `ltree` and `jsonb` — none
 * of which H2 emulates. A test suite that passes against H2 tells us nothing about whether tenant
 * isolation actually holds.
 *
 * The container is static, so one instance is shared across the whole suite rather than started
 * per class.
 */
@Testcontainers
abstract class PostgresTestBase {
    companion object {
        @JvmStatic
        protected val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16.8-alpine")
                .withDatabaseName("hr")
                .withUsername("hr_owner")
                .withPassword("hr_owner")
                // Mirrors infra/postgres/init: creates the non-owner runtime role that RLS
                // depends on.
                .withInitScript("db/testcontainers-init.sql")
                .also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            // Flyway migrates as the owner...
            registry.add("spring.flyway.url") { postgres.jdbcUrl }
            registry.add("spring.flyway.user") { "hr_owner" }
            registry.add("spring.flyway.password") { "hr_owner" }

            // ...the application connects as the non-owner role, which is subject to RLS.
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { "hr_app_login" }
            registry.add("spring.datasource.password") { "hr_app_login" }
        }
    }
}
