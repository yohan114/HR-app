-- =============================================================================
-- V2 — Identity, access control and devices
--
-- Covers Phase 0 tasks P0-BE-11 through P0-BE-21.
--
-- Note the index convention: tenant_id is the LEADING column of every index on
-- a tenant-scoped table. RLS adds `tenant_id = current_tenant_id()` to every
-- query, so an index that does not start with tenant_id cannot satisfy the
-- predicate and the planner falls back to a scan.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Users
--
-- Separate from `employee` (added in Phase 1). Not every employee has a login
-- (factory staff often clock in at a kiosk and never touch the app), and not
-- every user is an employee (implementation consultants, auditors). Modelling
-- them as one table is a mistake that is expensive to undo later.
-- -----------------------------------------------------------------------------
CREATE TABLE app_user
(
    id                     uuid         PRIMARY KEY,
    tenant_id              uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id            uuid,
    username               varchar(255) NOT NULL,
    email                  varchar(320),
    password_hash          text,
    password_changed_at    timestamptz,
    must_change_password   boolean      NOT NULL DEFAULT false,
    mfa_enabled            boolean      NOT NULL DEFAULT false,
    mfa_secret_enc         text,
    mfa_recovery_codes_enc text,
    status                 varchar(32)  NOT NULL DEFAULT 'ACTIVE',
    locale                 varchar(16),
    timezone               varchar(64),
    last_login_at          timestamptz,
    failed_attempts        smallint     NOT NULL DEFAULT 0,
    locked_until           timestamptz,

    created_at             timestamptz  NOT NULL DEFAULT now(),
    created_by             uuid,
    updated_at             timestamptz  NOT NULL DEFAULT now(),
    updated_by             uuid,
    version                bigint       NOT NULL DEFAULT 0,

    CONSTRAINT app_user_status_valid CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED', 'PENDING_ACTIVATION'))
);

-- Usernames and emails are unique per tenant, not globally. Two different
-- customers can both have an "admin", and the same person may legitimately hold
-- accounts in two tenants.
CREATE UNIQUE INDEX ux_app_user_tenant_username ON app_user (tenant_id, lower(username));
CREATE UNIQUE INDEX ux_app_user_tenant_email    ON app_user (tenant_id, lower(email)) WHERE email IS NOT NULL;
CREATE INDEX ix_app_user_tenant_employee        ON app_user (tenant_id, employee_id);
CREATE INDEX ix_app_user_tenant_status          ON app_user (tenant_id, status);

SELECT apply_tenant_rls('app_user');

COMMENT ON COLUMN app_user.password_hash IS 'Argon2id. NULL for SSO-only accounts.';
COMMENT ON COLUMN app_user.employee_id IS 'Nullable: not every user is an employee, not every employee is a user.';

-- -----------------------------------------------------------------------------
-- Roles & permissions
--
-- `permission` is a global catalogue, not tenant-scoped: the set of things the
-- software can do is a property of the software, not of the customer. Roles are
-- tenant-scoped because each customer composes their own.
-- -----------------------------------------------------------------------------
CREATE TABLE permission
(
    key         varchar(128) PRIMARY KEY,
    module      varchar(64)  NOT NULL,
    description text         NOT NULL,

    CONSTRAINT permission_key_format CHECK (key ~ '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$')
);

COMMENT ON TABLE permission IS 'Global catalogue of permissions, e.g. leave.approve, payroll.run.';

