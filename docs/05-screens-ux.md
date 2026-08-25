# Screens & UX

Native Android (Compose) + iOS (SwiftUI). Shared information architecture, platform-idiomatic execution.

---

## 1. Design principles

These are the rules that make the app feel different from PeoplesHR. Every screen decision traces back to one of them.

1. **Nothing waits on the network.** Every screen paints from the local store. Network updates arrive as a quiet refresh, never a full-screen spinner. Budget: any navigation → first paint **< 100 ms**.
2. **One thumb, one hand.** Primary actions live in the bottom third. Nothing critical in the top corners.
3. **Search before navigation.** A persistent search entry point that finds people, records, actions and pages. Beats "too many clicks".
4. **Show your working.** Leave balance, attendance calculation, and payslip lines are all tappable to reveal *how* the number was derived. This is the single most trust-building feature in HR software and it kills support tickets.
5. **Never block on a sensor.** GPS, camera, biometrics — all degrade gracefully. A failed sensor annotates the record; it never rejects the action.
6. **Role-adaptive, not role-switched.** One app. The home screen composes different cards for employee / manager / HR admin. No "switch to manager mode".
7. **Every list is filterable, sortable, bulk-actionable.** Managers live in lists.
8. **Empty and error states are designed**, not defaults. Every error names what happened and what to do next.
9. **Dark mode and dynamic type from day one**, not retrofitted.
10. **Motion is functional** — it communicates hierarchy and state change, never decoration.

---

## 2. Navigation structure

```
Bottom navigation (5 tabs, role-adaptive):

┌──────┬──────────┬──────────┬──────────┬──────────┐
│ Home │  Time    │ Approvals│  People  │   Me     │
└──────┴──────────┴──────────┴──────────┴──────────┘
   ▲        ▲          ▲          ▲          ▲
   │        │          │          │          │
 feed   attendance  workflow   directory   profile
 cards   + leave     inbox     + org chart  + pay
                    (badge)                 + docs
```

- **Approvals** tab is hidden for employees with no approval authority — replaced by **Requests** (their own submissions).
- **HR Admin** users get a 6th entry via the overflow / long-press on Home: **Manage** (admin console surfaces).
- Global search + assistant live in the top app bar, present on every tab.
- Deep links: `hrapp://leave/{id}`, `hrapp://approvals`, `hrapp://payslip/{periodId}`, `hrapp://employee/{id}`.

**Android:** Material 3 `NavigationBar`, predictive back, edge-to-edge, Material You dynamic colour opt-in (tenant branding wins if set).
**iOS:** `TabView` with `NavigationStack` per tab, native swipe-back, SF Symbols, Dynamic Island live activity for an in-progress shift.

---

## 3. Screen inventory

### 3.0 Onboarding & auth

| Screen | Content |
|---|---|
| **Splash / boot** | Logo, silent token refresh. If a valid biometric-sealed token exists, we skip straight to Home. Target: cold start → Home in **< 1.2 s**. |
| **Organisation** | Enter work email or org code. We resolve the tenant — no "service URL" field. Remembers the org afterwards. |
| **Sign in** | Password or SSO button (auto-detected from the email domain). |
| **MFA** | 6-digit TOTP / SMS, autofill from SMS. |
| **Biometric enrolment** | *Offered right after first successful login.* "Skip the password next time." One tap. Explains what's stored and where. |
| **Welcome tour** | 3 cards max, skippable, contextual to the user's role. |
| **Locked / re-auth** | Biometric prompt sheet. Password fallback link, not a password-first form. |

### 3.1 Home

A composed feed of cards, ordered by relevance and role. Pull to refresh, but the content is already there.

| Card | Shown to | Content |
|---|---|---|
| **Clock** | Everyone with attendance | Big primary action: Clock In / Clock Out. Live elapsed timer once clocked in. Today's shift, location status chip. |
| **Pending on you** | Approvers | Count + top 3 items, swipe to approve/reject inline |
| **My requests** | Everyone | Status of my in-flight applications |
| **Leave balance** | Everyone | Balance ring per leave type, "days since last leave" nudge |
| **Who's out today** | Everyone | Avatars of teammates on leave, expandable |
| **Payslip ready** | Everyone, on publish | Net pay hidden behind a tap + biometric |
| **Milestones** | Everyone | Birthdays & anniversaries this week, one-tap wish |
| **Announcements** | Everyone | Latest company news, pinned items first |
| **Team today** | Managers | Present / late / on leave / remote counts, tap to drill in |
| **Expiring soon** | Everyone | Visa, contract, certification expiries |
| **Payroll run status** | HR admin | Live phase indicator during a run |
| **Attrition risk** | HR admin | Top at-risk employees (P3) |
| **Nudges** | Everyone | Overdue approvals, unused leave, incomplete profile |

