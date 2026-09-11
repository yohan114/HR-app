-- =============================================================================
-- V12 — Payroll Statutory Calculation Pipeline (P3-BE-21 through P3-BE-36)
--
-- Adds core payroll models:
--   - pay_group: Multi-tenant payroll frequency and country schedule
--   - pay_period: Specific pay cycles with open/closed lifecycle
--   - payroll_run: State-machine payroll calculation batch
--   - payroll_result: Per-employee gross-to-net projection
--   - payroll_result_line: Itemized earnings, statutory lines, taxes, and traces
--
-- Enforces PostgreSQL Row-Level Security (RLS) on all tables and tenant_id
-- leading composite indexes.
-- =============================================================================

-- =============================================================================
-- 1. Pay Group
-- =============================================================================
CREATE TABLE pay_group
(
    id                     uuid          PRIMARY KEY,
    tenant_id              uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code                   varchar(32)   NOT NULL,
    name                   varchar(128)  NOT NULL,
    country_code           varchar(2)    NOT NULL,
    currency               varchar(3)    NOT NULL,
    pay_frequency          varchar(32)   NOT NULL DEFAULT 'MONTHLY',
    standard_days_per_month numeric(4, 2) NOT NULL DEFAULT 22.00,
    is_active              boolean       NOT NULL DEFAULT true,

    created_at             timestamptz   NOT NULL DEFAULT now(),
    created_by             uuid,
    updated_at             timestamptz   NOT NULL DEFAULT now(),
    updated_by             uuid,
    version                bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_pay_group_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT pay_frequency_valid CHECK (pay_frequency IN ('MONTHLY', 'SEMI_MONTHLY', 'BI_WEEKLY', 'WEEKLY'))
);

CREATE INDEX ix_pay_group_tenant_country ON pay_group (tenant_id, country_code);
SELECT apply_tenant_rls('pay_group');

COMMENT ON TABLE pay_group IS 'Defines payroll schedule, regional defaults, and standard work days for employee cohorts.';

-- =============================================================================
-- 2. Pay Period
-- =============================================================================
CREATE TABLE pay_period
(
    id            uuid         PRIMARY KEY,
    tenant_id     uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    pay_group_id  uuid         NOT NULL REFERENCES pay_group (id) ON DELETE CASCADE,
    code          varchar(32)  NOT NULL,
    start_date    date         NOT NULL,
    end_date      date         NOT NULL,
    payment_date  date         NOT NULL,
    status        varchar(32)  NOT NULL DEFAULT 'OPEN',

    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid,
    version       bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uq_pay_period_group_code UNIQUE (tenant_id, pay_group_id, code),
    CONSTRAINT pay_period_dates_valid CHECK (end_date >= start_date AND payment_date >= start_date),
    CONSTRAINT pay_period_status_valid CHECK (status IN ('OPEN', 'PROCESSING', 'APPROVED', 'CLOSED'))
);

CREATE INDEX ix_pay_period_tenant_group_dates ON pay_period (tenant_id, pay_group_id, start_date);
SELECT apply_tenant_rls('pay_period');

COMMENT ON TABLE pay_period IS 'Specific fiscal calculation interval for a pay group.';

-- =============================================================================
-- 3. Payroll Run
-- =============================================================================
CREATE TABLE payroll_run
(
    id                         uuid           PRIMARY KEY,
    tenant_id                  uuid           NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    pay_group_id               uuid           NOT NULL REFERENCES pay_group (id) ON DELETE CASCADE,
    pay_period_id              uuid           NOT NULL REFERENCES pay_period (id) ON DELETE CASCADE,
    run_number                 integer        NOT NULL DEFAULT 1,
    status                     varchar(32)    NOT NULL DEFAULT 'DRAFT',
    total_gross                numeric(14, 2) NOT NULL DEFAULT 0.00,
    total_statutory_employee   numeric(14, 2) NOT NULL DEFAULT 0.00,
    total_statutory_employer   numeric(14, 2) NOT NULL DEFAULT 0.00,
    total_tax                  numeric(14, 2) NOT NULL DEFAULT 0.00,
    total_net                  numeric(14, 2) NOT NULL DEFAULT 0.00,
    total_employees            integer        NOT NULL DEFAULT 0,

    calculated_at              timestamptz,
    calculated_by              uuid,
    approved_at                timestamptz,
    approved_by                uuid,
    committed_at               timestamptz,
    committed_by               uuid,

    created_at                 timestamptz    NOT NULL DEFAULT now(),
    created_by                 uuid,
    updated_at                 timestamptz    NOT NULL DEFAULT now(),
    updated_by                 uuid,
    version                    bigint         NOT NULL DEFAULT 0,

    CONSTRAINT payroll_run_status_valid CHECK (status IN ('DRAFT', 'CALCULATED', 'APPROVED', 'COMMITTED'))
);

