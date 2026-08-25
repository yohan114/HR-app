# Phase 5 — Depth, Engagement & Scale

**Weeks 43–56 · Milestone M5 — General Availability**

---

## Goal

Turn a product that works for one pilot customer into a product that ships to any customer in five countries.

Three workstreams run in parallel: **country expansion** (4 more statutory packs, multi-currency), **operational depth** (rosters, timesheets, overtime, biometric devices), and **the commercial surface** (engagement modules, reporting, public API, HR admin on mobile).

This phase ends at **General Availability**.

---

## Entry criteria

- [ ] Phase 4 exit criteria met; hire-to-retire test green
- [ ] Pilot customer live on payroll + performance for ≥2 cycles, referenceable
- [ ] Statutory rules documented and reviewed by a local expert for each of: Philippines, Indonesia, UAE, Bangladesh
- [ ] At least 2 design partners committed for country packs 2 and 3
- [ ] Biometric device models confirmed with customers (which vendors actually need supporting)

---

## Week-by-week

### Weeks 43–46 — Country packs & multi-currency
| Who | Focus |
|---|---|
| `TL` | Country pack review gates; residency architecture for UAE/Indonesia |
| `BE1` | Philippines pack, then Indonesia pack |
| `BE2` | Rosters, shift administration, shift swap |
| `AND`/`IOS` | Roster view, shift swap, overtime request/claim |
| `WEB` | Roster admin, country pack configuration |
| `QA` | Per-country golden datasets; parallel runs with design partners |
| `DES` | Reporting, dashboards, mobile admin designs |

### Weeks 47–50 — Timesheets, devices, engagement
| Who | Focus |
|---|---|
| `TL` | Public API v1 GA readiness; webhook design |
| `BE1` | UAE pack (incl. WPS), Bangladesh pack, multi-currency, payroll simulator |
| `BE2` | Timesheets, biometric device framework, engagement modules |
| `AND`/`IOS` | Timesheets, engagement screens, surveys |
| `WEB` | Timesheet admin, engagement admin, device management |
| `QA` | Device integration tests; timesheet matrix; engagement tests |
| `DES` | Mobile admin designs finalised |

### Weeks 51–53 — Reporting, analytics, public API
| Who | Focus |
|---|---|
| `TL` | API documentation, developer portal, versioning policy |
| `BE1` | Report builder, scheduling, exports |
| `BE2` | Analytics dashboards, public API hardening, webhooks, job scheduler, data import |
| `AND`/`IOS` | **HR admin on mobile**, reports viewer, dashboards |
| `WEB` | Report builder UI, dashboard designer |
| `QA` | Report correctness, API contract tests, load tests at GA scale |
| `DES` | Phase 6 designs (assistant) |

### Weeks 54–56 — GA hardening
| Who | Focus |
|---|---|
| `TL` | GA readiness review, runbooks, on-call rotation, SLA definition |
| `BE1`/`BE2` | Performance tuning, bug fixing, migration tooling |
| `AND`/`IOS` | Widgets, Live Activity, Siri Shortcuts, polish, **public store release** |
| `WEB` | Polish, onboarding wizard for new tenants |
| `QA` | Full regression, penetration test, load test, DR drill |
| `DES` | Phase 6 designs |

---

## Workstream A — Country expansion

