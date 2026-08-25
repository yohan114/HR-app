# Phase 4 — Talent & Performance

**Weeks 31–42 · Milestone M4 — Feature-Credible**

---

## Goal

Complete the hire-to-retire cycle: recruitment, onboarding, performance management, learning, employee movements, documents, and offboarding.

After Phase 3 we have an HRIS a company can run payroll on. After Phase 4 we have an HRIS a company can run its **people processes** on — which is what turns an evaluation into a purchase.

---

## Entry criteria

- [ ] Phase 3 exit criteria met; pilot customer live on payroll for ≥1 full cycle
- [ ] Pilot customer's performance cycle calendar and competency framework obtained
- [ ] Second design pass complete for performance, recruitment, onboarding flows
- [ ] Workflow engine proven in production (it now carries recruitment, movements, and offboarding too)

---

## Why this order within the phase

Employee Life Cycle comes **first** (weeks 31–33), before recruitment and offboarding, because both of them terminate in a lifecycle movement — a hire creates an employee record, an exit closes one. Building movements last would mean retrofitting both.

Documents come **second** because offer letters, onboarding packs, appraisal records and exit letters all depend on template generation.

---

## Week-by-week

### Weeks 31–33 — Life cycle & documents
| Who | Focus |
|---|---|
| `TL` | Movement cascade design; document template engine design |
| `BE1` | Employee Life Cycle: movement types, applications, approval, cascade, rollback |
| `BE2` | Document management: folders, files, permissions, versions, templates, e-signature |
| `AND`/`IOS` | Lifecycle timeline; document vault; letter request |
| `WEB` | Movement admin; document template designer |
| `QA` | Movement cascade test matrix; document permission tests |
| `DES` | Performance screens finalised |

### Weeks 34–37 — Performance management
| Who | Focus |
|---|---|
| `TL` | Assessment state machine review; MRA anonymity model |
| `BE1` | Competency framework, goals, KPIs, evaluation cycles, assessment lifecycle |
| `BE2` | MRA/360, bell curve, critical incidents, continuous feedback |
| `AND`/`IOS` | My performance, goals, self-assessment, team assessment, feedback |
| `WEB` | Competency admin, cycle configuration, calibration console |
| `QA` | Assessment state tests; scoring/weighting matrix; anonymity verification |
| `DES` | Recruitment + onboarding screens |

### Weeks 38–40 — Recruitment & onboarding
| Who | Focus |
|---|---|
| `TL` | Candidate data retention/GDPR model; job portal security |
| `BE1` | Requisition, vacancy, candidate, application pipeline, interviews, offers |
| `BE2` | Onboarding stages, actions, profiles, instances, tasks, progress |
| `AND`/`IOS` | Requisition approval, interviewer scorecard, onboarding checklist |
| `WEB` | ATS console, candidate pipeline, job portal, onboarding designer |
| `QA` | Pipeline state tests; candidate PII/retention tests; portal security tests |
| `DES` | Offboarding + learning screens |

### Weeks 41–42 — Offboarding, learning, hardening
| Who | Focus |
|---|---|
| `TL` | Phase 5 planning (country packs, timesheets, engagement) |
| `BE1` | Offboarding: exit notice, interview, clearance, handover |
| `BE2` | Training: courses, providers, schedules, enrollment, attendance, evaluation |
| `AND`/`IOS` | Exit flows, clearance tasks, learning screens, polish |
| `WEB` | Offboarding admin, training admin |
| `QA` | Full hire-to-retire end-to-end test; regression |
| `DES` | Phase 5 designs |

---

## Task backlog

### Employee Life Cycle — weeks 31–33

