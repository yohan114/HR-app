package com.hr

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.lang.reflect.Method

/**
 * Asserts that the implementation and the published OpenAPI contract have not drifted apart.
 *
 * ## Why this exists
 *
 * The three generated clients guarantee that *consumers* match `spec/openapi.yaml`. Nothing
 * guaranteed that the *server* did. Without that second half, the spec can quietly become
 * fiction: an endpoint is renamed, the clients regenerate happily against the stale document, and
 * the mismatch surfaces as a 404 in a mobile beta weeks later.
 *
 * This closes the loop in both directions:
 *
 *  - **Documented but not implemented** — the clients ship a method that 404s.
 *  - **Implemented but not documented** — an endpoint exists that no client knows about and no
 *    reviewer has seen. This is the more dangerous direction: undocumented endpoints are how
 *    authorisation gaps ship.
 *  - **Path parameters that disagree** — `/{deviceId}` in the spec against `/{id}` in the code
 *    generates a client whose parameter name is a lie.
 *
 * ## Why it scans the classpath rather than booting Spring
 *
 * A `@SpringBootTest` would give the real `RequestMappingHandlerMapping`, but it needs a database.
 * A `@WebMvcTest` would need every controller's service dependencies mocked — meaning every new
 * controller adds friction to this test, which is exactly how a test ends up disabled.
 *
 * Classpath scanning needs neither. Adding a controller requires no change here at all.
 */
@DisplayName("API contract")
class ApiContractTest {
    companion object {
        private const val BASE_PACKAGE = "com.hr"

        /**
         * Endpoints legitimately absent from the public contract.
         *
         * Keep this list empty if at all possible. Every entry is an endpoint that exists in
         * production and that nobody reviewing the spec will ever see.
         */
        private val UNDOCUMENTED_BY_DESIGN = emptySet<String>()

        private lateinit var spec: OpenAPI

        @JvmStatic
        @BeforeAll
        fun parseSpec() {
            val specFile = File("../spec/openapi.yaml")
            check(specFile.exists()) { "Cannot find the OpenAPI spec at ${specFile.absolutePath}" }

            val options = ParseOptions().apply { isResolve = true }
            val result = OpenAPIV3Parser().readLocation(specFile.toURI().toString(), null, options)

            check(result.messages.isNullOrEmpty()) {
                "The OpenAPI spec does not parse cleanly:\n" + result.messages.joinToString("\n") { "  - $it" }
            }
            spec = requireNonNull(result.openAPI)
        }

        private fun <T> requireNonNull(value: T?): T = value ?: error("OpenAPI spec could not be parsed")
    }

    @Test
    fun `every documented operation is implemented`() {
        val missing = documentedOperations().keys - implementedOperations().keys

        assertThat(missing)
            .describedAs(
                """
                These operations are documented in spec/openapi.yaml but no controller implements them.
                The generated clients expose methods for each one, and every call will 404.

                Either implement them, or remove them from the spec.
                """.trimIndent(),
            )
            .isEmpty()
    }

    @Test
    fun `every implemented endpoint is documented`() {
        val undocumented = implementedOperations().keys - documentedOperations().keys - UNDOCUMENTED_BY_DESIGN

        assertThat(undocumented)
            .describedAs(
                """
                These endpoints exist in the application but are absent from spec/openapi.yaml.

                This is the more dangerous direction of drift: an undocumented endpoint has not
                been reviewed as part of the public surface, no client can reach it, and it is
                where authorisation gaps tend to hide.

                Document them, or add them to UNDOCUMENTED_BY_DESIGN with a justification.
                """.trimIndent(),
            )
            .isEmpty()
    }

    /**
     * Catches the case where spec and code agree on an endpoint's shape but disagree on what its
     * path variables are called — which produces a generated client whose parameter names do not
     * describe what they carry.
     */
    @Test
    fun `path parameter names agree between spec and implementation`() {
        val documented = documentedOperations()
        val implemented = implementedOperations()

        val mismatches =
            implemented.keys.intersect(documented.keys).mapNotNull { key ->
                val inCode = implemented.getValue(key).pathVariables
                val inSpec = documented.getValue(key).pathVariables
                if (inCode == inSpec) null else "$key — spec declares $inSpec, code declares $inCode"
            }

        assertThat(mismatches)
            .describedAs("Path variable names must match, or the generated clients misname their parameters")
            .isEmpty()
    }

