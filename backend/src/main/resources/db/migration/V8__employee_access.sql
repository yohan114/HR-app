-- =============================================================================
-- V8 — Employee access permissions and default role grants
--
-- V6 added the employee permission catalogue, but `provision_tenant_defaults`
-- was written in V4 with a fixed list that predates it. A tenant provisioned
-- today therefore gets an HR_ADMIN role that cannot open an employee record.
-- This fixes that and adds the one permission the catalogue was missing.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- `employee.view` alone means "within the caller's data scope", which for a
-- manager is their reporting line. Seeing the whole organisation is a different
-- and much larger authority, so it gets its own key rather than being implied by
-- the absence of a scope — an unscoped grant is too easy to create by accident.
-- -----------------------------------------------------------------------------
INSERT INTO permission (key, module, description) VALUES
    ('employee.view.all', 'employee', 'View every employee record, regardless of reporting line')
ON CONFLICT (key) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Re-seed the default role grants.
--
-- Note what HR_ADMIN does *not* get: employee.salary.view, employee.bank.view
-- and employee.document.view. Maintaining employee records and handling
-- identity documents are separable duties, and a customer who wants them
-- separated cannot get there if the default role already holds both. Granting
-- is a one-click addition; discovering that your HR team has been able to read
-- everyone's passport numbers for a year is not recoverable.
-- -----------------------------------------------------------------------------
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
    -- ADMIN gets everything currently in the catalogue. Still a snapshot, not a
    -- wildcard: a permission added by a later migration is NOT granted
    -- retroactively, because a new capability should be an explicit decision by
    -- the customer rather than something that silently appears on a role.
    INSERT INTO role_permission (tenant_id, role_id, permission_key)
    SELECT target_tenant, admin_role_id, p.key FROM permission p
    ON CONFLICT DO NOTHING;

    INSERT INTO role_permission (tenant_id, role_id, permission_key)
    SELECT target_tenant, hr_admin_role_id, p.key
    FROM permission p
    WHERE p.key IN ('identity.user.view', 'identity.user.manage',
                    'identity.device.view', 'identity.device.revoke',
                    'platform.audit.view',
                    'org.structure.view', 'org.reference.view',
                    'config.field.view',
                    'employee.view', 'employee.view.all', 'employee.manage',
                    'employee.directory')
    ON CONFLICT DO NOTHING;

    -- MANAGER gets employee.view without employee.view.all, so the reporting-line
    -- check in EmployeeService is what decides whose records they can open.
    INSERT INTO role_permission (tenant_id, role_id, permission_key)
    SELECT target_tenant, manager_role_id, p.key
    FROM permission p
    WHERE p.key IN ('identity.user.view',
                    'org.structure.view', 'org.reference.view',
                    'employee.view', 'employee.directory')
    ON CONFLICT DO NOTHING;

    -- EMPLOYEE holds only the directory. Access to their own record is
    -- authorised by ownership, not by a permission grant — otherwise every
    -- employee would need a permission that reads, in effect, "may view
    -- employees", and that same permission would be the one gating access to
    -- everyone else's records.
    INSERT INTO role_permission (tenant_id, role_id, permission_key)
    SELECT target_tenant, employee_role_id, p.key
    FROM permission p
    WHERE p.key IN ('employee.directory', 'org.reference.view')
    ON CONFLICT DO NOTHING;
END;
$$;

COMMENT ON FUNCTION provision_tenant_defaults(uuid) IS
    'Seeds default roles, permissions and password policy for a new tenant. Idempotent.';

-- -----------------------------------------------------------------------------
-- Backfill tenants provisioned before this migration.
--
-- Without it, existing tenants keep the V4 grants and their HR administrators
-- stay locked out of employee records. Idempotent, so re-running is harmless.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    t uuid;
BEGIN
    FOR t IN SELECT id FROM tenant LOOP
        PERFORM provision_tenant_defaults(t);
    END LOOP;
END;
$$;

COMMENT ON COLUMN field_permission.access IS
    'HIDDEN | MASKED | READ | WRITE. Absent means the default applies: READ for ordinary fields, HIDDEN for those in FieldPermissionResolver.ALWAYS_SENSITIVE.';
