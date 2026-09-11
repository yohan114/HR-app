-- ============================================================================
-- V24: Enterprise Learning Management, Training & Development
-- Module 4.7 - Training Catalogue, Schedules, Enrollments, Attendance & Certificates
-- ============================================================================

-- 1. Training Provider (Internal Academy / External Institutions)
CREATE TABLE training_provider (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    provider_name     varchar(150) NOT NULL,
    provider_type     varchar(50) NOT NULL DEFAULT 'INTERNAL', -- 'INTERNAL', 'EXTERNAL', 'ACADEMIC', 'ONLINE_PLATFORM'
    contact_email     varchar(100),
    contact_phone     varchar(50),
    website_url       varchar(255),
    is_accredited     boolean NOT NULL DEFAULT true,
    created_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by        uuid,
    updated_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by        uuid,
    version           bigint NOT NULL DEFAULT 0,
    CONSTRAINT training_provider_type_valid CHECK (provider_type IN ('INTERNAL', 'EXTERNAL', 'ACADEMIC', 'ONLINE_PLATFORM'))
);

CREATE INDEX ix_training_provider_tenant ON training_provider (tenant_id);
SELECT apply_tenant_rls('training_provider');

-- 2. Resource Person / Trainer / Instructor
CREATE TABLE resource_person (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    provider_id          uuid REFERENCES training_provider(id) ON DELETE SET NULL,
    full_name            varchar(150) NOT NULL,
    email                varchar(100),
    bio                  text,
    specialization       varchar(200),
    internal_employee_id uuid REFERENCES employee(id) ON DELETE SET NULL,
    created_at           timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by           uuid,
    updated_at           timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by           uuid,
    version              bigint NOT NULL DEFAULT 0
);

CREATE INDEX ix_resource_person_tenant ON resource_person (tenant_id);
CREATE INDEX ix_resource_person_provider ON resource_person (tenant_id, provider_id);
SELECT apply_tenant_rls('resource_person');

-- 3. Training Course Catalogue
CREATE TABLE training_course (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    provider_id       uuid REFERENCES training_provider(id) ON DELETE SET NULL,
    course_code       varchar(50) NOT NULL,
    title             varchar(200) NOT NULL,
    description       text,
    category          varchar(50) NOT NULL DEFAULT 'TECHNICAL', -- 'TECHNICAL', 'LEADERSHIP', 'COMPLIANCE', 'SOFT_SKILLS', 'SECURITY'
    delivery_mode     varchar(50) NOT NULL DEFAULT 'CLASSROOM', -- 'CLASSROOM', 'ONLINE_SELF_PACED', 'ONLINE_LIVE', 'BLENDED'
    duration_hours    numeric(5,2) NOT NULL DEFAULT 8.0,
    target_audience   text,
    prerequisites     text,
    max_capacity      int NOT NULL DEFAULT 30,
    competency_id     uuid REFERENCES competency(id) ON DELETE SET NULL,
    is_active         boolean NOT NULL DEFAULT true,
    created_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by        uuid,
    updated_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by        uuid,
    version           bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_training_course_code UNIQUE (tenant_id, course_code),
    CONSTRAINT training_course_category_valid CHECK (category IN ('TECHNICAL', 'LEADERSHIP', 'COMPLIANCE', 'SOFT_SKILLS', 'SECURITY')),
    CONSTRAINT training_course_mode_valid CHECK (delivery_mode IN ('CLASSROOM', 'ONLINE_SELF_PACED', 'ONLINE_LIVE', 'BLENDED'))
);

CREATE INDEX ix_training_course_tenant_category ON training_course (tenant_id, category);
CREATE INDEX ix_training_course_competency ON training_course (tenant_id, competency_id);
SELECT apply_tenant_rls('training_course');

