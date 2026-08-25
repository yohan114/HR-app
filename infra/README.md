# Infrastructure

**Status: written, never executed.** No Terraform, AWS CLI, kubectl, kustomize or Docker was
available in the environment where this was authored.

What *has* been verified:

- **YAML syntax** — 20 files, 23 documents, 0 failures
- **Terraform wiring** — 47 files pass [`scripts/tf-static-check.mjs`](scripts/tf-static-check.mjs):
  brace balance, every `module.X.Y` resolves to a real output, every `var.X` is declared (in both
  modules and root modules), every module argument matches a declared variable, and every
  `local.X` reference exists. Each check was tested against a deliberately introduced error to
  confirm it fails rather than passing vacuously.

What has **not**: `terraform plan`, `kustomize build`, `docker build`, or anything touching AWS.
Treat the first plan as a debugging session, not a formality.

Cloud and region strategy: [ADR 0005](../docs/adr/0005-cloud-provider-and-regions.md).

---

## Layout

```
infra/
├── terraform/
│   ├── bootstrap/          State backend. Run ONCE per account, with local state.
│   ├── modules/
│   │   ├── network/        VPC, three subnet tiers, NAT, VPC endpoints, flow logs
│   │   ├── eks/            Cluster, OIDC provider for IRSA, node group, add-ons
│   │   ├── database/       RDS PostgreSQL, KMS, the two-role credential model
│   │   ├── cache/          ElastiCache Redis — cache, rate limits, payroll locks
│   │   ├── messaging/      MSK with IAM auth
│   │   ├── search/         OpenSearch in-VPC, IAM auth
│   │   ├── storage/        Four S3 buckets with differing retention
│   │   ├── secrets/        Secrets Manager containers + IRSA for External Secrets
│   │   ├── ecr/            Container registry, immutable tags, cross-account pull
│   │   └── cicd/           GitHub OIDC provider, build and deploy roles
│   └── environments/
│       ├── staging/        Root module composing all eight
│       └── prod/           Same modules, production sizing, separate AWS account
├── k8s/
│   ├── base/               Kustomize base — deployment, service, HPA, PDB, NetworkPolicy
│   ├── overlays/           staging, prod
│   └── observability/      OpenTelemetry collector
├── scripts/                Static checks that run without a Terraform binary
└── postgres/init/          Local Docker role bootstrap
```

### Order of operations

```bash
cd infra/terraform/bootstrap && terraform init && terraform apply
```

Then paste the `backend_configuration` output into the environment's `backend "s3"` block and:

```bash
cd infra/terraform/environments/staging && terraform init && terraform plan
```

---

## The one thing that must not be got wrong

**The application connects as a non-owner database role.**

PostgreSQL exempts table owners from row-level security. If the application connects as
`hr_owner`, every RLS policy silently stops applying — and `TenantIsolationTest` would still pass,
because it would be testing a connection that bypasses the thing it is meant to verify.

This is why:

- `modules/database` provisions **two** credentials: the AWS-managed owner secret (Flyway only)
  and a separate application secret.
- `k8s/base/deployment.yaml` wires `FLYWAY_*` and `DB_*` to **different** Kubernetes Secrets.
- `TenantIsolationTest` asserts `current_user != tableowner` as a test, not a convention.

If someone ever "simplifies" this by using one credential, tenant isolation is gone and nothing
will fail loudly. See [ADR 0002](../docs/adr/0002-multi-tenancy-rls.md).

---

## Secrets

No secret value exists in this repository, in a container image, or in Terraform state.

```
AWS Secrets Manager
        │  read via IRSA role scoped by ARN prefix to one environment
        ▼
External Secrets Operator
        │  materialises
        ▼
Kubernetes Secret  ──►  env vars in the pod
```

- Terraform creates secret **containers**, access policy and rotation schedule — never values. A
  value passed through Terraform lands in state, and state gets backed up, downloaded and pasted
  into tickets.
- The database owner password is managed by RDS (`manage_master_user_password`), so it never
  passes through Terraform at all.
- The External Secrets IRSA role is prefix-scoped: a staging cluster cannot read production
  secrets even if someone applies the wrong manifest.
- The role has `GetSecretValue` and `DescribeSecret` only. Nothing in the cluster writes secrets.

### Placing a value by hand

```bash
aws secretsmanager put-secret-value --secret-id hr-staging/jwt-signing-key --secret-string file://key.json
```

The JWT signing keypair is the one to get right first: if it is absent the application generates
an ephemeral pair at startup and logs a warning. Fine on a laptop. In a deployment it means every
restart invalidates all outstanding tokens, and multiple replicas cannot verify each other's.

---

## Deploying

```bash
cd infra/terraform/environments/staging && terraform init && terraform plan
```

Overlay values are **rendered from Terraform outputs**, not committed:

```bash
terraform -chdir=infra/terraform/environments/staging output -json > /tmp/tf.json
```

