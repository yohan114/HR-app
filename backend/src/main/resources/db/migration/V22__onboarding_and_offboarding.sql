-- =============================================================================
-- V22 — Employee Onboarding, Checklists & Offboarding Clearance Workflows
--
-- Phase 4: Module 4.5
--
-- Implements complete lifecycle entry and exit workflows benchmarked against PeoplesHR:
--   1. Role/Department-based Onboarding Profiles & Stages
--   2. Multi-Role Collaborative Task Checklists (New Hire, Buddy, Manager, IT/HR Ops)
--   3. Resignation Notices & Statutory Notice Period Tracking
--   4. Departmental Clearance Matrix (IT, Finance, HR, Admin, Manager) with Dues Recovery
--   5. Exit Interviews & Sentiment Analytics
--
-- Enforces PostgreSQL Row-Level Security (RLS) on all tables with tenant_id.
-- =============================================================================

-- =============================================================================
-- 1. Onboarding Stages & Profiles
-- =============================================================================
CREATE TABLE onboarding_stage
(
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code                varchar(32)   NOT NULL,
    name                varchar(128)  NOT NULL,
    sequence            int           NOT NULL,
    days_offset         int           NOT NULL DEFAULT 0,

    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          uuid,
    version             bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_onboarding_stage_code UNIQUE (tenant_id, code)
);

CREATE INDEX ix_onboarding_stage_tenant ON onboarding_stage (tenant_id, sequence);
SELECT apply_tenant_rls('onboarding_stage');
COMMENT ON TABLE onboarding_stage IS 'Sequential milestones in employee onboarding (Pre-boarding, Day One, Week One, Month One, Day 90).';

CREATE TABLE onboarding_profile
(
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code                varchar(64)   NOT NULL,
    name                varchar(128)  NOT NULL,
    department_id       uuid          REFERENCES department (id) ON DELETE SET NULL,
    description         text,
    active              boolean       NOT NULL DEFAULT true,

    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          uuid,
    version             bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_onboarding_profile_code UNIQUE (tenant_id, code)
);

CREATE INDEX ix_onboarding_profile_tenant ON onboarding_profile (tenant_id, active);
SELECT apply_tenant_rls('onboarding_profile');
COMMENT ON TABLE onboarding_profile IS 'Department or role specific onboarding blueprints containing predefined action checklists.';

CREATE TABLE onboarding_action
(
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    profile_id          uuid          NOT NULL REFERENCES onboarding_profile (id) ON DELETE CASCADE,
    stage_id            uuid          NOT NULL REFERENCES onboarding_stage (id) ON DELETE CASCADE,
    code                varchar(64)   NOT NULL,
    name                varchar(128)  NOT NULL,
    description         text,
    owner_role          varchar(32)   NOT NULL DEFAULT 'NEW_HIRE',
    mandatory           boolean       NOT NULL DEFAULT true,
    due_days_offset     int           NOT NULL DEFAULT 0,

    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          uuid,
    version             bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_onboarding_action_code UNIQUE (tenant_id, profile_id, code),
    CONSTRAINT ck_onboarding_action_owner CHECK (owner_role IN ('NEW_HIRE', 'BUDDY', 'MANAGER', 'IT_OPS', 'HR_OPS'))
);

CREATE INDEX ix_onboarding_action_tenant ON onboarding_action (tenant_id, profile_id, stage_id);
SELECT apply_tenant_rls('onboarding_action');
COMMENT ON TABLE onboarding_action IS 'Configured action items within an onboarding profile track.';