| ID | Task | Owner | Size |
|---|---|---|---|
| P4-BE-01 | `movement_group`, `movement_type`, `movement_reason` | BE1 | M |
| P4-BE-02 | Movement type configuration: which fields it affects, eligibility, workflow binding | BE1 | M |
| P4-BE-03 | `movement` table with `from_values`/`to_values` JSONB snapshots | BE1 | M |
| P4-BE-04 | Movement application → shortlist → approve state machine | BE1 | L |
| P4-BE-05 | **Cascade on effect** — update employee, payroll assignments, benefits, access rights, org chart, in one transaction | BE1 | XL |
| P4-BE-06 | Effective-date scheduling (movement applies on a future date via job) | BE1 | L |
| P4-BE-07 | Update-effective-date on an approved-but-unapplied movement | BE1 | M |
| P4-BE-08 | **Movement rollback** with full cascade reversal | BE1 | L |
| P4-BE-09 | Bulk movement application (restructures, mass increments) | BE1 | L |
| P4-BE-10 | `employee_history` population from movements | BE1 | M |
| P4-BE-11 | Career timeline API | BE1 | M |
| P4-BE-12 | Supervisor change → `employee_hierarchy` ltree re-materialisation | BE1 | M |

### Documents & signature — weeks 31–33

| ID | Task | Owner | Size |
|---|---|---|---|
| P4-BE-13 | `document_folder` hierarchy with path materialisation | BE2 | M |
| P4-BE-14 | `document` + `document_version` + versioning semantics | BE2 | L |
| P4-BE-15 | Document permissions: owner, role, explicit share, inherited from folder | BE2 | L |
| P4-BE-16 | `document_tag` + tagging + tag-based search | BE2 | M |
| P4-BE-17 | Archive + trash + restore + retention policy | BE2 | M |
| P4-BE-18 | Personal document vault (auto-populated: contract, payslips, certificates) | BE2 | M |
| P4-BE-19 | **`document_template`** with variable substitution from employee context | BE2 | L |
| P4-BE-20 | Document generation service → PDF/DOCX | BE2 | L |
| P4-BE-21 | **Letter request flow** — employee requests, system generates, manager approves/signs | BE2 | L |
| P4-BE-22 | `signature_request` + `signature_signer` + sequential/parallel signing | BE2 | L |
| P4-BE-23 | E-signature capture, audit trail, tamper-evident hash | BE2 | L |
| P4-BE-24 | Document expiry tracking + renewal reminders | BE2 | M |
| P4-BE-25 | Document approval workflow integration | BE2 | M |

### Performance — weeks 34–37

| ID | Task | Owner | Size |
|---|---|---|---|
| P4-BE-26 | `competency_group`, `competency_area`, `competency`, `competency_collection` | BE1 | M |
| P4-BE-27 | `rating_method`, `proficiency_level`, `proficiency_profile` (per designation) | BE1 | M |
| P4-BE-28 | `goal_group` + weighting rules + total-weight validation | BE1 | M |
| P4-BE-29 | `evaluation_cycle` with phase windows (self / manager / review / calibration) | BE1 | L |
| P4-BE-30 | Participant assignment: assessees, assessors, 2nd-level reviewers | BE1 | L |
| P4-BE-31 | Bulk participant assignment + change participants mid-cycle | BE1 | M |
| P4-BE-32 | **`assessment` state machine** (9 states) | BE1 | XL |
| P4-BE-33 | `goal` — creation, cascading from manager goals, progress tracking | BE1 | L |
| P4-BE-34 | Goal library + templates by designation | BE1 | M |
| P4-BE-35 | `assessment_competency` — self and manager ratings, gap computation | BE1 | M |
| P4-BE-36 | **Score computation**: weighted goals + weighted competencies + MRA → final | BE1 | L |
| P4-BE-37 | `rater_group`, `mra_question`, `mra_assessor`, `mra_response` | BE2 | L |
| P4-BE-38 | **MRA anonymity**: aggregate-only exposure, minimum-N threshold before showing results | BE2 | L |
| P4-BE-39 | MRA questionnaire builder + assignment by rater group | BE2 | M |
| P4-BE-40 | **Bell curve / forced distribution** modelling + calibration adjustment | BE2 | L |
| P4-BE-41 | Calibration session support (compare, adjust, justify) | BE2 | L |
| P4-BE-42 | `critical_incident` + approval + employee visibility control | BE2 | M |
| P4-BE-43 | `feedback_note` — praise, suggestion, 1-on-1, check-in | BE2 | M |
| P4-BE-44 | Assessment history + trend API | BE1 | M |
| P4-BE-45 | Upload assessment details (bulk import of ratings) | BE1 | M |