-- 4. Training Schedule / Batch Offerings
CREATE TABLE training_schedule (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    course_id            uuid NOT NULL REFERENCES training_course(id) ON DELETE CASCADE,
    trainer_id           uuid REFERENCES resource_person(id) ON DELETE SET NULL,
    batch_code           varchar(50) NOT NULL,
    start_date           timestamptz NOT NULL,
    end_date             timestamptz NOT NULL,
    venue_name           varchar(200),
    virtual_meeting_url  varchar(500),
    total_seats          int NOT NULL DEFAULT 25,
    enrolled_seats       int NOT NULL DEFAULT 0,
    status               varchar(50) NOT NULL DEFAULT 'SCHEDULED', -- 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'
    cost_per_participant numeric(12,2) NOT NULL DEFAULT 0.00,
    created_at           timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by           uuid,
    updated_at           timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by           uuid,
    version              bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_training_schedule_batch UNIQUE (tenant_id, course_id, batch_code),
    CONSTRAINT training_schedule_status_valid CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX ix_training_schedule_tenant_course ON training_schedule (tenant_id, course_id);
CREATE INDEX ix_training_schedule_tenant_dates ON training_schedule (tenant_id, start_date);
SELECT apply_tenant_rls('training_schedule');

-- 5. Training Enrollment
CREATE TABLE training_enrollment (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    schedule_id         uuid NOT NULL REFERENCES training_schedule(id) ON DELETE CASCADE,
    employee_id         uuid NOT NULL REFERENCES employee(id) ON DELETE CASCADE,
    enrollment_type     varchar(50) NOT NULL DEFAULT 'SELF_ENROLLED', -- 'SELF_ENROLLED', 'MANAGER_NOMINATED', 'MANDATORY'
    status              varchar(50) NOT NULL DEFAULT 'ENROLLED', -- 'REQUESTED', 'APPROVED', 'REJECTED', 'ENROLLED', 'COMPLETED', 'CANCELLED', 'NO_SHOW'
    nominated_by        uuid REFERENCES employee(id) ON DELETE SET NULL,
    approval_remarks    text,
    approved_at         timestamptz,
    cancellation_reason text,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by          uuid,
    updated_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by          uuid,
    version             bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_training_enrollment_emp_sched UNIQUE (tenant_id, schedule_id, employee_id),
    CONSTRAINT training_enrollment_type_valid CHECK (enrollment_type IN ('SELF_ENROLLED', 'MANAGER_NOMINATED', 'MANDATORY')),
    CONSTRAINT training_enrollment_status_valid CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'ENROLLED', 'COMPLETED', 'CANCELLED', 'NO_SHOW'))
);

CREATE INDEX ix_training_enrollment_tenant_emp ON training_enrollment (tenant_id, employee_id);
CREATE INDEX ix_training_enrollment_schedule ON training_enrollment (tenant_id, schedule_id);
SELECT apply_tenant_rls('training_enrollment');

-- 6. Training Attendance Session Logs
CREATE TABLE training_attendance (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    enrollment_id     uuid NOT NULL REFERENCES training_enrollment(id) ON DELETE CASCADE,
    session_date      date NOT NULL,
    check_in_time     timestamptz NOT NULL DEFAULT clock_timestamp(),
    attendance_status varchar(50) NOT NULL DEFAULT 'PRESENT', -- 'PRESENT', 'LATE', 'ABSENT', 'EXCUSED'
    remarks           text,
    created_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by        uuid,
    updated_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by        uuid,
    version           bigint NOT NULL DEFAULT 0,
    CONSTRAINT training_attendance_status_valid CHECK (attendance_status IN ('PRESENT', 'LATE', 'ABSENT', 'EXCUSED'))
);

CREATE INDEX ix_training_attendance_tenant_enrollment ON training_attendance (tenant_id, enrollment_id);
SELECT apply_tenant_rls('training_attendance');

-- 7. Trainee Evaluation & Course Feedback
CREATE TABLE trainee_evaluation (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    enrollment_id      uuid NOT NULL REFERENCES training_enrollment(id) ON DELETE CASCADE,
    rating_score       int NOT NULL, -- 1 to 5
    content_rating     int NOT NULL DEFAULT 5, -- 1 to 5
    instructor_rating  int NOT NULL DEFAULT 5, -- 1 to 5
    feedback_comments  text,
    submitted_at       timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_at         timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by         uuid,
    updated_at         timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by         uuid,
    version            bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_trainee_evaluation_enrollment UNIQUE (tenant_id, enrollment_id),
    CONSTRAINT training_eval_score_range CHECK (rating_score BETWEEN 1 AND 5),
    CONSTRAINT training_eval_content_range CHECK (content_rating BETWEEN 1 AND 5),
    CONSTRAINT training_eval_instructor_range CHECK (instructor_rating BETWEEN 1 AND 5)
);

CREATE INDEX ix_trainee_evaluation_tenant_enrollment ON trainee_evaluation (tenant_id, enrollment_id);
SELECT apply_tenant_rls('trainee_evaluation');

-- 8. Training Certificate Digital Credentials
CREATE TABLE training_certificate (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    enrollment_id       uuid NOT NULL REFERENCES training_enrollment(id) ON DELETE CASCADE,
    certificate_number  varchar(100) NOT NULL,
    issued_date         date NOT NULL DEFAULT CURRENT_DATE,
    expiry_date         date,
    verification_hash   varchar(64) NOT NULL,
    file_url            varchar(500),
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by          uuid,
    updated_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by          uuid,
    version             bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_training_cert_number UNIQUE (tenant_id, certificate_number)
);

