# Build Plan — from working code to a shippable HR product

**Written:** 12 September 2026
**Scope:** Web console + Android now. iOS as a later phase.
**Audience:** you, about to build and deploy this system.

Every claim below was checked against the repository on the date above. Where something is
unverified, it says so. Nothing here is inferred from the phase plans — those describe intent, this
describes the code as it stands.

---

## 0. Where the project actually is

Bigger and healthier than the status docs suggest, with one serious hole.

| | |
|---|---|
| Migrations | 27, applying cleanly to a real PostgreSQL 16.14 |
| Tables | 173 (169 tenant-scoped) |
| Backend modules | 22 |
| Controllers | 34 — **all present in `spec/openapi.yaml`** (178 paths, 220 operations) |
| Web routes | 26 |
| Android screens | 22 packages |
| iOS | 18 Swift files, never compiled |
| CI | 9 GitHub workflows |
| Infrastructure | 47 Terraform files, k8s manifests, Dockerfiles for backend and web |

The backend boots against a real database, runs all 27 migrations, seeds demo data, and serves
real API requests. The web console signs in against it and renders live data. That was proven by
running it, not by reading it.

**The hole:** the API has authentication but effectively **no authorisation**. Details in §1.

---

## 1. Stop-the-line items — before any deploy

These are not "nice to fix". Deploying an HR system with these open would expose every employee's
salary to every employee.

### 1.1 There is no authorisation layer — `BLOCKER`

Verified three independent ways:

1. `backend/.../config/SecurityConfig.kt` ends with `.anyRequest().authenticated()`. Authentication
   only. No role or permission is ever checked at the HTTP layer.
2. Fourteen controllers across ten modules contain **zero** permission checks — no `@PreAuthorize`,
   no manual assertion:

   | Module | Controllers | Permission checks |
   |---|---|---|
   | leave, expense, loan, benefit, performance, recruitment, training, timesheet | 1 each | 0 |
   | payroll | 2 | 0 |
   | attendance | 4 | 0 |

3. Migrations `V11` through `V27` — seventeen files covering leave, payroll, attendance, loans,
   expenses, benefits, lifecycle, disciplinary, performance, recruitment, onboarding, documents,
   training, timesheets and biometric devices — contain **zero** `INSERT INTO permission`. There
   are no permission keys to check even if someone wanted to check them.

**What this means in practice:** any employee who can sign in can call `GET /v1/payroll/...` and
read the payroll run for the whole company. They can approve their own leave. They can read
anyone's loan balance and disciplinary record.

**Fix — this is the single largest piece of work in the plan, and everything else depends on it:**

| Step | Work | Size |
|---|---|---|
| 1 | Define the permission catalogue for all 15 unpermissioned modules — a new migration, roughly 90–120 keys following the existing `module.resource.action` convention | M |
| 2 | Grant them to the four existing roles in a considered way (see §3 — the role model needs extending too) | M |
| 3 | Enforce at the controller layer. The `Caller` + `PermissionResolver` machinery already exists and is used by the employee module — copy that pattern | L |
| 4 | Add an `ApiContractTest`-style checker that **fails the build** when a controller method has no permission assertion. Without this, the gap reopens the first time someone adds a controller in a hurry | M |

Step 4 matters as much as steps 1–3. This gap appeared because nothing prevented it.

### 1.2 The biometric device endpoint is unauthenticated — `BLOCKER`

`SecurityConfig.kt:39` — `.requestMatchers("/iclock/**").permitAll()`.

`attendance/internal/ZkTecoAdmsController.kt` accepts a serial number as the *only* identity, and
line 30 reads `sn?.trim() ?: "ZK-UNKNOWN"` — a request with **no** serial number is accepted and
processed.

Anyone who can reach the host can post attendance punches for any tenant. That is payroll fraud:
clock-in records drive attendance, which drives pay.

**Fix:** device registration with a per-device shared secret or mTLS, an allow-list of known serial
numbers, rejection of unknown/absent serials, and rate limiting. `M`.

### 1.3 Certificate pinning is an empty block — `MAJOR`

`android/.../di/AppModule.kt:104` enters `if (BuildConfig.CERTIFICATE_PINNING)` in release builds
and the body contains only `TODO(P0-AND-08): supply pins from the release configuration.`

The config says pinning is on. Nothing is pinned. Either implement it with a backup pin, or set the
flag false so the build does not claim a protection it lacks. `S`.

### 1.4 Release signing is not configured — `BLOCKER for Play Store`

`app/build.gradle.kts` — the `release` build type has no `signingConfig`, so `assembleRelease`
produces an unsigned artefact. (The debug key on the `benchmark` type is correct and deliberate.)