### Recruitment — weeks 38–40

| ID | Task | Owner | Size |
|---|---|---|---|
| P4-BE-46 | `requisition` — new vs replacement, budget flag, approval workflow | BE1 | L |
| P4-BE-47 | `vacancy` + hiring manager and recruiter assignment | BE1 | M |
| P4-BE-48 | `advertisement` + channel management | BE1 | M |
| P4-BE-49 | **Public job portal** — vacancy list, detail, apply form, file upload | BE1 | XL |
| P4-BE-50 | Job portal security: rate limiting, CAPTCHA, no tenant enumeration, upload scanning | TL | L |
| P4-BE-51 | `candidate` + `candidate_qualification` + dedupe on email/phone | BE1 | L |
| P4-BE-52 | **Candidate PII & retention**: consent capture, `retention_until`, erasure job | TL | L |
| P4-BE-53 | CV upload + storage + OpenSearch indexing | BE1 | M |
| P4-BE-54 | `application` pipeline state machine (10 stages) | BE1 | L |
| P4-BE-55 | Candidate search + filter + shortlist + side-by-side comparison | BE1 | L |
| P4-BE-56 | CV pool + talent pool tagging | BE1 | M |
| P4-BE-57 | Offline application entry (recruiter enters a walk-in candidate) | BE1 | M |
| P4-BE-58 | `interview` + `interview_panel` + scheduling + calendar invites | BE1 | L |
| P4-BE-59 | `interview_scorecard` — competency-based, per interviewer | BE1 | L |
| P4-BE-60 | `offer` — generation from template, approval workflow, e-signature | BE1 | L |
| P4-BE-61 | `background_check` tracking | BE1 | M |
| P4-BE-62 | **Hire conversion** — application → candidate → employee record + movement | BE1 | L |
| P4-BE-63 | Recruitment funnel analytics (time-to-hire, stage conversion, source quality) | BE1 | M |

### Onboarding — weeks 38–40

| ID | Task | Owner | Size |
|---|---|---|---|
| P4-BE-64 | `onboarding_stage`, `onboarding_action`, `onboarding_profile` | BE2 | M |
| P4-BE-65 | Profile-to-action assignment with per-instance overrides | BE2 | M |
| P4-BE-66 | `onboarding_instance` + task generation with date offsets from join date | BE2 | L |
| P4-BE-67 | Task assignment by role (IT, facilities, HR, manager) | BE2 | M |
| P4-BE-68 | Task approval workflow integration | BE2 | M |
| P4-BE-69 | Progress tracking: individual and aggregate | BE2 | M |
| P4-BE-70 | **Pre-boarding portal** — candidate access before day 1, limited scope token | BE2 | L |
| P4-BE-71 | Candidate information collection + bulk upload | BE2 | M |
| P4-BE-72 | Buddy assignment | BE2 | S |
| P4-BE-73 | Exclude-vacancies configuration | BE2 | S |

### Offboarding — weeks 41–42

| ID | Task | Owner | Size |
|---|---|---|---|
| P4-BE-74 | `exit_type`, `exit_reason` with category taxonomy | BE1 | M |
| P4-BE-75 | `exit_notice` — employee-initiated and admin-initiated, approval, reversal | BE1 | L |
| P4-BE-76 | Notice period calculation + last-working-date negotiation | BE1 | M |
| P4-BE-77 | `exit_question_group`, `exit_question`, `exit_question_template` | BE1 | M |
| P4-BE-78 | `exit_interview` + `exit_interview_response` (self-service + interviewer-led) | BE1 | L |
| P4-BE-79 | `handover_item`, `handover_template` | BE1 | M |
| P4-BE-80 | `clearance` + `clearance_task` with department routing | BE1 | L |
| P4-BE-81 | Clearance approval workflow + recoverable-amount capture → final settlement | BE1 | L |
| P4-BE-82 | Exit → movement → employee status change cascade | BE1 | M |
| P4-BE-83 | Attrition reason analytics | BE1 | M |

