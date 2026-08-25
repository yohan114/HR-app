variable "name_prefix" {
  description = "Prefix for all resource names, e.g. hr-staging."
  type        = string
}

variable "environment" {
  description = "Deployment environment. Drives retention, deletion protection and apply behaviour."
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be one of: dev, staging, prod."
  }
}

variable "vpc_id" {
  description = "VPC the database lives in."
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnets for the DB subnet group. At least two AZs."
  type        = list(string)

  validation {
    condition     = length(var.private_subnet_ids) >= 2
    error_message = "RDS requires subnets in at least two availability zones."
  }
}

variable "application_security_group_id" {
  description = "Security group of the application nodes. The only permitted source of traffic."
  type        = string
}

variable "engine_version" {
  description = "PostgreSQL major.minor version."
  type        = string
  default     = "16.8"
}

variable "parameter_group_family" {
  description = "Parameter group family. Must match the engine major version."
  type        = string
  default     = "postgres16"
}

variable "instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.medium"
}

variable "allocated_storage_gb" {
  description = "Initial storage."
  type        = number
  default     = 50
}

variable "max_allocated_storage_gb" {
  description = "Ceiling for storage autoscaling. Attendance and audit tables grow steadily."
  type        = number
  default     = 500
}

variable "database_name" {
  description = "Initial database name."
  type        = string
  default     = "hr"
}

variable "owner_username" {
  description = "Schema owner. Runs Flyway migrations. The application must NOT use this role — see ADR 0002."
  type        = string
  default     = "hr_owner"
}

variable "application_username" {
  description = "Non-owner login role the application uses. Subject to row-level security."
  type        = string
  default     = "hr_app_login"
}

variable "multi_az" {
  description = "Standby in a second AZ. Required in prod; wasteful in dev."
  type        = bool
  default     = false
}

variable "backup_retention_days" {
  description = "Automated backup retention. Also bounds how far back point-in-time recovery reaches."
  type        = number
  default     = 7

  validation {
    # Zero disables automated backups AND point-in-time recovery entirely, which would make the
    # RPO target in P0-QA-20 unachievable. Never allowed, in any environment.
    condition     = var.backup_retention_days >= 1
    error_message = "backup_retention_days must be at least 1; zero disables point-in-time recovery."
  }
}

variable "deletion_protection" {
  description = "Blocks accidental deletion. Should be true anywhere holding real data."
  type        = bool
  default     = true
}

variable "tags" {
  description = "Tags applied to every resource."
  type        = map(string)
  default     = {}
}
