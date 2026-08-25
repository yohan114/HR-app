# Feature Matrix — What We Build

Derived from [01-research-peopleshr.md](01-research-peopleshr.md). This is the requirements specification.

**Legend**
- **P0** — MVP. Without it the app is not an HR app.
- **P1** — Fast-follow. Needed to be credible against PeoplesHR.
- **P2** — Parity. Closes the remaining gap.
- **P3** — Differentiation. Where we beat them.

**Surface**: `M` = mobile (Kotlin/Swift), `W` = web admin console, `A` = API only.

---

## 1. Platform & Identity

| # | Feature | Pri | Surface | Notes |
|---|---|---|---|---|
| 1.1 | Multi-tenant org provisioning | P0 | W/A | Tenant = customer company. Row-level isolation. |
| 1.2 | Email/username + password login | P0 | M/W | Argon2id hashing. |
| 1.3 | Tenant discovery by email domain or org code | P0 | M | Avoid asking users for a "service URL" (PeoplesHR does — bad UX). |
| 1.4 | **Biometric unlock that actually works** | P0 | M | Refresh token sealed in Android Keystore / iOS Secure Enclave, released only by BiometricPrompt/Face ID. Password re-entry only on enrolment, token revocation, or 30-day idle. Directly fixes complaint #2. |
| 1.5 | MFA (TOTP + SMS) | P1 | M/W | |
| 1.6 | SSO — SAML 2.0 + OIDC (Azure AD, Google, Okta) | P1 | M/W | |
| 1.7 | Device registration & trusted-device list | P1 | M/W | Remote revoke. |
| 1.8 | Session management, forced logout, active-login health | P1 | W | Mirrors their Enterprise Security Manager. |
| 1.9 | RBAC — roles, capability groups, menu activation | P0 | W/A | |
| 1.10 | ABAC data scoping — "my team", "my location", "my cost centre", custom filter rules | P1 | W/A | Their Data Filters / Filter Rules / Security Groups. |
| 1.11 | Field-level permissions (view/edit/mask per field) | P1 | A | Salary, bank details must be maskable. |
| 1.12 | Delegation / impersonation (with audit) | P1 | M/W | Their Workflow Delegation + Impersonate. |
| 1.13 | Full audit trail, field-level, with aliases | P1 | W/A | |
| 1.14 | Password policy configuration | P1 | W | |
| 1.15 | Encryption at rest incl. column-level for PII/salary | P0 | A | |
| 1.16 | Offline-first sync engine w/ conflict resolution | P0 | M | The core architectural bet. Fixes complaint #1. |
| 1.17 | Push notifications (FCM + APNs) with deep links | P0 | M | |
| 1.18 | i18n — English, Sinhala, Tamil, Bahasa, Arabic (RTL), Filipino | P1 | M/W | RTL is a real requirement for UAE. |
| 1.19 | Label Configurator — retitle any string per tenant | P2 | W | |
| 1.20 | Theming / white-label (logo, palette, app icon) | P1 | M/W | |
| 1.21 | Dark mode | P0 | M | |
| 1.22 | Accessibility: TalkBack/VoiceOver, dynamic type, contrast | P0 | M | Non-negotiable. |

---

## 2. Core HR — Employee Master

| # | Feature | Pri | Surface |
|---|---|---|---|
| 2.1 | Employee record: Personal / Employment / Workstation / Contact / Other | P0 | M/W |
| 2.2 | Configurable employee code schemes | P1 | W |
| 2.3 | Profile photo + document attachments | P0 | M/W |
| 2.4 | Dependents, Emergency Contacts, Nominees, Transport (Census) | P0 | M/W |
| 2.5 | Education & professional qualifications | P1 | M/W |
| 2.6 | Work experience history | P1 | M/W |
| 2.7 | Bank details (multi-account, split %) | P0 | M/W |
| 2.8 | Passport / visa / work permit / Labour Card, with expiry alerts | P1 | M/W |
| 2.9 | Memberships & bargaining units | P2 | W |
| 2.10 | Languages, extra-curricular activities | P2 | W |
| 2.11 | Contract & contract extension tracking | P1 | W |
| 2.12 | Covering / acting duty assignments | P2 | W |
| 2.13 | Reporting hierarchy + change supervisor | P0 | W |
| 2.14 | Employee directory with smart search (name, skill, location, dept) | P0 | M |
| 2.15 | Org chart — interactive, unlimited depth, permission-aware | P1 | M/W |
| 2.16 | Bulk upload / Excel import with validation preview | P1 | W |
| 2.17 | Employee 360 profile (lifecycle + attendance + leave + performance + training on one screen) | P1 | M/W |
| 2.18 | Effective-dated records + "view as of date" | P1 | A |
| 2.19 | Employee self-update with approval workflow | P0 | M |
| 2.20 | Custom fields (Dynamic Data Structure) — text, number, dropdown, date, radio, checkbox, attachment, employee-picker | P1 | W/A |

