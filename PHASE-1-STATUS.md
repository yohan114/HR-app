# Phase 1 — Status

**Started:** 23 August 2026
**Plan:** [docs/phases/phase-1-walking-skeleton.md](docs/phases/phase-1-walking-skeleton.md)

Honest tracking of what is built, what is verified, and what is not.

---

## Entry criterion not met

The phase plan requires "Phase 0 exit criteria all met" before starting. **They are not.**

`TenantIsolationTest` has never run — Testcontainers needs Docker, which is unavailable in this
environment. Checked again on 24 August 2026 rather than assumed: no `docker` binary, no `psql`,
and although `C:\Program Files\PostgreSQL\18` exists and is on `PATH`, it holds only an orphaned
`data` directory — the `bin` folder is gone and the last server log is from May 2026. Nothing is
listening on 5432. There is no local PostgreSQL to fall back to. Everything built in Phase 1 sits on top of tenant isolation that is written but
unproven, and every new table below calls `apply_tenant_rls()` on the same unverified assumption.

Proceeding was a deliberate instruction, not an oversight. It is recorded here so nobody later
reads the green checkmarks and concludes the foundation was verified.

---

## Verified in this environment

| What | How |
|---|---|
| Backend compiles | `./gradlew compileKotlin` — BUILD SUCCESSFUL |
| **Module boundaries hold across six modules** | `ModuleStructureTest` — shared, tenancy, organisation, identity, employee, config. Re-verified against a deliberate `config.forms.internal` import: still fails. |
| **Spec and implementation agree** | `ApiContractTest` 5/5 — 18 paths, parity both directions |
| Backend test suite | **134 tests**, 125 passing; the 9 failures are all `TenantIsolationTest` (Docker) |
| **Custom field validation** | 19 tests — required/partial semantics, every data type, malformed-pattern tolerance, all-violations-at-once |
| **Field permission defaults** | 20 tests — the full read/write matrix for ordinary, sensitive, self and manage |
| **Masking** | 13 tests, asserting on what is *absent* from the masked value |
| **Projection and update** | 22 tests — hidden fields absent not null, all-or-nothing writes, leaver fields unreachable |
| OpenAPI spec valid | `spectral lint --fail-severity warn` — 0 errors, 0 warnings |
| All three clients regenerate | 28 models; Kotlin compiles, TypeScript type-checks under `--strict` |
| Web console still builds | `tsc --noEmit` — 0 errors |
| Infra checkers | 47 `.tf` files, 20 alerts, design tokens — all clean |
| **Migrations, structurally** | `migration-check.mjs` — 8 migrations, 67 tables, 65 tenant-scoped, 0 problems. Every check proven to fire against an injected fault by `migration-check.selftest.mjs` (11 faults detected, 2 clean cases stayed quiet). |
| Documentation links | 60 local markdown links resolve |
| **The generated Kotlin client can actually decode a response** | `ClientSerialisationTest` — 8 round-trip tests. Compilation proves the shape is valid and nothing about whether kotlinx can resolve a serialiser at runtime; these are different failures. Found a severe PATCH bug (below). |
| Entity/schema agreement | `EntitySchemaTest` — 5 checks, each proven to fire against an injected fault. Covers JPA mappings by reflection *and* raw `INSERT` statements by parsing, so the seeder's hand-written SQL is checked too. |
| Demo seed data is well-formed | `LocalDemoSeederTest` — 9 checks on the workforce graph: no dangling supervisor, no cycle, supervisors declared before reports, milestones actually land today |

## Not verified

| What | Why |
|---|---|
| **Migrations V5–V8** | No PostgreSQL. ~40 tables, 4 trigger/PL-pgSQL functions and an ltree hierarchy, none of it executed. Expect to fix SQL on first run. |
| **The `field_permission` query** | `FieldPermissionResolver` reads explicit grants with a hand-written join. The defaults around it are heavily tested with the query stubbed; the query itself has never run. |
| **The reporting-line visibility check** | `assertVisible` calls `isManagerOf`, which is one of the unexercised ltree queries. Until it runs, "a manager can open their team's records" is unproven in both directions — it could deny everyone or allow everyone. |
| **The ltree hierarchy** | `rebuild_employee_hierarchy` recurses and the cycle guard uses an `lquery` match. Both are plausible and neither has run. |
| **Directory search** | The tsvector weighting and keyset pagination are unexercised. |
| **RLS on the new tables** | Still no Docker, so the *policies* have never been exercised. The structural half — that every tenant-scoped table calls `apply_tenant_rls()` — is now covered by `migration-check.mjs`, which found a real gap on its first run (see below). That the policies then behave correctly at runtime remains unproven. |

---

## Delivered

### Schema (V5–V7)

| Migration | Contents |
|---|---|
| `V5__organisation_structure.sql` | Company, location, cost centre, department, designation, salary grade, geography, banking, and 28 reference taxonomies |
| `V6__employee.sql` | Employee master, materialised reporting hierarchy, census, qualifications, experience, bank accounts, documents, attachments |
| `V7__custom_fields.sql` | Field definitions and label overrides |
| `V8__employee_access.sql` | `employee.view.all`; default role grants rewritten and backfilled |

### Kotlin

