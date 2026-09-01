#!/usr/bin/env node
/**
 * Structural checks for Flyway migrations.
 *
 * Not a substitute for running them. It exists because running them needs
 * PostgreSQL, `TenantIsolationTest` needs Docker, and neither is available in
 * every environment where someone edits a migration — so without this, a
 * migration can be written, reviewed and merged with nothing having looked at
 * it at all.
 *
 * What it checks, and why each one:
 *
 *   1. **Every table with `tenant_id` calls `apply_tenant_rls()`.** This is the
 *      realistic regression: someone adds a table in a future migration and
 *      forgets the one line that turns on row-level security. ADR 0002 calls
 *      this the failure that would end the business. `TenantIsolationTest` has
 *      the same assertion against a live database; this is the version that
 *      runs without one.
 *
 *   2. **Every tenant-scoped table has an index leading with `tenant_id`.**
 *      RLS appends `tenant_id = current_tenant_id()` to every query. An index
 *      that does not begin with `tenant_id` cannot satisfy it and the planner
 *      falls back to a scan. Not a micro-optimisation — getting it wrong makes
 *      every query against that table slow, and it will not show up until there
 *      is production data.
 *
 *   3. **Version numbers are unique and contiguous.** Flyway tolerates gaps,
 *      but a gap is nearly always a migration someone forgot to commit. A
 *      duplicate is worse: Flyway fails on it, but only on the machine that
 *      runs it second.
 *
 *   4. **Dollar-quoted blocks are balanced.** An unterminated `$$` swallows the
 *      rest of the file, and the resulting error points at the end of the file
 *      rather than the mistake.
 *
 *   5. **Foreign keys reference a table that already exists.** Flyway applies
 *      migrations in order, so a reference to a table created in a later
 *      version fails at deploy time — after review, on whichever environment
 *      deploys first.
 *
 *   6. **No `DROP TABLE`/`DROP COLUMN` without an explicit acknowledgement
 *      comment.** Destructive DDL in a forward migration is sometimes right and
 *      always worth a second pair of eyes.
 *
 *   7. **Migrations are immutable.** A checksum manifest catches an edit to an
 *      already-released migration — which Flyway rejects at deploy time with a
 *      checksum mismatch, long after the edit looked harmless.
 *
 *      The manifest is opt-in and should stay absent until the migrations have
 *      run somewhere real. Creating it while they are still being corrected
 *      turns every legitimate fix into a failure, and the response to that is
 *      always to delete the manifest rather than to reconsider the edit.
 *
 * Each check was verified against a deliberately introduced fault, so it fails
 * rather than passing vacuously.
 *
 *   node backend/scripts/migration-check.mjs [migrationDir]
 *   node backend/scripts/migration-check.mjs --update-manifest
 */

import { createHash } from 'node:crypto'
import { existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const args = process.argv.slice(2)
const updateManifest = args.includes('--update-manifest')
const directory = args.find((a) => !a.startsWith('--')) ?? 'backend/src/main/resources/db/migration'
const manifestPath = join(directory, '.checksums.json')

let problems = 0
const fail = (message) => {
  console.error('FAIL ' + message)
  problems++
}
const warn = (message) => console.error('WARN ' + message)

// ---------------------------------------------------------------------------
// Load

if (!existsSync(directory)) {
  console.error(`migration-check: no such directory ${directory}`)
  process.exit(1)
}

const files = readdirSync(directory)
  .filter((f) => /^V\d+__.+\.sql$/.test(f))
  .sort((a, b) => version(a) - version(b))

if (files.length === 0) {
  console.error(`migration-check: no V*__*.sql files in ${directory}`)
  process.exit(1)
}

function version(file) {
  return Number.parseInt(file.slice(1), 10)
}

/**
 * Strips comments and string literals before structural analysis.
 *
 * Without this, the word "tenant_id" inside a COMMENT ON or a doc block counts
 * as a column definition, and the checker reports faults that are not there. A
 * checker that cries wolf gets disabled, which is worse than not having one.
 */
function strip(sql) {
  return sql
    .replace(/\$\$[\s\S]*?\$\$/g, ' $$BODY$$ ') // function bodies: analysed separately
    .replace(/--[^\n]*/g, ' ')
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/'(?:[^']|'')*'/g, "''")
}

const sources = files.map((file) => {
  const raw = readFileSync(join(directory, file), 'utf8')
  return { file, version: version(file), raw, sql: strip(raw) }
})

