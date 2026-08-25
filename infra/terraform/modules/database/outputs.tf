output "endpoint" {
  description = "host:port of the database."
  value       = aws_db_instance.this.endpoint
}

output "address" {
  description = "Hostname of the database."
  value       = aws_db_instance.this.address
}

output "database_name" {
  value = aws_db_instance.this.db_name
}

output "security_group_id" {
  description = "Attach to anything that needs to reach the database."
  value       = aws_security_group.database.id
}

output "kms_key_arn" {
  description = "Key encrypting the database and its secrets. Grant read access sparingly."
  value       = aws_kms_key.database.arn
}

output "owner_secret_arn" {
  description = "AWS-managed secret for the schema owner. Used by the migration job ONLY."
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
}

output "application_secret_arn" {
  description = "Secret for the non-owner application role. This is what the running app uses."
  value       = aws_secretsmanager_secret.application.arn
}