- `com.hr.organisation` — entities, generic reference-data service, reference API
- `com.hr.employee` — employee entity, hierarchy repository, directory search, directory API
- `com.hr.config.forms` — field definitions, custom-field validator, form schema assembly and API

### API

Nine new endpoints: directory search, direct reports, two reference-data reads, the form schema,
and four employee-profile operations — `GET /v1/employees/me`, `GET /v1/employees/{id}`,
`PATCH /v1/employees/{id}` and `GET /v1/employees/{id}/form`. Documented, generated into all three
clients, and cross-checked by `ApiContractTest`.

---

## Decisions worth knowing

**28 reference taxonomies, one entity type.** They are generated from a single migration function
so they cannot structurally diverge, and they are read-mostly lookup data with no behaviour. One
`ReferenceItem` type plus a table-name parameter models that honestly; twenty-eight JPA entities
would be a thousand lines of boilerplate, each a place to forget something. The table name is
interpolated into SQL — safe only because it comes from an enum, never from user input.

**Materialised ltree hierarchy rather than recursive supervisor walks.** "Everyone under this
manager" is asked on nearly every screen a manager opens. A recursive CTE per request against a
10,000-employee tenant is the query that looks fine in development and falls over in production.
Re-parenting a manager with 200 reports rewrites 200 rows — acceptable, because reads vastly
outnumber writes here.

**Reporting cycles are rejected at write time.** A cycle makes the hierarchy rebuild recurse
forever and, worse, makes approval routing loop — a leave request that can never reach anyone.

**No JPA associations.** Relationships are raw ids. `@ManyToOne` would make a profile read a
candidate for lazy-loading through department → company → parent company, and the resulting N+1 is
the most common cause of a slow page in a JPA application.

**The directory omits sensitive fields at the query level, not by filtering.** A serialisation
filter someone forgets to apply leaks; a column never selected cannot.

**`display_name` is stored, not derived.** Naming order differs by market, and several target
countries use a mononym or patronymic that does not reconstruct from a first/last pair. Deriving
it would render some people's names wrong, which is not a cosmetic defect.

**Salary is deliberately not on `employee`.** It belongs in an effective-dated table (Phase 3) so
"what did they earn in March?" is answerable. A mutable column here would quietly make historical
payroll unreproducible.

**Custom field keys must be legal identifiers in Kotlin, Swift and TypeScript**, and may not shadow
a built-in column. Both enforced by constraint and trigger — a field named `firstName` would make
the API payload ambiguous, and which value won would depend on serialisation order.

**The form schema covers the whole form, not just the custom part.** Built-in and tenant-defined
fields are merged into one ordered list of sections. A hardcoded form with a custom-fields lump at
the bottom cannot interleave a tenant's field with the built-in one it relates to, and puts a
customer's mandatory field below the save button.

**Permissions are applied to the schema, not left to the client.** Fields the caller may not see
are absent; fields they may see but not change arrive with `editable: false`. A client cannot leak
what it never received.

**The schema version is a hash, not a timestamp.** Two servers behind a load balancer must produce
the same version for the same configuration, or a client sees the schema "change" on every other
request and re-renders constantly.

**The validator collects every violation rather than stopping at the first.** Reporting one at a
time turns filling a form into a guessing game — fix the date, resubmit, get told about the phone
number.

**A malformed validation regex does not reject the user's input.** It is a configuration error the
user cannot fix, and cannot even see. The value passes and the problem surfaces to whoever
configured the field.

**Unknown field keys are rejected, not ignored.** Silently dropping a value someone typed is worse
than refusing it: they watch the field save, return later, and find it empty with no explanation.

---

## Field-level permissions

Recorded as [ADR 0006](docs/adr/0006-field-level-permission-defaults.md), with the alternatives and
what each would have cost. The summary below is the short version.

The default decides the security posture, not the configured rules — almost every field has no
rule, so what happens in their absence is what happens to nearly everything. Neither uniform answer
works. Deny-everything is safe and unusable, and its predictable outcome is a customer granting
everything to everyone to make the product work. Allow-everything is unsafe twice over: a new
sensitive column is exposed until somebody remembers to restrict it, and an ordinary employee with
no rules configured could rewrite their own join date.

So the default is split:

| | reading | writing |
|---|---|---|
| ordinary field | READ | only with `employee.manage`, or self-service on an allow-listed field |
| sensitive field | HIDDEN — READ on your own record | never, without an explicit grant |

**Write is never the default.** Reading is permissive because a colleague's department is not a
secret. Writing is not, because an unintended write is silent and corrupts data that later feeds
payroll.

**`employee.manage` does not confer sight of sensitive fields.** An HR administrator can maintain a
profile without being shown national identity numbers. That is what lets a customer separate
"maintains records" from "handles identity documents" — and if the default granted both, they never
could.

**The sensitive list is in code, not configuration.** A tenant should not be able to make their
employees' identity numbers world-readable by clearing a row. Adding a field to it is a one-line
change made in the same commit as the column, while the person adding it still has the context to
decide who should see it.