```bash
node infra/scripts/render-overlay.mjs --environment staging --terraform-output /tmp/tf.json --image <repo>@sha256:<digest>
```

```bash
kustomize build infra/k8s/overlays/staging | kubectl apply -f -
```

`kustomize build` fails until the render script has run. That is deliberate — a missing file is
louder than a stale placeholder, which is what this replaced.

The render script enforces three things:

- **The image must be a digest**, not a tag. A tag can move, which makes a rollback ambiguous and
  a rollout non-reproducible.
- **Every required Terraform output must exist.** A missing one fails with a message naming it,
  rather than rendering an empty string into a ConfigMap.
- **Nothing credential-shaped is written.** A `database_password` output would abort the render.
  Secrets reach the cluster only through the External Secrets Operator.

All three guards were verified against deliberately bad input.

---

## Decisions worth knowing before you change something

**No CPU limit on the API container.** Only a request. CPU limits cause CFS throttling that
presents as unexplained tail latency; the request already guarantees a share and the HPA handles
sustained load. Memory *is* limited, because a leak should kill the pod rather than the node.

**`startupProbe` with a 5-minute budget.** Flyway migrations and Hibernate validation can take a
while against a cold database. Without it the liveness probe kills the pod mid-migration and
retries forever.

**Liveness fails slower than readiness.** A slow dependency should take a pod out of the load
balancer, not restart it.

**The NetworkPolicy blocks `169.254.169.254`.** Without that, an SSRF in the application can read
node IAM credentials — one of the most commonly exploited routes out of a container.

**Traces are scrubbed before export.** Request bodies, bind parameters and auth headers are
dropped; user identifiers are hashed. A trace backend is queried far more casually, by more
people, than the database is.

**`backup_retention_days` cannot be zero.** Validated in the module. Zero disables point-in-time
recovery, which would make the RPO target in `P0-QA-20` unachievable.

---

## More decisions worth knowing

**The data subnet tier has no route to a NAT gateway.** RDS, ElastiCache, MSK and OpenSearch
cannot reach the internet at all. This is the difference between "an attacker read your database"
and "an attacker read your database and exfiltrated it".

**Node launch templates enforce IMDSv2** with a hop limit of 2. With IMDSv1, any SSRF in a pod can
read the node's IAM credentials with a plain GET. This is the single highest-value node hardening
available and it is not on by default.

**MSK sets `unclean.leader.election.enable=false`.** Allowing an out-of-sync replica to become
leader trades durability for availability. For attendance punches and payroll events that is the
wrong trade — a silently dropped punch becomes a payroll dispute weeks later.

**`auto.create.topics.enable=false`.** A typo in a topic name should fail loudly, not quietly
create a topic nobody consumes and lose every message published to it.

**OpenSearch slow logs are deliberately disabled.** They record the query body, and directory
searches contain employee names. Slow-query analysis goes through application metrics instead.

**S3 payslip and payroll-snapshot buckets deny deletion to the application role**, including
`BypassGovernanceRetention`. Payroll reproducibility depends on those objects being byte-identical
years later; deleting one is an administrative act with a paper trail, not something a bug can do.

**Redis uses `volatile-lru`, not `allkeys-lru`.** Locks and rate-limit counters carry TTLs;
anything without one is there deliberately and must not be silently evicted.

**OpenSearch holds tenant data outside PostgreSQL, where RLS does not apply.** Isolation there has
to be enforced by the application on every query, through a single indexing/search facade rather
than by each caller remembering. Standing up that cluster is the moment tenant isolation stops
being purely a database property — worth remembering when the directory index lands in Phase 1.

---

## Not built

| Item | Task |
|---|---|
| Ingress controller, cert-manager, external-dns | P0-OPS-07 |
| Cluster autoscaler / Karpenter | P0-OPS-07 |
| Grafana dashboards and alert rules | P0-OPS-06 |
| Runbooks, on-call rotation, status page | Phase 5 |

**The deploy pipeline is now complete but has never run.** The ECR registry and the GitHub OIDC
roles it depends on are in Terraform (`modules/ecr`, `modules/cicd`), so the remaining prerequisite
is an AWS account to apply against, plus setting the three GitHub secrets listed in
[terraform/environments/README.md](terraform/environments/README.md).

Note the deploy roles' ARNs must be **environment** secrets, not repository secrets — the IAM
trust condition keys on `environment:prod`, so GitHub's approval gate *is* the access control
rather than a process convention on top of it.

---

## Before any of this touches real data

- [ ] `terraform plan` runs clean against a real account
- [ ] `kustomize build` succeeds for both overlays
- [ ] The image builds and starts against a real database
- [ ] `TenantIsolationTest` passes against RDS, not just Testcontainers — including
      `runtime role is not the table owner`
- [ ] A restore from backup is performed and verified (`P0-QA-20`)
- [ ] The legal question in ADR 0005 is answered before committing to UAE and Indonesia regions
