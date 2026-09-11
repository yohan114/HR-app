-- ============================================================================
-- V23: Enterprise Document Management, Templates & Digital E-Signatures
-- Module 4.6 - Feature-Credible Document Vault, Template Letter Generation & Sign-offs
-- ============================================================================

-- 1. Document Folders Hierarchy
CREATE TABLE document_folder (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    parent_id         uuid REFERENCES document_folder(id) ON DELETE CASCADE,
    folder_name       varchar(100) NOT NULL,
    path              varchar(500) NOT NULL,
    access_scope      varchar(50) NOT NULL DEFAULT 'PUBLIC', -- 'PUBLIC', 'ROLE_RESTRICTED', 'CONFIDENTIAL'
    is_system         boolean NOT NULL DEFAULT false,
    created_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by        uuid,
    updated_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by        uuid,
    version           bigint NOT NULL DEFAULT 0,
    CONSTRAINT document_folder_scope_valid CHECK (access_scope IN ('PUBLIC', 'ROLE_RESTRICTED', 'CONFIDENTIAL'))
);

CREATE INDEX ix_document_folder_tenant_parent ON document_folder (tenant_id, parent_id);
CREATE INDEX ix_document_folder_tenant_path ON document_folder (tenant_id, path);
SELECT apply_tenant_rls('document_folder');

-- 2. Document Taxonomy Tags
CREATE TABLE document_tag (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    tag_name          varchar(50) NOT NULL,
    color             varchar(20) NOT NULL DEFAULT '#1976D2',
    created_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by        uuid,
    updated_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by        uuid,
    version           bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_document_tag_tenant_name UNIQUE (tenant_id, tag_name)
);

CREATE INDEX ix_document_tag_tenant ON document_tag (tenant_id);
SELECT apply_tenant_rls('document_tag');

-- 3. Company Document Master Catalog
CREATE TABLE company_document (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    folder_id              uuid REFERENCES document_folder(id) ON DELETE SET NULL,
    employee_id            uuid REFERENCES employee(id) ON DELETE CASCADE, -- NULL for company-wide; set for employee vault
    title                  varchar(255) NOT NULL,
    description            text,
    document_category      varchar(50) NOT NULL DEFAULT 'GENERAL', -- 'POLICY', 'CONTRACT', 'LETTER', 'CERTIFICATE', 'FORM', 'GENERAL'
    current_version_number int NOT NULL DEFAULT 1,
    file_name              varchar(255) NOT NULL,
    file_size_bytes        bigint NOT NULL DEFAULT 0,
    mime_type              varchar(100) NOT NULL DEFAULT 'application/pdf',
    storage_key            varchar(500) NOT NULL,
    checksum_sha256        varchar(64),
    status                 varchar(50) NOT NULL DEFAULT 'PUBLISHED', -- 'DRAFT', 'PUBLISHED', 'ARCHIVED', 'TRASHED'
    is_confidential        boolean NOT NULL DEFAULT false,
    retention_until        date,
    created_by             uuid REFERENCES employee(id) ON DELETE SET NULL,
    created_at             timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at             timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by             uuid,
    version                bigint NOT NULL DEFAULT 0,
    CONSTRAINT company_document_category_valid CHECK (document_category IN ('POLICY', 'CONTRACT', 'LETTER', 'CERTIFICATE', 'FORM', 'GENERAL')),
    CONSTRAINT company_document_status_valid CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'TRASHED'))
);

CREATE INDEX ix_company_document_tenant_folder ON company_document (tenant_id, folder_id);
CREATE INDEX ix_company_document_tenant_employee ON company_document (tenant_id, employee_id);
CREATE INDEX ix_company_document_tenant_status ON company_document (tenant_id, status);
SELECT apply_tenant_rls('company_document');