Cards are **server-configured** per tenant and per role — order and visibility come down with the sync, so we can tune the home screen without an app release.

### 3.2 Time tab

| Screen | Key elements |
|---|---|
| **Time hub** | Segmented: Attendance · Leave · Timesheet · Overtime |
| **Clock** | Large In/Out button, current shift, elapsed time, location chip (`Verified` / `Outside site` / `Location off`), offline badge when queued |
| **My attendance calendar** | Month grid, colour-coded day status, tap a day → detail |
| **Attendance day detail** | **Calculation trace**: raw punches → paired sessions → grace/rounding applied → worked/OT/late minutes. Every step visible. "Request correction" CTA. |
| **Manual in/out request** | Date, times, reason, remarks, attachment |
| **My shift schedule** | Week/month roster view, shift colours, swap CTA |
| **Shift swap** | Pick your date, pick a colleague, pick their date, submit |
| **Overtime** | Prior OT request, OT claim, OT history, cap-usage bar |
| **Leave home** | Balance cards per type, Apply CTA, upcoming approved leave with countdown |
| **Apply leave** | Type → date range with an inline calendar showing holidays, weekends, team conflicts and *your* balance impact live. Half-day toggles. Reason, remarks, attachment, covering person, contact-while-away. **Balance preview before submit.** |
| **Leave balance detail** | The `leave_ledger` rendered as a statement: opening + accruals − taken ± adjustments = balance. Every row explains itself. |
| **Leave history** | List with filters, cancel/withdraw actions |
| **Team leave calendar** | Month view of who's off, conflict highlighting |
| **Holiday calendar** | Location-aware, add-to-phone-calendar |
| **My leave plan** | Plan the year, submit for visibility |
| **Timesheet** | Week grid (day × project), quick-add row, copy last week, running total, submit |
| **Timesheet entry** | Project → activity → hours → note; duration picker, not a text field |

### 3.3 Approvals tab

| Screen | Key elements |
|---|---|
| **Inbox** | One unified list across **every** module. Filter by type, requester, age, SLA. Overdue items surfaced first. |
| **Bulk mode** | Multi-select → approve / reject with one shared comment |
| **Item detail** | Request summary, requester context (their balance, their team coverage, their recent history), full approval chain with timestamps, comment box, attachment viewer |
| **Swipe actions** | Right = approve, left = reject-with-comment |
| **Delegation** | Set an out-of-office delegate, date-bounded, per workflow type |
| **My requests** | Everything I've submitted, current step, who it's sitting with, withdraw action |
| **Approval history** | What I've actioned, searchable |

**Notification-level approval:** push notifications for approvals carry action buttons. Approving from the shade calls the API directly with a signed single-use token — the app never opens.

### 3.4 People tab

| Screen | Key elements |
|---|---|
| **Directory** | Search-first: name, designation, department, skill, location. Recent + favourites pinned. Works fully offline. |
| **Employee profile** | Photo, contact actions (call/message/email/Teams), designation, department, manager, direct reports, location, tenure. Permission-scoped tabs for HR/managers: personal, employment, attendance, leave, performance, documents. |
| **Org chart** | Interactive, pinch-zoom, tap to expand/collapse, "show my path to CEO", search-and-centre |
| **My team** | Direct + indirect reports, status chips (present/leave/late), quick actions |
| **Team attendance board** | Live: who's in, who's late, who's remote, who's off |
| **Team leave** | Calendar + list, coverage warnings |
| **Team performance** | Cycle progress, overdue assessments |

### 3.5 Me tab

| Screen | Key elements |
|---|---|
| **My profile** | Header with photo, code, designation. Sections: Personal, Contact, Address, Emergency contacts, Dependents, Education, Experience, Bank, Documents, Custom fields. **Editable fields route through the approval workflow** with a clear "pending approval" chip. |
| **Pay** | Payslip list by period. Tap → biometric gate → payslip. |
| **Payslip** | Earnings / deductions / employer contributions, net pay. **Every line is tappable → shows the formula and inputs that produced it.** YoY and MoM comparison chart. Download PDF, share (with a warning). `FLAG_SECURE` on Android. |
| **Total rewards** | Salary + benefits + employer cost in one annual view (P3) |
| **Loans** | Active loans, outstanding, repayment schedule, apply, early settlement |
| **Claims** | Submit expense claim with camera receipt capture + OCR prefill, claim history, reimbursement status |
| **Benefits** | Available benefits with eligibility status *and reason if ineligible*, apply, history |
| **My documents** | Personal vault: contract, payslips, certificates, letters. Request a letter (employment/salary/NOC) → generated → signed → delivered. |
| **My performance** | Current cycle status, goals with progress, self-assessment entry, feedback received, history |
| **My learning** | Enrolled courses, calendar, catalogue, certificates + expiry |
| **My lifecycle** | Visual career timeline: joined → confirmed → promoted → transferred |
| **Settings** | Language, theme (light/dark/system), notification preferences per event type + quiet hours, biometric toggle, registered devices, offline data usage, privacy, about, sign out |

