-- =============================================================================
-- V21 — Recruitment, Applicant Tracking System (ATS), Interview Evaluation & Pipeline (Phase 4: Module 4.4)
--
-- Adds:
--   - recruitment_requisition: Manpower requisition and vacancy approval workflow
--   - recruitment_vacancy: Job openings and postings across departments and locations
--   - recruitment_candidate: Applicant profile pool and candidate master
--   - recruitment_application: Multi-stage candidate application tracking pipeline
--   - recruitment_interview: Scheduled rounds, panel members, and video/meeting details
--   - recruitment_interview_panel: Assigned interviewers and panel leads
--   - recruitment_interview_scorecard: Standardized interview scoring and hire recommendations
--   - recruitment_offer: Formal employment offers, compensation terms, and status tracking
--
-- Enforces PostgreSQL Row-Level Security (RLS) on all tables and tenant_id
-- leading composite indexes.
-- =============================================================================

-- =============================================================================
-- 1. Requisitions & Job Vacancies
-- =============================================================================
CREATE TABLE recruitment_requisition
(
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    requisition_number  varchar(64)   NOT NULL,
    title               varchar(255)  NOT NULL,
    department_id       uuid          REFERENCES department (id) ON DELETE SET NULL,
    requested_by        uuid          REFERENCES employee (id) ON DELETE SET NULL,
    headcount           integer       NOT NULL DEFAULT 1,
    employment_type     varchar(32)   NOT NULL DEFAULT 'FULL_TIME',
    min_salary          numeric(12, 2),
    max_salary          numeric(12, 2),
    currency            varchar(3)    NOT NULL DEFAULT 'LKR',
    justification       text,
    status              varchar(32)   NOT NULL DEFAULT 'DRAFT',
    approved_by         uuid          REFERENCES employee (id) ON DELETE SET NULL,
    approved_at         timestamptz,

    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          uuid,
    version             bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_recruitment_req_num UNIQUE (tenant_id, requisition_number),
    CONSTRAINT ck_recruitment_req_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_recruitment_req_emp_type CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERN', 'TEMPORARY'))
);

CREATE INDEX ix_recruitment_req_tenant ON recruitment_requisition (tenant_id, status);
SELECT apply_tenant_rls('recruitment_requisition');
COMMENT ON TABLE recruitment_requisition IS 'Headcount and manpower requisitions approved prior to job opening creation.';

CREATE TABLE recruitment_vacancy
(
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    requisition_id      uuid          REFERENCES recruitment_requisition (id) ON DELETE SET NULL,
    job_code            varchar(64)   NOT NULL,
    title               varchar(255)  NOT NULL,
    department_id       uuid          REFERENCES department (id) ON DELETE SET NULL,
    location            varchar(128)  NOT NULL DEFAULT 'Colombo, Sri Lanka',
    employment_type     varchar(32)   NOT NULL DEFAULT 'FULL_TIME',
    experience_level    varchar(32)   NOT NULL DEFAULT 'MID_LEVEL',
    min_salary          numeric(12, 2),
    max_salary          numeric(12, 2),
    currency            varchar(3)    NOT NULL DEFAULT 'LKR',
    description         text          NOT NULL,
    requirements        text,
    open_positions      integer       NOT NULL DEFAULT 1,
    filled_positions    integer       NOT NULL DEFAULT 0,
    target_hire_date    date,
    closing_date        date,
    status              varchar(32)   NOT NULL DEFAULT 'OPEN',

    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          uuid,
    version             bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_recruitment_vacancy_code UNIQUE (tenant_id, job_code),
    CONSTRAINT ck_recruitment_vacancy_status CHECK (status IN ('DRAFT', 'OPEN', 'ON_HOLD', 'CLOSED', 'FILLED')),
    CONSTRAINT ck_recruitment_vacancy_exp CHECK (experience_level IN ('ENTRY_LEVEL', 'MID_LEVEL', 'SENIOR_LEVEL', 'LEAD', 'EXECUTIVE'))
);

CREATE INDEX ix_recruitment_vacancy_tenant ON recruitment_vacancy (tenant_id, status);
CREATE INDEX ix_recruitment_vacancy_dept ON recruitment_vacancy (tenant_id, department_id);
SELECT apply_tenant_rls('recruitment_vacancy');
COMMENT ON TABLE recruitment_vacancy IS 'Job vacancy postings with candidate requirements and salary budgets.';