// ---------------------------------------------------------------------------
// 3. Versions unique and contiguous

const seen = new Map()
for (const { file, version: v } of sources) {
  if (seen.has(v)) fail(`duplicate migration version V${v}: ${seen.get(v)} and ${file}`)
  seen.set(v, file)
}

const versions = [...seen.keys()].sort((a, b) => a - b)
for (let i = 1; i < versions.length; i++) {
  if (versions[i] !== versions[i - 1] + 1) {
    fail(
      `gap in migration versions: V${versions[i - 1]} is followed by V${versions[i]}. ` +
        `A gap is nearly always a migration that was not committed.`,
    )
  }
}
if (versions[0] !== 1) fail(`migrations should start at V1, but the lowest is V${versions[0]}`)

// ---------------------------------------------------------------------------
// 4. Dollar quoting balanced
//
// Counts both bare `$$` and tagged `$tag$` forms. An odd count means a body was
// opened and never closed.

for (const { file, raw } of sources) {
  const withoutLineComments = raw.replace(/--[^\n]*/g, ' ')
  const tags = withoutLineComments.match(/\$[A-Za-z_]*\$/g) ?? []
  const byTag = new Map()
  for (const tag of tags) byTag.set(tag, (byTag.get(tag) ?? 0) + 1)
  for (const [tag, count] of byTag) {
    if (count % 2 !== 0) {
      fail(`${file}: unbalanced dollar quoting — ${count} occurrences of ${tag}. An unterminated body swallows the rest of the file.`)
    }
  }
}

// ---------------------------------------------------------------------------
// Parse CREATE TABLE blocks
//
// Deliberately a regex rather than a SQL parser: a real parser is a native
// dependency, and everything below only needs to know a table's name, its
// column names and its constraint targets.

const tables = new Map() // name -> { file, version, columns:Set, body }

