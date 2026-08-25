# Phase 3 — Payroll & Money

**Weeks 19–30 · Milestone M3 — Pilot Customer**

---

## Goal

Ship the module that makes this a real HRIS: a payroll engine that produces provably correct numbers, plus the money-facing features employees care about — payslips, loans, claims, benefits.

At the end of this phase, a real customer runs a real monthly payroll on our system.

**This is the highest-stakes phase in the programme.** A leave balance bug is embarrassing; a payroll bug loses the customer and can create legal liability. Everything below is designed around that.

---

## Entry criteria

- [ ] Phase 2 exit criteria met; internal beta stable
- [ ] Pilot customer signed, with a written agreement to run parallel payroll for 2–3 cycles
- [ ] **Target country confirmed** (assumed: Sri Lanka) with statutory rules documented: EPF, ETF, APIT brackets, minimum wage rules
- [ ] Pilot customer's current payroll data obtained: 3 months of historical runs for parallel validation
- [ ] Formula engine design reviewed (drafted in Phase 2 weeks 17–18)

---

## Non-negotiable engineering rules for this phase

1. **Money is `BigDecimal` with explicit scale and rounding mode. Never `Double`. Anywhere.** A lint rule fails the build on `double`/`float` in any payroll package.
2. **Every payroll run snapshots its inputs.** Re-running a committed period must be byte-identical.
3. **Every result line records its calculation trace** — the resolved formula and its inputs. This powers the payslip explainer and makes disputes resolvable.
4. **Segregation of duties.** The user who runs payroll cannot approve it.
5. **Commit is reversible** until the period closes. Rollback is a first-class feature, not a data-fix script.
6. **No payroll code merges without a golden-dataset test.**

---

## Week-by-week

### Weeks 19–21 — Formula engine & payroll core
| Who | Focus |
|---|---|
| `TL` | Formula DSL grammar design, sandboxing model, versioning semantics |
| `BE1` | ANTLR grammar, typed AST, interpreter, variable resolution |
| `BE2` | Pay groups, periods, pay items, salary grades, employee salary |
| `AND`/`IOS` | Payslip viewer, biometric step-up, screenshot protection |
| `WEB` | Pay item designer, formula editor with live preview |
| `QA` | Formula engine test suite; golden dataset construction |
| `DES` | Payslip design, explainer interaction, claims/loans flows |

### Weeks 22–24 — Tax, statutory, run pipeline
| Who | Focus |
|---|---|
| `TL` | Statutory plugin interface; run pipeline design review |
| `BE1` | Tax engine: brackets, exemptions, adjustments, annualisation |
| `BE2` | **Payroll run pipeline**: LOCK → VALIDATE → CALCULATE → REVIEW → COMMIT |
| `AND`/`IOS` | Payslip explainer, MoM/YoY comparison |
| `WEB` | Payroll run console, validation dashboard |
| `QA` | Tax calculation matrix; run pipeline state tests |
| `DES` | Total rewards, benefits screens |

### Weeks 25–27 — Outputs, loans, claims
| Who | Focus |
|---|---|
| `TL` | Parallel-run methodology with the pilot customer |
| `BE1` | Payslip generation, bank files, GL export |
| `BE2` | Loans, benefits, expense claims |
| `AND`/`IOS` | Loans, claims (camera + OCR), benefits |
| `WEB` | Bank file templates, GL mapping, loan/benefit admin |
| `QA` | Bank file format validation; **first parallel run** |
| `DES` | Phase 4 designs (performance, recruitment) |

### Weeks 28–30 — Parallel runs, hardening, go-live
| Who | Focus |
|---|---|
| `TL` | Pilot go-live coordination, variance triage |
| `BE1`/`BE2` | Variance fixes, performance tuning, anomaly detection |
| `AND`/`IOS` | Polish, accessibility, perf, release |
| `WEB` | Payroll admin completeness |
| `QA` | **Parallel runs 2 and 3, zero-variance target**; full regression |
| `DES` | Phase 4 designs |

---

## Task backlog

### Formula engine — weeks 19–21

