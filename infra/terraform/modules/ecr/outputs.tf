output "repository_urls" {
  description = "Map of repository name to its URL. Used by the build pipeline."
  value       = { for k, v in aws_ecr_repository.this : k => v.repository_url }
}

output "repository_arns" {
  description = "Needed in the build role's push policy."
  value       = { for k, v in aws_ecr_repository.this : k => v.arn }
}

output "registry_id" {
  description = "The account id owning the registry."
  value       = data.aws_caller_identity.current.account_id
}

output "kms_key_arn" {
  value = aws_kms_key.ecr.arn
}