for (const { file, version: v, sql } of sources) {
  const createRe = /CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([a-z_][a-z0-9_]*)\s*\(/gi
  let match
  while ((match = createRe.exec(sql)) !== null) {
    const name = match[1].toLowerCase()
    const body = balancedFrom(sql, createRe.lastIndex - 1)
    if (body === null) {
      fail(`${file}: unbalanced parentheses in CREATE TABLE ${name}`)
      continue
    }
    const columns = new Set(
      body
        .split(/,(?![^(]*\))/)
        .map((line) => line.trim())
        .filter((line) => /^[a-z_][a-z0-9_]*\s+/i.test(line))
        .filter((line) => !/^(PRIMARY|FOREIGN|UNIQUE|CHECK|CONSTRAINT|EXCLUDE|LIKE)\b/i.test(line))
        .map((line) => line.split(/\s+/)[0].toLowerCase()),
    )
    if (tables.has(name)) {
      fail(`table ${name} is created twice: ${tables.get(name).file} and ${file}`)
    }
    tables.set(name, { file, version: v, columns, body })
  }
}

// A column added later still makes its table tenant-scoped.
//
// `CREATE TABLE` was the only source of columns, so a `tenant_id` introduced by
// `ALTER TABLE ... ADD COLUMN` — the normal way to add one to an existing table — escaped the RLS
// assertion completely. The most likely future version of the exact bug this checker exists for.
for (const { sql } of sources) {
  for (const match of sql.matchAll(
    /ALTER\s+TABLE\s+(?:IF\s+EXISTS\s+)?([a-z_][a-z0-9_]*)\s+ADD\s+COLUMN\s+(?:IF\s+NOT\s+EXISTS\s+)?([a-z_][a-z0-9_]*)([^;]*)/gi,
  )) {
    const table = tables.get(match[1].toLowerCase())
    if (table) {
      table.columns.add(match[2].toLowerCase())
      table.body += `,\n    ${match[2]} ${match[3].trim()}`
    }
  }
}

/** Returns the text between the parenthesis at [open] and its match, or null. */
function balancedFrom(text, open) {
  let depth = 0
  for (let i = open; i < text.length; i++) {
    if (text[i] === '(') depth++
    else if (text[i] === ')') {
      depth--
      if (depth === 0) return text.slice(open + 1, i)
    }
  }
  return null
}

// ---------------------------------------------------------------------------
// Tables created by the reference-taxonomy generator
//
// V5 creates 28 lookup tables through `create_reference_table(name)` rather than
// 28 near-identical CREATE TABLE blocks, so that they cannot structurally
// diverge. That is the right call for the schema and invisible to a regex, so
// the checker has to understand the generator.
//
// It does NOT hardcode the generated shape. It reads the generator's body and
// asserts the invariants there — a tenant_id column, RLS applied, an index
// leading with tenant_id. If someone edits the generator to drop any of them,
// this fails once, loudly, instead of silently exempting 28 tables.

const GENERATOR = 'create_reference_table'

const generatorBody = sources
  .map(({ raw }) => raw.match(new RegExp(`CREATE\\s+OR\\s+REPLACE\\s+FUNCTION\\s+${GENERATOR}[\\s\\S]*?\\n\\$\\$;`, 'i')))
  .find(Boolean)?.[0]

let generatorTrusted = false
if (generatorBody) {
  const invariants = [
    [/tenant_id\s+uuid\s+NOT\s+NULL/i, 'a NOT NULL tenant_id column'],
    [new RegExp(`(PERFORM|SELECT)\\s+apply_tenant_rls`, 'i'), 'a call to apply_tenant_rls'],
    [/CREATE\s+(UNIQUE\s+)?INDEX[^;]*\(tenant_id\s*,/i, 'an index leading with tenant_id'],
  ]
  const missing = invariants.filter(([re]) => !re.test(generatorBody)).map(([, what]) => what)
  if (missing.length > 0) {
    fail(
      `${GENERATOR}() no longer guarantees ${missing.join(' and ')}. ` +
        `Every table it generates loses that guarantee at once, so this is checked here rather than ` +
        `at each of its call sites.`,
    )
  } else {
    generatorTrusted = true
  }
}

if (generatorTrusted) {
  for (const { file, version: v, raw } of sources) {
    for (const m of raw.replace(/--[^\n]*/g, ' ').matchAll(new RegExp(`${GENERATOR}\\s*\\(\\s*'([a-z_][a-z0-9_]*)'`, 'gi'))) {
      const name = m[1].toLowerCase()
      if (tables.has(name)) {
        fail(`table ${name} is created twice: ${tables.get(name).file} and ${file} (via ${GENERATOR})`)
        continue
      }
      tables.set(name, {
        file,
        version: v,
        columns: new Set(['id', 'tenant_id', 'code', 'name', 'description', 'sequence', 'active']),
        body: '',
        generated: true,
      })
    }
  }
}

// ---------------------------------------------------------------------------
// 1. Tenant-scoped tables enable RLS

// Read from the raw source, not the stripped copy: strip() blanks string
// literals, which is exactly where the table name lives.
const rlsApplied = new Set()
for (const { raw } of sources) {
  for (const m of raw.replace(/--[^\n]*/g, ' ').matchAll(/apply_tenant_rls\s*\(\s*'([a-z_][a-z0-9_]*)'/gi)) {
    rlsApplied.add(m[1].toLowerCase())
  }
}

/**
 * Tables that carry a `tenant_id` but must not have RLS, with the reason.
 *
 * Kept explicit rather than inferred: an exemption should be an argument
 * somebody wrote down, not a pattern the checker guessed at.
 */
const RLS_EXEMPT = new Map([
  ['tenant', 'the tenant registry itself — it is the table RLS resolves against'],
])

let tenantScoped = 0
for (const [name, table] of tables) {
  if (!table.columns.has('tenant_id')) continue
  tenantScoped++
  if (RLS_EXEMPT.has(name)) continue
  // Generated tables get RLS from `PERFORM apply_tenant_rls(table_name)` inside
  // the generator — a variable, so there is no literal here to match. The
  // generator-invariant check above is what covers them, and it covers all 28
  // at once rather than 28 times.
  if (table.generated) continue
  if (!rlsApplied.has(name)) {
    fail(
      `${table.file}: table ${name} has a tenant_id column but never calls apply_tenant_rls('${name}'). ` +
        `Without it the table is readable across tenants — see ADR 0002.`,
    )
  }
}

for (const name of rlsApplied) {
  if (!tables.has(name)) {
    fail(`apply_tenant_rls('${name}') refers to a table that is never created`)
  } else if (!tables.get(name).columns.has('tenant_id')) {
    fail(`apply_tenant_rls('${name}') but ${name} has no tenant_id column — the policy cannot work`)
  }
}

// ---------------------------------------------------------------------------
// 2. Tenant-scoped tables have an index leading with tenant_id

const leadingTenantIndex = new Set()
const indexRe =
  /CREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:CONCURRENTLY\s+)?(?:IF\s+NOT\s+EXISTS\s+)?[a-z_][a-z0-9_]*\s+ON\s+([a-z_][a-z0-9_]*)\s*(?:USING\s+\w+\s*)?\(([^)]*)\)/gi

for (const { sql } of sources) {
  let match
  while ((match = indexRe.exec(sql)) !== null) {
    const table = match[1].toLowerCase()
    const first = match[2].split(',')[0].trim().toLowerCase()
    if (first === 'tenant_id') leadingTenantIndex.add(table)
  }
}

// A primary key beginning with tenant_id serves the same purpose, in either of
// the two forms Postgres accepts:
//
//   PRIMARY KEY (tenant_id, module_key)   -- table-level constraint
//   tenant_id uuid PRIMARY KEY ...        -- column-level, one column
//
// Missing the second form is what made this checker report `password_policy`
// on its first run. Worth stating because the two spellings are equivalent to
// Postgres and not at all equivalent to a regex.
for (const [name, table] of tables) {
  if (table.generated) {
    leadingTenantIndex.add(name)
    continue
  }
  const tableLevelPk = table.body.match(/PRIMARY\s+KEY\s*\(([^)]*)\)/i)
  if (tableLevelPk && tableLevelPk[1].split(',')[0].trim().toLowerCase() === 'tenant_id') {
    leadingTenantIndex.add(name)
  }
  if (/^\s*tenant_id\s+[a-z0-9_()]+\s+PRIMARY\s+KEY/im.test(table.body)) {
    leadingTenantIndex.add(name)
  }
}

