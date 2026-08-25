package com.hr.shared.observability

/**
 * The application's metric names, in one place.
 *
 * ## Why constants rather than string literals at the call site
 *
 * These names are referenced by three things that live outside this codebase:
 * the alert rules in `infra/k8s/observability/alerts-*.yaml`, the Grafana
 * dashboards, and any query someone types during an incident. A rename that
 * compiles cleanly would silently break every one of them — the dashboard
 * would show a flat line and the alert would simply never fire, which is worse
 * than an error because nobody notices.
 *
 * `MetricsContractTest` cross-references these constants against the alert
 * rules and dashboards, so a rename fails the build instead.
 *
 * ## Naming
 *
 * Prometheus convention: `hr_<domain>_<thing>_<unit>`, counters suffixed
 * `_total`. Micrometer translates dots to underscores for the Prometheus
 * registry, so `hr.payroll.run` is exported as `hr_payroll_run_total` — the
 * constants below are the *Micrometer* names, and the exported names are what
 * the alerts use.
 */
object Metrics {
    // -----------------------------------------------------------------------
    // Errors
    // -----------------------------------------------------------------------

    /** Every API error, tagged by its machine-readable code. Exported: `hr_errors_total`. */
    const val ERRORS = "hr.errors"

    // -----------------------------------------------------------------------
    // Authentication
    // -----------------------------------------------------------------------

    /**
     * Refresh token reuse detections. Exported: `hr_auth_token_reuse_detected_total`.
     *
     * Each one revokes an entire token family. A sustained rate means either a
     * client bug replaying requests or tokens being stolen.
     */
    const val AUTH_TOKEN_REUSE_DETECTED = "hr.auth.token.reuse.detected"

    /** Login attempts, tagged by method and result. Exported: `hr_auth_login_total`. */
    const val AUTH_LOGIN = "hr.auth.login"

    // -----------------------------------------------------------------------
    // Payroll
    //
    // The only metrics carrying a `tenant` tag. See the cardinality note below.
    // -----------------------------------------------------------------------

    /** Completed payroll runs, tagged by result. Exported: `hr_payroll_run_total`. */
    const val PAYROLL_RUN = "hr.payroll.run"

    /** Duration of the current run by phase. Exported: `hr_payroll_run_duration_seconds`. */
    const val PAYROLL_RUN_DURATION = "hr.payroll.run.duration"

    /** Results flagged as anomalous against the prior period. Exported: `hr_payroll_anomalies_total`. */
    const val PAYROLL_ANOMALIES = "hr.payroll.anomalies"

    /** Per-employee results produced. Exported: `hr_payroll_results_total`. */
    const val PAYROLL_RESULTS = "hr.payroll.results"

    /**
     * Committed runs whose input snapshot could not be retrieved.
     * Exported: `hr_payroll_snapshot_missing_total`.
     *
     * Should always be zero. A non-zero value means a committed run is no
     * longer reproducible, which is a compliance problem as well as a
     * technical one.
     */
    const val PAYROLL_SNAPSHOT_MISSING = "hr.payroll.snapshot.missing"

    // -----------------------------------------------------------------------
    // Attendance and sync
    // -----------------------------------------------------------------------

    /** Punches ingested, tagged by source. Exported: `hr_attendance_punch_total`. */
    const val ATTENDANCE_PUNCH = "hr.attendance.punch"

    /** Gauge: punches awaiting processing. Exported: `hr_attendance_unprocessed_punches`. */
    const val ATTENDANCE_UNPROCESSED_PUNCHES = "hr.attendance.unprocessed.punches"

    /**
     * Outbox entries that exhausted their seven-day retry deadline.
     * Exported: `hr_outbox_failed_total`.
     *
     * Each one is a user action that was shown as queued and never arrived.
     */
    const val OUTBOX_FAILED = "hr.outbox.failed"

    /** Clients forced into a full resync by an expired cursor. Exported: `hr_sync_cursor_expired_total`. */
    const val SYNC_CURSOR_EXPIRED = "hr.sync.cursor.expired"

    // -----------------------------------------------------------------------
    // Workflow and notifications
    // -----------------------------------------------------------------------

    /** Gauge: approval tasks past their SLA. Exported: `hr_workflow_tasks_overdue`. */
    const val WORKFLOW_TASKS_OVERDUE = "hr.workflow.tasks.overdue"

