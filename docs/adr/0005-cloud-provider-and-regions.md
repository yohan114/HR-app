# ADR 0005 — AWS, with a region per data-residency tier

- **Status:** Accepted (provisional — see *Open question* below)
- **Date:** 2026-08-22
- **Phase:** 0 (task P0-OPS-03)

## Context

[03-architecture.md](../03-architecture.md) specifies Kubernetes, Terraform and GitHub Actions but
does not name a cloud provider. That decision cannot be deferred past Phase 0 because the
Terraform estate, the secrets strategy and the CI deployment path all encode it.

The constraint that actually decides it is **data residency**, not price or feature set. The target
markets from [01-research-peopleshr.md](../01-research-peopleshr.md) are Sri Lanka, UAE,
Philippines, Indonesia and Bangladesh. Two of those have regimes that can require personal data to
stay in-country or restrict its transfer:

- **UAE** — Federal Decree-Law 45/2021 (PDPL) restricts cross-border transfer absent an adequacy
  decision or specified safeguards, and sectoral rules (banking, health) are stricter still.
- **Indonesia** — PP 71/2019 relaxed blanket localisation for private non-public-service data, but
  sectoral rules still bite, and customer procurement teams frequently require local hosting
  regardless of what the law strictly demands.

Neither Sri Lanka, the Philippines nor Bangladesh has a provider region at all, so those are
served from the nearest suitable one either way.

## Decision

**AWS**, with three regions mapped to the `tenant.data_region` column already in the schema:

| `data_region` | AWS region | Serves |
|---|---|---|
| `default` | `ap-southeast-1` (Singapore) | Sri Lanka, Philippines, Bangladesh, Singapore, and any tenant with no residency requirement |
| `uae` | `me-central-1` (UAE) | UAE tenants requiring in-country data |
| `indonesia` | `ap-southeast-3` (Jakarta) | Indonesian tenants requiring in-country data |

Only `default` is provisioned in Phase 0. The others are stood up when the first customer needs
them — a region costs real money to run and there is no point paying for one speculatively.

## Rationale

**AWS is the only major provider with a region in every market that has one.** GCP has Singapore
and Jakarta but no UAE region (its nearest Middle East regions are in Saudi Arabia and Qatar).
Azure has UAE North and Singapore and would also work. AWS wins on breadth plus the maturity of
the specific managed services we depend on — RDS for PostgreSQL with logical replication, MSK,
ElastiCache, and IRSA for pod-level IAM.

**Region-per-tier rather than region-per-tenant.** The `data_region` column allows either, but one
region per residency requirement keeps the estate to three deployments rather than one per
customer. A tenant with no requirement lands on `default` and costs nothing extra.

**Managed services over self-hosted.** With one platform engineer's worth of capacity across the
team, running our own PostgreSQL, Kafka and Redis is a poor use of it. The premium over EC2 buys
back the operational time we do not have.

**Kubernetes rather than ECS or Lambda.** Portability matters here precisely *because* this
decision is provisional: EKS to AKS is a manageable migration, whereas an estate built on ECS task
definitions and ALB listeners is not. Lambda was rejected for the same reason as in
[ADR 0001](0001-modular-monolith.md) — payroll runs are long and CPU-bound and do not fit the
execution model.

## Consequences

**Accepted costs:**

- Three regions eventually means three of everything: cluster, database, cache, monitoring. Cost
  and operational surface grow roughly linearly with residency tiers, which is why they are
  demand-driven rather than pre-provisioned.
- Cross-region features (aggregate analytics, cross-tenant benchmarking in Phase 6) become harder,
  because the data deliberately cannot be pooled.
- We are exposed to AWS pricing and regional availability. Mitigated by Kubernetes and by using no
  service without a portable equivalent — no DynamoDB, no Step Functions, no proprietary
  event routing.

**Enforcement:**

- Terraform state is per-region and per-environment. There is no module that can accidentally
  create a resource in the wrong region.
- The tenant registry maps `data_region` to a connection pool; a tenant in `uae` cannot be served
  from `default` because the routing layer has no connection to offer.

## Open question — needs a decision before staging goes up

**We do not yet have a legal reading of what UAE and Indonesian customers actually require.** The
summary above is an engineer's reading of publicly available material, not advice, and the
difference between "customers prefer local hosting" and "the law requires it" changes the cost
model considerably.

Recommendation: get this reviewed before committing to `me-central-1` and `ap-southeast-3` spend.
The architecture supports either answer — that is the point of `data_region` — so this does not
block Phase 0.

## Revisit when

- A customer requires a provider we do not use (large enterprises occasionally mandate Azure).
- A target market gains a region on another provider and not AWS.
- Egress or managed-service costs become a material line item — likely only past a few hundred
  tenants.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Azure | Genuinely viable: has UAE North and Southeast Asia. AWS chosen on breadth of managed services we depend on. Closest alternative — revisit if a customer mandates it. |
| GCP | No UAE region. Otherwise strong. |
| Single region for everything | Simplest and cheapest, but forecloses UAE and Indonesian customers with residency requirements — two of our five target markets |
| Region per tenant | Isolation is excellent, economics are not; retained only as the `DEDICATED_DATABASE` tier |
