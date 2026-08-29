-- =============================================================================
-- V1 — Platform & tenancy
--
-- Establishes the tenant registry and the row-level security machinery that is
-- the real isolation boundary of the system.
--
-- SECURITY MODEL
-- --------------
-- Two database roles:
--
--   hr_owner   owns the schema, runs migrations. Subject to RLS is OFF for the
--              owner by default (we do not FORCE), which is what lets migrations
--              and backfills touch every tenant's rows.
--
--   hr_app     the role the application connects as. Owns nothing, holds only
--              DML grants, and IS subject to every policy below.
--
-- The application must never connect as hr_owner. If it does, RLS silently
-- stops applying and the isolation tests still pass — which is the worst
-- possible failure mode. `TenantIsolationTest` asserts the runtime role is not
-- a table owner precisely to catch this.
--
-- The policies read `current_setting('app.tenant_id', true)`, which
-- TenantAwareDataSource binds on every connection checkout. When it is unset or
-- empty the expression is NULL, every predicate is NULL, and every query
-- returns zero rows. Failing closed is deliberate.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "ltree";
-- Lets a GIN index include a scalar column alongside an array or jsonb one. Without it,
-- `USING gin (tenant_id, scopes)` in V3 fails outright — GIN ships no operator class for `uuid`,
-- so the statement aborts with "data type uuid has no default operator class". Available on RDS,
-- Cloud SQL and the official image.
CREATE EXTENSION IF NOT EXISTS "btree_gin";

-- -----------------------------------------------------------------------------
-- Helper: the tenant bound to the current connection.
--
-- STABLE (not IMMUTABLE) so the planner may cache it within a statement but
-- re-evaluates it per statement.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION current_tenant_id()
    RETURNS uuid
    LANGUAGE sql
    STABLE
    PARALLEL SAFE
AS $$
SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid
$$;

-- LEAKPROOF is applied separately, and is allowed to fail.
--
-- PostgreSQL restricts the attribute to genuine superusers (`rolsuper`). A managed instance does
-- not give you one: the RDS master role holds `rds_superuser`, which is a role membership and not
-- the same thing, and Cloud SQL is equivalent. Declaring it inline therefore aborts the very first
-- migration on every managed database — the schema would deploy on a developer's Docker container
-- and nowhere else.
--
-- What it buys, precisely: a LEAKPROOF qualifier may be pushed below a security barrier, so the
-- tenant predicate can be evaluated inside an index scan rather than on top of its output. That is
-- a *performance* property. The isolation itself does not depend on it — PostgreSQL already
-- refuses to evaluate a non-leakproof user function before an RLS policy, which is the actual leak
-- vector. So losing it costs query plans, not safety, and a warning is the proportionate response.
DO $$
BEGIN
    ALTER FUNCTION current_tenant_id() LEAKPROOF;
EXCEPTION WHEN insufficient_privilege THEN
    RAISE WARNING
        'current_tenant_id() is not LEAKPROOF: this role is not a superuser. Tenant isolation is '
        'unaffected, but RLS predicates cannot be pushed below security barriers and some plans '
        'will be slower. Ask a superuser to run: ALTER FUNCTION current_tenant_id() LEAKPROOF;';
END
$$;

COMMENT ON FUNCTION current_tenant_id() IS
    'Tenant bound to the current connection by TenantAwareDataSource. NULL when unbound, which causes every RLS policy to match zero rows.';

-- -----------------------------------------------------------------------------
-- Helper: apply the standard tenant isolation policy to a table.
--
-- Every tenant-scoped table must call this. Doing it through one function means
-- the policy is written once and cannot drift between tables.
--
-- The USING clause governs which rows are visible to SELECT/UPDATE/DELETE.
-- The WITH CHECK clause governs INSERT/UPDATE — it prevents a tenant from
-- writing a row *belonging to someone else*, which USING alone does not.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION apply_tenant_rls(target_table text)
    RETURNS void
    LANGUAGE plpgsql
AS $$
BEGIN
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', target_table);
    EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', target_table);
    EXECUTE format(
        'CREATE POLICY tenant_isolation ON %I
             USING (tenant_id = current_tenant_id())
             WITH CHECK (tenant_id = current_tenant_id())',
        target_table);
