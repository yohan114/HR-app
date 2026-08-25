output "build_role_arn" {
  description = "Set as the AWS_BUILD_ROLE_ARN repository secret."
  value       = var.create_build_role ? aws_iam_role.build[0].arn : null
}

output "deploy_role_arn" {
  description = <<-EOT
    Set as the AWS_DEPLOY_ROLE_ARN secret on the matching GitHub *environment*
    — not as a repository-wide secret. A repository secret would be readable by
    any workflow, defeating the environment scoping in the trust policy.
  EOT
  value = aws_iam_role.deploy.arn
}

output "oidc_provider_arn" {
  description = "Pass as existing_oidc_provider_arn to other stacks in this account."
  value       = local.oidc_provider_arn
}