| ID | Task | Owner | Size |
|---|---|---|---|
| P5-BE-01 | **Philippines pack**: SSS, PhilHealth, Pag-IBIG contribution tables | BE1 | XL |
| P5-BE-02 | Philippines: withholding tax, annualisation, substituted filing | BE1 | L |
| P5-BE-03 | Philippines: **13th month pay** computation and definition | BE1 | L |
| P5-BE-04 | Philippines: de-minimis benefits, taxable/non-taxable leave encashment | BE1 | L |
| P5-BE-05 | Philippines: premium rate definitions (night differential, holiday, rest day) | BE1 | L |
| P5-BE-06 | Philippines: statutory report formats + bank formats | BE1 | L |
| P5-BE-07 | **Indonesia pack**: BPJS Kesehatan, BPJS Ketenagakerjaan | BE1 | XL |
| P5-BE-08 | Indonesia: PPh 21 calculation, PTKP, gross-up methods | BE1 | XL |
| P5-BE-09 | Indonesia: THR (religious holiday allowance) | BE1 | M |
| P5-BE-10 | Indonesia: statutory reports + bank formats | BE1 | L |
| P5-BE-11 | **UAE pack**: end-of-service gratuity calculation | BE1 | L |
| P5-BE-12 | UAE: **WPS (Wage Protection System)** SIF file generation | BE1 | XL |
| P5-BE-13 | UAE: pension for GCC nationals, labour card integration | BE1 | L |
| P5-BE-14 | **Bangladesh pack**: PF, gratuity, income tax slabs, festival bonus | BE1 | XL |
| P5-BE-15 | Bangladesh: statutory reports + bank formats | BE1 | L |
| P5-BE-16 | **Multi-currency**: `currency_rate`, FX at transaction and reporting rates | BE1 | L |
| P5-BE-17 | Parallel processing of multiple regional payrolls in one run group | BE1 | L |
| P5-BE-18 | Localised payslip formats per country | BE1 | M |
| P5-BE-19 | **Payroll simulator**: increment, new joiner, exit, bonus-cost scenarios | BE1 | XL |
| P5-BE-20 | Country-variant employee information forms (PH, ID) | BE1 | M |
| P5-BE-21 | Data residency: per-region deployment + tenant→region routing | TL | XL |

---

## Workstream B — Time depth

| ID | Task | Owner | Size |
|---|---|---|---|
| P5-BE-22 | `roster_group`, `roster` with cycle patterns, `resource_definition` | BE2 | L |
| P5-BE-23 | `roster_assignment` + cycle offset + schedule generation | BE2 | L |
| P5-BE-24 | Rotating, split, night and flexible shift computation | BE2 | XL |
| P5-BE-25 | Cross-midnight shift handling (the classic attendance bug source) | BE2 | L |
| P5-BE-26 | `shift_adjustment` + approval | BE2 | M |
| P5-BE-27 | **`shift_swap`** — mutual, both parties approve, then supervisor | BE2 | L |
| P5-BE-28 | Roster conflict detection (double-booking, rest-period violations) | BE2 | L |
| P5-BE-29 | Coverage requirements per shift + understaffing alerts | BE2 | M |
| P5-BE-30 | `overtime_request` — prior and post, approval workflow | BE2 | L |
| P5-BE-31 | `ot_cap` — daily/weekly/monthly caps, breach actions | BE2 | M |
| P5-BE-32 | OT → payroll integration with rate multipliers | BE2 | M |
| P5-BE-33 | `client`, `project`, `activity`, `employee_billing_rate` | BE2 | L |
| P5-BE-34 | `timesheet` + `timesheet_entry` + period generation | BE2 | L |
| P5-BE-35 | Timesheet approval workflow + pending-timesheet reminders | BE2 | M |
| P5-BE-36 | Timesheet ↔ attendance reconciliation (hours must agree, or explain) | BE2 | L |
| P5-BE-37 | Project profitability + utilisation analytics | BE2 | L |
| P5-BE-38 | **Biometric device framework**: connector interface, polling/push, mapping | BE2 | XL |
| P5-BE-39 | Connector: ZKTeco | BE2 | L |
| P5-BE-40 | Connector: Suprema / Hikvision | BE2 | L |
| P5-BE-41 | Device health monitoring + missed-sync alerting | BE2 | M |
| P5-BE-42 | Biometric template transfer across locations | BE2 | M |
| P5-BE-43 | Bulk leave planner, leave plans, Excel leave upload | BE2 | L |
| P5-BE-44 | Earned leave management + leave-holiday adjustments | BE2 | M |

---

## Workstream C — Engagement & relations