Not a defect — you should not commit a keystore — but it is a gate. You need an upload keystore,
Play App Signing enrolment, and the key injected from CI secrets. `S`.

### 1.5 Two checker failures

- `migration-check.mjs`: `biometric_device_command` is tenant-scoped but has no index leading with
  `tenant_id`. RLS appends `tenant_id = current_tenant_id()` to every query, so the planner falls
  back to a scan. `S`
- `css-class-check.mjs`: 7 undefined classes — `.grid`, `.grid--2-col`, `.grid--4-col`, `.flow`,
  `.button-group`, `.text-secondary` in `Benefits.tsx`, `.font-semibold` in `Payroll.tsx`. Those
  two screens render partly unstyled today. `S`

---

## 2. Structural work — do before adding features

### 2.1 Android has no navigation framework — `MAJOR`

There is no `NavHost`, no `NavController`, no `composable()` route anywhere. `MainActivity.kt` is
**842 lines** containing roughly **64 hand-rolled state branches** deciding which of 22 screens to
show.

Consequences that will bite during the build:

- No back stack. System back does whatever the boolean soup happens to do.
- Deep links cannot route. `hrapp://leave/{id}` has a manifest entry and nowhere to go — the whole
  notification-to-screen story is unreachable.
- No state restoration across process death. Android *will* kill the process; the user comes back
  to the start.
- Adding screen 23 means editing a 64-branch conditional.

**Fix:** migrate to Navigation Compose with a typed route graph and wire `DeepLinks` to it.
This is invasive and gets worse with every screen added, so it should happen **before** the
dashboard work, not after. `L`

### 2.2 Push notifications do not exist — `MAJOR`

No `google-services.json`, no `FirebaseMessagingService` anywhere in the Android source. The
`HrNotificationManager` posts **local** notifications only — useful for testing presentation, but
nothing arrives from the server.

The backend side is also incomplete: `V9` defines templates, preferences, quiet hours and delivery
tracking, and `com.hr.notification` has the decision logic (send/defer/suppress, retry with
backoff, template rendering) — but there is **no FCM adapter and no dispatcher** joining a domain
event to a notification row.

**Fix, in order:** FCM project + `google-services.json` → `FirebaseMessagingService` + token
registration into the existing `user_device.push_token` → backend FCM adapter → the dispatcher that
calls the existing `decideDelivery` → deferred-release worker for quiet hours. `L`

### 2.3 Six web screens are not connected to anything — `MAJOR`

| Screen | Lines | API calls |
|---|---|---|
| `FormBuilder.tsx` | 894 | 0 |
| `Training.tsx` | 782 | 0 |
| `BatchTools.tsx` | 710 | 0 |
| `Loans.tsx` | 704 | 0 |
| `ReportBuilder.tsx` | 568 | 0 |
| `FormulaBuilder.tsx` | 544 | 0 |

These are complete-looking UIs over local `useState`. A user will fill in a loan application and
lose it on refresh. Either wire them to their (existing) backend modules, or gate them behind a
flag until wired — but do not ship a screen that silently discards work. `M` each.

Android has the same issue in two places: `ui/approvals/` and `ui/attendance/` have no API usage.

---

## 3. Role-based dashboards — your headline ask

### 3.1 What exists today

`web/src/routes/Overview.tsx` is **75 lines with zero API calls**. It shows your username, email,
organisation and permission list. The file's own comment is honest about it: *"the real overview
dashboards arrive with the modules that produce their numbers."*

Those modules now exist. The dashboards do not.

### 3.2 The role model needs extending first

Four roles exist: `ADMIN`, `EMPLOYEE`, `HR_ADMIN`, `MANAGER`.

That is not enough for the product you have built. Payroll data should not be visible to a
recruiter, and a recruiter's candidate pipeline should not be visible to a line manager. Suggested
additions:

| Role | Why it is needed |
|---|---|
| `FINANCE` / `PAYROLL_OFFICER` | Runs payroll, sees salary and bank data; should *not* have HR admin rights over records |
| `RECRUITER` | ATS and candidate pipeline; no access to existing employees' pay |
| `APPROVER` | Some organisations separate approval authority from line management |
| `AUDITOR` (read-only) | Statutory access without write capability |

This depends on §1.1 — roles are meaningless until permissions exist.

### 3.3 Dashboard specifications

The data behind every widget below already exists in the database. Each needs a composite endpoint;
building these as one call per role rather than fifteen calls per page is the difference between a
dashboard that loads and one that does not.