### 3.6 Engagement surfaces

| Screen | Notes |
|---|---|
| **Announcements** | Feed, categories, pinned, acknowledgement flow for policy items |
| **Company calendar** | Events, RSVP |
| **Recognition** | Give kudos (pick colleague → badge → message), recognition wall |
| **Surveys** | Active surveys, one-question-per-screen for pulse, progress bar, anonymity clearly stated |
| **Suggestions** | Submit idea (optionally anonymous), track status, upvote others |
| **Grievance** | Sensitive-by-design: confidential framing, channel choice, anonymity option, status tracking, appeal |
| **Meals** | Daily menu, order, order history, balance |

### 3.7 HR Admin on mobile (our differentiator — feature 4 of §18)

Not the full web console, but the 80% an HR manager needs away from a desk:

| Screen | Notes |
|---|---|
| **Manage hub** | Entry point to admin surfaces |
| **Employee management** | Search, view, edit core fields, initiate movement, upload document |
| **Add employee** | Multi-step, resumable draft, works offline |
| **Attendance oversight** | Anomalies queue, bulk regularise, exception report |
| **Leave oversight** | Balance adjustments, entitlement issues, bulk actions |
| **Payroll monitor** | Live run phase, validation errors, anomaly review, approve run (with step-up auth) |
| **Reports** | Run saved reports, view results as charts/tables, export & share |
| **Announcement composer** | Write, target audience, schedule, publish |
| **Requisition & candidates** | Approve requisitions, review shortlists, submit interview scorecards |
| **Config lite** | Holiday calendar, leave types, shifts, announcement categories — the settings that change often |

### 3.8 Assistant

| Screen | Notes |
|---|---|
| **Assistant sheet** | Invoked from the app bar. Chat with suggested prompts. |
| **Answers** | Grounded in the tenant's own policy documents, always with a source citation |
| **Actions** | "Apply leave next Friday" → renders a **prefilled confirmation card**, never submits silently |
| **Permission-aware** | It cannot surface data the user isn't authorised to see |
| **Voice** | Push-to-talk input (P3) |

---

## 4. Key interaction flows

### Clock in — the most-used flow in the app
```
Home → tap Clock In
  ├─ policy.location_capture == OFF
  │     → punch immediately, no permission request         [< 300 ms]
  ├─ policy.location_capture == OPTIONAL
  │     → request fused location with a 3 s timeout
  │       success → attach coordinates + geofence status
  │       timeout/denied → punch anyway, mark location_unavailable
  └─ policy.location_capture == REQUIRED
        → request location with 8 s timeout
          inside geofence  → punch, chip "Verified at <Site>"
          outside geofence → depends on geofence_enforcement:
                             WARN  → punch + flag for review
                             BLOCK → offer "request manual entry" instead
          unavailable      → punch queued + flagged; NEVER a dead end

Offline at any point → write locally, show "Queued • will sync", outbox drains later.
```
> This directly fixes the most-complained-about behaviour in their app. **The punch always succeeds.** Location is evidence, not a gate.

### Apply leave
```
Leave home → Apply
  → pick type (shows balance + eligibility reason if blocked)
  → pick dates on a calendar that already shows holidays, weekly-offs,
    team members off, and your balance impact updating live
  → half-day toggles on first/last day
  → reason + remarks + attachment (required after N days, per policy)
  → covering person + contact-while-away
  → REVIEW CARD: "5 days · 3 working days deducted · balance after: 9.5"
  → Submit → optimistic local write → approval chain shown immediately
  → push when each approver acts
```

### Approve from a notification
```
Push arrives: "Nimal requested 3 days annual leave (12–14 Sep)"
  Actions: [Approve] [Reject] [View]
  Approve → signed one-time token → API → confirmed toast
  App never opens. Round trip < 2 s.
```

### View payslip
```
Me → Pay → tap period
  → biometric prompt (step-up, valid 5 min)
  → payslip renders from local encrypted cache
  → tap any line → "How this was calculated": formula, inputs, result
  → toggle: compare with last month / same month last year
```