| ID | Task | Owner | Size |
|---|---|---|---|
| P5-BE-45 | `survey`, `survey_question`, `survey_response`, `survey_answer` | BE2 | L |
| P5-BE-46 | **Survey anonymity** — hashed respondent, minimum-N before results | BE2 | L |
| P5-BE-47 | Pulse survey scheduling + reminder cadence | BE2 | M |
| P5-BE-48 | Survey analytics: segmentation, dimension scoring, trend | BE2 | L |
| P5-BE-49 | `suggestion` box with status tracking, voting, anonymity option | BE2 | M |
| P5-BE-50 | `recognition` + `recognition_badge` + recognition wall | BE2 | M |
| P5-BE-51 | Grievance: grounds, groups, channels, templates | BE2 | L |
| P5-BE-52 | Grievance lifecycle: submit (self/team/HOD/admin), handle, resolve, appeal | BE2 | L |
| P5-BE-53 | Grievance confidentiality model + restricted handler access | BE2 | M |
| P5-BE-54 | Disciplinary: incident types/subtypes, incident reporting, journal | BE2 | L |
| P5-BE-55 | Corrective action process (9 action types) + document generation | BE2 | XL |
| P5-BE-56 | Disciplinary appeals | BE2 | M |
| P5-BE-57 | Meals: canteens, food catalogue, pricing, daily menu, order, issue | BE2 | XL |
| P5-BE-58 | Kiosk mode: shared-device auth (code / fingerprint / RFID), multilingual | BE2 | XL |
| P5-BE-59 | Travel request → advance → expense settlement | BE2 | L |

---

## Workstream D — Reporting, analytics, platform

| ID | Task | Owner | Size |
|---|---|---|---|
| P5-BE-60 | `report_definition` model + safe query generation (no raw SQL from users) | BE1 | XL |
| P5-BE-61 | Cross-module joins with permission and data-scope enforcement | BE1 | XL |
| P5-BE-62 | Report execution engine with row limits, timeouts, async for large results | BE1 | L |
| P5-BE-63 | Exports: PDF, Excel, CSV with formatting | BE1 | L |
| P5-BE-64 | `report_schedule` + delivery (email, password-protected) | BE1 | L |
| P5-BE-65 | System report library (~60 standard reports across modules) | BE1 | XL |
| P5-BE-66 | Self / supervisor / executive dashboard APIs | BE2 | L |
| P5-BE-67 | Mini widgets: milestones, years of service, age demographics, status | BE2 | M |
| P5-BE-68 | Headcount, attrition, absenteeism, cost metric computation + caching | BE2 | L |
| P5-BE-69 | **Public API v1 GA**: full OpenAPI docs, developer portal, API keys | TL | XL |
| P5-BE-70 | OAuth2 client-credentials flow for machine integrations | TL | L |
| P5-BE-71 | **Webhooks**: subscription, signed payloads, retry, dead-letter, replay | BE2 | L |
| P5-BE-72 | API rate limiting tiers + usage metering | TL | M |
| P5-BE-73 | `job_definition` + `job_execution` + scheduler dashboards (jobs, email, SMS, DB) | BE2 | L |
| P5-BE-74 | Data import framework: Excel import/export, table profiles, mapping wizard | BE2 | XL |
| P5-BE-75 | Label configurator (retitle any UI string per tenant) | BE2 | L |
| P5-BE-76 | Tenant onboarding wizard + demo-data seeding | BE1 | L |
| P5-BE-77 | Migration tooling: import from CSV/Excel with validation and rollback | BE1 | XL |

---

## Android & iOS (mirrored)

