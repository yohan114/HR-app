-- =============================================================================
-- V19 — Disciplinary Management, Grievance Redressal & Incident Tracking (Phase 4: Module 4.2)
--
-- Adds:
--   - grievance_ground_group: High-level classification of grievance grounds
--   - grievance_ground: Configurable grounds with SLA target days and severity
--   - grievance_channel: Channels (Direct Portal, Anonymous Hotline, Ombudsman, Union)
--   - grievance: Core employee grievance records (supports anonymous whistleblowing)
--   - grievance_appeal: Appeals against grievance resolutions
--   - incident_type: High-level disciplinary incident classifications
--   - incident_subtype: Detailed incident subcategories
--   - disciplinary_incident: Workplace incident reporting and case management
--   - corrective_action: Progressive discipline actions (Show Cause, Warnings, Domestic Inquiry)
--   - incident_journal: Investigation case notes and chronological journal
--   - disciplinary_appeal: Formal employee appeals against issued corrective actions
--
-- Enforces PostgreSQL Row-Level Security (RLS) on all tables and tenant_id
-- leading composite indexes.
-- =============================================================================

-- =============================================================================
-- 1. Grievance Master Tables
-- =============================================================================
CREATE TABLE grievance_ground_group
(
    id          uuid         PRIMARY KEY,
    tenant_id   uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code        varchar(32)  NOT NULL,
    name        varchar(128) NOT NULL,
    description text,

    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  uuid,
    version     bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uq_grievance_ground_group_code UNIQUE (tenant_id, code)
);

CREATE INDEX ix_grievance_ground_grp_tenant ON grievance_ground_group (tenant_id);
SELECT apply_tenant_rls('grievance_ground_group');
COMMENT ON TABLE grievance_ground_group IS 'High-level category groups for employee grievance grounds (Work Environment, Harassment, Compensation, etc.).';

CREATE TABLE grievance_ground
(
    id                   uuid         PRIMARY KEY,
    tenant_id            uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    group_id             uuid         NOT NULL REFERENCES grievance_ground_group (id) ON DELETE CASCADE,
    code                 varchar(64)  NOT NULL,
    name                 varchar(128) NOT NULL,
    description          text,
    severity             varchar(32)  NOT NULL DEFAULT 'MEDIUM',
    default_handler_role varchar(64)  NOT NULL DEFAULT 'HR_MANAGER',
    sla_days             integer      NOT NULL DEFAULT 5,

    created_at           timestamptz  NOT NULL DEFAULT now(),
    created_by           uuid,
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    updated_by           uuid,
    version              bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uq_grievance_ground_code UNIQUE (tenant_id, code),
    CONSTRAINT grievance_ground_severity_valid CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT grievance_ground_sla_valid CHECK (sla_days > 0)
);

CREATE INDEX ix_grievance_ground_group ON grievance_ground (tenant_id, group_id);
SELECT apply_tenant_rls('grievance_ground');
COMMENT ON TABLE grievance_ground IS 'Specific grounds for grievances with SLA resolution days, severity, and assigned handler role.';

CREATE TABLE grievance_channel
(
    id              uuid         PRIMARY KEY,
    tenant_id       uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code            varchar(32)  NOT NULL,
    name            varchar(128) NOT NULL,
    is_confidential boolean      NOT NULL DEFAULT true,

    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      uuid,
    version         bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uq_grievance_channel_code UNIQUE (tenant_id, code)
);

CREATE INDEX ix_grievance_channel_tenant ON grievance_channel (tenant_id);
SELECT apply_tenant_rls('grievance_channel');
COMMENT ON TABLE grievance_channel IS 'Channels of grievance submission (Portal, Whistleblower Hotline, Ombudsman, Union).';

