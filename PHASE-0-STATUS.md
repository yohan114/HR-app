# Phase 0 — Status

**Started:** 22 August 2026
**Plan:** [docs/phases/phase-0-foundations.md](docs/phases/phase-0-foundations.md)

Honest tracking of what is built, what is verified, and what is not.

---

## Verified in this environment

| What | How it was verified |
|---|---|
| Backend compiles (main + test) | `./gradlew compileTestKotlin` — BUILD SUCCESSFUL |
| Full build produces a runnable artefact | `./gradlew build -x test` — `hr-backend-0.1.0-SNAPSHOT.jar`, 91 MB boot jar |
| **48 tests pass without Docker** | Module structure 3, API contract 5, metrics contract 5, authentication 24, UUIDv7 5, tenant context 6 |
| **Spec and implementation are in sync** | `ApiContractTest` — parity both directions, path params, unique operationIds |
| **Refresh token reuse revokes the family** | `AuthenticationServiceTest` — the security-critical assertion |
| **Login is enumeration-resistant** | Unknown username and wrong password return identical errors; dummy hash verified to run |
| **Biometric grant is device-bound** | Token from another device rejected; unenrolled device rejected; revoked device rejected |
| **Module boundaries are enforced** | `./gradlew test --tests 'com.hr.ModuleStructureTest'` — 3/3 PASSED |
| **Android app builds** | `./gradlew :app:assembleDebug` and `:app:assembleRelease` — BUILD SUCCESSFUL |
| **Android release APK size** | **5.4–7.6 MB** per ABI against a 25 MB budget. Incumbent: 92.5 MB. |
| **Android outbox tests** | 9/9 passing — backoff, jitter, deadline, idempotency-key stability |
| **APK size gate enforces the budget** | 25 MB per ABI. Verified by lowering the budget to 6 MB and confirming the build fails with the right APKs named, then restoring. |
| **Alert rules pass their conventions check** | 20 alerts — severity routable, summary + description present, runbook on every critical. Checker verified against three deliberate faults. |
| **Design tokens generate identically for 3 platforms** | `design/generate.mjs --check` — contrast and staleness both verified against deliberate faults. Caught a real WCAG 1.4.11 failure on first run. |
| **Every web CSS variable resolves** | 29 referenced, 0 undefined |
| **Metric names agree across code, alerts and dashboards** | `MetricsContractTest` — verified by renaming a metric in an alert and confirming the build fails. It caught a real error on first run (see below). |
| **Web console builds** | `npm run build` — type-checks under `strict`, 206 kB JS (64 kB gzipped) |
| **Overlay renderer works** | Renders 4 files from a realistic Terraform output; all three guards (tag-not-digest, missing output, credential-shaped output) verified to reject bad input |
| **All three API clients generate** | `./gradlew generateAllClients` — 17 models + 3 API classes each |
| **Kotlin client compiles** | `./gradlew :client-verify:compileKotlin` against Retrofit + kotlinx.serialization |
| **TypeScript client type-checks** | `tsc --noEmit --strict` — 0 errors |
| The boundary check actually catches violations | It failed on first run: `config` was reaching into `tenancy.internal`. Fixed by moving the DataSource wiring into the tenancy module, where it belongs. This is worth noting — the test earned its place on day one. |
| OpenAPI spec is valid and passes the CI gate | `spectral lint --fail-severity warn` — 0 errors, 0 warnings |
| Gradle toolchain auto-provisions JDK 21 | Built on a machine with only JDK 17 installed |

## Note on P0-BE-25 (server interface generation)

The plan called for generating Kotlin/Spring interfaces from the spec and having controllers
implement them, so spec/implementation divergence breaks the build. That was **not** done: the
controllers were hand-written during the auth work, and retrofitting them onto generated
interfaces is a real refactor of working, tested code. `openapi-generator`'s `kotlin-spring`
output also handles Kotlin nullability and bean validation awkwardly.