**A projection, not a Jackson filter.** A `@JsonFilter` applies to a serialisation path, and a new
endpoint returning the entity by some other route — nested, in a list wrapper, in an event payload
— silently skips it. The leak is invisible in review, because a missing annotation looks like
nothing at all. Here there is no entity-to-JSON path: the only way to get an employee into a
response takes the caller's context and cannot produce output without consulting permissions. The
cost is an explicit accessor per field, and that cost is the point.

**Hidden means absent, not null.** A null tells the caller the field exists and they are not
allowed it, which is itself information — and invites a client to render a disabled input for
something they should not know about.

**Masking happens on the server.** Sending the real value and letting the client show dots is a
hint, not a control: the value is still in the response body, the HTTP cache, the client's local
database, and any log that captures payloads.

**Partial masks are for account numbers, not text.** "The last four" is an idiom with one purpose —
confirming the account you are about to pay into. Applied to text it just hands over the end of the
word. The first version of this masked by length, and its own test caught `Colombo` becoming
`••••ombo`. Now the test is what the value *is*: a number sequence of at least eight digits, or
nothing survives.

**Self-service is authorised by ownership, not by a permission.** Otherwise every employee would
need a permission reading, in effect, "may view employees" — and that same permission would be the
one gating access to everyone else's records. The self-writable list is deliberately short:
contact details, preferred name, photo. Not name, date of birth or join date, which appear on
statutory filings and change by request with evidence attached.

**A caller who may not see a record gets 404, not 403.** A 403 confirms the record exists, which
turns the endpoint into an oracle: walk ids, and the ones answering 403 are real employees.

**Roles combine to the most permissive grant.** Additive, like every other permission in the
system. A role that *removes* access would force an administrator to reason about the interaction
of every role a user holds to predict what they can see. The safety property comes from the
sensitive-field default, not from letting one role veto another.

**Updates are all-or-nothing.** Every value is coerced before any is assigned, so a malformed date
in the fifth field does not leave the first four applied. The transaction would roll back anyway —
but relying on that would make the writer unsafe to call from anywhere else, and "safe only where I
happened to put it" is how a helper becomes a bug later.

**`status`, `resignDate` and `lastWorkingDate` are not writable through the profile.** They are
outcomes of the joiner/leaver processes, which carry approvals and side effects: payroll cut-off,
access revocation, final settlement. A PATCH that could set `status: EXITED` would skip all of it
and leave someone paid but locked out, or the reverse.

**The edit form is built from the same context as the payload**, so what the client is offered and
what the server will accept cannot drift apart. Deriving the form separately is how you ship an
input that saves nothing.

---

## What the checks caught this round

Three findings, one from each kind of check, all on first run.

**`ModuleStructureTest` — `config.forms` was not a published API.** Spring Modulith exposes a
module's *root* package only, and `com.hr.config.forms` is a sub-package, so the employee module
could not legally validate a custom field value. Fixed with a `@NamedInterface` rather than by
flattening the types into `com.hr.config`: that module will grow to own approval flows, numbering
schemes and notification templates too, and a single root package holding four published surfaces
tells a caller nothing about which one it has coupled itself to. Re-verified afterwards with a
deliberate `config.forms.internal` import — still correctly rejected, so the named interface
opened exactly one package and no more.

**`ApiContractTest` — four undocumented endpoints.** Working as designed. This is the third time it
has caught the more dangerous direction of drift.

**`FieldMaskerTest` — the masker leaked.** My own test, on a value I chose to be realistic: masking
by length turned `Colombo` into `••••ombo`. Length was the wrong test; the rule is now what the
value *is*. Worth recording because the original was plausible, passed review in my head, and would
have shipped a redaction that redacts nothing on every short address line in the product.

A fourth thing, checked and found *not* to be a problem: `openapi-generator` sets `inputSpec` from a
URI string, which looked like it would leave Gradle blind to the spec's contents. The plugin tracks
the file separately and the clients did regenerate. Recorded because the reasoning was sound and the
conclusion was wrong — the fix would have been noise.

---

## A real RLS gap, found without a database

`tenant_module` — created in **V1**, the first migration — has a `tenant_id` column and never
called `apply_tenant_rls()`. It has been missing since the schema was written.

This is precisely the regression ADR 0002 names as the one worth building a test for, and
`TenantIsolationTest` has the assertion that would have caught it — *"every tenant-scoped table has
row level security enabled"* — which has never run, because it needs Docker. The table's own
comment says it "drives both API authorisation and the mobile navigation shell", so the primary
isolation control was absent on a table that gates authorisation.

Fixed at source in V1 rather than in a V9 fixup. No database has ever applied these migrations, so
there is no checksum to violate, and a later fixup would leave permanent archaeology for a table
that never actually shipped without RLS.

What found it was a new checker, `backend/scripts/migration-check.mjs`, written specifically to
recover the checks that are currently blocked on Docker. It runs on Node alone and asserts:

| Check | Why |
|---|---|
| Every table with `tenant_id` calls `apply_tenant_rls()` | The realistic regression. Same assertion as `TenantIsolationTest`, without needing a database. |
| Every tenant-scoped table has an index **leading** with `tenant_id` | RLS appends `tenant_id = current_tenant_id()` to every query; an index that does not lead with it cannot satisfy the predicate, and the table quietly scans forever |
| Versions unique and contiguous | A gap is nearly always an uncommitted migration; a duplicate fails only on the second machine to run it |
| Dollar-quoted blocks balanced | An unterminated `$$` swallows the rest of the file and reports the error at the wrong place |
| Foreign keys reference a table that already exists | Flyway applies in order, so a forward reference fails at deploy time, after review |
| Destructive DDL carries a `DESTRUCTIVE:` acknowledgement | Sometimes right, always worth a second pair of eyes |
| Released migrations are immutable | Flyway rejects a changed checksum long after the edit looked harmless |

