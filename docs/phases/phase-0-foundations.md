# Phase 0 — Foundations

**Weeks 1–4 · Milestone M0**

---

## Goal

Build the load-bearing infrastructure that every later phase depends on: multi-tenant data isolation, authentication with device-bound biometric tokens, the spec-first API pipeline, and the offline sync skeleton on both mobile platforms.

**Nothing user-visible ships in this phase.** That is intentional and worth defending to stakeholders: every one of these four things is extremely expensive to retrofit. Multi-tenancy bolted on later means rewriting every query. Offline sync bolted on later means rewriting every screen. Get them right now.

---

## Entry criteria

- [ ] Team assembled and onboarded
- [ ] Cloud accounts provisioned (AWS/GCP/Azure), billing configured
- [ ] Apple Developer + Google Play developer accounts registered (start now — approval takes days to weeks)
- [ ] Decision made: target countries and priority order (affects nothing in Phase 0, but Phase 3 planning starts in week 4)
- [ ] Domain names registered, DNS delegated

---

## Week-by-week

### Week 1 — Repos, infra, schema baseline

| Who | Focus |
|---|---|
| `TL` | Repo structure, module boundaries, ADR process, branch/PR conventions |
| `BE1` | Spring Boot skeleton, Spring Modulith module definitions, Flyway baseline |
| `BE2` | Local dev environment (Docker Compose: Postgres, Redis, Redpanda, MinIO, OpenSearch) |
| `AND` | Android project skeleton, DI (Hilt), module structure, Compose theme scaffold |
| `IOS` | iOS project skeleton, SPM structure, DI container, SwiftUI theme scaffold |
| `WEB` | React admin skeleton, routing, TanStack Query, auth shell |
| `QA` | Test strategy doc, CI pipeline skeleton, test data generation approach |
| `DES` | Design tokens: colour, type ramp, spacing, elevation, iconography decisions |

### Week 2 — Multi-tenancy and identity schema

| Who | Focus |
|---|---|
| `TL` | Multi-tenancy design review, tenant-resolution strategy, connection-pool routing |
| `BE1` | Tenancy: `tenant`, `tenant_module`, RLS policies, `TenantContext` filter, connection setup |
| `BE2` | Identity schema: `app_user`, `role`, `permission`, `user_role`, `user_device` |
| `AND` | Room setup, migration framework, secure storage (Keystore) wrapper |
| `IOS` | GRDB setup, migration framework, secure storage (Secure Enclave/Keychain) wrapper |
| `WEB` | Design system components (from `DES` tokens), form primitives |
| `QA` | **Multi-tenant isolation test harness** — the single most important test in the codebase |
| `DES` | Component library: buttons, inputs, cards, lists, sheets, states |

### Week 3 — OAuth2, OpenAPI pipeline, sync engine

| Who | Focus |
|---|---|
| `TL` | OpenAPI spec-first pipeline, code generation for 3 clients, contract-diff CI gate |
| `BE1` | OAuth2 authorization server: password grant, refresh rotation, reuse detection, device binding |
| `BE2` | RBAC/ABAC enforcement layer, field-level permission serialiser, audit log infrastructure |
| `AND` | **Sync engine v1**: delta pull by cursor, outbox with idempotency keys, WorkManager driver |
| `IOS` | **Sync engine v1**: same protocol, BGTaskScheduler driver |
| `WEB` | Auth flow against the real OAuth2 server |
| `QA` | Isolation tests green; auth flow integration tests; sync chaos-test scaffolding |
| `DES` | Auth + onboarding screen designs; empty/error/offline state patterns |

### Week 4 — Integration, hardening, gate decisions

| Who | Focus |
|---|---|
| `TL` | KMP go/no-go evaluation and decision record; Phase 1 planning |
| `BE1` | Change feed (`change_feed` table + sequence), `/v1/sync` endpoint |
| `BE2` | Object storage, Kafka wiring, notification service skeleton, health/observability |
| `AND` | End-to-end: login → sync a trivial entity → mutate offline → reconcile |
| `IOS` | Same end-to-end |
| `WEB` | Tenant admin: create tenant, create user, assign role |
| `QA` | Full CI pipeline green: build, test, isolation, contract diff, security scan |
| `OPS` | Staging environment deployed, secrets management, observability stack |

---

## Task backlog

### Infrastructure & DevOps