| ID | Task | Owner | Size |
|---|---|---|---|
| P3-BE-01 | ANTLR grammar: literals, operators, precedence, function calls, conditionals | BE1 | L |
| P3-BE-02 | Type system: `Money`, `Number`, `Date`, `Duration`, `Boolean`, `String`, `Enum` | BE1 | L |
| P3-BE-03 | Type checker — errors at publish time, never at run time | BE1 | L |
| P3-BE-04 | Interpreter with **no loops, no I/O, no reflection, no host access** | BE1 | L |
| P3-BE-05 | Execution guards: time bound, memory bound, expression depth limit | BE1 | M |
| P3-BE-06 | Variable resolution: `employee.*`, `attendance.*`, `leave.*`, `payitem.*`, `period.*`, `company.*` | BE1 | L |
| P3-BE-07 | Function library: arithmetic, `round`/`ceil`/`floor` with explicit modes, date math | BE1 | L |
| P3-BE-08 | Function library: `prorate`, `slab`, `lookup`, `min`/`max`/`clamp`, `if`/`case` | BE1 | L |
| P3-BE-09 | `formula` table with immutable versioning; publish semantics | BE1 | M |
| P3-BE-10 | Formula validation API + **live preview against a sample employee** | BE1 | M |
| P3-BE-11 | Dependency graph between pay items; cycle detection; evaluation ordering | BE1 | L |

### Payroll core — weeks 19–22

| ID | Task | Owner | Size |
|---|---|---|---|
| P3-BE-12 | `pay_group`, `pay_period`, `pay_process`, period generation, period lock | BE2 | L |
| P3-BE-13 | `pay_item` — all calculation methods, taxability, statutory base flags, GL code | BE2 | L |
| P3-BE-14 | `pay_group_pay_item` overrides | BE2 | M |
| P3-BE-15 | `employee_pay_item` assignment (individual, bulk, by classification) | BE2 | L |
| P3-BE-16 | `employee_salary` effective-dated + `salary_amendment` (increment/revision/correction) | BE2 | L |
| P3-BE-17 | Salary amendment approval workflow + cancellation | BE2 | M |
| P3-BE-18 | `salary_grade` bands with min/mid/max validation | BE2 | M |
| P3-BE-19 | Retro-pay / arrears computation on backdated amendments | BE2 | L |
| P3-BE-20 | Attendance & leave aggregation into payroll inputs (paid days, LOP days, OT hours) | BE2 | L |

### Tax & statutory — weeks 22–24

| ID | Task | Owner | Size |
|---|---|---|---|
| P3-BE-21 | `tax_config` — brackets, reliefs, effective-dated, per country/year | BE1 | L |
| P3-BE-22 | Tax calculation: progressive brackets, cumulative and non-cumulative modes | BE1 | L |
| P3-BE-23 | Annualisation rules + mid-year joiner handling | BE1 | L |
| P3-BE-24 | `employee_tax_profile` + exemptions + additional deductions | BE1 | M |
| P3-BE-25 | `tax_adjustment` + bulk upload | BE1 | M |
| P3-BE-26 | **`StatutoryCalculator` plugin interface** — contributions, tax, reports, validation | TL | L |
| P3-BE-27 | **Sri Lanka pack**: EPF (employee 8% / employer 12%), ETF (3%), APIT brackets | BE1 | XL |
| P3-BE-28 | Sri Lanka statutory report formats (EPF/ETF returns, APIT schedules) | BE1 | L |
| P3-BE-29 | `statutory_scheme` + `employee_statutory` with opt-out and ceilings | BE1 | M |

### Payroll run pipeline — weeks 22–25

