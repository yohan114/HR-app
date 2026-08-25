-- =============================================================================
-- V5 — Organisation structure (EIM)
--
-- The master data every other module hangs off: companies, locations,
-- departments, designations, cost centres, salary grades, and the reference
-- taxonomies employees are classified by.
--
-- ## On the reference taxonomies
--
-- docs/04-data-model.md §3 lists around thirty of these — employee category,
-- blood group, marital status, relationship, nationality and so on. They are
-- structurally identical: a tenant-scoped code/name lookup with an ordering and
-- an active flag.
--
-- Writing thirty near-identical CREATE TABLE blocks would be several hundred
-- lines in which a single missed `apply_tenant_rls()` is invisible. They are
-- generated from a list instead, so the shape is defined once and cannot drift
-- between them.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Helper: create a standard reference table.
--
-- Every reference table gets the same columns, the same unique constraint on
-- (tenant_id, code), the same tenant-leading index, and RLS. Defining that once
-- means a new taxonomy is one line rather than a block someone can get subtly
-- wrong.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION create_reference_table(table_name text, comment text DEFAULT NULL)
    RETURNS void
    LANGUAGE plpgsql
AS $$
BEGIN
    EXECUTE format($ddl$
        CREATE TABLE %I (
            id          uuid         PRIMARY KEY,
            tenant_id   uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
            code        varchar(64)  NOT NULL,
            name        varchar(255) NOT NULL,
            description text,
            sequence    integer      NOT NULL DEFAULT 0,
            active      boolean      NOT NULL DEFAULT true,

            created_at  timestamptz  NOT NULL DEFAULT now(),
            created_by  uuid,
            updated_at  timestamptz  NOT NULL DEFAULT now(),
            updated_by  uuid,
            version     bigint       NOT NULL DEFAULT 0
        )
    $ddl$, table_name);

    EXECUTE format('CREATE UNIQUE INDEX ux_%s_tenant_code ON %I (tenant_id, code)', table_name, table_name);
    EXECUTE format('CREATE INDEX ix_%s_tenant_active ON %I (tenant_id, active, sequence)', table_name, table_name);

    PERFORM apply_tenant_rls(table_name);

    IF comment IS NOT NULL THEN
        EXECUTE format('COMMENT ON TABLE %I IS %L', table_name, comment);
    END IF;
END;
$$;

COMMENT ON FUNCTION create_reference_table(text, text) IS
    'Creates a tenant-scoped code/name lookup with RLS applied. Use for every reference taxonomy.';