Two of those checks initially reported faults that were not there — `password_policy` (a
column-level `PRIMARY KEY` on `tenant_id`, equivalent to Postgres and invisible to the regex) and
the 28 taxonomy tables generated by `create_reference_table()`. Both are fixed. The generator case
is handled by reading the generator's *body* and asserting the invariants there rather than
hardcoding the shape it produces, so changing the generator to drop RLS fails once and loudly
instead of silently exempting 28 tables.

The checksum manifest is deliberately **not** created yet. These migrations have never run against
PostgreSQL and will need correcting when they do; a manifest now would fire on every legitimate
fix, and the response to that is always to delete the manifest rather than reconsider the edit.
The warning says so, so nobody closes it as a task.

---

## The generated client could not perform a partial update at all

`GET` worked. Every `PATCH` from the generated Kotlin client would have failed.

The generator emits `Json { encodeDefaults = true }` with no `explicitNulls` setting, so every
unset nullable property is serialised as an explicit `null`. A client changing one phone number
sent:

```json
{"employeeCode":null,"firstName":null,"lastName":null, … ,"mobile":"0771234567", … }
```

Forty explicit nulls. Against the server built in this phase, that is a `403 FIELD_NOT_WRITABLE`
listing every field the caller may not write — a self-service user updating their own phone number
gets a wall of permission errors — or, for a caller who *can* write those fields, a
`REQUIRED` violation for trying to blank the mandatory ones. It could not succeed either way.

Nothing in the build could have caught it. The client compiles; the spec lints; the contract test
compares paths and operations, not encoder behaviour. The failure only exists at runtime, and
nothing in this repo had ever deserialised — or serialised — anything with the generated client.
`ClientSerialisationTest` now does, and it is where this surfaced.

**Two changes, because the obvious fix creates a second problem.** Setting `explicitNulls = false`
lets partial updates work, but then a typed client can never say "clear this field" — kotlinx
encodes "never set" and "deliberately null" identically, so suppressing one suppresses both. So:

1. `omitNullsWhenEncoding()` in `build.gradle.kts` patches the generated `Serializer.kt`. It
   asserts on the line it matches, so a generator upgrade that moves it fails the build rather
   than silently reverting the fix.
2. `clearFields: ["middleName"]` on the update body is the spelling that survives code generation.
   `EmployeeWriter.expandClearFields` turns it back into a plain null before anything downstream
   sees it. A field both set and cleared in one request is rejected rather than resolved by
   precedence — two contradictory instructions, and picking a winner would make a save silently do
   the opposite of what was intended.

Worth recording that the failure I *expected* here did not happen. Three independent design agents
predicted that `@Contextual Map<String, JsonElement>` would throw at runtime, because
`Serializer.kotlinxSerializationAdapters` registers contextual serialisers for `BigDecimal`,
`LocalDate` and `UUID` but none for `Map` or `JsonElement`. It round-trips correctly — including
nested objects. The test written to catch that caught something worse instead, which is the usual
way of it.

---

## A hole closed while passing through

`FormSchemaService` exempted custom fields from the visibility filter (`|| it.custom`). That was
harmless while nothing supplied `visibleFields`, and would have become a leak the moment something
did — defeating the check for precisely the fields a tenant is most likely to put something
sensitive in. A "Disciplinary notes" field must be restrictable. The exemption is gone and callers
pass custom keys explicitly; `CustomFields.activeFieldKeys` exists to make that easy to get right.

---

## Demo data: a workforce, not a login

`LocalDemoSeeder` previously created a tenant and one ADMIN user with no employee record. Enough
to get past the login screen; not enough to exercise anything behind it. It now seeds a company,
a location, three departments, five designations, **nine employees three reporting levels deep**,
and three accounts.

Three accounts rather than one, because each makes a different authorisation rule visible without
reading the code:

| Account | Holds | Demonstrates |
|---|---|---|
| `admin` | everything, linked to the CEO's record | The `subjectIsSelf` path, which was previously unreachable — the admin had no employee record, so `Caller.employeeId` was always null |
| `manager` | `employee.view` **without** `employee.view.all` | Reporting-line visibility. Three direct reports, sees nobody else. |
| `employee` | directory only | Self-service authorised by ownership rather than by a grant — the property easiest to break and hardest to notice |

The fastest demonstration of the field-permission work is now two requests: as `employee`,
`GET /v1/employees/me` returns `dateOfBirth`; `GET /v1/employees/{a-colleague}` omits it entirely.
That was not showable before, because showing it needs two people.

**Dates are relative to today.** Birthdays and anniversaries are computed from `LocalDate.now()`
so something always falls today and more fall later in the week. Fixed dates would make the
milestone cards correct and empty for most of the year, which is the same demo problem in slower
motion. 29 February steps back a day rather than being handled properly — a leap-day birthday
would vanish from the card three years in four, which is worse than being one day out on one date.

