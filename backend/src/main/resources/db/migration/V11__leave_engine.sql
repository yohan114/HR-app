-- =============================================================================
-- V11 — Absence & Leave Engine (P2-BE-24 / P2-BE-25)
--
-- Establishes the core absence and leave calculation engine:
--
--   leave_year                  Cycle period (e.g. calendar/fiscal year)
--   leave_type                  Leave classifications (ANNUAL, CASUAL, MEDICAL, etc.)
--   leave_entitlement_rule      Accrual policies and entitlement formulas
--   employee_leave_entitlement  Balance snapshot per employee/year/type
--   leave_ledger                Append-only immutable audit statement of balance mutations
--
-- SECURITY MODEL & RLS
-- ---------------------
-- Every table is tenant-scoped with `tenant_id` and enforced by `apply_tenant_rls()`.
-- Indexes leading with `tenant_id` guarantee fast plan evaluation under RLS predicates.
--
-- EXPLAINABILITY & IDEMPOTENCY GUARANTEE
-- ---------------------------------------
-- `leave_ledger` is append-only. A balance is always reconstructible by summing
-- ledger entries. The unique constraint on (tenant_id, employee_id, leave_year_id,
-- leave_type_id, reference_type, reference_id) guarantees that recurring accrual runs
-- (e.g. for a given month) can be executed safely and repeatedly without double-crediting.
-- =============================================================================

-- =============================================================================
-- 1. Leave Year
-- =============================================================================
CREATE TABLE leave_year
(
    id         uuid        PRIMARY KEY,
    tenant_id  uuid        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code       varchar(32) NOT NULL,
    name       varchar(128) NOT NULL,
    start_date date        NOT NULL,
    end_date   date        NOT NULL,
    status     varchar(32) NOT NULL DEFAULT 'ACTIVE',

    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid,
    version    bigint      NOT NULL DEFAULT 0,

    CONSTRAINT leave_year_dates CHECK (end_date >= start_date),
    CONSTRAINT leave_year_status_valid CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT uq_leave_year_code UNIQUE (tenant_id, code)
);

CREATE INDEX ix_leave_year_tenant_dates ON leave_year (tenant_id, start_date, end_date);
SELECT apply_tenant_rls('leave_year');

COMMENT ON TABLE leave_year IS 'Cycle boundaries for leave entitlement and balance calculation.';

-- =============================================================================
-- 2. Leave Type
-- =============================================================================
CREATE TABLE leave_type
(
    id             uuid         PRIMARY KEY,
    tenant_id      uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code           varchar(32)  NOT NULL,
    name           varchar(128) NOT NULL,
    unit           varchar(16)  NOT NULL DEFAULT 'DAY',
    paid           boolean      NOT NULL DEFAULT true,
    allow_half_day boolean      NOT NULL DEFAULT true,
    color          varchar(32)  NOT NULL DEFAULT '#6366f1',
    sequence       smallint     NOT NULL DEFAULT 0,

    created_at     timestamptz  NOT NULL DEFAULT now(),
    created_by     uuid,
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    updated_by     uuid,
    version        bigint       NOT NULL DEFAULT 0,

    CONSTRAINT leave_type_unit_valid CHECK (unit IN ('DAY', 'HOUR')),
    CONSTRAINT uq_leave_type_code UNIQUE (tenant_id, code)
);

CREATE INDEX ix_leave_type_tenant_seq ON leave_type (tenant_id, sequence);
SELECT apply_tenant_rls('leave_type');

COMMENT ON TABLE leave_type IS 'Categories of leave (Annual, Casual, Medical, Maternity, Unpaid).';

-- =============================================================================
-- 3. Leave Entitlement Rule
-- =============================================================================
CREATE TABLE leave_entitlement_rule
(
    id                          uuid         PRIMARY KEY,
    tenant_id                   uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    leave_type_id               uuid         NOT NULL REFERENCES leave_type (id) ON DELETE CASCADE,
    name                        varchar(128) NOT NULL,
    accrual_method              varchar(32)  NOT NULL,
    accrual_rate                numeric(8, 4) NOT NULL DEFAULT 0.0000,
    accrual_frequency           varchar(32)  NOT NULL DEFAULT 'ANNUAL',
    prorate_on_join             boolean      NOT NULL DEFAULT true,
    prorate_on_exit             boolean      NOT NULL DEFAULT true,
    carry_forward_enabled       boolean      NOT NULL DEFAULT false,
    carry_forward_max           numeric(6, 2) NOT NULL DEFAULT 0.00,
    carry_forward_expiry_months integer      NOT NULL DEFAULT 0,
    encashment_enabled          boolean      NOT NULL DEFAULT false,
    encashment_max              numeric(6, 2) NOT NULL DEFAULT 0.00,
    max_balance                 numeric(6, 2) NOT NULL DEFAULT 999.00,
    service_based_slabs         jsonb        NOT NULL DEFAULT '[]'::jsonb,
    effective_from              date         NOT NULL DEFAULT CURRENT_DATE,

    created_at                  timestamptz  NOT NULL DEFAULT now(),
    created_by                  uuid,
    updated_at                  timestamptz  NOT NULL DEFAULT now(),
    updated_by                  uuid,
    version                     bigint       NOT NULL DEFAULT 0,

    CONSTRAINT accrual_method_valid CHECK (accrual_method IN ('ANNUAL_UPFRONT', 'MONTHLY', 'PER_WORKED_DAY', 'SERVICE_SLAB', 'EARNED')),
    CONSTRAINT accrual_freq_valid CHECK (accrual_frequency IN ('ANNUAL', 'MONTHLY', 'DAILY'))
);