-- =============================================================================
-- 2. Grievance Transactional Tables
-- =============================================================================
CREATE TABLE grievance
(
    id                       uuid         PRIMARY KEY,
    tenant_id                uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    grievance_number         varchar(64)  NOT NULL,
    raised_by_employee_id    uuid         REFERENCES employee (id) ON DELETE SET NULL,
    on_behalf_of_employee_id uuid         REFERENCES employee (id) ON DELETE SET NULL,
    anonymous                boolean      NOT NULL DEFAULT false,
    ground_id                uuid         NOT NULL REFERENCES grievance_ground (id),
    channel_id               uuid         NOT NULL REFERENCES grievance_channel (id),

    title                    varchar(255) NOT NULL,
    description              text         NOT NULL,
    attachment_keys          text[],

    status                   varchar(32)  NOT NULL DEFAULT 'SUBMITTED',
    handler_user_id          uuid,
    raised_at                timestamptz  NOT NULL DEFAULT now(),
    target_resolution_date   timestamptz  NOT NULL,
    resolved_at              timestamptz,
    resolution               text,
    satisfaction_rating      integer,

    created_at               timestamptz  NOT NULL DEFAULT now(),
    created_by               uuid,
    updated_at               timestamptz  NOT NULL DEFAULT now(),
    updated_by               uuid,
    version                  bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uq_grievance_number UNIQUE (tenant_id, grievance_number),
    CONSTRAINT grievance_status_valid CHECK (status IN ('SUBMITTED', 'ASSIGNED', 'UNDER_INVESTIGATION', 'RESOLVED', 'APPEALED', 'CLOSED')),
    CONSTRAINT grievance_rating_valid CHECK (satisfaction_rating IS NULL OR (satisfaction_rating >= 1 AND satisfaction_rating <= 5))
);

CREATE INDEX ix_grievance_emp ON grievance (tenant_id, raised_by_employee_id, status);
CREATE INDEX ix_grievance_status ON grievance (tenant_id, status, target_resolution_date);
SELECT apply_tenant_rls('grievance');
COMMENT ON TABLE grievance IS 'Employee grievances and whistleblowing complaints with SLA target resolution tracking.';

CREATE TABLE grievance_appeal
(
    id               uuid        PRIMARY KEY,
    tenant_id        uuid        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    grievance_id     uuid        NOT NULL REFERENCES grievance (id) ON DELETE CASCADE,
    reason           text        NOT NULL,
    appealed_at      timestamptz NOT NULL DEFAULT now(),
    reviewer_user_id uuid,
    outcome          text,
    reviewed_at      timestamptz,
    status           varchar(32) NOT NULL DEFAULT 'PENDING',

    created_at       timestamptz NOT NULL DEFAULT now(),
    created_by       uuid,
    updated_at       timestamptz NOT NULL DEFAULT now(),
    updated_by       uuid,
    version          bigint      NOT NULL DEFAULT 0,

    CONSTRAINT grievance_appeal_status_valid CHECK (status IN ('PENDING', 'UPHELD', 'MODIFIED', 'DISMISSED'))
);

CREATE INDEX ix_grievance_appeal_grievance ON grievance_appeal (tenant_id, grievance_id);
SELECT apply_tenant_rls('grievance_appeal');
COMMENT ON TABLE grievance_appeal IS 'Formal appeals lodged by employees against grievance resolutions.';

