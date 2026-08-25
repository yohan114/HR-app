# Phase 2 — The Daily Loop

**Weeks 11–18 · Milestone M2 — Internal Beta**

---

## Goal

Ship the four things employees actually open an HR app for: **clock in/out, apply for leave, approve requests, see what's happening.** All of it working offline, all of it explainable.

This is the phase where the product becomes real. At the end of it, your own staff run their working week on it.

---

## Entry criteria

- [ ] Phase 1 exit criteria all met
- [ ] Workflow engine design reviewed and signed off (drafted during Phase 1 week 7)
- [ ] Leave policy rules gathered from at least one real organisation — accrual method, carry-forward, entitlement slabs
- [ ] Internal beta cohort identified (target: 30–50 of your own employees)

---

## The sequencing rule that governs this phase

**The workflow engine ships in weeks 11–13, before leave and attendance.** Every module in the product routes approvals through it. Building leave first and retrofitting approvals would mean rewriting leave. This is non-negotiable.

---

## Week-by-week

### Weeks 11–13 — Workflow engine (the foundation)

| Who | Focus |
|---|---|
| `TL` | Workflow engine architecture, resolver contract, event model |
| `BE1` | Workflow schema, definition/step model, instance & task lifecycle |
| `BE2` | Resolvers (named, role, supervisor-level, expression, group), SLA & escalation |
| `AND` | Approvals inbox UI, item detail, swipe actions, bulk mode |
| `IOS` | Same |
| `WEB` | Workflow definition designer |
| `QA` | Workflow state-machine test suite (this needs to be exhaustive) |
| `DES` | Leave + attendance screen designs finalised |

### Weeks 14–16 — Absence & attendance

| Who | Focus |
|---|---|
| `TL` | Attendance processing pipeline design; accrual engine review |
| `BE1` | Leave: definitions, entitlement rules, accrual engine, `leave_ledger`, application lifecycle |
| `BE2` | Attendance: policy, punch ingestion, processor, `daily_attendance` computation |
| `AND` | Clock in/out flow; attendance calendar + day detail; leave apply + balance |
| `IOS` | Same |
| `WEB` | Leave type/calendar/entitlement admin; attendance policy & shift admin |
| `QA` | Accrual test matrix; attendance computation test matrix; offline punch chaos tests |
| `DES` | Announcement + engagement designs |

### Weeks 17–18 — Integration, beta, hardening

| Who | Focus |
|---|---|
| `TL` | Beta coordination, feedback triage, Phase 3 planning (formula engine design) |
| `BE1` | Announcements, milestones, request tracker |
| `BE2` | Notification wiring for every workflow event; performance tuning |
| `AND` | Team calendar, request tracker, announcements, notification actions, polish |
| `IOS` | Same |
| `WEB` | Approval monitoring, leave/attendance oversight screens |
| `QA` | Full regression, beta support, bug triage |
| `DES` | Phase 3 designs (payslip, claims, loans) |

---

## Task backlog

### Workflow engine — weeks 11–13

