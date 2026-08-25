-- Mirrors infra/postgres/init/01-roles.sql for the Testcontainers instance.
--
-- The non-owner login role is essential: connecting as the owner would exempt the session from
-- row-level security and TenantIsolationTest would pass while proving nothing.
CREATE ROLE hr_app NOLOGIN;
CREATE ROLE hr_app_login LOGIN PASSWORD 'hr_app_login' IN ROLE hr_app;
GRANT USAGE ON SCHEMA public TO hr_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