CREATE INDEX ix_training_certificate_tenant_enrollment ON training_certificate (tenant_id, enrollment_id);
SELECT apply_tenant_rls('training_certificate');

-- ============================================================================
-- Demonstration Fixtures for Acme Corp (00000000-0000-0000-0000-000000000001)
-- ============================================================================

DO $$
DECLARE
    v_tenant_id uuid := '00000000-0000-0000-0000-000000000001';
    v_kasun_id  uuid;
    v_amanda_id uuid;
    v_provider_internal uuid := gen_random_uuid();
    v_provider_external uuid := gen_random_uuid();
    v_trainer_aruni     uuid := gen_random_uuid();
    v_trainer_sanjaya   uuid := gen_random_uuid();
    v_c_kotlin          uuid := gen_random_uuid();
    v_c_owasp           uuid := gen_random_uuid();
    v_c_lead            uuid := gen_random_uuid();
    v_c_comm            uuid := gen_random_uuid();
    v_s_kotlin          uuid := gen_random_uuid();
    v_s_owasp           uuid := gen_random_uuid();
    v_s_lead            uuid := gen_random_uuid();
    v_s_comm            uuid := gen_random_uuid();
    v_enr_completed     uuid := gen_random_uuid();
    v_enr_upcoming      uuid := gen_random_uuid();
    v_cert_id           uuid := gen_random_uuid();
