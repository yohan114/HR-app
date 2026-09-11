-- =============================================================================
-- V17 — Flexible Benefits & Medical Insurance Self-Service (P3-BE-62 through P3-BE-63)
--
-- Adds models for:
--   - benefit_category: Configurable benefit classifications (Health, Optical, Dental, Wellness, Comm)
--   - benefit_policy: Entitlement rules, coverage tiers, annual caps, co-pays, and grade eligibility
--   - employee_benefit_enrollment: Annual policy enrollments, active balances, and usage tracking
--   - benefit_dependent: Covered family members (Spouse, Children, Parents) under medical policies
--   - benefit_claim: Reimbursement claims with receipt attachments, co-pay derivations, and approvals
--
-- Enforces PostgreSQL Row-Level Security (RLS) on all tables with tenant-leading indexes.
-- =============================================================================

-- =============================================================================
-- 1. Benefit Category
-- =============================================================================
CREATE TABLE benefit_category
(
    id                   uuid           PRIMARY KEY,
    tenant_id            uuid           NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code                 varchar(32)    NOT NULL,
    name                 varchar(128)   NOT NULL,
    description          text,
    benefit_kind         varchar(32)    NOT NULL DEFAULT 'NON_CASH',
    is_active            boolean        NOT NULL DEFAULT true,

    created_at           timestamptz    NOT NULL DEFAULT now(),
    created_by           uuid,
    updated_at           timestamptz    NOT NULL DEFAULT now(),
    updated_by           uuid,
    version              bigint         NOT NULL DEFAULT 0,

    CONSTRAINT uq_benefit_category_code UNIQUE (tenant_id, code),
    CONSTRAINT benefit_category_kind_valid CHECK (benefit_kind IN ('CASH', 'NON_CASH', 'REIMBURSEMENT'))
);

CREATE INDEX ix_benefit_category_tenant_active ON benefit_category (tenant_id, is_active);
SELECT apply_tenant_rls('benefit_category');

COMMENT ON TABLE benefit_category IS 'Company benefit categories (Health Insurance, Optical, Dental, Wellness, Allowances).';

-- =============================================================================
-- 2. Benefit Policy
-- =============================================================================
CREATE TABLE benefit_policy
(
    id                   uuid           PRIMARY KEY,
    tenant_id            uuid           NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    category_id          uuid           NOT NULL REFERENCES benefit_category (id) ON DELETE RESTRICT,
    code                 varchar(32)    NOT NULL,
    name                 varchar(128)   NOT NULL,
    description          text,
    coverage_tier        varchar(32)    NOT NULL DEFAULT 'INDIVIDUAL',
    annual_limit         numeric(12, 2) NOT NULL,
    currency             varchar(3)     NOT NULL DEFAULT 'LKR',
    co_pay_percentage    numeric(5, 2)  NOT NULL DEFAULT 0.00,
    deductible_amount    numeric(12, 2) NOT NULL DEFAULT 0.00,
    min_service_months   int            NOT NULL DEFAULT 0,
    eligible_grades      varchar(128)   NOT NULL DEFAULT 'ALL',
    requires_receipt     boolean        NOT NULL DEFAULT true,
    is_active            boolean        NOT NULL DEFAULT true,

    created_at           timestamptz    NOT NULL DEFAULT now(),
    created_by           uuid,
    updated_at           timestamptz    NOT NULL DEFAULT now(),
    updated_by           uuid,
    version              bigint         NOT NULL DEFAULT 0,

    CONSTRAINT uq_benefit_policy_code UNIQUE (tenant_id, code),
    CONSTRAINT benefit_policy_tier_valid CHECK (coverage_tier IN ('INDIVIDUAL', 'EMPLOYEE_AND_SPOUSE', 'FAMILY')),
    CONSTRAINT benefit_policy_limit_valid CHECK (annual_limit >= 0),
    CONSTRAINT benefit_policy_copay_valid CHECK (co_pay_percentage >= 0 AND co_pay_percentage <= 100)
);

CREATE INDEX ix_benefit_policy_tenant_cat ON benefit_policy (tenant_id, category_id, is_active);
SELECT apply_tenant_rls('benefit_policy');

COMMENT ON TABLE benefit_policy IS 'Benefit schemes, coverage rules, annual maximums, co-pay ratios, and eligibility criteria.';

