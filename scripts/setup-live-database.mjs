#!/usr/bin/env node
/**
 * Live Database Setup & Provisioning Tool for HR Platform
 *
 * Automates PostgreSQL 16 database provisioning with 3-role RLS security:
 *   - hr_owner:     Schema owner, executes Flyway migrations.
 *   - hr_app:       Group role with DML privileges (created by V1 migration).
 *   - hr_app_login: Runtime login role for the application (non-owner to enforce RLS).
 *
 * Supports:
 *   - Extracting credentials directly from .env.production or .env
 *   - Generating ready-to-run raw SQL (--sql) for cloud consoles (RDS, Supabase, Neon, etc.)
 *   - Dry run mode (--dry-run)
 *
 * Usage:
 *   node scripts/setup-live-database.mjs [--sql] [--dry-run] [--env <path>]
 */

import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

function parseEnv(filePath) {
  if (!existsSync(filePath)) return {};
  const content = readFileSync(filePath, 'utf8');
  const env = {};
  for (const line of content.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eqIdx = trimmed.indexOf('=');
    if (eqIdx !== -1) {
      const key = trimmed.slice(0, eqIdx).trim();
      const val = trimmed.slice(eqIdx + 1).trim();
      env[key] = val;
    }
  }
  return env;
}

function generateProvisioningSql(config) {
  const {
    dbName = 'hr',
    ownerUser = 'hr_owner',
    ownerPassword = 'change_me_owner_password',
    appUser = 'hr_app_login',
    appPassword = 'change_me_app_password',
  } = config;

  return `-- =============================================================================
-- HR PLATFORM: LIVE POSTGRESQL 16 PROVISIONING SCRIPT
-- Generated on: ${new Date().toISOString()}
--
-- RUN THIS AS THE POSTGRES SUPERUSER (e.g. postgres / rds_superuser)
-- =============================================================================

-- 1. Ensure target database exists
-- (If running inside psql connected to 'postgres', create database if needed):
-- CREATE DATABASE ${dbName} WITH ENCODING 'UTF8' LC_COLLATE 'C' LC_CTYPE 'C';
-- \\c ${dbName}

-- 2. Required PostgreSQL Extensions (§1.1, §1.5)
CREATE EXTENSION IF NOT EXISTS "ltree";
CREATE EXTENSION IF NOT EXISTS "btree_gin";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 3. Provision 3-Role Separation Architecture (§7)
--
-- Security rationale:
-- Table owners in PostgreSQL bypass Row-Level Security (RLS) policies by default.
-- Therefore, the application MUST NEVER connect as hr_owner.
-- Flyway migrations run as hr_owner; runtime queries execute as hr_app_login.

DO $$
BEGIN
  -- Create schema owner role if not present
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${ownerUser}') THEN
    CREATE ROLE ${ownerUser} WITH LOGIN PASSWORD '${ownerPassword}';
  ELSE
    ALTER ROLE ${ownerUser} WITH PASSWORD '${ownerPassword}';
  END IF;

  -- Create application group role (no login)
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'hr_app') THEN
    CREATE ROLE hr_app NOLOGIN;
  END IF;

  -- Create application runtime login role
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${appUser}') THEN
    CREATE ROLE ${appUser} WITH LOGIN PASSWORD '${appPassword}' IN ROLE hr_app;
  ELSE
    ALTER ROLE ${appUser} WITH PASSWORD '${appPassword}' IN ROLE hr_app;
  END IF;
END
$$;

-- 4. Schema Permissions
GRANT ALL ON SCHEMA public TO ${ownerUser};
GRANT USAGE ON SCHEMA public TO hr_app;

-- Revoke default public create rights to prevent unauthorized object creation
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- 5. Verification Queries
SELECT extname, extversion FROM pg_extension WHERE extname IN ('ltree', 'btree_gin', 'pgcrypto');
SELECT rolname, rolcanlogin, rolsuper FROM pg_roles WHERE rolname IN ('${ownerUser}', 'hr_app', '${appUser}');
`;
}

function main() {
  const args = process.argv.slice(2);
  const isSqlOnly = args.includes('--sql');
  const isDryRun = args.includes('--dry-run');

  let envPath = resolve(process.cwd(), '.env.production');
  const envIndex = args.indexOf('--env');
  if (envIndex !== -1 && args[envIndex + 1]) {
    envPath = resolve(process.cwd(), args[envIndex + 1]);
  } else if (!existsSync(envPath) && existsSync(resolve(process.cwd(), '.env'))) {
    envPath = resolve(process.cwd(), '.env');
  }

  const env = parseEnv(envPath);

  const config = {
    dbName: env.POSTGRES_DB || 'hr',
    ownerUser: env.POSTGRES_USER || 'hr_owner',
    ownerPassword: env.POSTGRES_PASSWORD || 'hr_owner_secure_password',
    appUser: env.POSTGRES_APP_USER || 'hr_app_login',
    appPassword: env.POSTGRES_APP_PASSWORD || 'hr_app_login_secure_password',
  };

  const sqlScript = generateProvisioningSql(config);

  if (isSqlOnly) {
    console.log(sqlScript);
    return;
  }

  console.log('=============================================================');
  console.log('        HR PLATFORM - LIVE DATABASE PROVISIONING            ');
  console.log('=============================================================');
  console.log(`Config Source:          ${existsSync(envPath) ? envPath : 'Default template parameters'}`);
  console.log(`Database Name:          ${config.dbName}`);
  console.log(`Schema Owner (Flyway):  ${config.ownerUser}`);
  console.log(`Runtime App Role (RLS): ${config.appUser}`);
  console.log('Required Extensions:    ltree, btree_gin, pgcrypto');
  console.log('-------------------------------------------------------------');

  if (isDryRun) {
    console.log('\n[DRY RUN] Generated SQL script below:\n');
    console.log(sqlScript);
    console.log('-------------------------------------------------------------');
    console.log('Run with --sql to output only the executable SQL script.');
    return;
  }

  console.log('\n[READY] To provision your target PostgreSQL instance:\n');
  console.log('Option 1: Execute SQL directly via psql CLI:');
  console.log(`  node scripts/setup-live-database.mjs --sql | psql -U postgres -h <host> -d ${config.dbName}\n`);
  console.log('Option 2: Cloud Console (AWS RDS Query Editor, Supabase, Neon):');
  console.log('  Run "node scripts/setup-live-database.mjs --sql", copy the output,');
  console.log('  and paste it into your cloud database SQL editor.\n');
  console.log('Option 3: Local Docker stack:');
  console.log('  docker compose up -d postgres\n');
  console.log('Next Step after running the SQL:');
  console.log('  Start the backend to run all 28 Flyway migrations automatically:');
  console.log('  cd backend && ./gradlew bootRun --args="--spring.profiles.active=prod"');
  console.log('=============================================================');
}

main();