| ID | Task | Size |
|---|---|---|
| **Time depth** | | |
| P5-{AND,IOS}-01 | Roster / shift schedule view (week + month) | L |
| P5-{AND,IOS}-02 | **Shift swap** — pick your date, pick a colleague, pick theirs, submit | L |
| P5-{AND,IOS}-03 | Shift adjustment request | M |
| P5-{AND,IOS}-04 | Overtime: prior request, post claim, cap-usage bar, history | L |
| P5-{AND,IOS}-05 | **Timesheet week grid** — day × project, inline entry, running total | XL |
| P5-{AND,IOS}-06 | Timesheet quick actions: copy last week, duplicate row, bulk fill | L |
| P5-{AND,IOS}-07 | Timesheet submit + approval view | M |
| **Engagement** | | |
| P5-{AND,IOS}-08 | Surveys: list, one-question-per-screen flow, progress, anonymity notice | L |
| P5-{AND,IOS}-09 | Suggestions: submit, track, vote | M |
| P5-{AND,IOS}-10 | Recognition: give kudos, recognition wall | L |
| P5-{AND,IOS}-11 | Grievance: submit (confidential framing), track, appeal | L |
| P5-{AND,IOS}-12 | Meals: daily menu, order, history | L |
| P5-{AND,IOS}-13 | Travel request + advance + settlement | L |
| **Reporting** | | |
| P5-{AND,IOS}-14 | Reports list + saved reports | M |
| P5-{AND,IOS}-15 | Report viewer: table + chart, filters, export, share | L |
| P5-{AND,IOS}-16 | Supervisor dashboard | L |
| P5-{AND,IOS}-17 | Executive dashboard | L |
| **HR admin on mobile — the differentiator** | | |
| P5-{AND,IOS}-18 | Manage hub (admin entry point) | M |
| P5-{AND,IOS}-19 | Employee management: search, view, edit, initiate movement | L |
| P5-{AND,IOS}-20 | Add employee (multi-step, resumable, works offline) | XL |
| P5-{AND,IOS}-21 | Attendance oversight: anomaly queue, bulk regularise | L |
| P5-{AND,IOS}-22 | Leave oversight: balance adjustment, bulk actions | L |
| P5-{AND,IOS}-23 | **Payroll monitor**: live run phase, validation errors, anomaly review, approve with step-up auth | XL |
| P5-{AND,IOS}-24 | Announcement composer with audience targeting | L |
| P5-{AND,IOS}-25 | Requisition + candidate review | L |
| P5-{AND,IOS}-26 | Config lite: holiday calendar, leave types, shifts | L |
| **Platform polish** | | |
| P5-{AND,IOS}-27 | Offline behaviour for all new screens | L |
| P5-{AND,IOS}-28 | All six states, accessibility, perf | L |
| P5-{AND,IOS}-29 | RTL verification (Arabic) across the whole app | L |
| P5-{AND,IOS}-30 | Localisation complete: EN, SI, TA, ID, AR, TL | L |
| P5-AND-31 | Wear OS companion: clock in/out | L |
| P5-AND-32 | Android Enterprise / Work Profile support | M |
| P5-IOS-31 | Siri Shortcuts / App Intents ("clock in") | L |
| P5-IOS-32 | Apple Watch companion: clock in/out, approvals | L |
| P5-IOS-33 | Focus filter integration | M |

---

## Web admin

| ID | Task | Owner | Size |
|---|---|---|---|
| P5-WEB-01 | Roster designer (pattern builder, visual cycle) | WEB | XL |
| P5-WEB-02 | Roster scheduling console + conflict resolution | WEB | L |
| P5-WEB-03 | Coverage planning view | WEB | L |
| P5-WEB-04 | Overtime configuration + cap management | WEB | M |
| P5-WEB-05 | Timesheet admin: clients, projects, activities, rates | WEB | L |
| P5-WEB-06 | Timesheet approval console + utilisation reports | WEB | L |
| P5-WEB-07 | Biometric device management + health dashboard | WEB | L |
| P5-WEB-08 | Country pack configuration per company | WEB | L |
| P5-WEB-09 | Payroll simulator UI with scenario comparison | WEB | L |
| P5-WEB-10 | Survey builder + analytics dashboard | WEB | XL |
| P5-WEB-11 | Grievance admin + handling console | WEB | L |
| P5-WEB-12 | Disciplinary admin + corrective action console | WEB | XL |
| P5-WEB-13 | Meals admin: catalogue, pricing, menus, reports | WEB | L |
| P5-WEB-14 | **Report builder** — field picker, filters, grouping, charts, preview | WEB | XL |
| P5-WEB-15 | Report scheduling + distribution | WEB | M |
| P5-WEB-16 | Dashboard designer | WEB | L |
| P5-WEB-17 | Job scheduler dashboards | WEB | M |
| P5-WEB-18 | Data import wizard | WEB | XL |
| P5-WEB-19 | Label configurator UI | WEB | M |
| P5-WEB-20 | **Tenant onboarding wizard** — the self-serve setup path | WEB | XL |
| P5-WEB-21 | Developer portal: API docs, keys, webhook management | WEB | L |