END;
$$;

COMMENT ON FUNCTION apply_tenant_rls(text) IS
    'Applies the standard tenant_id isolation policy. Call for every tenant-scoped table.';

-- =============================================================================
-- Tenant registry
--
-- Deliberately NOT tenant-scoped and NOT under RLS: it is the table that defines
-- what a tenant is, so it cannot be filtered by tenant. It is protected at the
-- application layer instead — no tenant-facing endpoint ever exposes it.
-- =============================================================================
CREATE TABLE tenant
(
    id                uuid         PRIMARY KEY,
    code              varchar(64)  NOT NULL UNIQUE,
    name              varchar(255) NOT NULL,
    legal_name        varchar(255),
    country_code      char(2)      NOT NULL,
    timezone          varchar(64)  NOT NULL DEFAULT 'UTC',
    default_currency  char(3)      NOT NULL,
    locale            varchar(16)  NOT NULL DEFAULT 'en',
    data_region       varchar(32)  NOT NULL DEFAULT 'default',
    isolation_tier    varchar(32)  NOT NULL DEFAULT 'SHARED',
    status            varchar(32)  NOT NULL DEFAULT 'PROVISIONING',
    subscription_plan varchar(64),

    created_at        timestamptz  NOT NULL DEFAULT now(),
    created_by        uuid,
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    updated_by        uuid,
    version           bigint       NOT NULL DEFAULT 0,

    CONSTRAINT tenant_code_format   CHECK (code ~ '^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$'),
    CONSTRAINT tenant_status_valid  CHECK (status IN ('PROVISIONING', 'ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    CONSTRAINT tenant_tier_valid    CHECK (isolation_tier IN ('SHARED', 'DEDICATED_SCHEMA', 'DEDICATED_DATABASE'))
);

COMMENT ON TABLE tenant IS 'Customer organisations. The only non-tenant-scoped business table.';
COMMENT ON COLUMN tenant.code IS 'URL-safe short code. Used as a subdomain and in the X-Tenant-Code header.';
COMMENT ON COLUMN tenant.data_region IS 'Physical data location, for residency obligations (UAE, Indonesia).';

-- =============================================================================
-- Per-tenant module enablement
-- =============================================================================
CREATE TABLE tenant_module
(
    tenant_id  uuid        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    module_key varchar(64) NOT NULL,
    enabled    boolean     NOT NULL DEFAULT false,
    config     jsonb       NOT NULL DEFAULT '{}'::jsonb,
    updated_at timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (tenant_id, module_key)
);

SELECT apply_tenant_rls('tenant_module');

COMMENT ON TABLE tenant_module IS
    'Which modules a tenant has. Drives both API authorisation and the mobile navigation shell.';

-- =============================================================================
-- Configurable identifier sequences (employee codes, claim numbers, ...)
--
-- Tenant-scoped, so it demonstrates the standard pattern: tenant_id first in
-- the primary key, RLS applied at the end of the file.
-- =============================================================================
CREATE TABLE sequence_config
(
    tenant_id    uuid        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    sequence_key varchar(64) NOT NULL,
    prefix       varchar(16) NOT NULL DEFAULT '',
    suffix       varchar(16) NOT NULL DEFAULT '',
    padding      smallint    NOT NULL DEFAULT 4,
    next_value   bigint      NOT NULL DEFAULT 1,
    reset_policy varchar(16) NOT NULL DEFAULT 'NEVER',

    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (tenant_id, sequence_key),
    CONSTRAINT sequence_reset_valid CHECK (reset_policy IN ('NEVER', 'YEARLY', 'MONTHLY')),
    CONSTRAINT sequence_padding_sane CHECK (padding BETWEEN 0 AND 12)
);

SELECT apply_tenant_rls('sequence_config');

-- =============================================================================
-- Application role
--
-- Created idempotently so the migration is safe to run against a database where
-- the role was provisioned by infrastructure (Terraform in staging/production,
-- the init script in local Docker).
-- =============================================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hr_app') THEN
        CREATE ROLE hr_app NOLOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO hr_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO hr_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO hr_app;

-- Future tables created by the owner are granted to hr_app automatically, so
-- later migrations do not have to remember to do it.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO hr_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO hr_app;
