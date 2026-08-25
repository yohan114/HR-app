variable "name_prefix" { type = string }

variable "environment" {
  description = <<-EOT
    Environment name. Also the GitHub environment the deploy role trusts.

    This value appears verbatim in the IAM trust condition
    `repo:<org>/<repo>:environment:<environment>`, so it MUST match the
    `environment:` declared in deploy-environment.yml. A mismatch produces an
    AssumeRole failure that reads like a credentials problem.
  EOT
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

variable "github_repository" {
  description = "In org/repo form. Scopes every trust policy to this repository."
  type        = string

  validation {
    condition     = can(regex("^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$", var.github_repository))
    error_message = "github_repository must be in org/repo form."
  }
}

variable "default_branch" {
  description = "Branch the build role trusts. A pull request cannot assume it."
  type        = string
  default     = "main"
}

variable "create_oidc_provider" {
  description = <<-EOT
    Create the GitHub OIDC provider.

    Account-wide singleton: true for the first stack in an account, false for
    the rest, or apply fails with EntityAlreadyExists.
  EOT
  type    = bool
  default = true
}

variable "existing_oidc_provider_arn" {
  description = "Required when create_oidc_provider is false."
  type        = string
  default     = null
}

variable "create_build_role" {
  description = <<-EOT
    Create the image-push role.

    Only the account owning the ECR registry needs one — production pulls the
    image the build account produced rather than building its own.
  EOT
  type    = bool
  default = false
}

variable "ecr_repository_arns" {
  description = "Repositories the build role may push to. Required when create_build_role is true."
  type        = list(string)
  default     = []
}

variable "ecr_kms_key_arn" {
  description = "Registry encryption key. Required when create_build_role is true."
  type        = string
  default     = null
}

variable "eks_cluster_name" {
  description = "Cluster the deploy role is granted admin access to. Null to skip."
  type        = string
  default     = null
}

variable "permissions_boundary_arn" {
  description = <<-EOT
    Optional permissions boundary for the deploy role.

    Caps what the role can grant to roles it creates, which is the practical
    defence against privilege escalation through Terraform-managed IAM.
    Strongly recommended in production.
  EOT
  type    = string
  default = null
}

variable "tags" {
  type    = map(string)
  default = {}
}
