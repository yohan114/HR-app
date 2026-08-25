# Phase Plans — Index

Detailed execution plans for each phase of the roadmap. Each document is a working plan: week-by-week breakdown, task lists with IDs, deliverables, acceptance criteria, and a demo script.

| Phase | Doc | Weeks | Milestone |
|---|---|---|---|
| 0 | [Foundations](phase-0-foundations.md) | 1–4 | M0 — multi-tenant backend, auth, sync skeleton |
| 1 | [Walking Skeleton](phase-1-walking-skeleton.md) | 5–10 | M1 — biometric login, directory, org chart, profile |
| 2 | [The Daily Loop](phase-2-daily-loop.md) | 11–18 | M2 — internal beta: leave, attendance, approvals |
| 3 | [Payroll & Money](phase-3-payroll.md) | 19–30 | M3 — pilot customer, one country live |
| 4 | [Talent & Performance](phase-4-talent.md) | 31–42 | M4 — feature-credible |
| 5 | [Depth, Engagement & Scale](phase-5-scale.md) | 43–56 | M5 — general availability |
| 6 | [Intelligence & Differentiation](phase-6-intelligence.md) | 57+ | M6 — differentiated |

---

## Team & notation used across all phase docs

| Code | Role | Count |
|---|---|---|
| `TL` | Tech lead / architect | 1 |
| `BE1`, `BE2` | Backend engineers (Kotlin/Spring) | 2 |
| `AND` | Android engineer (Kotlin/Compose) | 1 |
| `IOS` | iOS engineer (Swift/SwiftUI) | 1 |
| `WEB` | Web engineer (React/TS, admin console) | 1 |
| `QA` | QA / test automation | 1 |
| `DES` | Product designer | 0.5 (shared) |

**Total: 6.5–7.5 FTE.** All week estimates below assume this team. Scale timelines proportionally if your team differs — but note that Phases 0–2 are largely serial on the backend and won't compress much by adding people.

**Task ID format:** `P{phase}-{stream}-{number}` — e.g. `P2-BE-07` is Phase 2, backend, task 7.
Streams: `BE` backend · `AND` Android · `IOS` iOS · `WEB` web · `QA` quality · `OPS` infra · `DES` design.

**Task sizing:** S = ≤1 day · M = 2–3 days · L = 4–5 days · XL = 6–10 days (split it if it's bigger).

---

## How to read a phase doc

Each contains:

1. **Goal** — one sentence, plus why this phase exists
2. **Entry criteria** — what must be true before starting
3. **Week-by-week plan** — what each person is doing
4. **Task backlog** — full itemised list with IDs, owners, sizes, dependencies
5. **Deliverables** — DB tables, API endpoints, screens, infra
6. **Exit criteria** — objectively verifiable, no judgement calls
7. **Demo script** — exactly what gets shown at the phase review
8. **Risks for this phase** — with owners and triggers

---

## Cross-phase rules

1. **Nothing merges without** — tests, multi-tenant isolation check, OpenAPI spec updated, both platforms at parity, all six screen states, accessibility pass, perf budget met.
2. **Performance budgets are CI-enforced from Phase 1**, not retrofitted at the end.
3. **The workflow engine ships before any module that needs approvals** (Phase 2, week 11–13).
4. **The formula engine ships before payroll** (Phase 3, week 19–21).
5. **Admin configuration goes to web first.** Mobile admin arrives in Phase 5.
6. **One country pack proven end-to-end before starting the second.**
7. **Every phase ends with a working, demoable build on real devices.** No "integration phase" at the end.