### Learning — weeks 41–42

| ID | Task | Owner | Size |
|---|---|---|---|
| P4-BE-84 | `training_provider`, `resource_person`, `course` | BE2 | M |
| P4-BE-85 | `training_schedule` with seats and cost | BE2 | M |
| P4-BE-86 | `training_enrollment` — self, nominated, mandatory + approval workflow | BE2 | L |
| P4-BE-87 | `training_attendance` capture | BE2 | M |
| P4-BE-88 | `trainee_evaluation` (participant feedback + trainer assessment) | BE2 | M |
| P4-BE-89 | Certificate issuance + expiry tracking + renewal alerts | BE2 | M |
| P4-BE-90 | `training_need` from competency gaps → recommended courses | BE2 | M |
| P4-BE-91 | Training history on employee profile | BE2 | S |

### Android & iOS (mirrored)

| ID | Task | Size |
|---|---|---|
| **Lifecycle & documents** | | |
| P4-{AND,IOS}-01 | **My lifecycle timeline** — visual career progression | L |
| P4-{AND,IOS}-02 | Movement application (manager initiates for a team member) | L |
| P4-{AND,IOS}-03 | Document vault: browse, search, preview (PDF, image, Office) | L |
| P4-{AND,IOS}-04 | **Letter request** — pick type, submit, track, download signed result | L |
| P4-{AND,IOS}-05 | E-signature capture (draw/type) with audit consent | L |
| P4-{AND,IOS}-06 | Document expiry alerts + renewal action | M |
| **Performance** | | |
| P4-{AND,IOS}-07 | My performance home: cycle status, phase, deadlines | L |
| P4-{AND,IOS}-08 | Goals: list, detail, progress update, evidence attachment | L |
| P4-{AND,IOS}-09 | Goal creation with library/template picker | L |
| P4-{AND,IOS}-10 | **Self-assessment** — goals + competencies, autosave, resumable | XL |
| P4-{AND,IOS}-11 | Manager assessment of a direct report | XL |
| P4-{AND,IOS}-12 | 2nd-level review + acknowledgement | L |
| P4-{AND,IOS}-13 | MRA questionnaire completion (anonymity clearly communicated) | L |
| P4-{AND,IOS}-14 | Assessment history + score trend chart | M |
| P4-{AND,IOS}-15 | **Continuous feedback**: give praise, log a 1-on-1, request feedback | L |
| P4-{AND,IOS}-16 | Critical incident logging | M |
| P4-{AND,IOS}-17 | Team performance dashboard (cycle progress, overdue) | L |
| **Recruitment & onboarding** | | |
| P4-{AND,IOS}-18 | Requisition creation + approval | L |
| P4-{AND,IOS}-19 | **Interviewer scorecard** — rate immediately after the interview | L |
| P4-{AND,IOS}-20 | Interview schedule view + candidate CV preview | M |
| P4-{AND,IOS}-21 | Candidate shortlist review (swipe-friendly comparison) | L |
| P4-{AND,IOS}-22 | Offer approval | M |
| P4-{AND,IOS}-23 | Onboarding checklist — new hire view | L |
| P4-{AND,IOS}-24 | Onboarding tasks — assignee view (IT, facilities, manager) | L |
| P4-{AND,IOS}-25 | Pre-boarding experience for candidates (limited-scope auth) | L |
| **Offboarding & learning** | | |
| P4-{AND,IOS}-26 | Exit notice submission | M |
| P4-{AND,IOS}-27 | Exit interview questionnaire | L |
| P4-{AND,IOS}-28 | Clearance tasks — my tasks + approver view | L |
| P4-{AND,IOS}-29 | Learning: catalogue, course detail, enrol | L |
| P4-{AND,IOS}-30 | Training calendar + my enrollments | M |
| P4-{AND,IOS}-31 | Training attendance check-in | M |
| P4-{AND,IOS}-32 | Certificates + expiry | M |
| P4-{AND,IOS}-33 | Course feedback form | M |
| **Cross-cutting** | | |
| P4-{AND,IOS}-34 | Offline behaviour for all new screens | L |
| P4-{AND,IOS}-35 | All six states, accessibility, perf | L |

