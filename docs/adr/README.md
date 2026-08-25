# Architecture decision records

Decisions that were expensive to make and would be expensive to reverse. Each one records what was
decided, **why**, what it cost, and what would make us revisit it.

The last two are the point. Anyone can reconstruct *what* was decided by reading the code; nobody
can reconstruct *why* the alternative was rejected, and that is precisely the question asked six
months later by someone who thinks the alternative is obviously better. Several of these records
exist because the rejected option is the intuitive one.

## Index

| # | Decision | Status | Date | Phase |
|---|---|---|---|---|
| [0001](0001-modular-monolith.md) | Modular monolith, not microservices | Accepted | 2026-08-22 | 0 |
| [0002](0002-multi-tenancy-rls.md) | Multi-tenancy via shared schema + PostgreSQL row-level security | Accepted | 2026-08-22 | 0 |
| [0003](0003-offline-first-mobile.md) | Offline-first mobile, local database as the source of truth for the UI | Accepted | 2026-08-22 | 0 |
| [0004](0004-no-kotlin-multiplatform.md) | Two native data layers, not Kotlin Multiplatform | Accepted | 2026-08-22 | 0 |
| [0005](0005-cloud-provider-and-regions.md) | AWS, with a region per data-residency tier | Accepted (provisional) | 2026-08-22 | 0 |
| [0006](0006-field-level-permission-defaults.md) | Field-level permissions: split defaults, server-side projection | Accepted | 2026-08-24 | 1 |

## What belongs here

A decision earns an ADR when at least two of these are true:

- Reversing it would take more than a week.
- A competent engineer would reasonably choose differently.
- The reasoning depends on context that is not visible in the code.
- It constrains work in more than one module or on more than one platform.

Routine choices do not need one. A library selection with an obvious answer, a naming convention,
a schema detail confined to one table — those belong in a code comment next to the thing they
explain, where they will actually be read.

## Format

Each record carries: **Context** (the forces, stated without the answer), **Decision**,
**Rationale** (why, including why not the alternatives), **Consequences** (the costs we accepted,
and how the decision is enforced in tests or CI), **Revisit when** (the specific conditions that
should reopen it), and **Alternatives considered** as a table.

Records are immutable once accepted. A decision that changes gets a new record that supersedes the
old one, and the old one is marked — not edited. The history of what we believed and when is part
of what makes these useful.
