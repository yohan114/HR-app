##############################################################################
# Production environment
#
# Composes the same eight modules as staging, sized and configured for real
# data. Every difference from staging below is deliberate; read the comments
# before changing one.
#
# ## Account model
#
# **Production lives in its own AWS account**, separate from staging.
#
# Not a preference. A separate account is the only boundary AWS offers that a
# mistake cannot cross: an over-broad IAM policy, a `terraform destroy` run in
# the wrong terminal, or a compromised CI credential is contained to one
# account. Tag-based or IAM-condition separation within a single account all
# rely on a policy being correct, and the failure mode is silent.
#
# Consequences you will actually hit:
#   - Account-wide singletons (the OpenSearch service-linked role) must be
#     created here too, not just in staging.
#   - The Terraform state bucket is per-account, so `bootstrap` runs again.
#   - CI needs a separate role, assumable only from the release workflow.
#
#   terraform init && terraform plan
##############################################################################

terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.80"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # Populated from this account's bootstrap output. Uncommented once the
  # backend exists — the first `init` in a fresh account has nowhere to store
  # state yet.
  #
  # backend "s3" {
  #   bucket         = "hr-terraform-state-<prod-account-id>"
  #   key            = "prod/terraform.tfstate"
  #   region         = "ap-southeast-1"
  #   dynamodb_table = "terraform-state-lock"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = local.region

  default_tags {
    tags = local.common_tags
  }

  # Refuses to run against any account but the intended one. Cheap insurance
  # against the classic incident: a stale AWS_PROFILE and a plan that would
  # have replaced production's database.
  allowed_account_ids = [var.aws_account_id]
}

locals {
  environment = "prod"
  region      = "ap-southeast-1"
  name_prefix = "hr-prod"

  application_namespace = "hr-prod"

  common_tags = {
    Project     = "hr-platform"
    Environment = local.environment
    ManagedBy   = "terraform"
    Owner       = "platform"
    # Marks everything here as holding real personal data, so cost and
    # compliance tooling can find it without guessing from names.
    DataClassification = "confidential"
  }
}

# ---------------------------------------------------------------------------
# Network
#
# environment = "prod" makes the module use three AZs and a NAT gateway per AZ.
# A NAT is zonal: one shared gateway means an AZ failure takes egress down for
# every AZ, which defeats the point of spreading across them.
# ---------------------------------------------------------------------------
module "network" {
  source = "../../modules/network"

  name_prefix = local.name_prefix
  environment = local.environment
  region      = local.region

  # 10.0 for prod, 10.10 for staging. Distinct ranges so the VPCs can be peered
  # later — for a migration or a shared observability stack — without
  # overlapping.
  cidr_block = "10.0.0.0/16"

  tags = local.common_tags
}

# ---------------------------------------------------------------------------
# Cluster
# ---------------------------------------------------------------------------
module "eks" {
  source = "../../modules/eks"

  name_prefix = local.name_prefix
  environment = local.environment

  vpc_id             = module.network.vpc_id
  private_subnet_ids = module.network.private_subnet_ids
  public_subnet_ids  = module.network.public_subnet_ids

  kubernetes_version = "1.31"

  # The API server endpoint is reachable only from named CIDRs — no default,
  # so an empty list is a plan-time failure rather than an accidentally open
  # control plane. Fully private is stronger still and needs a VPN or an
  # in-VPC runner; see the note in environments/README.md.
  endpoint_public_access = true
  public_access_cidrs    = var.api_server_allowed_cidrs

  node_instance_types = ["m7g.xlarge"]
  node_desired_size   = 3
  node_min_size       = 3
  node_max_size       = 12

  tags = local.common_tags
}

# ---------------------------------------------------------------------------
# Database
# ---------------------------------------------------------------------------
module "database" {
  source = "../../modules/database"

  name_prefix = local.name_prefix
  environment = local.environment

  vpc_id                        = module.network.vpc_id
  private_subnet_ids            = module.network.data_subnet_ids
  application_security_group_id = module.eks.cluster_security_group_id

  instance_class       = "db.r7g.large"
  allocated_storage_gb = 200
  # Attendance and audit tables grow steadily and are partitioned monthly;
  # headroom here avoids an emergency resize during a busy month.
  max_allocated_storage_gb = 2000

  # A synchronous standby in a second AZ. Failover is automatic and takes
  # 60–120 seconds — the application's connection pool reconnects and the
  # outbox on every mobile client absorbs the gap.
  multi_az = true

  # 30 days of point-in-time recovery. Payroll errors are frequently discovered
  # weeks later, when someone reads a payslip — recovering to the moment before
  # a bad run needs more than a week of history.
  backup_retention_days = 30