| ID | Task | Owner | Size | Depends on |
|---|---|---|---|---|
| P0-OPS-01 | Monorepo structure (`/backend`, `/android`, `/ios`, `/web`, `/spec`, `/infra`) | TL | S | — |
| P0-OPS-02 | Docker Compose local stack: Postgres 16, Redis 7, Redpanda, MinIO, OpenSearch | BE2 | M | — |
| P0-OPS-03 | Terraform: VPC, EKS/GKE cluster, RDS Postgres, ElastiCache, MSK, S3 | TL | L | P0-OPS-01 |
| P0-OPS-04 | GitHub Actions: backend build/test, Android build, iOS build, web build | QA | L | P0-OPS-01 |
| P0-OPS-05 | Secrets management (AWS Secrets Manager / Vault), no secrets in repo | TL | M | P0-OPS-03 |
| P0-OPS-06 | Observability: OpenTelemetry SDK, Grafana/Tempo/Loki/Prometheus stack | BE2 | L | P0-OPS-03 |
| P0-OPS-07 | Staging environment deployed and reachable | TL | M | P0-OPS-03,05 |
| P0-OPS-08 | Apple Developer + Google Play accounts, provisioning profiles, signing keys | TL | M | — |
| P0-OPS-09 | Branch protection, PR template, CODEOWNERS, conventional commits | TL | S | P0-OPS-01 |
| P0-OPS-10 | ADR (architecture decision record) process + first 5 ADRs written | TL | M | — |

### Backend — tenancy

| ID | Task | Owner | Size | Depends on |
|---|---|---|---|---|
| P0-BE-01 | Spring Boot 3 + Java 21 skeleton, virtual threads enabled | BE1 | S | P0-OPS-01 |
| P0-BE-02 | Spring Modulith module definitions + boundary verification test | BE1 | M | P0-BE-01 |
| P0-BE-03 | Flyway migration framework, naming convention, baseline schema | BE1 | S | P0-BE-01 |
| P0-BE-04 | `tenant`, `tenant_module`, `sequence_config` tables | BE1 | S | P0-BE-03 |
| P0-BE-05 | RLS policies + `app.tenant_id` session variable pattern | BE1 | M | P0-BE-04 |
| P0-BE-06 | `TenantContext` (thread/virtual-thread scoped) + servlet filter | BE1 | M | P0-BE-05 |
| P0-BE-07 | Tenant resolution: subdomain, org code, JWT claim — with precedence rules | BE1 | M | P0-BE-06 |
| P0-BE-08 | Connection routing for dedicated-schema and dedicated-DB tenant tiers | TL | L | P0-BE-07 |
| P0-BE-09 | Base entity + auditing (`created_at/by`, `updated_at/by`, `version`) | BE1 | S | P0-BE-03 |
| P0-BE-10 | Repository base class enforcing explicit `tenant_id` filtering | BE1 | M | P0-BE-06 |

### Backend — identity & access

| ID | Task | Owner | Size | Depends on |
|---|---|---|---|---|
| P0-BE-11 | `app_user`, `role`, `permission`, `role_permission`, `user_role` tables | BE2 | M | P0-BE-04 |
| P0-BE-12 | `user_device`, `refresh_token`, `login_event` tables | BE2 | S | P0-BE-11 |
| P0-BE-13 | Argon2id password hashing + `password_policy` enforcement | BE2 | M | P0-BE-11 |
| P0-BE-14 | Spring Authorization Server: password grant | BE1 | L | P0-BE-13 |
| P0-BE-15 | Refresh token rotation + reuse detection + family revocation | BE1 | L | P0-BE-14 |
| P0-BE-16 | **Device binding**: register device, bind refresh token to `device_id` | BE1 | M | P0-BE-15 |
| P0-BE-17 | Biometric token exchange endpoint (device-sealed token → access token) | BE1 | M | P0-BE-16 |
| P0-BE-18 | JWT access token: claims, signing (RS256), key rotation | BE1 | M | P0-BE-14 |
| P0-BE-19 | RBAC: `@RequiresPermission` annotation + method-security integration | BE2 | M | P0-BE-11 |
| P0-BE-20 | ABAC: `data_scope`, `user_data_scope`, expression evaluation into query predicates | BE2 | L | P0-BE-19 |
| P0-BE-21 | Field-level permissions: `field_permission` + Jackson serialisation filter | BE2 | L | P0-BE-19 |
| P0-BE-22 | `audit_log` (monthly partitions) + `audit_config` + `@Audited` interceptor | BE2 | L | P0-BE-09 |
| P0-BE-23 | Rate limiting per tenant + per user (Redis token bucket) | BE2 | M | P0-OPS-02 |

### Backend — API pipeline & sync

