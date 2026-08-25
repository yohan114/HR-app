variable "name_prefix" {
  description = "Prefix for bucket names. Must be globally unique across all of S3."
  type        = string
}

variable "environment" {
  type = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be one of: dev, staging, prod."
  }
}

variable "region" {
  description = "Used in the kms:ViaService condition."
  type        = string
}

variable "oidc_provider_arn" {
  description = "Cluster IAM OIDC provider, for IRSA."
  type        = string
}

variable "oidc_provider_url" {
  description = "OIDC issuer host, without the scheme."
  type        = string
}

variable "application_namespace" {
  description = "Namespace the backend runs in."
  type        = string
}

variable "application_service_account" {
  description = "Service account the backend uses. Named in the IRSA trust condition."
  type        = string
  default     = "hr-backend"
}

variable "tags" {
  type    = map(string)
  default = {}
}