-- 4. Document Version History
CREATE TABLE document_version (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    document_id       uuid NOT NULL REFERENCES company_document(id) ON DELETE CASCADE,
    version_number    int NOT NULL,
    file_name         varchar(255) NOT NULL,
    file_size_bytes   bigint NOT NULL,
    storage_key       varchar(500) NOT NULL,
    checksum_sha256   varchar(64),
    changelog         text,
    uploaded_by       uuid REFERENCES employee(id) ON DELETE SET NULL,
    created_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by        uuid,
    updated_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by        uuid,
    version           bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_document_version_tenant_doc_num UNIQUE (tenant_id, document_id, version_number)
);

CREATE INDEX ix_document_version_tenant_doc ON document_version (tenant_id, document_id);
SELECT apply_tenant_rls('document_version');

-- 5. Document Tag Mapping
CREATE TABLE document_tag_mapping (
    tenant_id         uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    document_id       uuid NOT NULL REFERENCES company_document(id) ON DELETE CASCADE,
    tag_id            uuid NOT NULL REFERENCES document_tag(id) ON DELETE CASCADE,
    created_at        timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (tenant_id, document_id, tag_id)
);

CREATE INDEX ix_document_tag_mapping_tenant_doc ON document_tag_mapping (tenant_id, document_id);
CREATE INDEX ix_document_tag_mapping_tenant_tag ON document_tag_mapping (tenant_id, tag_id);
SELECT apply_tenant_rls('document_tag_mapping');

-- 6. Document Templates (with Variable Interpolation)
CREATE TABLE document_template (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    template_code       varchar(100) NOT NULL,
    title               varchar(255) NOT NULL,
    category            varchar(50) NOT NULL DEFAULT 'HR_LETTER', -- 'HR_LETTER', 'CONTRACT', 'POLICY', 'CERTIFICATE'
    content_template    text NOT NULL,
    placeholders        jsonb NOT NULL DEFAULT '[]',
    requires_signature  boolean NOT NULL DEFAULT true,
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by          uuid,
    updated_at          timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by          uuid,
    version             bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_document_template_tenant_code UNIQUE (tenant_id, template_code),
    CONSTRAINT document_template_category_valid CHECK (category IN ('HR_LETTER', 'CONTRACT', 'POLICY', 'CERTIFICATE'))
);

CREATE INDEX ix_document_template_tenant_category ON document_template (tenant_id, category);
SELECT apply_tenant_rls('document_template');

