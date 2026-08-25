variable "name_prefix" {
  description = "Prefix for the KMS alias and resource names."
  type        = string
}

variable "region" {
  description = "Used in the kms:ViaService condition for cross-account decrypt."
  type        = string
}

variable "repository_names" {
  description = "Repositories to create. One per deployable artefact."
  type        = list(string)
  default     = ["hr-backend"]
}

variable "retained_image_count" {
  description = <<-EOT
    Tagged images kept before the oldest are expired.

    Generous on purpose: an image that has been expired cannot be rolled back
    to, and the storage cost of a few dozen images is trivial next to being
    unable to revert a bad release.
  EOT
  type    = number
  default = 50
}

variable "pull_account_ids" {
  description = <<-EOT
    AWS account ids granted pull access.

    The production account goes here, so a single build can be deployed to both
    environments. Pull only — images are produced in exactly one place.
  EOT
  type    = list(string)
  default = []

  validation {
    condition     = alltrue([for id in var.pull_account_ids : can(regex("^[0-9]{12}$", id))])
    error_message = "Each entry must be a 12-digit AWS account id."
  }
}

variable "tags" {
  type    = map(string)
  default = {}
}
