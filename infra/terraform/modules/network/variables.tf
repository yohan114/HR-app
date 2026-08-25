variable "name_prefix" {
  description = "Prefix for all resource names, e.g. hr-staging."
  type        = string
}

variable "environment" {
  description = "Drives AZ count, NAT redundancy, flow-log verbosity and retention."
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be one of: dev, staging, prod."
  }
}

variable "region" {
  description = "AWS region. Used to construct VPC endpoint service names."
  type        = string
}

variable "cidr_block" {
  description = <<-EOT
    VPC CIDR. A /16 gives room for /20 subnets across three tiers and three AZs.

    Size this generously: the VPC CNI assigns a real VPC address to every pod,
    so address consumption tracks pod count rather than node count. Subnet
    CIDRs cannot be resized after creation.

    Use a distinct range per region so the VPCs can be peered later without
    overlapping.
  EOT
  type        = string
  default     = "10.0.0.0/16"

  validation {
    condition     = can(cidrnetmask(var.cidr_block)) && tonumber(split("/", var.cidr_block)[1]) <= 16
    error_message = "cidr_block must be valid and no smaller than a /16."
  }
}

variable "tags" {
  description = "Tags applied to every resource."
  type        = map(string)
  default     = {}
}
