-- =============================================================================
-- V28: Comprehensive Permission Catalogue, Extended Roles & Provisioning
--
-- Defines permission keys for all 15 modules following the module.resource.action
-- convention and extends the tenant role model with FINANCE, RECRUITER, and AUDITOR.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Permission Catalogue
-- -----------------------------------------------------------------------------
INSERT INTO permission (key, module, description) VALUES
    -- Leave
    ('leave.policy.view',          'leave',        'View leave policies and types'),
    ('leave.policy.manage',        'leave',        'Create and modify leave policies'),
    ('leave.balance.view',         'leave',        'View leave balances'),
    ('leave.balance.manage',       'leave',        'Allocate and adjust leave balances'),
    ('leave.request.view',         'leave',        'View leave applications'),
    ('leave.request.create',       'leave',        'Submit a leave application'),
    ('leave.request.approve',      'leave',        'Approve or reject leave applications'),

    -- Payroll
    ('payroll.view',               'payroll',      'View payroll overview and pay groups'),
    ('payroll.config.view',        'payroll',      'View pay groups and salary structures'),
    ('payroll.config.manage',      'payroll',      'Manage pay groups and formula items'),
    ('payroll.run.view',           'payroll',      'View payroll runs and calculation details'),
    ('payroll.run.manage',         'payroll',      'Execute, process, and finalize payroll runs'),
    ('payroll.payslip.view',       'payroll',      'View employee payslips'),
    ('payroll.payslip.manage',     'payroll',      'Publish and manage payslips'),
    ('payroll.report.view',        'payroll',      'View and export payroll reports and bank advice'),

    -- Attendance
    ('attendance.record.view',     'attendance',   'View attendance punches and daily summaries'),
    ('attendance.record.manage',   'attendance',   'Adjust attendance records and resolve exceptions'),
    ('attendance.punch.create',    'attendance',   'Submit clock-in and clock-out punches'),
    ('attendance.shift.view',      'attendance',   'View shift rosters and schedules'),
    ('attendance.shift.manage',    'attendance',   'Configure shifts and assign rosters'),
    ('attendance.device.view',     'attendance',   'View biometric attendance devices'),
    ('attendance.device.manage',   'attendance',   'Register and configure biometric devices'),

    -- Loans
    ('loan.type.view',             'loan',         'View loan products and terms'),
    ('loan.type.manage',           'loan',         'Configure loan products'),
    ('loan.request.view',          'loan',         'View loan applications and active loans'),
    ('loan.request.create',        'loan',         'Apply for an employee loan'),
    ('loan.request.approve',       'loan',         'Approve or reject loan applications'),
    ('loan.settle',                'loan',         'Process early loan settlements'),

    -- Expenses
    ('expense.type.view',          'expense',      'View expense categories and policies'),
    ('expense.type.manage',        'expense',      'Configure expense categories'),
    ('expense.claim.view',         'expense',      'View expense claims'),
    ('expense.claim.create',       'expense',      'Submit an expense claim'),
    ('expense.claim.approve',      'expense',      'Approve or reject expense claims'),
    ('expense.claim.reimburse',    'expense',      'Process reimbursement payments'),

    -- Benefits
    ('benefit.plan.view',          'benefit',      'View benefit plans'),
    ('benefit.plan.manage',        'benefit',      'Configure benefit plans and eligibility'),
    ('benefit.enrolment.view',     'benefit',      'View benefit enrolments and claims'),
    ('benefit.enrolment.manage',   'benefit',      'Manage benefit enrolments and adjudicate claims'),

    -- Employee Lifecycle
    ('lifecycle.movement.view',    'lifecycle',    'View employee promotions, transfers, and status changes'),
    ('lifecycle.movement.manage',  'lifecycle',    'Initiate and process employee movements'),
    ('lifecycle.probation.view',   'lifecycle',    'View probation statuses and reviews'),
    ('lifecycle.probation.manage', 'lifecycle',    'Complete and confirm probation evaluations'),

    -- Disciplinary & Grievance
    ('disciplinary.case.view',     'disciplinary', 'View disciplinary cases'),
    ('disciplinary.case.manage',   'disciplinary', 'Open, investigate, and close disciplinary cases'),
    ('disciplinary.grievance.view', 'disciplinary', 'View employee grievances'),
    ('disciplinary.grievance.manage', 'disciplinary', 'Manage and resolve grievance submissions'),

    -- Performance & Goals
    ('performance.cycle.view',     'performance',  'View performance appraisal cycles'),
    ('performance.cycle.manage',   'performance',  'Configure appraisal cycles, templates, and scales'),
    ('performance.review.view',    'performance',  'View performance reviews and ratings'),
    ('performance.review.manage',  'performance',  'Submit and finalize performance reviews'),
    ('performance.goal.view',      'performance',  'View performance goals and OKRs'),
    ('performance.goal.manage',    'performance',  'Assign and track goals'),

    -- Recruitment & ATS
    ('recruitment.job.view',       'recruitment',  'View job requisitions and postings'),
    ('recruitment.job.manage',     'recruitment',  'Create and publish job requisitions'),
    ('recruitment.candidate.view', 'recruitment',  'View candidate applications and resumes'),
    ('recruitment.candidate.manage', 'recruitment', 'Manage applicant pipeline, stages, and interviews'),
    ('recruitment.offer.manage',   'recruitment',  'Issue job offers and compensation packages'),

    -- Onboarding & Offboarding
    ('onboarding.task.view',       'onboarding',   'View onboarding workflows and tasks'),
    ('onboarding.task.manage',     'onboarding',   'Assign and complete onboarding task lists'),
    ('offboarding.task.view',      'onboarding',   'View offboarding workflows and clearance'),
    ('offboarding.task.manage',    'onboarding',   'Process employee exit clearance'),

    -- Documents & Signatures
    ('document.template.view',     'document',     'View document templates'),
    ('document.template.manage',   'document',     'Create and edit document templates'),
    ('document.employee.view',     'document',     'View employee documents and files'),
    ('document.employee.manage',   'document',     'Upload, verify, and delete employee documents'),
    ('document.signature.view',    'document',     'View e-signature requests and status'),
    ('document.signature.manage',  'document',     'Initiate and sign electronic documents'),

    -- Training & Development
    ('training.course.view',       'training',     'View training course catalogue'),
    ('training.course.manage',     'training',     'Manage courses, competencies, and materials'),
    ('training.schedule.view',     'training',     'View training schedules and sessions'),
    ('training.schedule.manage',   'training',     'Schedule training sessions and trainers'),
    ('training.enrolment.view',    'training',     'View training enrolments and completions'),
    ('training.enrolment.manage',  'training',     'Nominate, enroll, and mark course completion'),

    -- Timesheets & Billing
    ('timesheet.record.view',      'timesheet',    'View timesheets and project time entries'),
    ('timesheet.record.manage',    'timesheet',    'Manage time tracking projects and tasks'),
    ('timesheet.submit',           'timesheet',    'Submit timesheets for approval'),
    ('timesheet.approve',          'timesheet',    'Approve or reject timesheets'),

    -- Biometric Terminals
    ('biometric.device.view',      'biometric',    'View biometric devices'),
    ('biometric.device.manage',    'biometric',    'Register and configure biometric devices'),
    ('biometric.command.manage',   'biometric',    'Queue commands to biometric devices'),

    -- Dashboard
    ('dashboard.view',             'dashboard',    'View role-based dashboard widgets and analytics')
