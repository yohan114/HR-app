output "vpc_id" {
  value = aws_vpc.this.id
}

output "vpc_cidr_block" {
  value = aws_vpc.this.cidr_block
}

output "public_subnet_ids" {
  description = "Load balancers and NAT only."
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "EKS nodes and anything that runs code."
  value       = aws_subnet.private[*].id
}

output "data_subnet_ids" {
  description = "RDS, ElastiCache, MSK, OpenSearch. No egress route."
  value       = aws_subnet.data[*].id
}

output "availability_zones" {
  value = local.azs
}

output "vpc_endpoints_security_group_id" {
  value = aws_security_group.vpc_endpoints.id
}
