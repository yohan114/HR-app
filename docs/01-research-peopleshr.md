# PeoplesHR — Research Dossier

**Researched:** 22 August 2026
**Target:** [peopleshr.com](https://peopleshr.com/) — HRIS platform by hSenid Business Solutions / hSenid Software (Singapore) Pte. Ltd.
**Purpose:** Establish the complete feature surface of PeoplesHR (web + mobile) so we can design a superior native HR mobile application.

---

## 0. Method, and what I did *not* do

You asked me to "download the mobile app and get all data." Here is exactly what happened, so you can judge the evidence:

**What I did NOT do:**
- I did **not** download, install, run, or decompile the PeoplesHR APK/IPA. There is no Android emulator or device in this environment to run it on, and reverse-engineering a commercial vendor's binary to extract private API contracts is a licensing/ToS problem I'd need your explicit authorisation and a legitimate basis for.
- Consequence: I do **not** have their private API request/response schemas, their exact screen pixel layouts, or their internal DB schema.

**What I did instead — and it turned out to be better:**
PeoplesHR publishes a **complete public documentation portal** at `help.peopleshr.com` that documents *every single screen* in the product, module by module, plus a **public API Library**. That gave a far more complete and reliable functional inventory than screenshot-scraping an APK ever would. Sources used:

| Source | What it gave |
|---|---|
| `help.peopleshr.com` documentation tree | The complete screen-by-screen inventory of ~40 modules — this is the backbone of Section 4 |
| `help.peopleshr.com` API Library | Their public REST API module/endpoint groupings (Section 5) |
| `peopleshr.com/products/*` | Marketing feature names, AI positioning, module taglines |
| Google Play `com.peopleshr.hsenid` | Mobile app scope, version, size, ratings, changelog |
| Apple App Store `id1450474794` | iOS scope, size, minimum OS, privacy declaration |
| G2 / SoftwareAdvice / SoftwareSuggest / store reviews | Real user complaints → our differentiation opportunities (Section 7) |

**Confidence levels used below:** `[DOC]` = from official help portal, high confidence. `[MKT]` = from marketing pages, feature names are real but descriptions are promotional. `[STORE]` = from app store listing. `[REV]` = from user reviews, anecdotal.

---

## 1. Vendor & product snapshot

| Attribute | Value |
|---|---|
| Product | PeoplesHR (HRIS / HCM suite) |
| Vendor | hSenid Business Solutions; mobile published by hSenid Software (Singapore) Pte. Ltd. |
| Primary markets | Sri Lanka, UAE, Philippines, Indonesia, Bangladesh; claims **40+ countries** |
| Compliance certs | ISO 9001, ISO 27001 (UKAS), CSA STAR Level One |
| Deployment | Cloud (SaaS), multi-tenant; also on Microsoft Marketplace |
| Web product version | v9 ("v9-base" in help portal) |
| AI brand | **Lexi** — Smart Navigator, Super-Agent, Insights |

### Mobile app facts `[STORE]`

| | Android | iOS |
|---|---|---|
| Package / ID | `com.peopleshr.hsenid` | `id1450474794` |
| Latest version | 2.043.011 (18 Jul 2026) | 2.43.17 (18 Jul 2026) |
| Size | 92.5 MB | 275.6 MB |
| Min OS | Android 6.0+ | iOS 13.0+ |
| Rating | ~4.13★ (≈3,400 ratings) | 4.6★ (407 ratings) |
| Publisher | hSenid Software (Singapore) Pte. Ltd. | PeoplesHR Pty Limited |
| First released | March 2019 | — |
| Recent downloads | ~7.4k / 30 days | — |
| Privacy declaration | — | "The developer does not collect any data from this app" |
| Release cadence | Roughly monthly | Roughly monthly |

**Sibling apps:** `com.peopleshrgou.hsenid` (GoU HCM — white-label build), `com.hbs.mHRMv85` (legacy PeoplesHR V8.5, last updated 2020).

**Read on size:** 92.5 MB Android / 275.6 MB iOS for what is fundamentally a forms-and-lists business app is very large. Strong signal of a heavy cross-platform runtime and/or bundled assets. This is a direct opportunity for us — see §7.

---

## 2. Product module map `[MKT]`

PeoplesHR markets six core pillars plus three add-ons:

| # | Module | Tagline | Covers |
|---|---|---|---|
| 1 | **HR** (Core HR) | "The Foundation That Actually Thinks" | Employee master, lifecycle, org chart, reporting, self-service, kiosk, chatbot |
| 2 | **Pay** (Payroll) | "Globally Compliant Without the Complications" | Payroll engine, tax, statutory, multi-currency, payslips, loans, GL, bank files |
| 3 | **Time** | "Beyond Clock Watching" | Attendance capture, shifts, rosters, overtime, leave/absence, timesheets |
| 4 | **Talent** | "Hiring Smarter, Not Harder" | Requisition, recruitment, CV pool, onboarding, workforce planning |
| 5 | **Engagement** | "Engagement Beyond Annual Surveys" | Surveys, suggestions, grievances, chatbot, meals/canteen, kiosk |
| 6 | **Insight** (Analytics) | "Intelligence That Drives Action" | Dashboards, predictive turnover, KPI monitoring |
| + | **Compensation & Benefits** | "Total Rewards That Actually Reward" | Benefits, allowances, loan schemes, reimbursements |
| + | **People Management** | "Performance That Performs" | Goals, competencies, appraisals, MRA/360 |
| + | **Mobile App** | "Work Happens Everywhere. HR Should Too" | The ESS/MSS mobile client |

Add-on solutions: PeoplesHR Tracking, PeoplesHR Academy, Payroll Outsourcing.

Industry verticals marketed: Banking & Finance, Hospitality, Manufacturing, Professional & Business Services, Retail, IT & ITES, Transportation & Logistics, Healthcare.

---

## 3. The AI layer — "Lexi" `[MKT]`

Worth calling out because it's their headline differentiator and we need a credible answer to it.

| Feature | What it claims to do |
|---|---|
| **Lexi Smart Navigator** | Single smart search bar that finds any page/record across the system |
| **Lexi Super-Agent** | Conversational agent that *executes* tasks ("Apply for vacation next Friday"), not just answers |
| **Lexi Insights** | Predictive workforce intelligence, real-time decision support |
| Chatbot Training Console | Admin console to train/improve the model; usage analytics |
| Voice commands | Hands-free task execution, NLP, multilingual |
| Data widgets | Lexi surfaces structured widgets for Recruitment, Employee Info, Performance Assessment, Employee Life Cycle, Workflow, Benefits, Training |
| Workflow Assist | Guided approvals through chat |
| GPT-powered CV parsing | Structured extraction + ranking candidates by success likelihood |
| Video intelligence | Analyses candidate interview communication patterns |
| Turnover prediction | Per-employee attrition probability with business-unit breakdown |
| AI goal suggestions | Role-specific goal recommendations in performance reviews |

---

## 4. Complete functional inventory `[DOC]`

This is the core asset from this research: the full screen inventory extracted from the help portal. **Every line below is a real screen in the shipping product.** This is what "all features" actually means.

### 4.1 Absence Management
- **Configure Leave Rules:** Module Configuration, Script Editor
- **Definitions:** Leave Year, Leave Group, Leave Type, Short Leave Type
- **Calendar Definition:** Day Type, Calendar Group, Holiday Calendar
- **Annual Entitlements:** Leave Types & Group, Short Leave Types & Group, Employee Leave Entitlements, Leave Group Entitlement
- **Operational:** Update Leave Balance, Reason For Leave, Bulk Leave Planner, Upload Employee Leave Amounts, Earn Leave Management, Leave Holiday Adjustments
- **Application (employee):** Leave, My Leave Chart, Short Leave, Subordinate Bulk Leave, My Holiday Calendar, Bulk Leave, Leave History & Cancellation, Team Leave Management, Company Leave Management, My Leave Plan, Upload Bulk Leave
- **Approval:** Leave Approval, Short Leave Approval, Subordinate Entitlement Approval, SSHR My Workflow Items, Landing Page Widget
- **Management views:** View Company Leave Details, View Subordinates Leave Details, Leave Management (All Employees / Indirect Subordinates / All Eligible / My Eligible / Team Leave), Subordinate Leave Chart, Company Leave Chart, Company Leave Entry, Subordinate Leave Summary, Supervisor Dashboard, Self-Dashboard, Leave Plan (All / Subordinate)

### 4.2 Time & Attendance
- **System Configurations:** System Parameters, Rounding Information, Overtime Information, Grace Period Information, Download Configurations, Reason Information, Define Maximum OT Cap Code
- **Roster Administration:** Roster Group Information, Roster Information, Resource Definition
- **Shift Administration:** Shift Information
- **Attendance Operations:** Roster Employees, Schedule Roster, Prior Overtime Application, Data Process, Attendance Summary, Shift Adjustment, Manual In & Out, Time Sheet, Attendance Approval, Resource Employee
- **Other:** Schedule Resource Employee, Mutual Shift Adjustment
- **Supervisory:** My Team Attendance Summary
- **Self-service:** My Attendance, My Manual In & Out, Pending Time Sheets

Capture methods `[MKT]`: biometric devices, GPS-verified mobile check-in, kiosk, browser logging, RFID.

### 4.3 Payroll
- **Dashboards:** Anomaly Dashboard, Pre-Process Validation Dashboard
- **Administrative:**
  - Pay Group Configurations
  - Process Details: Define Pay Processes, Assign Payroll Actions to Processes
  - Company & Bank: Company Payroll Details, Define Banks, Define Branches, Pay Item Bank Details
  - Payroll Configurations: User Defined Fields, System Parameters, Currency Rates, Pay Warning Activation, Pay Anomaly Configuration
  - Pay Items: Define Pay Items, Pay Item Uploading Configuration, Avoid Negative Salary, Pay Group Specific Pay Item Settings
  - Salary Grade Details, Language Preferences
  - Tax: Define Taxes, Tax Adjustments, Upload Tax Adjustments, Tax Exemption, Entitle Taxes
  - User Control Panel: Control Pay Items for Users, Allocate Users to Processes
  - UDF Uploader (Configure + Excel Uploader), Loan Stop Configuration
  - Schema Details: Schema Configuration, Schema Information
- **Operational:**
  - Add Employees to Payroll
  - Assign Pay Items (Employees↔Pay Items, by Classification, Upload, Bulk Upload)
  - Salary Amendment (Process, Cancellation, Upload, Salary Grade Amendment)
  - Salary Revision Process, Payroll Validations
  - **Run Process:** Salary, Other Payments, Final Payment
  - **GL:** GL Navigator (Add/Reconfigure), GL Process, GL Rollback
  - **Bank Diskette:** Navigator (Add/Reconfigure), Process, Rollback, Password Management, Bank Diskette Security
  - Manage Process Period, Key Generator, Loan Uploader, Report Navigator
- **Country packs:** Payroll–Philippines (Leave Conversion, Pay Item Schedule, Premium Rate Definition, 13th Month Definition, Pag-Ibig, PhilHealth, tax annualisation, de-minimis/taxable/non-taxable leave encashment), Payroll–Indonesia, Payroll–Sri Lanka (Tax Annualization)
- **Payroll Simulator:** Budget Simulation for Increment / New Joiners / Employee Exits / Cost of Bonus, plus dashboard

### 4.4 Employee Information (Core HR master data)
Add New Employee (Personal, Employment, Workstation, Contact, Other Details) · Search & Edit · Check Availability · Navigate Records · View Previous Details · Attach Files · Profile Picture · **Census Information** (Dependents, Emergency Contacts, Transport, Nominees) · Educational & Professional Qualifications · Attended Trainings · Work Experience · Memberships & Bargaining Units · Bank Details · Credit Card Details · Passport & Other Articles · Attachments · Cash Benefits · Non-cash Benefits · **Employee Profile** (Life Cycle, Time & Attendance, Leave, Performance, Training) · Contract Extension · Covering Details · Languages · Extra Curricular Activities · Reporting Hierarchy · Change Supervisor · Job Specification · View-Only mode · Basic Salary · Labour Card Details

Country variants exist for **Philippines** and **Indonesia**.

### 4.5 EIM — Enterprise/organisation master data
- Company Profile: Location, Company Definition, Company Hierarchy, Cost Centre, Sub Location, Job Location, Setup Configuration
- Work Structures: Salary Grade, Corporate Title, Designation
- Job Profile: JD Category, JD Type, JD Key Result Area, Job Description, Industry Type
- Qualification Info: Qualification Type, Rating Method, Classification, Qualification, Property, Subject, Language
- Membership Info: Type, Details, Title, Bargaining Unit
- Benefits Info: Cash Benefit, Non-cash Benefit Category, Non-cash Benefit, assignment to Salary Grade, Benefit Clearance Head
- Nexus Info: Employee Category, Statutory Classification, Function, Functional Roles, Classification, Employee Group, Employment Type, Employee Title, Gender Type, Marital Status, Blood Group, Attachment Type, Currency Type
- Census Info: Route Information, Dwelling Type, Station Information, Statutory Items, Relationships
- Nationality & Religion: Nationality, Religion, Race
- Geographical: Country, Province, District, Electorate, DS Division, GN Division
- Extra Curricular Activity Category/Type
- Dynamic Fields, Bulk Image Uploader, WPS Company Master (UAE Wage Protection System), Search Employees, Enable Additional Details

### 4.6 Employee Life Cycle
Administration: Movement Group, Movement Type, Define Reason, Eligibility Configuration, Application, Application Shortlisting, Application Approval, Application History, Update Effective Date, Bulk Application, Application Rollback, Excel Uploader. Employee-facing: **Movement Timeline**.

### 4.7 Performance Management
- Administration: Rating Method, Proficiency Level, Competency Group, Competency Area, Competency, Competency Collection, Proficiency Profile, Goal Group
- **MRA (Multi-Rater / 360):** Rater Group, Define Questions, Assign Questionnaire
- Create Evaluation: Evaluation Information, Create Evaluation, Build MRA Questionnaire, **Model Bell Curve**, Goal Group Configuration, Upload Assessment Details
- Participants: Assessees, Assessors, 2nd Level Assessors/Reviewers, MRA Assessors, Change/Assign Participants
- Regular Observation: Critical Incident Journal + Approval
- Employee-facing: self-assessment, goals, assessment history

### 4.8 Recruitment
Requisition · Process Vacancy · Advertisement · Employment Type · Common Info · **Recruitment Activities:** Filter Candidates, Requisitions Summary, Offline Application, Application, Apply for Vacancy, **Job Portal**, Recruitment Widget, CV Pool, PeoplesHR Application

### 4.9 Onboarding
Admin: Define Stages, Define Events, Define Onboarding Actions, Exclude Vacancies, Define Onboarding Profile. Application: Manage Candidate Information, Candidate Information Upload, Assign Action to Onboarding Profile, Manage Candidate Onboarding Profile, Candidate Onboarding Progress, Individual Onboarding Progress. Approval: Task Approval.

### 4.10 Offboarding
Definitions: Define Reason, Interviewer Creation, Define Exit Types, Define Question Groups, Exit Question Templates, Clearance Admin Mapping, Assign Benefit Clearance Head, Define Work Handover Items, Work Handover Item Template. Process (Employee + Admin): Exit Notice, Exit Interview Questionnaire, Exit Reversal, Exit Interview Employee List, Employee Exit Process Administration, Exit Questionnaire Employee List, Exit Interview with Employee. Workflow: Exit Clearance Process.

### 4.11 Training & Development
Create Course · Enrollment · Resource Person · Trainee Evaluation · Training Attendance · Training Calendar · Training Provider · Training Schedule

### 4.12 Benefits
Admin: Define Benefit Types, Benefit Localization, Eligibility Criteria, Master Data, All Employee Benefits History. Application: Apply for Benefit, Benefit History (view/cancel), Excel Uploader, Self-Dashboard. Impersonate: All Employees, Apply Benefit–Team. Approval: For Your Approval.

### 4.13 Loan
Configuration: Checklist, Loan Entitlement, Loan Type, Other Information, Workflow Configuration. Administration: Apply Bulk Loan, Loan Settlement, Bulk Loan Settlement, Bulk Loan History. Application: Apply Loan, Loan History & Status.

### 4.14 Grievance Handling
Admin: Define Groups for Grounds, Grounds for Grievances, Communication Channels, Grievance Template. Recording: My / Team / HOD / Admin Grievance Application, Handle Grievances, Review Appeal, Grievance History, Appeal Grievance Application.

### 4.15 Disciplinary Management
Incident Reporting · Manage Incident · **Corrective Action Process:** Background Check, Charge Sheet, Oral Warning, Warning Letter, Show Cause Letter, Domestic Inquiry, Court Case · View Incidents · Appeal Management · Appeal Impersonate · Incident Journal · Incident Type · Incident Sub Type · Corrective Action

### 4.16 Workflow (cross-cutting engine)
Configuration: Workflow Types, Workflow Groups, Workflow Approval Person, Workflow Configuration. Application: Impersonate, **For Your Approval**, Workflow Summary, Workflow Delegation, Workflow Application History. Self-service widgets: My Notifications (Workflow History), My Workflow Items, Pending Time Sheets.

> This is the single most architecturally important module: **every** other module routes its approvals through it.

### 4.17 Meals / Canteen
Master: Manage Events, Food Categories, Food Items, Item Pricing, Functional Keys, Location & Canteen Mapping. Transaction: Daily Menu, Issue Meal, Order Meal. Reports + Advertisements. Configurations: Module, Kiosk, Shift Event.

### 4.18 Document Management
Define Tags · Folder Creation · File Creation · SharePoint Features · Folder Features · File Features · Archive · Trash · Workflow–For Your Approval

### 4.19 Digital Signature
Digital Signature Settings · Manage Document Template · Summary

### 4.20 Platform / configuration modules
| Module | Purpose |
|---|---|
| **Dynamic Data Structure** | Add custom fields without IT — Text, Numeric, Dropdown, Date, Radio, Checkbox, Attachment, Employee Search. Plus Process Lock Definition + Data Localization |
| **Eligibility Configurator** | Group/Template/Parameter config; Create Parameter, Build Expression |
| **Formula Builder** | Query Builder, Advance Query Builder, Formula Builder; classes `clsformula`, `clsemployee`, `clsSaveOutput` |
| **Enterprise Security Manager** | Data Access Points, Configure DB Objects, Table-Base Security, Table Encryption, Menu Activator, Templates, Capability Groups, Data Filters, Filter Rules, Security Groups, Single/Bulk User Accounts, Security Administrators, Password Configuration, Active Login Health |
| **Audit Manager** | Field-Level Audit, Define Aliases, Enable Audit, View Audit |
| **Job Scheduler** | Frequency setup; dashboards for Email Alerts, Jobs, DB Activities, SMS; Test Mail Config |
| **Label Configurator** | Relabel any UI string (white-labelling / localisation) |
| **On-demand Reporting** | System reports, data filtration, report builder, My Reports |
| **Data Import** | Database Wizard, Import Excel, Generate Excel, Table Profiles |
| **Organizational Chart** | Hierarchy Configuration, Organization Hierarchy, Company Hierarchy, Reporting Hierarchy views |
| **Extension Manager** | Form Extension, Dynamic Summary Page, 3rd-party integrations |
| **Common Configurator / Common Components** | Shared config; Search Employee component |
| **eHRM Framework** | Login, Navigation Bar, Login Details Popup, Logout |
| **eHRM Mini Widgets** | Milestones, Years of Service, Age Demographics, Status, Work Related |
| **Dashboard** | Service Consumption Dashboard |

---

## 5. Public API surface `[DOC]`

Their published API Library is grouped as:

| API module | Documented endpoint groups |
|---|---|
| Token Issuing & Refreshing | (OAuth2-style token endpoint — token-based auth) |
| Common API | shared lookups |
| Employee Information Manager | Census, Company Hierarchy, Designation, Employee, Employee-bank, Employee-qualification, Passport, Reporting-hierarchy, User |
| Benefit Management | benefit CRUD |
| Performance API | Assessment Summary, Competency Library, Goals, KPI |
| Training & Development | Create Course, Enrollment, Resource Person, Trainee Evaluation, Training Attendance, Training Calendar, Training Provider, Training Schedule |
| Recruitment | Advertisement, Common Info, Employment Type, Process Vacancy, Requisition |
| Onboarding | Actions, Assign Action to Onboarding Profile, Events, Exclude Vacancies, Manage Candidate, Onboarding Profile, Onboarding Progress, Stages |
| Offboarding | Reasons |

**Notable gaps in their public API:** no public Payroll, Attendance, Leave, Loan, Workflow, or Document APIs. Their integration story is weaker than their product. **That's an opening for us** — a complete, documented, public API is a genuine competitive feature.

**Integrations they ship:** Microsoft Teams app (company news, check-in/checkout, workflow approvals, employee search, service URL retrieval), SharePoint (document management), ERP integrations, biometric hardware, bank file formats, government portals.

---

## 6. What the *mobile app* actually does today `[STORE]` `[MKT]`

The mobile app is deliberately a **subset** — ESS (employee self-service) + MSS (manager self-service) only, not admin.

**Employee:**
- Leave: apply, view balance/entitlement in real time, leave history & cancellation, see which peers are on leave, "days since last vacation" counter, countdown to approved vacation, My Leave Chart, holiday calendar
- Attendance: tap-to-clock-in/out, geo-tagged check-in with GPS verification, manual in/out
- Payslips: view/retrieve
- Profile: update personal details, bank info, emergency contacts (with supervisor approval workflow)
- Company: employee directory/search, company info, company news/updates
- Milestones: birthday & work-anniversary reminders
- Performance: goals, reviews, real-time feedback, AI-generated goal suggestions
- Requests: request tracker across modules
- Benefits, loans (apply + history)

**Manager:**
- Approvals inbox ("For Your Approval") — leave, timesheets, cross-module
- Team availability / leave chart
- Team performance tracking
- Workforce analytics (permission-scoped)

**Platform:**
- Biometric authentication (fingerprint + face)
- Push notifications / intelligent alerts
- Theme customisation (multiple colour schemes)
- Offline capabilities for "critical functions"
- Multilingual (English, Sinhala, Tamil at minimum)
- Voice commands + conversational AI chat
- Encrypted communications

---

## 7. Weaknesses users actually report → our differentiation `[REV]`

This section is the most valuable part of the research. It tells us where to win.

| # | Reported problem | Evidence | Our counter-move |
|---|---|---|---|
| 1 | **App is slow** — "Very slow"; system slow at peak usage | Play Store, G2 | Native Kotlin/Swift + offline-first local DB. Every screen renders from local cache instantly, syncs in background. Target: cold start < 1.2s, any screen < 100ms. |
| 2 | **Biometric login is broken** — "fingerprint login is enabled, but username and password are still required… like no one tested this app" | Play Store | Real biometric-gated token vault: refresh token in Keystore/Secure Enclave, unlocked by BiometricPrompt / Face ID. Password only on first enrolment or after 30d. |
| 3 | **Forced GPS on check-in** — location mandatory even when the employer doesn't require it; "ooops" error blocks check-in entirely | Play Store | Location capture is a **per-policy, server-driven flag**. If policy says not required, we never request the permission. If GPS fails, punch is still accepted and queued with a `location_unavailable` reason — never block the punch. |
| 4 | **No admin parity on mobile** — HR managers can't do high-level config on the go | G2 | Full HR-admin capability set on mobile (approvals, employee edits, payroll run monitoring, report viewing, config for the common 80%). |
| 5 | **Steep learning curve** on report customisation | G2 | Guided report builder with templates + natural-language query, not a raw field picker. |
| 6 | **Too many clicks** for granular admin settings | G2 | Task-oriented flows, bulk actions, search-first navigation, command palette. |
| 7 | **"Limited features", "limited customization", "complex system usage"** | G2 review tags | Config-as-data (custom fields, workflows, formulas) exposed through a genuinely usable UI. |
| 8 | **Huge binary** (92MB Android / 276MB iOS) | Store | Native + on-demand resources. Target < 25 MB Android, < 40 MB iOS. |
| 9 | **Weak public API** (no payroll/attendance/leave endpoints) | API Library | Complete, versioned, OpenAPI-documented public API + webhooks from day one. |
| 10 | Location pickup errors persist across updates | Play Store | Fused location + last-known fallback + configurable accuracy threshold + mock-location detection that warns rather than blocks. |

---

## 8. Legal / IP note

Read this once, then we move on.

- **Features and functionality are not copyrightable.** Building an HR app with leave, attendance, payroll, and approvals is entirely legitimate — dozens of vendors do exactly that.
- **What we must not copy:** their source code, their exact screen layouts and visual design, their icons/illustrations/logos, their marketing copy verbatim, the "PeoplesHR" and "Lexi" names, and any private API contracts obtained by decompilation.
- **What we will do:** use the functional inventory above as a *requirements specification*, and design our own information architecture, visual language, and API. Where I quote their feature names in this dossier it is for research traceability; product-facing copy will be ours.

---

## 9. Sources

- [PeoplesHR home](https://peopleshr.com/)
- [Products index](https://peopleshr.com/products/)
- [Mobile App](https://peopleshr.com/products/mobile-app/) · [Core HR](https://peopleshr.com/products/core-hr/) · [Time & Attendance](https://peopleshr.com/products/time-and-attendance/) · [Payroll](https://peopleshr.com/products/payroll-system/) · [Talent Acquisition](https://peopleshr.com/products/talent-acquisition/) · [People Engagement](https://peopleshr.com/products/people-engagement/) · [People Analytics](https://peopleshr.com/products/people-analytics/) · [Compensation & Benefits](https://peopleshr.com/products/compensation-and-benefits/)
- [Help Portal — master index (v9)](https://help.peopleshr.com/index.php/doc/v9-base/)
- [Help Portal — API Library](https://help.peopleshr.com/index.php/documentation/benefit-management/)
- [Help Portal — Employee Information](https://help.peopleshr.com/index.php/documentation/employee-information-manager/) · [Payroll](https://help.peopleshr.com/index.php/documentation/payroll/) · [Absence](https://help.peopleshr.com/index.php/documentation/absence-management/) · [Time & Attendance: Roster Information](https://help.peopleshr.com/index.php/documentation/time-attendance/roster-administration/roster-information/) · [Recruitment: CV Pool](https://help.peopleshr.com/index.php/documentation/recruitment-2/recruitment-activities/cv-pool/) · [Training & Development](https://help.peopleshr.com/index.php/documentation/training-development/) · [Microsoft Teams app](https://help.peopleshr.com/index.php/documentation/peopleshr-microsoft-teams-app-help/)
- [Google Play — PeoplesHR Mobile](https://play.google.com/store/apps/details?id=com.peopleshr.hsenid)
- [Apple App Store — PeoplesHR Mobile](https://apps.apple.com/us/app/peopleshr-mobile/id1450474794)
- [APKPure listing](https://apkpure.com/peopleshr-mobile/com.peopleshr.hsenid)
- [G2 reviews](https://www.g2.com/products/peopleshr/reviews) · [SoftwareAdvice](https://www.softwareadvice.com/hr/peopleshr-profile/) · [SoftwareSuggest](https://www.softwaresuggest.com/peopleshr)
