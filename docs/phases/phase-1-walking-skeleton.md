# Phase 1 — Walking Skeleton

**Weeks 5–10 · Milestone M1**

---

## Goal

A real employee installs the app, logs in with a fingerprint, and sees their own data: their profile, their colleagues, the org chart. Fully usable offline.

This is the first phase with something to show a customer. It proves the Phase-0 foundations carry real features, and it delivers the differentiator that fixes PeoplesHR's most-complained-about defect — **biometric login that actually works**.

---

## Entry criteria

- [ ] Phase 0 exit criteria all met
- [ ] KMP decision recorded
- [ ] Design system component library complete for the screens in this phase
- [ ] TestFlight and Play Internal Testing tracks configured

---

## Week-by-week

### Week 5 — Employee & org schema
| Who | Focus |
|---|---|
| `TL` | Effective-dating strategy, `ltree` hierarchy design, custom-fields engine design |
| `BE1` | `employee` table + all sub-entities (bank, documents, dependents, qualifications) |
| `BE2` | Org structure: `company`, `location`, `department`, `designation`, `cost_centre`, `salary_grade` |
| `AND` | Auth screens: org resolve, sign in, MFA, biometric enrolment |
| `IOS` | Same auth screens |
| `WEB` | Org structure CRUD screens |
| `QA` | Employee/org test fixtures, expanded isolation coverage |
| `DES` | Home screen card system; directory + profile designs |

### Week 6 — Hierarchy, directory, search
| Who | Focus |
|---|---|
| `TL` | Custom fields engine implementation review |
| `BE1` | `employee_hierarchy` ltree materialisation + maintenance triggers; reporting hierarchy API |
| `BE2` | OpenSearch indexing pipeline; directory search endpoint with permission scoping |
| `AND` | Home shell + card framework (server-configured card list) |
| `IOS` | Same |
| `WEB` | Employee CRUD: create, search, edit, view |
| `QA` | Search relevance tests; hierarchy correctness tests |
| `DES` | Org chart interaction design; settings screens |

### Week 7 — Custom fields & profile
| Who | Focus |
|---|---|
| `TL` | Phase 2 detailed planning (workflow engine design) |
| `BE1` | `field_definition` + JSONB storage + GIN indexes + validation |
| `BE2` | **Server-rendered form schema endpoint** — drives mobile forms without app releases |
| `AND` | Employee directory (offline, search-first) + employee profile |
| `IOS` | Same |
| `WEB` | Custom field designer UI |
| `QA` | Custom field validation matrix across all 8 field types |
| `DES` | Profile edit flows; pending-approval states |

### Week 8 — Org chart, my profile, SSO/MFA
| Who | Focus |
|---|---|
| `BE1` | `GET /v1/mobile/home` composite endpoint |
| `BE2` | SSO: SAML 2.0 + OIDC; MFA: TOTP + SMS |
| `AND` | Org chart (interactive, pinch-zoom, search-and-centre) |
| `IOS` | Same |
| `WEB` | Custom field designer; SSO configuration UI |
| `QA` | SSO integration tests against Azure AD + Google test tenants |
| `DES` | Announcement/milestone card designs (used Phase 2) |

### Week 9 — My profile, settings, notifications
| Who | Focus |
|---|---|
| `BE1` | Photo upload + processing; attachment handling |
| `BE2` | Notification delivery live (FCM + APNs), preferences, quiet hours |
| `AND` | My profile view + edit; settings (language, theme, notifications, devices, biometrics) |
| `IOS` | Same |
| `WEB` | User/device management, notification template admin |
| `QA` | Push delivery tests both platforms; notification preference matrix |
| `DES` | Phase 2 designs begin (leave, attendance) |

### Week 10 — Polish, accessibility, performance, release
| Who | Focus |
|---|---|
| `TL` | Phase 1 review; Phase 2 kickoff |
| `BE1` | Performance: query optimisation, N+1 elimination, index review |
| `BE2` | Load test: 10k employees, 1k concurrent users |
| `AND` | Accessibility pass, perf budget enforcement, dark mode, dynamic type, **TestFlight/Play internal release** |
| `IOS` | Same |
| `WEB` | Polish + bug fix |
| `QA` | Full regression, device farm matrix, accessibility audit |
| `DES` | Phase 2 designs continue |