| ID | Task | Owner | Size | Depends on |
|---|---|---|---|---|
| P0-BE-24 | OpenAPI 3.1 spec repo layout, linting (Spectral), style guide | TL | M | P0-OPS-01 |
| P0-BE-25 | Server interface generation from spec (openapi-generator) | TL | M | P0-BE-24 |
| P0-BE-26 | Kotlin client generation for Android | TL | M | P0-BE-24 |
| P0-BE-27 | Swift client generation for iOS | TL | M | P0-BE-24 |
| P0-BE-28 | TypeScript client generation for web | TL | S | P0-BE-24 |
| P0-BE-29 | **Contract-diff CI gate** — breaking change to a published version fails the build | QA | M | P0-BE-25 |
| P0-BE-30 | Standard error envelope + global exception handler + machine-readable codes | BE2 | M | P0-BE-01 |
| P0-BE-31 | Cursor pagination utility (no offset pagination anywhere) | BE2 | S | P0-BE-30 |
| P0-BE-32 | `change_feed` table + monotonic sequence + write-side hook | BE1 | L | P0-BE-09 |
| P0-BE-33 | `GET /v1/sync` delta endpoint: cursor, scopes, changes, deletes, hasMore | BE1 | L | P0-BE-32 |
| P0-BE-34 | `mutation_log` idempotency ledger + `Idempotency-Key` header handling | BE1 | M | P0-BE-30 |
| P0-BE-35 | Kafka topics, producer config, event envelope schema | BE2 | M | P0-OPS-02 |
| P0-BE-36 | S3/MinIO abstraction: upload, signed URL, server-side encryption | BE2 | M | P0-OPS-02 |
| P0-BE-37 | Notification service skeleton: FCM + APNs clients, `notification` table | BE2 | L | P0-BE-35 |
| P0-BE-38 | Health, readiness, metrics endpoints; structured logging with PII scrubbing | BE2 | M | P0-OPS-06 |

### Android

| ID | Task | Owner | Size | Depends on |
|---|---|---|---|---|
| P0-AND-01 | Project skeleton: modules (`app`, `core-*`, `feature-*`), Gradle version catalog | AND | M | P0-OPS-01 |
| P0-AND-02 | Hilt DI setup, application scaffolding | AND | S | P0-AND-01 |
| P0-AND-03 | Compose theme from design tokens: colour, type, shape, dark mode | AND | M | P0-DES-01 |
| P0-AND-04 | Navigation shell (type-safe nav, bottom bar placeholder) | AND | M | P0-AND-02 |
| P0-AND-05 | Room database, migration framework, SQLCipher encryption | AND | M | P0-AND-01 |
| P0-AND-06 | Secure storage: Keystore key with `setUserAuthenticationRequired(true)` | AND | L | P0-AND-01 |
| P0-AND-07 | BiometricPrompt integration + token sealing/unsealing | AND | L | P0-AND-06 |
| P0-AND-08 | Networking: OkHttp, generated client, auth interceptor, token refresh, cert pinning | AND | L | P0-BE-26 |
| P0-AND-09 | **Sync engine**: cursor store, delta apply, conflict hooks | AND | XL | P0-AND-05, P0-BE-33 |
| P0-AND-10 | **Outbox**: DAO, idempotency keys, WorkManager drain, retry/backoff | AND | XL | P0-AND-09 |
| P0-AND-11 | Error envelope → localised message mapping | AND | M | P0-BE-30 |
| P0-AND-12 | i18n scaffolding, per-app language preference | AND | S | P0-AND-03 |
| P0-AND-13 | Baseline profile + startup tracing + perf test harness | AND | M | P0-AND-04 |

### iOS

| ID | Task | Owner | Size | Depends on |
|---|---|---|---|---|
| P0-IOS-01 | Project skeleton: SPM local packages mirroring Android module structure | IOS | M | P0-OPS-01 |
| P0-IOS-02 | DI container, app scaffolding, `@Observable` patterns | IOS | S | P0-IOS-01 |
| P0-IOS-03 | SwiftUI theme from design tokens: colour, type, shape, dark mode | IOS | M | P0-DES-01 |
| P0-IOS-04 | Navigation shell (`TabView` + `NavigationStack` per tab) | IOS | M | P0-IOS-02 |
| P0-IOS-05 | GRDB database, migration framework, file protection | IOS | M | P0-IOS-01 |
| P0-IOS-06 | Secure storage: Secure Enclave key with `.biometryCurrentSet` access control | IOS | L | P0-IOS-01 |
| P0-IOS-07 | `LAContext` biometric integration + token sealing/unsealing | IOS | L | P0-IOS-06 |
| P0-IOS-08 | Networking: URLSession, generated client, auth interceptor, refresh, cert pinning | IOS | L | P0-BE-27 |
| P0-IOS-09 | **Sync engine**: cursor store, delta apply, conflict hooks | IOS | XL | P0-IOS-05, P0-BE-33 |
| P0-IOS-10 | **Outbox**: table, idempotency keys, BGTaskScheduler drain, retry/backoff | IOS | XL | P0-IOS-09 |
| P0-IOS-11 | Error envelope → localised message mapping | IOS | M | P0-BE-30 |
| P0-IOS-12 | i18n scaffolding, string catalogs | IOS | S | P0-IOS-03 |
| P0-IOS-13 | Launch-time instrumentation + perf test harness | IOS | M | P0-IOS-04 |

