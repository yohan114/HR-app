##############################################################################
# S3 buckets
#
# Four buckets rather than one with prefixes. Prefix-based separation means one
# IAM policy mistake exposes everything; separate buckets let each have its own
# policy, its own lifecycle rules and its own retention, which genuinely differ:
#
#   documents          — contracts, letters. Deletable by users.
#   payslips           — statutory retention (5–7 years). NOT user-deletable.
#   attachments        — leave certificates, receipts. Modest retention.
#   payroll-snapshots  — the inputs a payroll run was computed from. These are
#                        what make a committed run reproducible years later, so
#                        they are versioned, locked and never expired.
##############################################################################

locals {
  common_tags = merge(var.tags, {
    Component = "storage"
    Module    = "storage"
  })

  buckets = {
    documents = {
      purpose            = "Employee documents, contracts and generated letters"
      versioning         = true
      noncurrent_days    = 90
      expire_after_days  = null
      object_lock        = false
    }
    payslips = {
      purpose = "Generated payslip PDFs"
      versioning         = true
      noncurrent_days    = 365
      # Never expired by lifecycle. Payroll records carry statutory retention
      # in every target market, and deletion is a deliberate act after a
      # documented retention review — not something a lifecycle rule does
      # quietly at 3am.
      expire_after_days  = null
      object_lock        = true
    }
    attachments = {
      purpose            = "Leave certificates, expense receipts, profile photos"
      versioning         = true
      noncurrent_days    = 30
      expire_after_days  = null
      object_lock        = false
    }
    payroll-snapshots = {
      purpose = "Immutable inputs to each payroll run"
      versioning         = true
      noncurrent_days    = 365
      expire_after_days  = null
      # Object Lock in governance mode: reproducibility of a committed payroll
      # run depends on these being byte-identical to what was used. An
      # accidental overwrite would make a historical run unreproducible, which
      # is a compliance problem as well as an engineering one.
      object_lock        = true
    }
  }
}

resource "aws_kms_key" "storage" {
  description             = "Encryption for ${var.name_prefix} object storage"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = local.common_tags
}

resource "aws_kms_alias" "storage" {
  name          = "alias/${var.name_prefix}-storage"
  target_key_id = aws_kms_key.storage.key_id
}

resource "aws_s3_bucket" "this" {
  for_each = local.buckets

  bucket = "${var.name_prefix}-${each.key}"

  # Object Lock can only be enabled at creation. Turning it on later means
  # creating a new bucket and copying everything across.
  object_lock_enabled = each.value.object_lock

  tags = merge(local.common_tags, {
    Name    = "${var.name_prefix}-${each.key}"
    Purpose = each.value.purpose
  })
}

# Nothing here is ever public. Applied per-bucket rather than relying on the
# account-level setting, so a bucket in this module is safe regardless of how
# the account is configured.
resource "aws_s3_bucket_public_access_block" "this" {
  for_each = aws_s3_bucket.this

  bucket = each.value.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  for_each = aws_s3_bucket.this

  bucket = each.value.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.storage.arn
    }
    # Caches the data key per request context rather than calling KMS for every
    # object. Payslip generation writes thousands of objects in a run; without
    # this the KMS bill and the request latency are both noticeable.
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_versioning" "this" {
  for_each = { for k, v in local.buckets : k => v if v.versioning }

  bucket = aws_s3_bucket.this[each.key].id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "this" {
  for_each = local.buckets

  bucket = aws_s3_bucket.this[each.key].id

  # Incomplete uploads are invisible in the console and billed indefinitely.
  # Every bucket gets this rule.
  rule {
    id     = "abort-incomplete-uploads"
    status = "Enabled"

    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }

  rule {
    id     = "expire-noncurrent-versions"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = each.value.noncurrent_days
    }
  }

  # Payslips and snapshots are read rarely after the first month but must
  # remain instantly retrievable when an employee asks. Intelligent-Tiering
  # handles that without the retrieval delay of Glacier.
  dynamic "rule" {
    for_each = each.value.object_lock ? [1] : []

    content {
      id     = "tier-cold-objects"
      status = "Enabled"

      filter {}

      transition {
        days          = 90
        storage_class = "INTELLIGENT_TIERING"
      }
    }
  }

  depends_on = [aws_s3_bucket_versioning.this]
}

# Deny any request that is not over TLS. S3 accepts plain HTTP by default, and
# a signed URL fetched over HTTP exposes both the URL and the object.
resource "aws_s3_bucket_policy" "enforce_tls" {
  for_each = aws_s3_bucket.this

  bucket = each.value.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource = [
        each.value.arn,
        "${each.value.arn}/*",
      ]
      Condition = {
        Bool = { "aws:SecureTransport" = "false" }
      }
    }]
  })
}

# ---------------------------------------------------------------------------
# Application access
#
# One IRSA role. Object-level read/write, no bucket-level delete, and
# explicitly no ability to alter bucket configuration — a compromised pod must
# not be able to switch off versioning or drop a lifecycle rule.
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "application_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [var.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.oidc_provider_url}:sub"
      values   = ["system:serviceaccount:${var.application_namespace}:${var.application_service_account}"]
    }

    condition {
      test     = "StringEquals"
      variable = "${var.oidc_provider_url}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "application" {
  statement {
    sid    = "ObjectAccess"
    effect = "Allow"

    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:GetObjectVersion",
    ]

    resources = [for b in aws_s3_bucket.this : "${b.arn}/*"]
  }

  statement {
    sid       = "ListBuckets"
    effect    = "Allow"
    actions   = ["s3:ListBucket", "s3:GetBucketLocation"]
    resources = [for b in aws_s3_bucket.this : b.arn]
  }

  statement {
    sid    = "UseEncryptionKey"
    effect = "Allow"

    actions = [
      "kms:Decrypt",
      "kms:GenerateDataKey",
      "kms:DescribeKey",
    ]

    resources = [aws_kms_key.storage.arn]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["s3.${var.region}.amazonaws.com"]
    }
  }

  # Payslips and payroll snapshots are append-only from the application's point
  # of view. Deleting a payslip version, or removing the object lock that keeps
  # a run reproducible, is an administrative act with a paper trail — not
  # something a bug or a compromised pod can do.
  statement {
    sid    = "DenyImmutableObjectDeletion"
    effect = "Deny"

    actions = [
      "s3:DeleteObjectVersion",
      "s3:PutBucketVersioning",
      "s3:PutLifecycleConfiguration",
      "s3:PutBucketPolicy",
      "s3:PutObjectRetention",
      "s3:BypassGovernanceRetention",
    ]

    resources = [
      aws_s3_bucket.this["payslips"].arn,
      "${aws_s3_bucket.this["payslips"].arn}/*",
      aws_s3_bucket.this["payroll-snapshots"].arn,
      "${aws_s3_bucket.this["payroll-snapshots"].arn}/*",
    ]
  }
}

resource "aws_iam_role" "application" {
  name               = "${var.name_prefix}-backend-storage"
  description        = "Object access for the ${var.name_prefix} backend"
  assume_role_policy = data.aws_iam_policy_document.application_trust.json

  tags = local.common_tags
}

resource "aws_iam_role_policy" "application" {
  name   = "object-access"
  role   = aws_iam_role.application.id
  policy = data.aws_iam_policy_document.application.json
}