**The names are Sinhala, Tamil and Burgher.** Not decoration: `V6` indexes the search vector with
the `simple` dictionary rather than `english` specifically so these tokenise correctly, and a
workforce of Anglophone names would leave that decision unexercised.

Still unrun, like everything else here — but `EntitySchemaTest` now parses raw `INSERT` statements
and checks them against the migrations, so a mistyped column in the seeder fails in CI rather than
at somebody's first startup. Verified by injecting one.

---

## iOS: HTTP layer and background scheduling

The largest gap in the phase. `ios/` had a sync engine, an outbox and a secure token store — and
no way to make a network request, and nothing that ever called any of it. Every "the app refreshes
on foreground / after a drain / every 15 minutes" claim in the design docs was true on Android and
aspirational on iOS.

Added, mirroring the Android layer rather than sharing code with it (ADR 0004):

| File | Role |
|---|---|
| `Network/APIConfiguration.swift` | Base URL and tenant code |
| `Network/APIError.swift` | The error envelope, classified by what the caller can do |
| `Network/TokenProvider.swift` | Session state and **single-flight** refresh |
| `Network/HTTPClient.swift` | URLSession, bearer injection, one retry on 401 |
| `Sync/OutboxHTTPSender.swift` | Status → outcome mapping, identical to Android's |
| `Sync/OutboxDrain.swift` | The drain and sync runs, as plain async functions |
| `Sync/SyncScheduler.swift` | `BGTaskScheduler` registration and foreground triggers |

**The single-flight refresh is a correctness requirement, not an optimisation.** The server rotates
refresh tokens and treats reuse as theft, revoking the whole family (`AuthenticationService`,
RFC 9700 §4.14.2). So the obvious client — refresh whenever you get a 401 — destroys itself: five
requests in flight when the token expires means five refreshes, one succeeds, four present a token
the server has just marked used, and the server correctly concludes the token was stolen. The user
is signed out of every device because a screen loaded five widgets. Nothing in the server is wrong.
`TokenProvider` is an actor holding one optional `Task`; concurrent callers await the same one.
Seven tests cover it, including ten simultaneous callers producing exactly one refresh.

**Two limitations, stated rather than worked around:**

- **Background work cannot authenticate after a cold launch.** The refresh token is sealed behind
  Face ID and there is no UI to prompt in, so a background task with no in-memory session returns
  `unauthenticated` and defers to the next foreground. The alternative — storing the refresh token
  unprotected so background tasks can use it — discards the entire point of sealing it.
- **`BGTaskScheduler` guarantees nothing.** Windows depend on how often the user opens the app,
  battery state and Low Power Mode; a force-quit app gets none at all. That is not a problem to
  design around, it is the reason the app is offline-first (ADR 0003).

### Two real bugs caught without a compiler

**`OutboxEntry` has both `id` and `idempotencyKey`, and I used `id`.** Still a stable key, so it
would have looked correct in isolation — but a *different* key from Android's, so the same logical
retry would read as a new request. Retry safety is a property of the protocol, not of each client
separately.

**A protocol nested inside an actor.** Swift forbids it, unlike every other declaration kind. Moved
to file scope.

Also caught: `await tokens?.accessToken()` on a method already returning `String?` yields `String??`,
so a single `guard let` would unwrap only the outer layer and send an unauthenticated request.

### Verification, and its limits

There is no Swift toolchain on this machine — no `swift`, `swiftc` or `xcodebuild`. **None of this
has been compiled.** `ios/scripts/swift-sanity.mjs` is what exists instead: balanced delimiters,
nested-protocol detection, tab characters. It is not a compiler and does not pretend to be; it
catches the class of error that would otherwise cost a teammate on a Mac an hour on code they did
not write.

Its first run reported three *correct* files, because `\s` matches newlines and a blank line before
a file-scope protocol read as indentation. Caught only because those files predated the rule — which
is the argument for pointing a new checker at known-good code before trusting it. Both rules are now
verified against injected faults.

The real fix is `.github/workflows/ios.yml`, which did not exist: iOS had no CI at all. It now runs
the sanity check on Linux and `swift build`/`swift test` plus an iOS-Simulator build on `macos-14`.
That last step matters because `BGTaskScheduler` is behind `#if os(iOS)`, so a macOS-only build
would leave the branch that actually runs in production unchecked.

**Expect the first CI run to fail.** It will be the first time any Swift in this repository has been
compiled.

---

## Adversarial review: 30 confirmed defects

Five reviewers over the Phase 1 backend — authorisation, SQL, the write path, the client contract,
and whether the checkers themselves hold — with every finding then re-examined by an independent
skeptic instructed to default to *refuted*. **36 findings, 30 confirmed, 6 refuted.**

The refutation pass earned its place. Six did not survive it, and an earlier review in this project
confidently reported a kotlinx deserialisation crash that did not exist when actually tested.

### Fixed

**The migration set could not run on any managed database.** Two independent defects, either one
fatal, and both invisible locally because Docker's `postgres` user is a real superuser:

