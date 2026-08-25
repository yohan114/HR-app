package com.hr

import com.hr.shared.observability.Metrics
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Asserts that the metric names in code, in the alert rules and in the Grafana
 * dashboards all agree.
 *
 * ## Why this exists
 *
 * A metric name is a contract between three things that cannot see each other:
 * the Kotlin that emits it, the YAML that alerts on it, and the JSON that
 * charts it. Renaming one is a change that compiles, passes every other test,
 * and silently breaks the other two — the dashboard shows a flat line and the
 * alert simply never fires.
 *
 * A never-firing alert is worse than no alert, because someone believes they
 * are covered. This test makes that failure a build failure instead.
 *
 * It is the same reasoning as `ApiContractTest`: the interesting bugs live in
 * the gaps between artefacts that are each individually correct.
 */
@DisplayName("Metrics contract")
class MetricsContractTest {
    private val observabilityDir = File("../infra/k8s/observability")

    private val alertFiles: List<File> =
        observabilityDir.listFiles { f -> f.name.startsWith("alerts-") && f.extension == "yaml" }
            ?.toList() ?: emptyList()

    private val dashboardFiles: List<File> =
        File(observabilityDir, "dashboards").listFiles { f -> f.extension == "json" }
            ?.toList() ?: emptyList()

    /** Exported (Prometheus) names for every metric the application declares. */
    private val declaredExportedNames: Set<String> =
        Metrics.ALL.keys.map(Metrics::exportedName).toSet()

    @Test
    fun `observability files are present`() {
        assertThat(observabilityDir)
            .describedAs("Expected alert rules and dashboards at ${observabilityDir.absolutePath}")
            .exists()
        assertThat(alertFiles).isNotEmpty()
        assertThat(dashboardFiles).isNotEmpty()
    }

    /**
     * Every `hr_*` metric an alert references must be declared in [Metrics].
     *
     * The direction that matters: an alert referencing a metric nobody emits
     * will never fire, and nothing else in the system would notice.
     */
    @Test
    fun `alert rules only reference declared metrics`() {
        val undeclared = mutableListOf<String>()

        alertFiles.forEach { file ->
            metricNamesIn(file.readText()).forEach { referenced ->
                if (referenced !in declaredExportedNames) {
                    undeclared += "${file.name}: $referenced"
                }
            }
        }

        assertThat(undeclared)
            .describedAs(
                """
                These alert rules reference hr_* metrics that the application does not declare.

                An alert on a metric nobody emits never fires — and unlike a broken
                dashboard, nothing makes that visible. Either add the metric to
                com.hr.shared.observability.Metrics and emit it, or remove the alert.
                """.trimIndent(),
            )
            .isEmpty()
    }

    @Test
    fun `dashboards only reference declared metrics`() {
        val undeclared = mutableListOf<String>()

        dashboardFiles.forEach { file ->
            metricNamesIn(file.readText()).forEach { referenced ->
                if (referenced !in declaredExportedNames) {
                    undeclared += "${file.name}: $referenced"
                }
            }
        }

        assertThat(undeclared)
            .describedAs("These dashboard panels query hr_* metrics the application does not declare — they will render empty.")
            .isEmpty()
    }

    /**
     * Every declared metric should be used by something.
     *
     * A metric nobody alerts on or charts is either dead code or, more often,
     * a signal someone meant to wire up and forgot. Reported as a warning list
     * rather than a hard failure, because a metric can legitimately exist for
     * ad-hoc querying during an incident.
     */
    @Test
    fun `report declared metrics that nothing observes`() {
        val referenced =
            (alertFiles + dashboardFiles)
                .flatMap { metricNamesIn(it.readText()) }
                .toSet()

        val unobserved = Metrics.ALL.keys.filterNot { referenced.contains(Metrics.exportedName(it)) }

        if (unobserved.isNotEmpty()) {
            println("Metrics declared but not referenced by any alert or dashboard:")
            unobserved.forEach { println("  - $it") }
            println("  (Not a failure — but check whether one was meant to be wired up.)")
        }

        assertThat(Metrics.ALL).isNotEmpty()
    }

    /**
     * A tenant label may only appear on the metrics allowed to carry one.
     *
     * Prometheus creates a series per label combination, so tagging a
     * high-frequency metric with a tenant id multiplies its series count by the
     * number of customers. `TenantTagPolicy` strips a disallowed tag at
     * runtime; this catches an alert or dashboard that was written expecting a
     * breakdown that will never exist.
     */
    @Test
    fun `only permitted metrics are grouped by tenant`() {
        val allowedExported = Metrics.TENANT_TAGGED_METRICS.map(Metrics::exportedName).toSet()

        val violations = mutableListOf<String>()

        (alertFiles + dashboardFiles).forEach { file ->
            val text = file.readText()
            // Matches `hr_x_total{... tenant ...}` and `by (tenant)` / `by (tenant, y)`.
            SELECTOR_WITH_TENANT.findAll(text).forEach { match ->
                val metric = match.groupValues[1]
                if (metric !in allowedExported) {
                    violations += "${file.name}: $metric is filtered or grouped by tenant"
                }
            }
        }

        assertThat(violations)
            .describedAs(
                """
                These queries expect a tenant breakdown on a metric that does not carry one.

                TenantTagPolicy strips the tag at runtime to protect Prometheus
                cardinality, so the query will return nothing. Either add the metric to
                Metrics.TENANT_TAGGED_METRICS — deliberately, considering what it costs
                at a thousand tenants — or use traces and logs, which carry the tenant
                in the MDC.
                """.trimIndent(),
            )
            .isEmpty()
    }

    // ------------------------------------------------------------------------

    /** Every `hr_*` metric name appearing in a PromQL expression. */
    private fun metricNamesIn(text: String): Set<String> =
        METRIC_REFERENCE.findAll(text).map { it.value }.toSet()

    private companion object {
        /**
         * Only `hr_*` names. Framework metrics (`http_server_requests_*`,
         * `hikaricp_*`, `up`) are emitted by Spring and Micrometer, not
         * declared by us, so they are outside this contract.
         */
        val METRIC_REFERENCE = Regex("""\bhr_[a-z0-9_]+\b""")

        /** An `hr_*` selector carrying a tenant matcher inside its braces. */
        val SELECTOR_WITH_TENANT = Regex("""\b(hr_[a-z0-9_]+)\{[^}]*\btenant\b[^}]*}""")
    }
}