    @Test
    fun `every documented operation has a unique operationId`() {
        val ids = documentedOperations().values.mapNotNull { it.operationId }
        val duplicates = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

        assertThat(duplicates)
            .describedAs("operationId becomes the generated client method name; duplicates silently collide")
            .isEmpty()
    }

    @Test
    fun `contract and implementation surfaces are reported`() {
        val documented = documentedOperations().keys.sorted()
        val implemented = implementedOperations().keys.sorted()

        println("Documented operations (${documented.size}):")
        documented.forEach { println("  $it") }
        println("Implemented endpoints (${implemented.size}):")
        implemented.forEach { println("  $it") }

        assertThat(documented).isNotEmpty()
        assertThat(implemented).isNotEmpty()
    }

    // -----------------------------------------------------------------------
    // Spec side
    // -----------------------------------------------------------------------

    private fun documentedOperations(): Map<String, DocumentedOperation> =
        spec.paths.orEmpty().flatMap { (path, item) ->
            item.readOperationsMap().map { (httpMethod, operation) ->
                val key = "${httpMethod.name} $path"
                key to DocumentedOperation(
                    key = key,
                    operationId = operation.operationId,
                    pathVariables = pathVariablesOf(path),
                )
            }
        }.toMap()

    // -----------------------------------------------------------------------
    // Implementation side
    // -----------------------------------------------------------------------

    private fun implementedOperations(): Map<String, ImplementedEndpoint> {
        val scanner =
            ClassPathScanningCandidateComponentProvider(false).apply {
                addIncludeFilter(AnnotationTypeFilter(RestController::class.java))
            }

        return scanner.findCandidateComponents(BASE_PACKAGE)
            .map { Class.forName(it.beanClassName) }
            .flatMap { controller ->
                val classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping::class.java)
                val basePaths = classMapping?.pathsOrEmpty() ?: listOf("")

                controller.declaredMethods.flatMap { method ->
                    endpointsOf(method, basePaths)
                }
            }
            .associateBy { it.key }
    }

    private fun endpointsOf(
        method: Method,
        basePaths: List<String>,
    ): List<ImplementedEndpoint> {
        val mapping =
            AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping::class.java)
                ?: return emptyList()

        val methodPaths = mapping.pathsOrEmpty().ifEmpty { listOf("") }
        val httpMethods =
            mapping.method.map { it.name }.ifEmpty {
                error(
                    "${method.declaringClass.simpleName}.${method.name} declares a mapping with no HTTP method. " +
                        "Use @GetMapping/@PostMapping/etc. rather than a bare @RequestMapping.",
                )
            }

        // The declared @PathVariable names, which is what actually binds at runtime — a template
        // variable in the path with no matching parameter would fail on the first request.
        val declaredVariables =
            method.parameters
                .mapNotNull { it.getAnnotation(PathVariable::class.java) to it }
                .filter { it.first != null }
                .map { (annotation, parameter) ->
                    annotation!!.value.ifBlank { annotation.name.ifBlank { parameter.name } }
                }
                .toSortedSet()

        return basePaths.flatMap { base ->
            methodPaths.flatMap { suffix ->
                val fullPath = joinPaths(base, suffix)
                httpMethods.map { httpMethod ->
                    ImplementedEndpoint(
                        key = "$httpMethod $fullPath",
                        // Prefer the names bound in code; fall back to the template when a
                        // handler takes no @PathVariable (which would itself be a bug).
                        pathVariables = declaredVariables.ifEmpty { pathVariablesOf(fullPath) },
                    )
                }
            }
        }
    }

    private fun RequestMapping.pathsOrEmpty(): List<String> =
        (path.takeIf { it.isNotEmpty() } ?: value).toList()

    private fun joinPaths(
        base: String,
        suffix: String,
    ): String {
        val combined = "${base.trimEnd('/')}/${suffix.trimStart('/')}"
        return combined.trimEnd('/').ifEmpty { "/" }
    }

    private fun pathVariablesOf(path: String): Set<String> =
        PATH_VARIABLE.findAll(path).map { it.groupValues[1] }.toSortedSet()

    private data class DocumentedOperation(
        val key: String,
        val operationId: String?,
        val pathVariables: Set<String>,
    )

    private data class ImplementedEndpoint(
        val key: String,
        val pathVariables: Set<String>,
    )
}

private val PATH_VARIABLE = Regex("""\{([^}:]+)(?::[^}]+)?}""")
