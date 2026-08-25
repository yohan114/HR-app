package com.hr.tenancy.internal

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import javax.sql.DataSource

/**
 * Wires the tenant-aware DataSource.
 *
 * This lives inside the tenancy module rather than in a central config package on purpose:
 * binding a tenant to a database connection is the tenancy module's responsibility, and
 * [TenantAwareDataSource] is an implementation detail that nothing outside this module should be
 * able to reach. `ModuleStructureTest` enforces that — an earlier revision declared these beans
 * in `com.hr.config` and the boundary check correctly rejected it.
 */
@Configuration
class TenancyDataSourceConfig {
    /**
     * The real connection pool.
     *
     * Deliberately not `@Primary`. Everything in the application must go through the wrapper
     * below; this bean exists only so the wrapper has something to delegate to.
     */
    @Bean(name = ["rawDataSource"])
    @ConfigurationProperties("spring.datasource.hikari")
    fun rawDataSource(properties: DataSourceProperties): HikariDataSource =
        properties
            .initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()

    /**
     * The DataSource every repository and JDBC template receives.
     *
     * Wrapping at the DataSource level, rather than in a transaction listener or an aspect, means
     * there is no code path in the application — not JPA, not raw JDBC, not a scheduled job — that
     * can obtain a connection without the tenant binding being applied first.
     */
    @Bean
    @Primary
    fun dataSource(
        @Qualifier("rawDataSource") raw: HikariDataSource,
    ): DataSource = TenantAwareDataSource(raw)
}