**Employee** — the screen most people see most often
| Widget | Source |
|---|---|
| Leave balance by type, with year-end expiry warning | `leave` |
| Today's attendance: clocked in/out, hours so far | `attendance` |
| Pending requests I submitted, and their stage | leave / expense / loan |
| Latest payslip (amount + download) | `payroll` |
| Timesheet due warning | `timesheet` |
| Documents expiring (visa, contract, certifications) | `document` |
| My goals and review status | `performance` |
| Team birthdays and anniversaries this week | `employee` |
| Assigned training, with overdue flagged | `training` |

**Manager** — everything above for themselves, plus their team
| Widget | Source |
|---|---|
| **Approvals awaiting me**, by type, oldest first | leave / expense / loan / timesheet |
| Who is off today and this week | `leave` |
| Team attendance exceptions: late, absent, missing punch | `attendance` |
| Team timesheet submission status | `timesheet` |
| Direct reports with review or probation due | performance / lifecycle |
| Open requisitions I own | `recruitment` |

**HR Admin**
| Widget | Source |
|---|---|
| Headcount: total, joiners, leavers, net movement | employee / lifecycle |
| Onboarding in progress, with stalled cases flagged | `onboarding` |
| Offboarding and exit interviews due | `onboarding` |
| Probations ending in 30 days | `lifecycle` |
| Open disciplinary and grievance cases | `disciplinary` |
| Documents expiring org-wide | `document` |
| Leave liability (accrued, unpaid) | `leave` |
| Attendance anomalies needing review | `attendance` |

**Finance / Payroll**
| Widget | Source |
|---|---|
| Payroll run status and next cut-off | `payroll` |
| Total cost by cost centre, month on month | payroll / organisation |
| Expense claims pending reimbursement | `expense` |
| Outstanding loan balances and repayment schedule | `loan` |
| Benefit enrolment costs | `benefit` |
| Billable vs non-billable hours | `timesheet` |

**System Admin**
| Widget | Source |
|---|---|
| Active users, recent sign-ins, failed attempts | `identity` |
| Devices registered and untrusted | `identity` |
| Notification delivery health, dead-lettered | `notification` |
| Audit log volume and anomalies | `audit` |
| Tenant module enablement | `tenancy` |
| Biometric device heartbeat and offline terminals | `attendance` |

### 3.4 How to build them

1. **One composite endpoint per role**, e.g. `GET /v1/dashboard` returning the widget set for
   whoever is calling. The server already knows the caller's permissions; it should decide the
   widget set, not the client. That keeps dashboards role-adaptive without the client shipping
   a copy of the permission rules. `L`
2. **Widget contract**: each widget returns `{ key, title, value, trend, deepLink, permission }`.
   A client that meets an unknown widget key skips it. That is what lets you add a widget on the
   server without an app release — the same trick the profile form schema already uses.
3. **Web**: replace `Overview.tsx` with a widget grid. `M`
4. **Android**: `ui/home/` renders the same contract. The architecture already says home is a sync
   scope (see `docs/home-composite.md`) — follow that decision rather than adding a live call. `M`
5. **Caching**: these queries are expensive and read-mostly. A 5-minute server-side cache per
   (tenant, user) is the difference between a snappy dashboard and a database on fire at 9am when
   everyone signs in at once.

---

## 4. Sequencing

Ordered so that each phase makes the next one safe, rather than by what is most fun.

### Phase A — Make it safe (2–3 weeks)
Nothing ships before this is done.
1. Permission catalogue for 15 modules (§1.1 steps 1–2)
2. Enforce permissions on all 34 controllers (§1.1 step 3)
3. Build-failing checker for unprotected endpoints (§1.1 step 4)
4. Lock down `/iclock/**` (§1.2)
5. Certificate pinning, or turn the flag off (§1.3)
6. Fix the two checker failures (§1.5)

**Exit:** an employee account cannot read payroll. Prove it with a test, not by inspection.

### Phase B — Make it navigable (2 weeks)
7. Android Navigation Compose migration (§2.1)
8. Wire deep links to the graph
9. Wire the two unwired Android screens

**Exit:** every screen reachable by route; back button correct; `hrapp://` links open the right
screen from a cold start.

### Phase C — Dashboards (2–3 weeks)
10. Extend the role model (§3.2)
11. `GET /v1/dashboard` composite endpoint + widget contract
12. Web dashboard grid
13. Android home

**Exit:** five roles each see a dashboard drawn from real data.

### Phase D — Close the gaps (2 weeks)
14. Wire the six disconnected web screens (§2.3)
15. FCM end to end (§2.2)
16. Notification dispatcher + deferred-release worker

**Exit:** a leave approval on the server produces a push on a real handset that opens the request.

### Phase E — Ship (1–2 weeks)
17. Release signing + Play Console setup (§1.4)
18. Production secrets: DB, JWT keys, field-encryption key, FCM credentials
19. Deploy pipeline dry run against staging
20. Load test the dashboard endpoint
21. Security review focused on the Phase A work