**Org master data (EIM)** — P1, web: Location, Company Definition, Company Hierarchy, Cost Centre, Sub Location, Job Location, Salary Grade, Corporate Title, Designation, Job Descriptions & KRAs, Industry Type, Qualification taxonomy, Membership taxonomy, Benefit catalogue, Employee Category/Group/Type/Title, Statutory Classification, Function & Functional Roles, Gender/Marital/Blood Group, Attachment & Currency types, Route/Dwelling/Station, Relationships, Nationality/Religion/Race, geographic hierarchy (Country→Province→District→Division), WPS company master.

---

## 3. Employee Life Cycle

| # | Feature | Pri | Surface |
|---|---|---|---|
| 3.1 | Movement types & groups (promotion, transfer, confirmation, redesignation, secondment, suspension) | P1 | W |
| 3.2 | Movement application → shortlist → approve → effect | P1 | M/W |
| 3.3 | Eligibility rules per movement type | P1 | W |
| 3.4 | Cascade on effect: payroll, benefits, access rights, org chart | P1 | A |
| 3.5 | Bulk movement application | P2 | W |
| 3.6 | Movement rollback + effective-date update | P2 | W |
| 3.7 | **Visual career timeline** for employee | P1 | M |
| 3.8 | Movement analytics: mobility patterns, high-potential, bottlenecks | P3 | W |

---

## 4. Time & Attendance