| ID | Task | Owner | Size |
|---|---|---|---|
| P3-BE-30 | `payroll_run` state machine: LOCK → VALIDATE → CALCULATE → REVIEW → COMMIT | BE2 | XL |
| P3-BE-31 | **Input snapshotting** to S3 — employee state, assignments, aggregates, tax tables, formula versions | BE2 | L |
| P3-BE-32 | Distributed lock on (pay_group, period) via Redis; period lock in DB | BE2 | M |
| P3-BE-33 | Validation phase: `payroll_validation` with ERROR/WARN severity | BE2 | L |
| P3-BE-34 | Pre-process validation rules library (~25 rules: missing bank, no salary, negative net, …) | BE2 | L |
| P3-BE-35 | **Calculation phase**: per-employee, parallel, deterministic, writes to staging | BE2 | XL |
| P3-BE-36 | `payroll_result` + `payroll_result_line` with **`calculation_trace` per line** | BE2 | L |
| P3-BE-37 | Avoid-negative-salary guard + configurable resolution strategy | BE2 | M |
| P3-BE-38 | Review phase: `payroll_anomaly` detection vs prior period with variance thresholds | BE2 | L |
| P3-BE-39 | Approval with **segregation of duties enforcement** | BE2 | M |
| P3-BE-40 | Commit phase: atomic publish, event emission, payslip generation trigger | BE2 | L |
| P3-BE-41 | **Rollback**: full reversal until period close, with audit | BE2 | L |
| P3-BE-42 | Resumability: any phase can restart from its last checkpoint | BE2 | L |
| P3-BE-43 | Run progress reporting (live phase + per-employee progress) | BE2 | M |
| P3-BE-44 | Other Payments run (bonus, ad-hoc) | BE2 | M |
| P3-BE-45 | Final Payment / full & final settlement run | BE2 | L |

### Outputs — weeks 25–27

| ID | Task | Owner | Size |
|---|---|---|---|
| P3-BE-46 | Payslip PDF generation (PDFBox), templated, multilingual | BE1 | L |
| P3-BE-47 | Payslip encryption at rest + short-lived signed URL delivery | BE1 | M |
| P3-BE-48 | `payslip` table, publish control, view tracking | BE1 | M |
| P3-BE-49 | **Payslip explainer API** — per-line formula, inputs, and result | BE1 | L |
| P3-BE-50 | MoM / YoY comparison API | BE1 | M |
| P3-BE-51 | `bank_file_template` + spec-driven generator (fixed-width, CSV, XML) | BE1 | XL |
| P3-BE-52 | Bank file generation, password protection, rollback | BE1 | L |
| P3-BE-53 | Sri Lanka bank formats: at least 3 major banks | BE1 | L |
| P3-BE-54 | `gl_mapping` + `gl_batch` + `gl_entry` + export (CSV + generic journal) | BE1 | L |
| P3-BE-55 | GL rollback | BE1 | M |

### Loans, benefits, claims — weeks 25–27

| ID | Task | Owner | Size |
|---|---|---|---|
| P3-BE-56 | `loan_type` — entitlement rules, checklists, interest methods, workflow binding | BE2 | L |
| P3-BE-57 | Loan application with **eligibility auto-check and reasons on failure** | BE2 | L |
| P3-BE-58 | `loan_schedule` generation (flat, reducing balance, zero-interest) | BE2 | L |
| P3-BE-59 | Loan → payroll deduction integration; loan stop configuration | BE2 | L |
| P3-BE-60 | Loan settlement (early, partial, full) + recalculation | BE2 | M |
| P3-BE-61 | Loan history & balance API | BE2 | M |
| P3-BE-62 | `benefit_category`, `benefit`, eligibility, grade assignment | BE2 | L |
| P3-BE-63 | Benefit application + workflow + history | BE2 | M |
| P3-BE-64 | `expense_category`, `expense_claim`, `expense_claim_line` | BE2 | L |
| P3-BE-65 | Receipt upload + **OCR extraction** (amount, date, merchant) | BE2 | L |
| P3-BE-66 | Claim approval workflow + payroll reimbursement integration | BE2 | M |

### Android & iOS (mirrored)