| ID | Task | Owner | Size |
|---|---|---|---|
| P2-BE-01 | `workflow_type` global catalogue + module registration | BE1 | S |
| P2-BE-02 | `workflow_definition` + `workflow_step` schema, versioning, publish semantics | BE1 | L |
| P2-BE-03 | `workflow_instance` + `workflow_task` + `workflow_history` schema | BE1 | M |
| P2-BE-04 | **State machine**: start, advance, complete, reject, return, withdraw, expire | BE1 | XL |
| P2-BE-05 | Resolver: `NAMED_USER` | BE2 | S |
| P2-BE-06 | Resolver: `ROLE` (with data-scope intersection) | BE2 | M |
| P2-BE-07 | Resolver: `SUPERVISOR_LEVEL_N` (walks the ltree hierarchy) | BE2 | M |
| P2-BE-08 | Resolver: `DEPARTMENT_HEAD`, `INITIATOR_MANAGER` | BE2 | M |
| P2-BE-09 | Resolver: `EXPRESSION` (uses the eligibility expression substrate) | BE2 | L |
| P2-BE-10 | Approval modes: `ALL`, `ANY`, `QUORUM` | BE1 | M |
| P2-BE-11 | Conditional step skipping (`skip_if_expression`) | BE1 | M |
| P2-BE-12 | Parallel step branches | BE1 | L |
| P2-BE-13 | SLA timers + escalation job (reassign / notify / auto-approve) | BE2 | L |
| P2-BE-14 | `workflow_delegation` — date-bounded, per workflow type, audited | BE2 | M |
| P2-BE-15 | Impersonation / apply-on-behalf with audit | BE2 | M |
| P2-BE-16 | **Unified inbox API** — all pending tasks across all modules, one endpoint | BE1 | L |
| P2-BE-17 | Bulk action API (approve/reject N tasks with a shared comment) | BE1 | M |
| P2-BE-18 | **Signed single-use action tokens** for notification-level approval | BE1 | L |
| P2-BE-19 | Workflow events → Kafka → notification dispatcher | BE2 | M |
| P2-BE-20 | `WorkflowClient` facade — the only way modules interact with the engine | TL | M |
| P2-BE-21 | Retrofit Phase-1 profile edits onto the workflow engine | BE1 | M |

### Absence — weeks 14–16

| ID | Task | Owner | Size |
|---|---|---|---|
| P2-BE-22 | `leave_year`, `leave_group`, `leave_type`, `short_leave_type` | BE1 | M |
| P2-BE-23 | `day_type`, `calendar_group`, `holiday_calendar` with location scoping | BE1 | M |
| P2-BE-24 | `leave_entitlement_rule` — all accrual methods | BE1 | L |
| P2-BE-25 | **Accrual engine** — scheduled job, idempotent, effective-dated | BE1 | XL |
| P2-BE-26 | Pro-rata on join / exit | BE1 | M |
| P2-BE-27 | Carry-forward with expiry + encashment | BE1 | L |
| P2-BE-28 | Service-based entitlement slabs | BE1 | M |
| P2-BE-29 | **`leave_ledger`** append-only + `employee_leave_entitlement` projection | BE1 | L |
| P2-BE-30 | Balance reconstruction from ledger (the explainability guarantee) | BE1 | M |
| P2-BE-31 | `leave_application` + `leave_application_day` + day-expansion logic | BE1 | L |
| P2-BE-32 | Working-day calculation: holidays, weekly offs, half days, `counts_holidays` flag | BE1 | L |
| P2-BE-33 | Balance projection API (`what will my balance be on date X`) | BE1 | M |
| P2-BE-34 | Leave eligibility checks with **reasons on failure** | BE1 | M |
| P2-BE-35 | Application → workflow integration; approve/reject → ledger entry | BE1 | M |
| P2-BE-36 | Cancellation and withdrawal with ledger reversal | BE1 | M |
| P2-BE-37 | Short leave application + monthly instance limits | BE1 | M |
| P2-BE-38 | Team & company leave calendar API (permission-scoped) | BE1 | M |
| P2-BE-39 | Coverage conflict detection (warn when team drops below threshold) | BE1 | M |
| P2-BE-40 | `leave_reason` + attachment requirement rules | BE1 | S |

### Time & attendance — weeks 14–16

