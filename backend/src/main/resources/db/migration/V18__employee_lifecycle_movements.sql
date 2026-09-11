-- =============================================================================
-- V18 — Employee Lifecycle (ELC) & Career Movements (Phase 4: Module 4.1)
--
-- Adds:
--   - career_movement: Promotions, transfers, confirmations, and salary revisions
--   - employee_salary_history: Effective-dated compensation ledger
--
-- Enforces PostgreSQL Row-Level Security (RLS) on all tables and tenant_id
-- leading composite indexes.
-- =============================================================================

-- =============================================================================
-- 1. Career Movement Table
-- =============================================================================
CREATE TABLE career_movement
(
    id                     uuid           PRIMARY KEY,
    tenant_id              uuid           NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id            uuid           NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    movement_number        varchar(64)    NOT NULL,
    movement_type          varchar(32)    NOT NULL,
    status                 varchar(32)    NOT NULL DEFAULT 'DRAFT',

    request_date           date           NOT NULL,
    effective_date         date           NOT NULL,
    initiator_id           uuid           NOT NULL REFERENCES employee (id),
    approver_id            uuid           REFERENCES employee (id),
    approved_at            timestamptz,
    reverted_at            timestamptz,
    reversion_reason       text,

    justification          text           NOT NULL,
    remarks                text,

    -- Previous State Snapshot
    prev_department_id     uuid           REFERENCES department (id) ON DELETE SET NULL,
    prev_department_name   varchar(128),
    prev_designation_id    uuid           REFERENCES designation (id) ON DELETE SET NULL,
    prev_designation_name  varchar(128),
    prev_salary_grade_id   uuid           REFERENCES salary_grade (id) ON DELETE SET NULL,
    prev_salary_grade_code varchar(32),
    prev_location_id       uuid           REFERENCES location (id) ON DELETE SET NULL,
    prev_location_name     varchar(128),
    prev_supervisor_id     uuid           REFERENCES employee (id) ON DELETE SET NULL,
    prev_supervisor_name   varchar(128),
    prev_base_salary       numeric(14, 2),
    prev_currency          varchar(3)     DEFAULT 'LKR',

    -- Proposed / New State
    new_department_id      uuid           REFERENCES department (id) ON DELETE SET NULL,
    new_department_name    varchar(128),
    new_designation_id     uuid           REFERENCES designation (id) ON DELETE SET NULL,
    new_designation_name   varchar(128),
    new_salary_grade_id    uuid           REFERENCES salary_grade (id) ON DELETE SET NULL,
    new_salary_grade_code  varchar(32),
    new_location_id        uuid           REFERENCES location (id) ON DELETE SET NULL,
    new_location_name      varchar(128),
    new_supervisor_id      uuid           REFERENCES employee (id) ON DELETE SET NULL,
    new_supervisor_name    varchar(128),
    new_base_salary        numeric(14, 2),
    new_currency           varchar(3)     DEFAULT 'LKR',

    -- Cascade Execution Audit
    cascade_applied        boolean        NOT NULL DEFAULT false,
    cascade_applied_at     timestamptz,
    cascade_summary        jsonb          NOT NULL DEFAULT '{}'::jsonb,

    created_at             timestamptz    NOT NULL DEFAULT now(),
    created_by             uuid,
    updated_at             timestamptz    NOT NULL DEFAULT now(),
    updated_by             uuid,
    version                bigint         NOT NULL DEFAULT 0,

    CONSTRAINT uq_career_movement_number UNIQUE (tenant_id, movement_number),
    CONSTRAINT career_movement_type_valid CHECK (
        movement_type IN ('PROMOTION', 'LATERAL_TRANSFER', 'DEMOTION', 'CONFIRMATION', 'SALARY_REVISION', 'DESIGNATION_CHANGE', 'SECONDMENT')
    ),
    CONSTRAINT career_movement_status_valid CHECK (
        status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'SCHEDULED', 'APPLIED', 'REJECTED', 'CANCELLED', 'REVERTED')
    )
);

CREATE INDEX ix_career_movement_emp_date ON career_movement (tenant_id, employee_id, effective_date DESC);
CREATE INDEX ix_career_movement_status ON career_movement (tenant_id, status);
CREATE INDEX ix_career_movement_effective ON career_movement (tenant_id, effective_date) WHERE status IN ('APPROVED', 'SCHEDULED');
SELECT apply_tenant_rls('career_movement');

COMMENT ON TABLE career_movement IS 'Employee career movements, promotions, lateral transfers, and probation confirmation events with automated cascade and rollback.';