---

## QA

| ID | Task | Owner | Size |
|---|---|---|---|
| P5-QA-01 | **Golden dataset per country** (PH, ID, AE, BD) — 200 employees each | QA | XL |
| P5-QA-02 | Parallel runs with design partners for PH and ID — zero variance gate | QA | XL |
| P5-QA-03 | 13th month / THR / gratuity / festival bonus correctness | QA | L |
| P5-QA-04 | WPS SIF file validation against UAE central bank spec | QA | L |
| P5-QA-05 | Multi-currency: FX application, reporting rate vs transaction rate | QA | L |
| P5-QA-06 | **Country pack isolation** — changing PH rules must not affect SL results | QA | L |
| P5-QA-07 | Rotating/split/night shift computation matrix | QA | XL |
| P5-QA-08 | **Cross-midnight shift matrix** (the classic bug source) | QA | L |
| P5-QA-09 | Shift swap: both-party approval, conflict rejection, rest-period rules | QA | M |
| P5-QA-10 | Timesheet ↔ attendance reconciliation tests | QA | M |
| P5-QA-11 | Biometric device integration tests with physical hardware | QA | L |
| P5-QA-12 | Survey anonymity verification (same rigour as MRA) | QA | L |
| P5-QA-13 | Grievance confidentiality — no unauthorised path to content | QA | L |
| P5-QA-14 | Report correctness vs. hand-computed values across 20 standard reports | QA | XL |
| P5-QA-15 | Report permission enforcement — data scope respected in every report | QA | L |
| P5-QA-16 | Public API contract tests + backwards-compatibility suite | QA | L |
| P5-QA-17 | Webhook delivery, retry, signature verification, replay | QA | M |
| P5-QA-18 | **GA load test**: 50 tenants, 100k total employees, 5k concurrent users | QA | XL |
| P5-QA-19 | **Penetration test** (external vendor) | QA | XL |
| P5-QA-20 | **Disaster recovery drill**: restore from backup, verify RPO/RTO | QA | L |
| P5-QA-21 | Data migration tooling validation with real customer data | QA | L |
| P5-QA-22 | RTL / localisation audit across 6 languages | QA | L |
| P5-QA-23 | Full regression Phases 1–5 | QA | XL |

---

## Exit criteria — GA readiness

| # | Criterion | Verification |
|---|---|---|
| 1 | **5 country packs live** (SL, PH, ID, AE, BD), each with a zero-variance parallel run | `P5-QA-02` + design partner sign-off |
| 2 | Country pack isolation proven — changing one country's rules doesn't affect another | `P5-QA-06` |
| 3 | Rotating, split, night and cross-midnight shifts compute correctly | `P5-QA-07`, `P5-QA-08` |
| 4 | Biometric device integration working with ≥2 vendors on physical hardware | `P5-QA-11` |
| 5 | Public API v1 published with complete OpenAPI docs and a developer portal | Live |
| 6 | Webhooks deliver reliably with signature verification and retry | `P5-QA-17` |
| 7 | **HR admin on mobile** — an HR manager can run a full day's work from a phone | Manual scenario walkthrough |
| 8 | Report builder produces correct results with data scope enforced | `P5-QA-14`, `P5-QA-15` |
| 9 | Load test: 100k employees / 5k concurrent, p95 API latency < 400 ms | `P5-QA-18` |
| 10 | **Penetration test passed** with no high or critical findings outstanding | External report |
| 11 | DR drill: RPO ≤ 15 min, RTO ≤ 4 h, verified by restore | `P5-QA-20` |
| 12 | 6 languages complete; Arabic RTL correct throughout | `P5-QA-22` |
| 13 | Apps published to the public App Store and Play Store | Live listings |
| 14 | App size still < 25 MB Android / < 40 MB iOS **after** all Phase 4–5 features | CI size gate |
| 15 | Runbooks, on-call rotation, SLA and status page in place | Ops review |
| 16 | ≥3 customers live in production across ≥2 countries | Customer sign-off |