- `V3` creates `USING gin (tenant_id, scopes)`. GIN ships no operator class for `uuid` — that lives
  in `btree_gin`, which nothing installed. The statement aborts and the migration fails.
- `V1` declares `current_tenant_id()` as `LEAKPROOF`, which PostgreSQL restricts to genuine
  superusers. A managed instance does not give you one: the RDS master role holds `rds_superuser`,
  a role membership, which is not the same thing. The **first** migration dies.

  Now applied in a `DO` block that tolerates `insufficient_privilege` and warns. Worth being
  precise about what that costs: `LEAKPROOF` lets the planner push the tenant predicate below a
  security barrier — a *performance* property. Isolation does not depend on it, because PostgreSQL
  already refuses to evaluate a non-leakproof user function ahead of an RLS policy. The original
  comment overstated this, and has been corrected.

**`optionalUuid` and `optionalDate` silently cleared a field for any non-string value.**
`PATCH {"departmentId": 12345}` unset the department and answered **200** — data loss behind a
success response, the one outcome this codebase refuses everywhere else. Now `WRONG_TYPE`.

**PATCH returned the pre-increment version.** Hibernate bumps `@Version` at flush, which happens at
commit — after the projection had already read it. The response carried version N while the
database held N+1, so a client following the documented `If-Match` protocol would 409 on its second
save against a record nobody else had touched. Now `saveAndFlush`.

**Two leaks in `FieldMasker`**, both in code written specifically to prevent leaks:

- `contains('@')` is not a test for an email address. An address line such as
  `Flat 3 @ 42 Galle Road, Colombo` was routed to the email branch, which preserves everything after
  the last `@` — so the mask published the entire street address.
- Only a *built-in* date arrives as a `LocalDate`. A tenant-defined `DATE` custom field lives in
  JSONB and arrives as `"1990-05-02"`, which passed the number test — eight digits once the dashes
  are stripped — and was masked as `••••5-02`, publishing the month and day of a date of birth.

**`EntitySchemaTest` cited a test that did not exist.** Its comment claimed
`MigrationSchemaParserTest` verified the hardcoded reference-table column list. Nothing did. A false
claim in a comment is worse than an unchecked mirror, because it stops anyone adding the check. The
test now exists.

`migration-check.mjs` gained a rule for the GIN class of error. Its first run produced a false
positive on `text[]` — an array, which GIN indexes natively, whose element type is a scalar — caught
because it fired on the very index that motivated the rule.

### Confirmed, not yet fixed

Recorded so none of it is lost. Two are worth reading before the next client change: the web console
cannot save a built-in `DATE` at all, and a `MASKED` field emits `••••` where the spec promises a
date or uuid, which aborts decoding in the Kotlin and Swift clients — masking and codegen have not
been reconciled.

Five of the twenty-four are defects in the checkers themselves, which is the finding I would have
been least likely to reach alone: `css-class-check` cannot see a template-literal `className`,
`migration-check` misses a two-line `DROP COLUMN` and a `tenant_id` added by `ALTER TABLE`, and
`ApiContractTest` compares only path and method, so a renamed query parameter is undetectable drift.
The status table above cites those checkers as evidence, so their blind spots are load-bearing.

| Severity | Area | Finding |
|---|---|---|
| high | contract | Web console cannot save any built-in DATE field: the generated TS client calls `toISOString()` on the string it is given |
| high | contract | A MASKED field violates its own declared type: the server emits `••••` where the spec says date/uuid/integer, aborting decode in the Kotlin and Swift clients |
| medium | authorisation | An explicit `field_permission` row silently revokes self-service and self-read on the owner's own record |
| medium | authorisation | `GET /v1/forms/{entityType}` applies no field permissions, contrary to its own OpenAPI contract |
| medium | sql | Partitions of `audit_log`, `change_feed` and `mutation_log` have no RLS and no append-only lockdown — cross-tenant read and audit deletion via the partition |
| medium | sql | `mutation_log`'s idempotency uniqueness is defeated by the partition key — the same idempotency key inserts twice |
| medium | write-path | There is no way to clear a custom field via `clearFields`; the web console sends exactly that and gets 400 `UNKNOWN_FIELD` |
| medium | write-path | A `customFields` value that is not a JSON object is silently discarded and the save returns 200 |
| medium | write-path | A whitespace-only string bypasses every type check and is stored verbatim in a typed JSONB slot |
| medium | contract | Clearing a tenant custom field from the web console is rejected with 400 `UNKNOWN_FIELD` |
| medium | contract | The employee form schema names three `referenceTable`s that no endpoint serves, and one bad name 404s the whole batch reference call |
| medium | checkers | `css-class-check.mjs` never sees classes in template-literal or render-prop `className`s — including `btn`, `badge` and `shell__link` |
| medium | checkers | `migration-check` misses a `DROP COLUMN` written across two lines — the repo's own house style |
| medium | checkers | `migration-check`'s foreign-key ordering check is blind to `REFERENCES <table>` without a column list |
| medium | checkers | `migration-check` fails correct SQL when a tenant_id-leading index comes from a table-level `UNIQUE` constraint |
| medium | checkers | A `tenant_id` column introduced by `ALTER TABLE ADD COLUMN` escapes `migration-check`'s RLS assertion entirely |
| low | authorisation | `genderTypeId` and `nationalityId` are omitted from `ALWAYS_SENSITIVE` while their four sibling columns are in it |
| low | sql | No index supports the directory's `ORDER BY` / keyset predicate — every directory page is a full scan and sort |
| low | sql | The reporting-cycle guard reads stale hierarchy state, so a set-based supervisor `UPDATE` escapes it and the rebuild recurses until the backend aborts |
| low | sql | `bank_account_split_value_present` allows a negative FIXED split amount |
| low | write-path | A NUMBER custom field with an out-of-range JSON literal throws `NumberFormatException` and becomes a 500 |
| low | write-path | A tenant-configured validation pattern is applied to user input with no backtracking guard |
| low | checkers | `ApiContractTest` sees only `@RestController` classes and only path+method, so a renamed query parameter is undetectable drift |
| low | checkers | `EntitySchemaTest`'s reference-table mirror — **fixed above**, listed for completeness |