---

## Task backlog

### Backend — employee & org

| ID | Task | Owner | Size |
|---|---|---|---|
| P1-BE-01 | `employee` table with personal/employment/workstation/contact sections | BE1 | L |
| P1-BE-02 | `employee_dependent`, `employee_emergency_contact`, `employee_nominee`, `employee_transport` | BE1 | M |
| P1-BE-03 | `employee_qualification`, `employee_work_experience`, `employee_language`, `employee_extracurricular` | BE1 | M |
| P1-BE-04 | `employee_bank_account` with column-level encryption for account numbers | BE1 | M |
| P1-BE-05 | `employee_document` (passport/visa/labour card) with expiry alerting | BE1 | M |
| P1-BE-06 | `employee_attachment` + S3 integration + virus scanning hook | BE1 | M |
| P1-BE-07 | `employee_contract`, `employee_covering`, `employee_membership` | BE1 | M |
| P1-BE-08 | `company`, `location` (with geo + geofence radius), `sub_location`, `job_location` | BE2 | M |
| P1-BE-09 | `department`, `cost_centre`, `designation`, `salary_grade`, `corporate_title` | BE2 | M |
| P1-BE-10 | `job_description`, `job_kra`, and the reference taxonomies (~30 lookup tables) | BE2 | L |
| P1-BE-11 | `geo_region` self-referencing hierarchy + seed data for target countries | BE2 | M |
| P1-BE-12 | `bank`, `bank_branch` + seed data | BE2 | S |
| P1-BE-13 | `employee_hierarchy` ltree + maintenance trigger on supervisor change | BE1 | L |
| P1-BE-14 | Reporting hierarchy API: ancestors, descendants, direct reports, path-to-root | BE1 | M |
| P1-BE-15 | Employee CRUD API with field-level permission enforcement | BE1 | L |
| P1-BE-16 | Effective-dated read (`?asOf=date`) for employee and org entities | TL | L |
| P1-BE-17 | Photo upload: resize, thumbnail generation, CDN delivery | BE1 | M |

### Backend — search, custom fields, composite

