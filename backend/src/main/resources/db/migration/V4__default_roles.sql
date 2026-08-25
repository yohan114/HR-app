-- =============================================================================
-- V4 — Default roles and tenant provisioning
--
-- Gives every new tenant a working set of roles and a password policy, so that
-- provisioning is one call rather than a checklist someone can get wrong.
--
-- Roles are seeded per tenant rather than shared globally because customers
-- rename and extend them. `is_system` marks the four we create: they can be
-- extended with extra permissions but not deleted, so there is always at least
-- one role that can administer the tenant.
-- =============================================================================

CREATE OR REPLACE FUNCTION provision_tenant_defaults(target_tenant uuid)
    RETURNS void
    LANGUAGE plpgsql
AS $$
DECLARE
    admin_role_id    uuid;
    hr_admin_role_id uuid;
    manager_role_id  uuid;
    employee_role_id uuid;
BEGIN
    -- Password policy. Defaults are the strict ones; note that periodic rotation
    -- is deliberately OFF (max_age_days NULL) — see PasswordPolicyService for why.
    INSERT INTO password_policy (tenant_id)
    VALUES (target_tenant)
    ON CONFLICT (tenant_id) DO NOTHING;

    -- ---- Roles -------------------------------------------------------------
    INSERT INTO role (id, tenant_id, key, name, description, is_system)
    VALUES (gen_random_uuid(), target_tenant, 'ADMIN', 'Administrator',
            'Full access to configuration and all employee data', true)
    ON CONFLICT (tenant_id, key) DO NOTHING;

    INSERT INTO role (id, tenant_id, key, name, description, is_system)
    VALUES (gen_random_uuid(), target_tenant, 'HR_ADMIN', 'HR Administrator',
            'Manages employees, leave, attendance and payroll', true)
    ON CONFLICT (tenant_id, key) DO NOTHING;

    INSERT INTO role (id, tenant_id, key, name, description, is_system)
    VALUES (gen_random_uuid(), target_tenant, 'MANAGER', 'Manager',
            'Approves requests and views their own team', true)
    ON CONFLICT (tenant_id, key) DO NOTHING;

    INSERT INTO role (id, tenant_id, key, name, description, is_system)
    VALUES (gen_random_uuid(), target_tenant, 'EMPLOYEE', 'Employee',
            'Self-service access to their own records', true)
    ON CONFLICT (tenant_id, key) DO NOTHING;

    SELECT id INTO admin_role_id    FROM role WHERE tenant_id = target_tenant AND key = 'ADMIN';
    SELECT id INTO hr_admin_role_id FROM role WHERE tenant_id = target_tenant AND key = 'HR_ADMIN';
    SELECT id INTO manager_role_id  FROM role WHERE tenant_id = target_tenant AND key = 'MANAGER';
    SELECT id INTO employee_role_id FROM role WHERE tenant_id = target_tenant AND key = 'EMPLOYEE';

    -- ---- Permissions -------------------------------------------------------
    -- ADMIN gets everything currently in the catalogue. Note this is a snapshot,
    -- not a wildcard: a permission added by a later migration is NOT granted
    -- retroactively. That is intentional — a new capability should be an explicit
    -- decision by the customer, not something that silently appears on a role.
    INSERT INTO role_permission (tenant_id, role_id, permission_key)
    SELECT target_tenant, admin_role_id, p.key FROM permission p
    ON CONFLICT DO NOTHING;

    INSERT INTO role_permission (tenant_id, role_id, permission_key)
    SELECT target_tenant, hr_admin_role_id, p.key
    FROM permission p
    WHERE p.key IN ('identity.user.view', 'identity.user.manage',
                    'identity.device.view', 'identity.device.revoke',
                    'platform.audit.view')
    ON CONFLICT DO NOTHING;

    INSERT INTO role_permission (tenant_id, role_id, permission_key)
    SELECT target_tenant, manager_role_id, p.key
    FROM permission p
    WHERE p.key IN ('identity.user.view')
    ON CONFLICT DO NOTHING;

    -- EMPLOYEE intentionally holds no administrative permission. Self-service
    -- access is authorised by ownership ("is this my own record?"), not by a
    -- permission grant — otherwise every employee would need a permission that
    -- reads, in effect, "may view employees".
END;
$$;

COMMENT ON FUNCTION provision_tenant_defaults(uuid) IS
    'Seeds default roles, permissions and password policy for a new tenant. Idempotent.';

-- -----------------------------------------------------------------------------
-- Email domain mapping, for resolving an organisation from a work email address.
--
-- Lets a user type their work email on the first screen instead of hunting for an
-- org code — and means we never ask them for a "service URL", which is a common
-- source of support load in comparable products.
-- -----------------------------------------------------------------------------
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS email_domains text[] NOT NULL DEFAULT '{}';

CREATE INDEX IF NOT EXISTS ix_tenant_email_domains ON tenant USING gin (email_domains);

COMMENT ON COLUMN tenant.email_domains IS
    'Verified email domains that resolve to this tenant, e.g. {acme.com, acme.lk}. Must be verified before use — an unverified domain would let anyone claim another organisation.';