**Closed instead by `ApiContractTest`**, which asserts parity in both directions without touching
the controllers. It found three genuine drifts on its first run — see below. The residual gap
versus generated interfaces is request/response *shape*: the contract test verifies that every
operation exists with matching paths and parameters, not that each returns exactly the documented
schema. Response-shape verification is the natural next increment if it proves necessary.

---

## Known gap: refresh tokens in the browser

The backend returns both tokens in a JSON body, which is right for mobile (where the refresh token
is sealed in secure hardware) and **wrong for a browser**, where any storage a script can read an
injected script can also read.

The console currently holds the access token in memory and the refresh token in `sessionStorage`.
That is the best available client-side trade-off, and it is still XSS-exposed.

**Proper fix, needing a backend change:** issue the refresh token to browser clients as an
`HttpOnly; Secure; SameSite=Strict` cookie via a web-specific variant of the token endpoints, so
script cannot read it at all. Should be closed before the console handles real payroll data.

---

## What type-checking the web console caught

Three spec/implementation divergences the contract test missed — because it verifies paths,
methods and path parameters, not headers, bodies or response shapes. This is precisely the
residual gap noted above, and it surfaced within an hour of a client actually consuming the spec:

| Divergence | Fix |
|---|---|
| `POST /v1/auth/resolve-tenant` requires `X-Tenant-Code` (the server's filter reads it) but the spec declared no parameter | Added the parameter |
| `POST /v1/auth/logout` accepts an optional refresh-token body; the spec declared none, so the generated client had no way to send it | Documented the body |
| `GET /v1/me` returns `roles`; the spec omitted the field entirely | Added to the schema |

**Worth acting on:** extending `ApiContractTest` to compare request/response *shapes*, not just
routes, would have caught all three. That is now a concrete, justified next increment rather than
a hypothetical one.

---

## What `ApiContractTest` caught immediately

Worth recording, because it justifies the test:

| Drift | Resolution |
|---|---|
| `GET /v1/sync` documented but never implemented | **Removed from the spec.** The generated clients were shipping a `sync()` method that would 404. It returns with its implementation (P0-BE-33). |
| `GET /v1/auth/.well-known/jwks.json` implemented but undocumented | Documented, with `JwkSet`/`Jwk` schemas. Undocumented endpoints are where authorisation gaps hide. |
| Spec said `/devices/{deviceId}`, code said `/devices/{id}` | **Spec corrected to `{id}`.** `deviceId` was actively misleading — the path carries the device record's UUID, while `Device.deviceId` is the client-generated string. Two different things. |

---

## NOT verified — no Docker in this environment

This is the significant caveat, and it matters:

| What | Why not | Risk |
|---|---|---|
| **`TenantIsolationTest`** | Testcontainers requires Docker | **The single most important test in the codebase has never been run.** The RLS policies, the connection binding, and the role separation are all written but unproven. |
| Flyway migrations | Needs a live PostgreSQL | SQL is unexecuted. Syntax errors are possible. |
| Application startup | Needs PostgreSQL + Redis | Bean wiring is unproven at runtime |
| Docker Compose stack | No Docker | Compose file is unexecuted |
| **The entire iOS client** | No Swift toolchain on Windows | **Never compiled, tests never run.** Mirrors the verified Android design, but expect first-build errors — particularly around GRDB's API and actor isolation. |
| **All infrastructure** | No Terraform, AWS CLI, kubectl, kustomize or Docker | Never planned, never applied, image never built. Verified only by YAML syntax (20 files) and static Terraform wiring checks (47 files — brace balance, module output references, variable declaration/use, module argument names). The wiring checker was itself tested against a deliberate error. Treat the first `terraform plan` as a debugging session. |
| Android on a real device | No emulator or device | Biometric sealing, SQLCipher and WorkManager are compiled but unexercised. The Keystore behaviour in particular differs across OEMs and needs the 5-device sweep from `P0-AND-07`. |

**First thing to do on a machine with Docker:**

```bash
docker compose up -d && cd backend && ./gradlew test
```

Expect to fix migration SQL on the first run. Nothing in `V1`–`V3` has touched a real database.

---

## Task completion against the Phase 0 plan

### Infrastructure & DevOps

| ID | Task | Status |
|---|---|---|
| P0-OPS-01 | Monorepo structure | ✅ Done |
| P0-OPS-02 | Docker Compose local stack | ✅ Written, ⚠️ unexecuted |
| P0-OPS-03 | Terraform: VPC, cluster, RDS, cache, Kafka, search, S3 | ✅ 8 modules + state backend + staging and prod roots (**never planned**) |
| P0-OPS-04 | CI pipelines | ✅ Backend, clients, web, infra + deploy pipeline (**never run** — needs ECR and the GitHub OIDC roles in Terraform first) |
| P0-OPS-05 | Secrets management | ✅ Design + Terraform + External Secrets manifests (**unexecuted**) |
| P0-OPS-06 | Observability stack | ✅ MDC logging, Prometheus registry, OTel collector with PII scrubbing, 20 alert rules, 2 Grafana dashboards, cardinality policy enforced at runtime, all cross-checked by `MetricsContractTest` |
| P0-OPS-07 | Staging environment | 🟡 Terraform + platform components (ingress-nginx, cert-manager, external-dns, external-secrets) written; needs an AWS account |
| P0-OPS-08 | Apple / Google developer accounts | ⬜ Human task — **start now, approval takes weeks** |
| P0-OPS-09 | Branch protection, PR template, CODEOWNERS | 🟡 PR template + CODEOWNERS written; branch protection is a GitHub setting, not a file |
| P0-OPS-10 | ADR process + first 5 ADRs | ✅ 5 written |
| — | Backend container image | ✅ Multi-stage, layered jar, non-root (**never built**) |
| — | Kubernetes manifests | ✅ Kustomize base + 2 overlays, YAML-validated (**never applied**) |

### Backend — tenancy

| ID | Task | Status |
|---|---|---|
| P0-BE-01 | Spring Boot 3 + Java 21 skeleton, virtual threads | ✅ Done |
| P0-BE-02 | Modulith modules + boundary verification | ✅ Done, **passing** |
| P0-BE-03 | Flyway framework + baseline | ✅ Done |
| P0-BE-04 | `tenant`, `tenant_module`, `sequence_config` | ✅ Done |
| P0-BE-05 | RLS policies + `app.tenant_id` pattern | ✅ Written, ⚠️ unproven |
| P0-BE-06 | `TenantContext` + filter | ✅ Done |
| P0-BE-07 | Tenant resolution with precedence rules | ✅ Done |
| P0-BE-08 | Connection routing for dedicated tiers | ⬜ Modelled in schema, not implemented |
| P0-BE-09 | Base entity + auditing | ✅ Done |
| P0-BE-10 | Repository base enforcing tenant filtering | 🟡 `TenantScopedEntity` done; repository base class pending |

### Backend — identity & access

| ID | Task | Status |
|---|---|---|
| P0-BE-11 | `app_user`, `role`, `permission`, `user_role` | ✅ Schema + entities + repositories |
| P0-BE-12 | `user_device`, `refresh_token`, `login_event` | ✅ Schema + entities + repositories |
| P0-BE-13 | Argon2id hashing + password policy | ✅ Done — OWASP params, policy entity, validation collecting all failures, lockout |
| P0-BE-14 | OAuth2 password grant | ✅ Done — enumeration-resistant, timing-equalised, lockout-aware |
| P0-BE-15 | Refresh rotation + reuse detection | ✅ Done — single-use rotation, family revocation on reuse |
| P0-BE-16 | Device binding | ✅ Done — upsert on `(user, deviceId)`, revocation cascades to tokens |
| P0-BE-17 | Biometric token exchange | ✅ Done — device match + enrolment required |
| P0-BE-18 | JWT signing + JWKS | ✅ Done — RS256, in-process decoder, published JWK set. ⬜ Key rotation window pending |
| P0-BE-19 | RBAC method security | ✅ Done — roles in token, permissions expanded per request into authorities |
| P0-BE-20 | ABAC data scopes | ⬜ Schema done, engine pending |
| P0-BE-21 | Field-level permission serialiser | ⬜ Schema done, interceptor pending |
| P0-BE-22 | Audit log + config + interceptor | 🟡 Schema + partitioning done; interceptor pending |
| P0-BE-23 | Rate limiting | ⬜ Not started |

### Backend — API pipeline & sync

| ID | Task | Status |
|---|---|---|
| P0-BE-24 | OpenAPI spec layout + linting | ✅ Done, passing |
| P0-BE-25 | Server/spec conformance | ✅ Done via `ApiContractTest` rather than generated interfaces — see note below |
| P0-BE-26 | Kotlin client (Retrofit + kotlinx.serialization + coroutines) | ✅ Generated and **compiles** |
| P0-BE-27 | Swift client (SPM package, async/await) | ✅ Generated; ⚠️ compilation unverified (needs macOS) |
| P0-BE-28 | TypeScript client (fetch) | ✅ Generated and **type-checks under `--strict`** |
| P0-BE-29 | Contract-diff CI gate | ✅ Workflow written |
| P0-BE-30 | Error envelope + global handler | ✅ Done |
| P0-BE-31 | Cursor pagination utility | ✅ Done |
| P0-BE-32 | `change_feed` + monotonic sequence | 🟡 Schema done; write-side hook pending |
| P0-BE-33 | `GET /v1/sync` delta endpoint | ⬜ Contract specified, not implemented |
| P0-BE-34 | `mutation_log` idempotency ledger | 🟡 Schema done; filter pending |
| P0-BE-35 | Kafka topics + event envelope | ⬜ Not started |
| P0-BE-36 | S3 abstraction | ⬜ Not started |
| P0-BE-37 | Notification service skeleton | ⬜ Not started |
| P0-BE-38 | Health, metrics, structured logging | ✅ Done (MDC carries tenant + request id) |

### Mobile & web

| ID | Task | Status |
|---|---|---|
| P0-AND-01…04 | Project skeleton, Hilt, Compose theme, navigation shell | ✅ **Builds** |
| P0-AND-05 | Room + SQLCipher | ✅ Done |
| P0-AND-06/07 | Keystore secure storage + biometric sealing | ✅ Done |
| P0-AND-08 | Networking, generated client wired in as a source set | 🟡 OkHttp + client compile; cert pinning is a TODO |
| P0-AND-09 | Sync engine | ✅ Written; `SyncApi` is a seam pending P0-BE-33 |
| P0-AND-10 | Outbox | ✅ Done + **9 unit tests passing** |
| P0-AND-11/12 | Error mapping, i18n scaffolding | 🟡 Envelope parsing done; strings externalised |
| P0-AND-13 | Perf harness | ✅ APK size gate **enforced and verified to fail**; Macrobenchmark module configures (needs a device to run) |
| P0-IOS-01…13 | iOS skeleton | 🟡 **Written, never compiled** — no Swift toolchain here |
| P0-WEB-01 | Vite + React 19 + TS skeleton, routing, layout shell | ✅ **Builds and type-checks** |
| P0-WEB-02 | TanStack Query + generated TS client wired | ✅ Done |
| P0-WEB-03 | Design system primitives + tokens | ✅ 8 primitives, tokens mirror the Android theme |
| P0-WEB-04 | Auth flow: sign-in, refresh, sign-out, protected routes | ✅ Done — incl. concurrent-refresh coalescing |
| P0-WEB-05 | Tenant admin | 🟡 Route + permission gate; screen is a labelled placeholder |
| P0-WEB-06 | User admin | 🟡 Same |
| P0-WEB-07 | Role & permission admin | 🟡 Same |
| P0-DES-01…06 | Design tokens | ✅ Single source (`design/tokens.json`) generating Android, web and iOS; WCAG contrast enforced by the generator; staleness gated in CI. Values remain placeholders pending a designer. |

**Cross-platform artefacts:**

| Item | Status |
|---|---|
| [docs/sync-protocol.md](docs/sync-protocol.md) | ✅ Written **before** either implementation, per the phase plan |
| [ADR 0004 — no KMP](docs/adr/0004-no-kotlin-multiplatform.md) | ✅ Gate decision recorded |

### QA

| ID | Task | Status |
|---|---|---|
| P0-QA-01 | Test strategy doc | ⬜ Not started |
| P0-QA-02 | Testcontainers harness | ✅ Done (`PostgresTestBase`) |
| P0-QA-03 | Multi-tenant isolation suite | 🟡 9 assertions written, ⚠️ **never executed** |
| P0-QA-04 | Auth flow tests | ⬜ Blocked on auth implementation |
| P0-QA-05 | Sync chaos tests | ⬜ Blocked on sync implementation |
| P0-QA-06 | Test data factory | ⬜ Not started |
| P0-QA-07 | SAST + dependency + secret scanning | ✅ In CI workflow |
| P0-QA-08 | Device farm | ⬜ Blocked on mobile projects |

---

## Overall

**Roughly 50% of Phase 0 complete.** The load-bearing decisions are made and encoded:

- Multi-tenancy design and its enforcement machinery
- Module boundaries, verified and already catching violations
- Error envelope, cursor pagination, UUIDv7 keys, audit and sync schema
- The API contract for the auth and sync surface
- Local stack and backend CI

- The full authentication surface: password grant, rotating refresh with reuse detection,
  biometric unlock, device registration and revocation, RS256 signing with a published JWK set
- Role-to-permission expansion per request, so permission changes take effect immediately

**The critical path from here**, in order:

1. **Run the migrations against real PostgreSQL and get `TenantIsolationTest` green.** Nothing else
   should be built on top of unproven isolation.
2. Client generation from the spec (P0-BE-25 → 28) — before the mobile projects start, so they
   consume generated clients from day one.
3. Android and iOS skeletons with the sync engine (the largest remaining Phase 0 risk).
4. Web admin skeleton.
5. Remaining infrastructure: Terraform, secrets, staging (P0-OPS-03/05/07).

---

## Decisions made during implementation

| Decision | Rationale |
|---|---|
| Gradle 8.14.3 with a Java 21 toolchain | The environment had only JDK 17. Gradle auto-provisions 21, so virtual threads stay on the table without requiring every developer to manage JDKs. |
| Non-owner runtime database role (`hr_app_login`) | PostgreSQL exempts table owners from RLS. Connecting as the owner would make every policy decorative while all isolation tests still passed. This is enforced by an assertion, not a convention. |
| `set_config()` with a bind parameter rather than `SET` | `SET` cannot take bind parameters, so it would require string concatenation. `set_config()` accepts one. |
| `LEAKPROOF` on `current_tenant_id()` | Without it the planner may evaluate a user-supplied function before the RLS predicate — a documented information-leak vector. |
| Audit and change-feed tables partitioned from day one | Adding partitioning to a large table later requires a rewrite under lock. It costs nothing now. |
| DataSource wiring moved into the tenancy module | The boundary test rejected it in `config`, correctly. Binding tenants to connections is tenancy's responsibility. |
| Redpanda instead of Kafka locally | Kafka-API compatible, starts in seconds, uses a fraction of the memory. Production runs managed Kafka. |

---

## Known gaps and risks

| Risk | Severity | Note |
|---|---|---|
| **`TenantIsolationTest` has never run** | **High** | Everything about isolation is theoretical until it goes green. Do this first. |
| Migration SQL is unexecuted | Medium | Expect syntax fixes on first run |
| Sync engine not started | High | Largest remaining Phase 0 risk. Write the protocol spec before either mobile platform starts coding. |
| KMP go/no-go not decided | Medium | Due by end of week 4. Default if undecided: no KMP. |
| Apple/Google accounts not started | Medium | Approval takes days to weeks and blocks TestFlight in Phase 1 |