-- =============================================================================
-- 2. Candidates & Applications
-- =============================================================================
CREATE TABLE recruitment_candidate
(
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    first_name          varchar(128)  NOT NULL,
    last_name           varchar(128)  NOT NULL,
    email               varchar(255)  NOT NULL,
    phone               varchar(64),
    current_company     varchar(128),
    current_title       varchar(128),
    years_of_experience numeric(4, 1),
    resume_url          varchar(512),
    portfolio_url       varchar(512),
    linkedin_url        varchar(512),
    skills              text[],
    notes               text,

    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          uuid,
    version             bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_recruitment_cand_email UNIQUE (tenant_id, email)
);

CREATE INDEX ix_recruitment_cand_tenant ON recruitment_candidate (tenant_id, email);
SELECT apply_tenant_rls('recruitment_candidate');
COMMENT ON TABLE recruitment_candidate IS 'Master candidate profiles and talent pool across all vacancies.';

CREATE TABLE recruitment_application
(
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    application_number  varchar(64)   NOT NULL,
    vacancy_id          uuid          NOT NULL REFERENCES recruitment_vacancy (id) ON DELETE CASCADE,
    candidate_id        uuid          NOT NULL REFERENCES recruitment_candidate (id) ON DELETE CASCADE,
    stage               varchar(32)   NOT NULL DEFAULT 'APPLIED',
    status              varchar(32)   NOT NULL DEFAULT 'ACTIVE',
    rating              numeric(3, 2),
    source              varchar(64)   NOT NULL DEFAULT 'CAREERS_PORTAL',
    applied_date        date          NOT NULL DEFAULT CURRENT_DATE,
    rejection_reason    text,

    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          uuid,
    version             bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_recruitment_app_num UNIQUE (tenant_id, application_number),
    CONSTRAINT uq_recruitment_app_cand_vac UNIQUE (tenant_id, vacancy_id, candidate_id),
    CONSTRAINT ck_recruitment_app_stage CHECK (stage IN (
        'APPLIED', 'SCREENING', 'INTERVIEW_ROUND_1', 'INTERVIEW_ROUND_2',
        'TECHNICAL_ASSESSMENT', 'FINAL_INTERVIEW', 'OFFER_EXTENDED',
        'OFFER_ACCEPTED', 'OFFER_DECLINED', 'REJECTED', 'WITHDRAWN'
    )),
    CONSTRAINT ck_recruitment_app_status CHECK (status IN ('ACTIVE', 'ARCHIVED', 'HIRED', 'REJECTED'))
);

CREATE INDEX ix_recruitment_app_tenant_vac ON recruitment_application (tenant_id, vacancy_id, stage);
CREATE INDEX ix_recruitment_app_tenant_cand ON recruitment_application (tenant_id, candidate_id);
SELECT apply_tenant_rls('recruitment_application');
COMMENT ON TABLE recruitment_application IS 'Candidate applications tracking progression through recruitment pipeline stages.';

-- =============================================================================
-- 3. Interviews, Panel Members & Scorecards
-- =============================================================================
CREATE TABLE recruitment_interview
(
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    application_id      uuid          NOT NULL REFERENCES recruitment_application (id) ON DELETE CASCADE,
    interview_round     integer       NOT NULL DEFAULT 1,
    title               varchar(255)  NOT NULL,
    interview_type      varchar(32)   NOT NULL DEFAULT 'TECHNICAL',
    scheduled_start     timestamptz   NOT NULL,
    scheduled_end       timestamptz   NOT NULL,
    location_or_link    varchar(255),
    meeting_link        varchar(512),
    status              varchar(32)   NOT NULL DEFAULT 'SCHEDULED',
    notes               text,

    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          uuid,
    version             bigint        NOT NULL DEFAULT 0,

    CONSTRAINT ck_recruitment_interview_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED', 'RESCHEDULED')),
    CONSTRAINT ck_recruitment_interview_type CHECK (interview_type IN ('PHONE_SCREEN', 'TECHNICAL', 'SYSTEM_DESIGN', 'BEHAVIORAL', 'HR', 'EXECUTIVE'))
);

CREATE INDEX ix_recruitment_interview_tenant_app ON recruitment_interview (tenant_id, application_id);
CREATE INDEX ix_recruitment_interview_schedule ON recruitment_interview (tenant_id, scheduled_start);
SELECT apply_tenant_rls('recruitment_interview');
COMMENT ON TABLE recruitment_interview IS 'Scheduled interview sessions with applicants and evaluation details.';

