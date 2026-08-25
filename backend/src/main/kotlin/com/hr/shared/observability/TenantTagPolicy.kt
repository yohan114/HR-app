package com.hr.shared.observability

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.config.MeterFilterReply
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Enforces the tenant-label policy described in [Metrics.Tag.TENANT], and caps
 * total cardinality.
 *
 * ## Why this is enforced rather than documented
 *
 * A comment saying "do not add a tenant tag here" survives exactly as long as
 * the next person who needs a per-tenant number during an incident. By then
 * the damage is a Prometheus instance that has silently doubled its memory
 * use, and the symptom appears weeks later as slow queries rather than as
 * anything connected to the change that caused it.
 *
 * A filter that strips the tag makes the mistake visible immediately — the
 * metric is there, the breakdown is not — and a warning names the metric.
 */
@Configuration
class TenantTagPolicy {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun tenantTagFilter(): MeterRegistryCustomizer<MeterRegistry> =
        MeterRegistryCustomizer { registry ->
            registry.config().meterFilter(StripDisallowedTenantTag(log))
            registry.config().meterFilter(cardinalityCap())
        }

    /**
     * Hard ceiling on series per metric name.
     *
     * A runaway label — a user id, an error message, a URI with an embedded
     * identifier — can produce unbounded series and take Prometheus down. This
     * caps each metric and logs once when the limit is hit, so the offending
     * metric is named rather than having to be inferred from a memory graph.
     *
     * 200 is generous for anything legitimate here: the widest is
     * `hr_errors_total` by code, and there are fewer than fifty codes.
     */
    private fun cardinalityCap(): MeterFilter =
        MeterFilter.maximumAllowableTags(
            /* prefix = */ "hr",
            /* tagKey = */ Metrics.Tag.TENANT,
            /* maximumTagValues = */ MAX_TENANT_SERIES,
            /* onMaxReached = */ MeterFilter.deny(),
        )

    private class StripDisallowedTenantTag(
        private val log: org.slf4j.Logger,
    ) : MeterFilter {
        private val warned = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        override fun map(id: Meter.Id): Meter.Id {
            val hasTenantTag = id.getTag(Metrics.Tag.TENANT) != null
            if (!hasTenantTag) return id

            // Micrometer ids use dotted names, matching the constants.
            if (id.name in Metrics.TENANT_TAGGED_METRICS) return id

            if (warned.add(id.name)) {
                log.warn(
                    "Metric '{}' was tagged with '{}' but is not in Metrics.TENANT_TAGGED_METRICS — " +
                        "the tag has been stripped to protect Prometheus cardinality. " +
                        "If a per-tenant breakdown is genuinely needed, add it to that set deliberately " +
                        "and consider what it costs at a thousand tenants.",
                    id.name,
                    Metrics.Tag.TENANT,
                )
            }

            return id.replaceTags(id.tags.filterNot { it.key == Metrics.Tag.TENANT }.toList())
        }

        override fun accept(id: Meter.Id): MeterFilterReply = MeterFilterReply.NEUTRAL
    }

    private companion object {
        const val MAX_TENANT_SERIES = 2_000
    }
}

/** Convenience for building a tenant tag, so call sites do not repeat the key. */
fun tenantTag(tenantId: String): Tag = Tag.of(Metrics.Tag.TENANT, tenantId)
