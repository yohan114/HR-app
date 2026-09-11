-- ============================================================================
-- V25: Enterprise Project Timesheets, Activity Billing & Attendance Reconciliation
-- Module 5.1 - Clients, Projects, Activities, Billing Rates, Timesheets & Entries
-- ============================================================================

-- 1. Timesheet Client
CREATE TABLE timesheet_client (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    client_code       varchar(50) NOT NULL,
    name              varchar(150) NOT NULL,
    currency          varchar(10) NOT NULL DEFAULT 'USD',
    contact_email     varchar(100),
    contact_person    varchar(100),
    is_active         boolean NOT NULL DEFAULT true,
    created_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by        uuid,
    updated_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by        uuid,
    version           bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_timesheet_client_code UNIQUE (tenant_id, client_code)
);

CREATE INDEX ix_timesheet_client_tenant ON timesheet_client (tenant_id);
SELECT apply_tenant_rls('timesheet_client');

-- 2. Timesheet Project
CREATE TABLE timesheet_project (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    client_id         uuid NOT NULL REFERENCES timesheet_client(id) ON DELETE CASCADE,
    project_code      varchar(50) NOT NULL,
    name              varchar(150) NOT NULL,
    description       text,
    start_date        date NOT NULL,
    end_date          date,
    budget_amount     numeric(12,2),
    budget_hours      numeric(8,2),
    is_billable       boolean NOT NULL DEFAULT true,
    status            varchar(30) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED'
    created_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by        uuid,
    updated_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by        uuid,
    version           bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_timesheet_project_code UNIQUE (tenant_id, project_code),
    CONSTRAINT timesheet_project_status_valid CHECK (status IN ('ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED'))
);

CREATE INDEX ix_timesheet_project_tenant ON timesheet_project (tenant_id);
CREATE INDEX ix_timesheet_project_client ON timesheet_project (tenant_id, client_id);
SELECT apply_tenant_rls('timesheet_project');

-- 3. Timesheet Activity
CREATE TABLE timesheet_activity (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    project_id        uuid REFERENCES timesheet_project(id) ON DELETE CASCADE,
    activity_code     varchar(50) NOT NULL,
    name              varchar(150) NOT NULL,
    description       text,
    is_billable       boolean NOT NULL DEFAULT true,
    default_rate      numeric(10,2) NOT NULL DEFAULT 0.00,
    is_active         boolean NOT NULL DEFAULT true,
    created_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by        uuid,
    updated_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by        uuid,
    version           bigint NOT NULL DEFAULT 0
);

CREATE INDEX ix_timesheet_activity_tenant ON timesheet_activity (tenant_id);
CREATE INDEX ix_timesheet_activity_project ON timesheet_activity (tenant_id, project_id);
SELECT apply_tenant_rls('timesheet_activity');

-- 4. Employee Billing Rate
CREATE TABLE employee_billing_rate (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    employee_id       uuid NOT NULL REFERENCES employee(id) ON DELETE CASCADE,
    project_id        uuid REFERENCES timesheet_project(id) ON DELETE CASCADE,
    rate              numeric(10,2) NOT NULL,
    currency          varchar(10) NOT NULL DEFAULT 'USD',
    effective_from    date NOT NULL,
    effective_to      date,
    created_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by        uuid,
    updated_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by        uuid,
    version           bigint NOT NULL DEFAULT 0
);

CREATE INDEX ix_employee_billing_rate_tenant ON employee_billing_rate (tenant_id);
CREATE INDEX ix_employee_billing_rate_emp ON employee_billing_rate (tenant_id, employee_id);
SELECT apply_tenant_rls('employee_billing_rate');

-- 5. Weekly Timesheet Header
CREATE TABLE timesheet (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    employee_id       uuid NOT NULL REFERENCES employee(id) ON DELETE CASCADE,
    period_start      date NOT NULL,
    period_end        date NOT NULL,
    status            varchar(30) NOT NULL DEFAULT 'DRAFT', -- 'DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED'
    submitted_at      timestamptz,
    approved_by       uuid REFERENCES employee(id) ON DELETE SET NULL,
    approved_at       timestamptz,
    rejection_reason  text,
    total_hours       numeric(6,2) NOT NULL DEFAULT 0.00,
    billable_hours    numeric(6,2) NOT NULL DEFAULT 0.00,
    created_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by        uuid,
    updated_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by        uuid,
    version           bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_timesheet_emp_period UNIQUE (tenant_id, employee_id, period_start),
    CONSTRAINT timesheet_status_valid CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED'))
);

CREATE INDEX ix_timesheet_tenant_emp ON timesheet (tenant_id, employee_id);
CREATE INDEX ix_timesheet_period ON timesheet (tenant_id, period_start, period_end);
SELECT apply_tenant_rls('timesheet');

-- 6. Timesheet Daily Entry
CREATE TABLE timesheet_entry (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    timesheet_id      uuid NOT NULL REFERENCES timesheet(id) ON DELETE CASCADE,
    work_date         date NOT NULL,
    project_id        uuid NOT NULL REFERENCES timesheet_project(id) ON DELETE CASCADE,
    activity_id       uuid NOT NULL REFERENCES timesheet_activity(id) ON DELETE CASCADE,
    hours             numeric(5,2) NOT NULL DEFAULT 0.00,
    description       text,
    is_billable       boolean NOT NULL DEFAULT true,
    rate              numeric(10,2) NOT NULL DEFAULT 0.00,
    amount            numeric(12,2) NOT NULL DEFAULT 0.00,
    created_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by        uuid,
    updated_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by        uuid,
    version           bigint NOT NULL DEFAULT 0
);

