# ADR 0003 — Offline-first mobile, local database as the source of truth for the UI

- **Status:** Accepted
- **Date:** 2026-08-22
- **Phase:** 0 (tasks P0-AND-09/10, P0-IOS-09/10, P0-BE-32/33/34)

## Context

The competitive research is unambiguous about the incumbent's weakest point. The single most
common complaint in PeoplesHR's Play Store reviews is that the app is *"very slow"*, and G2
reviewers report the system being slow at peak usage. Meanwhile, the app's primary users are
often on poor connectivity: factory floors, construction sites, retail back rooms, and commutes.

Speed is our positioning. That makes this an architectural decision, not an optimisation to apply
later.

## Decision

**The on-device SQLite database is the source of truth for everything the UI renders. The network
is a background reconciliation process.**

- Reads: the UI never awaits a network call for data it has seen before. Delta sync pulls changes
  by an opaque cursor into the local store; the UI observes the local store.
- Writes: the user's action is written locally and rendered immediately, then appended to an
  outbox with a client-generated idempotency key. A background worker drains the outbox with
  retry and backoff.
- Conflicts: resolved per entity type. There is no generic last-write-wins.

Server support: `change_feed` (monotonic sequence, not timestamps) for reads, `mutation_log`
(unique on tenant + idempotency key) for writes.

## Rationale

**Why the local database is authoritative for the UI.** The alternative — cache-with-fallback —
sounds equivalent and is not. In a cache model the happy path still awaits the network, so the app
is as slow as the connection on every screen; the cache only helps when the request fails. Making
the local store authoritative means every screen paints in under 100 ms regardless of network,
always. That is the difference between "fast when the wifi is good" and "fast".

**Why a monotonic sequence rather than timestamps for the change feed.** This is the subtle one.
With `WHERE updated_at > :lastSync`, a row committed by a long transaction can become visible
*after* a client has already synced past its timestamp — the client then never sees it, silently,
forever. A sequence assigned at commit closes that window. Clock skew between application
instances makes timestamps worse still.

**Why idempotency keys are mandatory, not optional.** An offline outbox retries until it gets a
definitive answer. Without server-side deduplication, a response lost on the return path causes a
duplicate leave application or — far worse — a duplicate attendance punch. The key is generated
when the user taps, so a retry is recognisable no matter how many times the request is replayed.

**Why per-entity conflict resolution.** Generic last-write-wins is wrong for every entity type we
have:

| Entity | Strategy | Why |
|---|---|---|
| Attendance punch | Append-only | Punches are facts about the past; they never conflict |
| Leave application | Server wins on state | The balance may have moved underneath the client |
| Profile edit | Field-level merge | Two people editing different fields should both succeed |
| Approval decision | First write wins | The second approver must be told, not silently overwritten |
| Reference data | Server wins | Clients never author it |

**Why this cost is worth paying.** Offline-first is genuinely harder than request/response. It is
also the thing the incumbent cannot easily retrofit, because it requires the data layer to be
built around it from the start. That is exactly what makes it a durable advantage rather than a
feature they can copy in a sprint.

## Consequences

**Accepted costs:**

- Significant complexity in the sync engine on both platforms. Mitigated by writing the protocol
  spec once, before either platform starts, and by a shared chaos-test suite (`P0-QA-05`).
- Local storage growth. Budget: under 60 MB for a year of history; scoped sync means a normal
  employee syncs a few hundred rows, not the company.
- Stale data is possible and must be visible. Every screen has a defined offline state.
- Some operations genuinely cannot work offline (payroll runs, report generation, first login).
  These are enumerated and degrade explicitly rather than failing mysteriously.

**Non-negotiable offline capabilities:** clock in/out, view and apply for leave, view cached
payslips, employee directory, org chart, own profile, holiday calendar, queue approval decisions.

**Enforcement:**

- `P0-QA-05` chaos suite: kill mid-sync, duplicate delivery, clock skew, out-of-order arrival,
  seven-day offline backlog.
- Performance budgets are CI-enforced from Phase 1, not checked at the end.

## Revisit when

- Local database size approaches device limits for the largest tenants.
- A feature genuinely requires strong read-after-write consistency across devices.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Request/response with a loading spinner | This is what the incumbent does and what users complain about |
| Cache with network fallback | Happy path still awaits the network, so the app is still slow on bad connections |
| Full local replica of tenant data | Unacceptable storage and a serious data-exposure risk on a lost device |
| Off-the-shelf sync framework (Firebase, PowerSync, ElectricSQL) | Conflict semantics we need are per-entity and domain-specific; and it would place the tenant isolation boundary in a third party's hands |
