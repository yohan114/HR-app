-- =============================================================================
-- V14 — Attendance & Shift Roster Engine (P2-BE-41 through P2-BE-58)
--
-- Adds core time & attendance models:
--   - shift: Shift templates, working hours, grace periods, and overtime thresholds
--   - attendance_policy: Mobile/biometric policy, geofence, and overtime multipliers
--   - employee_shift_schedule: Daily scheduled shifts, rest days, and holidays
--   - raw_punch: Immutable check-in/out punch log with trust and idempotency metadata
--   - daily_attendance: Deterministically derived daily attendance records with audit calculation traces
--
-- Enforces PostgreSQL Row-Level Security (RLS) on all tables with tenant-leading indexes.
-- =============================================================================

-- =============================================================================
-- 1. Shift Template
-- =============================================================================
CREATE TABLE shift
(
    id                         uuid         PRIMARY KEY,
    tenant_id                  uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code                       varchar(32)  NOT NULL,
    name                       varchar(128) NOT NULL,
    shift_type                 varchar(32)  NOT NULL DEFAULT 'FIXED',
    start_time                 time         NOT NULL,
    end_time                   time         NOT NULL,
    break_minutes              integer      NOT NULL DEFAULT 60,
    crosses_midnight           boolean      NOT NULL DEFAULT false,
    working_minutes            integer      NOT NULL DEFAULT 480,
    grace_in_minutes           integer      NOT NULL DEFAULT 15,
    grace_out_minutes          integer      NOT NULL DEFAULT 10,
    half_day_threshold_minutes integer      NOT NULL DEFAULT 240,
    ot_eligible                boolean      NOT NULL DEFAULT true,
    ot_start_after_minutes     integer      NOT NULL DEFAULT 480,
    min_ot_minutes             integer      NOT NULL DEFAULT 30,
    color                      varchar(32)  NOT NULL DEFAULT '#3b82f6',
    is_active                  boolean      NOT NULL DEFAULT true,

    created_at                 timestamptz  NOT NULL DEFAULT now(),
    created_by                 uuid,
    updated_at                 timestamptz  NOT NULL DEFAULT now(),
    updated_by                 uuid,
    version                    bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uq_shift_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT shift_type_valid CHECK (shift_type IN ('FIXED', 'ROTATING', 'SPLIT', 'NIGHT', 'FLEXIBLE', 'OPEN'))
);

CREATE INDEX ix_shift_tenant_code ON shift (tenant_id, code);
SELECT apply_tenant_rls('shift');

COMMENT ON TABLE shift IS 'Configured work shift templates with grace periods and overtime qualification rules.';

-- =============================================================================
-- 2. Attendance Policy
-- =============================================================================
CREATE TABLE attendance_policy
(
    id                         uuid          PRIMARY KEY,
    tenant_id                  uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    name                       varchar(128)  NOT NULL,
    location_capture           varchar(32)   NOT NULL DEFAULT 'OPTIONAL',
    geofence_enforcement       varchar(32)   NOT NULL DEFAULT 'WARN',
    mock_location_action       varchar(32)   NOT NULL DEFAULT 'FLAG',
    allow_offline_punch        boolean       NOT NULL DEFAULT true,
    max_offline_hours          integer       NOT NULL DEFAULT 72,
    lateness_penalty_tier      varchar(32)   NOT NULL DEFAULT 'PER_MINUTE',
    overtime_weekday_multiplier numeric(4, 2) NOT NULL DEFAULT 1.50,
    overtime_restday_multiplier numeric(4, 2) NOT NULL DEFAULT 2.00,
    overtime_holiday_multiplier numeric(4, 2) NOT NULL DEFAULT 2.50,

    created_at                 timestamptz   NOT NULL DEFAULT now(),
    created_by                 uuid,
    updated_at                 timestamptz   NOT NULL DEFAULT now(),
    updated_by                 uuid,
    version                    bigint        NOT NULL DEFAULT 0,

    CONSTRAINT location_capture_valid CHECK (location_capture IN ('OFF', 'OPTIONAL', 'REQUIRED')),
    CONSTRAINT geofence_enforcement_valid CHECK (geofence_enforcement IN ('OFF', 'WARN', 'BLOCK')),
    CONSTRAINT mock_location_action_valid CHECK (mock_location_action IN ('IGNORE', 'FLAG', 'BLOCK')),
    CONSTRAINT lateness_penalty_tier_valid CHECK (lateness_penalty_tier IN ('PER_MINUTE', 'TIERED_BRACKETS', 'FLAT_DEDUCTION'))
);

CREATE INDEX ix_attendance_policy_tenant ON attendance_policy (tenant_id);
SELECT apply_tenant_rls('attendance_policy');

COMMENT ON TABLE attendance_policy IS 'Tenant attendance rules governing location capture, geofencing, and overtime multipliers.';

-- =============================================================================
-- 3. Employee Shift Schedule
-- =============================================================================
CREATE TABLE employee_shift_schedule
(
    id            uuid         PRIMARY KEY,
    tenant_id     uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id   uuid         NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    work_date     date         NOT NULL,
    shift_id      uuid         REFERENCES shift (id) ON DELETE SET NULL,
    is_rest_day   boolean      NOT NULL DEFAULT false,
    is_holiday    boolean      NOT NULL DEFAULT false,
    holiday_name  varchar(128),
    source        varchar(32)  NOT NULL DEFAULT 'DEFAULT_SHIFT',

    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid,
    version       bigint       NOT NULL DEFAULT 0,

    CONSTRAINT uq_emp_shift_schedule UNIQUE (tenant_id, employee_id, work_date),
    CONSTRAINT schedule_source_valid CHECK (source IN ('DEFAULT_SHIFT', 'ROSTER', 'MANUAL', 'SWAP'))
);