CREATE INDEX ix_timesheet_entry_tenant ON timesheet_entry (tenant_id);
CREATE INDEX ix_timesheet_entry_sheet ON timesheet_entry (tenant_id, timesheet_id);
CREATE INDEX ix_timesheet_entry_date ON timesheet_entry (tenant_id, work_date);
SELECT apply_tenant_rls('timesheet_entry');

-- ============================================================================
-- Seed Fixtures for Acme Corp ('00000000-0000-0000-0000-000000000001')
-- ============================================================================

INSERT INTO timesheet_client (id, tenant_id, client_code, name, currency, contact_email, contact_person, is_active)
VALUES 
    ('c0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'CLIENT-FIN', 'Acme FinTech Global', 'USD', 'billing@acmefintech.example.com', 'David Sterling', true),
    ('c0000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'CLIENT-NEXUS', 'Nexus Health Systems', 'USD', 'invoicing@nexushealth.example.com', 'Sarah Connor', true),
    ('c0000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'CLIENT-INTERNAL', 'Acme Internal Engineering', 'USD', 'ops@acme.example.com', 'HR Ops Team', true)
ON CONFLICT (tenant_id, client_code) DO NOTHING;

INSERT INTO timesheet_project (id, tenant_id, client_id, project_code, name, description, start_date, end_date, budget_amount, budget_hours, is_billable, status)
VALUES
    ('b0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'PRJ-MOB-BANK', 'Mobile Banking Platform v2', 'React Native and Kotlin microservices modern banking backend', '2026-01-01', '2026-12-31', 150000.00, 1800.00, true, 'ACTIVE'),
    ('b0000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000002', 'PRJ-HLTH-CLOUD', 'Clinical Cloud EHR Migration', 'Multi-tenant FHIR data pipeline and clinical EHR migration', '2026-02-15', '2026-11-30', 220000.00, 2400.00, true, 'ACTIVE'),
    ('b0000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000003', 'PRJ-INT-HRMS', 'HRMS Platform & Mobile Apps', 'Internal development and Continuous Integration of Acme HR Suite', '2026-01-01', NULL, 80000.00, 1200.00, false, 'ACTIVE')
ON CONFLICT (tenant_id, project_code) DO NOTHING;

INSERT INTO timesheet_activity (id, tenant_id, project_id, activity_code, name, description, is_billable, default_rate, is_active)
VALUES
    ('a0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 'ACT-FE-DEV', 'Frontend Development', 'UI screen construction and Jetpack Compose component development', true, 85.00, true),
    ('a0000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 'ACT-BE-API', 'Backend API & Microservices', 'Spring Boot API endpoints and Postgres persistence logic', true, 95.00, true),
    ('a0000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 'ACT-QA-TEST', 'Automated Testing & QA', 'Unit, integration and end-to-end regression validation', true, 75.00, true),
    ('a0000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000002', 'ACT-HLTH-MIG', 'FHIR Data Mapping & Pipeline', 'ETL integration and HL7/FHIR compliance transformation', true, 110.00, true),
    ('a0000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000003', 'ACT-INT-MTG', 'Engineering Sync & Scrum', 'Team standups, retrospective and planning sessions', false, 0.00, true)
ON CONFLICT DO NOTHING;

INSERT INTO employee_billing_rate (id, tenant_id, employee_id, project_id, rate, currency, effective_from, effective_to)
VALUES
    ('b1000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000010', 'b0000000-0000-0000-0000-000000000001', 90.00, 'USD', '2026-01-01', NULL),
    ('b1000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000010', 'b0000000-0000-0000-0000-000000000002', 105.00, 'USD', '2026-01-01', NULL)
ON CONFLICT DO NOTHING;

-- Seed Sample Timesheet for Kasun Mendis (Week of 2026-03-02 to 2026-03-08)
INSERT INTO timesheet (id, tenant_id, employee_id, period_start, period_end, status, submitted_at, approved_by, approved_at, rejection_reason, total_hours, billable_hours)
VALUES
    ('b2000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000010', '2026-03-02', '2026-03-08', 'SUBMITTED', '2026-03-08 18:00:00+00', NULL, NULL, NULL, 40.00, 36.00)
ON CONFLICT (tenant_id, employee_id, period_start) DO NOTHING;

INSERT INTO timesheet_entry (id, tenant_id, timesheet_id, work_date, project_id, activity_id, hours, description, is_billable, rate, amount)
VALUES
    ('e0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0000-000000000001', '2026-03-02', 'b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000002', 8.00, 'Implemented OAuth2 token rotation endpoint and tests', true, 90.00, 720.00),
    ('e0000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0000-000000000001', '2026-03-03', 'b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000002', 7.50, 'Built API Gateway proxy routing rules and validation filters', true, 90.00, 675.00),
    ('e0000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0000-000000000001', '2026-03-03', 'b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000005', 0.50, 'Weekly engineering sprint planning session', false, 0.00, 0.00),
    ('e0000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0000-000000000001', '2026-03-04', 'b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001', 8.00, 'Designed Compose transaction grid and swipe actions', true, 90.00, 720.00),
    ('e0000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0000-000000000001', '2026-03-05', 'b0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000004', 8.00, 'FHIR resource parsing and mapping for clinical encounters', true, 105.00, 840.00),
    ('e0000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0000-000000000001', '2026-03-06', 'b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000003', 4.50, 'End-to-end integration and load testing validation', true, 90.00, 405.00),
    ('e0000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0000-000000000001', '2026-03-06', 'b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000005', 3.50, 'Quarterly architecture review and technical debt triage', false, 0.00, 0.00)
ON CONFLICT DO NOTHING;