-- =============================================================================
-- 2. Onboarding Instances & Instantiated Tasks
-- =============================================================================
CREATE TABLE onboarding_instance
(
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    candidate_id        uuid          REFERENCES recruitment_candidate (id) ON DELETE SET NULL,
    employee_id         uuid          NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    profile_id          uuid          NOT NULL REFERENCES onboarding_profile (id) ON DELETE CASCADE,
    join_date           date          NOT NULL,
    status              varchar(32)   NOT NULL DEFAULT 'IN_PROGRESS',
    progress_pct        numeric(5, 2) NOT NULL DEFAULT 0.00,
    buddy_employee_id   uuid          REFERENCES employee (id) ON DELETE SET NULL,
    completed_at        timestamptz,

    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          uuid,
    version             bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_onboarding_instance_emp UNIQUE (tenant_id, employee_id),
    CONSTRAINT ck_onboarding_inst_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX ix_onboarding_instance_tenant ON onboarding_instance (tenant_id, status);
CREATE INDEX ix_onboarding_instance_buddy ON onboarding_instance (tenant_id, buddy_employee_id);
SELECT apply_tenant_rls('onboarding_instance');
COMMENT ON TABLE onboarding_instance IS 'Active onboarding workflow instance executing for a newly hired employee.';

CREATE TABLE onboarding_task
(
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    instance_id         uuid          NOT NULL REFERENCES onboarding_instance (id) ON DELETE CASCADE,
    action_id           uuid          REFERENCES onboarding_action (id) ON DELETE SET NULL,
    title               varchar(255)  NOT NULL,
    description         text,
    owner_role          varchar(32)   NOT NULL DEFAULT 'NEW_HIRE',
    assignee_employee_id uuid         REFERENCES employee (id) ON DELETE SET NULL,
    due_date            date          NOT NULL,
    status              varchar(32)   NOT NULL DEFAULT 'PENDING',
    completed_at        timestamptz,
    notes               text,
    attachment_url      varchar(512),

    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          uuid,
    version             bigint        NOT NULL DEFAULT 0,

    CONSTRAINT ck_onboarding_task_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED', 'BLOCKED')),
    CONSTRAINT ck_onboarding_task_owner CHECK (owner_role IN ('NEW_HIRE', 'BUDDY', 'MANAGER', 'IT_OPS', 'HR_OPS'))
);

CREATE INDEX ix_onboarding_task_tenant_inst ON onboarding_task (tenant_id, instance_id, status);
CREATE INDEX ix_onboarding_task_assignee ON onboarding_task (tenant_id, assignee_employee_id, status);
SELECT apply_tenant_rls('onboarding_task');
COMMENT ON TABLE onboarding_task IS 'Individual checklist task assigned to new hire, buddy, manager, or operational team.';

-- =============================================================================
-- 3. Offboarding: Exit Types, Reasons & Notices
-- =============================================================================
CREATE TABLE exit_type
(
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code                varchar(32)   NOT NULL,
    name                varchar(128)  NOT NULL,
    voluntary           boolean       NOT NULL DEFAULT true,
    notice_days         int           NOT NULL DEFAULT 30,
    requires_interview  boolean       NOT NULL DEFAULT true,
    requires_clearance  boolean       NOT NULL DEFAULT true,

    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          uuid,
    version             bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_exit_type_code UNIQUE (tenant_id, code)
);

CREATE INDEX ix_exit_type_tenant ON exit_type (tenant_id, code);
SELECT apply_tenant_rls('exit_type');
COMMENT ON TABLE exit_type IS 'Categories of employee departure (Resignation, Retirement, Mutual Separation, End of Contract, Termination).';

CREATE TABLE exit_reason
(
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    exit_type_id        uuid          NOT NULL REFERENCES exit_type (id) ON DELETE CASCADE,
    code                varchar(64)   NOT NULL,
    name                varchar(128)  NOT NULL,
    category            varchar(64)   NOT NULL DEFAULT 'CAREER',

    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          uuid,
    version             bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_exit_reason_code UNIQUE (tenant_id, code)
);

CREATE INDEX ix_exit_reason_tenant ON exit_reason (tenant_id, exit_type_id);
SELECT apply_tenant_rls('exit_reason');
COMMENT ON TABLE exit_reason IS 'Specific driver or rationale for employee exit.';

CREATE TABLE exit_notice
(
    id                          uuid          PRIMARY KEY,
    tenant_id                   uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    notice_number               varchar(64)   NOT NULL,
    employee_id                 uuid          NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    exit_type_id                uuid          NOT NULL REFERENCES exit_type (id) ON DELETE CASCADE,
    exit_reason_id              uuid          REFERENCES exit_reason (id) ON DELETE SET NULL,
    notice_date                 date          NOT NULL,
    requested_last_working_date date          NOT NULL,
    approved_last_working_date  date,
    remarks                     text,
    status                      varchar(32)   NOT NULL DEFAULT 'SUBMITTED',
    reversal_reason             text,
    approved_by                 uuid          REFERENCES employee (id) ON DELETE SET NULL,
    approved_at                 timestamptz,

    created_at                  timestamptz   NOT NULL DEFAULT now(),
    created_by                  uuid,
    updated_at                  timestamptz   NOT NULL DEFAULT now(),
    updated_by                  uuid,
    version                     bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_exit_notice_num UNIQUE (tenant_id, notice_number),
    CONSTRAINT ck_exit_notice_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'REVERSED'))
);