CREATE TABLE recruitment_interview_panel
(
    id                      uuid         PRIMARY KEY,
    tenant_id               uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    interview_id            uuid         NOT NULL REFERENCES recruitment_interview (id) ON DELETE CASCADE,
    interviewer_employee_id uuid         NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    is_lead                 boolean      NOT NULL DEFAULT false,

    created_at              timestamptz  NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_at              timestamptz  NOT NULL DEFAULT now(),
    updated_by              uuid,
    version                 bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uq_recruitment_panel_member UNIQUE (tenant_id, interview_id, interviewer_employee_id)
);

CREATE INDEX ix_recruitment_panel_tenant_emp ON recruitment_interview_panel (tenant_id, interviewer_employee_id);
SELECT apply_tenant_rls('recruitment_interview_panel');
COMMENT ON TABLE recruitment_interview_panel IS 'Panel members and interviewers assigned to evaluate candidate sessions.';

CREATE TABLE recruitment_interview_scorecard
(
    id                      uuid          PRIMARY KEY,
    tenant_id               uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    interview_id            uuid          NOT NULL REFERENCES recruitment_interview (id) ON DELETE CASCADE,
    interviewer_employee_id uuid          NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    overall_recommendation  varchar(32)   NOT NULL,
    technical_skill_rating  numeric(3, 2),
    communication_rating    numeric(3, 2),
    problem_solving_rating  numeric(3, 2),
    cultural_fit_rating     numeric(3, 2),
    strengths               text,
    weaknesses              text,
    summary_notes           text,
    submitted_at            timestamptz   NOT NULL DEFAULT now(),

    created_at              timestamptz   NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_at              timestamptz   NOT NULL DEFAULT now(),
    updated_by              uuid,
    version                 bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_recruitment_scorecard_eval UNIQUE (tenant_id, interview_id, interviewer_employee_id),
    CONSTRAINT ck_recruitment_scorecard_rec CHECK (overall_recommendation IN ('STRONG_HIRE', 'HIRE', 'NEUTRAL', 'NO_HIRE', 'STRONG_NO_HIRE'))
);

CREATE INDEX ix_recruitment_scorecard_tenant_int ON recruitment_interview_scorecard (tenant_id, interview_id);
SELECT apply_tenant_rls('recruitment_interview_scorecard');
COMMENT ON TABLE recruitment_interview_scorecard IS 'Evaluation ratings, recommendation, and competency scorecards completed by interviewers.';

-- =============================================================================
-- 4. Job Offers & Approvals
-- =============================================================================
CREATE TABLE recruitment_offer
(
    id                  uuid          PRIMARY KEY,
    tenant_id           uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    application_id      uuid          NOT NULL REFERENCES recruitment_application (id) ON DELETE CASCADE,
    offer_number        varchar(64)   NOT NULL,
    base_salary         numeric(12, 2) NOT NULL,
    variable_bonus      numeric(12, 2) DEFAULT 0.00,
    currency            varchar(3)    NOT NULL DEFAULT 'LKR',
    start_date          date          NOT NULL,
    expiry_date         date          NOT NULL,
    offer_letter_url    varchar(512),
    status              varchar(32)   NOT NULL DEFAULT 'DRAFT',
    approved_by         uuid          REFERENCES employee (id) ON DELETE SET NULL,
    approved_at         timestamptz,
    decision_notes      text,

    created_at          timestamptz   NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz   NOT NULL DEFAULT now(),
    updated_by          uuid,
    version             bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_recruitment_offer_num UNIQUE (tenant_id, offer_number),
    CONSTRAINT uq_recruitment_offer_app UNIQUE (tenant_id, application_id),
    CONSTRAINT ck_recruitment_offer_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'EXTENDED', 'ACCEPTED', 'DECLINED', 'WITHDRAWN'))
);

CREATE INDEX ix_recruitment_offer_tenant ON recruitment_offer (tenant_id, status);
SELECT apply_tenant_rls('recruitment_offer');
COMMENT ON TABLE recruitment_offer IS 'Formal employment offer letters, compensation packages, and candidate decisions.';

-- =============================================================================
-- 5. Demonstration Fixtures & Seed Data
-- =============================================================================
DO $$
DECLARE
    v_tenant_id uuid;
    v_kasun_id  uuid;
    v_dept_id   uuid;
