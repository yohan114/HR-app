##############################################################################
# Staging environment (P0-OPS-07)
#
# Composes the modules into a working estate. Deliberately production-shaped
# rather than a toy: a staging environment that cannot reproduce a production
# problem is not much of a staging environment. It runs smaller — fewer AZs,
# one NAT, no dedicated OpenSearch masters — but every security control is
# identical, because those are exactly what you want rehearsed before prod.
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

  # Populated from the bootstrap module's `backend_configuration` output.
  # Left commented so the first `terraform init` in a fresh account works
  # before the backend exists.
  #
  # backend "s3" {
  #   bucket         = "hr-terraform-state-<account-id>"
  #   key            = "staging/terraform.tfstate"
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
}

locals {
  environment = "staging"
  region      = "ap-southeast-1"
  name_prefix = "hr-staging"

  # Namespace the backend runs in. Must match the kustomize overlay.
  application_namespace = "hr-staging"

  common_tags = {
    Project     = "hr-platform"
    Environment = local.environment
    ManagedBy   = "terraform"
    # Attributed so unowned resources are findable when the bill is reviewed.
    Owner = "platform"
  }
}

# ---------------------------------------------------------------------------
# Network
# ---------------------------------------------------------------------------
module "network" {
  source = "../../modules/network"

  name_prefix = local.name_prefix
  environment = local.environment
  region      = local.region

  # A distinct range per environment and region, so VPCs can be peered later
  # without overlapping. staging = 10.10, prod = 10.0.
  cidr_block = "10.10.0.0/16"

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

  # TODO(P0-OPS-07): narrow to the office range and CI egress addresses before
  # this cluster holds anything real. Left open for the first apply only.
  public_access_cidrs = ["0.0.0.0/0"]

  node_instance_types = ["m7g.large"]
  node_desired_size   = 2
  node_min_size       = 2
  node_max_size       = 4

  tags = local.common_tags
}

# ---------------------------------------------------------------------------
# Data stores
#
# All four take the EKS cluster security group as their only ingress source.
# Nothing outside the cluster can reach them, and there is no bastion path —
# administrative access goes via SSM Session Manager onto a node.
# ---------------------------------------------------------------------------
module "database" {
  source = "../../modules/database"

  name_prefix = local.name_prefix
  environment = local.environment

  vpc_id                        = module.network.vpc_id
  private_subnet_ids            = module.network.data_subnet_ids
  application_security_group_id = module.eks.cluster_security_group_id

  instance_class       = "db.t4g.medium"
  allocated_storage_gb = 50
  multi_az             = false

  # Shorter than prod, but never zero — that would disable point-in-time
  # recovery and make the DR drill in P0-QA-20 impossible to rehearse here.
  backup_retention_days = 3

  # False in staging so the environment can actually be torn down. Must be
  # true in prod.
  deletion_protection = false

  tags = local.common_tags
}

module "cache" {
  source = "../../modules/cache"

  name_prefix = local.name_prefix
  environment = local.environment

  vpc_id                        = module.network.vpc_id
  data_subnet_ids               = module.network.data_subnet_ids
  application_security_group_id = module.eks.cluster_security_group_id
  secrets_kms_key_arn           = module.secrets.kms_key_arn

  node_type     = "cache.t4g.micro"
  replica_count = 1

  tags = local.common_tags
}

module "messaging" {
  source = "../../modules/messaging"

  name_prefix = local.name_prefix
  environment = local.environment

  vpc_id                        = module.network.vpc_id
  data_subnet_ids               = module.network.data_subnet_ids
  application_security_group_id = module.eks.cluster_security_group_id

  broker_count         = 2
  broker_instance_type = "kafka.t3.small"
  broker_storage_gb    = 50

  tags = local.common_tags
}

module "search" {
  source = "../../modules/search"

  name_prefix = local.name_prefix
  environment = local.environment

  vpc_id                        = module.network.vpc_id
  data_subnet_ids               = module.network.data_subnet_ids
  application_security_group_id = module.eks.cluster_security_group_id
  application_role_arn          = module.storage.application_role_arn

  # First environment in the account creates the account-wide service-linked
  # role. Set false for every environment after this one.
  create_service_linked_role = true

  instance_type  = "t3.small.search"
  instance_count = 2

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

  # The External Secrets Operator needs decrypt access to the database
  # credential secret, which is encrypted with the database module's key
  # rather than the secrets module's own.
  database_kms_key_arn = module.database.kms_key_arn

  tags = local.common_tags
}

# ---------------------------------------------------------------------------
# Container registry
#
# Created HERE, not in production. The pipeline builds an image once and
# deploys that exact digest to both environments; rebuilding per environment
# would mean staging and production run different bytes. That requires one
# registry both accounts can pull from, and the staging account doubles as the
# build account.
#
# See the header of modules/ecr for why a dedicated shared-services account is
# the cleaner long-term answer, and why it is not worth a third account today.
# ---------------------------------------------------------------------------
module "ecr" {
  source = "../../modules/ecr"

  name_prefix = "hr"
  region      = local.region

  repository_names = ["hr-backend"]

  # Production pulls from here. Empty until the production account exists —
  # supply it via the tfvars file rather than committing an account id.
  pull_account_ids = var.image_pull_account_ids

  tags = local.common_tags
}

# ---------------------------------------------------------------------------
# GitHub Actions access
#
# This account owns the registry, so it holds the build role as well as the
# staging deploy role.
# ---------------------------------------------------------------------------
module "cicd" {
  source = "../../modules/cicd"

  name_prefix       = local.name_prefix
  environment       = local.environment
  region            = local.region
  github_repository = var.github_repository

  # First (and currently only) stack in this account, so it creates the
  # account-wide OIDC provider.
  create_oidc_provider = true

  create_build_role   = true
  ecr_repository_arns = values(module.ecr.repository_arns)
  ecr_kms_key_arn     = module.ecr.kms_key_arn

  eks_cluster_name = module.eks.cluster_name

  tags = local.common_tags
}
