package com.hr.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableMethodSecurity
class SecurityConfig {
    /**
     * @param jwtAuthenticationConverter supplied by the identity module. Injected by its Spring
     *   framework interface type rather than its concrete class, so this configuration does not
     *   depend on another module's internals — `ModuleStructureTest` would reject that, correctly.
     */
    @Bean
    fun filterChain(
        http: HttpSecurity,
        jwtAuthenticationConverter: Converter<Jwt, out AbstractAuthenticationToken>,
    ): SecurityFilterChain {
        http
            // No cookies, no server-side session — the API is stateless and token-based, so
            // CSRF does not apply. (If we ever add a cookie-authenticated surface, this must
            // be revisited rather than left off.)
            .csrf { it.disable() }
            .cors { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                    // The pre-authentication surface: resolve an org, sign in, refresh, unlock
                    // with biometrics. Note these are enumerated individually rather than
                    // permitting all of /v1/auth/** — device management lives under the same
                    // prefix and must stay authenticated.
                    .requestMatchers(
                        HttpMethod.POST,
                        "/v1/auth/resolve-tenant",
                        "/v1/auth/token",
                        "/v1/auth/token/refresh",
                        "/v1/auth/token/biometric",
                        "/v1/auth/logout",
                        // The second half of a password sign-in. Its caller holds an MFA challenge
                        // token and no session, because the password step deliberately issued
                        // none. The challenge is validated inside the handler, where the `purpose`
                        // claim can be checked — a filter here would accept any valid JWT, which
                        // would let an existing session mint a new one without a second factor.
                        "/v1/auth/mfa/verify",
                    ).permitAll()
                    .requestMatchers(HttpMethod.GET, "/v1/auth/.well-known/jwks.json").permitAll()
                    .requestMatchers("/v1/public/**").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter) }
            }
        return http.build()
    }

    /**
     * Argon2id password hashing.
     *
     * Parameters follow the OWASP Password Storage Cheat Sheet's Argon2id recommendation:
     * 19 MiB memory, 2 iterations, 1 degree of parallelism, 16-byte salt, 32-byte hash.
     *
     * Memory cost is the parameter that matters against GPU attack, which is why it is high
     * relative to the iteration count. These values must be re-benchmarked against production
     * hardware, and are versioned in the stored hash so they can be raised later without
     * invalidating existing passwords.
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder =
        Argon2PasswordEncoder(
            SALT_LENGTH_BYTES,
            HASH_LENGTH_BYTES,
            PARALLELISM,
            MEMORY_KIB,
            ITERATIONS,
        )

    private companion object {
        const val SALT_LENGTH_BYTES = 16
        const val HASH_LENGTH_BYTES = 32
        const val PARALLELISM = 1
        const val MEMORY_KIB = 19 * 1024
        const val ITERATIONS = 2
    }
}