---

## Demo script (end of week 56)

1. **Five countries, one run group** — Trigger payroll for Sri Lanka, Philippines, Indonesia, UAE and Bangladesh companies in parallel. Show each producing correct statutory deductions. Show the UAE WPS SIF file and the Philippines 13th-month computation.
2. **Rosters** — Build a 3-shift rotating roster for a factory. Assign 200 employees. Show conflict detection catching a rest-period violation. Then a night shift crossing midnight computing the right hours.
3. **Shift swap on mobile** — Two employees swap shifts from their phones; supervisor approves; the roster updates for both.
4. **Timesheets** — Week grid, copy last week, submit, manager approves, project profitability report updates.
5. **Biometric device** — Physical fingerprint reader. Punch. Watch it appear in the app within seconds and flow into `daily_attendance`.
6. **HR admin on mobile** — This is the differentiator demo. On a phone only: review the attendance anomaly queue and bulk-regularise; adjust a leave balance; open the payroll monitor and watch a live run; approve it with a Face ID step-up; publish an announcement targeted at one department. *Read out the G2 quote about their app lacking admin parity.*
7. **Report builder** — Build "headcount by department and gender, last 12 months, with attrition" from scratch in under two minutes. Chart it. Schedule it weekly to the CHRO, password-protected.
8. **Public API** — Open the developer portal. Create an API key. Call the payroll endpoint from a terminal. Register a webhook, trigger a leave approval, show the signed payload arriving. *Contrast with their API library, which has no payroll, attendance, or leave endpoints.*
9. **Scale** — Live load-test dashboard: 100k employees, 5k concurrent users, p95 latency.
10. **Store listings** — Both apps live publicly. Show the size: ours vs theirs.

---

## Phase risks

| Risk | Trigger | Owner | Mitigation |
|---|---|---|---|
| **Four country packs in 14 weeks is aggressive** | PH pack not zero-variance by week 46 | TL | Ship in strict priority order. If only PH and ID make GA, that's acceptable — AE and BD slip to Phase 6. **Do not ship an unverified country pack.** |
| Country pack cross-contamination | `P5-QA-06` failures | BE1 | Plugin isolation with per-country test suites; no shared conditional logic; a country's code cannot import another's |
| WPS file rejected by UAE banks | Test submission fails | BE1 | Submit a test SIF to a partner bank by week 50, not at GA |
| Cross-midnight shift bugs | `P5-QA-08` failures | BE2 | This is the single most common attendance defect in the industry. Dedicated test matrix built before the implementation. |
| Biometric device vendor SDKs are poor or undocumented | Integration stalls | BE2 | Confirm actual customer device models in the entry criteria. Prefer standard protocols (push/HTTP) over vendor SDKs where possible. |
| Report builder becomes an unbounded query engine | Slow queries in production | BE1 | Hard row limits, statement timeouts, async execution for large results, no user-supplied SQL, mandatory data-scope injection |
| Penetration test finds critical issues late | Week 55 findings | TL | Book the pen-test for week 51, not week 55, so there is time to remediate before GA |
| Team burnout — this is the widest phase | Velocity drop, rising defect rate | TL | Three parallel workstreams with clear owners; explicit cut list published at week 43; protect the hardening weeks 54–56 |
| App size creeps past budget with all the new features | CI size gate goes red | AND/IOS | Per-ABI splits, on-demand resources, dynamic feature modules for admin surfaces, asset audit at week 50 |

---

## The published cut list (decided at week 43, before pressure arrives)

If the phase runs hot, these are cut in this order — decided now, in the cold light of day:

1. Meals / canteen module → Phase 6
2. Kiosk mode → Phase 6
3. Wear OS + Apple Watch companions → Phase 6
4. Disciplinary corrective-action document generation (keep incident tracking) → Phase 6
5. Travel requests → Phase 6
6. Bangladesh country pack → Phase 6
7. UAE country pack → Phase 6

**Never cut:** country packs PH and ID, rosters, timesheets, public API, HR admin on mobile, report builder, pen-test, load test, DR drill.
