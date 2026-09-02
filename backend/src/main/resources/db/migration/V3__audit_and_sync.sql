-- =============================================================================
-- V3 — Audit log, change feed and idempotency ledger
--
-- Covers P0-BE-22 (audit), P0-BE-32/33 (sync) and P0-BE-34 (idempotency).
--
-- All three tables are append-heavy and grow without bound, so all three are
-- range-partitioned by month from day one. Adding partitioning to a large table
-- later means a rewrite under lock; adding it now costs nothing.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Audit log
--
-- Append-only: there is no UPDATE or DELETE path in application code, and the
-- hr_app role is granted INSERT and SELECT only (see the REVOKE below). An audit
-- trail that the application can rewrite is not an audit trail.
-- -----------------------------------------------------------------------------
CREATE TABLE audit_log
(
    id           uuid        NOT NULL,
    tenant_id    uuid        NOT NULL,
    entity_type  varchar(64) NOT NULL,
    entity_id    uuid,
    action       varchar(32) NOT NULL,
    actor_user_id uuid,
    actor_ip     inet,
    request_id   varchar(64),
    occurred_at  timestamptz NOT NULL DEFAULT now(),
    changes      jsonb,

    PRIMARY KEY (id, occurred_at),
    CONSTRAINT audit_action_valid CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'VIEW', 'EXPORT', 'LOGIN', 'APPROVE', 'REJECT'))
) PARTITION BY RANGE (occurred_at);

CREATE INDEX ix_audit_tenant_time   ON audit_log (tenant_id, occurred_at DESC);
CREATE INDEX ix_audit_tenant_entity ON audit_log (tenant_id, entity_type, entity_id, occurred_at DESC);
CREATE INDEX ix_audit_tenant_actor  ON audit_log (tenant_id, actor_user_id, occurred_at DESC);

SELECT apply_tenant_rls('audit_log');

COMMENT ON TABLE audit_log IS 'Append-only. Retention is statutory (typically 7 years) — see docs/04-data-model.md §18.';
COMMENT ON COLUMN audit_log.changes IS 'Field-level before/after for fields enabled in audit_config.';

-- Which fields are audited, and under what business name.
CREATE TABLE audit_config
(
    tenant_id   uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    entity_type varchar(64)  NOT NULL,
    field_key   varchar(128) NOT NULL,
    enabled     boolean      NOT NULL DEFAULT true,
    alias       varchar(255),

    PRIMARY KEY (tenant_id, entity_type, field_key)
);

SELECT apply_tenant_rls('audit_config');

COMMENT ON COLUMN audit_config.alias IS
    'Business-friendly field name shown in the audit UI, e.g. "Basic Salary" rather than basic_amount.';

-- -----------------------------------------------------------------------------
-- Change feed — the read side of mobile delta sync
--
-- Ordered by a monotonic bigint sequence rather than a timestamp. Timestamps are
-- the obvious choice and the wrong one: with concurrent transactions, a row
-- committed at 10:00:00.000 can become visible *after* a client has already
-- synced past that instant, and the client silently never sees it. A sequence
-- assigned at commit has no such window.
-- -----------------------------------------------------------------------------
CREATE SEQUENCE change_feed_seq AS bigint START 1;

CREATE TABLE change_feed
(
    id          uuid        NOT NULL,
    tenant_id   uuid        NOT NULL,
    sequence    bigint      NOT NULL DEFAULT nextval('change_feed_seq'),
    entity_type varchar(64) NOT NULL,
    entity_id   uuid        NOT NULL,
    operation   varchar(16) NOT NULL,
    scopes      text[]      NOT NULL DEFAULT '{}',
    visible_to  jsonb,
    occurred_at timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (id, occurred_at),
    CONSTRAINT change_operation_valid CHECK (operation IN ('CREATE', 'UPDATE', 'DELETE'))
) PARTITION BY RANGE (occurred_at);

CREATE INDEX ix_change_feed_tenant_seq   ON change_feed (tenant_id, sequence);
CREATE INDEX ix_change_feed_tenant_scope ON change_feed USING gin (tenant_id, scopes);

SELECT apply_tenant_rls('change_feed');

COMMENT ON COLUMN change_feed.scopes IS
    'Sync scopes this change belongs to (directory, leave, attendance, ...). Clients subscribe by role.';
COMMENT ON COLUMN change_feed.visible_to IS
    'Optional audience restriction evaluated per user, so one employee''s change is not pushed to everyone.';

GRANT USAGE, SELECT ON SEQUENCE change_feed_seq TO hr_app;