-- =============================================================================
-- 3. Employee Benefit Enrollment
-- =============================================================================
CREATE TABLE employee_benefit_enrollment
(
    id                   uuid           PRIMARY KEY,
    tenant_id            uuid           NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id          uuid           NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    policy_id            uuid           NOT NULL REFERENCES benefit_policy (id) ON DELETE RESTRICT,
    policy_number        varchar(64)    NOT NULL,
    enrollment_year      int            NOT NULL,
    start_date           date           NOT NULL,
    end_date             date           NOT NULL,
    annual_entitlement   numeric(12, 2) NOT NULL,
    used_amount          numeric(12, 2) NOT NULL DEFAULT 0.00,
    pending_amount       numeric(12, 2) NOT NULL DEFAULT 0.00,
    status               varchar(32)    NOT NULL DEFAULT 'ACTIVE',

    created_at           timestamptz    NOT NULL DEFAULT now(),
    created_by           uuid,
    updated_at           timestamptz    NOT NULL DEFAULT now(),
    updated_by           uuid,
    version              bigint         NOT NULL DEFAULT 0,

    CONSTRAINT uq_employee_benefit_enrollment UNIQUE (tenant_id, employee_id, policy_id, enrollment_year),
    CONSTRAINT benefit_enrollment_status_valid CHECK (status IN ('ACTIVE', 'SUSPENDED', 'EXPIRED', 'TERMINATED')),
    CONSTRAINT benefit_enrollment_dates_valid CHECK (start_date <= end_date)
);

CREATE INDEX ix_benefit_enrollment_emp ON employee_benefit_enrollment (tenant_id, employee_id, status);
SELECT apply_tenant_rls('employee_benefit_enrollment');

COMMENT ON TABLE employee_benefit_enrollment IS 'Active employee benefit subscriptions and annual balance tracking.';

-- =============================================================================
-- 4. Benefit Dependent
-- =============================================================================
CREATE TABLE benefit_dependent
(
    id                       uuid           PRIMARY KEY,
    tenant_id                uuid           NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    enrollment_id            uuid           NOT NULL REFERENCES employee_benefit_enrollment (id) ON DELETE CASCADE,
    full_name                varchar(128)   NOT NULL,
    relationship             varchar(32)    NOT NULL,
    date_of_birth            date           NOT NULL,
    national_id_or_passport  varchar(64),
    is_covered               boolean        NOT NULL DEFAULT true,

    created_at               timestamptz    NOT NULL DEFAULT now(),
    created_by               uuid,
    updated_at               timestamptz    NOT NULL DEFAULT now(),
    updated_by               uuid,
    version                  bigint         NOT NULL DEFAULT 0,

    CONSTRAINT benefit_dep_relationship_valid CHECK (relationship IN ('SPOUSE', 'CHILD', 'PARENT'))
);

CREATE INDEX ix_benefit_dependent_enrollment ON benefit_dependent (tenant_id, enrollment_id);
SELECT apply_tenant_rls('benefit_dependent');

COMMENT ON TABLE benefit_dependent IS 'Covered family dependents tied to employee health benefit policies.';

-- =============================================================================
-- 5. Benefit Claim
-- =============================================================================
CREATE TABLE benefit_claim
(
    id                   uuid           PRIMARY KEY,
    tenant_id            uuid           NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    enrollment_id        uuid           NOT NULL REFERENCES employee_benefit_enrollment (id) ON DELETE CASCADE,
    employee_id          uuid           NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    claim_number         varchar(32)    NOT NULL,
    claim_date           date           NOT NULL,
    dependent_id         uuid           REFERENCES benefit_dependent (id) ON DELETE SET NULL,
    service_provider     varchar(128)   NOT NULL,
    diagnosis_or_reason  varchar(255)   NOT NULL,
    invoice_number       varchar(64),
    claimed_amount       numeric(12, 2) NOT NULL,
    approved_amount      numeric(12, 2),
    co_pay_amount        numeric(12, 2) DEFAULT 0.00,
    payable_amount       numeric(12, 2),
    currency             varchar(3)     NOT NULL DEFAULT 'LKR',
    status               varchar(32)    NOT NULL DEFAULT 'SUBMITTED',
    receipt_url          text,
    receipt_key          varchar(255),
    rejection_reason     text,
    approved_at          timestamptz,
    approved_by          uuid,
    paid_at              timestamptz,
    remarks              text,

    created_at           timestamptz    NOT NULL DEFAULT now(),
    created_by           uuid,
    updated_at           timestamptz    NOT NULL DEFAULT now(),
    updated_by           uuid,
    version              bigint         NOT NULL DEFAULT 0,

    CONSTRAINT uq_benefit_claim_number UNIQUE (tenant_id, claim_number),
    CONSTRAINT benefit_claim_status_valid CHECK (status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'PAID', 'CANCELLED')),
    CONSTRAINT benefit_claim_amount_valid CHECK (claimed_amount > 0)
);

