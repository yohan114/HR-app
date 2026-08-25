# Roadmap

Phased delivery for a native Android + iOS HR app with a custom backend, matching the feature matrix in [02-feature-matrix.md](02-feature-matrix.md).

> **This document is the summary view.** For week-by-week execution plans with full task backlogs, deliverables, exit criteria and demo scripts, see **[docs/phases/](phases/README.md)**:
> [Phase 0](phases/phase-0-foundations.md) · [Phase 1](phases/phase-1-walking-skeleton.md) · [Phase 2](phases/phase-2-daily-loop.md) · [Phase 3](phases/phase-3-payroll.md) · [Phase 4](phases/phase-4-talent.md) · [Phase 5](phases/phase-5-scale.md) · [Phase 6](phases/phase-6-intelligence.md)

---

## 0. Honest scope assessment

Read this before committing to dates.

PeoplesHR is a **~20-year-old product with roughly 40 modules and several hundred admin screens**. Section 4 of the research dossier is the evidence. Reaching literal 100% parity is a multi-year programme for a sizeable team — that is simply what this category costs.

What is achievable, and what actually wins deals:

| Target | Realistic timeline (team of 6–8) |
|---|---|
| A mobile app that **beats theirs** on the ESS/MSS surface employees use daily | **~4 months** |
| Feature-credible against them for a mid-market customer | **~9 months** |
| Broad parity across the module set | **~18–24 months** |

The plan below front-loads the 20% of features that carry 80% of daily usage — leave, attendance, approvals, payslips, directory — and defers the long tail of admin configuration screens to the web console where they belong.

**Team assumption for the estimates:** 1 tech lead, 2 backend (Kotlin), 1 Android, 1 iOS, 1 web/admin, 1 QA/automation, 1 designer (shared). Adjust proportionally.

---

## Phase 0 — Foundations (Weeks 1–4)

**Goal:** nothing user-visible; everything downstream depends on getting this right.

**Backend**
- Repo, module structure (Spring Modulith), CI/CD, environments
- PostgreSQL schema baseline + Flyway migrations
- **Multi-tenancy with RLS** + automated cross-tenant isolation tests
- Identity: users, roles, permissions, RBAC/ABAC enforcement layer
- OAuth2 server: password grant, refresh rotation with reuse detection, device binding
- OpenAPI 3.1 spec-first pipeline → generated server interfaces + Kotlin/Swift clients
- Audit log infrastructure + field-level audit config
- Object storage, Redis, Kafka wired up

**Mobile (both platforms)**
- Project skeletons, DI, navigation shell, design-system package
- Local DB (Room / GRDB) + migration framework
- **Sync engine v1**: delta pull by cursor, outbox with idempotency keys
- Secure storage: Keystore / Secure Enclave, biometric-sealed token
- Networking, error envelope handling, i18n scaffolding, theming

**Exit criteria**
- Two tenants cannot see each other's data — proven by automated test
- Log in on both platforms, sync a trivial entity, mutate offline, watch it reconcile
- KMP go/no-go decision made and documented

---

## Phase 1 — Walking skeleton (Weeks 5–10)

**Goal:** a real employee can install the app, log in with a fingerprint, and see their own data.

**Backend**
- Employee master (core fields, photo, attachments)
- Org structure: company, location, department, designation, cost centre, salary grade
- Reporting hierarchy with `ltree` materialisation
- Employee directory + OpenSearch indexing
- **Custom fields engine** (`field_definition` → server-driven form schemas)
- Notification service: FCM + APNs, templates, preferences
- `GET /v1/mobile/home` composite endpoint

**Mobile**
- Auth flow: org resolve → password → MFA → **biometric enrolment**
- Home shell with card framework (server-configured)
- Employee directory (offline, search-first)
- Employee profile view
- Org chart (interactive)
- My profile view + edit (edits route to workflow in Phase 2)
- Settings: language, theme, notifications, devices, biometrics
- Dark mode, dynamic type, TalkBack/VoiceOver pass

**Web admin (minimum)**
- Tenant setup, org structure CRUD, employee CRUD, user & role management

**Exit criteria**
- Biometric login works end-to-end with **no password after enrolment** — the headline fix to their #1 complaint
- Directory and org chart fully usable in airplane mode
- Cold start → Home < 1.5 s on a Pixel 6a

---

## Phase 2 — The daily loop (Weeks 11–18) → **Internal beta**

**Goal:** the four things employees actually open an HR app for.

**Workflow engine (build first — everything depends on it)**
- Types, definitions, steps, resolvers (named / role / supervisor-level / expression / group)
- Multi-level, parallel, conditional routing; SLA + escalation
- Instances, tasks, history, withdrawal
- Delegation
- Unified approval inbox API
- Signed action tokens for notification-level approval

**Absence**
- Leave years, groups, types, short-leave types
- Day types, calendar groups, holiday calendars
- Entitlement rules + accrual engine (scheduled job)
- **`leave_ledger` append-only balance model**
- Apply / cancel / withdraw; balance projection at date
- Team & company leave calendar