  deletion_protection = true

  tags = local.common_tags
}

# ---------------------------------------------------------------------------
# Cache
# ---------------------------------------------------------------------------
module "cache" {
  source = "../../modules/cache"

  name_prefix = local.name_prefix
  environment = local.environment

  vpc_id                        = module.network.vpc_id
  data_subnet_ids               = module.network.data_subnet_ids
  application_security_group_id = module.eks.cluster_security_group_id
  secrets_kms_key_arn           = module.secrets.kms_key_arn

  node_type = "cache.r7g.large"
  # Two replicas across AZs. This holds payroll run locks, so a node loss
  # without failover would drop a lock mid-run and permit a second run to
  # start — the failure that double-pays people.
  replica_count           = 2
  snapshot_retention_days = 7

  tags = local.common_tags
}

# ---------------------------------------------------------------------------
# Messaging
# ---------------------------------------------------------------------------
module "messaging" {
  source = "../../modules/messaging"

  name_prefix = local.name_prefix
  environment = local.environment

  vpc_id                        = module.network.vpc_id
  data_subnet_ids               = module.network.data_subnet_ids
  application_security_group_id = module.eks.cluster_security_group_id

  # Three brokers is the minimum that gives a real durability guarantee: with
  # replication factor 3 and min.insync.replicas 2, losing one broker does not
  # lose writes. Two brokers cannot offer that, which is why staging's
  # configuration is explicitly not production-representative here.
  broker_count          = 3
  broker_instance_type  = "kafka.m7g.large"
  broker_storage_gb     = 200
  broker_storage_max_gb = 2000

  tags = local.common_tags
}

# ---------------------------------------------------------------------------
# Search
# ---------------------------------------------------------------------------
module "search" {
  source = "../../modules/search"

  name_prefix = local.name_prefix
  environment = local.environment

  vpc_id                        = module.network.vpc_id
  data_subnet_ids               = module.network.data_subnet_ids
  application_security_group_id = module.eks.cluster_security_group_id
  application_role_arn          = module.storage.application_role_arn

  # True because this is a different AWS account from staging, and the
  # OpenSearch service-linked role is account-scoped. If prod ever shares an
  # account with another environment, set this false in exactly one of them.
  create_service_linked_role = true

  # environment = "prod" additionally enables three dedicated master nodes, so
  # cluster-state management stays off the data nodes and a heavy directory
  # query cannot destabilise the cluster itself.
  instance_type        = "r7g.large.search"
  instance_count       = 3
  master_instance_type = "m7g.medium.search"
  volume_size_gb       = 200

  tags = local.common_tags
}

# ---------------------------------------------------------------------------
# Storage and secrets
# ---------------------------------------------------------------------------
module "storage" {
  source = "../../modules/storage"

  name_prefix = local.name_prefix
  environment = local.environment
  region      = local.region

  oidc_provider_arn     = module.eks.oidc_provider_arn
  oidc_provider_url     = module.eks.oidc_provider_url
  application_namespace = local.application_namespace

  tags = local.common_tags
}

module "secrets" {
  source = "../../modules/secrets"

  name_prefix = local.name_prefix
  environment = local.environment
  region      = local.region

  oidc_provider_arn = module.eks.oidc_provider_arn
  oidc_provider_url = module.eks.oidc_provider_url

  database_kms_key_arn = module.database.kms_key_arn

  tags = local.common_tags
}

# ---------------------------------------------------------------------------
# GitHub Actions access
#
# Deploy role only. There is deliberately no build role and no registry here:
# production pulls the exact image the build account produced, so nothing is
# ever built in this account. That is what makes "staging tested this artefact"
# a true statement rather than an approximation.
#
# The trust condition is `repo:<org>/<repo>:environment:prod`, so only a job
# declaring `environment: prod` can assume it — and that environment carries
# GitHub protection rules requiring a reviewer.
# ---------------------------------------------------------------------------
module "cicd" {
  source = "../../modules/cicd"

  name_prefix       = local.name_prefix
  environment       = local.environment
  region            = local.region
  github_repository = var.github_repository

  # Separate AWS account, so this account needs its own OIDC provider.
  create_oidc_provider = true
  create_build_role    = false

  eks_cluster_name = module.eks.cluster_name

  # Caps what the deploy role can grant to roles it creates. The practical
  # defence against privilege escalation via Terraform-managed IAM, and worth
  # having in production even though it is optional elsewhere.
  permissions_boundary_arn = var.deploy_permissions_boundary_arn

  tags = local.common_tags
}