CREATE INDEX ix_benefit_claim_emp_status ON benefit_claim (tenant_id, employee_id, status);
CREATE INDEX ix_benefit_claim_enrollment ON benefit_claim (tenant_id, enrollment_id);
SELECT apply_tenant_rls('benefit_claim');

COMMENT ON TABLE benefit_claim IS 'Reimbursement claims against medical and flexible benefits with policy balance verification.';

-- =============================================================================
-- 6. Seed Data for Tenant 00000000-0000-0000-0000-000000000001
-- =============================================================================
INSERT INTO tenant (id, code, name, legal_name, country_code, timezone, default_currency, locale, status, isolation_tier)
VALUES 
    ('00000000-0000-0000-0000-000000000001', 'system-seed', 'System Seed Tenant', 'System Seed Tenant Ltd', 'LK', 'Asia/Colombo', 'LKR', 'en', 'ACTIVE', 'SHARED'),
    ('11111111-1111-1111-1111-111111111111', 'acme-seed', 'Acme Seed Tenant', 'Acme Corporation Ltd', 'LK', 'Asia/Colombo', 'LKR', 'en', 'ACTIVE', 'SHARED')
ON CONFLICT (id) DO NOTHING;

INSERT INTO company (id, tenant_id, code, name, legal_name, country_code, currency)
VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'SYS_CO', 'System Company', 'System Company Ltd', 'LK', 'LKR')
ON CONFLICT (id) DO NOTHING;

INSERT INTO department (id, tenant_id, company_id, code, name)
VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'SYS_DEPT', 'General')
ON CONFLICT (id) DO NOTHING;

INSERT INTO designation (id, tenant_id, code, name)
VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'SYS_DESIG', 'Staff')
ON CONFLICT (id) DO NOTHING;

