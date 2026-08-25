##############################################################################
# Inputs
#
# Supply via staging.auto.tfvars (gitignored) or -var-file. Account ids are not
# committed — not secret, but reconnaissance value, and consistent with how
# `*.auto.tfvars` and the rendered kustomize overlays are handled.
##############################################################################

variable "github_repository" {
  description = <<-EOT
    The repository GitHub Actions deploys from, in org/repo form.

    Appears verbatim in every OIDC trust condition. Getting it wrong does not
    fail at apply — it fails later, when a workflow cannot assume the role, with
    an error that reads like a credentials problem.
  EOT
  type = string

  validation {
    condition     = can(regex("^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$", var.github_repository))
    error_message = "github_repository must be in org/repo form."
  }
}

variable "image_pull_account_ids" {
  description = <<-EOT
    Accounts granted pull access to the container registry.

    The production account goes here once it exists, so a single build can be
    deployed to both environments. Empty is valid — staging works on its own.
  EOT
  type    = list(string)
  default = []

  validation {
    condition     = alltrue([for id in var.image_pull_account_ids : can(regex("^[0-9]{12}$", id))])
    error_message = "Each entry must be a 12-digit AWS account id."
  }
}
