# ADR 0001 — Modular monolith, not microservices

- **Status:** Accepted
- **Date:** 2026-08-22
- **Deciders:** Tech lead
- **Phase:** 0 (task P0-OPS-10)

## Context

We are building a multi-tenant HRIS covering roughly 40 functional modules: core HR, time and
attendance, absence, payroll, talent, performance, engagement, documents, analytics. The research
in [01-research-peopleshr.md](../01-research-peopleshr.md) establishes the scope.

The obvious modern default is microservices — one service per bounded context, independently
deployable. We are choosing not to do that, and the reasoning should be written down because it
will be challenged.

## Decision

**One deployable API process, internally divided into modules whose boundaries are verified at
build time by Spring Modulith.** Three additional worker processes are split out where the
workload profile genuinely differs: the payroll engine, the attendance processor, and the
notification dispatcher.

Modules communicate through published APIs (a module's root package) and domain events. Reaching
into another module's `internal` package fails the build via `ModuleStructureTest`.

## Rationale

**An HRIS is an unusually interconnected domain.** A single promotion must atomically update the
employee record, the salary, the pay item assignments, the benefit entitlements, the org chart,
the access rights, and the reporting hierarchy. Under microservices this becomes a distributed
transaction — a saga with compensating actions — across precisely the boundaries that most need
atomicity. We would be paying the highest cost of microservices at the exact point where it buys
us the least.

**Payroll correctness is the product's existential risk.** A payroll run reads employee state,
attendance aggregates, leave balances, loan schedules, tax configuration, and statutory rules,
then writes results that must be reproducible byte-for-byte months later. Doing that across seven
services with independent deployment cadences turns "reproduce October's payroll" into an
archaeology exercise. In one process with one transactional boundary and one input snapshot, it
is a straightforward guarantee.

**We do not have a scaling problem that microservices solve.** Even at our GA target — 100k
employees across 50 tenants, 5k concurrent users — this is a modest workload for a well-indexed
Postgres and a JVM with virtual threads. The three workloads that genuinely differ in shape
(long-running CPU-bound payroll, high-volume attendance batch, I/O-bound notification fan-out)
are split out precisely because they differ, not on principle.

**Team size.** Six to eight engineers. Microservices distribute a codebase across a network in
order to let independent teams deploy independently. With one team, we would be paying the
distribution cost and receiving none of the organisational benefit.

**The boundaries are the valuable part, and we keep them.** What microservices actually enforce is
module discipline. Spring Modulith enforces the same discipline with a compile-time check instead
of a network hop. If a module later proves it needs independent scaling or an independent release
cadence, the boundary is already clean and extraction is mechanical.

## Consequences

**Accepted costs:**

- One deployment unit means one release cadence for the API. A change to the engagement module
  redeploys payroll code. Mitigated by feature flags and staged rollout.
- A memory leak or runaway query in one module affects all of them. Mitigated by per-module
  metrics, bulkheads on external calls, and statement timeouts.
- The codebase will get large. Mitigated by module boundaries being real and enforced, not
  aspirational.
- Scaling is coarse-grained: we scale the whole API, not one module. This is cheap at our size.

**Retained options:**

- Any module can be extracted later. Modulith's event-driven communication is already
  network-transport-agnostic.
- The three workers demonstrate the extraction pattern is available and already exercised.

**Enforcement:**

- `ModuleStructureTest` fails the build on a boundary violation or dependency cycle.
- Module documentation is generated from the code, so architecture diagrams cannot drift.

## Revisit when

- A single module's resource profile forces the whole API to scale for it.
- Team size passes roughly 25 engineers, where independent deploy cadence starts to pay for itself.
- A regulatory requirement demands physical separation of a specific module's processing.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Microservices per bounded context | Distributed transactions across the exact seams that need atomicity; payroll reproducibility becomes very hard; no organisational benefit at our team size |
| Unstructured monolith | Boundaries erode within months; the untangling cost lands exactly when the codebase is largest |
| Serverless functions | Cold starts are unacceptable for a mobile app whose entire positioning is speed; long-running payroll runs do not fit the execution model |
