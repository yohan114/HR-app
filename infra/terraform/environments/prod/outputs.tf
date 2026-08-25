##############################################################################
# Outputs
#
# Identical in shape to staging's, so the same automation consumes both.
#
# Nothing secret is emitted: no passwords, no auth tokens, no connection
# strings containing credentials. Those live in Secrets Manager and reach the
# cluster through the External Secrets Operator. An output is readable by
# anyone who can run `terraform output`, and it is stored in state.
##############################################################################

output "cluster_name" {
  value = module.eks.cluster_name
}

output "kubeconfig_command" {
  value = "aws eks update-kubeconfig --region ${local.region} --name ${module.eks.cluster_name}"
}

output "vpc_id" {
  value = module.network.vpc_id
}

# --- Values needed by the kustomize overlay --------------------------------

output "backend_service_account_role_arn" {
  description = "Annotate the hr-backend service account with this (eks.amazonaws.com/role-arn)."
  value       = module.storage.application_role_arn
}

output "external_secrets_role_arn" {
  description = "Annotate the external-secrets service account with this."
  value       = module.secrets.external_secrets_role_arn
}

output "redis_host" {
  description = "Writer endpoint. Locks and rate limiting must use this, not the reader."
  value       = module.cache.primary_endpoint
}

output "redis_reader_host" {
  description = "Reader endpoint. Only for cache reads that tolerate replica lag."
  value       = module.cache.reader_endpoint
}

output "kafka_bootstrap_servers" {
  value = module.messaging.bootstrap_brokers_sasl_iam
}

output "opensearch_endpoint" {
  value = module.search.endpoint
}

output "s3_buckets" {
  value = module.storage.bucket_names
}

# --- Secret locations, not secret values -----------------------------------

output "database_application_secret_arn" {
  description = "Non-owner credential the application uses. Subject to row-level security."
  value       = module.database.application_secret_arn
}

output "database_owner_secret_arn" {
  description = "Schema owner. Flyway ONLY — the application must never use this. See ADR 0002."
  value       = module.database.owner_secret_arn
}

output "redis_auth_token_secret_arn" {
  value = module.cache.auth_token_secret_arn
}

output "managed_secret_arns" {
  description = "Containers whose values must be placed by hand before the first deploy."
  value       = module.secrets.managed_secret_arns
}

# --- Disaster recovery ------------------------------------------------------

output "database_endpoint" {
  description = "Needed when restoring: the target a recovered snapshot is compared against."
  value       = module.database.endpoint
}

output "kms_key_arns" {
  description = <<-EOT
    Every KMS key protecting production data.

    Recorded as an output because a cross-region restore or an account
    recovery needs them, and hunting for key ARNs during an incident is time
    nobody has.
  EOT
  value = {
    database  = module.database.kms_key_arn
    cache     = module.cache.kms_key_arn
    messaging = module.messaging.kms_key_arn
    search    = module.search.kms_key_arn
    storage   = module.storage.kms_key_arn
    secrets   = module.secrets.kms_key_arn
    cluster   = module.eks.kms_key_arn
  }
}

# --- CI/CD -----------------------------------------------------------------

output "github_deploy_role_arn" {
  description = <<-EOT
    Set as AWS_DEPLOY_ROLE_ARN on the GitHub environment named "prod".

    That environment must carry protection rules requiring a reviewer — the IAM
    trust condition keys on `environment:prod`, so the approval gate IS the
    access control, not merely a process convention.
  EOT
  value = module.cicd.deploy_role_arn
}

output "aws_account_id" {
  description = "Confirms which account this state manages."
  value       = var.aws_account_id
}