| ID | Task | Size |
|---|---|---|
| P3-{AND,IOS}-01 | Payslip list by period with published-state handling | M |
| P3-{AND,IOS}-02 | **Biometric step-up gate** (fresh assertion, 5-minute validity) | L |
| P3-{AND,IOS}-03 | Payslip viewer: earnings, deductions, employer contributions, net | L |
| P3-{AND,IOS}-04 | **Payslip explainer** — tap any line → formula, inputs, result | L |
| P3-{AND,IOS}-05 | MoM / YoY comparison chart | M |
| P3-{AND,IOS}-06 | PDF download + share with a warning; encrypted local cache | M |
| P3-{AND,IOS}-07 | Screenshot protection (`FLAG_SECURE` / iOS detection + blurred snapshot) | M |
| P3-{AND,IOS}-08 | Loans: list, balance, repayment schedule | M |
| P3-{AND,IOS}-09 | Loan application with live eligibility feedback | L |
| P3-{AND,IOS}-10 | Loan early-settlement request | M |
| P3-{AND,IOS}-11 | Claims: list, status tracking | M |
| P3-{AND,IOS}-12 | **Claim submission with camera receipt capture + OCR prefill** | XL |
| P3-{AND,IOS}-13 | Multi-line claim entry with per-line categories | L |
| P3-{AND,IOS}-14 | Benefits: catalogue with **eligibility status and reason when ineligible** | L |
| P3-{AND,IOS}-15 | Benefit application + history | M |
| P3-{AND,IOS}-16 | Payslip-ready home card | S |
| P3-{AND,IOS}-17 | Payroll run status card (HR admin) | M |
| P3-{AND,IOS}-18 | Offline behaviour: cached payslips readable offline; claims queue offline | L |
| P3-{AND,IOS}-19 | All six states, accessibility, perf for new screens | L |

### Web admin

| ID | Task | Owner | Size |
|---|---|---|---|
| P3-WEB-01 | Pay group / period admin, period lock control | WEB | L |
| P3-WEB-02 | **Pay item designer** with formula editor, syntax highlighting, live preview | WEB | XL |
| P3-WEB-03 | Pay item assignment: individual, bulk, by classification, Excel upload | WEB | L |
| P3-WEB-04 | Salary administration: amendments, revisions, grade changes, bulk increment | WEB | XL |
| P3-WEB-05 | Tax configuration: brackets, reliefs, exemptions, effective dating | WEB | L |
| P3-WEB-06 | Statutory scheme configuration | WEB | M |
| P3-WEB-07 | **Payroll run console** — live phase, progress, per-employee status | WEB | XL |
| P3-WEB-08 | Validation dashboard: errors and warnings, drill to employee, bulk resolve | WEB | L |
| P3-WEB-09 | Anomaly review dashboard with variance highlighting | WEB | L |
| P3-WEB-10 | Run approval (segregation-of-duties enforced in UI and API) | WEB | M |
| P3-WEB-11 | Rollback with impact preview and confirmation | WEB | M |
| P3-WEB-12 | Bank file template designer | WEB | L |
| P3-WEB-13 | GL mapping configuration | WEB | L |
| P3-WEB-14 | Payslip template designer | WEB | L |
| P3-WEB-15 | Loan type / benefit / expense category admin | WEB | L |
| P3-WEB-16 | Loan and claim oversight with bulk actions | WEB | L |

### QA — the most important stream in this phase

| ID | Task | Owner | Size |
|---|---|---|---|
| P3-QA-01 | Formula engine test suite: type checking, evaluation, guards, sandbox escape attempts | QA | XL |
| P3-QA-02 | **Golden dataset construction** — 200 employees covering every edge case | QA | XL |
| P3-QA-03 | **Reproducibility test in CI** — re-running a committed period is byte-identical | QA | L |
| P3-QA-04 | Tax calculation matrix vs. hand-computed expected values | QA | XL |
| P3-QA-05 | Statutory contribution tests (EPF/ETF) incl. ceilings, opt-outs, mid-month joiners | QA | L |
| P3-QA-06 | Run pipeline state tests: every transition, failure at every phase, resume, rollback | QA | XL |
| P3-QA-07 | Concurrency: two runs on the same period → one blocked | QA | M |
| P3-QA-08 | Segregation-of-duties enforcement test | QA | M |
| P3-QA-09 | Rounding and precision tests — no cent drift across 10,000 employees | QA | L |
| P3-QA-10 | Bank file format validation against each bank's published spec | QA | L |
| P3-QA-11 | GL balance test — debits equal credits, always | QA | M |
| P3-QA-12 | Loan schedule tests: flat, reducing, early settlement, mid-loan salary change | QA | L |
| P3-QA-13 | **Parallel run #1** vs pilot customer's existing payroll — variance report | QA | XL |
| P3-QA-14 | **Parallel run #2** — target variance zero | QA | L |
| P3-QA-15 | **Parallel run #3** — zero variance required for go-live | QA | L |
| P3-QA-16 | Payroll performance: 10,000 employees calculated in < 10 minutes | QA | L |
| P3-QA-17 | Payslip security: no plaintext cache, screenshot blocked, signed URL expiry | QA | M |
| P3-QA-18 | Static analysis rule: no `double`/`float` in payroll packages | QA | S |