---

## 5. Visual language

| Token | Direction |
|---|---|
| **Type** | One geometric-humanist sans for UI (Inter-class), tabular figures for all numeric columns — money and hours must align. iOS may use SF Pro. |
| **Scale** | 4 pt base grid, 8 pt rhythm. Type ramp: 32/24/20/17/15/13/11. |
| **Colour** | Neutral-first surface palette. One brand accent (tenant-overridable). Semantic set: success / warning / danger / info. Status colours are **never** the only signal — always paired with an icon or label (colour-blind safe). |
| **Elevation** | Flat surfaces with hairline dividers; elevation reserved for genuinely floating elements (FAB, sheets). |
| **Corners** | 12 pt cards, 8 pt controls, full-round chips. |
| **Iconography** | Material Symbols (Android) / SF Symbols (iOS) — platform-native, not a shared icon set. |
| **Data viz** | Sparklines and rings on cards; full charts only on detail screens. Never a pie chart for more than 3 slices. |
| **Density** | Comfortable by default; a compact toggle for managers who live in lists. |
| **Motion** | 200 ms standard, 120 ms for micro-feedback, spring for sheets. Respect Reduce Motion. |
| **Branding** | Tenant logo in the app bar, tenant accent colour, optional custom app icon per white-label build. |

---

## 6. States every screen must define

For each screen we ship, the spec includes all six:

1. **Loading** — skeleton matching final layout, not a spinner (only on genuine cold-empty)
2. **Loaded** — the happy path
3. **Empty** — illustration + one-line explanation + primary action
4. **Error** — what happened, why, what to do, retry action
5. **Offline** — persistent but unobtrusive banner; queued-action badges; disabled-with-reason for network-only actions
6. **No permission** — explain what access is needed and who to ask, never a blank screen

---

## 7. Accessibility requirements

- WCAG 2.2 AA contrast on all text and interactive elements
- Full TalkBack / VoiceOver labelling, including custom composables and charts (chart data available as a table)
- Dynamic type support up to 200%; layouts reflow, never truncate critical values
- Minimum touch target 48×48 dp / 44×44 pt
- No colour-only status encoding
- Respect Reduce Motion, Bold Text, Increase Contrast
- Full keyboard navigation on tablets and external keyboards
- RTL layout correctness for Arabic — mirrored, not just translated

---

## 8. Performance budgets (CI-enforced)

| Metric | Target |
|---|---|
| Cold start → Home interactive | < 1.2 s (mid-tier device: Pixel 6a / iPhone SE 3) |
| Warm start | < 400 ms |
| Tab switch | < 100 ms |
| Any cached screen first paint | < 100 ms |
| Clock-in tap → confirmed (location off) | < 300 ms |
| Clock-in tap → confirmed (location required) | < 3 s p95 |
| Scroll jank (dropped frames) | < 1% |
| APK size (Android, per-ABI split) | **< 25 MB** |
| IPA size (iOS) | **< 40 MB** |
| Memory, steady state | < 180 MB |
| Battery: background sync | < 2% / day |
| Offline DB size, 1 year of history | < 60 MB |

Reference points from the research: their Android build is **92.5 MB** and iOS **275.6 MB**, with "very slow" as a recurring review theme. These budgets are the measurable form of beating them.

---

## 9. Platform-specific opportunities

**Android**
- Home-screen widget: clock in/out + today's status
- Quick Settings tile for clock in/out
- Notification actions for approvals
- Work Profile / Android Enterprise support for managed deployments
- Predictive back, per-app language preference
- Wear OS companion: clock in/out from the wrist (P3)

**iOS**
- Lock Screen + Home Screen widgets (WidgetKit)
- **Live Activity / Dynamic Island** showing the running shift timer — a genuinely delightful attendance feature
- Siri Shortcuts / App Intents: "Hey Siri, clock in"
- Notification actions for approvals
- Handoff to the web console
- Focus filter integration (suppress work notifications outside hours)
- Apple Watch companion: clock in/out, approvals (P3)

---

## 10. Screen count estimate

| Area | Screens |
|---|---|
| Auth & onboarding | 7 |
| Home & assistant | 4 |
| Time & attendance | 14 |
| Leave | 10 |
| Approvals | 7 |
| People & directory | 7 |
| Profile, pay & documents | 16 |
| Performance & learning | 9 |
| Engagement | 8 |
| HR admin on mobile | 12 |
| Settings & system | 6 |
| **Total** | **~100 distinct screens** per platform |

That is the honest scope. It is why the roadmap in [06-roadmap.md](06-roadmap.md) is phased rather than big-bang.