    /** Notification sends, tagged by channel and status. Exported: `hr_notification_delivery_total`. */
    const val NOTIFICATION_DELIVERY = "hr.notification.delivery"

    // -----------------------------------------------------------------------
    // Tags
    // -----------------------------------------------------------------------

    object Tag {
        const val CODE = "code"
        const val RESULT = "result"
        const val METHOD = "method"
        const val PHASE = "phase"
        const val SOURCE = "source"
        const val STATUS = "status"
        const val CHANNEL = "channel"
        const val WORKFLOW_TYPE = "workflow_type"

        /**
         * Tenant identifier.
         *
         * **Restricted on purpose.** A Prometheus time series exists for every
         * combination of labels, so tagging a high-frequency metric with a
         * tenant id multiplies its series count by the number of customers.
         * At a thousand tenants, `hr_errors_total{code, tenant}` alone would
         * be tens of thousands of series — enough to make Prometheus the most
         * expensive component in the estate, and slow enough to be useless
         * during the incident you added it for.
         *
         * It is permitted only on the metrics in [TENANT_TAGGED_METRICS]:
         * payroll (monthly, bounded by tenants actually running one) and
         * workflow overdue counts (a single gauge per tenant). Both are
         * genuinely per-tenant questions that cannot be answered otherwise.
         *
         * For everything else, per-tenant investigation goes through traces
         * and logs, which carry the tenant in the MDC and are indexed for it.
         */
        const val TENANT = "tenant"
    }

    /**
     * Metrics permitted to carry a [Tag.TENANT] label.
     *
     * Enforced by [TenantTagPolicy]. Adding to this set is a deliberate
     * decision about cardinality, not a convenience.
     */
    val TENANT_TAGGED_METRICS: Set<String> =
        setOf(
            PAYROLL_RUN,
            PAYROLL_RUN_DURATION,
            PAYROLL_ANOMALIES,
            PAYROLL_RESULTS,
            WORKFLOW_TASKS_OVERDUE,
        )

    /**
     * Meter kind, which determines the suffix Prometheus exports.
     *
     * Getting this wrong is not cosmetic: a query for `hr_payroll_run_duration`
     * against a metric exported as `hr_payroll_run_duration_seconds` matches
     * nothing, and the alert silently never fires. `MetricsContractTest` caught
     * exactly that mistake in an earlier revision of this file.
     */
    enum class Kind {
        /** Exported with a `_total` suffix. */
        COUNTER,

        /** Exported bare — no suffix. */
        GAUGE,

        /** Exported with the base unit appended, i.e. `_seconds`. */
        TIMER,
    }

    /**
     * Every metric and its kind. The contract test uses this to derive exported
     * names, so adding a metric here is what makes it alertable and chartable.
     */
    val ALL: Map<String, Kind> =
        mapOf(
            ERRORS to Kind.COUNTER,
            AUTH_TOKEN_REUSE_DETECTED to Kind.COUNTER,
            AUTH_LOGIN to Kind.COUNTER,
            PAYROLL_RUN to Kind.COUNTER,
            PAYROLL_RUN_DURATION to Kind.TIMER,
            PAYROLL_ANOMALIES to Kind.COUNTER,
            PAYROLL_RESULTS to Kind.COUNTER,
            PAYROLL_SNAPSHOT_MISSING to Kind.COUNTER,
            ATTENDANCE_PUNCH to Kind.COUNTER,
            ATTENDANCE_UNPROCESSED_PUNCHES to Kind.GAUGE,
            OUTBOX_FAILED to Kind.COUNTER,
            SYNC_CURSOR_EXPIRED to Kind.COUNTER,
            WORKFLOW_TASKS_OVERDUE to Kind.GAUGE,
            NOTIFICATION_DELIVERY to Kind.COUNTER,
        )

    /**
     * The name Prometheus exports for a Micrometer metric.
     *
     * Dots become underscores; the suffix depends on the meter kind. The alert
     * rules and dashboards use these exported names, so the contract test
     * translates through here rather than guessing.
     */
    fun exportedName(micrometerName: String): String {
        val base = micrometerName.replace('.', '_')
        return when (ALL[micrometerName]) {
            Kind.COUNTER -> "${base}_total"
            Kind.TIMER -> "${base}_seconds"
            Kind.GAUGE -> base
            null -> base
        }
    }
}
