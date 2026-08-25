# HR Mobile Application — Master Plan

A native HR mobile application (Android + iOS) with a custom backend, built to match and beat [PeoplesHR](https://peopleshr.com/).

**Status:** Planning complete. Ready to start Phase 0.
**Last updated:** 22 August 2026

---

## Documents

| Doc | What's in it |
|---|---|
| [01 — Research dossier](docs/01-research-peopleshr.md) | Everything found about PeoplesHR: the complete module/screen inventory pulled from their public help portal, their public API surface, app-store facts, and the user complaints we're going to beat |
| [02 — Feature matrix](docs/02-feature-matrix.md) | ~250 features across 18 areas, each prioritised P0–P3 and assigned to mobile / web / API |
| [03 — Architecture](docs/03-architecture.md) | Stack, topology, multi-tenancy, offline-first sync, security, and the four platform engines |
| [04 — Data model](docs/04-data-model.md) | Full PostgreSQL schema across every domain, with partitioning and retention |
| [05 — Screens & UX](docs/05-screens-ux.md) | ~100 screens, navigation, key flows, visual language, accessibility, performance budgets |
| [06 — Roadmap](docs/06-roadmap.md) | 6 phases, 6 milestones, sequencing rules, risks |
| **[Phase plans](docs/phases/README.md)** | **Detailed per-phase execution plans** — week-by-week, ~700 tasks with IDs/owners/sizes, deliverables, exit criteria, demo scripts |
| [Decision records](docs/adr/README.md) | The decisions that were expensive to make and would be expensive to reverse — and why the intuitive alternative was rejected |
| [Sync protocol](docs/sync-protocol.md) | The offline delta-sync contract, written before either mobile client so neither could define it by accident |

### Phase execution plans

| Phase | Weeks | Milestone | Doc |
|---|---|---|---|
| 0 — Foundations | 1–4 | M0 | [phase-0-foundations.md](docs/phases/phase-0-foundations.md) |
| 1 — Walking Skeleton | 5–10 | M1 | [phase-1-walking-skeleton.md](docs/phases/phase-1-walking-skeleton.md) |
| 2 — The Daily Loop | 11–18 | M2 Internal beta | [phase-2-daily-loop.md](docs/phases/phase-2-daily-loop.md) |
| 3 — Payroll & Money | 19–30 | M3 Pilot | [phase-3-payroll.md](docs/phases/phase-3-payroll.md) |
| 4 — Talent & Performance | 31–42 | M4 | [phase-4-talent.md](docs/phases/phase-4-talent.md) |
| 5 — Depth, Engagement & Scale | 43–56 | M5 **GA** | [phase-5-scale.md](docs/phases/phase-5-scale.md) |
| 6 — Intelligence & Differentiation | 57+ | M6 | [phase-6-intelligence.md](docs/phases/phase-6-intelligence.md) |

---

## Decisions locked in

| Decision | Choice |
|---|---|
| Deliverable now | **Plan only** — build starts after your approval |
| Mobile | **Native: Kotlin/Compose (Android) + Swift/SwiftUI (iOS)** |
| Backend | **Custom: Kotlin + Spring Boot 3, PostgreSQL 16, Redis, Kafka, S3** |
| Tenancy | Shared schema + `tenant_id` + PostgreSQL Row-Level Security |
| Service shape | Modular monolith (Spring Modulith) + 3 dedicated workers |
| Mobile data | Offline-first — local SQLite is the UI's source of truth |
| API | Spec-first OpenAPI 3.1, public from day one |

---

## What the research actually found

I did **not** decompile their APK — there's no device here to run it on, and reverse-engineering their binary for private API contracts isn't something I'd do without a clear legal basis. It turned out not to matter: **PeoplesHR publishes a complete public documentation portal** at `help.peopleshr.com` that documents every screen in the product, module by module, plus a public API library. That gave a more complete and more reliable inventory than screenshots ever would.

**The scale of what they've built:**

~40 modules. Absence · Time & Attendance · Payroll (+ 4 country packs) · Payroll Simulator · Employee Information (+ 2 country variants) · EIM org master data · Employee Life Cycle · Performance Management (incl. 360/MRA and bell curve) · Recruitment · Onboarding · Offboarding · Training & Development · Benefits · Loans · Grievance Handling · Disciplinary Management · Workflow · Meals/Canteen · Document Management · Digital Signature · Dynamic Data Structure · Eligibility Configurator · Formula Builder · Enterprise Security Manager · Audit Manager · Job Scheduler · Label Configurator · On-demand Reporting · Data Import · Organizational Chart · Extension Manager · Chatbot (Lexi) · Analytics · Kiosk.

Their **mobile app is a deliberate subset** — employee and manager self-service only. It is 92.5 MB on Android and 275.6 MB on iOS, rates 4.13★ / 4.6★, and ships roughly monthly.

**Full detail:** [docs/01-research-peopleshr.md](docs/01-research-peopleshr.md)

---

## The ten things that make ours better

Each one is a direct answer to a complaint users actually filed against their app.

| # | What we do | The complaint it fixes |
|---|---|---|
| 1 | **Offline-first native app.** Every screen paints from the local database in under 100 ms; the network is a background reconciliation process. | *"Very slow."* Their most common review. |
| 2 | **Biometric login that works.** The refresh token is sealed in the Keystore / Secure Enclave and released only by fingerprint or face. No password after enrolment. | *"Fingerprint login is enabled, but username and password are still required… like no one tested this app."* |
| 3 | **The punch always succeeds.** Location capture is a server-driven policy flag (off / optional / required). If GPS fails, the punch is recorded and flagged — never rejected. | *Location is mandatory even when the employer doesn't require it, and an "ooops" error blocks check-in entirely.* |
| 4 | **Full HR-admin capability on mobile** — approvals, employee edits, payroll run monitoring, reports, and the config that changes often. | *"The mobile app lacks full administrative parity with the web dashboard."* |
| 5 | **Under 25 MB on Android, under 40 MB on iOS.** Native, no bundled runtime. | Their 92.5 MB / 275.6 MB binaries. |
| 6 | **A complete public API for every module**, OpenAPI 3.1 documented, with signed webhooks. | Their public API has no payroll, attendance, leave, loan or workflow endpoints. |
| 7 | **Search-first navigation and a command palette.** | *"Too many clicks", "complex system usage".* |
| 8 | **Guided report builder plus natural-language queries.** | *"Steep learning curve in the advanced report customization module."* |
| 9 | **One unified approval inbox across every module**, with approve/reject directly from the push notification — the app never opens. | Approvals scattered per module. |
| 10 | **Explainability everywhere.** Tap any payslip line to see the formula and inputs. Leave balance renders as an append-only statement. Attendance shows the full calculation trace from raw punches to final hours. | Nothing comparable exists in their product — and "why is my number wrong?" is the top HR support ticket in every organisation. |

---

## Architecture in one page

```
Android (Kotlin/Compose)  ·  iOS (Swift/SwiftUI)  ·  Web admin (React)
        │  offline-first: local SQLite is the UI's source of truth
        │  outbox + idempotency keys for writes, cursor delta-sync for reads
        ▼
   API Gateway  →  Modular Monolith (Spring Modulith, Kotlin/Java 21)
                     identity · core-hr · time · absence · payroll · workflow
                     talent · performance · docs · engagement
                     platform: config · formula · eligibility · audit · notify · sync
        │
        ├── PostgreSQL 16   (RLS multi-tenancy, JSONB custom fields, partitioned)
        ├── Redis           (cache, locks, rate limits)
        ├── Kafka           (events → workers)
        ├── S3              (documents, payslips)
        └── OpenSearch      (directory, documents, assistant RAG)
                 │
        Workers: payroll-engine · attendance-processor · notification-dispatcher
```

**The four platform engines** — build these early, they're what make the product configurable instead of hard-coded:

1. **Workflow engine** — every module's approvals route through it. Multi-level, parallel, conditional, with delegation and SLA escalation.
2. **Formula engine** — a sandboxed, typed expression DSL for payroll. Versioned and immutable once published, so historical payroll runs stay reproducible forever.
3. **Eligibility engine** — same substrate, answers "can this employee have X?" and always returns *why not*.
4. **Custom fields** — tenant-defined fields drive server-rendered form schemas, so a new field appears on mobile with **no app release**.

**Full detail:** [docs/03-architecture.md](docs/03-architecture.md)

---

## Delivery plan

| Milestone | Week | What exists |
|---|---|---|
| **M0 — Foundations** | 4 | Multi-tenant backend, OAuth2 with device binding, sync engine skeleton |
| **M1 — Walking skeleton** | 10 | Biometric login, employee directory, org chart, profile — fully offline |
| **M2 — Internal beta** | 18 | Leave, attendance, unified approvals, push notifications |
| **M3 — Pilot customer** | 30 | Payroll, payslips with explainer, loans, claims — one country live |
| **M4 — Feature-credible** | 42 | Performance, recruitment, onboarding, offboarding, documents |
| **M5 — GA** | 56 | 5 country packs, timesheets, engagement, reports, public API, mobile admin |
| **M6 — Differentiated** | 57+ | Assistant, predictive attrition, platform integrations, wearables |

Assumes a team of 6–8 (1 lead, 2 backend, 1 Android, 1 iOS, 1 web, 1 QA, shared designer).

**Be realistic about the far end:** they have a ~20-year head start and several hundred admin screens. Broad parity is an 18–24 month programme. But the app employees *use every day* — leave, attendance, approvals, payslips, directory — is beatable in about four months, and that's what wins the evaluation.

**Full detail:** [docs/06-roadmap.md](docs/06-roadmap.md)

---

## Legal position

Features and functionality aren't copyrightable — building an HR app with leave, attendance and payroll is entirely legitimate. What we will not do: copy their code, replicate their exact screen layouts or visual design, use their icons, illustrations or logos, reuse their marketing copy, use the "PeoplesHR" or "Lexi" names, or extract private API contracts by decompilation. Their documented feature set is being used here as a **requirements specification**; the information architecture, visual language and API are ours.

---

## Open questions for you

Three answers materially change the plan. None of them block starting Phase 0.

1. **Which countries, in what order?** This drives the statutory pack sequence and is the single biggest scope lever. My assumption for now: Sri Lanka first, then Philippines, Indonesia, UAE, Bangladesh — matching where PeoplesHR is strongest.
2. **What size customers?** 100 / 1,000 / 10,000 employees changes the partitioning, caching and roster-scheduling decisions.
3. **Are you selling this as a product, or building it for one organisation?** Multi-tenancy is already in the design either way, but it changes how much weight the configuration engines and white-labelling need.

---

## Next step

Say the word and I'll start **Phase 0**: scaffold the Spring Modulith backend with PostgreSQL RLS multi-tenancy, the OAuth2 server with device-bound biometric tokens, the OpenAPI spec-first pipeline, and both mobile projects with the design system and offline sync engine.