for (const [name, table] of tables) {
  if (!table.columns.has('tenant_id')) continue
  if (RLS_EXEMPT.has(name)) continue
  if (!leadingTenantIndex.has(name)) {
    fail(
      `${table.file}: table ${name} is tenant-scoped but has no index leading with tenant_id. ` +
        `RLS appends tenant_id = current_tenant_id() to every query; an index that does not lead ` +
        `with it cannot satisfy the predicate and the planner falls back to a scan.`,
    )
  }
}

// ---------------------------------------------------------------------------
// 8. A GIN index over a scalar column requires btree_gin
//
// GIN ships operator classes for arrays, jsonb and tsvector — not for uuid, text or timestamp. A
// multicolumn GIN index that leads with a scalar (`USING gin (tenant_id, scopes)`) therefore fails
// outright unless `btree_gin` is installed, and the failure aborts the whole migration.
//
// This is here because it happened: V3 carried exactly that index and nothing installed the
// extension, so the schema would have deployed on nobody's machine.

const SCALAR_GIN_TYPES = /^(uuid|text|varchar|char|int|integer|bigint|smallint|numeric|date|timestamp|timestamptz|boolean)/i

const hasBtreeGin = sources.some(({ raw }) =>
  /CREATE\s+EXTENSION[^;]*btree_gin/i.test(raw.replace(/--[^\n]*/g, ' ')),
)

if (!hasBtreeGin) {
  const ginIndex =
    /CREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:CONCURRENTLY\s+)?(?:IF\s+NOT\s+EXISTS\s+)?[a-z_][a-z0-9_]*\s+ON\s+([a-z_][a-z0-9_]*)\s+USING\s+gin\s*\(([^)]*)\)/gi

  for (const { file, sql } of sources) {
    let match
    while ((match = ginIndex.exec(sql)) !== null) {
      const table = match[1].toLowerCase()
      const indexed = match[2].split(',').map((c) => c.trim().toLowerCase())
      const known = tables.get(table)
      if (!known) continue

      for (const column of indexed) {
        const declaration = columnDeclaration(known, column)
        // An array type is written `text[]`, and GIN indexes arrays natively — but its *element*
        // type is a scalar, so a naive prefix match reports `text[]` as a scalar. That false
        // positive appeared on the first run of this rule, on the very index that motivated it.
        const isArray = declaration !== null && /^\w+\s*\[\s*\]/.test(declaration)
        if (declaration !== null && !isArray && SCALAR_GIN_TYPES.test(declaration)) {
          fail(
            `${file}: GIN index on ${table} includes the scalar column '${column}', but no ` +
              `migration installs btree_gin. GIN has no operator class for that type, so the ` +
              `statement aborts and the migration fails.`,
          )
        }
      }
    }
  }
}

