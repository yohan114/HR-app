#!/usr/bin/env node
/**
 * Fault injection for migration-check.
 *
 * A checker nobody has seen fail is indistinguishable from a checker that
 * cannot fail. This copies the real migrations to a scratch directory, breaks
 * one thing at a time, and asserts the checker notices — and, separately, that
 * it stays quiet when nothing is broken.
 *
 * The second half matters as much as the first. A checker that fires on correct
 * input gets muted within a week, and a muted checker is worse than none: it
 * still looks like coverage on a dashboard.
 *
 *   node backend/scripts/migration-check.selftest.mjs
 */

import { execFileSync } from 'node:child_process'
import { cpSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const SOURCE = 'backend/src/main/resources/db/migration'
const CHECKER = 'backend/scripts/migration-check.mjs'

let failures = 0

/** Runs the checker against a scratch copy, returning its combined output. */
function run(mutate) {
  const dir = mkdtempSync(join(tmpdir(), 'migcheck-'))
  try {
    cpSync(SOURCE, dir, { recursive: true })
    // The real tree has a genuine finding in V1 (tenant_module, no RLS). Patch
    // it out of the baseline so an injected fault is the only thing left, and
    // the "clean input stays quiet" case is actually reachable.
    patch(dir, 'V1__platform_tenancy.sql', (sql) =>
      sql.replace(
        /SELECT apply_tenant_rls\('sequence_config'\);/,
        "SELECT apply_tenant_rls('tenant_module');\nSELECT apply_tenant_rls('sequence_config');",
      ),
    )
    mutate?.(dir)
    try {
      return { code: 0, out: execFileSync('node', [CHECKER, dir], { encoding: 'utf8', stdio: 'pipe' }) }
    } catch (e) {
      return { code: e.status ?? 1, out: (e.stdout ?? '') + (e.stderr ?? '') }
    }
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
}

function patch(dir, file, fn) {
  const p = join(dir, file)
  writeFileSync(p, fn(readFileSync(p, 'utf8')))
}

function expectFail(name, mutate, expectedSubstring) {
  const { code, out } = run(mutate)
  if (code === 0) {
    console.error(`NOT DETECTED  ${name}\n  the checker passed a tree containing this fault`)
    failures++
  } else if (expectedSubstring && !out.includes(expectedSubstring)) {
    console.error(
      `WRONG MESSAGE ${name}\n  expected output to mention ${JSON.stringify(expectedSubstring)}\n  got:\n${indent(out)}`,
    )
    failures++
  } else {
    console.log(`detected      ${name}`)
  }
}

function expectPass(name, mutate) {
  const { code, out } = run(mutate)
  if (code !== 0) {
    console.error(`FALSE POSITIVE ${name}\n${indent(out)}`)
    failures++
  } else {
    console.log(`quiet         ${name}`)
  }
}

const indent = (s) => s.split('\n').map((l) => '    ' + l).join('\n')

// ---------------------------------------------------------------------------

console.log('migration-check self-test\n')

expectPass('an unmodified tree (with the known V1 finding patched) produces no output', null)

expectFail(
  'a new tenant-scoped table with no apply_tenant_rls',
  (dir) =>
    writeFileSync(
      join(dir, 'V9__fault.sql'),
      `CREATE TABLE widget (
    id        uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant (id),
    name      text NOT NULL
);
CREATE INDEX ix_widget_tenant ON widget (tenant_id, name);
`,
    ),
  "never calls apply_tenant_rls('widget')",
)

expectFail(
  'a tenant-scoped table with no index leading with tenant_id',
  (dir) =>
    writeFileSync(
      join(dir, 'V9__fault.sql'),
      `CREATE TABLE widget (
    id        uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenant (id),
    name      text NOT NULL
);
CREATE INDEX ix_widget_name ON widget (name, tenant_id);
SELECT apply_tenant_rls('widget');
`,
    ),
  'no index leading with tenant_id',
)

expectFail(
  'a gap in migration versions',
  (dir) => writeFileSync(join(dir, 'V11__fault.sql'), 'SELECT 1;\n'),
  'gap in migration versions',
)

expectFail(
  'an unterminated dollar-quoted body',
  (dir) =>
    writeFileSync(
      join(dir, 'V9__fault.sql'),
      `CREATE OR REPLACE FUNCTION broken() RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    PERFORM 1;
END;
`,
    ),
  'unbalanced dollar quoting',
)

expectFail(
  'a foreign key to a table created in a later migration',
  (dir) => {
    writeFileSync(
      join(dir, 'V9__fault.sql'),
      `CREATE TABLE early (
    id        uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    late_id   uuid REFERENCES late (id)
);
CREATE INDEX ix_early_tenant ON early (tenant_id);
SELECT apply_tenant_rls('early');
`,
    )
    writeFileSync(
      join(dir, 'V10__fault2.sql'),
      `CREATE TABLE late (
    id        uuid PRIMARY KEY,
    tenant_id uuid NOT NULL
);
CREATE INDEX ix_late_tenant ON late (tenant_id);
SELECT apply_tenant_rls('late');
`,
    )
  },
  'created later in',
)

expectFail(
  'a foreign key to a table that does not exist at all',
  (dir) =>
    writeFileSync(
      join(dir, 'V9__fault.sql'),
      `CREATE TABLE widget (
    id        uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    ghost_id  uuid REFERENCES ghost (id)
);
CREATE INDEX ix_widget_tenant ON widget (tenant_id);
SELECT apply_tenant_rls('widget');
`,
    ),
  'which no migration creates',
)

expectFail(
  'a DROP TABLE with no acknowledgement comment',
  (dir) => writeFileSync(join(dir, 'V9__fault.sql'), 'DROP TABLE sequence_config;\n'),
  'destructive DDL without an acknowledgement',
)

expectPass('a DROP TABLE that is acknowledged', (dir) =>
  writeFileSync(
    join(dir, 'V9__fault.sql'),
    `-- DESTRUCTIVE: sequence_config was superseded by the numbering engine in V8.
-- No tenant has rows in it; verified against production on 2026-08-24.
DROP TABLE sequence_config;
`,
  ),
)

expectFail(
  'the reference-table generator losing its RLS call',
  (dir) => patch(dir, 'V5__organisation_structure.sql', (sql) => sql.replace(/PERFORM apply_tenant_rls\(table_name\);/, '')),
  'no longer guarantees',
)

expectFail(
  'the reference-table generator losing its tenant_id column',
  (dir) =>
    patch(dir, 'V5__organisation_structure.sql', (sql) =>
      sql.replace(/tenant_id\s+uuid\s+NOT NULL REFERENCES tenant \(id\) ON DELETE CASCADE,/, ''),
    ),
  'no longer guarantees',
)

expectFail(
  'an edit to a migration already recorded in the manifest',
  (dir) => {
    execFileSync('node', [CHECKER, dir, '--update-manifest'], { stdio: 'pipe' })
    patch(dir, 'V2__identity.sql', (sql) => sql + '\n-- an innocuous-looking edit\n')
  },
  'has changed since it was recorded',
)

expectFail(
  'a released migration being deleted',
  (dir) => {
    execFileSync('node', [CHECKER, dir, '--update-manifest'], { stdio: 'pipe' })
    rmSync(join(dir, 'V7__custom_fields.sql'))
    // Renumber so the deletion shows as a manifest violation rather than a gap.
    cpSync(join(dir, 'V8__employee_access.sql'), join(dir, 'V7__renamed.sql'))
    rmSync(join(dir, 'V8__employee_access.sql'))
  },
  'cannot be deleted',
)

// ---------------------------------------------------------------------------

const total = readdirSync(SOURCE).filter((f) => /^V\d+__/.test(f)).length
console.log(
  `\n${failures === 0 ? 'all checks proven to fire' : `${failures} self-test failure(s)`} — ${total} real migrations in ${SOURCE}`,
)
process.exit(failures > 0 ? 1 : 0)