### Web admin

| ID | Task | Owner | Size |
|---|---|---|---|
| P4-WEB-01 | Movement type designer + eligibility + cascade configuration | WEB | L |
| P4-WEB-02 | Movement processing console + bulk movements | WEB | L |
| P4-WEB-03 | Document management console (folders, permissions, bulk operations) | WEB | L |
| P4-WEB-04 | **Document template designer** with variable picker and preview | WEB | XL |
| P4-WEB-05 | E-signature configuration + signing summary | WEB | M |
| P4-WEB-06 | Competency framework admin (groups, areas, competencies, collections, profiles) | WEB | XL |
| P4-WEB-07 | Rating method + proficiency level admin | WEB | M |
| P4-WEB-08 | **Evaluation cycle designer** — phases, windows, participants, weights | WEB | XL |
| P4-WEB-09 | MRA questionnaire builder + rater group configuration | WEB | L |
| P4-WEB-10 | **Calibration console** — bell curve view, drag to adjust, justification capture | WEB | XL |
| P4-WEB-11 | Assessment monitoring: completion rates, overdue, nudge | WEB | L |
| P4-WEB-12 | ATS console: requisitions, vacancies, pipeline kanban | WEB | XL |
| P4-WEB-13 | Candidate detail: CV viewer, timeline, scorecards, comparison | WEB | L |
| P4-WEB-14 | Job portal configuration + branding | WEB | L |
| P4-WEB-15 | Offer generation + approval | WEB | M |
| P4-WEB-16 | Onboarding designer: stages, actions, profiles | WEB | L |
| P4-WEB-17 | Onboarding monitoring dashboard | WEB | M |
| P4-WEB-18 | Offboarding admin: exit types, question templates, handover templates, clearance mapping | WEB | L |
| P4-WEB-19 | Exit monitoring + attrition analytics | WEB | M |
| P4-WEB-20 | Training admin: courses, providers, schedules, enrollments | WEB | L |

### QA

| ID | Task | Owner | Size |
|---|---|---|---|
| P4-QA-01 | **Movement cascade matrix** — every movement type × every affected system | QA | XL |
| P4-QA-02 | Movement rollback correctness (full reversal, no orphans) | QA | L |
| P4-QA-03 | Future-dated movement application via scheduler | QA | M |
| P4-QA-04 | Hierarchy re-materialisation after supervisor change (deep trees) | QA | M |
| P4-QA-05 | Document permission matrix (owner/role/share/inherited × read/write/delete) | QA | L |
| P4-QA-06 | Template generation with all variable types + missing-variable handling | QA | M |
| P4-QA-07 | E-signature audit trail + tamper detection | QA | M |
| P4-QA-08 | **Assessment state machine** — all 9 states, all transitions, all role permissions | QA | XL |
| P4-QA-09 | Score computation matrix: goal weights, competency weights, MRA weights, edge cases | QA | L |
| P4-QA-10 | **MRA anonymity verification** — no API path exposes an individual rater's response | QA | L |
| P4-QA-11 | Bell curve + calibration adjustment correctness | QA | M |
| P4-QA-12 | Application pipeline state tests | QA | L |
| P4-QA-13 | **Job portal security**: rate limit, CAPTCHA, no enumeration, malicious upload rejection | QA | L |
| P4-QA-14 | Candidate retention/erasure job correctness | QA | M |
| P4-QA-15 | Hire conversion: candidate → employee with no data loss | QA | M |
| P4-QA-16 | Onboarding task generation with date offsets across weekends/holidays | QA | M |
| P4-QA-17 | Clearance → final settlement integration | QA | M |
| P4-QA-18 | **Full hire-to-retire end-to-end test** (see below) | QA | XL |
| P4-QA-19 | Full regression Phases 1–4 | QA | L |