**Time & attendance**
- Shifts, `employee_shift_schedule`
- **Attendance policy** (`location_capture` OFF/OPTIONAL/REQUIRED, geofence enforcement)
- `raw_punch` ingestion with idempotency + offline replay
- Attendance processor: pairing, grace, rounding, worked/OT/late computation
- `daily_attendance` as derived, recomputable state with calculation trace
- Manual in/out request

**Mobile**
- Clock in/out with the **never-blocks** location flow
- My attendance calendar + day detail with calculation trace
- Leave: balance cards, apply (live balance preview), history, team calendar
- **Leave balance statement** (ledger rendered, fully explainable)
- Unified approvals inbox: list, detail, swipe actions, bulk mode
- Push notifications with approve/reject actions
- My requests tracker
- Announcements feed + milestones

**Exit criteria**
- Full offline day: clock in, apply leave, approve a request — all queued and reconciled correctly
- Approve from the notification shade without opening the app
- **Internal beta** with your own staff on both platforms

---

## Phase 3 — Payroll & money (Weeks 19–30) → **Pilot customer**

**Goal:** the module that makes it a real HRIS.

**Backend**
- **Formula engine**: ANTLR grammar, typed AST, sandboxed interpreter, versioning
- Pay groups, periods, processes, period locking
- Pay items with all calculation methods
- Salary grades, employee salary, amendments & revisions
- Tax engine: brackets, exemptions, adjustments, annualisation
- **Statutory pack #1 — Sri Lanka** (EPF / ETF / APIT) as a plugin
- **Payroll run**: LOCK → VALIDATE → CALCULATE → REVIEW → COMMIT, resumable, reversible
- Input snapshotting for reproducibility
- Validation + anomaly dashboards
- Payslip generation (PDF, multilingual) with `calculation_trace` per line
- Bank file templates + generation + rollback
- GL mapping, export, rollback
- Loans: types, entitlement, application, schedule, payroll deduction
- Expense claims with receipt upload

**Mobile**
- Payslip list + payslip viewer with **biometric step-up**
- **Payslip explainer** — tap any line, see the formula and inputs
- MoM / YoY comparison
- `FLAG_SECURE` / screenshot protection
- Loans: apply, schedule, balance, history
- Claims: camera capture + OCR prefill, submit, track
- Benefits: catalogue with eligibility reasons, apply, history

