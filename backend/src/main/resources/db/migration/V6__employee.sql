-- =============================================================================
-- V6 — Employee master
--
-- The centre of the product. Every other module references this table.
--
-- ## Encryption
--
-- National identity numbers and bank account numbers are stored encrypted
-- (columns suffixed `_enc`). pgcrypto is available from V1. The application
-- holds the key, not the database, so a database dump alone does not yield
-- them — which is the threat that matters, since backups travel further than
-- live data.
--
-- Salary is deliberately NOT here. It lives in `employee_salary` (Phase 3),
-- effective-dated, so that "what did they earn in March?" is answerable. A
-- single mutable salary column cannot answer that and quietly makes historical
-- payroll unreproducible.
-- =============================================================================

CREATE TABLE employee
(
    id                        uuid         PRIMARY KEY,
    tenant_id                 uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_code             varchar(64)  NOT NULL,
    company_id                uuid         NOT NULL REFERENCES company (id) ON DELETE RESTRICT,
    status                    varchar(32)  NOT NULL DEFAULT 'ACTIVE',

    -- Personal ------------------------------------------------------------
    title_id                  uuid         REFERENCES employee_title (id) ON DELETE SET NULL,
    first_name                varchar(128) NOT NULL,
    middle_name               varchar(128),
    last_name                 varchar(128) NOT NULL,
    -- Stored rather than derived: naming order differs by market, and several
    -- of our target countries use a mononym or a patronymic that does not
    -- reconstruct from first/last.
    display_name              varchar(255) NOT NULL,
    preferred_name            varchar(128),
    date_of_birth             date,
    gender_type_id            uuid         REFERENCES gender_type (id) ON DELETE SET NULL,
    marital_status_id         uuid         REFERENCES marital_status (id) ON DELETE SET NULL,
    blood_group_id            uuid         REFERENCES blood_group (id) ON DELETE SET NULL,
    nationality_id            uuid         REFERENCES nationality (id) ON DELETE SET NULL,
    religion_id               uuid         REFERENCES religion (id) ON DELETE SET NULL,
    race_id                   uuid         REFERENCES race (id) ON DELETE SET NULL,
    national_id_enc           text,
    photo_key                 varchar(512),

    -- Employment ----------------------------------------------------------
    join_date                 date         NOT NULL,
    confirmation_date         date,
    probation_end_date        date,
    resign_date               date,
    last_working_date         date,
    employment_type_id        uuid         REFERENCES employment_type (id) ON DELETE SET NULL,
    employee_category_id      uuid         REFERENCES employee_category (id) ON DELETE SET NULL,
    employee_group_id         uuid         REFERENCES employee_group (id) ON DELETE SET NULL,
    statutory_classification_id uuid       REFERENCES statutory_classification (id) ON DELETE SET NULL,

    -- Workstation ---------------------------------------------------------
    department_id             uuid         REFERENCES department (id) ON DELETE RESTRICT,
    designation_id            uuid         REFERENCES designation (id) ON DELETE RESTRICT,
    salary_grade_id           uuid         REFERENCES salary_grade (id) ON DELETE SET NULL,
    corporate_title_id        uuid         REFERENCES corporate_title (id) ON DELETE SET NULL,
    location_id               uuid         REFERENCES location (id) ON DELETE RESTRICT,
    cost_centre_id            uuid         REFERENCES cost_centre (id) ON DELETE SET NULL,
    function_id               uuid         REFERENCES function (id) ON DELETE SET NULL,
    supervisor_id             uuid         REFERENCES employee (id) ON DELETE SET NULL,
    -- Matrix reporting. Separate from supervisor_id because approval routing
    -- follows the solid line and only the solid line — an ambiguous approver is
    -- how requests sit unactioned for a week.
    dotted_line_supervisor_id uuid         REFERENCES employee (id) ON DELETE SET NULL,

    -- Contact -------------------------------------------------------------
    personal_email            varchar(320),
    work_email                varchar(320),
    mobile                    varchar(32),
    work_phone                varchar(32),
    permanent_address         jsonb        NOT NULL DEFAULT '{}'::jsonb,
    current_address           jsonb        NOT NULL DEFAULT '{}'::jsonb,

    -- System --------------------------------------------------------------
    custom_fields             jsonb        NOT NULL DEFAULT '{}'::jsonb,
    -- Maintained by trigger below. Directory search reads this rather than
    -- doing ILIKE over four columns.
    search_vector             tsvector,

    created_at                timestamptz  NOT NULL DEFAULT now(),
    created_by                uuid,
    updated_at                timestamptz  NOT NULL DEFAULT now(),
    updated_by                uuid,
    version                   bigint       NOT NULL DEFAULT 0,

    CONSTRAINT employee_status_valid CHECK (
        status IN ('ACTIVE', 'PROBATION', 'SUSPENDED', 'ON_NOTICE', 'EXITED', 'PENDING_JOIN')
    ),
    CONSTRAINT employee_not_own_supervisor CHECK (supervisor_id IS NULL OR supervisor_id <> id),
    CONSTRAINT employee_not_own_dotted_supervisor CHECK (
        dotted_line_supervisor_id IS NULL OR dotted_line_supervisor_id <> id
    ),
    CONSTRAINT employee_dates_ordered CHECK (
        (confirmation_date IS NULL OR confirmation_date >= join_date)
        AND (resign_date IS NULL OR resign_date >= join_date)
        AND (last_working_date IS NULL OR last_working_date >= join_date)
    )
);