---

## The web console can now show a person

Two screens, and the first thing in this phase that can be *seen* working rather than reasoned
about. The web console is the only client that builds and runs in this environment, so it is where
the server-driven form schema and the field permissions become demonstrable instead of asserted.

**Directory** (`/directory`) — debounced search, cursor pagination with a back-stack, `aria-live`
on the results so a screen-reader user is told the table changed under them. Open to every
authenticated employee, with no permission guard, because the endpoint is safe by virtue of what it
never selects rather than by who may call it.

**Employee profile** (`/employees/:id`) — **rendered entirely from `GET /v1/employees/{id}/form`.**
There is no hardcoded field list in the file. The schema decides which sections exist, which fields
are in them, their order, labels, and which are editable; the page walks it. That is the payoff for
the form-schema engine: a tenant adds a custom field in the admin console and it appears here with
no code change.

It also means the page contains **no client-side permission check at all**, deliberately. Fields
the caller may not see are absent from both the schema and the payload, so "not received" and "do
not draw" are the same condition. Adding a client-side check would duplicate a decision the server
has already made, and duplicated authorisation drifts.

Three details worth keeping:

- `editable: false` renders as text, not a disabled input. A disabled input looks like something
  you could gain permission to use, and a form of them reads as unfilled rather than read-only.
- An emptied input sends `clearFields`, not `null` — the typed clients must omit nulls, so a null
  cannot survive code generation (see the `explicitNulls` fix).
- The save sends `If-Match` with the version it loaded, so a concurrent edit is a 409 rather than a
  silent overwrite.

### A checker for the failure that survives every other check

Neither `tsc` nor the Vite build catches a class name with no matching CSS rule. It compiles,
builds, ships, and renders unstyled — a visual-only failure found by a person looking at the
screen, which is the most expensive way to find it. I had written ten such names before noticing.

`web/scripts/css-class-check.mjs` closes it, and found two **pre-existing** ones on its first run:
`.page` — used by `Overview.tsx` since it was written — and `.card__actions`. Neither had ever been
styled. It is deliberately one-directional: unused CSS rules are often intentional, and a checker
that fires on them gets muted.

Its own first run also produced four false positives, from taking `?` and `:` out of a ternary
inside `className={…}` as class names. Fixed by requiring a CSS-identifier shape, then verified
against an injected typo.

---

## The home screen is decided

[docs/home-composite.md](docs/home-composite.md). Three designs, written independently from
different angles, each judged by three adversarial reviewers reading the actual repository. **Every
design scored 4–5 out of 10**, and that unanimity was the finding: the reviewers were not choosing
between good options, they were finding the same defect in all three.

**All three put the home screen's freshness marker outside the tables it describes, and all three
produce a permanently blank home screen.** `SyncEngine.resetScope` clears *every* applier in the
shared registry for *any* scope, so an unrelated `410 SYNC_CURSOR_EXPIRED` wipes the home tables
while a bespoke `home_meta` ETag survives. The next refresh returns 304, the client writes nothing,
and home is empty forever — rendered as the *designed* empty state, so indistinguishable from a
tenant with no cards. No error, no retry, nothing in telemetry. Recovery is a reinstall.

Each design justified the bespoke table by saying home has no cursor and cannot use `sync_cursor`.
That is false: `cursor` is nullable on both platforms and there is a `lastSyncedAt` column.
`scope='home', cursor=NULL` is exactly the slot. Home is a sync scope, and the composite endpoint is
a cold-start convenience over the same resolvers — not a second write path with its own rules.

Four further decisions the review forced, each recorded with its reasoning in the doc: no
conditional requests (both generated clients treat 304 as an *error* — Swift throws, Retrofit
reports failure — and a 304 costs the server as much as a 200); counts ship with their identity sets
so the client can subtract its own outbox; no closed OpenAPI enums on the wire, because an unknown
card type from a newer server would abort decoding of the entire response and blank the screen; and
expression indexes plus tenant-scoped caching before the milestones query meets a 10,000-employee
tenant at 09:00.

### A security finding about code that already exists

One reviewer found that a card gated on `FieldPermissions.accessFor(..., "joinDate")` alone would
leak. `joinDate` is not in `ALWAYS_SENSITIVE`, so it resolves to `READ` for every colleague and the
check passes for every active employee — while `V8` grants the default `EMPLOYEE` role only
`employee.directory`, so those same users get a **404** from `GET /v1/employees/{id}` on the very
records the card just listed.

