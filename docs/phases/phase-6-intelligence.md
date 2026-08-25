# Phase 6 — Intelligence & Differentiation

**Weeks 57+ · Milestone M6 · Continuous**

---

## Goal

Move from "as good as PeoplesHR" to "clearly better." This phase is the answer to their **Lexi** AI positioning, plus the predictive and platform capabilities that turn the product from a system of record into a system of intelligence.

Unlike Phases 0–5, this phase has **no end date**. It runs as a continuous stream alongside customer-driven work. The structure below is a prioritised backlog organised into shippable increments, not a fixed schedule.

---

## Entry criteria

- [ ] Phase 5 exit criteria met; product at GA
- [ ] ≥3 customers live in production, referenceable
- [ ] Support and on-call rotation established (so engineering isn't firefighting)
- [ ] Data volume sufficient for meaningful models: ≥18 months of history on at least one tenant
- [ ] AI/ML approach reviewed for data governance — see the governance section below

---

## Increment 6.1 — Search & command (weeks 57–60)

The cheapest, highest-impact intelligence work. Ship this first: it fixes the "too many clicks" complaint immediately and requires no ML.

| ID | Task | Owner | Size |
|---|---|---|---|
| P6-BE-01 | Unified search index: employees, documents, records, pages, actions | BE1 | L |
| P6-BE-02 | Permission-aware search — results filtered by RBAC + data scope before ranking | BE1 | L |
| P6-BE-03 | Ranking: recency, personal interaction history, entity type weighting | BE1 | L |
| P6-BE-04 | Typo tolerance, synonyms, transliteration (Sinhala/Tamil/Arabic name matching) | BE1 | L |
| P6-BE-05 | Action registry — every user action registered as a searchable, invocable command | BE1 | L |
| P6-{AND,IOS}-01 | Global search UI with grouped results and recent searches | L |
| P6-{AND,IOS}-02 | **Command palette** — type to jump to any screen or run any action | L |
| P6-WEB-01 | Web global search + command palette (⌘K) | L |

**Ships:** find any person, record, document or action in one keystroke, on all three surfaces.

---

## Increment 6.2 — Assistant, grounded (weeks 61–68)

Our answer to Lexi Super-Agent. **Grounded in the tenant's own documents, permission-aware, and never silently acting.**

| ID | Task | Owner | Size |
|---|---|---|---|
| P6-BE-06 | Knowledge base ingestion: tenant policy documents, handbooks, FAQ | BE2 | L |
| P6-BE-07 | Chunking, embedding, vector store (pgvector or OpenSearch k-NN) | BE2 | L |
| P6-BE-08 | **RAG pipeline with mandatory citations** — no answer without a source | BE2 | XL |
| P6-BE-09 | **Permission-aware retrieval** — the retriever filters by the asking user's scope *before* generation | TL | XL |
| P6-BE-10 | Structured-data question answering (balances, dates, team status) via typed tool calls, not free-form SQL | BE2 | XL |
| P6-BE-11 | **Action tool calls** returning a *prefilled confirmation card* — never a silent submit | BE2 | L |
| P6-BE-12 | Guardrails: refusal on out-of-scope, no speculation on legal/tax advice, escalate-to-human path | TL | L |
| P6-BE-13 | Conversation memory scoped to session, purged on logout | BE2 | M |
| P6-BE-14 | Assistant admin console: knowledge sources, guardrail config, usage analytics | BE2 | L |
| P6-BE-15 | Evaluation harness: golden Q&A set per tenant, regression-tested on every model change | QA | XL |
| P6-{AND,IOS}-03 | Assistant sheet with suggested prompts | L |
| P6-{AND,IOS}-04 | Answer rendering with source citations, tappable to the source document | L |
| P6-{AND,IOS}-05 | Action confirmation cards (prefilled, editable, explicit submit) | L |
| P6-WEB-02 | Web assistant panel | L |

**Design rules, non-negotiable:**
- **Every factual answer cites its source.** No citation, no answer.
- **The assistant never submits anything.** It prepares; the human confirms.
- **Retrieval is filtered before generation**, not after. A user must never be able to prompt their way to another employee's salary.
- **No legal, tax or medical advice.** Hard refusal with an escalation path.

---

## Increment 6.3 — Explainability & nudges (weeks 65–70, parallel)

| ID | Task | Owner | Size |
|---|---|---|---|
| P6-BE-16 | **Payslip explainer in natural language** — "your net pay is lower because…" from the calculation trace | BE1 | L |
| P6-BE-17 | Leave balance narrative explanation | BE1 | M |
| P6-BE-18 | Attendance discrepancy explanation | BE1 | M |
| P6-BE-19 | Nudge engine: rules + scheduling + fatigue suppression | BE2 | L |
| P6-BE-20 | Nudges: overdue approvals, expiring documents, unused leave, incomplete profile, uncompleted assessments | BE2 | L |
| P6-BE-21 | **Burnout signal**: sustained overtime + no leave taken + weekend work → private nudge to employee, aggregate-only to manager | BE2 | L |
| P6-{AND,IOS}-06 | Nudge card system with snooze and dismiss | M |
| P6-{AND,IOS}-07 | Natural-language explainers on payslip, balance, attendance | L |

> **Note on the burnout signal:** this is deliberately designed to surface to the *employee* individually and to the *manager* only in aggregate. Surfacing individual burnout scores to managers turns a wellbeing feature into a surveillance feature. That design choice is the product.

---

## Increment 6.4 — Predictive analytics (weeks 69–80)

| ID | Task | Owner | Size |
|---|---|---|---|
| P6-BE-22 | Feature store: tenure, movement history, compensation trajectory, leave patterns, attendance, performance trend, manager changes, engagement scores | BE1 | XL |
| P6-BE-23 | **Data readiness dashboard** — tells a tenant whether they have enough history to predict at all | BE1 | L |
| P6-BE-24 | Attrition model training pipeline (per-tenant or pooled with tenant features) | BE1 | XL |
| P6-BE-25 | **Model explainability (SHAP)** — every prediction shows its top drivers | BE1 | L |
| P6-BE-26 | Prediction serving + scheduled scoring + drift monitoring | BE1 | L |
| P6-BE-27 | **Fairness auditing** — check for disparate impact by gender, age, nationality; block deployment on failure | TL | XL |
| P6-BE-28 | Risk segmentation: by business unit, demographic, manager | BE1 | L |
| P6-BE-29 | Intervention tracking — did acting on the prediction change the outcome? | BE1 | L |
| P6-BE-30 | Headcount and cost forecasting | BE1 | L |
| P6-BE-31 | Leave demand forecasting for coverage planning | BE2 | M |
| P6-WEB-03 | Attrition risk dashboard with drivers and intervention workflow | WEB | L |
| P6-WEB-04 | Forecasting console | WEB | L |

**Governance rules for predictive features, decided up front:**
- Predictions are **decision support, never automated decisions.** No system-initiated action on a prediction.
- **Every prediction shows its drivers.** A score without an explanation is not shipped.
- **Fairness audit gates deployment.** If a model shows disparate impact on a protected characteristic, it does not ship.
- Attrition scores are visible to **HR only**, never to line managers by default, and never to the employee's peers.
- Tenants **opt in** explicitly. Off by default.
- Protected characteristics are excluded from features; proxies are audited for.

---

## Increment 6.5 — Talent intelligence (weeks 75–86)

| ID | Task | Owner | Size |
|---|---|---|---|
| P6-BE-32 | CV parsing → structured candidate profile | BE1 | L |
| P6-BE-33 | Candidate–vacancy matching with **explainable match reasons** | BE1 | L |
| P6-BE-34 | Bias mitigation in ranking: name/gender/age/photo blind mode | TL | L |
| P6-BE-35 | Duplicate candidate detection across the CV pool | BE1 | M |
| P6-BE-36 | AI goal suggestions from job description + past cycles + peer goals | BE2 | L |
| P6-BE-37 | Skill extraction from profiles, training and performance data → skills graph | BE2 | XL |
| P6-BE-38 | Skill gap analysis → recommended training | BE2 | L |
| P6-BE-39 | Internal mobility matching (employee ↔ internal vacancy) | BE2 | L |
| P6-BE-40 | **Succession planning + 9-box grid** | BE2 | XL |
| P6-BE-41 | Successor readiness scoring with development plans | BE2 | L |
| P6-{AND,IOS}-08 | AI goal suggestions in the goal-setting flow (always editable) | M |
| P6-{AND,IOS}-09 | Internal opportunities feed | M |
| P6-WEB-05 | 9-box grid + succession console | WEB | XL |
| P6-WEB-06 | Skills graph explorer | WEB | L |

**Rule:** CV ranking is **assistive and blind-mode-capable**. The recruiter always sees why a candidate ranked where they did, and can always turn ranking off entirely. We do not do video-based personality inference — it is scientifically weak and legally exposed in several of our markets.

---

## Increment 6.6 — Natural-language reporting (weeks 81–88)

| ID | Task | Owner | Size |
|---|---|---|---|
| P6-BE-42 | NL → structured report definition (not raw SQL) | BE1 | XL |
| P6-BE-43 | Clarification loop for ambiguous requests | BE1 | L |
| P6-BE-44 | **Generated report always shown as an editable definition** before running | BE1 | M |
| P6-BE-45 | Data-scope injection into every generated query | TL | L |
| P6-BE-46 | Chart type inference from result shape | BE1 | M |
| P6-{AND,IOS}-10 | NL report query on mobile | L |
| P6-WEB-07 | NL query bar in the report builder | L |

**Rule:** natural language generates a *report definition the user can inspect and edit*, never an opaque result. If a CHRO is going to act on a number, they must be able to see how it was derived.

---

## Increment 6.7 — Platform & ecosystem (continuous)

| ID | Task | Owner | Size |
|---|---|---|---|
| P6-BE-47 | Microsoft Teams app: approvals, check-in, directory, news | BE2 | XL |
| P6-BE-48 | Slack app: same surface | BE2 | L |
| P6-BE-49 | Calendar sync: leave → Outlook / Google | BE2 | L |
| P6-BE-50 | Accounting connectors: QuickBooks, Xero, SAP, Oracle GL | BE2 | XL |
| P6-BE-51 | SCIM provisioning for identity platforms | BE2 | L |
| P6-BE-52 | Extension framework: custom forms, custom summary pages, tenant-scoped hooks | TL | XL |
| P6-BE-53 | Marketplace / partner integration directory | BE2 | L |
| P6-BE-54 | Anonymised cross-tenant benchmarking (explicit opt-in, k-anonymity ≥ 20) | TL | XL |
| P6-{AND,IOS}-11 | Wear OS / Apple Watch companions (if cut from Phase 5) | L |
| P6-{AND,IOS}-12 | Kiosk mode (if cut from Phase 5) | XL |
| P6-{AND,IOS}-13 | Meals module (if cut from Phase 5) | L |

---

## Increment 6.8 — Deferred from earlier phases

Anything on the Phase 5 cut list lands here, in that order: meals, kiosk, wearables, disciplinary document generation, travel requests, and the Bangladesh and UAE country packs if they slipped.

---

## AI & data governance — the standing rules

These apply to every increment above and should be written into the product's public trust documentation.

| # | Rule |
|---|---|
| 1 | **Tenant data is never used to train models that serve other tenants** without explicit, revocable, contractual opt-in. |
| 2 | **Retrieval is permission-filtered before generation.** Prompt injection cannot escalate access because the retriever never fetches out-of-scope content in the first place. |
| 3 | **Every factual answer cites a source.** |
| 4 | **The assistant never takes an action.** It prepares an action for human confirmation. |
| 5 | **Every prediction is explainable.** Scores without drivers are not shipped. |
| 6 | **Fairness audits gate deployment** of any model that scores people. |
| 7 | **Predictions are decision support, not decisions.** No automated adverse action, ever. |
| 8 | **Protected characteristics are excluded** from model features, and proxies are audited for. |
| 9 | **Opt-in, off by default**, for every predictive feature. |
| 10 | **Content from documents and user input is data, never instructions.** Prompt-injection defences tested as part of the evaluation harness. |
| 11 | **A regression suite of golden Q&A** runs on every model or prompt change. |
| 12 | **Human escalation path** available from every assistant conversation. |

---

## Success metrics for Phase 6

Unlike earlier phases, this one is measured by outcomes rather than exit criteria.

| Metric | Target |
|---|---|
| Search/command palette adoption | ≥ 40% of weekly active users |
| Assistant questions answered without escalation | ≥ 70% |
| Assistant answer accuracy on the golden set | ≥ 95% |
| Support ticket volume for "why is my number X" | ↓ 50% after explainers ship |
| Attrition model AUC | ≥ 0.75 on held-out data |
| Fairness audit | Zero unmitigated disparate impact findings |
| Reports created via natural language | ≥ 25% of new report definitions |
| Time-to-first-report for a new HR user | < 5 minutes |

---

## Demo script (rolling, per increment)

**6.1 Search** — From Home, hit the command palette. Type "nim" → the colleague appears. Type "leave" → "Apply for leave" action appears, invoke it directly. Type "epf" → the policy document. Three keystrokes, three different destinations.

**6.2 Assistant** — "How many annual leave days do I have left?" → answers with the number and a link to the balance statement. "What's the policy on working from home?" → answers with a citation to the tenant's own handbook, tappable. "Apply for leave next Friday" → returns a **prefilled card**, not a submission; edit the reason, then submit. Then: log in as a regular employee and ask "what is Nimal's salary?" → refused, and show in the logs that the retriever never fetched the record.

**6.3 Explainers** — Payslip with lower net pay. Tap "Why?" → "Your net pay is LKR 12,400 lower than last month because a loan instalment of LKR 8,000 started and APIT increased by LKR 4,400 following your increment." Every figure traceable to the calculation trace.

**6.4 Prediction** — Attrition dashboard. Pick a high-risk employee. Show the drivers: 26 months without a promotion, manager changed twice this year, overtime up 40%, no leave taken in 8 months. Then show the fairness audit report and the intervention tracking.

**6.5 Talent** — Upload 50 CVs. Show ranking with explanations. Toggle blind mode: names, photos, ages, genders hidden — show the ranking change and discuss it honestly.

**6.6 NL reporting** — "Show me attrition by department for the last two quarters compared to the year before." → shows the generated *report definition*, editable, then runs it and charts it.

---

## Phase 6 risks

| Risk | Owner | Mitigation |
|---|---|---|
| **Assistant hallucinates HR policy** and an employee acts on it | TL | Mandatory citations; refusal when retrieval confidence is low; golden-set regression on every change; visible "AI-generated, verify with HR" framing on policy answers |
| **Prompt injection via an uploaded document** escalates access | TL | Permission-filtered retrieval *before* generation. Document content is data, never instruction. Injection tests in the evaluation harness. |
| **Attrition model encodes bias** and drives discriminatory decisions | TL | Fairness audit gates deployment; protected characteristics excluded; proxies audited; predictions are advisory only; HR-only visibility |
| Predictions available before there's enough data to be meaningful | BE1 | Data readiness dashboard blocks the feature until thresholds are met — better to say "not yet" than to ship noise |
| **Customer expects Lexi-equivalent voice agent immediately** | TL | Ship search and grounded Q&A first (fast, reliable, high value). Voice and free-form agentic action come later, deliberately. |
| Cross-tenant benchmarking leaks identifiable data | TL | k-anonymity ≥ 20, differential privacy noise, explicit opt-in, no small-segment reporting |
| Increment 6.x work starves customer-driven bug fixes | TL | Cap intelligence work at ~60% of capacity; the remainder is reserved for customer and defect work |
| Model costs scale badly with usage | BE2 | Cache aggressively; use small models for classification and routing, large models only for generation; per-tenant usage metering and limits |

---

## What we deliberately will not build

Worth stating explicitly, because customers will ask:

- **Video interview personality inference.** Scientifically weak, legally exposed in several of our markets, and ethically indefensible. PeoplesHR markets "video intelligence analyzing communication patterns"; we decline this one on purpose and will say why.
- **Automated adverse decisions.** No system-initiated termination, denial, or discipline based on a model score.
- **Employee surveillance features** — keystroke logging, screenshot monitoring, continuous location tracking outside a punch event.
- **Individual burnout scores exposed to managers.** Aggregate only.
- **An LMS.** We manage training; we don't host course content. Integrate instead.