**Web admin**
- Full payroll administration console (this stays on web — it's a desk task)

**Exit criteria**
- Golden-dataset reproducibility test green in CI: re-running a committed period is byte-identical
- Parallel run against a real customer's existing payroll, zero variance
- **Pilot with one friendly customer**, one country, full monthly cycle

---

## Phase 4 — Talent & performance (Weeks 31–42)

**Backend**
- Performance: competency framework, rating methods, goal groups, evaluation cycles, assessments, self/manager/reviewer flow, MRA/360, bell curve
- Recruitment: requisition, vacancy, candidate, CV pool, application pipeline, interviews & scorecards, offers
- Onboarding: stages, actions, profiles, instances, tasks, progress
- Offboarding: exit types, notice, interview, clearance, handover
- Employee life cycle: movement types, applications, approval, cascade-on-effect, rollback
- Documents: folders, files, tags, permissions, versions
- Document templates + generation + e-signature
- Training: courses, providers, schedules, enrollment, attendance, evaluation

**Mobile**
- My performance: goals, self-assessment, feedback, history
- Team performance: cycle progress, assess direct reports
- Continuous feedback / 1-on-1 notes
- Interviewer scorecard (rate straight after the interview)
- Requisition approval
- Onboarding checklist (new hire + buddy + manager views)
- Exit notice, exit interview, clearance tasks
- My lifecycle timeline
- Documents vault + letter request
- Learning: catalogue, enrolment, calendar, certificates

**Exit criteria**
- A complete hire-to-retire cycle runs end-to-end on a pilot tenant

---

## Phase 5 — Depth, engagement & scale (Weeks 43–56)

**Backend**
- Statutory packs: **Philippines** (SSS/PhilHealth/Pag-IBIG/13th month), **Indonesia** (BPJS/PPh21), **UAE** (WPS/gratuity), **Bangladesh**
- Multi-currency + FX
- Payroll simulator (increment / joiner / exit / bonus)
- Final settlement, retro-pay/arrears
- Rosters, roster scheduling, shift adjustment, shift swap
- Timesheets: clients, projects, activities, billing rates, approval
- OT caps, prior OT, OT claims
- Biometric device connector framework + 2 vendor integrations
- Engagement: surveys, suggestions, recognition, grievance, disciplinary
- Reporting: report builder, saved reports, scheduling, exports
- Analytics dashboards: self, supervisor, executive
- Job scheduler + data import framework
- **Public API v1 GA** with full OpenAPI docs, webhooks, OAuth2 client credentials

**Mobile**
- Timesheets (week grid, quick entry, submit)
- Roster view, shift swap
- OT request & claim
- Surveys, suggestions, recognition, grievance
- Reports viewer with charts + export
- Supervisor & executive dashboards
- **HR admin on mobile** — the parity differentiator (§3.7 of the screens doc)
- Widgets, Quick Settings tile, Live Activity, Siri Shortcuts

**Exit criteria**
- 5 country packs live
- Public API published
- General availability

---

## Phase 6 — Intelligence & differentiation (Weeks 57+, continuous)

- Global search + command palette
- Conversational assistant: policy Q&A via RAG over tenant documents, with citations
- Action execution through the assistant (prefilled confirmation cards, never silent submits)
- Permission-aware answer filtering
- Assistant admin console: knowledge sources, guardrails, usage analytics
- Payslip explainer in natural language
- Smart nudges (overdue approvals, expiring documents, unused leave, burnout signals)
- Predictive attrition with per-employee drivers
- Leave coverage conflict detection + suggested cover
- AI goal suggestions from job descriptions
- CV parsing + candidate ranking
- Natural-language report queries
- Headcount & cost forecasting
- Succession planning / 9-box
- Wear OS + Apple Watch companions
- Teams / Slack apps
- Kiosk mode, meals/canteon module
- Anonymised cross-tenant benchmarking (opt-in)

---

## Milestone summary

| Milestone | Week | What exists |
|---|---|---|
| **M0 — Foundations** | 4 | Multi-tenant backend, auth, sync skeleton |
| **M1 — Walking skeleton** | 10 | Biometric login, directory, org chart, profile |
| **M2 — Internal beta** | 18 | Leave + attendance + approvals + notifications, offline |
| **M3 — Pilot** | 30 | Payroll, payslips, loans, claims — one country |
| **M4 — Feature-credible** | 42 | Performance, recruitment, onboarding, offboarding, docs |
| **M5 — GA** | 56 | 5 country packs, timesheets, engagement, reports, public API, mobile admin |
| **M6 — Differentiated** | 57+ | Assistant, predictions, platform integrations |

---

## Sequencing rules

1. **Workflow engine before any module that needs approval.** Building it fourth would mean rewriting three modules.
2. **Formula engine before payroll.** Same reasoning.
3. **Sync engine before any offline feature.** Retrofitting offline is a rewrite.
4. **Custom fields before the first customer onboards.** Otherwise every tenant needs a code change.
5. **Admin config goes to web first, mobile later.** Configuration is a desk task; don't burn mobile weeks on it early.
6. **One country pack, proven end-to-end, before starting the second.** The plugin interface must be validated by real use, not by design.
7. **Performance budgets enforced in CI from Phase 1.** Speed is our positioning; you cannot bolt it on at the end.

---

## Definition of done (every feature)

- Backend endpoint spec'd in OpenAPI, implemented, unit + integration tested
- Multi-tenant isolation test passes
- Permission checks: RBAC + data scope + field-level
- Audit entries written
- Both mobile platforms implemented, at feature parity with each other
- Offline behaviour defined and tested (works / queues / clearly degrades)
- All six screen states designed and built (loading, loaded, empty, error, offline, no-permission)
- Accessibility pass (TalkBack + VoiceOver + dynamic type + contrast)
- i18n: all strings externalised, RTL verified
- Performance budget met
- Analytics events instrumented
- Documentation updated

---

## Top risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **Scope creep** — the matrix is enormous and every customer wants "just one more module" | High | High | Hard phase gates. Anything not in the current phase goes to the backlog, no exceptions. |
| **Payroll correctness bug reaches production** | Medium | Severe | Golden-dataset reproducibility tests, mandatory review phase, parallel runs against the customer's existing system before cutover, reversible commits |
| **Offline sync data corruption** | Medium | High | Idempotency keys, append-only where possible, per-entity conflict strategies, chaos testing (kill mid-sync, clock skew, duplicate delivery) |
| **Two platforms drift apart** | High | Medium | Shared OpenAPI-generated clients, shared feature checklist, parity gate in the definition of done, KMP for the data layer if the Phase-1 gate passes |
| **Country pack #2 breaks country pack #1** | Medium | High | Plugin interface with an independent test suite per country; no shared conditional logic |
| **Native = 2× mobile cost** | Certain | Medium | Accepted trade-off for your stack choice. Mitigated by KMP data layer and by keeping ~60% of the feature matrix on web-only admin. |
| **Statutory rules change mid-build** | High | Medium | Tax tables and statutory rates are *data*, effective-dated, not code |
| **Pilot customer's data is messier than expected** | High | Medium | Build the import/validation tooling in Phase 1, not Phase 5 |

---

## Immediate next steps

When you're ready to start building:

1. Confirm target countries and their priority order — this determines the statutory pack sequence and is the single biggest scope lever.
2. Confirm target company size (100 / 1,000 / 10,000 employees) — this drives the partitioning and caching decisions in [03-architecture.md](03-architecture.md).
3. Decide the KMP question, or defer it to the Phase-1 gate.
4. I scaffold the backend (Spring Modulith, Postgres + RLS, OAuth2, OpenAPI pipeline) and both mobile projects with the design system and sync engine — that's Phase 0.