The cause is that a per-field check is not equivalent to `EmployeeService.assertVisible`. **A field
check that passes is not evidence the caller may see the record**; it is only evidence that if they
may, they may see that field of it. No live bug — the card does not exist — but the trap is one line
away for whoever builds it, so the warning now sits in the doc comment on `FieldPermissions`, which
is the type they will be holding when they make the mistake.

---

## What the home-composite design research turned up

Three independent designs for `GET /v1/mobile/home` were produced from different angles
(offline-first, maximally server-driven, minimal-and-fast) and put through an adversarial judge
panel. Every one scored 4–5 out of 10. That unanimity is the useful result: the problem is not
that the wrong shape was chosen, it is that **the foundations the endpoint needs are not there
yet**, and all three designs had to build on sand in the same places.

Each item below was re-checked directly against the repo rather than taken from the agents:

| Verified | Consequence for P1-BE-25 |
|---|---|
| **iOS has 6 Swift files, no `URLSession`, no `BGTaskScheduler`** | Nothing on iOS can fetch anything. Every refresh-cadence claim — foreground, periodic, after-outbox-drain — is Android-only. Shipping the endpoint means exactly one client exercises it. |
| **Android has no `NavHost` and no `NavController`**; `DeepLinks` is a constants object nobody reads | Cards would render but not be tappable. A directory card you cannot open is a screenshot, not a feature. |
| **`LocalDemoSeeder` creates one ADMIN user with no employee record and zero employees** | Every card demos empty, and the `employeeId == null` branch is the *only* path a developer ever exercises locally. The Phase-1 demo step is "change the card config, pull to refresh, cards reorder" — reordering two blank tiles. |
| **No index on `employee.date_of_birth` or `employee.join_date`** | Birthday and anniversary cards are `EXTRACT(MONTH/DAY)` predicates on the most-called endpoint in the product. Sequential scan per tenant per request. Needs expression indexes, and the plan is unverifiable here anyway. |
| **`GET /v1/sync` has no resolvers; nothing writes `change_feed` or `mutation_log`** | The home endpoint was specified as a stand-in "until `/v1/sync` lands". It has not landed, so the stand-in would become the permanent path for the app's most-viewed screen. |
| **`enabledModules` is hardcoded to `["identity"]`** and no `module_key` vocabulary exists anywhere | Role-adaptive navigation has nothing to adapt to. Spun off as its own task, because inventing the module taxonomy in passing would be deciding the app's whole navigation structure as a side effect. |
| **Android uses `fallbackToDestructiveMigration()`** | Any new local table for home wipes the outbox on upgrade — losing queued user writes to add a cache. |

**So P1-BE-25 is not the next thing to build.** An endpoint whose only consumer cannot fetch,
cannot navigate, and has no data to show is not a walking skeleton; it is a limb with nothing
attached. The sequencing that follows from this is in the table below.

One design decision did survive all three proposals and all five judges intact, and is worth
recording now because it will not be re-derived: **the home endpoint must be a transport, not a
render path.** The screen paints from local tables in under 100 ms whether or not the fetch
succeeded, and the response is applied through the same `ChangeApplier` seam as sync — so that
retiring it when `/v1/sync` lands removes one controller and one call, leaving every table,
applier and renderer untouched. Designs that made the endpoint the render path failed the
offline-first requirement outright.

---

## Next

Reordered from the previous plan, for the reasons above.

| Order | Task | Why here |
|---|---|---|
| ~~1~~ | ~~Seed real demo data~~ | **Done** — see below. |
| ~~2~~ | ~~**P1-IOS** HTTP layer + scheduler~~ | **Written, not compiled** — see below. |
| 2b | Get the iOS package **compiled** | `.github/workflows/ios.yml` now builds and tests it on `macos-14`. The first run is the first time any Swift in this repository has been compiled, and it will find things. |
| 2c | **P1-IOS** remaining: UI shell | The largest single gap in the phase. Until it exists, "native on both platforms" is one platform, and every endpoint is validated against one client. |
| 3 | **P1-AND-07** nav shell | `NavHost`, `NavController`, and `DeepLinks` actually wired to the launch intent. Nothing on any screen can be tapped until this lands. |
| 4 | P1-BE-25/26 | The home composite, on the foundations above rather than ahead of them. Design already researched. |
| 5 | P1-AND/IOS-08…12 | Card framework, directory, profile screens |
| 6 | P1-BE-28…33 | MFA and SSO |
| 7 | P1-BE-34…40 | Notifications (FCM/APNs, templates, preferences) |
| 8 | P1-WEB-04…07 | Employee admin screens |

Spun off separately: the tenant module registry behind `enabledModules`, which needs the module
taxonomy decided alongside the navigation shell rather than in isolation.

Employee create and delete are deliberately not here. Creating an employee is the joiner process —
provisioning a user account, assigning a role, seeding leave entitlement, triggering onboarding
— and modelling it as a POST to this controller would produce a record that is missing all of it.
Deleting one is never a delete: it is the leaver process, and the record has to survive for
statutory retention.
