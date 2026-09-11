-- =============================================================================
-- V16 — Expense Claims & Reimbursements Engine (P3-BE-64 through P3-BE-66)
--
-- Adds models for:
--   - expense_category: Configurable expense policies, receipts requirement, rates, and limits
--   - expense_claim: Multi-line expense reimbursement applications and lifecycle statuses
--   - expense_claim_line: Itemized expense lines with receipt metadata, OCR extraction, and approvals
--
-- Enforces PostgreSQL Row-Level Security (RLS) on all tables with tenant-leading indexes.
-- =============================================================================

-- =============================================================================
-- 1. Expense Category
-- =============================================================================
CREATE TABLE expense_category
(
    id                   uuid           PRIMARY KEY,
    tenant_id            uuid           NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code                 varchar(32)    NOT NULL,
    name                 varchar(128)   NOT NULL,
    description          text,
    gl_code              varchar(32),
    requires_receipt     boolean        NOT NULL DEFAULT true,
    max_amount_per_claim numeric(12, 2),
    rate_per_unit        numeric(10, 2),
    unit_name            varchar(32),
    is_active            boolean        NOT NULL DEFAULT true,

    created_at           timestamptz    NOT NULL DEFAULT now(),
    created_by           uuid,
    updated_at           timestamptz    NOT NULL DEFAULT now(),
    updated_by           uuid,
    version              bigint         NOT NULL DEFAULT 0,

    CONSTRAINT uq_expense_category_code UNIQUE (tenant_id, code),
    CONSTRAINT expense_category_rates_valid CHECK (
        (rate_per_unit IS NULL AND unit_name IS NULL) OR
        (rate_per_unit IS NOT NULL AND rate_per_unit > 0 AND unit_name IS NOT NULL)
    )
);

CREATE INDEX ix_expense_category_tenant_active ON expense_category (tenant_id, is_active);
SELECT apply_tenant_rls('expense_category');

COMMENT ON TABLE expense_category IS 'Company expense policies, spending limits, per-unit mileage/per-diem rates, and receipt rules.';

-- =============================================================================
-- 2. Expense Claim
-- =============================================================================
CREATE TABLE expense_claim
(
    id                        uuid           PRIMARY KEY,
    tenant_id                 uuid           NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id               uuid           NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    claim_number              varchar(32)    NOT NULL,
    title                     varchar(128)   NOT NULL,
    claim_date                date           NOT NULL,
    currency                  varchar(3)     NOT NULL DEFAULT 'LKR',
    total_amount              numeric(12, 2) NOT NULL,
    approved_amount           numeric(12, 2),
    status                    varchar(32)    NOT NULL DEFAULT 'SUBMITTED',
    rejection_reason          text,
    approved_at               timestamptz,
    approved_by               uuid,
    reimbursed_at             timestamptz,
    reimbursed_payroll_run_id uuid           REFERENCES payroll_run (id) ON DELETE SET NULL,
    remarks                   text,

    created_at                timestamptz    NOT NULL DEFAULT now(),
    created_by                uuid,
    updated_at                timestamptz    NOT NULL DEFAULT now(),
    updated_by                uuid,
    version                   bigint         NOT NULL DEFAULT 0,

    CONSTRAINT uq_expense_claim_number UNIQUE (tenant_id, claim_number),
    CONSTRAINT expense_claim_status_valid CHECK (status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'REIMBURSED', 'CANCELLED')),
    CONSTRAINT expense_claim_amount_valid CHECK (total_amount >= 0)
);

CREATE INDEX ix_expense_claim_tenant_emp ON expense_claim (tenant_id, employee_id, status);
CREATE INDEX ix_expense_claim_tenant_status_date ON expense_claim (tenant_id, status, claim_date);
SELECT apply_tenant_rls('expense_claim');

COMMENT ON TABLE expense_claim IS 'Employee expense claim submissions, approval state, and payroll reimbursement links.';

-- =============================================================================
-- 3. Expense Claim Line
-- =============================================================================
CREATE TABLE expense_claim_line
(
    id                     uuid           PRIMARY KEY,
    tenant_id              uuid           NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    claim_id               uuid           NOT NULL REFERENCES expense_claim (id) ON DELETE CASCADE,
    category_id            uuid           NOT NULL REFERENCES expense_category (id) ON DELETE RESTRICT,
    expense_date           date           NOT NULL,
    description            varchar(255)   NOT NULL,
    merchant_name          varchar(128),
    unit_quantity          numeric(10, 2),
    amount                 numeric(12, 2) NOT NULL,
    receipt_key            varchar(255),
    receipt_url            text,
    receipt_mime_type      varchar(64),
    ocr_extracted          jsonb,
    approved_amount        numeric(12, 2),
    status                 varchar(32)    NOT NULL DEFAULT 'PENDING',
    line_rejection_reason  text,

    created_at             timestamptz    NOT NULL DEFAULT now(),
    created_by             uuid,
    updated_at             timestamptz    NOT NULL DEFAULT now(),
    updated_by             uuid,
    version                bigint         NOT NULL DEFAULT 0,

    CONSTRAINT expense_claim_line_status_valid CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT expense_claim_line_amount_valid CHECK (amount >= 0)
);

CREATE INDEX ix_expense_claim_line_claim ON expense_claim_line (tenant_id, claim_id);
CREATE INDEX ix_expense_claim_line_cat ON expense_claim_line (tenant_id, category_id);
SELECT apply_tenant_rls('expense_claim_line');

COMMENT ON TABLE expense_claim_line IS 'Itemized claim lines with receipt proof, OCR extracted tokens, and individual line approval.';
