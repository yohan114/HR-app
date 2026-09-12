-- ============================================================================
-- V27: Physical Biometric Device Listener & Terminal Management Engine
-- Terminals (ZKTeco, Hikvision, Suprema, Anviz) and Device Commands
-- ============================================================================

-- 1. Biometric Hardware Device
CREATE TABLE biometric_device (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    name                 varchar(100) NOT NULL,
    serial_number        varchar(64) NOT NULL,
    vendor               varchar(32) NOT NULL DEFAULT 'ZKTECO',
    model_name           varchar(64),
    ip_address           varchar(64),
    port                 int NOT NULL DEFAULT 4370,
    location_name        varchar(100),
    direction            varchar(16) NOT NULL DEFAULT 'IN_OUT',
    status               varchar(32) NOT NULL DEFAULT 'ONLINE',
    last_heartbeat_at    timestamptz,
    last_sync_at         timestamptz,
    total_punches_logged bigint NOT NULL DEFAULT 0,
    firmware_version     varchar(64),
    auth_token           varchar(128),
    created_at           timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by           uuid,
    updated_at           timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by           uuid,
    version              bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_biometric_device_sn UNIQUE (tenant_id, serial_number),
    CONSTRAINT biometric_device_vendor_valid CHECK (vendor IN ('ZKTECO', 'HIKVISION', 'SUPREMA', 'ANVIZ', 'GENERIC_HTTP')),
    CONSTRAINT biometric_device_direction_valid CHECK (direction IN ('IN', 'OUT', 'IN_OUT')),
    CONSTRAINT biometric_device_status_valid CHECK (status IN ('ONLINE', 'OFFLINE', 'SYNCING', 'ERROR'))
);

CREATE INDEX ix_biometric_device_tenant ON biometric_device (tenant_id);
SELECT apply_tenant_rls('biometric_device');

-- 2. Biometric Device Command Queue (Server to Terminal)
CREATE TABLE biometric_device_command (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    device_id            uuid NOT NULL REFERENCES biometric_device(id) ON DELETE CASCADE,
    command_type         varchar(32) NOT NULL,
    command_content      text NOT NULL,
    status               varchar(32) NOT NULL DEFAULT 'PENDING',
    sent_at              timestamptz,
    executed_at          timestamptz,
    created_at           timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by           uuid,
    updated_at           timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by           uuid,
    version              bigint NOT NULL DEFAULT 0,
    CONSTRAINT biometric_cmd_status_valid CHECK (status IN ('PENDING', 'SENT', 'EXECUTED', 'FAILED'))
);

CREATE INDEX ix_biometric_device_cmd_tenant_device ON biometric_device_command (tenant_id, device_id);
SELECT apply_tenant_rls('biometric_device_command');