BEGIN
    SELECT id INTO v_kasun_id FROM employee WHERE tenant_id = v_tenant_id AND employee_code = 'LK010' LIMIT 1;
    SELECT id INTO v_amanda_id FROM employee WHERE tenant_id = v_tenant_id AND employee_code = 'LK001' LIMIT 1;

    IF v_kasun_id IS NULL THEN
        SELECT id INTO v_kasun_id FROM employee WHERE tenant_id = v_tenant_id ORDER BY created_at ASC LIMIT 1;
    END IF;
    IF v_amanda_id IS NULL THEN
        v_amanda_id := v_kasun_id;
    END IF;

    -- Providers
    INSERT INTO training_provider (id, tenant_id, provider_name, provider_type, contact_email, is_accredited)
    VALUES
        (v_provider_internal, v_tenant_id, 'Acme Engineering & Leadership Academy', 'INTERNAL', 'academy@acmecorp.com', true),
        (v_provider_external, v_tenant_id, 'Lanka Institute of Management & Technology', 'ACADEMIC', 'info@limt.edu.lk', true)
    ON CONFLICT DO NOTHING;

    -- Trainers
    INSERT INTO resource_person (id, tenant_id, provider_id, full_name, email, bio, specialization, internal_employee_id)
    VALUES
        (v_trainer_aruni, v_tenant_id, v_provider_internal, 'Dr. Aruni Silva', 'aruni.silva@acmecorp.com', 'Distinguished Enterprise Architect with 15+ years experience in reactive distributed systems.', 'Kotlin, Coroutines, Spring Modulith & DDD', v_amanda_id),
        (v_trainer_sanjaya, v_tenant_id, v_provider_external, 'Sanjaya Fernando', 'sanjaya@limt.edu.lk', 'Senior Certified Security & Leadership Consultant.', 'Application Security, OWASP, Agile Leadership', NULL)
    ON CONFLICT DO NOTHING;

    -- Courses
    INSERT INTO training_course (id, tenant_id, provider_id, course_code, title, description, category, delivery_mode, duration_hours, target_audience, max_capacity, is_active)
    VALUES
        (v_c_kotlin, v_tenant_id, v_provider_internal, 'ENG-KOTLIN-2026', 'Advanced Kotlin, Coroutines & Reactive Architecture', 'Master enterprise backend and mobile architecture with Kotlin 2.x, structured concurrency, and modular architecture.', 'TECHNICAL', 'BLENDED', 16.0, 'Senior Software Engineers & Tech Leads', 25, true),
        (v_c_owasp, v_tenant_id, v_provider_external, 'SEC-OWASP-2026', 'Secure Coding, Threat Modeling & OWASP Top 10', 'Comprehensive security engineering course covering cryptographically sound authentication, SQLi/XSS defense, and API protection.', 'SECURITY', 'ONLINE_LIVE', 8.0, 'Full Stack Engineers & QA Engineers', 30, true),
        (v_c_lead, v_tenant_id, v_provider_internal, 'LEAD-AGILE-2026', 'High-Performance Agile Engineering Leadership', 'Practical management skills, psychological safety, team mentorship, and high-velocity delivery in hybrid workforces.', 'LEADERSHIP', 'CLASSROOM', 12.0, 'Engineering Managers & Scrum Masters', 20, true),
        (v_c_comm, v_tenant_id, v_provider_external, 'COMM-EXEC-2026', 'Executive Presence & Technical Stakeholder Influence', 'Master persuasive technical storytelling, executive reporting, and cross-functional leadership communications.', 'SOFT_SKILLS', 'CLASSROOM', 8.0, 'Team Leads & Product Managers', 20, true)
    ON CONFLICT DO NOTHING;

    -- Schedules
    INSERT INTO training_schedule (id, tenant_id, course_id, trainer_id, batch_code, start_date, end_date, venue_name, virtual_meeting_url, total_seats, enrolled_seats, status, cost_per_participant)
    VALUES
        (v_s_kotlin, v_tenant_id, v_c_kotlin, v_trainer_aruni, 'BATCH-2026-01', now() - interval '14 days', now() - interval '12 days', 'Innovation Lab 3B', 'https://meet.acmecorp.com/kotlin-masterclass', 25, 18, 'COMPLETED', 0.00),
        (v_s_owasp, v_tenant_id, v_c_owasp, v_trainer_sanjaya, 'BATCH-2026-02', now() + interval '5 days', now() + interval '6 days', 'Virtual Classroom A', 'https://meet.acmecorp.com/owasp-security', 30, 22, 'SCHEDULED', 7500.00),
        (v_s_lead, v_tenant_id, v_c_lead, v_trainer_aruni, 'BATCH-2026-03', now() + interval '18 days', now() + interval '20 days', 'Executive Boardroom & Training Centre', NULL, 20, 8, 'SCHEDULED', 0.00),
        (v_s_comm, v_tenant_id, v_c_comm, v_trainer_sanjaya, 'BATCH-2026-04', now() + interval '30 days', now() + interval '31 days', 'Conference Hall 1', NULL, 20, 5, 'SCHEDULED', 5000.00)
    ON CONFLICT DO NOTHING;

    -- Enrollments for Kasun
    IF v_kasun_id IS NOT NULL THEN
        -- Completed Kotlin Course
        INSERT INTO training_enrollment (id, tenant_id, schedule_id, employee_id, enrollment_type, status, nominated_by, approval_remarks, approved_at)
        VALUES
            (v_enr_completed, v_tenant_id, v_s_kotlin, v_kasun_id, 'SELF_ENROLLED', 'COMPLETED', v_amanda_id, 'Approved for technical skill enhancement.', now() - interval '20 days')
        ON CONFLICT DO NOTHING;

        -- Attendance Record
        INSERT INTO training_attendance (tenant_id, enrollment_id, session_date, check_in_time, attendance_status, remarks)
        VALUES
            (v_tenant_id, v_enr_completed, (now() - interval '14 days')::date, now() - interval '14 days' + interval '9 hours', 'PRESENT', 'On-time check-in via mobile app.')
        ON CONFLICT DO NOTHING;

        -- Evaluation Record
        INSERT INTO trainee_evaluation (tenant_id, enrollment_id, rating_score, content_rating, instructor_rating, feedback_comments, submitted_at)
        VALUES
            (v_tenant_id, v_enr_completed, 5, 5, 5, 'Exceptional deep dive into coroutines and reactive pipelines. Highly recommended for every senior developer!', now() - interval '12 days')
        ON CONFLICT DO NOTHING;

        -- Certificate Record
        INSERT INTO training_certificate (id, tenant_id, enrollment_id, certificate_number, issued_date, expiry_date, verification_hash, file_url)
        VALUES
            (v_cert_id, v_tenant_id, v_enr_completed, 'CERT-2026-0891', (now() - interval '12 days')::date, (now() + interval '718 days')::date, '9a72e816c5b9679f04128532c253d1ec5df3421bb86127e4e1e5b88231ec6914', 'https://storage.acmecorp.com/certificates/CERT-2026-0891.pdf')
        ON CONFLICT DO NOTHING;

        -- Upcoming OWASP Course
        INSERT INTO training_enrollment (id, tenant_id, schedule_id, employee_id, enrollment_type, status, nominated_by, approval_remarks, approved_at)
        VALUES
            (v_enr_upcoming, v_tenant_id, v_s_owasp, v_kasun_id, 'MANAGER_NOMINATED', 'ENROLLED', v_amanda_id, 'Mandatory compliance training for Q3.', now() - interval '2 days')
        ON CONFLICT DO NOTHING;
    END IF;

END $$;