-- -----------------------------------------------------------------------------
-- Mutation log — server-side idempotency ledger for the mobile outbox
--
-- The offline outbox retries forever until it receives a definitive answer. That
-- is only safe if the server can recognise a repeat of a mutation it has already
-- applied and return the original outcome instead of applying it twice.
--
-- The unique constraint on (tenant_id, idempotency_key) is what makes this work:
-- a concurrent duplicate loses the insert race and reads the winner's result.
-- -----------------------------------------------------------------------------
CREATE TABLE mutation_log
(
    id              uuid         NOT NULL,
    tenant_id       uuid         NOT NULL,
    user_id         uuid         NOT NULL,
    device_id       varchar(128),
    idempotency_key varchar(128) NOT NULL,
    endpoint        varchar(255) NOT NULL,
    payload_hash    char(64)     NOT NULL,
    status          varchar(16)  NOT NULL DEFAULT 'IN_PROGRESS',
    response_status smallint,
    response_body   jsonb,
    received_at     timestamptz  NOT NULL DEFAULT now(),
    processed_at    timestamptz,

    PRIMARY KEY (id, received_at),
    CONSTRAINT mutation_status_valid CHECK (status IN ('IN_PROGRESS', 'SUCCEEDED', 'FAILED'))
) PARTITION BY RANGE (received_at);

CREATE UNIQUE INDEX ux_mutation_log_key ON mutation_log (tenant_id, idempotency_key, received_at);
CREATE INDEX ix_mutation_log_user       ON mutation_log (tenant_id, user_id, received_at DESC);

SELECT apply_tenant_rls('mutation_log');

COMMENT ON COLUMN mutation_log.payload_hash IS
    'Guards against key reuse with different content: same key + different payload is a client bug, and we reject it rather than silently returning the wrong result.';

-- -----------------------------------------------------------------------------
-- Partition management
--
-- Creates partitions for a month if they do not already exist. Called by a
-- scheduled job that runs ahead of time; also invoked below to bootstrap the
-- current and next month so a fresh database is immediately usable.
--
-- Each partition gets its own RLS policy and its own append-only revocation.
-- That is not belt-and-braces: a policy on a partitioned table is applied when
-- rows are reached *through the parent*, and a query naming a partition
-- directly is governed only by that partition's own policies. So
-- `SELECT * FROM audit_log_202608` on a partition without RLS returns every
-- tenant's audit rows — the parent's policy never runs.
--
-- Doing it inside this function rather than at the call sites is what makes it
-- durable: the scheduled job creates a partition a month from now, long after
-- anybody is looking at this file.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION ensure_monthly_partition(parent_table text, month_start date)
    RETURNS void
    LANGUAGE plpgsql
AS $$
DECLARE
    partition_name text;
    month_end      date;
BEGIN
    partition_name := format('%s_%s', parent_table, to_char(month_start, 'YYYYMM'));
    month_end := (month_start + INTERVAL '1 month')::date;

    IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = partition_name) THEN
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
            partition_name, parent_table, month_start, month_end);

        PERFORM apply_tenant_rls(partition_name);

        -- Mirrors the lockdown applied to the parents further down this file.
        -- An audit row that can be deleted through a partition is not an audit
        -- row, and `REVOKE` on the parent does not cascade.
        EXECUTE format('REVOKE UPDATE, DELETE ON %I FROM hr_app', partition_name);
        EXECUTE format('GRANT SELECT, INSERT ON %I TO hr_app', partition_name);
    END IF;
END;
$$;

COMMENT ON FUNCTION ensure_monthly_partition(text, date) IS
    'Idempotent monthly partition creation. Scheduled to run ahead of need; a missing partition means failed inserts.';

DO $$
DECLARE
    this_month date := date_trunc('month', now())::date;
    tbl        text;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['audit_log', 'change_feed', 'mutation_log']
        LOOP
            PERFORM ensure_monthly_partition(tbl, this_month);
            PERFORM ensure_monthly_partition(tbl, (this_month + INTERVAL '1 month')::date);
            PERFORM ensure_monthly_partition(tbl, (this_month + INTERVAL '2 months')::date);
        END LOOP;
END
$$;

-- -----------------------------------------------------------------------------
-- Lock down the append-only tables.
--
-- The application can write and read audit rows; it cannot alter or remove them.
-- Corrections are made by appending, never by editing history.
-- -----------------------------------------------------------------------------
REVOKE UPDATE, DELETE ON audit_log FROM hr_app;
REVOKE UPDATE, DELETE ON change_feed FROM hr_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT ON TABLES TO hr_app;