-- =============================================================================
-- Company and locations
-- =============================================================================
CREATE TABLE company
(
    id               uuid         PRIMARY KEY,
    tenant_id        uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    parent_id        uuid         REFERENCES company (id) ON DELETE RESTRICT,
    code             varchar(64)  NOT NULL,
    name             varchar(255) NOT NULL,
    legal_name       varchar(255),
    country_code     char(2)      NOT NULL,
    currency         char(3)      NOT NULL,
    tax_registration varchar(64),
    -- Tenant-defined fields, driven by field_definition (V7). JSONB rather than
    -- EAV: a single row read returns everything, and GIN indexing makes the
    -- occasional query-by-custom-field workable.
    custom_fields    jsonb        NOT NULL DEFAULT '{}'::jsonb,

    created_at       timestamptz  NOT NULL DEFAULT now(),
    created_by       uuid,
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    updated_by       uuid,
    version          bigint       NOT NULL DEFAULT 0,

    CONSTRAINT company_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE UNIQUE INDEX ux_company_tenant_code ON company (tenant_id, code);
CREATE INDEX ix_company_tenant_parent      ON company (tenant_id, parent_id);
CREATE INDEX ix_company_custom_fields      ON company USING gin (custom_fields);
SELECT apply_tenant_rls('company');

COMMENT ON TABLE company IS 'Legal entities within a tenant. A group with several registered companies is one tenant with several rows here.';

CREATE TABLE location
(
    id              uuid         PRIMARY KEY,
    tenant_id       uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    company_id      uuid         NOT NULL REFERENCES company (id) ON DELETE RESTRICT,
    parent_id       uuid         REFERENCES location (id) ON DELETE RESTRICT,
    code            varchar(64)  NOT NULL,
    name            varchar(255) NOT NULL,
    address         jsonb        NOT NULL DEFAULT '{}'::jsonb,
    timezone        varchar(64)  NOT NULL,
    -- Geofence for attendance. Nullable: a location without coordinates simply
    -- cannot be geofenced, which is a valid configuration — see the attendance
    -- policy in Phase 2.
    geo_lat         numeric(9, 6),
    geo_lng         numeric(9, 6),
    geofence_radius_m integer,
    custom_fields   jsonb        NOT NULL DEFAULT '{}'::jsonb,

    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      uuid,
    version         bigint       NOT NULL DEFAULT 0,

    CONSTRAINT location_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT location_geo_complete CHECK (
        (geo_lat IS NULL AND geo_lng IS NULL) OR (geo_lat IS NOT NULL AND geo_lng IS NOT NULL)
    ),
    CONSTRAINT location_geofence_needs_coords CHECK (
        geofence_radius_m IS NULL OR geo_lat IS NOT NULL
    ),
    CONSTRAINT location_lat_range CHECK (geo_lat IS NULL OR geo_lat BETWEEN -90 AND 90),
    CONSTRAINT location_lng_range CHECK (geo_lng IS NULL OR geo_lng BETWEEN -180 AND 180)
);

CREATE UNIQUE INDEX ux_location_tenant_code ON location (tenant_id, code);
CREATE INDEX ix_location_tenant_company     ON location (tenant_id, company_id);
SELECT apply_tenant_rls('location');

CREATE TABLE cost_centre
(
    id          uuid         PRIMARY KEY,
    tenant_id   uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    company_id  uuid         REFERENCES company (id) ON DELETE RESTRICT,
    parent_id   uuid         REFERENCES cost_centre (id) ON DELETE RESTRICT,
    code        varchar(64)  NOT NULL,
    name        varchar(255) NOT NULL,
    gl_code     varchar(64),
    active      boolean      NOT NULL DEFAULT true,

    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  uuid,
    version     bigint       NOT NULL DEFAULT 0,

    CONSTRAINT cost_centre_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE UNIQUE INDEX ux_cost_centre_tenant_code ON cost_centre (tenant_id, code);
CREATE INDEX ix_cost_centre_tenant_parent      ON cost_centre (tenant_id, parent_id);
SELECT apply_tenant_rls('cost_centre');

-- =============================================================================
-- Work structures
-- =============================================================================
CREATE TABLE salary_grade
(
    id          uuid           PRIMARY KEY,
    tenant_id   uuid           NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code        varchar(64)    NOT NULL,
    name        varchar(255)   NOT NULL,
    -- numeric(19,6), never float. Money precision is decided once, here, and
    -- the payroll engine depends on it — see docs/03-architecture.md §7.
    min_amount  numeric(19, 6),
    mid_amount  numeric(19, 6),
    max_amount  numeric(19, 6),
    currency    char(3),
    sequence    integer        NOT NULL DEFAULT 0,
    active      boolean        NOT NULL DEFAULT true,

    created_at  timestamptz    NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz    NOT NULL DEFAULT now(),
    updated_by  uuid,
    version     bigint         NOT NULL DEFAULT 0,

    CONSTRAINT salary_grade_band_ordered CHECK (
        (min_amount IS NULL OR max_amount IS NULL OR min_amount <= max_amount)
        AND (mid_amount IS NULL OR min_amount IS NULL OR mid_amount >= min_amount)
        AND (mid_amount IS NULL OR max_amount IS NULL OR mid_amount <= max_amount)
    ),
    CONSTRAINT salary_grade_currency_with_amounts CHECK (
        (min_amount IS NULL AND mid_amount IS NULL AND max_amount IS NULL) OR currency IS NOT NULL
    )
);

CREATE UNIQUE INDEX ux_salary_grade_tenant_code ON salary_grade (tenant_id, code);
SELECT apply_tenant_rls('salary_grade');

SELECT create_reference_table('corporate_title', 'Formal titles independent of job function, e.g. Vice President.');

CREATE TABLE department
(
    id               uuid         PRIMARY KEY,
    tenant_id        uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    parent_id        uuid         REFERENCES department (id) ON DELETE RESTRICT,
    company_id       uuid         REFERENCES company (id) ON DELETE RESTRICT,
    cost_centre_id   uuid         REFERENCES cost_centre (id) ON DELETE SET NULL,
    code             varchar(64)  NOT NULL,
    name             varchar(255) NOT NULL,
    -- Set once employees exist (V6). Deliberately no FK: adding one here and an
    -- employee.department_id FK there would make either table impossible to
    -- populate first.
    head_employee_id uuid,
    active           boolean      NOT NULL DEFAULT true,
    custom_fields    jsonb        NOT NULL DEFAULT '{}'::jsonb,

    created_at       timestamptz  NOT NULL DEFAULT now(),
    created_by       uuid,
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    updated_by       uuid,
    version          bigint       NOT NULL DEFAULT 0,

    CONSTRAINT department_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE UNIQUE INDEX ux_department_tenant_code ON department (tenant_id, code);
CREATE INDEX ix_department_tenant_parent      ON department (tenant_id, parent_id);
SELECT apply_tenant_rls('department');

CREATE TABLE designation
(
    id                 uuid         PRIMARY KEY,
    tenant_id          uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code               varchar(64)  NOT NULL,
    name               varchar(255) NOT NULL,
    salary_grade_id    uuid         REFERENCES salary_grade (id) ON DELETE SET NULL,
    corporate_title_id uuid         REFERENCES corporate_title (id) ON DELETE SET NULL,
    active             boolean      NOT NULL DEFAULT true,
    custom_fields      jsonb        NOT NULL DEFAULT '{}'::jsonb,

    created_at         timestamptz  NOT NULL DEFAULT now(),
    created_by         uuid,
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    updated_by         uuid,
    version            bigint       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_designation_tenant_code ON designation (tenant_id, code);
CREATE INDEX ix_designation_tenant_grade      ON designation (tenant_id, salary_grade_id);
SELECT apply_tenant_rls('designation');

-- =============================================================================
-- Reference taxonomies
--
-- One line each. The shape is defined once in create_reference_table(), so a
-- new taxonomy cannot accidentally ship without RLS or without a tenant-leading
-- index.
-- =============================================================================
SELECT create_reference_table('employee_category',        'Broad classification, e.g. Permanent, Contract, Intern.');
SELECT create_reference_table('employee_group',           'Grouping for policy application — leave and benefit eligibility key off this.');
SELECT create_reference_table('employment_type',          'Full time, part time, fixed term.');
SELECT create_reference_table('employee_title',           'Salutation: Mr, Ms, Dr.');
SELECT create_reference_table('statutory_classification', 'Drives which statutory schemes apply. Country packs read this.');
SELECT create_reference_table('function',                 'Business function, e.g. Engineering, Finance.');
SELECT create_reference_table('functional_role',          'Role within a function.');
SELECT create_reference_table('classification',           'Tenant-defined classification used by pay item assignment rules.');
SELECT create_reference_table('gender_type',              'Configurable rather than an enum: the appropriate set differs by market and by customer policy.');
SELECT create_reference_table('marital_status',           NULL);
SELECT create_reference_table('blood_group',              NULL);
SELECT create_reference_table('attachment_type',          'Categorises employee document uploads.');
SELECT create_reference_table('currency_type',            NULL);
SELECT create_reference_table('nationality',              NULL);
SELECT create_reference_table('religion',                 NULL);
SELECT create_reference_table('race',                     NULL);
SELECT create_reference_table('relationship',             'Dependant and emergency contact relationships.');
SELECT create_reference_table('dwelling_type',            NULL);
SELECT create_reference_table('route',                    'Transport route, for tenants providing staff transport.');
SELECT create_reference_table('station',                  'Pickup point on a transport route.');
SELECT create_reference_table('qualification_type',       'Degree, diploma, certification.');
SELECT create_reference_table('qualification',            'A specific qualification, e.g. BSc Computer Science.');
SELECT create_reference_table('subject',                  NULL);
SELECT create_reference_table('language',                 NULL);
SELECT create_reference_table('membership_type',          'Professional body membership.');
SELECT create_reference_table('bargaining_unit',          'Union or collective bargaining group.');
SELECT create_reference_table('extracurricular_type',     NULL);

-- =============================================================================
-- Geography
--
-- Self-referencing rather than one table per level. Country → Province →
-- District → DS Division → GN Division is the Sri Lankan hierarchy; other
-- markets have three levels or five with different names. A fixed set of tables
-- would need altering per market; a level column does not.
-- =============================================================================
CREATE TABLE geo_region
(
    id         uuid         PRIMARY KEY,
    tenant_id  uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    parent_id  uuid         REFERENCES geo_region (id) ON DELETE CASCADE,
    level      varchar(32)  NOT NULL,
    code       varchar(64)  NOT NULL,
    name       varchar(255) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,

    created_at timestamptz  NOT NULL DEFAULT now(),
    created_by uuid,
    updated_at timestamptz  NOT NULL DEFAULT now(),
    updated_by uuid,
    version    bigint       NOT NULL DEFAULT 0,

    CONSTRAINT geo_region_level_valid CHECK (
        level IN ('COUNTRY', 'PROVINCE', 'DISTRICT', 'ELECTORATE', 'DS_DIVISION', 'GN_DIVISION', 'CITY')
    ),
    CONSTRAINT geo_region_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE UNIQUE INDEX ux_geo_region_tenant_level_code ON geo_region (tenant_id, level, code);
CREATE INDEX ix_geo_region_tenant_parent            ON geo_region (tenant_id, parent_id);
SELECT apply_tenant_rls('geo_region');

-- =============================================================================
-- Banking
-- =============================================================================
CREATE TABLE bank
(
    id           uuid         PRIMARY KEY,
    tenant_id    uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code         varchar(64)  NOT NULL,
    name         varchar(255) NOT NULL,
    swift        varchar(11),
    country_code char(2),
    active       boolean      NOT NULL DEFAULT true,

    created_at   timestamptz  NOT NULL DEFAULT now(),
    created_by   uuid,
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    updated_by   uuid,
    version      bigint       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_bank_tenant_code ON bank (tenant_id, code);
SELECT apply_tenant_rls('bank');

CREATE TABLE bank_branch
(
    id           uuid         PRIMARY KEY,
    tenant_id    uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    bank_id      uuid         NOT NULL REFERENCES bank (id) ON DELETE CASCADE,
    code         varchar(64)  NOT NULL,
    name         varchar(255) NOT NULL,
    routing_code varchar(64),
    active       boolean      NOT NULL DEFAULT true,

    created_at   timestamptz  NOT NULL DEFAULT now(),
    created_by   uuid,
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    updated_by   uuid,
    version      bigint       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_bank_branch_tenant_bank_code ON bank_branch (tenant_id, bank_id, code);
CREATE INDEX ix_bank_branch_tenant_bank             ON bank_branch (tenant_id, bank_id);
SELECT apply_tenant_rls('bank_branch');

-- =============================================================================
-- Permissions for this module
-- =============================================================================
INSERT INTO permission (key, module, description) VALUES
    ('org.structure.view',   'org', 'View companies, locations, departments and designations'),
    ('org.structure.manage', 'org', 'Create and modify organisation structure'),
    ('org.reference.view',   'org', 'View reference data'),
    ('org.reference.manage', 'org', 'Create and modify reference data')
ON CONFLICT (key) DO NOTHING;
