-- =============================================================================
-- V15 — Employee Loans & Salary Advances Engine (P3-BE-56 through P3-BE-61)
--
-- Adds models for:
--   - loan_type: Configurable loan programs, interest methods, tenure limits, and eligibility rules
--   - employee_loan: Employee loan applications, approved active loans, and balances
--   - loan_repayment_schedule: Amortization schedule lines integrated with monthly payroll runs
--
-- Enforces PostgreSQL Row-Level Security (RLS) on all tables with tenant-leading indexes.
-- =============================================================================

-- =============================================================================
-- 1. Loan Type
-- =============================================================================
CREATE TABLE loan_type
(
    id                            uuid          PRIMARY KEY,
    tenant_id                     uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code                          varchar(32)   NOT NULL,
    name                          varchar(128)  NOT NULL,
    description                   text,
    interest_method               varchar(32)   NOT NULL DEFAULT 'ZERO_INTEREST',
    annual_interest_rate          numeric(5, 2) NOT NULL DEFAULT 0.00,
    min_tenure_months             integer       NOT NULL DEFAULT 1,
    max_tenure_months             integer       NOT NULL DEFAULT 12,
    min_principal                 numeric(12, 2) NOT NULL DEFAULT 1000.00,
    max_principal                 numeric(12, 2) NOT NULL DEFAULT 500000.00,
    salary_multiple_limit         numeric(4, 2) NOT NULL DEFAULT 3.00,
    min_service_months            integer       NOT NULL DEFAULT 6,
    max_active_loans_per_employee integer       NOT NULL DEFAULT 1,
    is_active                     boolean       NOT NULL DEFAULT true,

    created_at                    timestamptz   NOT NULL DEFAULT now(),
    created_by                    uuid,
    updated_at                    timestamptz   NOT NULL DEFAULT now(),
    updated_by                    uuid,
    version                       bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_loan_type_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT loan_interest_method_valid CHECK (interest_method IN ('ZERO_INTEREST', 'REDUCING_BALANCE', 'FLAT_RATE')),
    CONSTRAINT loan_tenure_valid CHECK (max_tenure_months >= min_tenure_months AND min_tenure_months > 0),
    CONSTRAINT loan_principal_valid CHECK (max_principal >= min_principal AND min_principal > 0)
);

CREATE INDEX ix_loan_type_tenant_active ON loan_type (tenant_id, is_active);
SELECT apply_tenant_rls('loan_type');

COMMENT ON TABLE loan_type IS 'Company loan/advance products with interest calculation rules and eligibility constraints.';

-- =============================================================================
-- 2. Employee Loan
-- =============================================================================
CREATE TABLE employee_loan
(
    id                   uuid           PRIMARY KEY,
    tenant_id            uuid           NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id          uuid           NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    loan_type_id         uuid           NOT NULL REFERENCES loan_type (id) ON DELETE RESTRICT,
    loan_code            varchar(32)    NOT NULL,
    principal_amount     numeric(12, 2) NOT NULL,
    interest_method      varchar(32)    NOT NULL,
    annual_interest_rate numeric(5, 2)  NOT NULL DEFAULT 0.00,
    tenure_months        integer        NOT NULL,
    monthly_installment  numeric(12, 2) NOT NULL,
    total_interest       numeric(12, 2) NOT NULL DEFAULT 0.00,
    total_repayable      numeric(12, 2) NOT NULL,
    total_repaid         numeric(12, 2) NOT NULL DEFAULT 0.00,
    remaining_balance    numeric(12, 2) NOT NULL,
    reason               text           NOT NULL,
    status               varchar(32)    NOT NULL DEFAULT 'SUBMITTED',
    disbursed_date       date,
    settled_date         date,

    created_at           timestamptz    NOT NULL DEFAULT now(),
    created_by           uuid,
    updated_at           timestamptz    NOT NULL DEFAULT now(),
    updated_by           uuid,
    version              bigint         NOT NULL DEFAULT 0,

    CONSTRAINT uq_employee_loan_code UNIQUE (tenant_id, loan_code),
    CONSTRAINT employee_loan_status_valid CHECK (status IN ('SUBMITTED', 'APPROVED', 'ACTIVE', 'SETTLED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT employee_loan_amounts_valid CHECK (principal_amount > 0 AND total_repayable >= principal_amount)
);

CREATE INDEX ix_employee_loan_tenant_emp ON employee_loan (tenant_id, employee_id, status);
SELECT apply_tenant_rls('employee_loan');

COMMENT ON TABLE employee_loan IS 'Employee loan records, outstanding balances, and active approval status.';

-- =============================================================================
-- 3. Loan Repayment Schedule
-- =============================================================================
CREATE TABLE loan_repayment_schedule
(
    id                  uuid           PRIMARY KEY,
    tenant_id           uuid           NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    loan_id             uuid           NOT NULL REFERENCES employee_loan (id) ON DELETE CASCADE,
    installment_number  integer        NOT NULL,
    due_date            date           NOT NULL,
    principal_amount    numeric(12, 2) NOT NULL,
    interest_amount     numeric(12, 2) NOT NULL DEFAULT 0.00,
    total_installment   numeric(12, 2) NOT NULL,
    status              varchar(32)    NOT NULL DEFAULT 'PENDING',
    deducted_date       date,
    payroll_run_id      uuid           REFERENCES payroll_run (id) ON DELETE SET NULL,

    created_at          timestamptz    NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz    NOT NULL DEFAULT now(),
    updated_by          uuid,
    version             bigint         NOT NULL DEFAULT 0,

    CONSTRAINT uq_loan_installment UNIQUE (tenant_id, loan_id, installment_number),
    CONSTRAINT loan_schedule_status_valid CHECK (status IN ('PENDING', 'DEDUCTED', 'WAIVED'))
);

CREATE INDEX ix_loan_schedule_tenant_due ON loan_repayment_schedule (tenant_id, status, due_date);
CREATE INDEX ix_loan_schedule_loan ON loan_repayment_schedule (tenant_id, loan_id);
SELECT apply_tenant_rls('loan_repayment_schedule');

COMMENT ON TABLE loan_repayment_schedule IS 'Amortization installment lines linked to payroll deduction cycles.';