### Web admin

| ID | Task | Owner | Size | Depends on |
|---|---|---|---|---|
| P0-WEB-01 | Vite + React 19 + TypeScript skeleton, routing, layout shell | WEB | M | P0-OPS-01 |
| P0-WEB-02 | TanStack Query setup, generated TS client wiring | WEB | M | P0-BE-28 |
| P0-WEB-03 | Design system components from tokens (tables, forms, dialogs, toasts) | WEB | L | P0-DES-02 |
| P0-WEB-04 | Auth flow: login, refresh, logout, protected routes | WEB | M | P0-BE-14 |
| P0-WEB-05 | Tenant admin: create/list tenants, module toggles | WEB | M | P0-BE-04 |
| P0-WEB-06 | User admin: create user, assign roles, reset password | WEB | M | P0-BE-11 |
| P0-WEB-07 | Role & permission admin | WEB | M | P0-BE-19 |

### QA

| ID | Task | Owner | Size | Depends on |
|---|---|---|---|---|
| P0-QA-01 | Test strategy document: pyramid, tooling, coverage targets, ownership | QA | M | — |
| P0-QA-02 | Testcontainers harness (Postgres, Redis, Kafka) for integration tests | QA | M | P0-BE-01 |
| P0-QA-03 | **Multi-tenant isolation test suite** — every endpoint, two tenants, assert zero leakage | QA | XL | P0-BE-10 |
| P0-QA-04 | Auth flow integration tests: login, refresh, rotation, reuse detection, revocation | QA | L | P0-BE-15 |
| P0-QA-05 | **Sync chaos tests**: kill mid-sync, duplicate delivery, clock skew, out-of-order | QA | XL | P0-AND-10, P0-IOS-10 |
| P0-QA-06 | Test data factory + seeded demo tenant generator | QA | L | P0-BE-04 |
| P0-QA-07 | SAST + dependency scanning + secret scanning in CI | QA | M | P0-OPS-04 |
| P0-QA-08 | Device farm setup (Firebase Test Lab / BrowserStack) with smoke tests | QA | M | P0-OPS-04 |

### Design

| ID | Task | Owner | Size | Depends on |
|---|---|---|---|---|
| P0-DES-01 | Design tokens: colour ramps (light+dark), type scale, spacing, radii, elevation | DES | L | — |
| P0-DES-02 | Core component library in Figma: buttons, inputs, cards, lists, sheets, chips | DES | XL | P0-DES-01 |
| P0-DES-03 | State patterns: loading skeletons, empty, error, offline, no-permission | DES | L | P0-DES-02 |
| P0-DES-04 | Auth + onboarding screen designs (7 screens) | DES | L | P0-DES-02 |
| P0-DES-05 | Accessibility spec: contrast matrix, touch targets, focus order, dynamic type behaviour | DES | M | P0-DES-01 |
| P0-DES-06 | Icon set decisions + tenant branding override spec | DES | M | P0-DES-01 |

---

## Deliverables

### Database tables (18)
`tenant` · `tenant_module` · `sequence_config` · `app_user` · `role` · `permission` · `role_permission` · `user_role` · `user_device` · `refresh_token` · `login_event` · `password_policy` · `data_scope` · `user_data_scope` · `field_permission` · `audit_log` · `audit_config` · `change_feed` · `mutation_log`

### API endpoints
```
POST   /v1/auth/resolve-tenant
POST   /v1/auth/token                    (password grant)
POST   /v1/auth/token/refresh            (rotating)
POST   /v1/auth/token/biometric          (device-sealed exchange)
POST   /v1/auth/devices                  (register)
GET    /v1/auth/devices                  (list)
DELETE /v1/auth/devices/{id}             (revoke)
POST   /v1/auth/logout
GET    /v1/sync                          (delta, cursor-based)
GET    /v1/me
GET    /health  /ready  /metrics
```