---

## Deliverables

### Database tables (~55)
All lifecycle, document, signature, performance, recruitment, onboarding, offboarding, and training tables from [04-data-model.md](../04-data-model.md) sections 5, 11, 12, 13, 15.

### API endpoints
```
GET/POST /v1/movements, /v1/movements/{id}/approve|apply|rollback
GET      /v1/me/timeline

GET/POST /v1/documents, /v1/documents/folders
POST     /v1/documents/templates/{id}/generate
POST     /v1/me/letter-requests
POST     /v1/signatures/{id}/sign

GET      /v1/performance/cycles
GET      /v1/me/assessments/{cycleId}
PUT      /v1/me/assessments/{cycleId}/self
GET/POST /v1/performance/goals
POST     /v1/performance/assessments/{id}/submit|review|acknowledge
GET/POST /v1/performance/mra
POST     /v1/performance/feedback

GET/POST /v1/recruitment/requisitions|vacancies|candidates|applications
POST     /v1/recruitment/applications/{id}/advance|reject
GET/POST /v1/recruitment/interviews, /v1/recruitment/scorecards
POST     /v1/recruitment/offers, /v1/recruitment/applications/{id}/hire
GET      /v1/public/jobs                       ← unauthenticated job portal
POST     /v1/public/applications

GET/POST /v1/onboarding/instances|tasks
GET/POST /v1/offboarding/exit-notices|interviews|clearances
GET/POST /v1/training/courses|schedules|enrollments
```

### Screens
~35 new per platform. Web admin gains its largest surface area of the programme.

---

## Exit criteria

| # | Criterion | Verification |
|---|---|---|
| 1 | **Full hire-to-retire cycle runs end-to-end** on the pilot tenant | `P4-QA-18` |
| 2 | Movement cascade updates every dependent system atomically; rollback fully reverses | `P4-QA-01`, `P4-QA-02` |
| 3 | A complete performance cycle runs: self → manager → review → calibration → acknowledge | Manual on pilot tenant |
| 4 | **MRA responses are provably anonymous** — no API path exposes an individual rater | `P4-QA-10` |
| 5 | Job portal survives security testing (rate limit, enumeration, upload) | `P4-QA-13` |
| 6 | Candidate erasure job removes all PII on `retention_until` | `P4-QA-14` |
| 7 | Offer letter generates from template, routes for approval, is e-signed, and is stored with a tamper-evident audit trail | Manual |
| 8 | Clearance recoverable amounts flow into the final settlement payroll run | `P4-QA-17` |
| 9 | Pilot customer runs one full performance cycle on the system | Customer sign-off |
| 10 | All Phase 1–3 criteria and budgets still met | Regression |

---

## The hire-to-retire end-to-end test (`P4-QA-18`)

This single automated test is the phase's headline deliverable. It must pass on every build.