-- 7. Letter Requests (Self-Service Letters)
CREATE TABLE letter_request (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    request_number        varchar(50) NOT NULL,
    employee_id           uuid NOT NULL REFERENCES employee(id) ON DELETE CASCADE,
    template_id           uuid NOT NULL REFERENCES document_template(id) ON DELETE RESTRICT,
    purpose               varchar(255) NOT NULL,
    addressee             varchar(255) NOT NULL,
    specific_instructions text,
    status                varchar(50) NOT NULL DEFAULT 'SUBMITTED', -- 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'GENERATED', 'REJECTED'
    generated_document_id uuid REFERENCES company_document(id) ON DELETE SET NULL,
    approver_id           uuid REFERENCES employee(id) ON DELETE SET NULL,
    approval_remarks      text,
    approved_at           timestamptz,
    created_at            timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by            uuid,
    updated_at            timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by            uuid,
    version               bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_letter_request_tenant_number UNIQUE (tenant_id, request_number),
    CONSTRAINT letter_request_status_valid CHECK (status IN ('SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'GENERATED', 'REJECTED'))
);

CREATE INDEX ix_letter_request_tenant_employee ON letter_request (tenant_id, employee_id);
CREATE INDEX ix_letter_request_tenant_status ON letter_request (tenant_id, status);
SELECT apply_tenant_rls('letter_request');

-- 8. Signature Requests (E-Signature Workflows)
CREATE TABLE signature_request (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    document_id        uuid NOT NULL REFERENCES company_document(id) ON DELETE CASCADE,
    title              varchar(255) NOT NULL,
    workflow_type      varchar(50) NOT NULL DEFAULT 'PARALLEL', -- 'PARALLEL', 'SEQUENTIAL'
    status             varchar(50) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'PARTIALLY_SIGNED', 'COMPLETED', 'DECLINED', 'EXPIRED'
    due_date           date,
    requested_by       uuid NOT NULL REFERENCES employee(id) ON DELETE RESTRICT,
    completed_at       timestamptz,
    created_at         timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by         uuid,
    updated_at         timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by         uuid,
    version            bigint NOT NULL DEFAULT 0,
    CONSTRAINT signature_request_workflow_valid CHECK (workflow_type IN ('PARALLEL', 'SEQUENTIAL')),
    CONSTRAINT signature_request_status_valid CHECK (status IN ('PENDING', 'PARTIALLY_SIGNED', 'COMPLETED', 'DECLINED', 'EXPIRED'))
);

CREATE INDEX ix_signature_request_tenant_doc ON signature_request (tenant_id, document_id);
CREATE INDEX ix_signature_request_tenant_status ON signature_request (tenant_id, status);
SELECT apply_tenant_rls('signature_request');

-- 9. Signature Signers
CREATE TABLE signature_signer (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    request_id           uuid NOT NULL REFERENCES signature_request(id) ON DELETE CASCADE,
    signer_employee_id   uuid REFERENCES employee(id) ON DELETE SET NULL,
    signer_name          varchar(255) NOT NULL,
    signer_email         varchar(255) NOT NULL,
    signing_order        int NOT NULL DEFAULT 1,
    status               varchar(50) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'SIGNED', 'DECLINED'
    signature_method     varchar(50), -- 'DRAWN', 'TYPED', 'CERTIFICATE'
    signature_data       text, -- Base64 PNG or SVG stroke path
    ip_address           varchar(45),
    user_agent           varchar(255),
    signed_at            timestamptz,
    decline_reason       text,
    created_at           timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by           uuid,
    updated_at           timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by           uuid,
    version              bigint NOT NULL DEFAULT 0,
    CONSTRAINT signature_signer_status_valid CHECK (status IN ('PENDING', 'SIGNED', 'DECLINED')),
    CONSTRAINT signature_signer_method_valid CHECK (signature_method IS NULL OR signature_method IN ('DRAWN', 'TYPED', 'CERTIFICATE'))
);

CREATE INDEX ix_signature_signer_tenant_req ON signature_signer (tenant_id, request_id);
CREATE INDEX ix_signature_signer_tenant_employee ON signature_signer (tenant_id, signer_employee_id);
SELECT apply_tenant_rls('signature_signer');

-- 10. Tamper-Evident Cryptographic Signature Audit Log
CREATE TABLE signature_audit_log (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    request_id           uuid NOT NULL REFERENCES signature_request(id) ON DELETE CASCADE,
    event_type           varchar(50) NOT NULL, -- 'CREATED', 'VIEWED', 'SIGNED', 'DECLINED', 'COMPLETED'
    actor_id             uuid REFERENCES employee(id) ON DELETE SET NULL,
    actor_name           varchar(255) NOT NULL,
    ip_address           varchar(45),
    document_hash_sha256 varchar(64) NOT NULL,
    details              jsonb,
    created_at           timestamptz NOT NULL DEFAULT clock_timestamp(),
    created_by           uuid,
    updated_at           timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_by           uuid,
    version              bigint NOT NULL DEFAULT 0,
    CONSTRAINT signature_audit_event_valid CHECK (event_type IN ('CREATED', 'VIEWED', 'SIGNED', 'DECLINED', 'COMPLETED'))
);

CREATE INDEX ix_signature_audit_log_tenant_req ON signature_audit_log (tenant_id, request_id);
SELECT apply_tenant_rls('signature_audit_log');

-- ============================================================================
-- Demonstration Fixtures for Acme Corp (00000000-0000-0000-0000-000000000001)
-- ============================================================================

DO $$
DECLARE
    v_tenant_id uuid := '00000000-0000-0000-0000-000000000001';
    v_kasun_id  uuid;
    v_amanda_id uuid;
    v_f_policies uuid := gen_random_uuid();
    v_f_contracts uuid := gen_random_uuid();
    v_f_letters uuid := gen_random_uuid();
    v_f_vault   uuid := gen_random_uuid();
    v_tag_policy uuid := gen_random_uuid();
    v_tag_conf   uuid := gen_random_uuid();
    v_tag_tax    uuid := gen_random_uuid();
    v_tag_stat   uuid := gen_random_uuid();
    v_doc_handbook uuid := gen_random_uuid();
    v_doc_infosec  uuid := gen_random_uuid();
    v_doc_contract uuid := gen_random_uuid();
    v_tmpl_service uuid := gen_random_uuid();
    v_tmpl_visa    uuid := gen_random_uuid();
    v_tmpl_salary  uuid := gen_random_uuid();
    v_sig_req_id   uuid := gen_random_uuid();
BEGIN
    SELECT id INTO v_kasun_id FROM employee WHERE tenant_id = v_tenant_id AND work_email = 'kasun.mendis@acme.corp' LIMIT 1;
    SELECT id INTO v_amanda_id FROM employee WHERE tenant_id = v_tenant_id AND work_email = 'amanda.jayawardena@acme.corp' LIMIT 1;

    IF v_kasun_id IS NOT NULL THEN
        -- 1. Folders
        INSERT INTO document_folder (id, tenant_id, parent_id, folder_name, path, access_scope, is_system)
        VALUES
            (v_f_policies, v_tenant_id, NULL, 'Company Policies & Guidelines', '/Company Policies & Guidelines', 'PUBLIC', true),
            (v_f_contracts, v_tenant_id, NULL, 'Employment Contracts & Agreements', '/Employment Contracts & Agreements', 'ROLE_RESTRICTED', true),
            (v_f_letters, v_tenant_id, NULL, 'HR Letters & Statements', '/HR Letters & Statements', 'PUBLIC', true),
            (v_f_vault, v_tenant_id, NULL, 'Personal Vault (Kasun Mendis)', '/Personal Vault (Kasun Mendis)', 'CONFIDENTIAL', true);

        -- 2. Tags
        INSERT INTO document_tag (id, tenant_id, tag_name, color)
        VALUES
            (v_tag_policy, v_tenant_id, 'Policy', '#1976D2'),
            (v_tag_conf, v_tenant_id, 'Confidential', '#D32F2F'),
            (v_tag_tax, v_tenant_id, 'Tax & Statutory', '#388E3C'),
            (v_tag_stat, v_tenant_id, 'Compliance', '#F57C00')
        ON CONFLICT (tenant_id, tag_name) DO NOTHING;

        -- 3. Company Documents
        INSERT INTO company_document (id, tenant_id, folder_id, employee_id, title, description, document_category, current_version_number, file_name, file_size_bytes, mime_type, storage_key, checksum_sha256, status, is_confidential, retention_until, created_by)
        VALUES
            (v_doc_handbook, v_tenant_id, v_f_policies, NULL, 'Acme Global Employee Handbook 2026', 'Comprehensive operational policies, values, leave protocols, and ethics charter.', 'POLICY', 2, 'acme_employee_handbook_2026_v2.pdf', 2458112, 'application/pdf', 's3://acme-documents/policies/handbook_2026_v2.pdf', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', 'PUBLISHED', false, NULL, v_amanda_id),
            (v_doc_infosec, v_tenant_id, v_f_policies, NULL, 'Information Security & Acceptable Use Policy v3.1', 'Security hygiene, device encryption, zero-trust controls, and remote work safety guidelines.', 'POLICY', 1, 'infosec_policy_v3_1.pdf', 1048576, 'application/pdf', 's3://acme-documents/policies/infosec_policy_v3_1.pdf', '4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a', 'PUBLISHED', false, NULL, v_amanda_id),
            (v_doc_contract, v_tenant_id, v_f_vault, v_kasun_id, 'Principal Software Engineer Contract — Kasun Mendis', 'Official signed full-time employment agreement and intellectual property assignment.', 'CONTRACT', 1, 'contract_kasun_mendis_2026.pdf', 524288, 'application/pdf', 's3://acme-documents/contracts/contract_kasun_mendis.pdf', 'a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e', 'PUBLISHED', true, '2036-12-31', v_amanda_id);

        -- 4. Versions
        INSERT INTO document_version (id, tenant_id, document_id, version_number, file_name, file_size_bytes, storage_key, checksum_sha256, changelog, uploaded_by)
        VALUES
            (gen_random_uuid(), v_tenant_id, v_doc_handbook, 1, 'acme_employee_handbook_2026_v1.pdf', 2150000, 's3://acme-documents/policies/handbook_2026_v1.pdf', '8a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b', 'Initial 2026 version release.', v_amanda_id),
            (gen_random_uuid(), v_tenant_id, v_doc_handbook, 2, 'acme_employee_handbook_2026_v2.pdf', 2458112, 's3://acme-documents/policies/handbook_2026_v2.pdf', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', 'Added hybrid working stipend provisions and updated paternity leave to 15 days.', v_amanda_id),
            (gen_random_uuid(), v_tenant_id, v_doc_infosec, 1, 'infosec_policy_v3_1.pdf', 1048576, 's3://acme-documents/policies/infosec_policy_v3_1.pdf', '4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a', 'Standard annual review.', v_amanda_id),
            (gen_random_uuid(), v_tenant_id, v_doc_contract, 1, 'contract_kasun_mendis_2026.pdf', 524288, 's3://acme-documents/contracts/contract_kasun_mendis.pdf', 'a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e', 'Executed contract.', v_amanda_id);

        -- 5. Tag Mappings
        INSERT INTO document_tag_mapping (tenant_id, document_id, tag_id)
        VALUES
            (v_tenant_id, v_doc_handbook, v_tag_policy),
            (v_tenant_id, v_doc_infosec, v_tag_policy),
            (v_tenant_id, v_doc_infosec, v_tag_stat),
            (v_tenant_id, v_doc_contract, v_tag_conf)
        ON CONFLICT DO NOTHING;

        -- 6. Document Templates
        INSERT INTO document_template (id, tenant_id, template_code, title, category, content_template, placeholders, requires_signature, is_active)
        VALUES
            (v_tmpl_service, v_tenant_id, 'SERVICE_LETTER', 'Certificate of Employment & Service Letter', 'HR_LETTER',
             'TO WHOMSOEVER IT MAY CONCERN\n\nThis is to certify that {{employee.name}} (Employee Code: {{employee.code}}) has been employed with Acme Corporation since {{joinDate}}. {{employee.name}} currently holds the position of {{designation}} in the {{department}} Department.\n\nDuring their tenure, {{employee.name}} has demonstrated exceptional diligence, professional dedication, and integrity. We wish them continued success in all their future undertakings.\n\nYours faithfully,\nAcme Corporation Management\nAuthorized Signatory: {{signatoryName}}',
             '["employee.name", "employee.code", "joinDate", "designation", "department", "signatoryName"]'::jsonb, true, true),

            (v_tmpl_visa, v_tenant_id, 'VISA_EMBASSY_LETTER', 'Embassy Visa Application Verification Letter', 'HR_LETTER',
             'Date: {{currentDate}}\n\nTo:\n{{addressee}}\n\nDear Sir / Madam,\n\nRE: EMPLOYMENT AND LEAVE VERIFICATION FOR {{employee.name}}\n\nWe write to formally confirm that {{employee.name}} (National ID / Passport: {{employee.nic}}, Employee Code: {{employee.code}}) is a permanent full-time employee of Acme Corporation, serving as {{designation}} since {{joinDate}}.\n\n{{employee.name}} receives an annual basic compensation of LKR {{annualSalary}}. They have been granted approved annual leave from {{leaveStartDate}} to {{leaveEndDate}} to undertake personal travel, and are scheduled to resume their standard employment duties on {{resumeDate}}.\n\nAcme Corporation guarantees their continuous employment upon return. Please do not hesitate to contact our HR Operations desk at hr@acme.corp for any further verification.\n\nSincerely,\nHR Director, Acme Corporation',
             '["currentDate", "addressee", "employee.name", "employee.nic", "employee.code", "designation", "joinDate", "annualSalary", "leaveStartDate", "leaveEndDate", "resumeDate"]'::jsonb, true, true),

            (v_tmpl_salary, v_tenant_id, 'SALARY_VERIFICATION', 'Bank Financial & Salary Verification Statement', 'HR_LETTER',
             'Date: {{currentDate}}\n\nTo: The Branch Manager, {{addressee}}\n\nCONFIDENTIAL SALARY VERIFICATION FOR {{employee.name}}\n\nWe hereby verify that {{employee.name}} is actively employed as a permanent {{designation}} at Acme Corporation.\n\nMonthly Remuneration Summary:\n- Basic Salary: LKR {{salaryBasic}}\n- Fixed Monthly Allowances: LKR {{salaryAllowances}}\n- Gross Monthly Remuneration: LKR {{salaryGross}}\n\nSalary is electronically remitted via direct bank transfer on the 25th calendar day of each month to Account {{employee.accountNumber}} at {{employee.bankName}}.\n\nAuthorized by:\nHead of Compensation & Benefits',
             '["currentDate", "addressee", "employee.name", "designation", "salaryBasic", "salaryAllowances", "salaryGross", "employee.accountNumber", "employee.bankName"]'::jsonb, true, true)
        ON CONFLICT (tenant_id, template_code) DO NOTHING;

        -- 7. Letter Request (Pending/Approved for Kasun)
        INSERT INTO letter_request (id, tenant_id, request_number, employee_id, template_id, purpose, addressee, specific_instructions, status, generated_document_id, approver_id, approval_remarks, approved_at)
        VALUES
            (gen_random_uuid(), v_tenant_id, 'LTR-2026-001', v_kasun_id, v_tmpl_visa,
             'Personal tourist travel visa application for UK holiday conference.',
             'The Visa Section, British High Commission, Colombo',
             'Please state annual leave dates from 10th October to 24th October 2026.',
             'APPROVED', NULL, v_amanda_id, 'Approved as requested. Annual leave balance verified.', clock_timestamp())
        ON CONFLICT (tenant_id, request_number) DO NOTHING;

        -- 8. Signature Request (Awaiting Kasun's sign-off)
        INSERT INTO signature_request (id, tenant_id, document_id, title, workflow_type, status, due_date, requested_by)
        VALUES
            (v_sig_req_id, v_tenant_id, v_doc_infosec, 'Annual Information Security Policy Acknowledgment (2026)', 'PARALLEL', 'PENDING', CURRENT_DATE + 14, v_amanda_id);

        INSERT INTO signature_signer (id, tenant_id, request_id, signer_employee_id, signer_name, signer_email, signing_order, status)
        VALUES
            (gen_random_uuid(), v_tenant_id, v_sig_req_id, v_kasun_id, 'Kasun Mendis', 'kasun.mendis@acme.corp', 1, 'PENDING');

        INSERT INTO signature_audit_log (id, tenant_id, request_id, event_type, actor_id, actor_name, ip_address, document_hash_sha256, details)
        VALUES
            (gen_random_uuid(), v_tenant_id, v_sig_req_id, 'CREATED', v_amanda_id, 'Amanda Jayawardena', '192.168.1.10', '4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a', '{"message": "Signature request initiated for all senior engineers."}'::jsonb);

    END IF;
END $$;
