##############################################################################
# GitHub Actions OIDC access
#
# Replaces long-lived AWS access keys stored as GitHub secrets. GitHub mints a
# short-lived OIDC token per job; AWS exchanges it for temporary credentials.
# Nothing durable exists to leak.
#
# ## The part that matters: the `sub` condition
#
# `sub` is what scopes the trust. Get it wrong and the consequences are severe
# and silent:
#
#   repo:org/repo:*                  ANY workflow in the repo — including one
#                                    added by a pull request from a fork.
#   repo:org/repo:ref:refs/heads/*   Any branch, so a pushed branch can deploy.
#   repo:org/repo:environment:prod   Only a job that declared
#                                    `environment: prod`, which GitHub gates
#                                    behind the environment's protection rules.
#
# This module uses the environment form. That is why `deploy-environment.yml`
# declares `environment:` — it is not decoration, it is the thing the IAM trust
# policy keys on.
##############################################################################

locals {
  common_tags = merge(var.tags, {
    Component = "cicd"
    Module    = "cicd"
  })

  github_host = "token.actions.githubusercontent.com"
}

data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}

# ---------------------------------------------------------------------------
# OIDC provider
#
# Account-wide singleton. Set `create_oidc_provider = false` if another stack
# in this account already created it, or apply fails with EntityAlreadyExists.
# ---------------------------------------------------------------------------
resource "aws_iam_openid_connect_provider" "github" {
  count = var.create_oidc_provider ? 1 : 0

  url            = "https://${local.github_host}"
  client_id_list = ["sts.amazonaws.com"]

  # AWS has verified GitHub's certificate chain natively since 2023, so this
  # value is no longer used for validation. It remains a required argument.
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]

  tags = local.common_tags
}

locals {
  oidc_provider_arn = var.create_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : var.existing_oidc_provider_arn
}

# ---------------------------------------------------------------------------
# Build role — push images
#
# Trusted from the default branch only. A pull request cannot assume it, so a
# fork cannot push an image into the registry production pulls from.
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "build_trust" {
  count = var.create_build_role ? 1 : 0

  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.github_host}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.github_host}:sub"
      values = [
        "repo:${var.github_repository}:ref:refs/heads/${var.default_branch}",
      ]
    }
  }
}

data "aws_iam_policy_document" "build" {
  count = var.create_build_role ? 1 : 0

  statement {
    sid       = "AuthenticateToRegistry"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"] # This action does not support resource scoping.
  }

  statement {
    sid    = "PushImages"
    effect = "Allow"

    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
      "ecr:DescribeImages",
    ]

    resources = var.ecr_repository_arns
  }

  # Deliberately absent: ecr:DeleteRepository, ecr:BatchDeleteImage,
  # ecr:PutImageTagMutability. The build pipeline adds images; it does not
  # remove them or weaken the immutability guarantee.

  statement {
    sid    = "EncryptImages"
    effect = "Allow"

    actions   = ["kms:Decrypt", "kms:GenerateDataKey", "kms:DescribeKey"]
    resources = [var.ecr_kms_key_arn]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ecr.${var.region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "build" {
  count = var.create_build_role ? 1 : 0

  name               = "${var.name_prefix}-github-build"
  description        = "Pushes container images from GitHub Actions on ${var.default_branch}"
  assume_role_policy = data.aws_iam_policy_document.build_trust[0].json

  # An hour is ample for a build and push. The default is also an hour, but
  # stating it makes the intent explicit rather than inherited.
  max_session_duration = 3600

  tags = local.common_tags
}

resource "aws_iam_role_policy" "build" {
  count = var.create_build_role ? 1 : 0

  name   = "push-images"
  role   = aws_iam_role.build[0].id
  policy = data.aws_iam_policy_document.build[0].json
}

# ---------------------------------------------------------------------------
# Deploy role — Terraform and Kubernetes
#
# Scoped to a GitHub *environment*, so production requires whatever protection
# rules that environment carries (typically a reviewer).
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "deploy_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.github_host}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.github_host}:sub"
      values   = ["repo:${var.github_repository}:environment:${var.environment}"]
    }
  }
}

