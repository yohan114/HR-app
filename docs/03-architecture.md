# Architecture

Stack decisions per your choices: **native Kotlin (Android) + Swift (iOS)**, **custom backend we build ourselves**.

---

## 1. Stack decision summary

| Layer | Choice | Why |
|---|---|---|
| Android | **Kotlin 2.x, Jetpack Compose, min SDK 26 (Android 8)** | Compose for velocity on a form/list-heavy app. SDK 26 covers >97% of devices and gives us real Keystore + BiometricPrompt. (PeoplesHR ships min SDK 23 — we don't need that tail.) |
| iOS | **Swift 6, SwiftUI, min iOS 16** | SwiftUI + Observation. iOS 16 gives Swift Charts, modern navigation, and passkey support. |
| Shared mobile logic | **Kotlin Multiplatform for the data layer only** (optional, decide at Phase 1 gate) | Sync engine, models, validation, and offline queue are the risky, duplicated parts. UI stays 100% native. If KMP proves friction, fall back to two hand-written data layers against one OpenAPI-generated client. |
| Backend | **Kotlin 2.x + Spring Boot 3.x (Java 21, virtual threads)** | Same language as Android → shared mental model and shareable validation logic. Spring Security/Batch/Modulith are the strongest ecosystem for payroll-grade batch + auth + audit. |
| Database | **PostgreSQL 16** | Row-Level Security for tenancy, JSONB for custom fields, partitioning for attendance/payroll volume, `numeric` for money. |
| Cache / locks | **Redis 7** | Sessions, rate limits, distributed locks on payroll runs, hot lookups. |
| Async / events | **Kafka** (Redpanda in dev) | Payroll runs, attendance processing, notification fan-out, audit stream, webhook delivery. |
| Object storage | **S3-compatible** (MinIO on-prem, S3/R2 cloud) | Documents, payslip PDFs, profile photos, attachments. |
| Search | **OpenSearch** | Employee directory, document search, report discovery, assistant RAG. |
| Files/PDF | Apache PDFBox + a template engine | Payslips, letters, statutory reports. |
| API contract | **OpenAPI 3.1, spec-first** | Generates server stubs + Kotlin + Swift clients. Non-negotiable given we want a public API (§17.1 of the feature matrix). |
| Auth | In-house OAuth2 authorization server on Spring Authorization Server | Avoids Keycloak operational weight; we need custom tenant/device/biometric flows anyway. |
| Web admin console | React 19 + TypeScript + TanStack Query | Not your immediate ask, but it must exist — ~60% of the feature matrix is admin-only. |
| Infra | Kubernetes, Terraform, GitHub Actions | Multi-region for data residency (SL, UAE, PH, ID). |
| Observability | OpenTelemetry → Grafana/Tempo/Loki/Prometheus | |

### Why not the alternatives

- **Not React Native/Flutter** — you chose native, and it's the right call here: biometrics, background location, geofencing, biometric-device SDKs, secure storage and app-size targets are exactly where cross-platform costs you. Their 92 MB/276 MB binaries are the visible symptom.
- **Not Node/NestJS backend** — payroll is decimal-precision batch computation with heavy transactional integrity. JVM's `BigDecimal`, Spring Batch, and mature transaction management fit better.
- **Not .NET** — perfectly viable, but Kotlin unifies with Android and gives us one language across backend + Android.
- **Not BaaS (Firebase/Supabase)** — you chose custom, correctly. Payroll formula evaluation, statutory engines, multi-level workflow routing and effective-dated records are not BaaS-shaped problems.

---

## 2. System topology

```
┌──────────────┐   ┌──────────────┐   ┌───────────────┐   ┌──────────────┐
│ Android      │   │ iOS          │   │ Web Admin     │   │ Kiosk (tablet)│
│ Kotlin/      │   │ Swift/       │   │ React/TS      │   │ Android       │
│ Compose      │   │ SwiftUI      │   │               │   │               │
└──────┬───────┘   └──────┬───────┘   └───────┬───────┘   └──────┬───────┘
       │  HTTPS/JSON + gRPC-web(sync)         │                   │
       └──────────────┬───────────────────────┴───────────────────┘
                      ▼
            ┌─────────────────────┐
            │  API Gateway        │  TLS, rate limit, tenant resolve,
            │  (Spring Cloud GW)  │  request signing, WAF
            └──────────┬──────────┘
                       ▼
    ┌──────────────────────────────────────────────────────┐
    │  Modular Monolith  (Spring Modulith)                 │
    │  ┌────────┬────────┬────────┬────────┬────────────┐  │
    │  │identity│ core-hr│ time   │ absence│ payroll    │  │
    │  ├────────┼────────┼────────┼────────┼────────────┤  │
    │  │workflow│ talent │ perf   │ docs   │ engagement │  │
    │  ├────────┴────────┴────────┴────────┴────────────┤  │
    │  │ platform: config · formula · eligibility ·      │  │
    │  │ audit · notify · report · sync · search         │  │
    │  └─────────────────────────────────────────────────┘  │
    └───────┬───────────────┬──────────────┬────────────────┘
            ▼               ▼              ▼
     ┌────────────┐  ┌────────────┐  ┌────────────┐
     │ PostgreSQL │  │  Redis     │  │  Kafka     │
     │  (RLS)     │  │            │  │            │
     └────────────┘  └────────────┘  └─────┬──────┘
                                            ▼
              ┌──────────────┬──────────────┬──────────────┐
              │ payroll-     │ attendance-  │ notification │
              │ engine       │ processor    │ dispatcher   │
              │ (worker)     │ (worker)     │ (worker)     │
              └──────────────┴──────────────┴──────────────┘
                     │              │
                     ▼              ▼
              ┌────────────┐ ┌────────────┐ ┌────────────┐
              │ S3 storage │ │ OpenSearch │ │ Biometric  │
              │            │ │            │ │ device hub │
              └────────────┘ └────────────┘ └────────────┘
```

**Modular monolith, not microservices.** One deployable for the API, with hard module boundaries enforced by Spring Modulith (compile-time verified — modules talk via published events and typed facades, never by reaching into each other's tables). Separate worker deployments only where the workload profile genuinely differs: payroll engine (CPU-heavy, long-running, needs isolation), attendance processor (high-volume batch), notification dispatcher (I/O-bound fan-out).

Rationale: an HRIS is a *deeply interconnected* domain — a promotion touches payroll, benefits, access rights and the org chart in one transaction. Microservices would force distributed transactions across the exact boundaries that most need atomicity. Keep the boundaries in code; extract to services only when a module proves it needs independent scaling.

---

## 3. Multi-tenancy

**Model: shared database, shared schema, `tenant_id` discriminator + PostgreSQL Row-Level Security.**

```sql
ALTER TABLE employee ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON employee
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
```

- Every request resolves the tenant at the gateway (subdomain, org code, or JWT claim) and sets `app.tenant_id` on the connection for the transaction's lifetime.
- RLS is a *second* line of defence — the repository layer also filters explicitly. Defence in depth: a forgotten `WHERE tenant_id` becomes a zero-row result, not a data breach.
- `tenant_id` is the **leading column of every index** on tenant-scoped tables.

**Escape hatches:**
- **Dedicated schema** tier for enterprise customers demanding logical separation. Same code, different `search_path`.
- **Dedicated database / region** tier for data-residency requirements (UAE and Indonesia both have localisation pressure). Tenant registry maps tenant → connection pool.

**Why not schema-per-tenant by default:** migrations across thousands of schemas become the operational bottleneck that kills release velocity. RLS gives us the isolation guarantee without that cost.

---

## 4. Offline-first mobile architecture

This is the single most important decision for beating them on perceived speed.

### Principle
**The local database is the source of truth for the UI. The network is a background reconciliation process.** No screen ever shows a spinner waiting on a network call for data it has seen before.

### Android
```
Compose UI
   ↕ StateFlow
ViewModel
   ↕
Repository ──► Room (SQLite)  ◄── SyncEngine ──► Retrofit/OkHttp
                   │                  │
                   └── OutboxDao ─────┘
                          │
                    WorkManager (constraint: connected)
```

### iOS
```
SwiftUI View
   ↕ @Observable
ViewModel
   ↕
Repository ──► GRDB (SQLite)  ◄── SyncEngine ──► URLSession
                   │                  │
                   └── OutboxTable ───┘
                          │
                    BGTaskScheduler
```

> **GRDB over SwiftData/Core Data** — we need identical SQL semantics to Room for a shared sync design, plus reliable full-text search and predictable migrations. SwiftData's model is too opinionated for a sync engine we control.

### Sync protocol

**Read path — delta sync by cursor:**
```
GET /v1/sync?since=<opaque_cursor>&scopes=leave,attendance,directory
→ { changes: [...], deletes: [...], cursor: "...", hasMore: bool }
```
- Server maintains a per-tenant monotonic change sequence (Postgres logical sequence, not timestamps — no clock skew).
- Scopes are subscribed per user based on role, so a normal employee syncs a few hundred rows, not the company.
- Directory and reference data (leave types, holidays, designations) sync on a slower cadence with ETag.

**Write path — outbox with idempotency:**
1. User acts → write to local DB immediately, marked `pending`, UI updates instantly.
2. Append a mutation to the outbox with a client-generated `idempotency_key` (UUIDv7).
3. WorkManager/BGTaskScheduler drains the outbox in order, per aggregate.
4. Server dedupes on `idempotency_key` — safe to retry forever.
5. On success, mark `confirmed` and reconcile with the server's canonical version.
6. On 4xx business rejection, mark `rejected`, surface a non-blocking banner, keep the user's input for editing.

**Conflict resolution — per entity type, no generic LWW:**

| Entity | Strategy |
|---|---|
| Attendance punch | Append-only. Never conflicts. Server assigns authoritative record. |
| Leave application | Server wins on state; client resubmits if balance changed underneath. |
| Profile edit | Field-level merge; conflicting fields flagged for user choice. |
| Approvals | First write wins; second gets `409 ALREADY_DECIDED` and refreshes. |
| Reference data | Server always wins. |

**What works fully offline (must):** clock in/out, view leave balance & history, apply for leave, view payslips already downloaded, employee directory, org chart, my profile, holiday calendar, pending approvals list (queue the decision), document vault, company news already fetched.

**What requires network:** payroll run monitoring, report generation, new document download, assistant queries, first-time login.

---

## 5. Security architecture

### Authentication flow

```
First login:  org code / email domain → tenant resolve
              → password (+ MFA if enabled)
              → device registration (device_id, attestation)
              → issue access token (15 min) + refresh token (30 d, device-bound)
              → offer biometric enrolment

Biometric:    BiometricPrompt / LAContext
              → unlock Keystore/Secure Enclave key
              → decrypt sealed refresh token
              → exchange for access token
              → NO password. Ever. Until 30-day idle or revocation.
```

The refresh token is encrypted with a key that has `setUserAuthenticationRequired(true)` (Android) / `.biometryCurrentSet` access control (iOS). **Consequence: if biometrics change on the device, the key is invalidated by the OS and we force re-authentication.** That's the correct security property, and it's what PeoplesHR appears to have gotten wrong.

### Token & transport
- Access token: JWT, 15 min, claims = `sub`, `tenant_id`, `employee_id`, `roles`, `scopes`, `device_id`.
- Refresh token: opaque, rotating, device-bound, one-time-use with reuse detection (reuse → revoke the whole family and alert).
- Certificate pinning on both clients, with a backup pin and a remote kill-switch.
- All PII endpoints require a fresh access token; payslip and bank-detail endpoints require a **step-up biometric assertion** within the last 5 minutes.

### Data protection
| Concern | Control |
|---|---|
| PII at rest | Postgres TDE + column-level encryption (pgcrypto) for national ID, bank account, salary |
| Payslip PDFs | Encrypted at rest in S3, served via short-lived signed URL, never cached to disk unencrypted on device |
| Screenshots | `FLAG_SECURE` on Android for payslip/salary screens; iOS screenshot-detection warning + blurred app-switcher snapshot |
| Local DB | SQLCipher (Android) / GRDB + file protection `.completeUnlessOpen` (iOS) |
| Logs | PII scrubbing filter; salary and national ID never logged |
| Audit | Append-only audit table + Kafka audit stream; field-level before/after for configured fields |
| Backups | Encrypted, cross-region, tested restore quarterly |

### Authorisation model
Three layers, all enforced server-side:
1. **RBAC** — role → capability groups → permissions (`leave.approve`, `payroll.run`, `employee.salary.view`).
2. **ABAC data scope** — expression-based filters: `employee.cost_centre IN :user_cost_centres`, `employee.supervisor_path CONTAINS :user_employee_id`.
3. **Field-level** — per-role field visibility with three states: `hidden` / `masked` / `visible`. Applied in a serialization interceptor so it cannot be bypassed by a forgotten check.

---

## 6. The four platform engines

These are the pieces that make the product configurable rather than hard-coded. They are the highest-leverage and highest-risk components — build them early, get them right.

### 6.1 Workflow engine
Every module routes approvals here. Model:
- `WorkflowType` (leave_application, loan_application, movement, …)
- `WorkflowDefinition` — versioned, per tenant, with steps
- `Step` — resolver (`NAMED_USER` | `ROLE` | `SUPERVISOR_LEVEL_N` | `EXPRESSION` | `ANY_OF_GROUP`), mode (`ALL` | `ANY` | `QUORUM`), SLA, escalation
- `WorkflowInstance` + `WorkflowTask` — the runtime
- Delegation, impersonation, withdrawal, and history are first-class

Instances are driven by events, not polling. A step completing publishes `WorkflowTaskCompleted`; the engine advances and emits `WorkflowStepReady` → notification dispatcher.

**Deep-link approvals** (feature 13.10) work by embedding a signed, single-use action token in the push payload, so "Approve" from the notification shade hits the API directly.

### 6.2 Formula engine (payroll + rules)
A **sandboxed expression DSL** — not a scripting language. ANTLR grammar → typed AST → interpreter.

- Types: `Money` (BigDecimal, scale-aware), `Number`, `Date`, `Duration`, `Boolean`, `String`, `Enum`
- Variables resolved from a typed context: `employee.*`, `attendance.*`, `leave.*`, `payitem.*`, `period.*`, `company.*`
- Functions: arithmetic, rounding modes, date math, conditionals, lookups, tax-bracket application, prorating
- **No loops, no I/O, no reflection, no host access.** Evaluation is deterministic, time-bounded, and memory-bounded.
- Every formula is versioned; a payroll run pins the formula version it used, so historical runs stay reproducible forever.

```
// example pay item formula
if (employee.employmentType == FULL_TIME)
  then basic * 0.10
  else prorate(basic * 0.10, attendance.workedDays, period.workingDays)
```

### 6.3 Eligibility engine
Same expression substrate, different context. Answers "is this employee eligible for X?" for benefits, loans, leave types, movements, training. Returns a *reason* on failure — never a bare `false`, because "why am I not eligible" is a top support ticket.

### 6.4 Custom fields (Dynamic Data Structure)
- `field_definition` table: tenant, entity, key, label, type, validation, options, position, section, permissions
- Values stored in a `JSONB` column on the owning entity (`custom_fields`), with a GIN index
- Types: text, number, dropdown, multi-select, date, radio, checkbox, attachment, employee-picker
- Definitions drive **server-rendered form schemas** the mobile clients consume — so adding a custom field appears on mobile with **no app release**. This is essential; otherwise every tenant customisation blocks on an app-store review.

---

## 7. Payroll engine design

Payroll is where correctness matters most and where mistakes are most expensive. Design rules:

1. **Money is `BigDecimal` with explicit scale and rounding mode. Never `Double`. Anywhere.**
2. **A payroll run is an immutable, versioned artefact.** Inputs (employee snapshot, pay item assignments, formula versions, tax tables, attendance/leave aggregates) are all snapshotted at run start.
3. **Five-phase execution**, each phase resumable and independently auditable:
   ```
   LOCK  → snapshot inputs, lock the period (Redis distributed lock + DB period lock)
   VALIDATE → pre-process checks; produces a validation report, blocks on ERROR, warns on WARN
   CALCULATE → per-employee, parallel, deterministic; writes to a staging table
   REVIEW → anomaly detection vs prior period; HR approves; nothing is visible to employees yet
   COMMIT → atomically publish; generate payslips, bank files, GL entries; emit events
   ```
4. **Rollback is a first-class operation**, not a data-fix script. Every commit is reversible until the period is closed.
5. **Reproducibility test**: re-running a committed period with the same snapshot must produce byte-identical results. This is an automated test in CI, run against a golden dataset per country pack.
6. **Country packs are plugins**, not `if (country == "LK")` branches. Interface: `StatutoryCalculator` with `calculateContributions()`, `calculateTax()`, `generateStatutoryReports()`, `validate()`. Ship SL, PH, ID, AE, BD.
7. **Segregation of duties** enforced: the user who runs payroll cannot be the user who approves it.

---

## 8. Attendance processing

High volume (punches × employees × days) and latency-tolerant → separate worker, partitioned tables.

```
punch ingested (mobile / device / kiosk / web)
   → raw_punch table (append-only, partitioned monthly)
   → Kafka: attendance.punch.recorded
   → attendance-processor:
        pair punches into sessions
        apply shift + roster
        apply grace, rounding, late/early rules
        compute worked hours, OT (with cap codes), short hours
        reconcile against approved leave
        detect anomalies (missing punch, impossible duration, geofence violation)
   → daily_attendance table (recomputable, never hand-edited)
   → Kafka: attendance.day.computed → payroll input, dashboards, alerts
```

`daily_attendance` is **derived state** — always safe to delete and recompute from `raw_punch` + config. Any correction happens by adding a `manual_adjustment` record, never by mutating the computed row. This makes attendance disputes auditable and is a genuine differentiator.

---

## 9. API design

- **Spec-first OpenAPI 3.1.** The spec is the source of truth; server interfaces and both mobile clients are generated from it. Breaking the contract breaks CI.
- Versioned under `/v1/`. Additive changes only within a version; new version for breaking changes; two versions supported concurrently.
- Consistent envelope for errors:
  ```json
  { "error": { "code": "LEAVE_BALANCE_INSUFFICIENT",
               "message": "…", "field": "days",
               "details": { "available": 2.5, "requested": 5 } } }
  ```
  Machine-readable `code` so clients can localise the message — critical for our 6-language requirement.
- **Mobile-optimised composite endpoints** alongside granular REST: `GET /v1/mobile/home` returns everything the home screen needs in one round trip. Chatty REST is how you get a slow app.
- Pagination: cursor-based everywhere. No offset pagination on tenant-scoped data.
- Rate limiting per tenant and per user, with generous burst.
- **Public API = the same API.** No separate "integration API" that lags behind. This is how we beat their gap.

---

## 10. Notifications

```
domain event → Kafka → notification-dispatcher
   → resolve recipients (respecting permissions)
   → resolve per-user channel preferences + quiet hours + timezone
   → template render (per language)
   → fan out: FCM / APNs / email / in-app / (SMS, Teams, Slack)
   → delivery receipts + retry with backoff
```
- Every notification carries a deep link (`hrapp://approvals/leave/{id}`).
- Approval notifications carry a signed action token for one-tap approve/reject.
- Digest mode: batch low-priority notifications into a daily summary to avoid notification fatigue — a real complaint about HR apps generally.

---

## 11. Environments & delivery

| Env | Purpose |
|---|---|
| `local` | Docker Compose: Postgres, Redis, Redpanda, MinIO, OpenSearch |
| `dev` | Continuous deploy from `main` |
| `staging` | Production-like, anonymised production-shaped data, full country packs |
| `prod` | Multi-region (SG primary, UAE + ID for residency) |

- **CI gates:** compile, unit tests, module-boundary verification (Spring Modulith), OpenAPI contract diff, payroll golden-dataset reproducibility, security scan (SAST + dependency), Android/iOS build + UI smoke tests on a device farm.
- **Mobile release:** feature flags (server-driven) for everything; staged rollout 5% → 25% → 100%; forced-update floor version enforced by the API.
- **Server-driven UI for forms** (custom fields, module enable/disable) so tenant configuration never waits on an app-store review.

---

## 12. Key risks and how the architecture handles them

| Risk | Mitigation |
|---|---|
| Payroll produces wrong numbers | Immutable snapshots, versioned formulas, golden-dataset reproducibility tests in CI, mandatory review phase, reversible commits |
| Offline sync corrupts data | Idempotency keys, append-only where possible, per-entity conflict strategies, server as authority, full outbox audit |
| Tenant data leak | RLS + explicit repository filtering + field-level serialization filter + automated multi-tenant isolation tests in CI |
| Scope explosion (the matrix is huge) | Modular monolith with hard boundaries; strict P0/P1 phasing; admin-only features go to web first |
| KMP friction slows both platforms | KMP is *only* the data layer and is a Phase-1 gate decision — fallback path is two hand-written layers from a generated client |
| Country pack #6 breaks country pack #1 | Statutory logic is a plugin interface with its own test suite per country |
| Slow app (their #1 complaint) | Offline-first is architectural, not an optimisation. Performance budgets are CI-enforced. |
