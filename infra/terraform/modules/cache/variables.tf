variable "name_prefix" { type = string }

variable "environment" {
  type = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be one of: dev, staging, prod."
  }
}

variable "vpc_id" { type = string }

variable "data_subnet_ids" {
  description = "Data-tier subnets. No egress route, by design."
  type        = list(string)
}

variable "application_security_group_id" {
  description = "The only permitted source of traffic."
  type        = string
}

variable "secrets_kms_key_arn" {
  description = "Key encrypting the auth-token secret. From the secrets module."
  type        = string
}

variable "engine_version" {
  type    = string
  default = "7.1"
}

variable "parameter_group_family" {
  description = "Must match the engine major version."
  type        = string
  default     = "redis7"
}

variable "node_type" {
  type    = string
  default = "cache.t4g.micro"
}

variable "replica_count" {
  description = <<-EOT
    Read replicas. At least one in prod: automatic failover requires it, and
    without failover a node loss drops every held payroll lock.
  EOT
  type    = number
  default = 1
}

variable "snapshot_retention_days" {
  description = "Zero disables snapshots. Acceptable for a pure cache; this is not one."
  type        = number
  default     = 3
}

variable "tags" {
  type    = map(string)
  default = {}
}