### Mobile
- Both apps build, install, log in against staging, sync a trivial entity, mutate offline, reconcile on reconnect
- Biometric seal/unseal working on both platforms
- No UI beyond a debug screen — this is deliberate

### Infra
- Local Docker Compose stack
- Staging environment deployed
- Full CI pipeline green on every PR
- Observability stack receiving traces, logs, metrics

---

## Exit criteria

Every item must be objectively verifiable. No judgement calls.

| # | Criterion | How it's verified |
|---|---|---|
| 1 | Two tenants cannot see each other's data | `P0-QA-03` suite passes: every endpoint, cross-tenant request returns 0 rows or 404 |
| 2 | RLS is active and enforced independently of application code | Test connects with `app.tenant_id` unset and asserts zero rows returned |
| 3 | Login works on Android and iOS against staging | Manual + automated device-farm smoke test |
| 4 | Biometric unlock returns a valid access token with no password | Manual verification on a physical device, both platforms |
| 5 | Refresh-token reuse revokes the whole family | `P0-QA-04` integration test |
| 6 | Offline mutation survives app kill and reconciles on reconnect | `P0-QA-05` chaos test |
| 7 | Duplicate mutation delivery produces exactly one server-side effect | `P0-QA-05` idempotency test |
| 8 | Breaking an OpenAPI contract fails CI | Deliberate breaking change on a branch; build must go red |
| 9 | Module boundary violation fails CI | Spring Modulith verification test |
| 10 | No secrets in the repository | Secret scanner clean |
| 11 | Audit log records a write with actor, before/after, request ID | Integration test |
| 12 | Staging deploys automatically from `main` | Observed deployment |
| 13 | KMP go/no-go decision recorded as an ADR | ADR merged |

---

## Demo script (end of week 4)

1. **Isolation** — Open two browser tabs, two different tenants. Show identical URLs returning different data. Then run the isolation suite live; 100% pass.
2. **Auth** — Log in on a physical Android device with a password. Enrol biometrics. Kill the app. Reopen → fingerprint → straight into the app, no password. Repeat on iPhone with Face ID.
3. **Sync** — Show a debug screen with a synced entity list. Change the record in the web admin. Watch it appear on both phones within the sync interval.
4. **Offline** — Put the phone in airplane mode. Create a record. Show it appear instantly in the local list with a "queued" badge. Kill the app. Reopen — still queued. Restore network. Watch the badge clear and the record appear in the web admin.
5. **Idempotency** — Replay the same mutation three times from a REST client. Show exactly one row created and two `mutation_log` dedupe hits.
6. **CI** — Push a branch with a deliberate breaking API change. Watch the pipeline go red on the contract-diff gate.

---

## Phase risks

| Risk | Trigger to watch for | Owner | Mitigation |
|---|---|---|---|
| RLS performance overhead surprises us | Query plans showing policy re-evaluation per row | TL | Benchmark in week 2 with 1M-row tables; `tenant_id` leads every index; consider `SECURITY DEFINER` views for hot paths |
| Sync engine is under-scoped | Week 3 ends without a working delta apply | TL | It's the single biggest Phase-0 risk. Both `AND` and `IOS` build it in parallel with a shared written protocol spec; `TL` reviews the spec before either starts coding. |
| Biometric key invalidation behaviour differs across OEM Android devices | Samsung/Xiaomi devices failing key unsealing | AND | Test on at least 5 physical devices across OEMs in week 3; explicit `KeyPermanentlyInvalidatedException` handling with a clean re-auth path |
| KMP decision drags and blocks Phase 1 | No decision by end of week 3 | TL | Hard deadline: decision recorded by Friday of week 4. Default if undecided = **no KMP**, two hand-written data layers. |
| Apple/Google account approval delays | Not approved by week 3 | TL | Started in week 1 (P0-OPS-08). Non-blocking for Phase 0 but blocks TestFlight in Phase 1. |
| Team unfamiliar with Spring Modulith | Boundary violations piling up | TL | Half-day workshop in week 1; boundary test in CI from week 2 so violations are caught immediately, not at review |

---

## What is deliberately NOT in Phase 0

Stated explicitly so nobody expects it:

- Any employee-facing screen
- Any business module (leave, attendance, payroll…)
- SSO / MFA (Phase 1)
- Push notification delivery (skeleton only; wired up in Phase 1)
- Web admin beyond tenant/user/role CRUD
- Any country-specific logic
