##############################################################################
# Outputs
#
# These are the values that have to be transferred into the Kubernetes overlay
# by hand today. That handoff is the weakest part of the deployment story and
# is the next thing to automate (P0-OPS-04): a `terraform output -json` piped
# into a kustomize patch would remove the transcription step entirely.
#
# Note what is deliberately absent: no passwords, no auth tokens, no
# connection strings containing credentials. Those live in Secrets Manager and
# reach the cluster through the External Secrets Operator. An output is
# readable by anyone who can run `terraform output`, and it lands in state.
##############################################################################

output "cluster_name" {
  description = "For `aws eks update-kubeconfig`."
  value       = module.eks.cluster_name
}

output "kubeconfig_command" {
  description = "Run this to point kubectl at the cluster."
  value       = "aws eks update-kubeconfig --region ${local.region} --name ${module.eks.cluster_name}"
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
  description = "Writer endpoint. Goes into hr-backend-config.redisHost."
  value       = module.cache.primary_endpoint
}

output "kafka_bootstrap_servers" {
  description = "IAM SASL bootstrap servers."
  value       = module.messaging.bootstrap_brokers_sasl_iam
}

output "opensearch_endpoint" {
  value = module.search.endpoint
}

output "s3_buckets" {
  value = module.storage.bucket_names
}

# --- Secret locations, not secret values -----------------------------------

output "database_application_secret_arn" {
  description = "Non-owner credential the application uses. Referenced by the hr-database ExternalSecret."
  value       = module.database.application_secret_arn
}

output "database_owner_secret_arn" {
  description = "Schema owner credential. Flyway ONLY — see ADR 0002."
  value       = module.database.owner_secret_arn
}

output "redis_auth_token_secret_arn" {
  value = module.cache.auth_token_secret_arn
}

output "managed_secret_arns" {
  description = "Containers whose values must be placed by hand before first deploy."
  value       = module.secrets.managed_secret_arns
}

# --- CI/CD -----------------------------------------------------------------

output "ecr_repository_urls" {
  description = "Registry the build pipeline pushes to. Production pulls from here."
  value       = module.ecr.repository_urls
}

output "github_build_role_arn" {
  description = "Set as the AWS_BUILD_ROLE_ARN repository secret."
  value       = module.cicd.build_role_arn
}

output "github_deploy_role_arn" {
  description = <<-EOT
    Set as AWS_DEPLOY_ROLE_ARN on the GitHub *environment* named "staging" —
    not as a repository-wide secret. A repository secret is readable by any
    workflow, which would defeat the environment scoping in the trust policy.
  EOT
  value = module.cicd.deploy_role_arn
}