---

## Deliverables

### Database tables (~40)
`formula` · `pay_group` · `pay_period` · `pay_process` · `pay_item` · `pay_group_pay_item` · `employee_pay_item` · `employee_salary` · `salary_amendment` · `tax_config` · `employee_tax_profile` · `tax_adjustment` · `statutory_scheme` · `employee_statutory` · `payroll_run` · `payroll_run_input_snapshot` · `payroll_result` · `payroll_result_line` · `payroll_validation` · `payroll_anomaly` · `payslip` · `bank_file_template` · `bank_file` · `gl_mapping` · `gl_batch` · `gl_entry` · `loan_type` · `loan` · `loan_schedule` · `loan_settlement` · `benefit_category` · `benefit` · `salary_grade_benefit` · `employee_benefit` · `benefit_application` · `expense_category` · `expense_claim` · `expense_claim_line`

### API endpoints
```
GET/POST /v1/payroll/pay-groups|periods|items
POST     /v1/payroll/formulas/validate
POST     /v1/payroll/formulas/preview
POST     /v1/payroll/runs                     ← start
GET      /v1/payroll/runs/{id}                ← live status
POST     /v1/payroll/runs/{id}/validate|calculate|approve|commit|rollback
GET      /v1/payroll/runs/{id}/validations|anomalies|results
GET      /v1/payroll/runs/{id}/bank-files|gl

GET      /v1/me/payslips
GET      /v1/me/payslips/{periodId}           ← requires step-up auth
GET      /v1/me/payslips/{periodId}/explain   ← per-line trace
GET      /v1/me/payslips/compare

GET/POST /v1/loans, /v1/loans/{id}/schedule|settle
GET/POST /v1/benefits, /v1/benefits/applications
GET/POST /v1/claims, POST /v1/claims/{id}/receipts
```

### Screens
~20 new per platform; the web payroll console is the largest single web deliverable in the programme.

---

## Exit criteria

| # | Criterion | Verification |
|---|---|---|
| 1 | **Three consecutive parallel runs with zero variance** against the pilot customer's existing payroll | `P3-QA-13/14/15` — variance report signed off by the customer |
| 2 | **Reproducibility**: re-running a committed period produces byte-identical results | `P3-QA-03` in CI |
| 3 | Golden dataset (200 employees, every edge case) passes 100% | `P3-QA-02` |
| 4 | Rollback fully reverses a committed run including payslips, bank files, GL | Manual + `P3-QA-06` |
| 5 | Segregation of duties enforced — runner cannot approve | `P3-QA-08` |
| 6 | No cent drift: sum of 10,000 payslips equals the run total exactly | `P3-QA-09` |
| 7 | Bank files validate against each bank's published specification | `P3-QA-10` |
| 8 | GL debits equal credits on every run | `P3-QA-11` |
| 9 | Payslip explainer shows formula and inputs for every line | Manual |
| 10 | Payslip requires biometric step-up; screenshots blocked; no plaintext local cache | `P3-QA-17` |
| 11 | 10,000-employee payroll calculates in < 10 minutes | `P3-QA-16` |
| 12 | No `double`/`float` anywhere in payroll code | `P3-QA-18` build rule |
| 13 | **Pilot customer live on production payroll for one full cycle** | Customer sign-off |
| 14 | All Phase 1–2 budgets and criteria still met | CI regression |

---

## The parallel-run methodology

This is how we de-risk go-live. Do not skip a step.