CREATE INDEX ix_payroll_run_tenant_period ON payroll_run (tenant_id, pay_period_id);
SELECT apply_tenant_rls('payroll_run');

COMMENT ON TABLE payroll_run IS 'Execution instance of payroll calculation for a pay period.';

-- =============================================================================
-- 4. Payroll Result (Per-Employee Pay Slip Header)
-- =============================================================================
CREATE TABLE payroll_result
(
    id                         uuid           PRIMARY KEY,
    tenant_id                  uuid           NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    payroll_run_id             uuid           NOT NULL REFERENCES payroll_run (id) ON DELETE CASCADE,
    employee_id                uuid           NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    employee_code              varchar(32)    NOT NULL,
    employee_name              varchar(128)   NOT NULL,
    currency                   varchar(3)     NOT NULL,
    basic_salary               numeric(12, 2) NOT NULL,
    gross_pay                  numeric(12, 2) NOT NULL,
    total_statutory_employee   numeric(12, 2) NOT NULL DEFAULT 0.00,
    total_statutory_employer   numeric(12, 2) NOT NULL DEFAULT 0.00,
    tax_withheld               numeric(12, 2) NOT NULL DEFAULT 0.00,
    total_voluntary_deductions numeric(12, 2) NOT NULL DEFAULT 0.00,
    net_pay                    numeric(12, 2) NOT NULL,
    payment_status             varchar(32)    NOT NULL DEFAULT 'PENDING',

    created_at                 timestamptz    NOT NULL DEFAULT now(),
    created_by                 uuid,
    updated_at                 timestamptz    NOT NULL DEFAULT now(),
    updated_by                 uuid,
    version                    bigint         NOT NULL DEFAULT 0,

    CONSTRAINT uq_payroll_result_run_employee UNIQUE (tenant_id, payroll_run_id, employee_id),
    CONSTRAINT payment_status_valid CHECK (payment_status IN ('PENDING', 'PROCESSING', 'PAID', 'FAILED'))
);

CREATE INDEX ix_payroll_result_tenant_emp ON payroll_result (tenant_id, employee_id);
SELECT apply_tenant_rls('payroll_result');

COMMENT ON TABLE payroll_result IS 'Aggregated gross-to-net pay result per employee for a payroll run.';

-- =============================================================================
-- 5. Payroll Result Line (Itemized Payslip Lines with Trace)
-- =============================================================================
CREATE TABLE payroll_result_line
(
    id                uuid           PRIMARY KEY,
    tenant_id         uuid           NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    payroll_result_id uuid           NOT NULL REFERENCES payroll_result (id) ON DELETE CASCADE,
    line_category     varchar(32)    NOT NULL,
    item_code         varchar(32)    NOT NULL,
    item_name         varchar(128)   NOT NULL,
    amount            numeric(12, 2) NOT NULL,
    is_statutory      boolean        NOT NULL DEFAULT false,
    calculation_trace text,

    created_at        timestamptz    NOT NULL DEFAULT now(),
    created_by        uuid,
    updated_at        timestamptz    NOT NULL DEFAULT now(),
    updated_by        uuid,
    version           bigint         NOT NULL DEFAULT 0,

    CONSTRAINT line_category_valid CHECK (line_category IN ('EARNING', 'STATUTORY_DEDUCTION', 'EMPLOYER_CONTRIBUTION', 'VOLUNTARY_DEDUCTION', 'TAX'))
);

CREATE INDEX ix_payroll_result_line_tenant_result ON payroll_result_line (tenant_id, payroll_result_id);
SELECT apply_tenant_rls('payroll_result_line');

COMMENT ON TABLE payroll_result_line IS 'Detailed payslip line item with formula audit trace.';
