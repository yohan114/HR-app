output "endpoint" {
  description = "VPC endpoint. HTTPS only."
  value       = aws_opensearch_domain.this.endpoint
}

output "domain_arn" {
  value = aws_opensearch_domain.this.arn
}

output "security_group_id" {
  value = aws_security_group.search.id
}

output "kms_key_arn" {
  value = aws_kms_key.search.arn
}
