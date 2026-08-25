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
  description = "Data-tier subnets. Must contain at least broker_count entries."
  type        = list(string)
}

variable "application_security_group_id" { type = string }

variable "kafka_version" {
  type    = string
  default = "3.7.x"
}

variable "broker_count" {
  description = <<-EOT
    Broker nodes. Must be a multiple of the AZ count.

    Three in production: with replication factor 3 and min.insync.replicas 2,
    the cluster survives losing one broker without losing writes. Two brokers
    cannot give that guarantee.
  EOT
  type    = number
  default = 2

  validation {
    condition     = var.broker_count >= 2
    error_message = "MSK requires at least two brokers."
  }
}

variable "broker_instance_type" {
  type    = string
  default = "kafka.t3.small"
}

variable "broker_storage_gb" {
  type    = number
  default = 100
}

variable "broker_storage_max_gb" {
  description = "Autoscaling ceiling. Grows at 70% utilisation."
  type        = number
  default     = 500
}

variable "tags" {
  type    = map(string)
  default = {}
}
