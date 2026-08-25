-- =============================================================================
-- V7 — Dynamic data structure (custom fields)
--
-- Lets a tenant add fields to an entity without a code change or an app
-- release. This matters more than it looks: without it, every customer
-- onboarding that needs one extra field blocks on an engineering ticket and an
-- App Store review — a two-week round trip for a text box.
--
-- ## How it works
--
-- Definitions live here. Values live in the owning entity's `custom_fields`
-- JSONB column (added in V5/V6), with a GIN index.
--
-- The definitions also drive **server-rendered form schemas**: the mobile
-- clients ask `GET /v1/forms/{entityType}` and render whatever comes back. A
-- field added here appears on Android and iOS on the next sync, with no
-- release. That is the whole point.
--
-- ## Why JSONB rather than EAV
--
-- An entity-attribute-value table needs one join per field to reconstruct a
-- record. An employee with twelve custom fields becomes a twelve-way join on
-- every profile read. JSONB returns the whole record in one row, and the
-- occasional "find everyone whose T-shirt size is L" is served by the GIN
-- index.
-- =============================================================================

CREATE TABLE field_definition
(
    id            uuid         PRIMARY KEY,
    tenant_id     uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    entity_type   varchar(64)  NOT NULL,
    field_key     varchar(64)  NOT NULL,
    -- Per-locale labels. We ship in six languages, and a tenant adding a field
    -- for a Sri Lankan workforce may want it in Sinhala and Tamil too.
    label_i18n    jsonb        NOT NULL DEFAULT '{}'::jsonb,
    help_text     text,
    data_type     varchar(32)  NOT NULL,
    -- Rules the server enforces and the client mirrors for immediate feedback:
    -- { "required": true, "minLength": 3, "pattern": "^[A-Z]{2}$", "min": 0 }
    validation    jsonb        NOT NULL DEFAULT '{}'::jsonb,
    -- For DROPDOWN and RADIO: [{ "value": "S", "label": {"en": "Small"} }]
    options       jsonb,
    section       varchar(64)  NOT NULL DEFAULT 'other',
    position      integer      NOT NULL DEFAULT 0,
    -- Per-role visibility, same three states as field_permission. A custom
    -- field can hold something sensitive, so it needs the same controls as a
    -- built-in one.
    permissions   jsonb        NOT NULL DEFAULT '{}'::jsonb,
    active        boolean      NOT NULL DEFAULT true,

    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    uuid,
    version       bigint       NOT NULL DEFAULT 0,

    CONSTRAINT field_definition_type_valid CHECK (
        data_type IN ('TEXT', 'NUMBER', 'DROPDOWN', 'MULTI_SELECT', 'DATE', 'RADIO', 'CHECKBOX', 'ATTACHMENT', 'EMPLOYEE')
    ),
    CONSTRAINT field_definition_entity_valid CHECK (
        entity_type IN ('employee', 'company', 'location', 'department', 'designation')
    ),
    -- A valid JSON object key that is also a legal identifier in Kotlin, Swift
    -- and TypeScript. Without this, a field named "first-name" or "2fa"
    -- generates client code that does not compile.
    CONSTRAINT field_definition_key_format CHECK (field_key ~ '^[a-z][a-zA-Z0-9]*$'),
    -- A choice field with no choices renders an empty dropdown the user cannot
    -- get past.
    CONSTRAINT field_definition_options_present CHECK (
        data_type NOT IN ('DROPDOWN', 'MULTI_SELECT', 'RADIO')
        OR (options IS NOT NULL AND jsonb_array_length(options) > 0)
    )
);

CREATE UNIQUE INDEX ux_field_definition_tenant_entity_key
    ON field_definition (tenant_id, entity_type, field_key);
CREATE INDEX ix_field_definition_tenant_entity
    ON field_definition (tenant_id, entity_type, active, position);

SELECT apply_tenant_rls('field_definition');

COMMENT ON TABLE field_definition IS
    'Tenant-defined fields. Drives server-rendered form schemas, so a new field reaches the mobile apps without a release.';
COMMENT ON COLUMN field_definition.field_key IS
    'Must be a legal identifier in Kotlin, Swift and TypeScript — it becomes one in generated client code.';

-- -----------------------------------------------------------------------------
-- Reserved keys
--
-- A custom field named `firstName` would shadow the built-in column in a merged
-- API payload, and which one wins would depend on serialisation order. Rejected
-- at definition time rather than discovered as a mystery in production.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION field_definition_reject_reserved_key()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    reserved boolean;
BEGIN
    SELECT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = NEW.entity_type
          -- Column names are snake_case; field keys are camelCase. Compare in a
          -- single normalised form so `firstName` is caught against `first_name`.
          AND replace(lower(column_name), '_', '') = lower(NEW.field_key)
    ) INTO reserved;

    IF reserved THEN
        RAISE EXCEPTION
            'Custom field "%" collides with a built-in column on %', NEW.field_key, NEW.entity_type
            USING ERRCODE = 'check_violation',
                  HINT = 'Choose a different key. A shadowing field would make the API payload ambiguous.';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER field_definition_reserved_key
    BEFORE INSERT OR UPDATE OF field_key, entity_type ON field_definition
    FOR EACH ROW
EXECUTE FUNCTION field_definition_reject_reserved_key();

-- =============================================================================
-- Label overrides
--
-- Retitle any built-in UI string per tenant. One customer calls them
-- "Associates", another "Staff", a third "Crew" — and the request arrives during
-- every implementation.
-- =============================================================================
CREATE TABLE label_override
(
    tenant_id  uuid         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    label_key  varchar(128) NOT NULL,
    locale     varchar(16)  NOT NULL,
    value      text         NOT NULL,
    updated_at timestamptz  NOT NULL DEFAULT now(),
    updated_by uuid,

    PRIMARY KEY (tenant_id, label_key, locale)
);

SELECT apply_tenant_rls('label_override');

-- =============================================================================
-- Permissions
-- =============================================================================
INSERT INTO permission (key, module, description) VALUES
    ('config.field.view',   'config', 'View custom field definitions'),
    ('config.field.manage', 'config', 'Create and modify custom fields'),
    ('config.label.manage', 'config', 'Override UI labels')
ON CONFLICT (key) DO NOTHING;