CREATE INDEX ix_leave_entitlement_rule_tenant_type ON leave_entitlement_rule (tenant_id, leave_type_id);
SELECT apply_tenant_rls('leave_entitlement_rule');

COMMENT ON TABLE leave_entitlement_rule IS 'Policy formulas governing accrual rates, caps, tenure slabs, and carry-forward.';

-- =============================================================================
-- 4. Employee Leave Entitlement (Aggregate Balance Snapshot)
-- =============================================================================
CREATE TABLE employee_leave_entitlement
(
    tenant_id        uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id      uuid          NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    leave_year_id    uuid          NOT NULL REFERENCES leave_year (id) ON DELETE CASCADE,
    leave_type_id    uuid          NOT NULL REFERENCES leave_type (id) ON DELETE CASCADE,
    opening_balance  numeric(6, 2) NOT NULL DEFAULT 0.00,
    accrued          numeric(6, 2) NOT NULL DEFAULT 0.00,
    taken            numeric(6, 2) NOT NULL DEFAULT 0.00,
    adjusted         numeric(6, 2) NOT NULL DEFAULT 0.00,
    carried_forward  numeric(6, 2) NOT NULL DEFAULT 0.00,
    encashed         numeric(6, 2) NOT NULL DEFAULT 0.00,
    expired          numeric(6, 2) NOT NULL DEFAULT 0.00,
    balance          numeric(6, 2) NOT NULL DEFAULT 0.00,
    last_accrued_at  timestamptz,

    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),

    PRIMARY KEY (tenant_id, employee_id, leave_year_id, leave_type_id)
);

CREATE INDEX ix_employee_leave_entitlement_lookup ON employee_leave_entitlement (tenant_id, employee_id, leave_year_id);
SELECT apply_tenant_rls('employee_leave_entitlement');

COMMENT ON TABLE employee_leave_entitlement IS 'Fast aggregate balance projection per employee and leave type.';

-- =============================================================================
-- 5. Leave Ledger (Append-Only Audit Statement)
-- =============================================================================
CREATE TABLE leave_ledger
(
    id             uuid          PRIMARY KEY,
    tenant_id      uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id    uuid          NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    leave_year_id  uuid          NOT NULL REFERENCES leave_year (id) ON DELETE CASCADE,
    leave_type_id  uuid          NOT NULL REFERENCES leave_type (id) ON DELETE CASCADE,
    entry_type     varchar(32)   NOT NULL,
    days           numeric(6, 2) NOT NULL,
    reference_type varchar(64)   NOT NULL,
    reference_id   varchar(128)  NOT NULL,
    effective_date date          NOT NULL,
    balance_after  numeric(6, 2) NOT NULL,
    remarks        varchar(255),

    created_at     timestamptz   NOT NULL DEFAULT now(),
    created_by     uuid,
    updated_at     timestamptz   NOT NULL DEFAULT now(),
    updated_by     uuid,
    version        bigint        NOT NULL DEFAULT 0,

    CONSTRAINT entry_type_valid CHECK (entry_type IN ('OPENING', 'ACCRUAL', 'TAKEN', 'CANCELLED', 'ADJUSTMENT', 'CARRY_FORWARD', 'ENCASHMENT', 'EXPIRY')),
    CONSTRAINT uq_leave_ledger_idempotency UNIQUE (tenant_id, employee_id, leave_year_id, leave_type_id, reference_type, reference_id)
);

CREATE INDEX ix_leave_ledger_tenant_emp_date ON leave_ledger (tenant_id, employee_id, effective_date);
CREATE INDEX ix_leave_ledger_tenant_ref ON leave_ledger (tenant_id, reference_type, reference_id);
SELECT apply_tenant_rls('leave_ledger');

COMMENT ON TABLE leave_ledger IS 'Immutable, append-only statement of every leave credit, debit, or adjustment.';