# ---------------------------------------------------------------------------
# Deploy permissions
#
# Broad by necessity: Terraform manages VPCs, clusters, databases and IAM, and
# an accurate least-privilege policy for that is effectively "administrator
# minus a few things". Rather than pretend otherwise with a policy that has to
# be widened on every apply, the boundary is drawn elsewhere:
#
#   - The role is assumable only from a job in this repo declaring this
#     environment, which for production means a human approved it.
#   - Production is a separate AWS account, so the blast radius is one
#     environment.
#   - A permissions boundary (var.permissions_boundary_arn) can cap what the
#     role may grant to roles it creates, preventing privilege escalation
#     through Terraform-managed IAM.
#
# The explicit denies below are the ones worth having: they stop the pipeline
# doing the specific things nothing in a deploy should ever need to do.
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "deploy_guardrails" {
  statement {
    sid    = "DenyDestructiveDataActions"
    effect = "Deny"

    actions = [
      # Deleting the state bucket or the audit trail would remove the record
      # of what happened, which is exactly what a compromised pipeline would
      # want to do.
      "s3:DeleteBucket",
      "cloudtrail:StopLogging",
      "cloudtrail:DeleteTrail",
      # Scheduling key deletion makes every encrypted object unrecoverable
      # after the waiting period, silently.
      "kms:ScheduleKeyDeletion",
      "kms:DisableKey",
      # Turning off backups is how a recoverable incident becomes a permanent
      # one.
      "rds:DeleteDBClusterSnapshot",
      "rds:DeleteDBSnapshot",
      "dynamodb:DeleteTable",
    ]

    resources = ["*"]
  }

  statement {
    sid       = "DenyIdentityProviderTampering"
    effect    = "Deny"
    actions   = ["iam:DeleteOpenIDConnectProvider", "iam:UpdateOpenIDConnectProviderThumbprint"]
    resources = ["*"]
  }
}

resource "aws_iam_role" "deploy" {
  name               = "${var.name_prefix}-github-deploy"
  description        = "Terraform and Kubernetes deploys from GitHub Actions (${var.environment})"
  assume_role_policy = data.aws_iam_policy_document.deploy_trust.json

  permissions_boundary = var.permissions_boundary_arn

  # A Terraform apply that creates a cluster genuinely takes longer than an
  # hour. Shorter would fail mid-apply and leave state locked.
  max_session_duration = 7200

  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "deploy_admin" {
  role = aws_iam_role.deploy.name
  # See the block comment above for why this is broad and where the boundary
  # actually sits.
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/PowerUserAccess"
}

resource "aws_iam_role_policy_attachment" "deploy_iam" {
  role       = aws_iam_role.deploy.name
  policy_arn = aws_iam_policy.deploy_iam.arn
}

# PowerUserAccess excludes IAM, which Terraform needs for IRSA roles. Granted
# separately so the addition is visible rather than buried in an AWS-managed
# policy.
resource "aws_iam_policy" "deploy_iam" {
  name        = "${var.name_prefix}-github-deploy-iam"
  description = "IAM management for Terraform-created IRSA roles"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "iam:CreateRole", "iam:DeleteRole", "iam:GetRole", "iam:UpdateRole",
        "iam:TagRole", "iam:UntagRole", "iam:ListRoleTags",
        "iam:PutRolePolicy", "iam:DeleteRolePolicy", "iam:GetRolePolicy", "iam:ListRolePolicies",
        "iam:AttachRolePolicy", "iam:DetachRolePolicy", "iam:ListAttachedRolePolicies",
        "iam:CreatePolicy", "iam:DeletePolicy", "iam:GetPolicy", "iam:ListPolicyVersions",
        "iam:CreatePolicyVersion", "iam:DeletePolicyVersion", "iam:GetPolicyVersion",
        "iam:CreateServiceLinkedRole", "iam:PassRole",
        "iam:CreateOpenIDConnectProvider", "iam:GetOpenIDConnectProvider",
        "iam:TagOpenIDConnectProvider",
      ]
      Resource = "*"
    }]
  })

  tags = local.common_tags
}

resource "aws_iam_role_policy" "deploy_guardrails" {
  name   = "guardrails"
  role   = aws_iam_role.deploy.id
  policy = data.aws_iam_policy_document.deploy_guardrails.json
}

# ---------------------------------------------------------------------------
# Cluster access
#
# EKS access entries rather than the aws-auth ConfigMap: an entry is a real AWS
# resource with an audit trail, and a bad edit cannot lock everyone out of the
# cluster the way a malformed ConfigMap can.
# ---------------------------------------------------------------------------
resource "aws_eks_access_entry" "deploy" {
  count = var.eks_cluster_name != null ? 1 : 0

  cluster_name  = var.eks_cluster_name
  principal_arn = aws_iam_role.deploy.arn
  type          = "STANDARD"

  tags = local.common_tags
}

resource "aws_eks_access_policy_association" "deploy" {
  count = var.eks_cluster_name != null ? 1 : 0

  cluster_name  = var.eks_cluster_name
  principal_arn = aws_iam_role.deploy.arn
  policy_arn    = "arn:${data.aws_partition.current.partition}:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"

  access_scope {
    type = "cluster"
  }

  depends_on = [aws_eks_access_entry.deploy]
}