ON CONFLICT (key) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 2. Updated Tenant Provisioning Function
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION provision_tenant_defaults(target_tenant uuid)
    RETURNS void
    LANGUAGE plpgsql
AS $$
DECLARE
    admin_role_id     uuid;
    hr_admin_role_id  uuid;
    manager_role_id   uuid;
    employee_role_id  uuid;
    finance_role_id   uuid;
    recruiter_role_id uuid;
    auditor_role_id   uuid;
BEGIN
    INSERT INTO password_policy (tenant_id)
    VALUES (target_tenant)
    ON CONFLICT (tenant_id) DO NOTHING;

    -- Standard System Roles
    INSERT INTO role (id, tenant_id, key, name, description, is_system)
    VALUES (gen_random_uuid(), target_tenant, 'ADMIN', 'Administrator',
            'Full access to configuration and all tenant records', true)
    ON CONFLICT (tenant_id, key) DO NOTHING;

    INSERT INTO role (id, tenant_id, key, name, description, is_system)
    VALUES (gen_random_uuid(), target_tenant, 'HR_ADMIN', 'HR Administrator',
            'Manages employees, leave, attendance, and talent operations', true)
    ON CONFLICT (tenant_id, key) DO NOTHING;

    INSERT INTO role (id, tenant_id, key, name, description, is_system)
    VALUES (gen_random_uuid(), target_tenant, 'MANAGER', 'Manager',
            'Approves team requests and reviews direct reports', true)
    ON CONFLICT (tenant_id, key) DO NOTHING;

    INSERT INTO role (id, tenant_id, key, name, description, is_system)
    VALUES (gen_random_uuid(), target_tenant, 'EMPLOYEE', 'Employee',
            'Self-service access to own records and applications', true)
    ON CONFLICT (tenant_id, key) DO NOTHING;

    INSERT INTO role (id, tenant_id, key, name, description, is_system)
    VALUES (gen_random_uuid(), target_tenant, 'FINANCE', 'Finance / Payroll Officer',
            'Executes payroll runs, manages statutory items, reimbursements, and loans', true)
    ON CONFLICT (tenant_id, key) DO NOTHING;

    INSERT INTO role (id, tenant_id, key, name, description, is_system)
    VALUES (gen_random_uuid(), target_tenant, 'RECRUITER', 'Recruitment Specialist',
            'Manages candidate pipelines, requisitions, and interviews', true)
    ON CONFLICT (tenant_id, key) DO NOTHING;

    INSERT INTO role (id, tenant_id, key, name, description, is_system)
    VALUES (gen_random_uuid(), target_tenant, 'AUDITOR', 'Auditor',
            'Read-only statutory and compliance auditing access', true)
    ON CONFLICT (tenant_id, key) DO NOTHING;

    SELECT id INTO admin_role_id     FROM role WHERE tenant_id = target_tenant AND key = 'ADMIN';
    SELECT id INTO hr_admin_role_id  FROM role WHERE tenant_id = target_tenant AND key = 'HR_ADMIN';
    SELECT id INTO manager_role_id   FROM role WHERE tenant_id = target_tenant AND key = 'MANAGER';
    SELECT id INTO employee_role_id  FROM role WHERE tenant_id = target_tenant AND key = 'EMPLOYEE';
    SELECT id INTO finance_role_id   FROM role WHERE tenant_id = target_tenant AND key = 'FINANCE';
    SELECT id INTO recruiter_role_id FROM role WHERE tenant_id = target_tenant AND key = 'RECRUITER';
    SELECT id INTO auditor_role_id   FROM role WHERE tenant_id = target_tenant AND key = 'AUDITOR';

    -- ADMIN: All permissions
    INSERT INTO role_permission (tenant_id, role_id, permission_key)
    SELECT target_tenant, admin_role_id, p.key FROM permission p
    ON CONFLICT DO NOTHING;

    -- HR_ADMIN: Core HR operations (excluding payroll execution)
    INSERT INTO role_permission (tenant_id, role_id, permission_key)
    SELECT target_tenant, hr_admin_role_id, p.key
    FROM permission p
    WHERE p.key IN (
        'identity.user.view', 'identity.user.manage',
        'identity.device.view', 'identity.device.revoke',
        'platform.audit.view', 'org.structure.view', 'org.structure.manage',
        'org.reference.view', 'org.reference.manage', 'config.field.view',
        'config.field.manage', 'config.label.manage', 'employee.view',
        'employee.view.all', 'employee.manage', 'employee.directory',
        'employee.document.view', 'notification.template.view', 'notification.template.manage',
        'notification.send', 'leave.policy.view', 'leave.policy.manage',
        'leave.balance.view', 'leave.balance.manage', 'leave.request.view',
        'leave.request.approve', 'attendance.record.view', 'attendance.record.manage',
        'attendance.shift.view', 'attendance.shift.manage', 'attendance.device.view',
        'attendance.device.manage', 'biometric.device.view', 'biometric.device.manage',
        'biometric.command.manage', 'benefit.plan.view', 'benefit.plan.manage',
        'benefit.enrolment.view', 'benefit.enrolment.manage', 'lifecycle.movement.view',
        'lifecycle.movement.manage', 'lifecycle.probation.view', 'lifecycle.probation.manage',
        'disciplinary.case.view', 'disciplinary.case.manage', 'disciplinary.grievance.view',
        'disciplinary.grievance.manage', 'performance.cycle.view', 'performance.cycle.manage',
        'performance.review.view', 'performance.review.manage', 'performance.goal.view',
        'performance.goal.manage', 'recruitment.job.view', 'recruitment.job.manage',
        'recruitment.candidate.view', 'recruitment.candidate.manage', 'recruitment.offer.manage',
        'onboarding.task.view', 'onboarding.task.manage', 'offboarding.task.view',
        'offboarding.task.manage', 'document.template.view', 'document.template.manage',
        'document.employee.view', 'document.employee.manage', 'document.signature.view',
        'document.signature.manage', 'training.course.view', 'training.course.manage',
        'training.schedule.view', 'training.schedule.manage', 'training.enrolment.view',
        'training.enrolment.manage', 'timesheet.record.view', 'timesheet.approve',
        'dashboard.view'
    )
    ON CONFLICT DO NOTHING;

    -- MANAGER: Team approvals and visibility over direct reports
    INSERT INTO role_permission (tenant_id, role_id, permission_key)
    SELECT target_tenant, manager_role_id, p.key
    FROM permission p
    WHERE p.key IN (
        'identity.user.view', 'org.structure.view', 'org.reference.view',
        'employee.view', 'employee.directory', 'leave.request.view',
        'leave.request.approve', 'attendance.record.view', 'attendance.shift.view',
        'expense.claim.view', 'expense.claim.approve', 'timesheet.record.view',
        'timesheet.approve', 'performance.review.view', 'performance.review.manage',
        'performance.goal.view', 'performance.goal.manage', 'dashboard.view'
    )
    ON CONFLICT DO NOTHING;

    -- EMPLOYEE: Self-service applications and personal dashboard
    INSERT INTO role_permission (tenant_id, role_id, permission_key)
    SELECT target_tenant, employee_role_id, p.key
    FROM permission p
    WHERE p.key IN (
        'employee.directory', 'org.reference.view', 'leave.request.create',
        'leave.request.view', 'expense.claim.create', 'expense.claim.view',
        'loan.request.create', 'loan.request.view', 'attendance.punch.create',
        'attendance.record.view', 'timesheet.submit', 'timesheet.record.view',
        'training.course.view', 'training.enrolment.view', 'document.signature.manage',
        'performance.goal.view', 'benefit.enrolment.view', 'dashboard.view'
    )
    ON CONFLICT DO NOTHING;

    -- FINANCE / PAYROLL_OFFICER: Runs payroll, processes claims, manages loans
    INSERT INTO role_permission (tenant_id, role_id, permission_key)
    SELECT target_tenant, finance_role_id, p.key
    FROM permission p
    WHERE p.key IN (
        'org.reference.view', 'employee.directory', 'employee.salary.view',
        'employee.bank.view', 'payroll.view', 'payroll.config.view',
        'payroll.config.manage', 'payroll.run.view', 'payroll.run.manage',
        'payroll.payslip.view', 'payroll.payslip.manage', 'payroll.report.view',
        'expense.claim.view', 'expense.claim.approve', 'expense.claim.reimburse',
        'loan.type.view', 'loan.type.manage', 'loan.request.view',
        'loan.request.approve', 'loan.settle', 'timesheet.record.view',
        'benefit.plan.view', 'benefit.enrolment.view', 'dashboard.view'
    )
    ON CONFLICT DO NOTHING;

    -- RECRUITER: ATS, candidates, job requisitions
    INSERT INTO role_permission (tenant_id, role_id, permission_key)
    SELECT target_tenant, recruiter_role_id, p.key
    FROM permission p
    WHERE p.key IN (
        'org.structure.view', 'org.reference.view', 'employee.directory',
        'recruitment.job.view', 'recruitment.job.manage', 'recruitment.candidate.view',
        'recruitment.candidate.manage', 'recruitment.offer.manage',
        'onboarding.task.view', 'dashboard.view'
    )
    ON CONFLICT DO NOTHING;

    -- AUDITOR: Read-only compliance & audit view
    INSERT INTO role_permission (tenant_id, role_id, permission_key)
    SELECT target_tenant, auditor_role_id, p.key
    FROM permission p
    WHERE p.key LIKE '%.view' OR p.key = 'platform.audit.view'
    ON CONFLICT DO NOTHING;
END;
$$;

-- -----------------------------------------------------------------------------
-- 3. Backfill Existing Tenants
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    t RECORD;
BEGIN
    FOR t IN SELECT id FROM tenant LOOP
        PERFORM provision_tenant_defaults(t.id);
    END LOOP;
END;
$$;