/** The type of a column, as written in its CREATE TABLE line. Null if not found. */
function columnDeclaration(table, column) {
  for (const line of table.body.split(/,(?![^(]*\))/)) {
    const trimmed = line.trim()
    if (trimmed.toLowerCase().startsWith(column + ' ')) {
      return trimmed.slice(column.length).trim()
    }
  }
  return null
}

// ---------------------------------------------------------------------------
// 5. Foreign keys reference a table that already exists

for (const { file, version: v, sql } of sources) {
  // The column list is optional in PostgreSQL: `REFERENCES tenant` targets the primary key and is
  // perfectly valid. Requiring `(` meant every such reference was invisible to this check.
  for (const m of sql.matchAll(/REFERENCES\s+([a-z_][a-z0-9_]*)\s*(?:\(|\s|,|$)/gi)) {
    const target = m[1].toLowerCase()
    const known = tables.get(target)
    if (!known) {
      fail(`${file}: REFERENCES ${target}, which no migration creates`)
    } else if (known.version > v) {
      fail(
        `${file}: REFERENCES ${target}, created later in ${known.file}. ` +
          `Flyway applies migrations in order, so this fails at deploy time.`,
      )
    }
  }
}

// ---------------------------------------------------------------------------
// 6. Destructive DDL needs an acknowledgement

for (const { file, raw } of sources) {
  const lines = raw.split('\n')

  // Matched against the statement, not the line. This repo's own house style wraps an
  // `ALTER TABLE` across two lines, so a line-anchored `DROP COLUMN` pattern missed every
  // destructive statement written the way the existing migrations write them.
  const destructive = lines
    .map((line, i) => {
      // Join with the following line so a wrapped statement is visible to the pattern, while the
      // reported line number stays the one the statement starts on.
      const statement = (line + ' ' + (lines[i + 1] ?? '')).replace(/\s+/g, ' ')
      return { line, statement, n: i + 1 }
    })
    .filter(({ statement }) =>
      /^\s*(DROP\s+TABLE|ALTER\s+TABLE\s+\S+\s+DROP\s+COLUMN|TRUNCATE)\b/i.test(statement),
    )

  for (const { line, n } of destructive) {
    const context = raw.split('\n').slice(Math.max(0, n - 6), n - 1).join('\n')
    if (!/DESTRUCTIVE:/i.test(context)) {
      fail(
        `${file}:${n}: destructive DDL without an acknowledgement — ${line.trim()}\n` +
          `      Add a comment containing "DESTRUCTIVE:" within the five lines above, explaining ` +
          `what is lost and why that is acceptable.`,
      )
    }
  }
}

// ---------------------------------------------------------------------------
// 7. Migrations are immutable

const checksums = Object.fromEntries(
  sources.map(({ file, raw }) => [file, createHash('sha256').update(raw.replace(/\r\n/g, '\n')).digest('hex').slice(0, 16)]),
)

if (updateManifest) {
  writeFileSync(manifestPath, JSON.stringify(checksums, null, 2) + '\n')
  console.log(`migration-check: manifest written with ${Object.keys(checksums).length} entries`)
} else if (existsSync(manifestPath)) {
  const recorded = JSON.parse(readFileSync(manifestPath, 'utf8'))
  for (const [file, hash] of Object.entries(recorded)) {
    if (!(file in checksums)) {
      fail(`${file} is in the checksum manifest but no longer exists. A released migration cannot be deleted.`)
    } else if (checksums[file] !== hash) {
      fail(
        `${file} has changed since it was recorded. Flyway will reject it with a checksum mismatch ` +
          `on any environment that already applied it — write a new migration instead.`,
      )
    }
  }
  const unrecorded = Object.keys(checksums).filter((f) => !(f in recorded))
  if (unrecorded.length > 0) {
    warn(
      `${unrecorded.length} migration(s) not yet in the manifest: ${unrecorded.join(', ')}. ` +
        `Run with --update-manifest once they are released.`,
    )
  }
} else {
  warn(
    `no checksum manifest at ${manifestPath}.\n` +
      `     Create one with --update-manifest once these migrations have actually been applied to a\n` +
      `     shared environment. Not before: until then they are still being corrected, and a manifest\n` +
      `     would fire on every legitimate fix. This warning is not a task to close.`,
  )
}

// ---------------------------------------------------------------------------

console.log(
  `${files.length} migrations checked, ${tables.size} tables (${tenantScoped} tenant-scoped), ${problems} problems`,
)
process.exit(problems > 0 ? 1 : 0)
