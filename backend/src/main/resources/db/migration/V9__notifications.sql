-- =============================================================================
-- V9 — Notifications
--
-- Implements docs/04-data-model.md §16 and the dispatch flow in
-- docs/03-architecture.md §10.
--
-- The shape to notice: a `notification` is what the user is told, and a
-- `notification_delivery` is one attempt to tell them on one channel. Keeping
-- them separate is what makes "delivered to your phone but not your email"
-- expressible, and what lets a retry be a new row rather than a mutation of
-- history.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Templates
--
-- Per tenant, per event, per channel, per locale. A tenant that writes its own
-- copy overrides the shipped default; a locale we have no translation for falls
-- back at render time rather than here, so adding a language is data, not a
-- migration.
-- -----------------------------------------------------------------------------
CREATE TABLE notification_template
(
    id                 uuid         PRIMARY KEY,
    tenant_id          uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    event_key          varchar(128) NOT NULL,
    channel            varchar(32)  NOT NULL,
    locale             varchar(16)  NOT NULL DEFAULT 'en',
    subject            varchar(255),
    body               text         NOT NULL,
    -- e.g. 'hrapp://leave/{leaveId}'. Placeholders resolve from the event payload,
    -- so a template change can redirect a notification without a code change.
    deep_link_pattern  varchar(512),
    active             boolean      NOT NULL DEFAULT true,

    created_at         timestamptz  NOT NULL DEFAULT now(),
    created_by         uuid,
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    updated_by         uuid,
    version            bigint       NOT NULL DEFAULT 0,

    CONSTRAINT notification_template_channel_valid CHECK (
        channel IN ('PUSH', 'EMAIL', 'IN_APP', 'SMS')
    )
);

CREATE UNIQUE INDEX ux_notification_template_key
    ON notification_template (tenant_id, event_key, channel, locale);
CREATE INDEX ix_notification_template_tenant ON notification_template (tenant_id, event_key);
SELECT apply_tenant_rls('notification_template');

-- -----------------------------------------------------------------------------
-- Per-user preferences
--
-- Absence means "use the default for this event", which is why there is no row
-- per user per event per channel seeded anywhere: a tenant with 10,000 staff and
-- 40 event types would otherwise carry 1.6 M rows that all say the same thing.
-- -----------------------------------------------------------------------------
CREATE TABLE notification_preference
(
    tenant_id   uuid         NOT NULL,
    user_id     uuid         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    event_key   varchar(128) NOT NULL,
    channel     varchar(32)  NOT NULL,
    enabled     boolean      NOT NULL DEFAULT true,
    -- IMMEDIATE or DIGEST. Digest batches low-priority notifications into one
    -- daily summary — notification fatigue is a real and specific complaint
    -- about HR apps, and the answer is not "send fewer important things".
    digest_mode varchar(16)  NOT NULL DEFAULT 'IMMEDIATE',

    updated_at  timestamptz  NOT NULL DEFAULT now(),

    PRIMARY KEY (tenant_id, user_id, event_key, channel),
    CONSTRAINT notification_preference_channel_valid CHECK (
        channel IN ('PUSH', 'EMAIL', 'IN_APP', 'SMS')
    ),
    CONSTRAINT notification_preference_digest_valid CHECK (
        digest_mode IN ('IMMEDIATE', 'DIGEST')
    )
);

SELECT apply_tenant_rls('notification_preference');

-- -----------------------------------------------------------------------------
-- Quiet hours
--
-- One row per user, not per event: "do not wake me at 3am" is a property of the
-- person, not of the thing being sent.
--
-- Stored as local wall-clock times plus the user's timezone rather than as UTC
-- offsets. An offset is wrong twice a year in every country that observes
-- daylight saving, and the failure mode is notifications arriving an hour into
-- the night for six months.
-- -----------------------------------------------------------------------------
CREATE TABLE notification_quiet_hours
(
    tenant_id  uuid        NOT NULL,
    user_id    uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- Local times. `start_at > end_at` legitimately means the window crosses
    -- midnight, which is the common case (22:00 → 07:00).
    start_at   time        NOT NULL,
    end_at     time        NOT NULL,
    timezone   varchar(64) NOT NULL,
    -- Days the window applies, 1 = Monday. Empty means every day.
    days       smallint[]  NOT NULL DEFAULT '{}',
    enabled    boolean     NOT NULL DEFAULT true,

    updated_at timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (tenant_id, user_id),
    CONSTRAINT quiet_hours_days_valid CHECK (
        days <@ ARRAY[1, 2, 3, 4, 5, 6, 7]::smallint[]
    )
);

SELECT apply_tenant_rls('notification_quiet_hours');

COMMENT ON COLUMN notification_quiet_hours.timezone IS
    'IANA zone, e.g. Asia/Colombo. Not an offset: an offset is wrong twice a year wherever daylight saving applies.';

