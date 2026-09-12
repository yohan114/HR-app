package com.hr

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.reflect.Method

/**
 * Asserts that every controller endpoint is explicitly protected by an authorization rule
 * (@PreAuthorize at class or method level) or explicitly declared on the public allowlist.
 *
 * This acts as an automated build breaker (P0-BE-16) to prevent accidental introduction of
 * unauthenticated or unpermissioned endpoints.
 */
@DisplayName("API authorisation coverage")
class ApiAuthorisationTest {

    companion object {
        private const val BASE_PACKAGE = "com.hr"

        /**
         * Endpoints intentionally reachable without an authenticated user session.
         *
         * Every entry must correspond to an explicitly public endpoint configured in
         * SecurityConfig or device-authenticated endpoint (like ZKTeco ADMS).
         */
        private val PUBLIC_ALLOWLIST_PATTERNS = listOf(
            "POST /v1/auth/token",
            "POST /v1/auth/token/refresh",
            "POST /v1/auth/token/biometric",
            "POST /v1/auth/resolve-tenant",
            "POST /v1/auth/logout",
            "POST /v1/auth/mfa/verify",
            "GET /v1/auth/.well-known/jwks.json",
        )

        private val PUBLIC_ALLOWLIST_PREFIXES = listOf(
            "/iclock/",
            "/actuator/",
            "/v1/public/",
        )
    }

    @Test
    fun `every controller endpoint is protected by PreAuthorize or listed on the public allowlist`() {
        val scanner = ClassPathScanningCandidateComponentProvider(false).apply {
            addIncludeFilter(AnnotationTypeFilter(RestController::class.java))
        }

        val unprotectedEndpoints = mutableListOf<String>()

        val controllers = scanner.findCandidateComponents(BASE_PACKAGE)
            .map { Class.forName(it.beanClassName) }

        for (controller in controllers) {
            val classPreAuthorize = AnnotatedElementUtils.findMergedAnnotation(controller, PreAuthorize::class.java)
            val classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping::class.java)
            val basePaths = (classMapping?.path?.takeIf { it.isNotEmpty() } ?: classMapping?.value)?.toList() ?: listOf("")

            for (method in controller.declaredMethods) {
                val methodMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping::class.java)
                    ?: continue

                val methodPreAuthorize = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize::class.java)
                val isProtected = classPreAuthorize != null || methodPreAuthorize != null

                if (!isProtected) {
                    val methodPaths = (methodMapping.path.takeIf { it.isNotEmpty() } ?: methodMapping.value).toList().ifEmpty { listOf("") }
                    val httpMethods = methodMapping.method.map { it.name }.ifEmpty { listOf("ALL") }

                    for (base in basePaths) {
                        for (suffix in methodPaths) {
                            val fullPath = joinPaths(base, suffix)
                            for (httpMethod in httpMethods) {
                                val endpointKey = "$httpMethod $fullPath"
                                if (!isAllowlisted(endpointKey, fullPath)) {
                                    unprotectedEndpoints.add(
                                        "${controller.simpleName}.${method.name} -> $endpointKey"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        assertThat(unprotectedEndpoints)
            .describedAs(
                """
                The following endpoints are neither annotated with @PreAuthorize (class or method level)
                nor present on the explicit public allowlist.
                
                Every API endpoint must enforce authorization or be explicitly documented as public.
                """.trimIndent()
            )
            .isEmpty()
    }

    private fun isAllowlisted(endpointKey: String, fullPath: String): Boolean {
        if (PUBLIC_ALLOWLIST_PATTERNS.contains(endpointKey)) {
            return true
        }
        return PUBLIC_ALLOWLIST_PREFIXES.any { prefix -> fullPath.startsWith(prefix) }
    }

    private fun joinPaths(base: String, suffix: String): String {
        val combined = "${base.trimEnd('/')}/${suffix.trimStart('/')}"
        return combined.trimEnd('/').ifEmpty { "/" }
    }
}
