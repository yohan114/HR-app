output "bootstrap_brokers_sasl_iam" {
  description = "Bootstrap servers for IAM SASL. The only endpoint clients should use."
  value       = aws_msk_cluster.this.bootstrap_brokers_sasl_iam
}

output "cluster_arn" {
  description = "Needed in IAM policies granting topic access."
  value       = aws_msk_cluster.this.arn
}

output "security_group_id" {
  value = aws_security_group.messaging.id
}

output "kms_key_arn" {
  value = aws_kms_key.messaging.arn
}