-- -----------------------------------------------------------------------------
-- Push tokens
--
-- Deliberately NOT a new table. `user_device.push_token` already exists, is
-- written on every sign-in by AuthenticationService and DeviceService, and
-- already carries the partial index `ix_user_device_push` for exactly this
-- lookup. A second table would be a source of truth that nothing writes to, and
-- the symptom would be notifications silently going nowhere.
--
-- What is genuinely missing is the other direction: the provider is the only
-- thing that can tell us a token is dead — FCM with UNREGISTERED, APNs with
-- 410 — because an uninstalled app cannot report its own removal. Clearing the
-- token is the whole treatment: the partial index means "has a token" and
-- "is reachable" become the same predicate, and the failed delivery row keeps
-- the reason.
-- -----------------------------------------------------------------------------
COMMENT ON COLUMN user_device.push_token IS
    'FCM/APNs token, or NULL when the provider has told us it is dead. Cleared on UNREGISTERED/410 rather than flagged, so ix_user_device_push stays the reachability index.';

-- -----------------------------------------------------------------------------
-- Notifications
--
-- What the user is told. Partitioned by month because this is the highest-volume
-- table in the product after attendance, and retention is 12 months.
-- -----------------------------------------------------------------------------
CREATE TABLE notification
(
    id          uuid         NOT NULL,
    tenant_id   uuid         NOT NULL,
    user_id     uuid         NOT NULL,
    event_key   varchar(128) NOT NULL,
    title       varchar(255) NOT NULL,
    body        text         NOT NULL,
    deep_link   varchar(512),
    priority    varchar(16)  NOT NULL DEFAULT 'NORMAL',
    channels    text[]       NOT NULL DEFAULT '{}',
    data        jsonb        NOT NULL DEFAULT '{}'::jsonb,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    read_at     timestamptz,
    actioned_at timestamptz,

    PRIMARY KEY (id, created_at),
    CONSTRAINT notification_priority_valid CHECK (
        priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')
    )
) PARTITION BY RANGE (created_at);

CREATE INDEX ix_notification_tenant_user ON notification (tenant_id, user_id, created_at DESC);
-- Serves the unread badge, which every client asks for on every foreground.
CREATE INDEX ix_notification_unread ON notification (tenant_id, user_id) WHERE read_at IS NULL;
SELECT apply_tenant_rls('notification');

-- -----------------------------------------------------------------------------
-- Delivery attempts
--
-- One row per channel per attempt. Append-only in spirit: a retry adds a row
-- rather than overwriting the last, so "we tried four times over two hours and
-- the token was dead" is answerable after the fact.
-- -----------------------------------------------------------------------------
CREATE TABLE notification_delivery
(
    id              uuid         NOT NULL,
    tenant_id       uuid         NOT NULL,
    notification_id uuid         NOT NULL,
    channel         varchar(32)  NOT NULL,
    provider        varchar(32),
    status          varchar(32)  NOT NULL,
    attempt         smallint     NOT NULL DEFAULT 1,
    attempted_at    timestamptz  NOT NULL DEFAULT now(),
    delivered_at    timestamptz,
    next_attempt_at timestamptz,
    error           text,

    PRIMARY KEY (id, attempted_at),
    CONSTRAINT notification_delivery_status_valid CHECK (
        status IN ('PENDING', 'SENT', 'DELIVERED', 'FAILED', 'DEAD_LETTERED', 'SUPPRESSED')
    )
) PARTITION BY RANGE (attempted_at);

CREATE INDEX ix_notification_delivery_tenant ON notification_delivery (tenant_id, notification_id);
-- Drives the retry sweep: everything due, oldest first.
CREATE INDEX ix_notification_delivery_due
    ON notification_delivery (tenant_id, next_attempt_at)
    WHERE status = 'PENDING';
SELECT apply_tenant_rls('notification_delivery');

COMMENT ON COLUMN notification_delivery.status IS
    'SUPPRESSED means we deliberately did not send — quiet hours, or the user turned the channel off. Distinct from FAILED so a silent night is not read as an outage.';

-- -----------------------------------------------------------------------------
-- Partitions
--
-- Created ahead for the first year. A scheduled job extends them; running out of
-- partitions makes every insert fail, so the job alerts well before the edge.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    start_month date := date_trunc('month', now())::date;
    i           integer;
BEGIN
    FOR i IN 0..12 LOOP
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS notification_p%s PARTITION OF notification
             FOR VALUES FROM (%L) TO (%L)',
            to_char(start_month + (i || ' month')::interval, 'YYYYMM'),
            start_month + (i || ' month')::interval,
            start_month + ((i + 1) || ' month')::interval
        );
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS notification_delivery_p%s PARTITION OF notification_delivery
             FOR VALUES FROM (%L) TO (%L)',
            to_char(start_month + (i || ' month')::interval, 'YYYYMM'),
            start_month + (i || ' month')::interval,
            start_month + ((i + 1) || ' month')::interval
        );
    END LOOP;
END
$$;

-- -----------------------------------------------------------------------------
-- Permissions
-- -----------------------------------------------------------------------------
INSERT INTO permission (key, module, description) VALUES
    ('notification.template.view',   'notification', 'View notification templates'),
    ('notification.template.manage', 'notification', 'Create and modify notification templates'),
    ('notification.send',            'notification', 'Send an ad-hoc notification')
ON CONFLICT (key) DO NOTHING;
