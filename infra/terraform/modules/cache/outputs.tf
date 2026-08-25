output "primary_endpoint" {
  description = "Writer endpoint. Locks and rate limiting must use this, not the reader."
  value       = aws_elasticache_replication_group.this.primary_endpoint_address
}

output "reader_endpoint" {
  description = "Read-only endpoint. Safe for cache reads that tolerate replica lag."
  value       = aws_elasticache_replication_group.this.reader_endpoint_address
}

output "security_group_id" {
  value = aws_security_group.cache.id
}

output "auth_token_secret_arn" {
  description = "Secrets Manager ARN. The token itself is deliberately not an output."
  value       = aws_secretsmanager_secret.auth_token.arn
}

output "kms_key_arn" {
  value = aws_kms_key.cache.arn
}