-- =============================================================================
-- 2. Employee Salary History Table
-- =============================================================================
CREATE TABLE employee_salary_history
(
    id                 uuid           PRIMARY KEY,
    tenant_id          uuid           NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id        uuid           NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    career_movement_id uuid           REFERENCES career_movement (id) ON DELETE SET NULL,

    effective_from     date           NOT NULL,
    effective_to       date,
    base_salary        numeric(14, 2) NOT NULL,
    currency           varchar(3)     NOT NULL DEFAULT 'LKR',
    change_percentage  numeric(6, 2),
    revision_reason    varchar(128)   NOT NULL,
    is_current         boolean        NOT NULL DEFAULT true,

    created_at         timestamptz    NOT NULL DEFAULT now(),
    created_by         uuid,
    updated_at         timestamptz    NOT NULL DEFAULT now(),
    updated_by         uuid,
    version            bigint         NOT NULL DEFAULT 0,

    CONSTRAINT employee_salary_history_amount_valid CHECK (base_salary >= 0)
);

CREATE INDEX ix_salary_history_emp_current ON employee_salary_history (tenant_id, employee_id, is_current);
CREATE INDEX ix_salary_history_effective ON employee_salary_history (tenant_id, employee_id, effective_from DESC);
SELECT apply_tenant_rls('employee_salary_history');

COMMENT ON TABLE employee_salary_history IS 'Effective-dated compensation ledger capturing salary progressions and adjustments over time.';

-- =============================================================================
-- 3. Seed Career History Fixtures for Acme Corp (Tenant 00000000-0000-0000-0000-000000000001)
-- =============================================================================
INSERT INTO career_movement (
    id, tenant_id, employee_id, movement_number, movement_type, status,
    request_date, effective_date, initiator_id, approver_id, approved_at,
    justification, remarks,
    prev_department_id, prev_department_name, prev_designation_id, prev_designation_name, prev_salary_grade_id, prev_salary_grade_code,
    prev_base_salary, prev_currency,
    new_department_id, new_department_name, new_designation_id, new_designation_name, new_salary_grade_id, new_salary_grade_code,
    new_base_salary, new_currency,
    cascade_applied, cascade_applied_at, cascade_summary
) VALUES
(
    'b0000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'MOV-2024-001',
    'CONFIRMATION',
    'APPLIED',
    '2024-06-15',
    '2024-07-01',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    '2024-06-25T09:00:00Z',
    'Satisfactory completion of 6-month probationary review with distinction',
    'Confirmed into permanent engineering cadre',
    NULL, 'Engineering', NULL, 'Associate Software Engineer', NULL, 'E1',
    100000.00, 'LKR',
    NULL, 'Engineering', NULL, 'Associate Software Engineer', NULL, 'E1',
    120000.00, 'LKR',
    true, '2024-07-01T00:00:00Z',
    '{"employeeMasterUpdated": true, "confirmationDateSet": "2024-07-01", "salaryRevised": true}'::jsonb
),
(
    'b0000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'MOV-2025-002',
    'PROMOTION',
    'APPLIED',
    '2025-05-10',
    '2025-06-01',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    '2025-05-20T11:00:00Z',
    'Exceeded annual performance targets and led high-scale payments refactoring',
    'Promoted to Software Engineer (Grade E2)',
    NULL, 'Engineering', NULL, 'Associate Software Engineer', NULL, 'E1',
    120000.00, 'LKR',
    NULL, 'Engineering', NULL, 'Software Engineer', NULL, 'E2',
    150000.00, 'LKR',
    true, '2025-06-01T00:00:00Z',
    '{"employeeMasterUpdated": true, "designationChanged": "Software Engineer", "gradeChanged": "E2", "salaryRevised": true}'::jsonb
),
(
    'b0000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'MOV-2026-003',
    'PROMOTION',
    'APPROVED',
    '2026-03-01',
    '2026-04-01',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    '2026-03-05T14:30:00Z',
    'Promotion to Senior Software Engineer & Technical Lead for enterprise mobile suite',
    'Approved by CTO and HR Executive Committee; scheduled to take effect April 1st',
    NULL, 'Engineering', NULL, 'Software Engineer', NULL, 'E2',
    150000.00, 'LKR',
    NULL, 'Engineering', NULL, 'Senior Software Engineer', NULL, 'M1',
    220000.00, 'LKR',
    false, NULL,
    '{"status": "SCHEDULED_FOR_CASCADE", "effectiveDate": "2026-04-01"}'::jsonb
)
ON CONFLICT (tenant_id, movement_number) DO NOTHING;

INSERT INTO employee_salary_history (
    id, tenant_id, employee_id, career_movement_id, effective_from, effective_to, base_salary, currency, change_percentage, revision_reason, is_current
) VALUES
(
    'c0000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    NULL,
    '2024-01-01',
    '2024-06-30',
    100000.00,
    'LKR',
    NULL,
    'Initial Joining Salary',
    false
),
(
    'c0000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'b0000000-0000-0000-0000-000000000001',
    '2024-07-01',
    '2025-05-31',
    120000.00,
    'LKR',
    20.00,
    'Probation Confirmation Increment',
    false
),
(
    'c0000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'b0000000-0000-0000-0000-000000000002',
    '2025-06-01',
    NULL,
    150000.00,
    'LKR',
    25.00,
    'Promotion to Software Engineer',
    true
)
ON CONFLICT DO NOTHING;
