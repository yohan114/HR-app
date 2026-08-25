##############################################################################
# ECR
#
# ## One registry, not one per account
#
# The deploy pipeline builds an image once and deploys that exact digest to
# both environments — rebuilding per environment would mean staging and
# production run different bytes, which defeats the point of having a staging
# environment. That requires a single registry both accounts can pull from.
#
# So this module is instantiated in the staging account only, which doubles as
# the build account, and grants cross-account pull to production via
# `pull_account_ids`. Production's root module does not create a registry.
#
# The cleaner long-term arrangement is a dedicated shared-services account that
# owns the registry and is trusted by both environments. That is worth doing
# when there are more than two environments; with two it would be a third
# account to administer for very little gain.
##############################################################################

locals {
  common_tags = merge(var.tags, {
    Component = "ecr"
    Module    = "ecr"
  })
}

data "aws_caller_identity" "current" {}

resource "aws_kms_key" "ecr" {
  description             = "Encryption for ${var.name_prefix} container images"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = local.common_tags
}

resource "aws_kms_alias" "ecr" {
  name          = "alias/${var.name_prefix}-ecr"
  target_key_id = aws_kms_key.ecr.key_id
}

resource "aws_ecr_repository" "this" {
  for_each = toset(var.repository_names)

  name = each.value

  # Immutable tags. A tag that can be overwritten makes "roll back to the build
  # from Tuesday" meaningless, and lets a compromised CI credential replace the
  # image behind a tag production is already running. The pipeline tags by
  # commit SHA and deploys by digest, so nothing legitimate needs to re-push a
  # tag.
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "KMS"
    kms_key         = aws_kms_key.ecr.arn
  }

  tags = merge(local.common_tags, { Name = each.value })
}

# ---------------------------------------------------------------------------
# Lifecycle
#
# Untagged images accumulate from every multi-arch build and every overwritten
# manifest, are invisible in the console's default view, and are billed the
# same as anything else.
# ---------------------------------------------------------------------------
resource "aws_ecr_lifecycle_policy" "this" {
  for_each = aws_ecr_repository.this

  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after 1 day"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 1
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Keep the most recent ${var.retained_image_count} tagged images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.retained_image_count
        }
        action = { type = "expire" }
      },
    ]
  })
}

# ---------------------------------------------------------------------------
# Cross-account pull
#
# Pull only. The production account can retrieve images; it cannot push, and it
# cannot delete. Images are produced in exactly one place, by exactly one
# pipeline.
# ---------------------------------------------------------------------------
resource "aws_ecr_repository_policy" "cross_account_pull" {
  for_each = length(var.pull_account_ids) > 0 ? aws_ecr_repository.this : {}

  repository = each.value.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid    = "AllowCrossAccountPull"
      Effect = "Allow"
      Principal = {
        AWS = [for id in var.pull_account_ids : "arn:aws:iam::${id}:root"]
      }
      Action = [
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:BatchCheckLayerAvailability",
      ]
    }]
  })
}

# The pulling account also needs to decrypt with the registry's KMS key —
# easily forgotten, and the symptom is an image pull failure that looks like a
# permissions problem with ECR rather than with KMS.
resource "aws_kms_key_policy" "ecr" {
  count = length(var.pull_account_ids) > 0 ? 1 : 0

  key_id = aws_kms_key.ecr.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat(
      [{
        Sid       = "EnableAccountRoot"
        Effect    = "Allow"
        Principal = { AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root" }
        Action    = "kms:*"
        Resource  = "*"
      }],
      [{
        Sid       = "AllowCrossAccountDecrypt"
        Effect    = "Allow"
        Principal = { AWS = [for id in var.pull_account_ids : "arn:aws:iam::${id}:root"] }
        Action    = ["kms:Decrypt", "kms:DescribeKey"]
        Resource  = "*"
        Condition = {
          StringEquals = { "kms:ViaService" = "ecr.${var.region}.amazonaws.com" }
        }
      }],
    )
  })
}