CREATE INDEX ix_exit_notice_tenant_emp ON exit_notice (tenant_id, employee_id, status);
SELECT apply_tenant_rls('exit_notice');
COMMENT ON TABLE exit_notice IS 'Formal resignation or termination exit notice submitted by employee or HR.';

CREATE TABLE exit_interview
(
    id                          uuid          PRIMARY KEY,
    tenant_id                   uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    exit_notice_id              uuid          NOT NULL REFERENCES exit_notice (id) ON DELETE CASCADE,
    employee_id                 uuid          NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    interviewer_employee_id     uuid          REFERENCES employee (id) ON DELETE SET NULL,
    conducted_at                timestamptz   NOT NULL DEFAULT now(),
    overall_experience_rating   int           NOT NULL DEFAULT 3,
    management_rating           int           NOT NULL DEFAULT 3,
    culture_rating              int           NOT NULL DEFAULT 3,
    reason_details              text,
    suggestions                 text,
    would_recommend             boolean       NOT NULL DEFAULT true,

    created_at                  timestamptz   NOT NULL DEFAULT now(),
    created_by                  uuid,
    updated_at                  timestamptz   NOT NULL DEFAULT now(),
    updated_by                  uuid,
    version                     bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_exit_interview_notice UNIQUE (tenant_id, exit_notice_id),
    CONSTRAINT ck_exit_interview_ratings CHECK (
        overall_experience_rating BETWEEN 1 AND 5 AND
        management_rating BETWEEN 1 AND 5 AND
        culture_rating BETWEEN 1 AND 5
    )
);

CREATE INDEX ix_exit_interview_tenant ON exit_interview (tenant_id, employee_id);
SELECT apply_tenant_rls('exit_interview');
COMMENT ON TABLE exit_interview IS 'Structured feedback and sentiment captured during exit interview.';

-- =============================================================================
-- 4. Cross-Departmental Clearance Matrix
-- =============================================================================
CREATE TABLE clearance_item
(
    id                          uuid          PRIMARY KEY,
    tenant_id                   uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    department                  varchar(32)   NOT NULL,
    code                        varchar(64)   NOT NULL,
    name                        varchar(128)  NOT NULL,
    description                 text,
    default_assignee_role       varchar(32)   NOT NULL DEFAULT 'DEPARTMENT_LEAD',

    created_at                  timestamptz   NOT NULL DEFAULT now(),
    created_by                  uuid,
    updated_at                  timestamptz   NOT NULL DEFAULT now(),
    updated_by                  uuid,
    version                     bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_clearance_item_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_clearance_item_dept CHECK (department IN ('IT_INFRASTRUCTURE', 'FINANCE_PAYROLL', 'HR_OPERATIONS', 'ADMIN_FACILITIES', 'LINE_MANAGER'))
);

CREATE INDEX ix_clearance_item_tenant ON clearance_item (tenant_id, department);
SELECT apply_tenant_rls('clearance_item');
COMMENT ON TABLE clearance_item IS 'Catalog of departmental clearance checklist items for departing employees.';