| ID | Task | Owner | Size |
|---|---|---|---|
| P2-BE-41 | `shift` table — all shift types, grace, rounding, OT config | BE2 | M |
| P2-BE-42 | `employee_shift_schedule` — one row per employee per day | BE2 | M |
| P2-BE-43 | Schedule generation job (from default shift; roster comes in Phase 5) | BE2 | M |
| P2-BE-44 | **`attendance_policy`** — `location_capture` OFF/OPTIONAL/REQUIRED, geofence enforcement, mock-location action | BE2 | L |
| P2-BE-45 | `raw_punch` table, monthly partitioning, partition maintenance job | BE2 | M |
| P2-BE-46 | **Punch ingestion API** — idempotent, accepts offline batches with original timestamps | BE2 | L |
| P2-BE-47 | Geofence evaluation (point-in-radius, multi-site, nearest match) | BE2 | M |
| P2-BE-48 | Trust metadata: mock-location flag, accuracy, offline-recorded flag, clock-skew detection | BE2 | M |
| P2-BE-49 | **Attendance processor**: punch pairing into sessions | BE2 | L |
| P2-BE-50 | Processor: grace periods, rounding rules, late/early computation | BE2 | L |
| P2-BE-51 | Processor: worked minutes, break handling, OT computation | BE2 | L |
| P2-BE-52 | Processor: leave reconciliation, holiday/weekly-off resolution, day status | BE2 | L |
| P2-BE-53 | **`calculation_trace`** — every step recorded for the day-detail explainer | BE2 | L |
| P2-BE-54 | Recompute API (delete + rebuild `daily_attendance` from `raw_punch`) | BE2 | M |
| P2-BE-55 | Anomaly detection: missing punch, impossible duration, geofence violation | BE2 | M |
| P2-BE-56 | `manual_attendance_request` + workflow integration | BE2 | M |
| P2-BE-57 | Attendance summary APIs: self, team, company | BE2 | M |
| P2-BE-58 | Live team attendance board API (who's in / late / remote / off) | BE2 | M |

### Engagement basics — weeks 17–18

| ID | Task | Owner | Size |
|---|---|---|---|
| P2-BE-59 | `announcement` + audience targeting + `announcement_read` | BE1 | M |
| P2-BE-60 | Acknowledgement flow for policy announcements | BE1 | M |
| P2-BE-61 | `company_event` + calendar API | BE1 | M |
| P2-BE-62 | Milestones API (birthdays, anniversaries) with privacy opt-out | BE1 | S |
| P2-BE-63 | **Request tracker** — all my submissions across all modules, one endpoint | BE1 | M |
| P2-BE-64 | Home card payloads for leave balance, who's out, pending-on-you, team today | BE1 | M |

### Android & iOS (mirrored)

| ID | Task | Size |
|---|---|---|
| **Approvals** | | |
| P2-{AND,IOS}-01 | Approvals inbox: unified list, filters (type/requester/age/SLA), overdue-first | L |
| P2-{AND,IOS}-02 | Swipe actions: right = approve, left = reject-with-comment | M |
| P2-{AND,IOS}-03 | Bulk mode: multi-select, shared comment, batch submit | L |
| P2-{AND,IOS}-04 | Approval item detail: summary, requester context, approval chain, attachments | L |
| P2-{AND,IOS}-05 | **Notification action buttons** — approve/reject from the shade, app never opens | L |
| P2-{AND,IOS}-06 | Delegation setup screen | M |
| P2-{AND,IOS}-07 | My requests tracker + withdraw action | M |
| P2-{AND,IOS}-08 | Approval history (searchable) | M |
| **Attendance** | | |
| P2-{AND,IOS}-09 | **Clock in/out** with the never-blocks location flow (all 3 policy modes) | XL |
| P2-{AND,IOS}-10 | Location acquisition: fused provider, timeout, last-known fallback, accuracy threshold | L |
| P2-{AND,IOS}-11 | Geofence evaluation on-device + status chip (Verified / Outside / Location off) | M |
| P2-{AND,IOS}-12 | Mock-location detection (flag, never block) | M |
| P2-{AND,IOS}-13 | **Offline punch queue** with original timestamp + "queued" badge | L |
| P2-{AND,IOS}-14 | Live elapsed-shift timer | M |
| P2-{AND,IOS}-15 | My attendance calendar (month grid, colour-coded day status) | L |
| P2-{AND,IOS}-16 | **Attendance day detail with full calculation trace** | L |
| P2-{AND,IOS}-17 | Manual in/out request form | M |
| P2-{AND,IOS}-18 | My shift schedule view | M |
| **Leave** | | |
| P2-{AND,IOS}-19 | Leave home: balance rings per type, upcoming leave with countdown | L |
| P2-{AND,IOS}-20 | **Apply leave**: calendar showing holidays, weekly offs, team conflicts, live balance impact | XL |
| P2-{AND,IOS}-21 | Half-day toggles, reason, remarks, attachment, covering person, contact-while-away | L |
| P2-{AND,IOS}-22 | Pre-submit review card ("3 working days · balance after: 9.5") | M |
| P2-{AND,IOS}-23 | **Leave balance statement** — the ledger rendered as an explainable statement | L |
| P2-{AND,IOS}-24 | Leave history with filters, cancel, withdraw | M |
| P2-{AND,IOS}-25 | Team leave calendar with conflict highlighting | L |
| P2-{AND,IOS}-26 | Holiday calendar + add-to-device-calendar | M |
| P2-{AND,IOS}-27 | Short leave application | M |
| **Home & engagement** | | |
| P2-{AND,IOS}-28 | Home cards: clock, pending-on-you, leave balance, who's out, team today | L |
| P2-{AND,IOS}-29 | Announcements feed + detail + acknowledgement | L |
| P2-{AND,IOS}-30 | Company calendar | M |
| P2-{AND,IOS}-31 | Nudges card (overdue approvals, unused leave) | M |
| **Cross-cutting** | | |
| P2-{AND,IOS}-32 | Offline behaviour for every screen in this phase | L |
| P2-{AND,IOS}-33 | All six screen states for every new screen | L |
| P2-{AND,IOS}-34 | Accessibility pass for all new screens | L |
| P2-{AND,IOS}-35 | Perf budget verification | M |

**Platform extras**

| ID | Task | Size |
|---|---|---|
| P2-AND-36 | Home-screen widget: clock in/out + today's status | L |
| P2-AND-37 | Quick Settings tile for clock in/out | M |
| P2-IOS-36 | Lock Screen + Home Screen widgets (WidgetKit) | L |
| P2-IOS-37 | **Live Activity / Dynamic Island** — running shift timer | L |

### Web admin

| ID | Task | Owner | Size |
|---|---|---|---|
| P2-WEB-01 | **Workflow definition designer** — visual step builder, resolver config, SLA | WEB | XL |
| P2-WEB-02 | Workflow monitoring: running instances, stuck tasks, SLA breaches | WEB | L |
| P2-WEB-03 | Leave type / group / year admin | WEB | L |
| P2-WEB-04 | Holiday calendar admin (multi-country, location-scoped, bulk import) | WEB | L |
| P2-WEB-05 | Entitlement rule builder | WEB | XL |
| P2-WEB-06 | Employee entitlement view + manual adjustment (with mandatory reason) | WEB | L |
| P2-WEB-07 | Leave oversight: all applications, filters, bulk actions, balance report | WEB | L |
| P2-WEB-08 | Shift definition admin | WEB | L |
| P2-WEB-09 | **Attendance policy admin** — the location-capture setting lives here | WEB | M |
| P2-WEB-10 | Attendance oversight: anomalies queue, bulk regularise, exception report | WEB | L |
| P2-WEB-11 | Attendance recompute trigger (with scope and confirmation) | WEB | M |
| P2-WEB-12 | Announcement composer: rich text, audience targeting, scheduling | WEB | L |

### QA

| ID | Task | Owner | Size |
|---|---|---|---|
| P2-QA-01 | **Workflow state-machine exhaustive test suite** — every transition, every resolver, every mode | QA | XL |
| P2-QA-02 | Concurrent approval test (two approvers act simultaneously → one wins, one gets 409) | QA | M |
| P2-QA-03 | Delegation + impersonation audit tests | QA | M |
| P2-QA-04 | **Accrual test matrix**: 5 accrual methods × join/exit mid-period × carry-forward × slabs | QA | XL |
| P2-QA-05 | Ledger reconciliation test — balance always equals sum of ledger entries | QA | M |
| P2-QA-06 | Working-day calculation matrix: holidays, weekends, half days, cross-month, cross-year | QA | L |
| P2-QA-07 | **Attendance computation matrix**: shift types × grace × rounding × OT × missing punches | QA | XL |
| P2-QA-08 | Recompute idempotency: recompute produces identical results | QA | M |
| P2-QA-09 | **Offline punch chaos**: airplane mode, app kill, clock change, duplicate sync, 7-day backlog | QA | XL |
| P2-QA-10 | Location policy matrix: 3 modes × GPS available/denied/timeout/mock × inside/outside geofence | QA | L |
| P2-QA-11 | Notification action token tests: single-use, expiry, tamper rejection | QA | M |
| P2-QA-12 | Beta support: crash monitoring, feedback triage, weekly bug review | QA | L |
| P2-QA-13 | Full regression suite for Phase 1 + 2 | QA | L |

---

## Deliverables

### Database tables (~35)
`workflow_type` · `workflow_definition` · `workflow_step` · `workflow_instance` · `workflow_task` · `workflow_history` · `workflow_delegation` · `leave_year` · `leave_group` · `leave_type` · `short_leave_type` · `day_type` · `calendar_group` · `holiday_calendar` · `leave_entitlement_rule` · `employee_leave_entitlement` · `leave_ledger` · `leave_application` · `leave_application_day` · `short_leave_application` · `leave_reason` · `shift` · `employee_shift_schedule` · `attendance_policy` · `employee_attendance_policy` · `raw_punch` · `attendance_session` · `daily_attendance` · `manual_attendance_request` · `announcement` · `announcement_read` · `company_event` · `event_rsvp`

### API endpoints
```
GET    /v1/workflow/inbox                    ← unified across all modules
POST   /v1/workflow/tasks/{id}/approve|reject|return
POST   /v1/workflow/tasks/bulk-action
POST   /v1/workflow/action                   ← signed token from notification
GET    /v1/workflow/instances/{id}
POST   /v1/workflow/instances/{id}/withdraw
GET/PUT /v1/workflow/delegations
GET    /v1/me/requests                       ← request tracker

GET    /v1/leave/types
GET    /v1/leave/balances
GET    /v1/leave/balances/{typeId}/ledger    ← the explainable statement
POST   /v1/leave/balance-projection
POST   /v1/leave/applications
GET    /v1/leave/applications
POST   /v1/leave/applications/{id}/cancel
GET    /v1/leave/calendar/team|company
GET    /v1/leave/holidays

POST   /v1/attendance/punch                  ← idempotent, accepts offline batch
GET    /v1/attendance/today
GET    /v1/attendance/calendar
GET    /v1/attendance/days/{date}            ← includes calculation trace
POST   /v1/attendance/manual-requests
GET    /v1/attendance/summary/self|team
GET    /v1/attendance/team/live

GET    /v1/announcements
POST   /v1/announcements/{id}/acknowledge
GET    /v1/events
```

### Screens
~30 new per platform.

---

## Exit criteria

| # | Criterion | Verification |
|---|---|---|
| 1 | **A full offline working day**: clock in, apply leave, approve a request — all queued, all reconciled correctly on reconnect | `P2-QA-09` + manual |
| 2 | **The punch always succeeds** — in all 3 location policy modes, with GPS denied, timed out, and mocked | `P2-QA-10` |
| 3 | Approve from the notification shade without opening the app, both platforms | Manual on physical devices |
| 4 | Leave balance is fully reconstructible from the ledger; every statement row explains itself | `P2-QA-05` + manual |
| 5 | Attendance day detail shows the complete trace from raw punches to final hours | Manual |
| 6 | `daily_attendance` recompute is idempotent | `P2-QA-08` |
| 7 | Accrual matrix passes for all 5 methods including mid-period join and exit | `P2-QA-04` |
| 8 | Workflow: multi-level, parallel, quorum, conditional-skip, delegation, escalation all working | `P2-QA-01` |
| 9 | Concurrent approval → exactly one succeeds | `P2-QA-02` |
| 10 | **Internal beta live** with ≥30 employees for ≥2 weeks | Usage analytics |
| 11 | Beta crash-free session rate ≥ 99.5% | Crash reporting |
| 12 | Clock-in tap → confirmed < 300 ms (location off), < 3 s p95 (location required) | CI perf gate |
| 13 | All Phase-1 perf and size budgets still met | CI gates |

---

## Demo script (end of week 18)

1. **Clock in, three ways** — Policy set to `OFF`: tap, confirmed in under 300 ms, no permission prompt. Switch policy to `REQUIRED`: tap, "Verified at Head Office". Now walk outside the geofence: tap, punch still recorded, flagged for review. Now turn GPS off entirely: **tap, punch still recorded**, marked location-unavailable. *Read out the PeoplesHR review about the "ooops" error blocking check-in.*
2. **Offline day** — Airplane mode. Clock in. Apply for 3 days' leave. Approve two pending requests. Show everything queued with badges. Force-kill the app. Reopen — still queued. Restore network. Watch everything drain and appear in the web admin.
3. **Apply leave** — Open the calendar: holidays greyed, two teammates already off shown inline, balance impact updating as dates are selected. Review card before submit. Submit → approval chain appears immediately.
4. **Explainability** — Open the leave balance statement: opening balance, monthly accruals, days taken, an adjustment with its reason, closing balance. Then open an attendance day: raw punches → paired sessions → grace applied → rounding applied → worked minutes, late minutes, OT minutes. *Nothing comparable exists in their product.*
5. **Approve from the notification** — Have someone submit a request live. Notification arrives. Tap Approve in the shade. Show the requester's phone updating. The approver's app was never opened.
6. **Bulk approve** — Manager with 12 pending items. Multi-select 8, one comment, one tap.
7. **Live team board** — Who's in, who's late, who's remote, who's off — updating live.
8. **Widgets** — Android home-screen widget clock-in. iOS Live Activity showing the running shift in the Dynamic Island.
9. **Beta metrics** — Show two weeks of real usage from your own staff: DAU, punches, leave applications, approvals, crash-free rate.

---

## Phase risks

| Risk | Trigger | Owner | Mitigation |
|---|---|---|---|
| **Workflow engine over-runs** and squeezes leave/attendance | Week 13 ends with the state machine incomplete | TL | Hard scope cut list prepared in advance: parallel branches and quorum mode move to Phase 3 if needed. Multi-level sequential + delegation are the non-negotiable minimum. |
| Accrual engine correctness | Test matrix failures in week 16 | BE1 | Build the test matrix *first*, from real policies at a real company. Treat it as the spec. |
| Attendance computation edge cases explode | Week 16 matrix < 90% passing | BE2 | Constrain Phase 2 to fixed shifts only. Rotating/split/night shifts move to Phase 5 with the roster module. |
| Offline punch clock manipulation | User changes device clock to fake a punch | BE2 | Record both device time and server receipt time; flag skew > 5 min; policy option to reject skewed punches |
| Beta users hit a data-correctness bug and lose trust internally | Any leave balance error | TL | Ledger model means balances are auditable and correctable. Run the first beta week in parallel with the existing system before cutting over. |
| Two platforms drift on the clock-in flow | Any divergence in behaviour | TL | The location flow is specified as a written decision table; both platforms implement the same table; `P2-QA-10` runs identically on both. |

---

## Not in Phase 2

- Rosters, shift adjustment, shift swap, mutual swap (Phase 5)
- Rotating / split / night shifts (Phase 5)
- Overtime request & claim workflow (Phase 5 — OT is *computed* in Phase 2, but not claimable)
- Timesheets (Phase 5)
- Bulk leave planner, leave plans, Excel upload (Phase 5)
- Payroll (Phase 3)