CREATE TABLE role
(
    id          uuid         PRIMARY KEY,
    tenant_id   uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    key         varchar(64)  NOT NULL,
    name        varchar(255) NOT NULL,
    description text,
    is_system   boolean      NOT NULL DEFAULT false,

    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  uuid,
    version     bigint       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_role_tenant_key ON role (tenant_id, key);
SELECT apply_tenant_rls('role');

COMMENT ON COLUMN role.is_system IS 'System roles are seeded per tenant and cannot be deleted, only extended.';

CREATE TABLE role_permission
(
    tenant_id      uuid         NOT NULL,
    role_id        uuid         NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    permission_key varchar(128) NOT NULL REFERENCES permission (key) ON DELETE CASCADE,

    PRIMARY KEY (role_id, permission_key)
);

CREATE INDEX ix_role_permission_tenant ON role_permission (tenant_id, role_id);
SELECT apply_tenant_rls('role_permission');

CREATE TABLE user_role
(
    tenant_id  uuid        NOT NULL,
    user_id    uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role_id    uuid        NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    valid_from date        NOT NULL DEFAULT CURRENT_DATE,
    valid_to   date,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,

    PRIMARY KEY (user_id, role_id),
    CONSTRAINT user_role_period_valid CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE INDEX ix_user_role_tenant_user ON user_role (tenant_id, user_id);
SELECT apply_tenant_rls('user_role');

-- -----------------------------------------------------------------------------
-- Attribute-based data scoping
--
-- RBAC answers "may this user approve leave?". This answers "*whose* leave?".
-- Both are required: a department head and the CHRO hold the same permission but
-- must see different populations.
-- -----------------------------------------------------------------------------
CREATE TABLE data_scope
(
    id         uuid         PRIMARY KEY,
    tenant_id  uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    key        varchar(64)  NOT NULL,
    name       varchar(255) NOT NULL,
    expression text         NOT NULL,

    created_at timestamptz  NOT NULL DEFAULT now(),
    created_by uuid,
    updated_at timestamptz  NOT NULL DEFAULT now(),
    updated_by uuid,
    version    bigint       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_data_scope_tenant_key ON data_scope (tenant_id, key);
SELECT apply_tenant_rls('data_scope');

COMMENT ON COLUMN data_scope.expression IS
    'Eligibility-engine expression compiled to a query predicate, e.g. employee.supervisor_path CONTAINS :user_employee_id';

CREATE TABLE user_data_scope
(
    tenant_id     uuid NOT NULL,
    user_id       uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    data_scope_id uuid NOT NULL REFERENCES data_scope (id) ON DELETE CASCADE,

    PRIMARY KEY (user_id, data_scope_id)
);

CREATE INDEX ix_user_data_scope_tenant ON user_data_scope (tenant_id, user_id);
SELECT apply_tenant_rls('user_data_scope');

-- -----------------------------------------------------------------------------
-- Field-level permissions
--
-- Enforced in a serialisation interceptor, so a field the caller may not see is
-- absent from the response payload entirely rather than merely hidden by the UI.
-- -----------------------------------------------------------------------------
CREATE TABLE field_permission
(
    tenant_id   uuid        NOT NULL,
    role_id     uuid        NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    entity_type varchar(64) NOT NULL,
    field_key   varchar(128) NOT NULL,
    access      varchar(16) NOT NULL,

    PRIMARY KEY (role_id, entity_type, field_key),
    CONSTRAINT field_permission_access_valid CHECK (access IN ('HIDDEN', 'MASKED', 'READ', 'WRITE'))
);

CREATE INDEX ix_field_permission_tenant_entity ON field_permission (tenant_id, entity_type);
SELECT apply_tenant_rls('field_permission');

-- -----------------------------------------------------------------------------
-- Devices
--
-- A refresh token is bound to a device, and the biometric-sealed copy of that
-- token never leaves the device's secure hardware. Revoking a device therefore
-- revokes the ability to log in from it without a password.
-- -----------------------------------------------------------------------------
CREATE TABLE user_device
(
    id                   uuid         PRIMARY KEY,
    tenant_id            uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    user_id              uuid         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    device_id            varchar(128) NOT NULL,
    platform             varchar(16)  NOT NULL,
    model                varchar(128),
    os_version           varchar(64),
    app_version          varchar(32),
    push_token           text,
    biometric_enrolled   boolean      NOT NULL DEFAULT false,
    attestation_verified boolean      NOT NULL DEFAULT false,
    trusted              boolean      NOT NULL DEFAULT true,
    last_seen_at         timestamptz,
    revoked_at           timestamptz,
    revoked_reason       varchar(255),

    created_at           timestamptz  NOT NULL DEFAULT now(),
    created_by           uuid,
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    updated_by           uuid,
    version              bigint       NOT NULL DEFAULT 0,

    CONSTRAINT user_device_platform_valid CHECK (platform IN ('ANDROID', 'IOS', 'WEB', 'KIOSK'))
);

CREATE UNIQUE INDEX ux_user_device_user_device ON user_device (tenant_id, user_id, device_id);
CREATE INDEX ix_user_device_tenant_user        ON user_device (tenant_id, user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_user_device_push               ON user_device (tenant_id, user_id) WHERE push_token IS NOT NULL AND revoked_at IS NULL;

SELECT apply_tenant_rls('user_device');

-- -----------------------------------------------------------------------------
-- Refresh tokens
--
-- Rotating, single-use, and grouped into a "family" per login session.
--
-- Reuse detection: if a token that has already been used is presented again, the
-- most likely explanation is that it was stolen — so the entire family is
-- revoked, forcing re-authentication on every device sharing that session. This
-- is the standard OAuth2 mitigation for refresh token theft (RFC 9700 §4.14.2).
--
-- Only the hash is stored. A database dump must not yield usable tokens.
-- -----------------------------------------------------------------------------
CREATE TABLE refresh_token
(
    id             uuid         PRIMARY KEY,
    tenant_id      uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    user_id        uuid         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    device_id      uuid         REFERENCES user_device (id) ON DELETE CASCADE,
    token_hash     char(64)     NOT NULL,
    family_id      uuid         NOT NULL,
    parent_id      uuid,
    issued_at      timestamptz  NOT NULL DEFAULT now(),
    expires_at     timestamptz  NOT NULL,
    used_at        timestamptz,
    revoked_at     timestamptz,
    revoked_reason varchar(64)
);

CREATE UNIQUE INDEX ux_refresh_token_hash    ON refresh_token (token_hash);
CREATE INDEX ix_refresh_token_family         ON refresh_token (tenant_id, family_id);
CREATE INDEX ix_refresh_token_user           ON refresh_token (tenant_id, user_id);
CREATE INDEX ix_refresh_token_expiry         ON refresh_token (expires_at) WHERE revoked_at IS NULL;

SELECT apply_tenant_rls('refresh_token');

COMMENT ON COLUMN refresh_token.token_hash IS 'SHA-256 hex of the token. The token itself is never stored.';
COMMENT ON COLUMN refresh_token.family_id IS 'All tokens descended from one login. Revoked as a unit on reuse detection.';

-- -----------------------------------------------------------------------------
-- Login audit
--
-- Deliberately separate from the general audit log: it is queried on a different
-- access path (security review, account lockout, "where am I signed in?") and
-- retained on a different schedule.
-- -----------------------------------------------------------------------------
CREATE TABLE login_event
(
    id           uuid        PRIMARY KEY,
    tenant_id    uuid        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    user_id      uuid,
    device_id    uuid,
    username     varchar(255),
    method       varchar(32) NOT NULL,
    result       varchar(32) NOT NULL,
    failure_code varchar(64),
    ip           inet,
    user_agent   text,
    occurred_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT login_event_method_valid CHECK (method IN ('PASSWORD', 'BIOMETRIC', 'REFRESH', 'SSO', 'MFA')),
    CONSTRAINT login_event_result_valid CHECK (result IN ('SUCCESS', 'FAILURE', 'LOCKED', 'MFA_REQUIRED'))
);

CREATE INDEX ix_login_event_tenant_user ON login_event (tenant_id, user_id, occurred_at DESC);
CREATE INDEX ix_login_event_tenant_time ON login_event (tenant_id, occurred_at DESC);

SELECT apply_tenant_rls('login_event');

COMMENT ON COLUMN login_event.user_id IS 'NULL when the login failed because the username did not exist.';

-- -----------------------------------------------------------------------------
-- Password policy
-- -----------------------------------------------------------------------------
CREATE TABLE password_policy
(
    tenant_id         uuid        PRIMARY KEY REFERENCES tenant (id) ON DELETE CASCADE,
    min_length        smallint    NOT NULL DEFAULT 12,
    require_uppercase boolean     NOT NULL DEFAULT true,
    require_lowercase boolean     NOT NULL DEFAULT true,
    require_digit     boolean     NOT NULL DEFAULT true,
    require_symbol    boolean     NOT NULL DEFAULT false,
    history_count     smallint    NOT NULL DEFAULT 5,
    max_age_days      smallint,
    lockout_threshold smallint    NOT NULL DEFAULT 5,
    lockout_minutes   smallint    NOT NULL DEFAULT 15,
    updated_at        timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT password_min_length_sane CHECK (min_length BETWEEN 8 AND 128)
);

SELECT apply_tenant_rls('password_policy');

-- -----------------------------------------------------------------------------
-- Seed the global permission catalogue for Phase 0.
--
-- Module permissions are added by their own migrations as those modules land.
-- -----------------------------------------------------------------------------
INSERT INTO permission (key, module, description) VALUES
    ('platform.tenant.view',   'platform', 'View tenant configuration'),
    ('platform.tenant.manage', 'platform', 'Modify tenant configuration'),
    ('platform.audit.view',    'platform', 'View the audit log'),
    ('identity.user.view',     'identity', 'View user accounts'),
    ('identity.user.manage',   'identity', 'Create and modify user accounts'),
    ('identity.role.view',     'identity', 'View roles and permissions'),
    ('identity.role.manage',   'identity', 'Create and modify roles'),
    ('identity.device.view',   'identity', 'View registered devices'),
    ('identity.device.revoke', 'identity', 'Revoke a registered device')
ON CONFLICT (key) DO NOTHING;
