# HR Platform

Multi-tenant HR platform — native Android and iOS apps on a custom Kotlin backend.

**Current status: Phase 1 (Walking Skeleton) in progress.**
[PHASE-1-STATUS.md](PHASE-1-STATUS.md) records exactly what is built, what is verified, and — at
least as importantly — what is not. [PHASE-0-STATUS.md](PHASE-0-STATUS.md) covers the foundations.

> **One caveat worth reading before anything else.** No migration in this repository has ever been
> executed against PostgreSQL, and `TenantIsolationTest` has never run, because Docker is
> unavailable in the environment this was built in. The SQL compiles, is statically checked, and is
> reviewed — but it is unproven. `PHASE-1-STATUS.md` is explicit about which claims are verified and
> which are not.

---

## Repository layout

```
├── backend/          Kotlin + Spring Boot 3 API (modular monolith)
├── spec/             OpenAPI 3.1 — the API contract, source of truth for all clients
├── clients/          Generated Kotlin, Swift and TypeScript clients (do not hand-edit)
├── android/          Native Android app (Kotlin, Compose)
├── ios/              Native iOS app (Swift)
├── web/              Admin console (React 19, Vite)
├── design/           Design tokens, generated into all three platforms
├── infra/            Local and cloud infrastructure (Terraform, Kubernetes, observability)
├── docs/             Research, architecture, data model, UX, roadmap, ADRs
│   ├── phases/       Per-phase execution plans
│   └── adr/          Architecture decision records
└── docker-compose.yml   Local development stack
```

---

## Getting started

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | **21** | Gradle auto-provisions it if absent |
| Docker | latest | Required for the local stack and for Testcontainers-based tests |
| Node | 22+ | Only for OpenAPI tooling |

### Run the backend

```bash
docker compose up -d
```

```bash
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'
```

The API comes up on `http://localhost:8080`. Health check: `http://localhost:8080/actuator/health`.

Flyway applies migrations on startup as `hr_owner`; the application then serves requests as
`hr_app_login`, which is a non-owner role and therefore subject to row-level security. That
separation is the point — see [ADR 0002](docs/adr/0002-multi-tenancy-rls.md).

### Run the tests

```bash
cd backend && ./gradlew test
```

Two of these matter more than the rest:

```bash
cd backend && ./gradlew test --tests 'com.hr.tenancy.TenantIsolationTest'
```

```bash
cd backend && ./gradlew test --tests 'com.hr.ModuleStructureTest'
```

The first proves customers cannot see each other's data. The second proves the module boundaries
in the modular monolith are real rather than aspirational. Both run on every pull request.

Two checks run without a database, and exist because the ones above cannot:

```bash
node backend/scripts/migration-check.mjs
node backend/scripts/migration-check.selftest.mjs
```

The first asserts structurally what `TenantIsolationTest` asserts at runtime — every tenant-scoped
table calls `apply_tenant_rls()`, every one has an index leading with `tenant_id`, no foreign key
points at a table created in a later migration. The second breaks each rule on purpose and fails if
the checker does not notice, because a checker nobody has seen fail is indistinguishable from one
that cannot.

### Lint the API contract

```bash
npx @stoplight/spectral-cli lint spec/openapi.yaml --fail-severity warn
```

---

## Architecture in one paragraph

A modular monolith on Kotlin/Spring Boot with PostgreSQL, Redis, Kafka, S3 and OpenSearch, plus
three dedicated workers (payroll engine, attendance processor, notification dispatcher). Tenant
isolation is enforced by PostgreSQL row-level security with the application connecting as a
non-owner role. Mobile clients are offline-first: the on-device SQLite database is the source of
truth for the UI, reads arrive by cursor-based delta sync, and writes go through an outbox with
idempotency keys. The OpenAPI spec is the contract and generates the server interfaces and all
three clients.

Full detail: [docs/03-architecture.md](docs/03-architecture.md).

---

## Documentation

| Doc | Contents |
|---|---|
| [PLAN.md](PLAN.md) | Master plan and decisions |
| [01 — Research](docs/01-research-peopleshr.md) | Competitive research: complete module inventory, API surface, user complaints |
| [02 — Feature matrix](docs/02-feature-matrix.md) | ~250 features, prioritised P0–P3 |
| [03 — Architecture](docs/03-architecture.md) | Stack, topology, tenancy, sync, security, platform engines |
| [04 — Data model](docs/04-data-model.md) | Full PostgreSQL schema |
| [05 — Screens & UX](docs/05-screens-ux.md) | ~100 screens, flows, budgets |
| [06 — Roadmap](docs/06-roadmap.md) | Phases and milestones |
| [Phase plans](docs/phases/README.md) | Week-by-week execution plans |
| [ADRs](docs/adr/) | Architecture decision records |

---

## Engineering rules

These are enforced in CI, not left to review discipline.

1. **Every tenant-scoped table calls `apply_tenant_rls()` in its migration.** `TenantIsolationTest`
   fails the build if a table has a `tenant_id` column without a policy, and
   `migration-check.mjs` fails it without needing a database. The second one found a real gap in
   `V1` that had been there since the schema was written.
2. **The application never connects as the database owner.** Owners bypass RLS; the test asserts
   the runtime role is not an owner.
3. **`tenant_id` leads every index on a tenant-scoped table.** RLS appends a `tenant_id` predicate
   to every query; an index that does not start with it cannot serve that predicate.
4. **Modules talk through published APIs, never through another module's `internal` package.**
5. **The OpenAPI spec is the contract.** Breaking a published version fails CI.
6. **Money is `BigDecimal` with explicit scale and rounding.** Never `Double`. A build rule
   enforces this in payroll packages from Phase 3.
7. **Audit rows are append-only.** The application role holds no `UPDATE` or `DELETE` grant on
   `audit_log` or `change_feed`.
8. **Cursor pagination only** on tenant-scoped collections.
9. **No secrets in the repository.** Gitleaks runs on every push.

---

## Licence

Proprietary. All rights reserved.
