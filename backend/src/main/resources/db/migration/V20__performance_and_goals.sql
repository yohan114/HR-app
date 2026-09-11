-- =============================================================================
-- V20 — Performance Management, Goal Tracking (OKRs) & 360° Appraisals (Phase 4: Module 4.3)
--
-- Adds:
--   - goal_cycle: Annual or quarterly goal tracking cycle
--   - goal: Employee goals / OKR objectives with measurable metrics and progress
--   - goal_check_in: Chronological progress check-ins and notes
--   - competency_group: High-level competency clusters
--   - competency: Specific behavioral and technical competencies
--   - evaluation_cycle: Formal appraisal periods with review deadlines and weights
--   - performance_appraisal: Master appraisal records with multi-stage review states
--   - appraisal_goal_rating: Self and manager ratings for individual goals
--   - appraisal_competency_rating: Self and manager ratings for competencies
--   - continuous_feedback: Peer praise, 1-on-1 coaching logs, and check-in notes
--   - mra_request: 360 Multi-Rater Assessment requests
--   - mra_rating: 360 Competency scores and qualitative feedback
--
-- Enforces PostgreSQL Row-Level Security (RLS) on all tables and tenant_id
-- leading composite indexes.
-- =============================================================================

-- =============================================================================
-- 1. Goal Tracking & OKRs Tables
-- =============================================================================
CREATE TABLE goal_cycle
(
    id          uuid         PRIMARY KEY,
    tenant_id   uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code        varchar(64)  NOT NULL,
    name        varchar(128) NOT NULL,
    start_date  date         NOT NULL,
    end_date    date         NOT NULL,
    status      varchar(32)  NOT NULL DEFAULT 'ACTIVE',

    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  uuid,
    version     bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uq_goal_cycle_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_goal_cycle_dates CHECK (end_date >= start_date)
);

CREATE INDEX ix_goal_cycle_tenant ON goal_cycle (tenant_id);
SELECT apply_tenant_rls('goal_cycle');
COMMENT ON TABLE goal_cycle IS 'Goal cycles (annual or quarterly) for OKRs and performance objectives.';