-- =============================================================================
-- 3. Disciplinary Incident Master & Subtypes
-- =============================================================================
CREATE TABLE incident_type
(
    id          uuid         PRIMARY KEY,
    tenant_id   uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code        varchar(64)  NOT NULL,
    name        varchar(128) NOT NULL,
    description text,
    severity    varchar(32)  NOT NULL DEFAULT 'MEDIUM',

    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  uuid,
    version     bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uq_incident_type_code UNIQUE (tenant_id, code),
    CONSTRAINT incident_type_severity_valid CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX ix_incident_type_tenant ON incident_type (tenant_id);
SELECT apply_tenant_rls('incident_type');
COMMENT ON TABLE incident_type IS 'High-level classifications of disciplinary infractions (Misconduct, Attendance, Safety, etc.).';

CREATE TABLE incident_subtype
(
    id               uuid         PRIMARY KEY,
    tenant_id        uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    incident_type_id uuid         NOT NULL REFERENCES incident_type (id) ON DELETE CASCADE,
    code             varchar(64)  NOT NULL,
    name             varchar(128) NOT NULL,
    description      text,

    created_at       timestamptz  NOT NULL DEFAULT now(),
    created_by       uuid,
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    updated_by       uuid,
    version          bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uq_incident_subtype_code UNIQUE (tenant_id, incident_type_id, code)
);

CREATE INDEX ix_incident_subtype_type ON incident_subtype (tenant_id, incident_type_id);
SELECT apply_tenant_rls('incident_subtype');
COMMENT ON TABLE incident_subtype IS 'Detailed subtypes for disciplinary incidents (e.g. Insubordination, Unauthorized Absence).';

-- =============================================================================
-- 4. Disciplinary Incident Reporting & Investigation
-- =============================================================================
CREATE TABLE disciplinary_incident
(
    id                      uuid         PRIMARY KEY,
    tenant_id               uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    incident_number         varchar(64)  NOT NULL,
    employee_id             uuid         NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    reported_by_employee_id uuid         NOT NULL REFERENCES employee (id),
    incident_type_id        uuid         NOT NULL REFERENCES incident_type (id),
    subtype_id              uuid         REFERENCES incident_subtype (id),

    incident_date           date         NOT NULL,
    location                varchar(128),
    description             text         NOT NULL,
    witnesses               jsonb,
    attachment_keys         text[],

    status                  varchar(32)  NOT NULL DEFAULT 'REPORTED',
    severity                varchar(32)  NOT NULL DEFAULT 'MEDIUM',

    created_at              timestamptz  NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_at              timestamptz  NOT NULL DEFAULT now(),
    updated_by              uuid,
    version                 bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uq_disciplinary_incident_number UNIQUE (tenant_id, incident_number),
    CONSTRAINT disciplinary_incident_status_valid CHECK (status IN ('REPORTED', 'UNDER_INVESTIGATION', 'ACTION_PROPOSED', 'ACTION_ISSUED', 'APPEALED', 'CONCLUDED')),
    CONSTRAINT disciplinary_incident_severity_valid CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX ix_disc_incident_emp ON disciplinary_incident (tenant_id, employee_id, status);
CREATE INDEX ix_disc_incident_date ON disciplinary_incident (tenant_id, incident_date DESC);
SELECT apply_tenant_rls('disciplinary_incident');
COMMENT ON TABLE disciplinary_incident IS 'Workplace incident reports and active disciplinary cases.';

-- =============================================================================
-- 5. Progressive Corrective Actions
-- =============================================================================
CREATE TABLE corrective_action
(
    id                uuid         PRIMARY KEY,
    tenant_id         uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    incident_id       uuid         NOT NULL REFERENCES disciplinary_incident (id) ON DELETE CASCADE,
    action_type       varchar(32)  NOT NULL,
    issued_at         timestamptz  NOT NULL DEFAULT now(),
    issued_by         uuid         NOT NULL REFERENCES employee (id),
    document_key      varchar(255),
    title             varchar(255) NOT NULL,
    details           text         NOT NULL,
    response_due_date date,
    employee_response text,
    responded_at      timestamptz,
    outcome           text,
    effective_from    date,
    effective_to      date,
    status            varchar(32)  NOT NULL DEFAULT 'ISSUED',

    created_at        timestamptz  NOT NULL DEFAULT now(),
    created_by        uuid,
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    updated_by        uuid,
    version           bigint       NOT NULL DEFAULT 0,

    CONSTRAINT corrective_action_type_valid CHECK (action_type IN ('ORAL_WARNING', 'WRITTEN_WARNING', 'SHOW_CAUSE', 'CHARGE_SHEET', 'DOMESTIC_INQUIRY', 'SUSPENSION', 'DEMOTION', 'TERMINATION')),
    CONSTRAINT corrective_action_status_valid CHECK (status IN ('ISSUED', 'RESPONDED', 'UNDER_REVIEW', 'CONFIRMED', 'REVOKED', 'APPEALED'))
);

CREATE INDEX ix_corrective_action_incident ON corrective_action (tenant_id, incident_id);
SELECT apply_tenant_rls('corrective_action');
COMMENT ON TABLE corrective_action IS 'Progressive discipline corrective actions issued to employees.';

-- =============================================================================
-- 6. Incident Investigation Journal & Disciplinary Appeals
-- =============================================================================
CREATE TABLE incident_journal
(
    id          uuid        PRIMARY KEY,
    tenant_id   uuid        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    incident_id uuid        NOT NULL REFERENCES disciplinary_incident (id) ON DELETE CASCADE,
    entry       text        NOT NULL,
    entered_by  uuid        NOT NULL REFERENCES employee (id),
    entered_at  timestamptz NOT NULL DEFAULT now(),

    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  uuid,
    version     bigint      NOT NULL DEFAULT 0
);

CREATE INDEX ix_incident_journal_incident ON incident_journal (tenant_id, incident_id, entered_at);
SELECT apply_tenant_rls('incident_journal');
COMMENT ON TABLE incident_journal IS 'Chronological investigation journal entries and case notes for disciplinary incidents.';

CREATE TABLE disciplinary_appeal
(
    id                   uuid        PRIMARY KEY,
    tenant_id            uuid        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    incident_id          uuid        NOT NULL REFERENCES disciplinary_incident (id) ON DELETE CASCADE,
    corrective_action_id uuid        NOT NULL REFERENCES corrective_action (id) ON DELETE CASCADE,
    reason               text        NOT NULL,
    appealed_at          timestamptz NOT NULL DEFAULT now(),
    reviewer_user_id     uuid,
    outcome              text,
    reviewed_at          timestamptz,
    status               varchar(32) NOT NULL DEFAULT 'SUBMITTED',

    created_at           timestamptz NOT NULL DEFAULT now(),
    created_by           uuid,
    updated_at           timestamptz NOT NULL DEFAULT now(),
    updated_by           uuid,
    version              bigint      NOT NULL DEFAULT 0,

    CONSTRAINT disciplinary_appeal_status_valid CHECK (status IN ('SUBMITTED', 'UPHELD', 'REDUCED', 'OVERTURNED'))
);

CREATE INDEX ix_disc_appeal_action ON disciplinary_appeal (tenant_id, corrective_action_id);
SELECT apply_tenant_rls('disciplinary_appeal');
COMMENT ON TABLE disciplinary_appeal IS 'Formal appeals against disciplinary corrective actions.';

-- =============================================================================
-- 7. Seed Demonstration Fixtures for Acme Corp (Tenant 00000000-0000-0000-0000-000000000001)
-- =============================================================================
INSERT INTO grievance_ground_group (id, tenant_id, code, name, description)
VALUES
    ('d0000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'WORK_ENVIRONMENT', 'Work Environment & Facilities', 'Physical workplace conditions, safety, ergonomic equipment, and health standards'),
    ('d0000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'HARASSMENT_DISCRIMINATION', 'Harassment, Bullying & Discrimination', 'Interpersonal misconduct, hostile work environment, unfair bias, or discrimination'),
    ('d0000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'COMPENSATION_BENEFITS', 'Compensation & Benefits Disputes', 'Discrepancies in payroll, overtime pay, medical benefits, or allowance disbursement'),
    ('d0000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'SUPERVISION_INTERPERSONAL', 'Supervision & Management Practices', 'Unfair workload distribution, appraisal bias, lack of consultation, or micromanagement')
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO grievance_ground (id, tenant_id, group_id, code, name, description, severity, default_handler_role, sla_days)
VALUES
    ('d1000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001', 'SAFETY_HAZARD', 'Workplace Safety Hazard', 'Imminent risk to health and physical safety in workplace facilities', 'CRITICAL', 'OPERATIONS_DIRECTOR', 2),
    ('d1000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000002', 'BULLYING_HARASSMENT', 'Bullying or Verbal Harassment', 'Unacceptable interpersonal hostility, intimidation, or verbal abuse', 'HIGH', 'HR_OMBUDSMAN', 3),
    ('d1000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000003', 'OVERTIME_DISPUTE', 'Overtime Calculation Discrepancy', 'Disputed overtime hours or incorrect statutory multiplier application', 'MEDIUM', 'PAYROLL_ADMIN', 5),
    ('d1000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000004', 'APPRAISAL_UNFAIRNESS', 'Performance Appraisal Grievance', 'Unsubstantiated rating or failure to follow performance calibration policy', 'MEDIUM', 'HR_BUSINESS_PARTNER', 7)
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO grievance_channel (id, tenant_id, code, name, is_confidential)
VALUES
    ('d2000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'DIRECT_PORTAL', 'Self-Service Mobile & Web Portal', true),
    ('d2000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'ANONYMOUS_HOTLINE', 'Anonymous Whistleblower Hotline', true),
    ('d2000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'HR_OMBUDSMAN', 'HR Ombudsman Confidential Office', true)
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO incident_type (id, tenant_id, code, name, description, severity)
VALUES
    ('d3000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'ATTENDANCE_BREACH', 'Attendance & Timekeeping Violation', 'Habitual lateness, unauthorized absence, or clock falsification', 'MEDIUM'),
    ('d3000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'POLICY_MISCONDUCT', 'Policy Misconduct & Insubordination', 'Refusal to follow lawful instructions or breach of company code of conduct', 'HIGH'),
    ('d3000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'SECURITY_DATA_BREACH', 'Confidentiality & Data Protection Breach', 'Unauthorized disclosure of proprietary IP, client secrets, or GDPR violation', 'CRITICAL')
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO incident_subtype (id, tenant_id, incident_type_id, code, name, description)
VALUES
    ('d4000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'd3000000-0000-0000-0000-000000000001', 'UNAUTHORIZED_ABSENCE', 'Unauthorized Absence / AWOL', 'Absence from scheduled shift for 3+ consecutive days without prior notification'),
    ('d4000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'd3000000-0000-0000-0000-000000000002', 'INSUBORDINATION', 'Direct Insubordination', 'Willful non-compliance with documented supervisor directives'),
    ('d4000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'd3000000-0000-0000-0000-000000000003', 'SOURCE_CODE_EXFILTRATION', 'Source Code & Secret Leakage', 'Public exposure or unauthorized transmission of proprietary codebase assets')
ON CONFLICT (tenant_id, incident_type_id, code) DO NOTHING;

-- Seed Sample Grievance for Kasun Mendis (Tenant 00000000-0000-0000-0000-000000000001)
INSERT INTO grievance (
    id, tenant_id, grievance_number, raised_by_employee_id, anonymous, ground_id, channel_id,
    title, description, status, raised_at, target_resolution_date, resolved_at, resolution, satisfaction_rating
) VALUES
(
    'd5000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'GRV-2026-0001',
    '00000000-0000-0000-0000-000000000001',
    false,
    'd1000000-0000-0000-0000-000000000003',
    'd2000000-0000-0000-0000-000000000001',
    'Discrepancy in February 2026 Rest Day Overtime Multiplier',
    'The February 2026 payroll statement computed rest-day overtime at 1.5x normal rate instead of statutory 2.0x for Sunday release on-call shift.',
    'RESOLVED',
    '2026-03-01T08:30:00Z',
    '2026-03-06T08:30:00Z',
    '2026-03-03T16:00:00Z',
    'Payroll operations verified the rest-day shift classification and adjusted the retro variance (+LKR 3,408.00) in the upcoming March payroll disbursement.',
    5
)
ON CONFLICT (tenant_id, grievance_number) DO NOTHING;
