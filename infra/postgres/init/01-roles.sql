-- Runs once, on first container start, as the superuser.
--
-- Creates the runtime login role. This is the whole point of the exercise: the application must
-- connect as a role that does NOT own the tables, because PostgreSQL exempts table owners from
-- row-level security by default. Connect as the owner and every RLS policy silently stops
-- applying — while all your isolation tests continue to pass.
--
--   hr_owner       (created by POSTGRES_USER) — owns the schema, runs Flyway migrations
--   hr_app         group role granted DML, created by V1__platform_tenancy.sql
--   hr_app_login   the login role the application actually uses, a member of hr_app
--
-- Production provisions the equivalent through Terraform with generated credentials.

CREATE ROLE hr_app NOLOGIN;

CREATE ROLE hr_app_login LOGIN PASSWORD 'hr_app_login' IN ROLE hr_app;

-- Without this the application role cannot see the schema at all.
GRANT USAGE ON SCHEMA public TO hr_app;

-- Belt and braces: make sure the app role can never accidentally acquire ownership rights
-- through the PUBLIC pseudo-role.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
