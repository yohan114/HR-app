##############################################################################
# Inputs that must be supplied explicitly.
#
# None of these have defaults. That is the point: a default here would be a
# guess about production, and a wrong guess is either an outage or an open
# control plane. An unset variable fails at plan time, which is the cheapest
# possible moment to find out.
#
# Supply via prod.auto.tfvars (not committed — see .gitignore) or -var-file.
##############################################################################

variable "aws_account_id" {
  description = <<-EOT
    The production AWS account id.

    Enforced by the provider's allowed_account_ids, so a plan run with a stale
    AWS_PROFILE fails immediately instead of proposing changes to the wrong
    account. This is the cheap guard against the classic incident.
  EOT
  type = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id must be a 12-digit AWS account id."
  }
}

variable "api_server_allowed_cidrs" {
  description = <<-EOT
    CIDRs permitted to reach the Kubernetes API server endpoint.

    Office egress and CI runner addresses only. Deliberately no default:
    0.0.0.0/0 on a production control plane is the kind of thing that gets
    inherited from a template and never revisited.
  EOT
  type = list(string)

  validation {
    condition     = length(var.api_server_allowed_cidrs) > 0
    error_message = "At least one CIDR must be specified; an empty list would lock everyone out."
  }

  validation {
    condition     = !contains(var.api_server_allowed_cidrs, "0.0.0.0/0")
    error_message = "0.0.0.0/0 is not permitted for the production API server. Narrow to office and CI ranges."
  }
}

variable "github_repository" {
  description = <<-EOT
    The repository GitHub Actions deploys from, in org/repo form.

    Appears verbatim in the OIDC trust condition. A mismatch does not fail at
    apply — it fails when a workflow cannot assume the role, with an error that
    reads like a credentials problem.
  EOT
  type = string

  validation {
    condition     = can(regex("^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$", var.github_repository))
    error_message = "github_repository must be in org/repo form."
  }
}

variable "deploy_permissions_boundary_arn" {
  description = <<-EOT
    Permissions boundary for the GitHub deploy role.

    Caps what that role can grant to roles it creates via Terraform, which is
    the practical defence against privilege escalation through Terraform-managed
    IAM. Null is permitted so the first apply can bootstrap, but it should be
    set before production holds real data.
  EOT
  type    = string
  default = null
}