CREATE TABLE goal
(
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id         uuid          NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    cycle_id            uuid          NOT NULL REFERENCES goal_cycle (id) ON DELETE CASCADE,
    parent_goal_id      uuid          REFERENCES goal (id) ON DELETE SET NULL,
    title               varchar(255)  NOT NULL,
    description         text,
    category            varchar(32)   NOT NULL DEFAULT 'INDIVIDUAL',
    weight              numeric(5, 2) NOT NULL DEFAULT 100.00,
    target_value        numeric(12, 2) NOT NULL DEFAULT 100.00,
    current_value       numeric(12, 2) NOT NULL DEFAULT 0.00,
    unit                varchar(32)   NOT NULL DEFAULT '%',
    start_date          date          NOT NULL,
    due_date            date          NOT NULL,
    status              varchar(32)   NOT NULL DEFAULT 'NOT_STARTED',
    progress_percentage numeric(5, 2) NOT NULL DEFAULT 0.00,

    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          uuid,
    version             bigint        NOT NULL DEFAULT 0,

    CONSTRAINT ck_goal_dates CHECK (due_date >= start_date),
    CONSTRAINT ck_goal_category CHECK (category IN ('ORGANIZATIONAL', 'DEPARTMENTAL', 'INDIVIDUAL', 'DEVELOPMENTAL')),
    CONSTRAINT ck_goal_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'ON_TRACK', 'AT_RISK', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX ix_goal_tenant_emp ON goal (tenant_id, employee_id);
CREATE INDEX ix_goal_tenant_cycle ON goal (tenant_id, cycle_id);
SELECT apply_tenant_rls('goal');
COMMENT ON TABLE goal IS 'Individual and team goals / OKRs with progress tracking.';

CREATE TABLE goal_check_in
(
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    goal_id             uuid          NOT NULL REFERENCES goal (id) ON DELETE CASCADE,
    previous_value      numeric(12, 2) NOT NULL,
    new_value           numeric(12, 2) NOT NULL,
    progress_percentage numeric(5, 2) NOT NULL,
    note                text,
    checked_in_by       uuid          NOT NULL REFERENCES employee (id),

    created_at          timestamptz   NOT NULL DEFAULT clock_timestamp(),
    created_by          uuid,
    updated_at          timestamptz   NOT NULL DEFAULT clock_timestamp(),
    updated_by          uuid,
    version             bigint        NOT NULL DEFAULT 0
);

CREATE INDEX ix_goal_checkin_tenant_goal ON goal_check_in (tenant_id, goal_id);
SELECT apply_tenant_rls('goal_check_in');
COMMENT ON TABLE goal_check_in IS 'Progress updates and check-in comments for performance goals.';

-- =============================================================================
-- 2. Competency Framework Tables
-- =============================================================================
CREATE TABLE competency_group
(
    id          uuid         PRIMARY KEY,
    tenant_id   uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code        varchar(64)  NOT NULL,
    name        varchar(128) NOT NULL,
    description text,

    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  uuid,
    version     bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uq_competency_group_code UNIQUE (tenant_id, code)
);

CREATE INDEX ix_competency_group_tenant ON competency_group (tenant_id);
SELECT apply_tenant_rls('competency_group');
COMMENT ON TABLE competency_group IS 'Clusters for company competencies (Technical, Leadership, Core Values).';

CREATE TABLE competency
(
    id           uuid         PRIMARY KEY,
    tenant_id    uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    group_id     uuid         NOT NULL REFERENCES competency_group (id) ON DELETE CASCADE,
    code         varchar(64)  NOT NULL,
    name         varchar(128) NOT NULL,
    description  text,
    target_level integer      NOT NULL DEFAULT 3,

    created_at   timestamptz  NOT NULL DEFAULT now(),
    created_by   uuid,
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    updated_by   uuid,
    version      bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uq_competency_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_competency_level CHECK (target_level BETWEEN 1 AND 5)
);

CREATE INDEX ix_competency_tenant_group ON competency (tenant_id, group_id);
SELECT apply_tenant_rls('competency');
COMMENT ON TABLE competency IS 'Individual competencies with expected proficiency level ratings (1-5).';

-- =============================================================================
-- 3. Evaluation Cycle & Appraisal Tables
-- =============================================================================
CREATE TABLE evaluation_cycle
(
    id                       uuid          PRIMARY KEY,
    tenant_id                uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code                     varchar(64)   NOT NULL,
    name                     varchar(128)  NOT NULL,
    start_date               date          NOT NULL,
    end_date                 date          NOT NULL,
    goal_weight              numeric(5, 2) NOT NULL DEFAULT 60.00,
    competency_weight        numeric(5, 2) NOT NULL DEFAULT 30.00,
    mra_weight               numeric(5, 2) NOT NULL DEFAULT 10.00,
    self_review_deadline     date          NOT NULL,
    manager_review_deadline  date          NOT NULL,
    calibration_deadline     date          NOT NULL,
    status                   varchar(32)   NOT NULL DEFAULT 'ACTIVE',

    created_at               timestamptz   NOT NULL DEFAULT now(),
    created_by               uuid,
    updated_at               timestamptz   NOT NULL DEFAULT now(),
    updated_by               uuid,
    version                  bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_evaluation_cycle_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_eval_cycle_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_eval_weights CHECK ((goal_weight + competency_weight + mra_weight) = 100.00),
    CONSTRAINT ck_eval_cycle_status CHECK (status IN ('SETUP', 'ACTIVE', 'IN_EVALUATION', 'CALIBRATION', 'CLOSED'))
);

CREATE INDEX ix_eval_cycle_tenant ON evaluation_cycle (tenant_id);
SELECT apply_tenant_rls('evaluation_cycle');
COMMENT ON TABLE evaluation_cycle IS 'Formal performance appraisal review cycles and deadlines.';

CREATE TABLE performance_appraisal
(
    id                         uuid          PRIMARY KEY,
    tenant_id                  uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    cycle_id                   uuid          NOT NULL REFERENCES evaluation_cycle (id) ON DELETE CASCADE,
    employee_id                uuid          NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    manager_id                 uuid          NOT NULL REFERENCES employee (id),
    second_reviewer_id         uuid          REFERENCES employee (id),
    status                     varchar(32)   NOT NULL DEFAULT 'NOT_STARTED',

    self_goals_score           numeric(5, 2),
    manager_goals_score        numeric(5, 2),
    self_competency_score      numeric(5, 2),
    manager_competency_score   numeric(5, 2),
    mra_score                  numeric(5, 2),
    final_score                numeric(5, 2),
    final_rating               varchar(64),

    self_overall_comments      text,
    manager_overall_comments   text,
    calibration_notes          text,
    employee_acknowledged_at   timestamptz,

    created_at                 timestamptz   NOT NULL DEFAULT now(),
    created_by                 uuid,
    updated_at                 timestamptz   NOT NULL DEFAULT now(),
    updated_by                 uuid,
    version                    bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_appraisal_employee_cycle UNIQUE (tenant_id, cycle_id, employee_id),
    CONSTRAINT ck_appraisal_status CHECK (status IN (
        'NOT_STARTED',
        'SELF_REVIEW_PENDING',
        'SELF_REVIEW_SUBMITTED',
        'MANAGER_REVIEW_PENDING',
        'MANAGER_REVIEW_SUBMITTED',
        'IN_CALIBRATION',
        'ACKNOWLEDGED',
        'CLOSED'
    ))
);

CREATE INDEX ix_appraisal_tenant_emp ON performance_appraisal (tenant_id, employee_id);
CREATE INDEX ix_appraisal_tenant_mgr ON performance_appraisal (tenant_id, manager_id);
CREATE INDEX ix_appraisal_tenant_cycle ON performance_appraisal (tenant_id, cycle_id);
SELECT apply_tenant_rls('performance_appraisal');
COMMENT ON TABLE performance_appraisal IS 'Master employee appraisal record managing multi-stage review workflow.';

CREATE TABLE appraisal_goal_rating
(
    id              uuid          PRIMARY KEY,
    tenant_id       uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    appraisal_id    uuid          NOT NULL REFERENCES performance_appraisal (id) ON DELETE CASCADE,
    goal_id         uuid          NOT NULL REFERENCES goal (id) ON DELETE CASCADE,
    self_rating     numeric(5, 2),
    self_comments   text,
    manager_rating  numeric(5, 2),
    manager_comments text,
    weighted_score  numeric(5, 2),

    created_at      timestamptz   NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    updated_by      uuid,
    version         bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_appraisal_goal UNIQUE (tenant_id, appraisal_id, goal_id)
);

CREATE INDEX ix_appraisal_goal_tenant ON appraisal_goal_rating (tenant_id, appraisal_id);
SELECT apply_tenant_rls('appraisal_goal_rating');
COMMENT ON TABLE appraisal_goal_rating IS 'Goal ratings and remarks within an appraisal.';

CREATE TABLE appraisal_competency_rating
(
    id                         uuid         PRIMARY KEY,
    tenant_id                  uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    appraisal_id               uuid         NOT NULL REFERENCES performance_appraisal (id) ON DELETE CASCADE,
    competency_id              uuid         NOT NULL REFERENCES competency (id) ON DELETE CASCADE,
    self_proficiency_level     integer,
    manager_proficiency_level  integer,
    self_comments              text,
    manager_comments           text,

    created_at                 timestamptz  NOT NULL DEFAULT now(),
    created_by                 uuid,
    updated_at                 timestamptz  NOT NULL DEFAULT now(),
    updated_by                 uuid,
    version                    bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uq_appraisal_comp UNIQUE (tenant_id, appraisal_id, competency_id),
    CONSTRAINT ck_appraisal_comp_self CHECK (self_proficiency_level IS NULL OR self_proficiency_level BETWEEN 1 AND 5),
    CONSTRAINT ck_appraisal_comp_mgr CHECK (manager_proficiency_level IS NULL OR manager_proficiency_level BETWEEN 1 AND 5)
);

CREATE INDEX ix_appraisal_comp_tenant ON appraisal_competency_rating (tenant_id, appraisal_id);
SELECT apply_tenant_rls('appraisal_competency_rating');
COMMENT ON TABLE appraisal_competency_rating IS 'Competency ratings and remarks within an appraisal.';

-- =============================================================================
-- 4. Continuous Feedback & 360 Multi-Rater Assessment (MRA)
-- =============================================================================
CREATE TABLE continuous_feedback
(
    id                     uuid         PRIMARY KEY,
    tenant_id              uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    sender_employee_id     uuid         NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    recipient_employee_id  uuid         NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    feedback_type          varchar(32)  NOT NULL DEFAULT 'PRAISE',
    title                  varchar(255) NOT NULL,
    content                text         NOT NULL,
    is_private             boolean      NOT NULL DEFAULT false,
    shared_with_manager    boolean      NOT NULL DEFAULT true,

    created_at             timestamptz  NOT NULL DEFAULT clock_timestamp(),
    created_by             uuid,
    updated_at             timestamptz  NOT NULL DEFAULT clock_timestamp(),
    updated_by             uuid,
    version                bigint       NOT NULL DEFAULT 0,

    CONSTRAINT ck_feedback_type CHECK (feedback_type IN ('PRAISE', 'COACHING', 'ONE_ON_ONE_NOTE', 'CHECK_IN'))
);

CREATE INDEX ix_feedback_tenant_recip ON continuous_feedback (tenant_id, recipient_employee_id);
CREATE INDEX ix_feedback_tenant_sender ON continuous_feedback (tenant_id, sender_employee_id);
SELECT apply_tenant_rls('continuous_feedback');
COMMENT ON TABLE continuous_feedback IS 'Ongoing 1-on-1 coaching notes, peer praise, and check-in logs.';

CREATE TABLE mra_request
(
    id                  uuid         PRIMARY KEY,
    tenant_id           uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    appraisal_id        uuid         NOT NULL REFERENCES performance_appraisal (id) ON DELETE CASCADE,
    subject_employee_id uuid         NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    rater_employee_id   uuid         NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    relationship        varchar(32)  NOT NULL DEFAULT 'PEER',
    status              varchar(32)  NOT NULL DEFAULT 'REQUESTED',
    requested_at        timestamptz  NOT NULL DEFAULT now(),
    submitted_at        timestamptz,

    created_at          timestamptz  NOT NULL DEFAULT clock_timestamp(),
    created_by          uuid,
    updated_at          timestamptz  NOT NULL DEFAULT clock_timestamp(),
    updated_by          uuid,
    version             bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uq_mra_request UNIQUE (tenant_id, appraisal_id, rater_employee_id),
    CONSTRAINT ck_mra_rel CHECK (relationship IN ('PEER', 'SUBORDINATE', 'STAKEHOLDER', 'CROSS_FUNCTIONAL')),
    CONSTRAINT ck_mra_status CHECK (status IN ('REQUESTED', 'COMPLETED', 'DECLINED'))
);

CREATE INDEX ix_mra_req_tenant_subj ON mra_request (tenant_id, subject_employee_id);
CREATE INDEX ix_mra_req_tenant_rater ON mra_request (tenant_id, rater_employee_id);
SELECT apply_tenant_rls('mra_request');
COMMENT ON TABLE mra_request IS '360 multi-rater feedback invitations with confidentiality safeguards.';

CREATE TABLE mra_rating
(
    id              uuid         PRIMARY KEY,
    tenant_id       uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    mra_request_id  uuid         NOT NULL REFERENCES mra_request (id) ON DELETE CASCADE,
    competency_id   uuid         NOT NULL REFERENCES competency (id) ON DELETE CASCADE,
    score           numeric(3, 1) NOT NULL,
    comments        text,

    created_at      timestamptz   NOT NULL DEFAULT clock_timestamp(),
    created_by      uuid,
    updated_at      timestamptz   NOT NULL DEFAULT clock_timestamp(),
    updated_by      uuid,
    version         bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_mra_rating UNIQUE (tenant_id, mra_request_id, competency_id),
    CONSTRAINT ck_mra_score CHECK (score BETWEEN 1.0 AND 5.0)
);

CREATE INDEX ix_mra_rating_tenant ON mra_rating (tenant_id, mra_request_id);
SELECT apply_tenant_rls('mra_rating');
COMMENT ON TABLE mra_rating IS 'Anonymous individual ratings submitted by 360 raters.';

-- =============================================================================
-- 5. Seed Fixtures for Acme Corp & Kasun Mendis (LK010)
-- =============================================================================
INSERT INTO goal_cycle (id, tenant_id, code, name, start_date, end_date, status)
VALUES (
    'c0000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'FY2026_GOALS',
    'FY2026 Annual Goals & OKRs',
    '2026-01-01',
    '2026-12-31',
    'ACTIVE'
) ON CONFLICT DO NOTHING;

INSERT INTO competency_group (id, tenant_id, code, name, description)
VALUES
(
    'c9000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'TECHNICAL',
    'Technical Excellence & Craftsmanship',
    'System architecture, engineering rigor, code quality, and technical problem solving'
),
(
    'c9000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    'COLLABORATION',
    'Communication & Collaboration',
    'Cross-functional teamwork, mentorship, clear documentation, and proactive transparency'
),
(
    'c9000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000001',
    'DELIVERY',
    'Ownership & Execution',
    'On-time delivery, SLA adherence, business acumen, and reliability'
) ON CONFLICT DO NOTHING;

INSERT INTO competency (id, tenant_id, group_id, code, name, description, target_level)
VALUES
(
    'cb000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'c9000000-0000-0000-0000-000000000001',
    'SYS_ARCH',
    'System Architecture & Design',
    'Designs resilient, scalable multi-tenant services with clear domain boundaries',
    4
),
(
    'cb000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    'c9000000-0000-0000-0000-000000000001',
    'CODE_QUALITY',
    'Code Quality & Testing',
    'Writes clean, idiomatic Kotlin code with comprehensive unit and contract test coverage',
    4
),
(
    'cb000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000001',
    'c9000000-0000-0000-0000-000000000002',
    'TEAM_MENTOR',
    'Mentorship & Knowledge Sharing',
    'Actively coaches junior developers and contributes to shared architectural RFCs',
    3
),
(
    'cb000000-0000-0000-0000-000000000004',
    '00000000-0000-0000-0000-000000000001',
    'c9000000-0000-0000-0000-000000000003',
    'EXECUTION',
    'Project Execution & Reliability',
    'Delivers milestones on schedule with zero high-severity production regressions',
    4
) ON CONFLICT DO NOTHING;

INSERT INTO evaluation_cycle (
    id, tenant_id, code, name, start_date, end_date,
    goal_weight, competency_weight, mra_weight,
    self_review_deadline, manager_review_deadline, calibration_deadline, status
)
VALUES (
    'ec000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'CYCLE_2026_H1',
    '2026 H1 Mid-Year Performance Review',
    '2026-01-01',
    '2026-06-30',
    60.00,
    30.00,
    10.00,
    '2026-06-15',
    '2026-06-25',
    '2026-06-30',
    'ACTIVE'
) ON CONFLICT DO NOTHING;

-- Seed Goals for Kasun Mendis (e0000000-0000-0000-0000-000000000010)
INSERT INTO goal (
    id, tenant_id, employee_id, cycle_id, title, description,
    category, weight, target_value, current_value, unit,
    start_date, due_date, status, progress_percentage
)
VALUES
(
    'a0000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'e0000000-0000-0000-0000-000000000010',
    'c0000000-0000-0000-0000-000000000001',
    'Deliver High-Throughput Payroll Engine',
    'Implement ANTLR statutory formula calculation with LankaPay and NACHA banking integration',
    'INDIVIDUAL',
    40.00,
    100.00,
    95.00,
    '%',
    '2026-01-10',
    '2026-06-30',
    'ON_TRACK',
    95.00
),
(
    'a0000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    'e0000000-0000-0000-0000-000000000010',
    'c0000000-0000-0000-0000-000000000001',
    'Zero-Defect Code Coverage & Contract Compliance',
    'Maintain >=90% automated unit and contract test coverage across all mobile API client surfaces',
    'DEVELOPMENTAL',
    30.00,
    90.00,
    92.00,
    '%',
    '2026-01-15',
    '2026-06-30',
    'COMPLETED',
    100.00
),
(
    'a0000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000001',
    'e0000000-0000-0000-0000-000000000010',
    'c0000000-0000-0000-0000-000000000001',
    'Mobile Offline-First Outbox Synchronization',
    'Build reliable optimistic mutations and SQLite outbox replay under airplane mode conditions',
    'INDIVIDUAL',
    30.00,
    100.00,
    85.00,
    '%',
    '2026-02-01',
    '2026-06-30',
    'ON_TRACK',
    85.00
) ON CONFLICT DO NOTHING;

-- Seed Performance Appraisal for Kasun Mendis (Manager: Nimal Perera LK002 = e0000000-0000-0000-0000-000000000002)
INSERT INTO performance_appraisal (
    id, tenant_id, cycle_id, employee_id, manager_id, status,
    self_goals_score, manager_goals_score, self_competency_score, manager_competency_score,
    final_score, final_rating, self_overall_comments
)
VALUES (
    '0a000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'ec000000-0000-0000-0000-000000000001',
    'e0000000-0000-0000-0000-000000000010',
    'e0000000-0000-0000-0000-000000000002',
    'SELF_REVIEW_PENDING',
    92.00,
    NULL,
    4.20,
    NULL,
    NULL,
    NULL,
    'Strong progress achieved on payroll computation pipeline and offline-first client SDK architectures.'
) ON CONFLICT DO NOTHING;

-- Seed Continuous Feedback Notes
INSERT INTO continuous_feedback (
    id, tenant_id, sender_employee_id, recipient_employee_id,
    feedback_type, title, content, is_private, shared_with_manager
)
VALUES
(
    'fb000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'e0000000-0000-0000-0000-000000000002',
    'e0000000-0000-0000-0000-000000000010',
    'PRAISE',
    'Outstanding Delivery on Multi-Platform SDKs',
    'Kasun led the Kotlin, TypeScript, and Swift client SDK generation with 100% contract fidelity. Exceptional work!',
    false,
    true
),
(
    'fb000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    'e0000000-0000-0000-0000-000000000002',
    'e0000000-0000-0000-0000-000000000010',
    'ONE_ON_ONE_NOTE',
    'Q1 1-on-1 Touchpoint: Architecture & Goals',
    'Discussed performance calibration for H1, mentoring junior engineers on Kotlin coroutines, and expanding test suites.',
    true,
    true
) ON CONFLICT DO NOTHING;