| Run | Week | What happens | Success bar |
|---|---|---|---|
| **Parallel 1** | 26 | Import 3 months of the customer's historical data. Run our engine on a closed historical period. Compare every employee, every line. | Variance report produced; all differences explained (ours wrong / theirs wrong / config difference) |
| **Parallel 2** | 28 | Run alongside their live payroll for the current period. They pay from their system; we compare. | ≤ 5 employees with variance, all traced to config, all fixed |
| **Parallel 3** | 29 | Same, next period. | **Zero variance.** Non-negotiable gate. |
| **Go-live** | 30 | We are the system of record. Their old system runs one final time as a safety net only. | Customer signs off |

**Variance triage rule:** every single difference gets a written explanation before the next run. "Rounding" is not an explanation — identify the exact rounding rule and where it differs.

---

## Demo script (end of week 30)

1. **Formula editor** — Create a pay item: "Transport allowance = 10% of basic, prorated by worked days for part-timers." Show the type checker rejecting a mistake. Show live preview against a real employee.
2. **Run payroll** — Start a run on the demo tenant. Watch the console: LOCK → VALIDATE (show 3 validation errors, drill into one, fix it) → CALCULATE (live progress across 500 employees) → REVIEW (show an anomaly: one employee's net pay up 40%, drill in, it's a legitimate promotion) → approve as a *different user* (show the runner being blocked) → COMMIT.
3. **Reproducibility** — Re-run the same committed period from its snapshot. Diff the output: byte-identical. Show the CI test doing this on every build.
4. **Rollback** — Roll the run back. Show payslips unpublished, bank file voided, GL batch reversed, all audited.
5. **Payslip on mobile** — Open the app, tap the payslip card. Face ID prompt. Payslip renders. **Tap the "Transport allowance" line** — it shows the formula, the basic salary input, the worked-days input, and the result. Tap "compare" — this month vs last month vs same month last year.
6. **Security** — Try to screenshot the payslip on Android: blocked. Background the app on iOS: blurred snapshot.
7. **Loan** — Apply for a loan that exceeds entitlement. Show the rejection **with the specific reason** ("requires 12 months service; you have 7"). Apply for a valid one. Show the repayment schedule and the deduction appearing on the next payslip.
8. **Claim** — Photograph a receipt. OCR prefills amount, date, merchant. Submit. Approve on a manager's phone. Show it flowing into the reimbursement run.
9. **The headline** — Show the three parallel-run variance reports: 47 variances → 3 variances → **zero**. Then the customer's sign-off.

---

## Phase risks

| Risk | Trigger | Owner | Mitigation |
|---|---|---|---|
| **Parallel run variance won't close** | Run 2 still showing >20 variances | TL | This is the phase's defining risk. Budget 2 extra weeks in the plan. If run 3 isn't zero, **do not go live** — slip the milestone. A wrong payroll is unrecoverable reputationally. |
| Formula engine expressiveness insufficient for real policies | Customer policy can't be expressed by week 24 | BE1 | Gather 20 real pay-item formulas from the pilot customer *before* week 19 and use them as the grammar's acceptance criteria |
| Statutory rules misunderstood | Variance concentrated in EPF/ETF/APIT | TL | Engage a local payroll accountant as a paid reviewer from week 22. Do not rely on documentation alone. |
| Payroll run too slow at scale | > 10 min for 10k employees | BE2 | Parallel per-employee calculation with a bounded pool; profile early in week 25, not week 29 |
| Cent drift from rounding | `P3-QA-09` failures | BE1 | Explicit scale and `RoundingMode` at every arithmetic site; property-based tests |
| OCR accuracy disappoints on claims | User complaints in beta | BE2 | OCR is prefill-only, always user-editable. Never auto-submit. Frame it as a convenience, not automation. |
| Bank file rejected by the bank | Failed transfer in run 1 | BE1 | Validate against the published spec *and* submit a test file to the bank before go-live |
| Scope pressure to add country #2 early | Sales asking for Philippines in week 26 | TL | **Refuse.** Rule 6: one country proven end-to-end first. Philippines is Phase 5. |

---

## Not in Phase 3

- Country packs beyond Sri Lanka (Phase 5)
- Multi-currency (Phase 5)
- Payroll simulator (Phase 5)
- Travel requests and advances (Phase 5)
- Performance, recruitment, onboarding (Phase 4)
- Mobile payroll administration beyond the status card (Phase 5)
