# Environments

Each directory is a root module composing the eight shared modules for one deployment.

| Environment | Account | Region | State key |
|---|---|---|---|
| `staging` | staging | `ap-southeast-1` | `staging/terraform.tfstate` |
| `prod` | **separate production account** | `ap-southeast-1` | `prod/terraform.tfstate` |

---

## Why production has its own AWS account

A separate account is the only boundary AWS offers that a mistake cannot cross.

An over-broad IAM policy, a `terraform destroy` in the wrong terminal, or a compromised CI
credential is contained to one account. Tag-based or IAM-condition separation within a single
account all depend on a policy being correct, and when one isn't, the failure is silent.

Things this actually costs you:

- **Account-wide singletons must be created twice.** The OpenSearch service-linked role is
  account-scoped, so `create_service_linked_role = true` in both roots. Sharing an account would
  mean setting it true in exactly one.
- **`bootstrap` runs per account.** The state bucket and lock table are not shared.
- **CI needs a separate role per account**, assumable only from the release workflow.
- **Cross-account visibility takes work.** A shared observability account, or log forwarding, has
  to be configured rather than assumed.

---

## Asymmetries between the two roots

These are not oversights — each has a reason.

| | staging | prod |
|---|---|---|
| **ECR registry** | creates it | none |
| **Build role** | yes | no |
| **Deploy role** | yes | yes |

**Production never builds an image.** It pulls the exact digest the build account produced, which
is what makes "staging tested this artefact" literally true rather than approximately true.
Rebuilding in production would mean the two environments run different bytes from the same commit.

The consequence: **the staging account owns the registry** and grants production pull access via
`image_pull_account_ids`. That is an unusual-looking dependency, and the cleaner long-term answer
is a dedicated shared-services account owning the registry and trusted by both. With two
environments that would be a third account to administer for very little gain; revisit at three.

---

## GitHub secrets to set after the first apply

| Secret | Scope | From |
|---|---|---|
| `AWS_BUILD_ROLE_ARN` | repository | staging `github_build_role_arn` |
| `AWS_DEPLOY_ROLE_ARN` | **environment `staging`** | staging `github_deploy_role_arn` |
| `AWS_DEPLOY_ROLE_ARN` | **environment `prod`** | prod `github_deploy_role_arn` |

The deploy role ARNs must be **environment** secrets, not repository secrets. A repository secret
is readable by any workflow in the repo, which would defeat the environment scoping the IAM trust
policy depends on.

### The trust condition is the access control

The deploy roles trust `repo:<org>/<repo>:environment:<name>`. Only a job that declares
`environment:` can assume them, and GitHub enforces that environment's protection rules before the
job starts.

So the reviewer requirement on the `prod` environment is not a process convention layered on top of
the permissions — **it is the permission**. Removing it does not merely skip a review; it removes
the only thing standing between a merged commit and production credentials.

Get the `sub` condition wrong and the failure is severe and silent:

| Pattern | Who can assume the role |
|---|---|
| `repo:org/repo:*` | any workflow in the repo — **including one added by a fork's pull request** |
| `repo:org/repo:ref:refs/heads/*` | anyone who can push a branch |
| `repo:org/repo:environment:prod` | only a gated job — correct |

---

## First run in a fresh account

```bash
cd infra/terraform/bootstrap && terraform init && terraform apply
```

Paste the `backend_configuration` output into the environment's `backend "s3"` block, then:

```bash
cd infra/terraform/environments/prod && cp prod.auto.tfvars.example prod.auto.tfvars
```

Fill in the account id, CIDR allowlist and repository, then:

```bash
cd infra/terraform/environments/prod && terraform init && terraform plan
```

Staging follows the same shape with `staging.auto.tfvars.example`. Do staging first: it owns the
registry that production pulls from, and its `image_pull_account_ids` needs production's account
id once that account exists.

---

## What differs between staging and prod

Everything below is deliberate. Read the reason before changing one.

| | staging | prod | Why |
|---|---|---|---|
| Availability zones | 2 | 3 | Genuine fault tolerance needs three; a third AZ in staging costs money and proves nothing |
| NAT gateways | 1 shared | 1 per AZ | A NAT is zonal — one shared gateway makes an AZ failure take down egress everywhere |
| RDS | single-AZ, `db.t4g.medium` | Multi-AZ, `db.r7g.large` | Synchronous standby; failover is 60–120s and the mobile outbox absorbs the gap |
| Backup retention | 3 days | 30 days | Payroll errors surface weeks later, when someone reads a payslip |
| Deletion protection | off | **on** | Staging must be tear-down-able; production must not be |
| Redis replicas | 1 | 2 | Holds payroll run locks — a dropped lock permits a second run and double-pays people |
| Kafka brokers | 2 | 3 | RF 3 + `min.insync.replicas` 2 survives one broker loss without losing writes. Two brokers cannot. |
| OpenSearch | 2 data nodes | 3 data + 3 dedicated masters | Keeps cluster-state management off data nodes, so a heavy directory query cannot destabilise the cluster |
| API server access | `0.0.0.0/0` (first apply only) | named CIDRs, `0.0.0.0/0` **rejected** by validation | — |
| Flow logs | REJECT only | ALL | ACCEPT records are voluminous; in production the cost is worth the forensic record |
| Account guard | none | `allowed_account_ids` | A stale `AWS_PROFILE` fails at plan time instead of proposing changes to the wrong account |

**Note the two-broker staging Kafka is explicitly not production-representative.** It cannot
reproduce a durability problem that only appears with `min.insync.replicas = 2`. If that becomes a
concern, raise staging to three brokers rather than assuming staging proves anything about it.

---

## Verification before touching real data

```bash
node infra/scripts/tf-static-check.mjs infra/terraform
```

Runs without a Terraform binary. Checks brace balance, module output references, variable
declaration and use, module argument names, and local references. It has been tested against
deliberately introduced errors, so it fails rather than passing vacuously — but it is **not** a
substitute for `terraform validate` and `terraform plan`.

Nothing in either environment has ever been planned or applied. See [../../README.md](../../README.md).