CREATE TABLE clearance_task
(
    id                          uuid          PRIMARY KEY,
    tenant_id                   uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    exit_notice_id              uuid          NOT NULL REFERENCES exit_notice (id) ON DELETE CASCADE,
    employee_id                 uuid          NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    item_id                     uuid          REFERENCES clearance_item (id) ON DELETE SET NULL,
    department                  varchar(32)   NOT NULL,
    title                       varchar(255)  NOT NULL,
    assignee_employee_id        uuid          REFERENCES employee (id) ON DELETE SET NULL,
    status                      varchar(32)   NOT NULL DEFAULT 'PENDING',
    cleared_at                  timestamptz,
    remarks                     text,
    recoverable_amount          numeric(12, 2) NOT NULL DEFAULT 0.00,

    created_at                  timestamptz   NOT NULL DEFAULT now(),
    created_by                  uuid,
    updated_at                  timestamptz   NOT NULL DEFAULT now(),
    updated_by                  uuid,
    version                     bigint        NOT NULL DEFAULT 0,

    CONSTRAINT ck_clearance_task_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'CLEARED', 'WAIVED', 'REJECTED')),
    CONSTRAINT ck_clearance_task_dept CHECK (department IN ('IT_INFRASTRUCTURE', 'FINANCE_PAYROLL', 'HR_OPERATIONS', 'ADMIN_FACILITIES', 'LINE_MANAGER'))
);

CREATE INDEX ix_clearance_task_tenant_notice ON clearance_task (tenant_id, exit_notice_id, department, status);
CREATE INDEX ix_clearance_task_assignee ON clearance_task (tenant_id, assignee_employee_id, status);
SELECT apply_tenant_rls('clearance_task');
COMMENT ON TABLE clearance_task IS 'Concrete departmental clearance checklist task with dues and recovery tracking.';

-- =============================================================================
-- 5. Demonstration Fixtures & Seed Data
-- =============================================================================
DO $$
DECLARE
    v_tenant_id uuid;
    v_kasun_id  uuid;
    v_dept_id   uuid;
    v_stage_pre uuid := gen_random_uuid();
    v_stage_d1  uuid := gen_random_uuid();
    v_stage_w1  uuid := gen_random_uuid();
    v_stage_m1  uuid := gen_random_uuid();
    v_profile_id uuid := gen_random_uuid();
    v_instance_id uuid := gen_random_uuid();
    v_exit_type_res uuid := gen_random_uuid();
    v_exit_reason_car uuid := gen_random_uuid();
    v_notice_id uuid := gen_random_uuid();
    v_it_item uuid := gen_random_uuid();
    v_fin_item uuid := gen_random_uuid();
    v_hr_item uuid := gen_random_uuid();
    v_adm_item uuid := gen_random_uuid();
    v_mgr_item uuid := gen_random_uuid();