BEGIN
    -- Resolve tenant if present
    SELECT id INTO v_tenant_id FROM tenant WHERE id = '00000000-0000-0000-0000-000000000001' OR id = '11111111-1111-1111-1111-111111111111' LIMIT 1;
    IF v_tenant_id IS NOT NULL THEN
        -- Resolve Kasun Mendis (LK010) or first employee
        SELECT id INTO v_kasun_id FROM employee WHERE tenant_id = v_tenant_id AND (employee_code = 'LK010' OR employee_code = 'E010' OR id = 'e0000000-0000-0000-0000-000000000010') LIMIT 1;
        IF v_kasun_id IS NULL THEN
            SELECT id INTO v_kasun_id FROM employee WHERE tenant_id = v_tenant_id LIMIT 1;
        END IF;

        -- Resolve Department
        SELECT id INTO v_dept_id FROM department WHERE tenant_id = v_tenant_id LIMIT 1;

        -- 1. Vacancies
        INSERT INTO recruitment_vacancy (
            id, tenant_id, job_code, title, department_id, location,
            employment_type, experience_level, min_salary, max_salary, currency,
            description, requirements, open_positions, filled_positions, status
        ) VALUES
        (
            'fa000000-0000-0000-0000-000000000001',
            v_tenant_id,
            'VAC-2026-001',
            'Senior Backend Engineer (Kotlin & Distributed Systems)',
            v_dept_id,
            'Colombo, Sri Lanka',
            'FULL_TIME',
            'SENIOR_LEVEL',
            350000.00,
            480000.00,
            'LKR',
            'Lead the engineering of mission-critical payroll calculation engines, ANTLR DSLs, and Postgres multi-tenant row-level security architectures.',
            '5+ years experience in JVM/Kotlin, Spring Boot, Postgres RLS, asynchronous event-driven architectures, and high-precision financial computing.',
            2,
            0,
            'OPEN'
        ),
        (
            'fa000000-0000-0000-0000-000000000002',
            v_tenant_id,
            'VAC-2026-002',
            'Mobile Engineering Lead (Android & Jetpack Compose)',
            v_dept_id,
            'Colombo, Sri Lanka',
            'FULL_TIME',
            'LEAD',
            420000.00,
            550000.00,
            'LKR',
            'Direct mobile platform architecture across offline-first SQLite outbox synchronization, deep linking, and enterprise design system components.',
            '6+ years mobile development with 3+ years in Kotlin Multiplatform/Jetpack Compose, Room/SQLite sync, and biometric authentication.',
            1,
            0,
            'OPEN'
        ) ON CONFLICT (tenant_id, job_code) DO NOTHING;

        -- 2. Candidates
        INSERT INTO recruitment_candidate (
            id, tenant_id, first_name, last_name, email, phone,
            current_company, current_title, years_of_experience,
            resume_url, linkedin_url, skills
        ) VALUES
        (
            'ca000000-0000-0000-0000-000000000001',
            v_tenant_id,
            'Nuwan',
            'Senaratne',
            'nuwan.senaratne@example.com',
            '+94771234567',
            'Virtusa Global',
            'Senior Software Engineer',
            7.5,
            'https://storage.hrapp.io/resumes/nuwan-senaratne.pdf',
            'https://linkedin.com/in/nuwan-senaratne-demo',
            ARRAY['Kotlin', 'Spring Boot', 'PostgreSQL', 'Docker', 'Kafka']
        ),
        (
            'ca000000-0000-0000-0000-000000000002',
            v_tenant_id,
            'Dulani',
            'Jayawardena',
            'dulani.j@example.com',
            '+94719876543',
            'WSO2 Lanka',
            'Associate Technical Lead - Mobile',
            6.0,
            'https://storage.hrapp.io/resumes/dulani-j.pdf',
            'https://linkedin.com/in/dulani-jayawardena-demo',
            ARRAY['Android', 'Jetpack Compose', 'Kotlin Coroutines', 'Offline Sync', 'Clean Architecture']
        ),
        (
            'ca000000-0000-0000-0000-000000000003',
            v_tenant_id,
            'Kaveen',
            'Alwis',
            'kaveen.alwis@example.com',
            '+94765551234',
            'Sysco LABS Sri Lanka',
            'Software Engineer',
            4.0,
            'https://storage.hrapp.io/resumes/kaveen-alwis.pdf',
            'https://linkedin.com/in/kaveen-alwis-demo',
            ARRAY['Java', 'Kotlin', 'REST APIs', 'Spring Security']
        ) ON CONFLICT (tenant_id, email) DO NOTHING;

        -- 3. Applications
        INSERT INTO recruitment_application (
            id, tenant_id, application_number, vacancy_id, candidate_id,
            stage, status, rating, source, applied_date
        ) VALUES
        (
            'ab000000-0000-0000-0000-000000000001',
            v_tenant_id,
            'APP-2026-001',
            'fa000000-0000-0000-0000-000000000001',
            'ca000000-0000-0000-0000-000000000001',
            'OFFER_EXTENDED',
            'ACTIVE',
            4.70,
            'CAREERS_PORTAL',
            '2026-02-15'
        ),
        (
            'ab000000-0000-0000-0000-000000000002',
            v_tenant_id,
            'APP-2026-002',
            'fa000000-0000-0000-0000-000000000002',
            'ca000000-0000-0000-0000-000000000002',
            'INTERVIEW_ROUND_2',
            'ACTIVE',
            4.85,
            'LINKEDIN',
            '2026-02-20'
        ),
        (
            'ab000000-0000-0000-0000-000000000003',
            v_tenant_id,
            'APP-2026-003',
            'fa000000-0000-0000-0000-000000000001',
            'ca000000-0000-0000-0000-000000000003',
            'SCREENING',
            'ACTIVE',
            3.80,
            'REFERRAL',
            '2026-03-01'
        ) ON CONFLICT (tenant_id, application_number) DO NOTHING;

        -- 4. Scheduled Interview
        INSERT INTO recruitment_interview (
            id, tenant_id, application_id, interview_round, title,
            interview_type, scheduled_start, scheduled_end, location_or_link, meeting_link, status, notes
        ) VALUES
        (
            '1a000000-0000-0000-0000-000000000001',
            v_tenant_id,
            'ab000000-0000-0000-0000-000000000002',
            2,
            'Mobile Architecture & Offline-First State Sync Deep Dive',
            'TECHNICAL',
            '2026-03-12 10:00:00+05:30',
            '2026-03-12 11:30:00+05:30',
            'Conference Room Alpha / Google Meet',
            'https://meet.google.com/abc-ats-tech',
            'SCHEDULED',
            'Technical evaluation focusing on Jetpack Compose recomposition hygiene, Room SQLite migrations, and cryptographic key storage.'
        ) ON CONFLICT DO NOTHING;

        -- 5. Panel member & Scorecard if employee exists
        IF v_kasun_id IS NOT NULL THEN
            INSERT INTO recruitment_interview_panel (
                id, tenant_id, interview_id, interviewer_employee_id, is_lead
            ) VALUES (
                '1b000000-0000-0000-0000-000000000001',
                v_tenant_id,
                '1a000000-0000-0000-0000-000000000001',
                v_kasun_id,
                true
            ) ON CONFLICT (tenant_id, interview_id, interviewer_employee_id) DO NOTHING;

            INSERT INTO recruitment_interview_scorecard (
                id, tenant_id, interview_id, interviewer_employee_id,
                overall_recommendation, technical_skill_rating, communication_rating,
                problem_solving_rating, cultural_fit_rating, strengths, weaknesses, summary_notes
            ) VALUES (
                '1c000000-0000-0000-0000-000000000001',
                v_tenant_id,
                '1a000000-0000-0000-0000-000000000001',
                v_kasun_id,
                'STRONG_HIRE',
                4.80,
                4.60,
                4.90,
                4.75,
                'Exceptional understanding of Kotlin coroutines concurrency dispatchers, Jetpack Compose stability metrics, and local database caching.',
                'None of concern; would benefit from quick ramp-up on proprietary bank advice file formats.',
                'Candidate demonstrated exemplary mobile system design capabilities. Enthusiastically recommend hire for Lead Mobile role.'
            ) ON CONFLICT (tenant_id, interview_id, interviewer_employee_id) DO NOTHING;
        END IF;

        -- 6. Offer
        INSERT INTO recruitment_offer (
            id, tenant_id, application_id, offer_number, base_salary,
            variable_bonus, currency, start_date, expiry_date,
            offer_letter_url, status, decision_notes
        ) VALUES (
            '0f000000-0000-0000-0000-000000000001',
            v_tenant_id,
            'ab000000-0000-0000-0000-000000000001',
            'OFF-2026-001',
            450000.00,
            50000.00,
            'LKR',
            '2026-04-01',
            '2026-03-25',
            'https://storage.hrapp.io/offers/off-2026-001-nuwan.pdf',
            'EXTENDED',
            'Competitive offer extended meeting top tier market compensation for senior distributed systems engineer.'
        ) ON CONFLICT (tenant_id, offer_number) DO NOTHING;

    END IF;
END
$$;
