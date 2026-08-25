##############################################################################
# State backend bootstrap
#
# The chicken-and-egg problem: Terraform state needs somewhere to live, and
# that somewhere has to be created before any Terraform runs.
#
# Run this ONCE per account, with local state, then commit the resulting
# backend configuration. This directory's own state file is checked into the
# repository deliberately — it contains only bucket and table identifiers, no
# credentials, and losing it would mean the state backend became unmanaged.
#
#   cd infra/terraform/bootstrap
#   terraform init && terraform apply
#
# After that, every environment uses the S3 backend it created.
##############################################################################

terraform {
  required_version = ">= 1.9.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.80"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "hr-platform"
      ManagedBy = "terraform"
      Module    = "bootstrap"
    }
  }
}

variable "region" {
  description = "Region holding Terraform state. One region for all environments."
  type        = string
  default     = "ap-southeast-1"
}

variable "state_bucket_name" {
  description = "Globally unique. Include the account id or organisation name."
  type        = string
}

resource "aws_kms_key" "state" {
  description             = "Encryption for Terraform state"
  deletion_window_in_days = 30
  enable_key_rotation     = true
}

resource "aws_kms_alias" "state" {
  name          = "alias/terraform-state"
  target_key_id = aws_kms_key.state.key_id
}

resource "aws_s3_bucket" "state" {
  bucket = var.state_bucket_name

  # State is the map of the entire estate. Deleting this bucket is not
  # recoverable by re-running anything.
  lifecycle {
    prevent_destroy = true
  }
}

# Non-negotiable. State holds resource identifiers, connection strings and —
# despite our best efforts — occasionally a secret that a provider surfaced as
# an attribute. Versioning is what makes a corrupted or truncated state
# recoverable rather than terminal.
resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.state.arn
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket = aws_s3_bucket.state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  # Keep 90 days of state history. Long enough to recover from a bad apply
  # discovered late; short enough that the bucket does not accumulate every
  # version of every state file forever.
  rule {
    id     = "expire-old-state-versions"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }

  depends_on = [aws_s3_bucket_versioning.state]
}

resource "aws_s3_bucket_policy" "state" {
  bucket = aws_s3_bucket.state.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [aws_s3_bucket.state.arn, "${aws_s3_bucket.state.arn}/*"]
      Condition = {
        Bool = { "aws:SecureTransport" = "false" }
      }
    }]
  })
}

# ---------------------------------------------------------------------------
# Locking
#
# Terraform 1.10+ supports S3-native locking via `use_lockfile`, which removes
# the need for this table. It is retained because the DynamoDB mechanism is
# still what most tooling and CI examples assume, and running both costs
# pennies. Drop it once every consumer is on 1.10+.
# ---------------------------------------------------------------------------
resource "aws_dynamodb_table" "lock" {
  name         = "terraform-state-lock"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  server_side_encryption {
    enabled     = true
    kms_key_arn = aws_kms_key.state.arn
  }

  point_in_time_recovery {
    enabled = true
  }

  lifecycle {
    prevent_destroy = true
  }
}

output "state_bucket" {
  value = aws_s3_bucket.state.id
}

output "lock_table" {
  value = aws_dynamodb_table.lock.name
}

output "kms_key_arn" {
  value = aws_kms_key.state.arn
}

output "backend_configuration" {
  description = "Paste into each environment's backend block."
  value       = <<-EOT
    terraform {
      backend "s3" {
        bucket         = "${aws_s3_bucket.state.id}"
        key            = "<environment>/terraform.tfstate"
        region         = "${var.region}"
        dynamodb_table = "${aws_dynamodb_table.lock.name}"
        encrypt        = true
        kms_key_id     = "${aws_kms_key.state.arn}"
      }
    }
  EOT
}
