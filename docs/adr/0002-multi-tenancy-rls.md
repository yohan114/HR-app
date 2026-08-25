# ADR 0002 — Multi-tenancy via shared schema + PostgreSQL row-level security

- **Status:** Accepted
- **Date:** 2026-08-22
- **Phase:** 0 (tasks P0-BE-04 through P0-BE-10)

## Context

Every row of business data in this system belongs to exactly one customer organisation. A leak
across that boundary exposes salaries, national identity numbers, bank accounts, medical
absence reasons and disciplinary records. It is the single failure that would end the business.

There are three standard approaches, and each trades isolation strength against operational cost.

## Decision

**Shared database, shared schema, `tenant_id` discriminator, enforced by PostgreSQL row-level
security.** Two escape-hatch tiers exist for customers with stronger requirements:
`DEDICATED_SCHEMA` and `DEDICATED_DATABASE`, selected per tenant via `tenant.isolation_tier`.

Enforcement is layered:

1. **Database (primary).** RLS policies applied by `apply_tenant_rls()` in every migration. The
   policy has both a `USING` clause (controls reads) and a `WITH CHECK` clause (prevents writing
   rows attributed to another tenant).
2. **Connection binding.** `TenantAwareDataSource` sets `app.tenant_id` via a bound parameter on
   every connection checkout — including resetting it to empty when no tenant is bound.
3. **Application (defence in depth).** Repositories filter explicitly; `TenantScopedEntity`
   populates `tenant_id` on persist and never accepts it from a request payload.
4. **Deployment shape.** The application connects as a non-owner role, because PostgreSQL exempts
   table owners from RLS.

## Rationale

**Why RLS rather than relying on application filtering alone.** Application filtering fails the
moment someone writes a repository method and forgets the predicate — a mistake that is invisible
in code review, produces no error, and leaks silently. With RLS, the same mistake returns zero
rows. The failure mode changes from "data breach" to "bug report". That asymmetry is the entire
argument.

**Why not schema-per-tenant by default.** It is the more intuitive answer and it is a trap at
scale. Every migration must run against every schema; at a thousand tenants a routine column
addition becomes a multi-hour orchestrated operation with partial-failure states. Migration
velocity is the thing that determines how fast we can ship, and this approach taxes it on every
single release. Connection pooling also degrades badly, since pools become per-schema.

**Why not database-per-tenant by default.** Strongest isolation, worst economics. Hundreds of
database instances to patch, back up, monitor and pay for. Correct for a handful of enterprise
customers with contractual separation requirements — which is exactly why it remains available as
a tier rather than the default.

**Why `tenant_id` leads every index.** RLS appends `tenant_id = current_tenant_id()` to every
query. An index that does not begin with `tenant_id` cannot satisfy that predicate, and the
planner falls back to a scan. This is not a micro-optimisation; getting it wrong makes every query
in the system slow.

**Why `LEAKPROOF` on `current_tenant_id()`.** Without it, the planner may evaluate a user-supplied
function *before* the RLS predicate, which is a documented information-leak vector: a function
that raises an error revealing its argument can be used to exfiltrate rows the caller cannot see.

## Consequences

**Accepted costs:**

- A small per-query planning overhead from policy evaluation. Measured in Phase 0; negligible when
  `tenant_id` leads the index.
- Noisy-neighbour effects are possible — one tenant's heavy report can affect others. Mitigated by
  statement timeouts, per-tenant rate limits, and the dedicated tiers for large customers.
- The application must never connect as the schema owner. This is a deployment constraint that is
  easy to violate silently, so `TenantIsolationTest` asserts it explicitly.

**Enforcement:**

- `TenantIsolationTest` verifies: cross-tenant reads return nothing, unfiltered queries stay
  scoped, writes attributed to another tenant are rejected, an unbound connection sees nothing,
  pooled connections do not leak the previous binding, the runtime role is not the owner, the
  runtime role lacks `BYPASSRLS`, and **every table with a `tenant_id` column has RLS enabled**.
- That last check is the important one for the long run: it catches the realistic regression,
  which is someone adding a table in a future migration and forgetting `apply_tenant_rls()`.

## Revisit when

- A single tenant's data volume justifies its own database on cost or performance grounds.
- Regulatory requirements in a target market mandate physical separation (watch UAE and Indonesia).
- Tenant count passes roughly 5,000 and the shared tables need partitioning by tenant.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Schema per tenant | Migration cost scales linearly with tenant count and throttles release velocity |
| Database per tenant | Operationally expensive; correct only for specific enterprise customers, retained as a tier |
| Application-level filtering only | One forgotten `WHERE` clause is a silent data breach |
| Separate deployment per tenant | Multiplies infrastructure and release cost; only viable single-digit tenant counts |
