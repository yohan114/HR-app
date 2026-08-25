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

variable "application_security_group_id" { type = string }

variable "application_role_arn" {
  description = "IRSA role the backend uses. The only principal granted access."
  type        = string
}

variable "create_service_linked_role" {
  description = <<-EOT
    Create the OpenSearch service-linked role.

    Account-wide singleton: set true for the first environment in an account
    and false for the rest, or apply fails with EntityAlreadyExists.
  EOT
  type    = bool
  default = false
}

variable "engine_version" {
  type    = string
  default = "OpenSearch_2.17"
}

variable "instance_type" {
  type    = string
  default = "t3.small.search"
}

variable "instance_count" {
  description = "Data nodes. Two or more enables zone awareness."
  type        = number
  default     = 2
}

variable "master_instance_type" {
  description = "Dedicated master type. Production only."
  type        = string
  default     = "t3.small.search"
}

variable "volume_size_gb" {
  type    = number
  default = 50
}

variable "tags" {
  type    = map(string)
  default = {}
}