BEGIN
    SELECT id INTO v_tenant_id FROM tenant WHERE id = '00000000-0000-0000-0000-000000000001' OR id = '11111111-1111-1111-1111-111111111111' LIMIT 1;
    IF v_tenant_id IS NOT NULL THEN
        SELECT id INTO v_kasun_id FROM employee WHERE tenant_id = v_tenant_id AND (employee_code = 'LK010' OR employee_code = 'E010' OR id = 'e0000000-0000-0000-0000-000000000010') LIMIT 1;
        IF v_kasun_id IS NULL THEN
            SELECT id INTO v_kasun_id FROM employee WHERE tenant_id = v_tenant_id LIMIT 1;
        END IF;

        SELECT id INTO v_dept_id FROM department WHERE tenant_id = v_tenant_id LIMIT 1;

        -- 1. Onboarding Stages
        INSERT INTO onboarding_stage (id, tenant_id, code, name, sequence, days_offset)
        VALUES
            (v_stage_pre, v_tenant_id, 'PRE_BOARDING', 'Pre-boarding Preparation', 1, -7),
            (v_stage_d1, v_tenant_id, 'DAY_ONE', 'Day One Welcome & Orientation', 2, 0),
            (v_stage_w1, v_tenant_id, 'WEEK_ONE', 'Week One Integration', 3, 7),
            (v_stage_m1, v_tenant_id, 'MONTH_ONE', 'Month One Alignment & Check-in', 4, 30)
        ON CONFLICT (tenant_id, code) DO NOTHING;

        -- 2. Onboarding Profile
        INSERT INTO onboarding_profile (id, tenant_id, code, name, department_id, description, active)
        VALUES
            (v_profile_id, v_tenant_id, 'ENG_STANDARD', 'Engineering Standard Onboarding Track', v_dept_id, 'Default engineering onboarding track covering hardware provisioning, codebase setup, security policies, and buddy assignment.', true)
        ON CONFLICT (tenant_id, code) DO NOTHING;

        -- 3. Onboarding Actions
        INSERT INTO onboarding_action (id, tenant_id, profile_id, stage_id, code, name, description, owner_role, mandatory, due_days_offset)
        VALUES
            (gen_random_uuid(), v_tenant_id, v_profile_id, v_stage_pre, 'PROVISION_LAPTOP', 'Provision Development Laptop & Peripherals', 'Prepare MacBook Pro or ThinkPad with pre-imaged developer tools.', 'IT_OPS', true, -2),
            (gen_random_uuid(), v_tenant_id, v_profile_id, v_stage_pre, 'SUBMIT_DOCS', 'Upload National Identity & Bank Account Details', 'Complete digital compliance documentation and emergency contacts.', 'NEW_HIRE', true, -1),
            (gen_random_uuid(), v_tenant_id, v_profile_id, v_stage_d1, 'ISSUE_SECURITY_BADGE', 'Issue Building Access Card & Security Badge', 'Issue RFID proximity card for office entry.', 'HR_OPS', true, 0),
            (gen_random_uuid(), v_tenant_id, v_profile_id, v_stage_d1, 'BUDDY_WELCOME_LUNCH', 'Buddy Welcome Coffee & Team Introduction', 'Introduce new hire to team members and office facilities.', 'BUDDY', false, 0),
            (gen_random_uuid(), v_tenant_id, v_profile_id, v_stage_w1, 'CODEBASE_WALKTHROUGH', 'Architecture Overview & Local Environment Setup', 'Guide through architecture docs, clone repos, run first build.', 'BUDDY', true, 3),
            (gen_random_uuid(), v_tenant_id, v_profile_id, v_stage_w1, 'POLICY_ACKNOWLEDGEMENT', 'Information Security & Code of Conduct Sign-off', 'Review and digitally sign employee handbook policies.', 'NEW_HIRE', true, 5),
            (gen_random_uuid(), v_tenant_id, v_profile_id, v_stage_m1, 'MANAGER_30_DAY_REVIEW', '30-Day Expectations & OKR Alignment Review', 'Manager 1-on-1 check-in to establish Q1/Q2 goals and address feedback.', 'MANAGER', true, 30)
        ON CONFLICT DO NOTHING;

        -- 4. If Kasun exists, create an active/recent onboarding instance & tasks
        IF v_kasun_id IS NOT NULL THEN
            INSERT INTO onboarding_instance (id, tenant_id, candidate_id, employee_id, profile_id, join_date, status, progress_pct, buddy_employee_id)
            VALUES (v_instance_id, v_tenant_id, NULL, v_kasun_id, v_profile_id, CURRENT_DATE - INTERVAL '10 days', 'IN_PROGRESS', 57.14, v_kasun_id)
            ON CONFLICT (tenant_id, employee_id) DO NOTHING;

            INSERT INTO onboarding_task (id, tenant_id, instance_id, action_id, title, description, owner_role, assignee_employee_id, due_date, status, completed_at, notes)
            VALUES
                (gen_random_uuid(), v_tenant_id, v_instance_id, NULL, 'Provision Development Laptop & Peripherals', 'MacBook Pro 16-inch M3 Max configured and shipped.', 'IT_OPS', v_kasun_id, CURRENT_DATE - 12, 'COMPLETED', now() - INTERVAL '11 days', 'Device serial: C02G12345ABC'),
                (gen_random_uuid(), v_tenant_id, v_instance_id, NULL, 'Upload National Identity & Bank Account Details', 'NIC copy and Commercial Bank account verified.', 'NEW_HIRE', v_kasun_id, CURRENT_DATE - 11, 'COMPLETED', now() - INTERVAL '10 days', 'Verified by HR'),
                (gen_random_uuid(), v_tenant_id, v_instance_id, NULL, 'Issue Building Access Card & Security Badge', 'Access badge #8841 activated for Colombo HQ.', 'HR_OPS', v_kasun_id, CURRENT_DATE - 10, 'COMPLETED', now() - INTERVAL '10 days', 'Card ID: 8841'),
                (gen_random_uuid(), v_tenant_id, v_instance_id, NULL, 'Buddy Welcome Coffee & Team Introduction', 'Meet team in Colombo cafeteria.', 'BUDDY', v_kasun_id, CURRENT_DATE - 10, 'COMPLETED', now() - INTERVAL '9 days', 'Team lunch completed'),
                (gen_random_uuid(), v_tenant_id, v_instance_id, NULL, 'Architecture Overview & Local Environment Setup', 'Clone backend and android repos, setup local docker postgres.', 'BUDDY', v_kasun_id, CURRENT_DATE - 7, 'PENDING', NULL, NULL),
                (gen_random_uuid(), v_tenant_id, v_instance_id, NULL, 'Information Security & Code of Conduct Sign-off', 'Read compliance handbook and digitally acknowledge.', 'NEW_HIRE', v_kasun_id, CURRENT_DATE - 5, 'PENDING', NULL, NULL),
                (gen_random_uuid(), v_tenant_id, v_instance_id, NULL, '30-Day Expectations & OKR Alignment Review', 'First monthly milestone alignment with engineering director.', 'MANAGER', v_kasun_id, CURRENT_DATE + 20, 'PENDING', NULL, NULL)
            ON CONFLICT DO NOTHING;
        END IF;

        -- 5. Offboarding: Exit Types & Reasons
        INSERT INTO exit_type (id, tenant_id, code, name, voluntary, notice_days, requires_interview, requires_clearance)
        VALUES
            (v_exit_type_res, v_tenant_id, 'RESIGNATION', 'Voluntary Resignation', true, 30, true, true),
            (gen_random_uuid(), v_tenant_id, 'RETIREMENT', 'Superannuation / Retirement', true, 60, true, true),
            (gen_random_uuid(), v_tenant_id, 'MUTUAL_SEPARATION', 'Mutual Separation Agreement', false, 14, true, true),
            (gen_random_uuid(), v_tenant_id, 'CONTRACT_EXPIRY', 'End of Fixed-Term Contract', false, 30, false, true)
        ON CONFLICT (tenant_id, code) DO NOTHING;

        INSERT INTO exit_reason (id, tenant_id, exit_type_id, code, name, category)
        VALUES
            (v_exit_reason_car, v_tenant_id, v_exit_type_res, 'CAREER_GROWTH', 'External Career Progression / New Opportunity', 'CAREER'),
            (gen_random_uuid(), v_tenant_id, v_exit_type_res, 'COMPENSATION', 'Competitive Compensation & Rewards', 'COMPENSATION'),
            (gen_random_uuid(), v_tenant_id, v_exit_type_res, 'RELOCATION', 'Geographic Relocation / Family Reasons', 'PERSONAL'),
            (gen_random_uuid(), v_tenant_id, v_exit_type_res, 'HIGHER_STUDIES', 'Pursuing Full-time Higher Studies', 'EDUCATION')
        ON CONFLICT (tenant_id, code) DO NOTHING;

        -- 6. Clearance Catalog Items
        INSERT INTO clearance_item (id, tenant_id, department, code, name, default_assignee_role)
        VALUES
            (v_it_item, v_tenant_id, 'IT_INFRASTRUCTURE', 'IT_ASSET_RETURN', 'Company Laptop, Monitors & Access Tokens Return', 'IT_ASSET_MANAGER'),
            (v_fin_item, v_tenant_id, 'FINANCE_PAYROLL', 'FIN_LOAN_CLEARANCE', 'Outstanding Company Loans & Travel Advances Audit', 'FINANCE_OFFICER'),
            (v_hr_item, v_tenant_id, 'HR_OPERATIONS', 'HR_INSURANCE_RECOVERY', 'Medical Insurance Card Surrender & Statutory Exit Docs', 'HR_SPECIALIST'),
            (v_adm_item, v_tenant_id, 'ADMIN_FACILITIES', 'ADM_ACCESS_KEY_RETURN', 'Building Access Proximity Card & Locker Keys Return', 'FACILITIES_MANAGER'),
            (v_mgr_item, v_tenant_id, 'LINE_MANAGER', 'MGR_KNOWLEDGE_HANDOVER', 'Project Documentation, Code Review Handoff & Credentials', 'REPORTING_MANAGER')
        ON CONFLICT (tenant_id, code) DO NOTHING;

    END IF;
END $$;