-- tenant_id leads every index: RLS appends `tenant_id = current_tenant_id()` to
-- every query, and an index that does not start with it cannot serve that
-- predicate. See ADR 0002.
CREATE UNIQUE INDEX ux_employee_tenant_code       ON employee (tenant_id, employee_code);
CREATE INDEX ix_employee_tenant_status            ON employee (tenant_id, status);
CREATE INDEX ix_employee_tenant_supervisor        ON employee (tenant_id, supervisor_id);
CREATE INDEX ix_employee_tenant_department        ON employee (tenant_id, department_id);
CREATE INDEX ix_employee_tenant_location          ON employee (tenant_id, location_id);
CREATE INDEX ix_employee_tenant_company           ON employee (tenant_id, company_id);
CREATE INDEX ix_employee_search                   ON employee USING gin (search_vector);
CREATE INDEX ix_employee_custom_fields            ON employee USING gin (custom_fields);
-- Work email is how SSO and email-based lookup find someone; unique per tenant.
CREATE UNIQUE INDEX ux_employee_tenant_work_email ON employee (tenant_id, lower(work_email))
    WHERE work_email IS NOT NULL;

SELECT apply_tenant_rls('employee');

-- -----------------------------------------------------------------------------
-- Search vector
--
-- Weighted so a name match outranks a code match, and both outrank an email
-- match. Without weighting, searching "Silva" returns everyone whose email
-- happens to contain it before the person actually called Silva.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION employee_search_vector_update()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('simple', coalesce(NEW.display_name, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(NEW.preferred_name, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(NEW.first_name, '') || ' ' || coalesce(NEW.last_name, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(NEW.employee_code, '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(NEW.work_email, '')), 'C');
    RETURN NEW;
END;
$$;

-- 'simple' rather than 'english': stemming an English dictionary over Sinhala,
-- Tamil, Bahasa and Arabic names produces nonsense matches, and nobody searches
-- a directory expecting stemming.
CREATE TRIGGER employee_search_vector
    BEFORE INSERT OR UPDATE OF display_name, preferred_name, first_name, last_name, employee_code, work_email
    ON employee
    FOR EACH ROW
EXECUTE FUNCTION employee_search_vector_update();

-- -----------------------------------------------------------------------------
-- Reporting hierarchy
--
-- A materialised ltree path per employee, maintained by trigger.
--
-- Why not walk supervisor_id recursively at query time: "everyone under this
-- manager" is asked on nearly every screen a manager opens — team attendance,
-- team leave, approvals, the org chart. A recursive CTE per request against a
-- 10,000-employee tenant is the kind of query that looks fine in development
-- and falls over in production.
--
-- An ltree path answers it with a single indexed prefix match.
-- -----------------------------------------------------------------------------
CREATE TABLE employee_hierarchy
(
    tenant_id   uuid    NOT NULL,
    employee_id uuid    NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    path        ltree   NOT NULL,
    depth       integer NOT NULL,

    PRIMARY KEY (employee_id)
);

CREATE INDEX ix_employee_hierarchy_path   ON employee_hierarchy USING gist (path);
CREATE INDEX ix_employee_hierarchy_tenant ON employee_hierarchy (tenant_id, depth);
SELECT apply_tenant_rls('employee_hierarchy');

COMMENT ON COLUMN employee_hierarchy.path IS
    'Dotted path of ancestor ids from the root, e.g. n<uuid>.n<uuid>. Prefixed with n because an ltree label cannot start with a digit.';

/**
 * Rebuilds the hierarchy path for an employee and everyone beneath them.
 *
 * Called on insert and whenever supervisor_id changes. Re-parenting a manager
 * with 200 reports rewrites 200 rows — acceptable because it happens rarely,
 * whereas reading the subtree happens constantly.
 */
CREATE OR REPLACE FUNCTION rebuild_employee_hierarchy(target_employee uuid)
    RETURNS void
    LANGUAGE plpgsql
AS $$
DECLARE
    target_tenant   uuid;
    supervisor      uuid;
    supervisor_path ltree;
    new_path        ltree;
BEGIN
    SELECT tenant_id, supervisor_id INTO target_tenant, supervisor
    FROM employee WHERE id = target_employee;

    IF target_tenant IS NULL THEN
        RETURN; -- Employee no longer exists.
    END IF;

    IF supervisor IS NULL THEN
        new_path := text2ltree('n' || replace(target_employee::text, '-', ''));
    ELSE
        SELECT path INTO supervisor_path FROM employee_hierarchy WHERE employee_id = supervisor;

        IF supervisor_path IS NULL THEN
            -- Supervisor has no path yet (bulk import order). Build theirs
            -- first; the recursion terminates because the cycle check below
            -- guarantees the chain is acyclic.
            PERFORM rebuild_employee_hierarchy(supervisor);
            SELECT path INTO supervisor_path FROM employee_hierarchy WHERE employee_id = supervisor;
        END IF;

        new_path := supervisor_path || text2ltree('n' || replace(target_employee::text, '-', ''));
    END IF;

    INSERT INTO employee_hierarchy (tenant_id, employee_id, path, depth)
    VALUES (target_tenant, target_employee, new_path, nlevel(new_path) - 1)
    ON CONFLICT (employee_id) DO UPDATE
        SET path = EXCLUDED.path, depth = EXCLUDED.depth, tenant_id = EXCLUDED.tenant_id;

    -- Cascade to reports.
    PERFORM rebuild_employee_hierarchy(e.id)
    FROM employee e
    WHERE e.supervisor_id = target_employee;
END;
$$;

/**
 * Rejects a supervisor assignment that would create a cycle.
 *
 * A cycle makes the hierarchy rebuild recurse forever and, more importantly,
 * makes approval routing loop — a leave request that can never reach anyone.
 * Cheaper to reject at write time than to detect later.
 */
CREATE OR REPLACE FUNCTION employee_hierarchy_maintain()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    ancestor_path ltree;
    own_label     text := 'n' || replace(NEW.id::text, '-', '');
BEGIN
    IF NEW.supervisor_id IS NOT NULL THEN
        SELECT path INTO ancestor_path FROM employee_hierarchy WHERE employee_id = NEW.supervisor_id;

        IF ancestor_path IS NOT NULL AND ancestor_path ~ ('*.' || own_label || '.*')::lquery THEN
            RAISE EXCEPTION
                'Assigning % as supervisor of % would create a reporting cycle',
                NEW.supervisor_id, NEW.id
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;

    PERFORM rebuild_employee_hierarchy(NEW.id);
    RETURN NEW;
END;
$$;

CREATE TRIGGER employee_hierarchy_on_insert
    AFTER INSERT ON employee
    FOR EACH ROW
EXECUTE FUNCTION employee_hierarchy_maintain();

CREATE TRIGGER employee_hierarchy_on_supervisor_change
    AFTER UPDATE OF supervisor_id ON employee
    FOR EACH ROW
    WHEN (OLD.supervisor_id IS DISTINCT FROM NEW.supervisor_id)
EXECUTE FUNCTION employee_hierarchy_maintain();

-- =============================================================================
-- Census — dependants, emergency contacts, nominees, transport
-- =============================================================================
CREATE TABLE employee_dependant
(
    id              uuid         PRIMARY KEY,
    tenant_id       uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id     uuid         NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    name            varchar(255) NOT NULL,
    relationship_id uuid         REFERENCES relationship (id) ON DELETE SET NULL,
    date_of_birth   date,
    gender_type_id  uuid         REFERENCES gender_type (id) ON DELETE SET NULL,
    is_beneficiary  boolean      NOT NULL DEFAULT false,
    share_pct       numeric(5, 2),
    custom_fields   jsonb        NOT NULL DEFAULT '{}'::jsonb,

    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      uuid,
    version         bigint       NOT NULL DEFAULT 0,

    CONSTRAINT dependant_share_range CHECK (share_pct IS NULL OR share_pct BETWEEN 0 AND 100)
);

CREATE INDEX ix_employee_dependant_tenant_employee ON employee_dependant (tenant_id, employee_id);
SELECT apply_tenant_rls('employee_dependant');

CREATE TABLE employee_emergency_contact
(
    id              uuid         PRIMARY KEY,
    tenant_id       uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id     uuid         NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    name            varchar(255) NOT NULL,
    relationship_id uuid         REFERENCES relationship (id) ON DELETE SET NULL,
    phone           varchar(32)  NOT NULL,
    alt_phone       varchar(32),
    address         text,
    -- Lower is contacted first. An emergency contact list with no order is a
    -- list nobody can act on quickly.
    priority        smallint     NOT NULL DEFAULT 1,

    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      uuid,
    version         bigint       NOT NULL DEFAULT 0
);

CREATE INDEX ix_employee_emergency_contact_tenant_employee
    ON employee_emergency_contact (tenant_id, employee_id, priority);
SELECT apply_tenant_rls('employee_emergency_contact');

-- =============================================================================
-- Qualifications and experience
-- =============================================================================
CREATE TABLE employee_qualification
(
    id                  uuid         PRIMARY KEY,
    tenant_id           uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id         uuid         NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    qualification_id    uuid         REFERENCES qualification (id) ON DELETE SET NULL,
    qualification_type_id uuid       REFERENCES qualification_type (id) ON DELETE SET NULL,
    institution         varchar(255),
    from_date           date,
    to_date             date,
    grade               varchar(64),
    attachment_key      varchar(512),
    verified            boolean      NOT NULL DEFAULT false,
    verified_at         timestamptz,
    verified_by         uuid,

    created_at          timestamptz  NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    updated_by          uuid,
    version             bigint       NOT NULL DEFAULT 0,

    CONSTRAINT qualification_dates_ordered CHECK (to_date IS NULL OR from_date IS NULL OR to_date >= from_date)
);

CREATE INDEX ix_employee_qualification_tenant_employee ON employee_qualification (tenant_id, employee_id);
SELECT apply_tenant_rls('employee_qualification');

CREATE TABLE employee_work_experience
(
    id                 uuid         PRIMARY KEY,
    tenant_id          uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id        uuid         NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    employer           varchar(255) NOT NULL,
    designation        varchar(255),
    from_date          date,
    to_date            date,
    reason_for_leaving text,
    reference_contact  varchar(255),

    created_at         timestamptz  NOT NULL DEFAULT now(),
    created_by         uuid,
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    updated_by         uuid,
    version            bigint       NOT NULL DEFAULT 0,

    CONSTRAINT work_experience_dates_ordered CHECK (to_date IS NULL OR from_date IS NULL OR to_date >= from_date)
);

CREATE INDEX ix_employee_work_experience_tenant_employee ON employee_work_experience (tenant_id, employee_id);
SELECT apply_tenant_rls('employee_work_experience');

-- =============================================================================
-- Financial
-- =============================================================================
CREATE TABLE employee_bank_account
(
    id             uuid          PRIMARY KEY,
    tenant_id      uuid          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id    uuid          NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    bank_id        uuid          NOT NULL REFERENCES bank (id) ON DELETE RESTRICT,
    branch_id      uuid          REFERENCES bank_branch (id) ON DELETE RESTRICT,
    account_no_enc text          NOT NULL,
    account_name   varchar(255)  NOT NULL,
    is_primary     boolean       NOT NULL DEFAULT true,
    -- Salary split across accounts: FIXED pays an amount, PERCENTAGE a share,
    -- REMAINDER whatever is left. Exactly one REMAINDER account per employee is
    -- enforced in the application — a check constraint cannot express it.
    split_type     varchar(16)   NOT NULL DEFAULT 'REMAINDER',
    split_value    numeric(19, 6),
    currency       char(3),
    valid_from     date          NOT NULL DEFAULT CURRENT_DATE,
    valid_to       date,

    created_at     timestamptz   NOT NULL DEFAULT now(),
    created_by     uuid,
    updated_at     timestamptz   NOT NULL DEFAULT now(),
    updated_by     uuid,
    version        bigint        NOT NULL DEFAULT 0,

    CONSTRAINT bank_account_split_valid CHECK (split_type IN ('FIXED', 'PERCENTAGE', 'REMAINDER')),
    CONSTRAINT bank_account_split_value_present CHECK (
        (split_type = 'REMAINDER' AND split_value IS NULL) OR
        (split_type <> 'REMAINDER' AND split_value IS NOT NULL)
    ),
    CONSTRAINT bank_account_percentage_range CHECK (
        split_type <> 'PERCENTAGE' OR split_value BETWEEN 0 AND 100
    ),
    CONSTRAINT bank_account_period_ordered CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE INDEX ix_employee_bank_account_tenant_employee ON employee_bank_account (tenant_id, employee_id);
SELECT apply_tenant_rls('employee_bank_account');

COMMENT ON COLUMN employee_bank_account.account_no_enc IS
    'Encrypted. A database dump must not yield account numbers — backups travel further than live data.';

-- =============================================================================
-- Documents and attachments
-- =============================================================================
CREATE TABLE employee_document
(
    id                uuid         PRIMARY KEY,
    tenant_id         uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id       uuid         NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    doc_type          varchar(32)  NOT NULL,
    doc_number_enc    text,
    issue_date        date,
    expiry_date       date,
    issuing_country   char(2),
    attachment_key    varchar(512),
    -- Drives the expiry reminder job. A visa that lapses unnoticed can stop
    -- someone legally working, so the lead time is per-document rather than a
    -- global default.
    alert_days_before smallint     NOT NULL DEFAULT 30,
    status            varchar(32)  NOT NULL DEFAULT 'VALID',

    created_at        timestamptz  NOT NULL DEFAULT now(),
    created_by        uuid,
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    updated_by        uuid,
    version           bigint       NOT NULL DEFAULT 0,

    CONSTRAINT employee_document_type_valid CHECK (
        doc_type IN ('PASSPORT', 'VISA', 'WORK_PERMIT', 'LABOUR_CARD', 'DRIVING_LICENCE', 'NATIONAL_ID', 'OTHER')
    ),
    CONSTRAINT employee_document_status_valid CHECK (status IN ('VALID', 'EXPIRING', 'EXPIRED', 'REPLACED')),
    CONSTRAINT employee_document_dates_ordered CHECK (
        expiry_date IS NULL OR issue_date IS NULL OR expiry_date >= issue_date
    )
);

CREATE INDEX ix_employee_document_tenant_employee ON employee_document (tenant_id, employee_id);
-- Partial index: the expiry job only ever scans documents that have one.
CREATE INDEX ix_employee_document_expiry ON employee_document (tenant_id, expiry_date)
    WHERE expiry_date IS NOT NULL AND status <> 'REPLACED';

SELECT apply_tenant_rls('employee_document');

CREATE TABLE employee_attachment
(
    id                 uuid         PRIMARY KEY,
    tenant_id          uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    employee_id        uuid         NOT NULL REFERENCES employee (id) ON DELETE CASCADE,
    attachment_type_id uuid         REFERENCES attachment_type (id) ON DELETE SET NULL,
    file_key           varchar(512) NOT NULL,
    file_name          varchar(255) NOT NULL,
    mime_type          varchar(128),
    size_bytes         bigint,
    -- Uploads are scanned asynchronously; the UI shows a scanning state rather
    -- than blocking the upload on it.
    scan_status        varchar(16)  NOT NULL DEFAULT 'PENDING',

    created_at         timestamptz  NOT NULL DEFAULT now(),
    created_by         uuid,
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    updated_by         uuid,
    version            bigint       NOT NULL DEFAULT 0,

    CONSTRAINT employee_attachment_scan_valid CHECK (scan_status IN ('PENDING', 'CLEAN', 'INFECTED', 'FAILED'))
);

CREATE INDEX ix_employee_attachment_tenant_employee ON employee_attachment (tenant_id, employee_id);
SELECT apply_tenant_rls('employee_attachment');

-- =============================================================================
-- Link app_user to employee
--
-- The FK could not be declared in V2 because `employee` did not exist yet.
-- =============================================================================
ALTER TABLE app_user
    ADD CONSTRAINT fk_app_user_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id) ON DELETE SET NULL;

ALTER TABLE department
    ADD CONSTRAINT fk_department_head
        FOREIGN KEY (head_employee_id) REFERENCES employee (id) ON DELETE SET NULL;

-- =============================================================================
-- Permissions
-- =============================================================================
INSERT INTO permission (key, module, description) VALUES
    ('employee.view',          'employee', 'View employee records within the caller''s data scope'),
    ('employee.manage',        'employee', 'Create and modify employee records'),
    ('employee.salary.view',   'employee', 'View salary information'),
    ('employee.bank.view',     'employee', 'View bank account details'),
    ('employee.document.view', 'employee', 'View identity documents'),
    ('employee.directory',     'employee', 'Search the employee directory')
ON CONFLICT (key) DO NOTHING;