CREATE INDEX ix_emp_shift_sched_tenant_date ON employee_shift_schedule (tenant_id, work_date);
CREATE INDEX ix_emp_shift_sched_tenant_emp_date ON employee_shift_schedule (tenant_id, employee_id, work_date);
SELECT apply_tenant_rls('employee_shift_schedule');

COMMENT ON TABLE employee_shift_schedule IS 'Rostered and assigned work shifts per employee per day.';

-- =============================================================================
-- 4. Raw Punch (Clock-in / Clock-out Log)
-- =============================================================================
CREATE TABLE raw_punch
(
    id                     uuid           PRIMARY KEY,
    tenant_id              uuid           NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id            uuid           NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    punched_at             timestamptz    NOT NULL,
    punch_type             varchar(32)    NOT NULL DEFAULT 'AUTO',
    source                 varchar(32)    NOT NULL DEFAULT 'BIOMETRIC_DEVICE',
    device_id              varchar(64),
    location_id            uuid,
    geo_lat                numeric(10, 7),
    geo_lng                numeric(10, 7),
    geo_accuracy_m         numeric(8, 2),
    geofence_status        varchar(32)    NOT NULL DEFAULT 'UNKNOWN',
    is_mock_location       boolean        NOT NULL DEFAULT false,
    client_idempotency_key varchar(128),
    recorded_offline       boolean        NOT NULL DEFAULT false,
    synced_at              timestamptz    NOT NULL DEFAULT now(),

    created_at             timestamptz    NOT NULL DEFAULT now(),
    created_by             uuid,
    updated_at             timestamptz    NOT NULL DEFAULT now(),
    updated_by             uuid,
    version                bigint         NOT NULL DEFAULT 0,

    CONSTRAINT punch_type_valid CHECK (punch_type IN ('IN', 'OUT', 'BREAK_IN', 'BREAK_OUT', 'AUTO')),
    CONSTRAINT punch_source_valid CHECK (source IN ('BIOMETRIC_DEVICE', 'MOBILE_APP', 'WEB_PORTAL', 'KIOSK', 'MANUAL_IMPORT')),
    CONSTRAINT geofence_status_valid CHECK (geofence_status IN ('INSIDE', 'OUTSIDE', 'UNKNOWN', 'NOT_APPLICABLE'))
);

CREATE INDEX ix_raw_punch_tenant_emp_punched ON raw_punch (tenant_id, employee_id, punched_at);
CREATE INDEX ix_raw_punch_tenant_idempotency ON raw_punch (tenant_id, client_idempotency_key) WHERE client_idempotency_key IS NOT NULL;
SELECT apply_tenant_rls('raw_punch');

COMMENT ON TABLE raw_punch IS 'Immutable raw clock-in/out records ingested from biometric devices and mobile devices.';

-- =============================================================================
-- 5. Daily Attendance Record (Consolidated Derived Computation)
-- =============================================================================
CREATE TABLE daily_attendance
(
    id                         uuid           PRIMARY KEY,
    tenant_id                  uuid           NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id                uuid           NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    work_date                  date           NOT NULL,
    shift_id                   uuid           REFERENCES shift (id) ON DELETE SET NULL,
    first_in_at                timestamptz,
    last_out_at                timestamptz,
    gross_duration_minutes     integer        NOT NULL DEFAULT 0,
    break_minutes              integer        NOT NULL DEFAULT 0,
    net_worked_minutes         integer        NOT NULL DEFAULT 0,
    late_minutes               integer        NOT NULL DEFAULT 0,
    early_leave_minutes        integer        NOT NULL DEFAULT 0,
    overtime_minutes_normal    integer        NOT NULL DEFAULT 0,
    overtime_minutes_rest_day  integer        NOT NULL DEFAULT 0,
    overtime_minutes_holiday   integer        NOT NULL DEFAULT 0,
    day_status                 varchar(32)    NOT NULL DEFAULT 'PRESENT',
    leave_type_id              uuid,
    leave_days                 numeric(4, 2)  NOT NULL DEFAULT 0.00,
    anomaly_flags              text           NOT NULL DEFAULT '',
    calculation_trace          text,
    computed_at                timestamptz    NOT NULL DEFAULT now(),

    created_at                 timestamptz    NOT NULL DEFAULT now(),
    created_by                 uuid,
    updated_at                 timestamptz    NOT NULL DEFAULT now(),
    updated_by                 uuid,
    version                    bigint         NOT NULL DEFAULT 0,

    CONSTRAINT uq_daily_attendance_tenant_emp_date UNIQUE (tenant_id, employee_id, work_date),
    CONSTRAINT day_status_valid CHECK (day_status IN ('PRESENT', 'HALF_DAY', 'ABSENT', 'ON_LEAVE', 'REST_DAY', 'HOLIDAY', 'NO_SHOW'))
);

CREATE INDEX ix_daily_att_tenant_emp_date ON daily_attendance (tenant_id, employee_id, work_date);
CREATE INDEX ix_daily_att_tenant_date_status ON daily_attendance (tenant_id, work_date, day_status);
SELECT apply_tenant_rls('daily_attendance');

COMMENT ON TABLE daily_attendance IS 'Derived daily attendance roll-up with worked hours, lateness, overtime tiers, and formula trace.';
