output "bucket_names" {
  description = "Map of logical name to actual bucket name."
  value       = { for k, v in aws_s3_bucket.this : k => v.id }
}

output "bucket_arns" {
  value = { for k, v in aws_s3_bucket.this : k => v.arn }
}

output "kms_key_arn" {
  value = aws_kms_key.storage.arn
}

output "application_role_arn" {
  description = "Annotate the backend service account with this."
  value       = aws_iam_role.application.arn
}
