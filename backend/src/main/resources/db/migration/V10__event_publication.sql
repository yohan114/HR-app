-- =============================================================================
-- V10 — Spring Modulith event publication registry
--
-- Found by starting the application against a real database for the first time:
-- `spring-modulith-starter-jpa` registers JPA entities for these two tables, so
-- Hibernate's schema validation refuses to build the SessionFactory without
-- them and the application cannot start at all. No unit test could have caught
-- it — the entities belong to the library, not to us.
--
-- The shape is dictated by the library (Spring Modulith 1.3.4,
-- `JpaEventPublication` / `ArchivedJpaEventPublication`). It is reproduced here
-- rather than created by Modulith's own schema initialisation because the
-- runtime role deliberately has no DDL rights: migrations run as `hr_owner` and
-- the application connects as `hr_app_login`, which is the separation that makes
-- row-level security meaningful (see V1__platform_tenancy.sql).
--
-- Not tenant-scoped, and deliberately so. A publication is an internal record
-- that a listener has yet to process an event — it belongs to the deployment,
-- not to a customer, and the application writes it as itself rather than on
-- behalf of whoever happened to make the request. `migration-check.mjs` requires
-- RLS only on tables carrying `tenant_id`, so these are correctly exempt.
--
-- Worth knowing: `serialized_event` holds the event payload as JSON, so it can
-- contain tenant data in transit. It is not a durable store of that data — rows
-- are deleted or archived on completion — but it is a reason to keep the
-- retention job below in mind rather than letting the table grow unbounded.
-- =============================================================================

CREATE TABLE event_publication
(
    id               uuid        PRIMARY KEY,
    listener_id      text        NOT NULL,
    event_type       text        NOT NULL,
    serialized_event text        NOT NULL,
    publication_date timestamptz NOT NULL,
    -- NULL until the listener has completed. Incomplete rows are what the
    -- republication mechanism replays after a crash, which is the entire point
    -- of the registry: an event handled in-process is lost if the process dies
    -- between the transaction committing and the listener finishing.
    completion_date  timestamptz
);

-- The registry's hot query is "everything still incomplete", which is a small
-- fraction of the table in a healthy system and all of it in a broken one.
CREATE INDEX ix_event_publication_incomplete
    ON event_publication (publication_date)
    WHERE completion_date IS NULL;

CREATE INDEX ix_event_publication_completion
    ON event_publication (completion_date);

-- Modulith looks publications up by (listener, event) when marking one complete.
-- A hash index because the serialised payload can be large and the lookup is
-- only ever an equality test.
CREATE INDEX ix_event_publication_event_hash
    ON event_publication USING hash (serialized_event);

-- -----------------------------------------------------------------------------
-- Archive
--
-- Only written in ARCHIVE completion mode, which is not the default. Created
-- regardless because `ArchivedJpaEventPublication` is an `@Entity` on the
-- classpath either way, and Hibernate validates every registered entity rather
-- than only the ones the current configuration will use.
-- -----------------------------------------------------------------------------
CREATE TABLE event_publication_archive
(
    id               uuid        PRIMARY KEY,
    listener_id      text        NOT NULL,
    event_type       text        NOT NULL,
    serialized_event text        NOT NULL,
    publication_date timestamptz NOT NULL,
    completion_date  timestamptz
);

CREATE INDEX ix_event_publication_archive_completion
    ON event_publication_archive (completion_date);