| # | Feature | Pri | Surface | Notes |
|---|---|---|---|---|
| 4.1 | Tap clock in/out | P0 | M | |
| 4.2 | **Policy-driven location capture** | P0 | M | Server flag decides: `off` / `optional` / `required`. Never request permission when `off`. Never block a punch on GPS failure — queue with reason. **Fixes complaint #3.** |
| 4.3 | Geofence validation (multi-site, radius per site) | P0 | M/A | |
| 4.4 | Mock-location / rooted-device detection | P1 | M | Flag for review, do not hard-block. |
| 4.5 | Offline punch queue with guaranteed delivery | P0 | M | Punches recorded locally, synced with original timestamp + trust metadata. |
| 4.6 | Selfie / face-match check-in (on-device ML) | P1 | M | Optional per policy; template stays on device. |
| 4.7 | NFC / QR site check-in | P2 | M | |
| 4.8 | Biometric device ingestion (ZKTeco, Suprema, Hikvision, generic) | P1 | A | |
| 4.9 | Kiosk mode (shared tablet, employee code / fingerprint / RFID) | P2 | M | Multilingual. |
| 4.10 | Shift definitions (fixed, rotating, split, night, flexible) | P0 | W |
| 4.11 | Roster groups, roster info, resource definitions | P1 | W |
| 4.12 | Roster scheduling + assign employees to roster | P1 | W |
| 4.13 | Shift adjustment + **mutual shift swap** (employee-to-employee, approved) | P1 | M |
| 4.14 | Grace periods, rounding rules, late/early rules | P0 | W |
| 4.15 | Overtime: config, thresholds, max OT cap codes, prior OT application, claim & approve | P0 | M/W |
| 4.16 | Attendance data process (nightly recompute) | P0 | A |
| 4.17 | Attendance summary — self, team, company | P0 | M/W |
| 4.18 | Manual in/out request + approval | P0 | M |
| 4.19 | Attendance approval queue | P0 | M |
| 4.20 | Anomaly detection (missing punch, impossible duration, pattern breaks) | P1 | W |
| 4.21 | Timesheets: client/project/activity hierarchy, billing rates, multi-currency | P1 | M/W |
| 4.22 | Timesheet entry (quick, bulk, from calendar), pending timesheets, approval | P1 | M |
| 4.23 | Project profitability & utilisation analytics | P2 | W |
| 4.24 | **Live team attendance board** (who's in, who's late, who's remote) | P3 | M |

---

## 5. Absence / Leave

| # | Feature | Pri | Surface |
|---|---|---|---|
| 5.1 | Leave year, leave groups, leave types, short-leave types | P0 | W |
| 5.2 | Day types, calendar groups, holiday calendars (multi-country) | P0 | W |
| 5.3 | Entitlement rules: accrual, pro-rata, carry-forward, encashment, expiry, max balance | P0 | W |
| 5.4 | Employee & group entitlements, bulk upload | P1 | W |
| 5.5 | Apply leave (full/half/hourly short leave), attachments, reason codes | P0 | M |
| 5.6 | Real-time balance & projected balance at date | P0 | M |
| 5.7 | Leave history + cancellation + amendment | P0 | M |
| 5.8 | My Leave Chart / My Leave Plan | P1 | M |
| 5.9 | Team & company leave calendar ("who else is off") | P0 | M |
| 5.10 | Multi-level approval routing by duration/type/impact | P0 | M/A |
| 5.11 | Bulk leave, subordinate bulk leave, bulk leave planner, Excel upload | P2 | W |
| 5.12 | Earned-leave management, leave-holiday adjustments, balance recalculation | P1 | W |
| 5.13 | Supervisor & self leave dashboards | P1 | M |
| 5.14 | Leave-plan submission for the year | P2 | M |
| 5.15 | **Coverage conflict detection** — warn before approving when team drops below threshold | P3 | M |
| 5.16 | **Suggested cover** — surface qualified available colleagues | P3 | M |
| 5.17 | Days-since-last-leave nudge + burnout signal | P3 | M |

---

## 6. Payroll

| # | Feature | Pri | Surface |
|---|---|---|---|
| 6.1 | Pay groups, pay periods, process periods, period lock | P0 | W |
| 6.2 | Pay items (earning / deduction / employer-cost / info), with formulas | P0 | W |
| 6.3 | **Formula engine** — safe sandboxed expression DSL over employee/attendance/leave variables | P0 | A |
| 6.4 | Salary grades, basic salary, grade-based benefit assignment | P0 | W |
| 6.5 | Assign pay items to employee / group / classification; bulk + Excel upload | P0 | W |
| 6.6 | Salary amendment, revision, cancellation, grade amendment | P1 | W |
| 6.7 | Tax engine: define taxes, brackets, exemptions, adjustments, annualisation | P0 | W |
| 6.8 | Statutory packs (SL: EPF/ETF/APIT · PH: SSS/PhilHealth/Pag-IBIG/13th month/de-minimis · ID: BPJS/PPh21 · AE: WPS/gratuity · BD) | P0→P1 | A |
| 6.9 | Multi-currency + FX rates | P1 | A |
| 6.10 | **Payroll run**: validate → preview → commit → publish, fully idempotent & reversible | P0 | W |
| 6.11 | Pre-process validation dashboard + anomaly dashboard | P1 | W |
| 6.12 | Pay warnings, avoid-negative-salary guard | P1 | W |
| 6.13 | Other payments run (bonus, ad-hoc) | P1 | W |
| 6.14 | Final payment / full & final settlement | P1 | W |
| 6.15 | **Payslip** — interactive, multilingual, YoY comparison, breakdown, PDF export | P0 | M |
| 6.16 | Payslip security (masked, biometric-gated, no screenshot on Android) | P1 | M |
| 6.17 | Bank file generation (per-bank templates), rollback, password protection | P0 | W |
| 6.18 | GL export: mapping navigator, process, rollback | P1 | W |
| 6.19 | Statutory report generation & filing exports | P1 | W |
| 6.20 | Payroll simulator: increment / new joiner / exit / bonus cost budgeting | P2 | W |
| 6.21 | Payroll cost analytics & headcount cost by cost centre | P2 | W |
| 6.22 | Payroll approval workflow with segregation of duties | P1 | W |
| 6.23 | Retro-pay / arrears calculation | P1 | A |
| 6.24 | **Payroll run live status on mobile for HR** | P3 | M |

---

## 7. Compensation, Benefits & Loans

| # | Feature | Pri | Surface |
|---|---|---|---|
| 7.1 | Benefit types + localisation + master data | P1 | W |
| 7.2 | Cash / non-cash benefit catalogue, grade assignment | P1 | W |
| 7.3 | Eligibility criteria engine | P1 | W |
| 7.4 | Apply for benefit, benefit history, cancel | P1 | M |
| 7.5 | Team benefit application (manager on behalf) | P2 | M |
| 7.6 | Benefit approval queue | P1 | M |
| 7.7 | Loan types, entitlement rules, checklists, workflow config | P1 | W |
| 7.8 | Apply loan → eligibility auto-check → approve → schedule instalments | P1 | M |
| 7.9 | Loan history, balance, repayment schedule, early settlement | P1 | M |
| 7.10 | Bulk loan apply / settle / history, loan uploader, loan stop config | P2 | W |
| 7.11 | Expense claims & reimbursements with receipt capture + OCR | P1 | M |
| 7.12 | Travel request → advance → expense → settlement | P2 | M |
| 7.13 | **Total rewards statement** (salary + benefits + employer cost, one view) | P3 | M |

---

## 8. Performance Management

| # | Feature | Pri | Surface |
|---|---|---|---|
| 8.1 | Competency framework: groups, areas, competencies, collections, proficiency levels/profiles | P1 | W |
| 8.2 | Rating methods & scales | P1 | W |
| 8.3 | Goal groups, goal setting, cascading goals, KPIs with weights | P1 | M/W |
| 8.4 | Evaluation cycles: create, configure, upload assessment details | P1 | W |
| 8.5 | Participants: assessees, assessors, 2nd-level reviewers | P1 | W |
| 8.6 | Self-assessment + manager assessment + reviewer sign-off | P1 | M |
| 8.7 | **MRA / 360**: rater groups, questionnaires, anonymised aggregation | P2 | M/W |
| 8.8 | Bell curve modelling / forced distribution | P2 | W |
| 8.9 | Critical incident journal + approval | P2 | M |
| 8.10 | Continuous feedback / praise / 1-on-1 notes | P1 | M |
| 8.11 | Assessment summary & history | P1 | M |
| 8.12 | Succession planning & 9-box grid | P3 | W |
| 8.13 | **AI goal suggestions from job description + past cycles** | P3 | M |

---

## 9. Talent Acquisition

| # | Feature | Pri | Surface |
|---|---|---|---|
| 9.1 | Manpower requisition (new vs replacement) + approval | P1 | M/W |
| 9.2 | Vacancy processing, hiring manager assignment | P1 | W |
| 9.3 | Advertisement management + job portal (public careers page) | P2 | W |
| 9.4 | Application intake (portal, offline entry, referral) | P1 | W |
| 9.5 | CV pool + candidate search | P1 | W |
| 9.6 | CV parsing → structured profile | P2 | A |
| 9.7 | Filter/shortlist candidates, side-by-side comparison | P2 | W |
| 9.8 | Interview scheduling + scorecards + panel feedback | P2 | M/W |
| 9.9 | Background check tracking | P3 | W |
| 9.10 | Offer generation + e-signature | P2 | W |
| 9.11 | Talent pool nurture | P3 | W |
| 9.12 | Time-to-hire / funnel analytics | P2 | W |
| 9.13 | **Interviewer mobile scorecard** (rate right after the interview) | P3 | M |

---

## 10. Onboarding & Offboarding

| # | Feature | Pri | Surface |
|---|---|---|---|
| 10.1 | Onboarding stages, events, actions, profiles | P1 | W |
| 10.2 | Candidate info management + bulk upload | P1 | W |
| 10.3 | Assign actions to profile, onboarding progress (individual + aggregate) | P1 | M/W |
| 10.4 | Task approval | P1 | M |
| 10.5 | **Pre-boarding portal for the candidate** (before day 1, mobile) | P2 | M |
| 10.6 | Buddy assignment | P3 | M |
| 10.7 | Exit types, reasons, interviewers, question templates | P1 | W |
| 10.8 | Exit notice (employee + admin), exit reversal | P1 | M/W |
| 10.9 | Exit interview questionnaire + interview record | P1 | M/W |
| 10.10 | Clearance process: work handover items & templates, clearance admin mapping, benefit clearance head | P1 | M/W |
| 10.11 | Exit clearance approval workflow | P1 | M |
| 10.12 | Attrition reason analytics | P2 | W |

---

## 11. Learning & Development

| # | Feature | Pri | Surface |
|---|---|---|---|
| 11.1 | Course catalogue, training providers, resource persons | P2 | W |
| 11.2 | Training calendar & schedule | P2 | M/W |
| 11.3 | Enrollment (self + nominated) + approval | P2 | M |
| 11.4 | Training attendance capture | P2 | M |
| 11.5 | Trainee evaluation / feedback | P2 | M |
| 11.6 | Training history on employee profile, certification expiry alerts | P2 | M |
| 11.7 | Skill gap → recommended courses | P3 | M |

---

## 12. Employee Engagement & Relations

| # | Feature | Pri | Surface |
|---|---|---|---|
| 12.1 | Company news feed / announcements with targeting | P0 | M |
| 12.2 | Company calendar & events | P1 | M |
| 12.3 | Birthday & work-anniversary milestones | P0 | M |
| 12.4 | Recognition / kudos / peer praise | P2 | M |
| 12.5 | Pulse surveys + full surveys, segmented analysis, action planning | P2 | M/W |
| 12.6 | Suggestions / ideas box with status tracking + attachments | P2 | M |
| 12.7 | Grievance: grounds, groups, channels, templates; my/team/HOD/admin application; handling; appeal; history | P2 | M/W |
| 12.8 | Disciplinary: incident reporting, incident types/subtypes, journal, corrective actions (background check, charge sheet, oral warning, warning letter, show-cause, domestic inquiry, court case), appeals | P2 | W |
| 12.9 | Request tracker (all my requests, all modules, one list) | P0 | M |
| 12.10 | Meals/canteen: events, food categories & items, pricing, daily menu, order meal, issue meal, canteen mapping, reports | P3 | M |
| 12.11 | **Anonymous voice-of-employee channel** | P2 | M |

---

## 13. Workflow Engine (cross-cutting — build first)

| # | Feature | Pri | Surface |
|---|---|---|---|
| 13.1 | Workflow types & groups | P0 | W |
| 13.2 | Approval-person resolution: named, role-based, supervisor-N-levels-up, dynamic expression | P0 | W |
| 13.3 | Multi-level, parallel, and conditional routing | P0 | A |
| 13.4 | **Unified "For You" approval inbox** across every module | P0 | M |
| 13.5 | Bulk approve / reject with comment | P0 | M |
| 13.6 | Delegation (date-bounded, audited) | P1 | M |
| 13.7 | Impersonation / apply-on-behalf | P1 | M/W |
| 13.8 | Workflow summary, application history, SLA & escalation timers | P1 | M/W |
| 13.9 | Push + email + in-app notification per step | P0 | M |
| 13.10 | **Approve from the notification** (no app open needed) | P3 | M |
| 13.11 | Withdraw / recall an application | P1 | M |

---

## 14. Documents & Signature

| # | Feature | Pri | Surface |
|---|---|---|---|
| 14.1 | Folder/file hierarchy, tags, permissions, archive, trash | P1 | M/W |
| 14.2 | Personal document vault (contract, payslips, certificates) | P0 | M |
| 14.3 | Document templates + auto-generation (employment letter, salary confirmation, NOC) | P1 | M/W |
| 14.4 | **Self-service letter request** — employee requests, system generates, manager signs | P1 | M |
| 14.5 | E-signature: settings, template management, signing summary | P2 | M/W |
| 14.6 | Document approval workflow | P1 | M |
| 14.7 | SharePoint / Google Drive / OneDrive connector | P3 | A |
| 14.8 | Expiry tracking & renewal reminders (visa, contract, certification) | P1 | M |

---

## 15. Reporting & Analytics

| # | Feature | Pri | Surface |
|---|---|---|---|
| 15.1 | Standard report library per module | P1 | M/W |
| 15.2 | Report builder: field selection, filters, grouping, cross-module joins | P1 | W |
| 15.3 | Saved reports ("My Reports") + scheduled delivery (email, password-protected) | P1 | W |
| 15.4 | Export: PDF / Excel / CSV | P1 | M/W |
| 15.5 | Self-analytics dashboard (my headcount context, my data) | P1 | M |
| 15.6 | Supervisor dashboard (team headcount, attendance, leave, performance) | P0 | M |
| 15.7 | Executive dashboard (headcount, attrition, absenteeism, cost, engagement) | P1 | M/W |
| 15.8 | Mini widgets: milestones, years of service, age demographics, status, work-related | P1 | M |
| 15.9 | Predictive attrition scoring with per-employee probability + drivers | P3 | W |
| 15.10 | Headcount & cost forecasting | P3 | W |
| 15.11 | **Natural-language query** ("show me attrition in Ops last quarter") | P3 | M/W |
| 15.12 | Benchmarking against anonymised cross-tenant aggregates (opt-in) | P3 | W |

---

## 16. Assistant / AI Layer (our answer to "Lexi")

| # | Feature | Pri | Surface |
|---|---|---|---|
| 16.1 | Global search across employees, pages, documents, records | P1 | M/W |
| 16.2 | Command palette / quick actions | P1 | M/W |
| 16.3 | Conversational assistant: policy Q&A grounded in tenant's own documents (RAG) | P2 | M |
| 16.4 | Action execution: "apply leave next Friday", "who's on leave this week" | P2 | M |
| 16.5 | Voice input | P3 | M |
| 16.6 | Assistant admin console: knowledge sources, guardrails, usage analytics | P2 | W |
| 16.7 | Permission-aware answers (never leaks data the user can't see) | P2 | A |
| 16.8 | Payslip explainer ("why is my net pay lower this month?") | P3 | M |
| 16.9 | Smart nudges (approve overdue items, expiring documents, unused leave) | P2 | M |

---

## 17. Integrations & Extensibility

| # | Feature | Pri | Surface |
|---|---|---|---|
| 17.1 | **Complete public REST API, OpenAPI 3.1 documented, all modules** | P1 | A |
| 17.2 | Webhooks with signed payloads + retry | P1 | A |
| 17.3 | OAuth2 client credentials for machine integrations | P1 | A |
| 17.4 | Biometric device connectors | P1 | A |
| 17.5 | Bank file format library | P0 | A |
| 17.6 | Accounting/ERP GL connectors (SAP, Oracle, QuickBooks, Xero) | P2 | A |
| 17.7 | Microsoft Teams + Slack apps (approvals, check-in, directory, news) | P2 | A |
| 17.8 | Calendar sync (leave → Outlook/Google) | P2 | A |
| 17.9 | Job scheduler: frequency setup, job/email/SMS dashboards, test mail | P1 | W |
| 17.10 | Data import: Excel import/export, table profiles, mapping wizard | P1 | W |
| 17.11 | Extension framework (custom forms, custom summary pages) | P3 | W |

---

## 18. Where we deliberately beat PeoplesHR

The ten things that make this "better", tied to the complaints in §7 of the dossier:

1. **Instant UI.** Offline-first, native. Every screen paints from local store in <100 ms; network is background. → fixes "very slow".
2. **Biometric login that works.** Sealed refresh token, no password after enrolment. → fixes the single most-cited store complaint.
3. **Location is policy-driven, never a blocker.** Punch always succeeds; location is metadata. → fixes the check-in "ooops" failures.
4. **Full HR-admin on mobile.** Approvals, employee edits, payroll monitoring, reports, common config. → fixes "no admin parity".
5. **App size under 25 MB.** Native, no bundled runtime. → 4× smaller than theirs.
6. **A real public API for everything**, OpenAPI-documented, with webhooks. → they have no public payroll/attendance/leave API.
7. **Search-first navigation + command palette.** → fixes "too many clicks", "complex system usage".
8. **Guided report builder + natural-language query.** → fixes "steep learning curve on reports".
9. **Unified approval inbox with notification-level actions.** Approve without opening the app.
10. **Explainability everywhere** — payslip explainer, leave balance derivation, attendance calculation trace. HR software that shows its working builds trust and kills support tickets.