INSERT INTO employee (id, tenant_id, employee_code, company_id, status, first_name, last_name, display_name, work_email, join_date, department_id, designation_id)
VALUES 
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'SYS_EMP_001', '00000000-0000-0000-0000-000000000001', 'ACTIVE', 'System', 'Seed', 'System Seed Employee', 'system@acme.corp', '2024-01-01', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001'),
    ('00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000001', 'LK010', '00000000-0000-0000-0000-000000000001', 'ACTIVE', 'Kasun', 'Mendis', 'Kasun Mendis', 'kasun.mendis@acme.corp', '2024-01-01', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001'),
    ('e0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'LK001', '00000000-0000-0000-0000-000000000001', 'ACTIVE', 'Amanda', 'Jayawardena', 'Amanda Jayawardena', 'amanda.jayawardena@acme.corp', '2023-01-01', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001'),
    ('e0000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'LK002', '00000000-0000-0000-0000-000000000001', 'ACTIVE', 'Nimal', 'Perera', 'Nimal Perera', 'nimal.perera@acme.corp', '2023-01-01', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001'),
    ('e0000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000001', 'E010', '00000000-0000-0000-0000-000000000001', 'ACTIVE', 'Kasun', 'Fernando', 'Kasun Fernando', 'kasun.fernando@acme.test', '2024-01-01', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001')
ON CONFLICT (id) DO NOTHING;

INSERT INTO benefit_category (id, tenant_id, code, name, description, benefit_kind, is_active)
VALUES
    ('c0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'HEALTH_INSURANCE', 'Outpatient Medical Insurance', 'Comprehensive outpatient medical consultations, diagnostics, and prescriptions', 'NON_CASH', true),
    ('c0000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'OPTICAL', 'Optical & Vision Care', 'Annual frames, corrective lenses, and ophthalmology consultations', 'REIMBURSEMENT', true),
    ('c0000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'DENTAL', 'Dental & Oral Healthcare', 'Routine cleaning, extractions, root canals, and orthodontic procedures', 'REIMBURSEMENT', true),
    ('c0000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'WELLNESS', 'Wellness & Fitness Allowance', 'Gym memberships, yoga studio subscriptions, and fitness trackers', 'CASH', true),
    ('c0000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000001', 'COMMUNICATION', 'Mobile & Broadband Allowance', 'Monthly corporate voice and high-speed data reimbursement', 'CASH', true)
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO benefit_policy (id, tenant_id, category_id, code, name, description, coverage_tier, annual_limit, currency, co_pay_percentage, deductible_amount, min_service_months, eligible_grades, requires_receipt, is_active)
VALUES
    ('b1000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'POL_OPD_FAMILY', 'Comprehensive Outpatient Medical (Family)', 'Covers employee, spouse, and dependent children up to age 21', 'FAMILY', 250000.00, 'LKR', 10.00, 0.00, 3, 'ALL', true, true),
    ('b1000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000002', 'POL_OPTICAL_STD', 'Annual Vision & Lens Care', 'Annual reimbursement for prescription spectacles and eye examinations', 'INDIVIDUAL', 40000.00, 'LKR', 0.00, 0.00, 6, 'ALL', true, true),
    ('b1000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000003', 'POL_DENTAL_STD', 'Annual Dental Care Program', 'Covers restorative, preventive, and emergency dental consultations', 'INDIVIDUAL', 50000.00, 'LKR', 15.00, 0.00, 3, 'ALL', true, true),
    ('b1000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000004', 'POL_WELLNESS_EXEC', 'Executive Wellness & Gym Subsidy', 'Subsidized fitness club access and mental wellbeing apps', 'INDIVIDUAL', 60000.00, 'LKR', 0.00, 0.00, 6, 'M1,M2,EX', false, true)
ON CONFLICT (tenant_id, code) DO NOTHING;

-- Seed Active Enrollments for Default Employee 00000000-0000-0000-0000-000000000001
INSERT INTO employee_benefit_enrollment (id, tenant_id, employee_id, policy_id, policy_number, enrollment_year, start_date, end_date, annual_entitlement, used_amount, pending_amount, status)
VALUES
    ('e0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', 'MED-2026-00812', 2026, '2026-01-01', '2026-12-31', 250000.00, 45000.00, 12500.00, 'ACTIVE'),
    ('e0000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000002', 'OPT-2026-00455', 2026, '2026-01-01', '2026-12-31', 40000.00, 18500.00, 0.00, 'ACTIVE'),
    ('e0000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000003', 'DEN-2026-00219', 2026, '2026-01-01', '2026-12-31', 50000.00, 0.00, 8000.00, 'ACTIVE')
ON CONFLICT (tenant_id, employee_id, policy_id, enrollment_year) DO NOTHING;

-- Seed Dependents for Medical Enrollment
INSERT INTO benefit_dependent (id, tenant_id, enrollment_id, full_name, relationship, date_of_birth, national_id_or_passport, is_covered)
VALUES
    ('d0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001', 'Champa Perera', 'SPOUSE', '1992-05-14', '199264501234', true),
    ('d0000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001', 'Senuka Perera', 'CHILD', '2018-09-22', '201826509988', true)
ON CONFLICT DO NOTHING;

-- Seed Historical Claims
INSERT INTO benefit_claim (id, tenant_id, enrollment_id, employee_id, claim_number, claim_date, dependent_id, service_provider, diagnosis_or_reason, invoice_number, claimed_amount, approved_amount, co_pay_amount, payable_amount, currency, status, receipt_url, receipt_key, paid_at, remarks)
VALUES
    ('b0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'CLM-BEN-2026-001', '2026-01-15', 'd0000000-0000-0000-0000-000000000002', 'Asiri Central Hospital', 'Pediatric Viral Fever consultation and medications', 'INV-AC-99412', 45000.00, 45000.00, 4500.00, 40500.00, 'LKR', 'PAID', 'https://storage.hrapp.io/receipts/ac-99412.pdf', 'receipts/ac-99412.pdf', '2026-01-20T10:00:00Z', 'Direct settlement processed to payroll'),
    ('b0000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'CLM-BEN-2026-002', '2026-02-04', NULL, 'Vision Care Optical', 'Prescription anti-glare progressives and frames', 'VC-2026-4412', 18500.00, 18500.00, 0.00, 18500.00, 'LKR', 'PAID', 'https://storage.hrapp.io/receipts/vc-4412.pdf', 'receipts/vc-4412.pdf', '2026-02-10T14:30:00Z', 'Optical subsidy disbursed'),
    ('b0000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'CLM-BEN-2026-003', '2026-02-20', 'd0000000-0000-0000-0000-000000000001', 'Nawaloka Hospitals PLC', 'Specialist Dermatological treatment and prescription', 'NW-88190', 12500.00, NULL, 1250.00, 11250.00, 'LKR', 'UNDER_REVIEW', 'https://storage.hrapp.io/receipts/nw-88190.pdf', 'receipts/nw-88190.pdf', NULL, 'Pending medical officer verification'),
    ('b0000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'CLM-BEN-2026-004', '2026-03-01', NULL, 'Colombo Dental Specialists', 'Routine scaling and ultrasonic prophylaxis', 'CDS-5012', 8000.00, NULL, 1200.00, 6800.00, 'LKR', 'SUBMITTED', 'https://storage.hrapp.io/receipts/cds-5012.pdf', 'receipts/cds-5012.pdf', NULL, 'Submitted via mobile self-service')
ON CONFLICT (tenant_id, claim_number) DO NOTHING;