```
1.  Create a requisition (replacement, budgeted)      → approved via workflow
2.  Open a vacancy, publish to the job portal
3.  Candidate applies via the public portal with a CV
4.  Recruiter shortlists; schedules 2 interviews
5.  Interviewers submit scorecards from mobile
6.  Offer generated from template → approved → e-signed by candidate
7.  Hire conversion → employee record created + JOIN movement applied
8.  Onboarding instance generated; 12 tasks across 4 owners
9.  New hire completes pre-boarding; IT and facilities complete their tasks
10. Employee clocks in on day 1                        → attendance recorded
11. Employee applies for leave                         → approved via workflow
12. Payroll run includes the new hire, prorated        → payslip published
13. Performance cycle opens; goals set; self-assessment; manager assessment;
    calibration; acknowledgement
14. PROMOTION movement applied                          → salary, designation,
                                                          org chart, payroll all update
15. Employee submits exit notice                       → approved
16. Exit interview completed
17. Clearance tasks across 5 departments completed; one recoverable amount captured
18. Final payment run                                  → settlement payslip includes
                                                          the recovery
19. Employee status → EXITED; access revoked; documents retained per policy

ASSERT at every step: correct data, correct permissions, correct audit entries,
                      no orphaned records, no cross-tenant leakage.
```

---

## Demo script (end of week 42)

1. **The full cycle, live** — Run the hire-to-retire test in front of the audience with a live dashboard. It takes about 4 minutes. This is the demo.
2. **Interviewer scorecard on mobile** — Finish an interview, open the phone, rate against competencies, submit. Show it appearing on the recruiter's pipeline board instantly.
3. **Performance cycle** — Employee's phone: goals with progress, self-assessment with autosave (kill the app mid-form, reopen, it's still there). Manager's phone: assess a report. Web: calibration console with the bell curve, drag a rating, capture the justification.
4. **MRA anonymity** — Show 5 raters submitting. Show the aggregate. Then attempt, via the API with an admin token, to retrieve an individual response — denied. Show the minimum-N threshold blocking results when only 2 raters have responded.
5. **Letter request** — Employee taps "Request salary confirmation letter". Manager gets a notification, approves, signs. Employee downloads the signed PDF. Elapsed: about 90 seconds.
6. **Promotion cascade** — Apply a promotion. Watch designation, salary, org chart, payroll pay items, and benefit entitlements all update. Then roll it back and watch every one revert.
7. **Offboarding** — Exit notice → interview → clearance across 5 departments on 5 different phones → recoverable amount → final settlement payslip showing the recovery.

---

## Phase risks

| Risk | Trigger | Owner | Mitigation |
|---|---|---|---|
| **Movement cascade misses a dependency** and corrupts data | `P4-QA-01` failures, or a production data inconsistency | BE1 | The cascade is a single explicit registry of handlers, not scattered listeners. Adding a module *requires* registering or explicitly declining a handler — enforced by a compile-time check. |
| Performance module scope is bigger than it looks | Week 37 with self-assessment incomplete | TL | MRA/360, bell curve and calibration are the cut candidates — move to Phase 5 if needed. Goals + self + manager + review is the non-negotiable core. |
| MRA anonymity leak | Any path exposing individual responses | TL | Anonymity is enforced at the repository layer, not the controller. `P4-QA-10` probes every endpoint including admin ones. Minimum-N threshold is a hard database constraint. |
| Job portal is a public attack surface | Any security finding | TL | It's the only unauthenticated surface in the product. Separate rate-limit tier, CAPTCHA, upload sandboxing, no tenant enumeration, and a dedicated pen-test before launch. |
| Candidate PII creates compliance exposure | Retention job not working | TL | Consent captured at application; `retention_until` set on creation; erasure job tested; documented in the privacy policy |
| Document template engine becomes a mini-CMS | Scope creep in `P4-WEB-04` | WEB | Variables + conditionals + tables only. No loops, no scripting. If a customer needs more, they upload a DOCX with merge fields. |
| Two platforms drift on the large assessment forms | Divergent autosave behaviour | TL | Assessment form state model specified once, implemented identically; shared test scenarios |

---

## Not in Phase 4

- Succession planning / 9-box (Phase 6)
- CV parsing and candidate ranking (Phase 6)
- Video interview intelligence (out of scope — deliberately)
- Learning content delivery / LMS (out of scope — we manage training, we don't host courses)
- Rosters, timesheets, overtime claims (Phase 5)
- Country packs 2–5 (Phase 5)