| ID | Task | Owner | Size |
|---|---|---|---|
| P1-BE-18 | OpenSearch index mapping for employee; indexing pipeline via Kafka | BE2 | L |
| P1-BE-19 | Directory search API: fuzzy name, designation, department, skill, location | BE2 | L |
| P1-BE-20 | Permission-scoped search results (never return employees the user can't see) | BE2 | M |
| P1-BE-21 | `field_definition` table + CRUD API | BE1 | M |
| P1-BE-22 | JSONB `custom_fields` storage + GIN index + query support | BE1 | M |
| P1-BE-23 | Custom field validation engine (all 8 types + required/regex/range rules) | BE1 | L |
| P1-BE-24 | **Form schema endpoint** — `GET /v1/forms/{entityType}` returns render instructions | BE1 | L |
| P1-BE-25 | `GET /v1/mobile/home` — cards, counts, and payloads in one round trip | BE1 | L |
| P1-BE-26 | Home card configuration per tenant + role | BE1 | M |
| P1-BE-27 | Sync scopes for directory, org, profile with per-role subscription rules | BE1 | M |

### Backend — auth extensions

| ID | Task | Owner | Size |
|---|---|---|---|
| P1-BE-28 | MFA: TOTP enrolment, verification, recovery codes | BE2 | L |
| P1-BE-29 | MFA: SMS channel with provider abstraction | BE2 | M |
| P1-BE-30 | SSO: SAML 2.0 service-provider implementation | BE2 | XL |
| P1-BE-31 | SSO: OIDC relying-party implementation | BE2 | L |
| P1-BE-32 | `sso_config` + domain-hint auto-detection at the org-resolve step | BE2 | M |
| P1-BE-33 | JIT user provisioning from SSO assertions with attribute mapping | BE2 | M |

### Backend — notifications

| ID | Task | Owner | Size |
|---|---|---|---|
| P1-BE-34 | `notification_template` + rendering per locale | BE2 | M |
| P1-BE-35 | FCM delivery with token management and cleanup on unregister | BE2 | M |
| P1-BE-36 | APNs delivery with token management | BE2 | M |
| P1-BE-37 | `notification_preference` + quiet hours + timezone resolution | BE2 | M |
| P1-BE-38 | `notification_delivery` tracking, retry with backoff, dead-letter | BE2 | M |
| P1-BE-39 | Deep-link payload convention (`hrapp://…`) | BE2 | S |
| P1-BE-40 | Document expiry alert job (visa, contract, certification) | BE2 | M |

### Android & iOS (mirrored — every task exists on both)

| ID | Task | Size |
|---|---|---|
| P1-{AND,IOS}-01 | Org-resolve screen (work email or org code → tenant) | M |
| P1-{AND,IOS}-02 | Sign-in screen with SSO auto-detection | M |
| P1-{AND,IOS}-03 | MFA entry screen with SMS autofill | M |
| P1-{AND,IOS}-04 | **Biometric enrolment screen** — offered right after first login | L |
| P1-{AND,IOS}-05 | Re-auth / locked screen (biometric-first, password as fallback link) | M |
| P1-{AND,IOS}-06 | Welcome tour (3 cards, role-contextual, skippable) | M |
| P1-{AND,IOS}-07 | Home shell + bottom navigation (role-adaptive tabs) | L |
| P1-{AND,IOS}-08 | Home card framework — server-driven card list with per-card renderers | XL |
| P1-{AND,IOS}-09 | Milestones card (birthdays, anniversaries) | M |
| P1-{AND,IOS}-10 | Expiring-documents card | M |
| P1-{AND,IOS}-11 | Directory screen: search-first, offline, recents, favourites | L |
| P1-{AND,IOS}-12 | Employee profile screen with permission-scoped tabs | L |
| P1-{AND,IOS}-13 | Contact actions (call, SMS, email, Teams deep link) | S |
| P1-{AND,IOS}-14 | **Org chart** — interactive, pinch-zoom, expand/collapse, search-and-centre | XL |
| P1-{AND,IOS}-15 | "My path to CEO" org chart mode | M |
| P1-{AND,IOS}-16 | My profile view (all sections) | L |
| P1-{AND,IOS}-17 | My profile edit with **dynamic form rendering from the schema endpoint** | XL |
| P1-{AND,IOS}-18 | Pending-approval chips on edited fields | M |
| P1-{AND,IOS}-19 | Photo capture/upload with crop | M |
| P1-{AND,IOS}-20 | Attachment upload + viewer (PDF, image) | M |
| P1-{AND,IOS}-21 | Settings: language, theme, notification prefs, quiet hours | L |
| P1-{AND,IOS}-22 | Settings: registered devices, revoke, biometric toggle | M |
| P1-{AND,IOS}-23 | Settings: offline data usage, clear cache, about, sign out | M |
| P1-{AND,IOS}-24 | Push notification handling + deep-link routing | L |
| P1-{AND,IOS}-25 | All six screen states implemented for every screen in this phase | L |
| P1-{AND,IOS}-26 | Accessibility pass: TalkBack/VoiceOver, dynamic type, contrast, focus order | L |
| P1-{AND,IOS}-27 | Dark mode verification across every screen | M |
| P1-{AND,IOS}-28 | Perf budget instrumentation + CI gate (cold start, tab switch, first paint) | L |
| P1-{AND,IOS}-29 | TestFlight / Play internal testing release pipeline | M |

**Platform-specific extras**

| ID | Task | Size |
|---|---|---|
| P1-AND-30 | Per-app language preference (Android 13+) | S |
| P1-AND-31 | Predictive back support | S |
| P1-AND-32 | Edge-to-edge + Material You dynamic colour (tenant branding wins) | M |
| P1-IOS-30 | String catalogs + locale switching | S |
| P1-IOS-31 | Handoff to web console | S |
| P1-IOS-32 | App-switcher snapshot blurring | S |

### Web admin

| ID | Task | Owner | Size |
|---|---|---|---|
| P1-WEB-01 | Org structure CRUD: company, location, department, cost centre | WEB | L |
| P1-WEB-02 | Work structures CRUD: designation, salary grade, corporate title | WEB | M |
| P1-WEB-03 | Reference data admin (the ~30 lookup tables, generated CRUD) | WEB | L |
| P1-WEB-04 | Employee list with advanced filters, saved views, bulk select | WEB | L |
| P1-WEB-05 | Employee create wizard (multi-step, all sections) | WEB | XL |
| P1-WEB-06 | Employee edit with section tabs and field-level permission enforcement | WEB | L |
| P1-WEB-07 | **Custom field designer** — drag to position, configure type/validation/permissions | WEB | XL |
| P1-WEB-08 | Home card configuration per role | WEB | M |
| P1-WEB-09 | SSO configuration UI | WEB | M |
| P1-WEB-10 | Notification template editor | WEB | M |
| P1-WEB-11 | Bulk employee import: upload, map columns, validate, preview, commit | WEB | XL |

### QA

| ID | Task | Owner | Size |
|---|---|---|---|
| P1-QA-01 | Employee/org API contract + integration tests | QA | L |
| P1-QA-02 | Hierarchy correctness tests (deep trees, cycles rejected, re-parenting) | QA | M |
| P1-QA-03 | Field-level permission matrix tests (hidden/masked/read/write × roles) | QA | L |
| P1-QA-04 | Custom field validation matrix (8 types × validation rules) | QA | L |
| P1-QA-05 | Search relevance + permission-scoping tests | QA | M |
| P1-QA-06 | SSO integration tests: Azure AD, Google, Okta test tenants | QA | L |
| P1-QA-07 | Push delivery tests both platforms incl. deep-link routing | QA | M |
| P1-QA-08 | **Offline scenario suite**: directory, profile, org chart all usable in airplane mode | QA | L |
| P1-QA-09 | Accessibility audit both platforms (automated + manual screen-reader pass) | QA | L |
| P1-QA-10 | Performance test suite wired into CI with hard budget gates | QA | L |
| P1-QA-11 | Load test: 10k employees, 1k concurrent, p95 latency targets | QA | L |
| P1-QA-12 | Device farm matrix: 8 Android devices (incl. Samsung/Xiaomi), 4 iPhones | QA | M |

---

## Deliverables

### Database tables (~55)
All employee tables · all org/EIM tables · `field_definition` · `sso_config` · `notification_template` · `notification` · `notification_preference` · `notification_delivery` · ~30 reference taxonomies

### API endpoints
```
GET    /v1/employees                     (search, filter, cursor paginate)
POST   /v1/employees
GET    /v1/employees/{id}
PATCH  /v1/employees/{id}
GET    /v1/employees/{id}/hierarchy
GET    /v1/employees/{id}/reports
GET    /v1/employees/{id}/documents
POST   /v1/employees/{id}/attachments
GET    /v1/directory/search
GET    /v1/org/chart
GET    /v1/org/companies|locations|departments|designations|...
GET    /v1/forms/{entityType}            ← server-rendered form schema
GET    /v1/field-definitions
POST   /v1/field-definitions
GET    /v1/mobile/home                   ← composite home payload
GET    /v1/me/profile
PATCH  /v1/me/profile
POST   /v1/me/photo
GET    /v1/notifications
PATCH  /v1/notifications/{id}/read
GET    /v1/notifications/preferences
PUT    /v1/notifications/preferences
POST   /v1/auth/mfa/enroll|verify
GET    /v1/auth/sso/{provider}/…
```

### Screens (both platforms, ~20 each)
Org resolve · Sign in · MFA · Biometric enrolment · Re-auth · Welcome tour · Home · Directory · Employee profile · Org chart · My profile · My profile edit · Photo capture · Attachment viewer · Settings (×3) · Notification centre · plus all six states each

---

## Exit criteria

| # | Criterion | Verification |
|---|---|---|
| 1 | **Biometric login with no password after enrolment**, both platforms, ≥5 Android OEMs | Manual on physical devices + device farm |
| 2 | Directory, profile and org chart fully usable in airplane mode | `P1-QA-08` |
| 3 | Cold start → Home interactive **< 1.5 s** on Pixel 6a and iPhone SE 3 | CI perf gate |
| 4 | Tab switch < 100 ms; any cached screen first paint < 100 ms | CI perf gate |
| 5 | A custom field added in the web admin appears on both mobile apps **with no app release** | Manual demo |
| 6 | Field-level permissions enforced — a role without salary access never receives the value in the API response | `P1-QA-03` |
| 7 | SSO login works against Azure AD | `P1-QA-06` |
| 8 | Push notification delivered and deep-links to the correct screen, both platforms | `P1-QA-07` |
| 9 | Accessibility: zero critical issues, full screen-reader traversal of every screen | `P1-QA-09` |
| 10 | 10k-employee tenant: directory search p95 < 300 ms | `P1-QA-11` |
| 11 | Builds distributed via TestFlight and Play Internal Testing | Observed |
| 12 | Android APK < 25 MB, iOS IPA < 40 MB | CI size gate |

---

## Demo script (end of week 10)

1. **First run** — Fresh install on a physical Android phone. Enter work email → tenant resolved automatically, no "service URL" field. Sign in. Prompted to enrol biometrics; accept.
2. **The fix** — Force-kill the app. Reopen → fingerprint → straight to Home. *No password.* Repeat on iPhone with Face ID. Contrast this explicitly with the PeoplesHR review quote.
3. **Home** — Show the card feed: milestones, expiring documents. Change the card configuration in the web admin, pull to refresh, cards reorder.
4. **Directory** — Search a colleague. Open their profile. Call/email actions. Now airplane mode — search again, still instant.
5. **Org chart** — Pinch-zoom, expand a branch, tap "my path to CEO". Still in airplane mode.
6. **Custom field** — In the web admin, add a "T-shirt size" dropdown to the employee entity. Pull to refresh on the phone. The field is there, editable, validating. **No app release.**
7. **Profile edit** — Change an emergency contact. Show the pending-approval chip.
8. **Permissions** — Log in as a non-HR user. Show that salary fields are absent from the API response, not just hidden in the UI (show the raw response in a proxy).
9. **Performance** — Run the startup trace live. Show the CI dashboard with budget history.
10. **Accessibility** — Turn on TalkBack. Navigate Home → Directory → Profile entirely by screen reader.

---

## Phase risks

| Risk | Trigger | Owner | Mitigation |
|---|---|---|---|
| Dynamic form rendering is harder than expected on two platforms | Week 7 ends without a working renderer | TL | Constrain v1 to the 8 field types only; no conditional visibility or cross-field validation until Phase 4 |
| Org chart performance with 10k employees | Frame drops on expand | AND/IOS | Lazy subtree loading, viewport culling, cap default depth at 3 with explicit expand |
| SSO integration eats more time than budgeted | Week 8 not complete | BE2 | SAML is the XL risk. If it slips, ship OIDC only in Phase 1 and move SAML to Phase 2. |
| Reference taxonomy CRUD (~30 tables) is tedious and slips | Week 6 | WEB | Generate the CRUD screens from metadata rather than hand-writing 30 screens |
| Photo/attachment storage costs or virus-scanning latency | Upload p95 > 3 s | BE1 | Async scanning; optimistic display with a "scanning" state |
| Perf budgets fail late and force rework | First red gate in week 10 | QA | Gates live from week 5, so violations surface the day they're introduced |

---

## Not in Phase 1

- Any approval workflow (Phase 2 — profile edits queue but aren't routed yet; they're auto-approved with an audit entry until the engine exists)
- Leave, attendance, payroll
- Any analytics or reporting
- Assistant / search-everything
