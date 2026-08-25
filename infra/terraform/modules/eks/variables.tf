variable "name_prefix" { type = string }

variable "environment" {
  type = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be one of: dev, staging, prod."
  }
}

variable "vpc_id" { type = string }

variable "private_subnet_ids" {
  description = "Nodes run here. Never public subnets."
  type        = list(string)
}

variable "public_subnet_ids" {
  description = "For internet-facing load balancers only."
  type        = list(string)
}

variable "kubernetes_version" {
  description = "EKS control plane version. Upgrade one minor at a time."
  type        = string
  default     = "1.31"
}

variable "endpoint_public_access" {
  description = "Public API server endpoint. Keep CIDR-restricted; consider disabling for prod."
  type        = bool
  default     = true
}

variable "public_access_cidrs" {
  description = <<-EOT
    CIDRs permitted to reach the public API endpoint.

    The 0.0.0.0/0 default is for a first apply only and MUST be narrowed before
    the cluster holds real data — to the office range and the CI egress
    addresses.
  EOT
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "node_instance_types" {
  description = "Graviton by default: ~20% better price-performance for a JVM workload."
  type        = list(string)
  default     = ["m7g.large"]
}

variable "node_ami_type" {
  description = "Must match the architecture of node_instance_types."
  type        = string
  default     = "AL2023_ARM_64_STANDARD"
}

variable "node_desired_size" {
  type    = number
  default = 2
}

variable "node_min_size" {
  type    = number
  default = 2
}

variable "node_max_size" {
  type    = number
  default = 6
}

variable "node_disk_gb" {
  description = "Root volume. Sized for image layers plus ephemeral pod storage."
  type        = number
  default     = 50
}

variable "tags" {
  type    = map(string)
  default = {}
}