### Phase F — iOS (later, 6–10 weeks)
See §6.

---

## 5. Further HR capability

After the above. Ordered by how central each is to an HR product, based on the feature matrix in
`docs/02-feature-matrix.md` and what the schema already supports.

**High value, schema already exists** — these are wiring jobs, not new systems:
- Self-service report builder wired to real data (`ReportBuilder.tsx` shell exists)
- Bulk operations: mass leave allocation, bulk salary revision (`BatchTools.tsx` shell exists)
- Org chart visualisation (the ltree hierarchy is already materialised)
- Document e-signature flow (`V23` defines the tables)
- Shift rostering and scheduling
- Overtime calculation and approval

**New build:**
- Employee self-service kiosk mode for factory floors
- Mobile offline attendance with geofencing
- Payroll statutory reporting for your jurisdiction (EPF/ETF for Sri Lanka — the demo data is
  Sri Lankan, so this is likely your first market)
- Multi-currency payroll
- Succession planning and 9-box grid
- Compensation benchmarking
- Employee engagement surveys and pulse checks
- Asset allocation and returns tracking
- Travel and per-diem management
- HR analytics: attrition prediction, cost-per-hire, time-to-fill

---

## 6. iOS — what it takes to resume

Current state: 18 Swift files, a generated client in `clients/swift`, a CI workflow
(`.github/workflows/ios.yml`), and `ios/scripts/swift-sanity.mjs` which passes. That checker is a
lint-like text scan, **not a compiler** — no Swift in this repository has ever been compiled.

To reach parity with the Android app as it will be after Phase D:

| Work | Size |
|---|---|
| First compile on a Mac; fix what a first compile always finds | M |
| Regenerate the Swift client against the current 220-operation spec | S |
| Networking: auth, single-flight refresh, keychain, biometric unlock | L |
| 22 screens in SwiftUI | XL |
| Offline store + outbox (mirror of the Room/outbox design) | L |
| APNs | M |
| App Store submission | M |

**Cheap things to do now that save expensive work later:**
- Keep `spec/openapi.yaml` the single source of truth — it already is; do not let anyone hand-write
  a client
- Keep the dashboard widget contract (§3.4) platform-neutral, so iOS gets dashboards free
- Keep the deep-link route list in sync — `backend/scripts/deeplink-check.mjs` already compares
  backend and Android, and will pick up iOS automatically when `ios/HR/Navigation/DeepLinks.swift`
  exists
- Do not put business rules in Android-only code

---

## 7. What to set up before the first deploy

- [ ] PostgreSQL 16 with `ltree`, `btree_gin`, `pgcrypto` extensions
- [ ] The three database roles: `hr_owner` (migrations), `hr_app` (group), `hr_app_login` (runtime).
      The app must **never** connect as the owner — table owners bypass RLS
- [ ] `FIELD_ENCRYPTION_KEY` — 32 bytes, base64. The app refuses to start without it outside `local`
- [ ] JWT signing keys and rotation plan
- [ ] FCM service account credentials
- [ ] TLS certificates, and the real pins for §1.3
- [ ] S3/object storage for documents and payslips
- [ ] SMTP for email notifications
- [ ] Backup schedule and a **tested** restore
- [ ] Log aggregation and error tracking
- [ ] The 9 `TenantIsolationTest` tests currently fail for want of Docker. Run them somewhere with
      Docker **before** go-live — they are the only proof that tenant isolation works

---

## 8. Honest risk list

| Risk | Why it matters |
|---|---|
| **The authorisation gap is large** | 34 controllers, 15 modules. Under time pressure the temptation will be to protect the obvious ones and move on. The checker in §1.1 step 4 is what prevents that |
| **Tenant isolation is unproven at runtime** | RLS policies exist and were observed rejecting bad writes during local testing, but `TenantIsolationTest` has never run. A multi-tenant HR system leaking across tenants is an existential bug |
| **No load testing has been done** | 173 tables, an ltree hierarchy, and dashboard aggregates. The first thousand-employee tenant is where you find out |
| **Android navigation rewrite touches everything** | Doing it before the dashboards is cheaper than after, but it will be disruptive either way |
| **`api.hrapp.io` is hardcoded** in the release build config. Change it when you know your real domain |

---

## 9. Summary

You have far more working software than the status documents claim — a real schema, a booting
backend, a web console that signs in and shows live data, and 22 Android screens.

What stands between here and shipping is not features. It is that **the API trusts anyone who is
logged in**, the Android app has no navigation framework, and there is no dashboard.

Fix authorisation first. Everything else is ordinary work.
