-- =============================================================================
-- V13 — Leave Application & Approval Workflow Engine (P2-BE-31 to P2-BE-36)
--
-- Adds:
--   - public_holiday: Calendar holidays with location scoping
--   - leave_application: Employee leave submissions and approval lifecycle
--   - leave_application_day: Itemized date-by-date working-day breakdown
--
-- Enforces PostgreSQL Row-Level Security (RLS) on all tables and tenant_id
-- leading composite indexes.
-- =============================================================================

-- =============================================================================
-- 1. Public Holiday
-- =============================================================================
CREATE TABLE public_holiday
(
    id           uuid         PRIMARY KEY,
    tenant_id    uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    holiday_date date         NOT NULL,
    name         varchar(128) NOT NULL,
    country_code varchar(2)   NOT NULL DEFAULT 'LK',
    is_half_day  boolean      NOT NULL DEFAULT false,

    created_at   timestamptz  NOT NULL DEFAULT now(),
    created_by   uuid,
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    updated_by   uuid,
    version      bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uq_public_holiday_date UNIQUE (tenant_id, country_code, holiday_date)
);

CREATE INDEX ix_public_holiday_tenant_date ON public_holiday (tenant_id, holiday_date);
SELECT apply_tenant_rls('public_holiday');

COMMENT ON TABLE public_holiday IS 'Statutory and company public holidays used for working-day calculations.';

-- =============================================================================
-- 2. Leave Application
-- =============================================================================
CREATE TABLE leave_application
(
    id            uuid          PRIMARY KEY,
    tenant_id     uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id   uuid          NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    leave_year_id uuid          NOT NULL REFERENCES leave_year (id) ON DELETE CASCADE,
    leave_type_id uuid          NOT NULL REFERENCES leave_type (id) ON DELETE CASCADE,
    start_date    date          NOT NULL,
    end_date      date          NOT NULL,
    total_days    numeric(5, 2) NOT NULL,
    day_portion   varchar(16)   NOT NULL DEFAULT 'FULL_DAY',
    status        varchar(32)   NOT NULL DEFAULT 'SUBMITTED',
    reason        text,
    submitted_at  timestamptz   NOT NULL DEFAULT now(),
    actioned_at   timestamptz,
    actioned_by   uuid,
    action_reason text,

    created_at    timestamptz   NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz   NOT NULL DEFAULT now(),
    updated_by    uuid,
    version       bigint        NOT NULL DEFAULT 0,

    CONSTRAINT leave_application_dates_valid CHECK (end_date >= start_date),
    CONSTRAINT leave_application_days_positive CHECK (total_days > 0),
    CONSTRAINT leave_application_portion_valid CHECK (day_portion IN ('FULL_DAY', 'FIRST_HALF', 'SECOND_HALF')),
    CONSTRAINT leave_application_status_valid CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED', 'WITHDRAWN'))
);

CREATE INDEX ix_leave_app_tenant_emp_dates ON leave_application (tenant_id, employee_id, start_date);
CREATE INDEX ix_leave_app_tenant_status ON leave_application (tenant_id, status);
SELECT apply_tenant_rls('leave_application');

COMMENT ON TABLE leave_application IS 'Employee leave requests and workflow approval state machine.';

-- =============================================================================
-- 3. Leave Application Day
-- =============================================================================
CREATE TABLE leave_application_day
(
    id                   uuid          PRIMARY KEY,
    tenant_id            uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    leave_application_id uuid          NOT NULL REFERENCES leave_application (id) ON DELETE CASCADE,
    day_date             date          NOT NULL,
    day_portion          varchar(16)   NOT NULL DEFAULT 'FULL_DAY',
    is_working_day       boolean       NOT NULL DEFAULT true,
    hours                numeric(4, 2) NOT NULL DEFAULT 8.00,

    created_at           timestamptz   NOT NULL DEFAULT now(),
    created_by           uuid,
    updated_at           timestamptz   NOT NULL DEFAULT now(),
    updated_by           uuid,
    version              bigint        NOT NULL DEFAULT 0,

    CONSTRAINT uq_leave_app_day UNIQUE (tenant_id, leave_application_id, day_date),
    CONSTRAINT leave_app_day_portion_valid CHECK (day_portion IN ('FULL_DAY', 'FIRST_HALF', 'SECOND_HALF'))
);

CREATE INDEX ix_leave_app_day_tenant_date ON leave_application_day (tenant_id, day_date);
SELECT apply_tenant_rls('leave_application_day');

COMMENT ON TABLE leave_application_day IS 'Granular date-by-date schedule breakdown for each leave application.';
