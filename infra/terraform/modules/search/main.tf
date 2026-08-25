##############################################################################
# OpenSearch
#
# Backs the employee directory, document search and (from Phase 6) the
# assistant's retrieval layer.
#
# The important property: this holds a *copy* of tenant data outside PostgreSQL,
# where row-level security does not apply. Isolation therefore has to be
# enforced by the application on every query — a filter on `tenant_id` in the
# query body, applied in a single indexing/search facade rather than by each
# caller remembering. That is an application concern, but it is worth stating
# here because standing up this cluster is the moment the guarantee stops being
# purely a database property.
##############################################################################

locals {
  name = "${var.name_prefix}-search"

  common_tags = merge(var.tags, {
    Component = "search"
    Module    = "search"
  })
}

data "aws_caller_identity" "current" {}

resource "aws_kms_key" "search" {
  description             = "Encryption at rest for ${local.name}"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = local.common_tags
}

resource "aws_kms_alias" "search" {
  name          = "alias/${local.name}"
  target_key_id = aws_kms_key.search.key_id
}

resource "aws_security_group" "search" {
  name        = "${local.name}-sg"
  description = "OpenSearch access for ${var.name_prefix}"
  vpc_id      = var.vpc_id

  tags = merge(local.common_tags, { Name = local.name })
}

resource "aws_vpc_security_group_ingress_rule" "from_application" {
  security_group_id            = aws_security_group.search.id
  referenced_security_group_id = var.application_security_group_id
  from_port                    = 443
  to_port                      = 443
  ip_protocol                  = "tcp"
  description                  = "HTTPS from the application nodes"
}

# Required once, per account, before a domain can be placed in a VPC.
# `create_before_destroy` is not applicable — it is a singleton, so the root
# module owns whether to create it.
resource "aws_iam_service_linked_role" "opensearch" {
  count = var.create_service_linked_role ? 1 : 0

  aws_service_name = "opensearchservice.amazonaws.com"
}

resource "aws_opensearch_domain" "this" {
  domain_name    = local.name
  engine_version = var.engine_version

  cluster_config {
    instance_type  = var.instance_type
    instance_count = var.instance_count

    # A dedicated master keeps cluster state management off the data nodes, so
    # a heavy query cannot destabilise the cluster itself. Production only —
    # it is three extra nodes.
    dedicated_master_enabled = var.environment == "prod"
    dedicated_master_type    = var.environment == "prod" ? var.master_instance_type : null
    dedicated_master_count   = var.environment == "prod" ? 3 : null

    zone_awareness_enabled = var.instance_count > 1

    dynamic "zone_awareness_config" {
      for_each = var.instance_count > 1 ? [1] : []
      content {
        availability_zone_count = var.instance_count >= 3 && var.environment == "prod" ? 3 : 2
      }
    }
  }

  ebs_options {
    ebs_enabled = true
    volume_type = "gp3"
    volume_size = var.volume_size_gb
  }

  vpc_options {
    # Never public. A publicly reachable OpenSearch domain is one
    # misconfiguration away from being the breach in a news story.
    subnet_ids         = slice(var.data_subnet_ids, 0, var.instance_count > 1 ? 2 : 1)
    security_group_ids = [aws_security_group.search.id]
  }

  encrypt_at_rest {
    enabled    = true
    kms_key_id = aws_kms_key.search.arn
  }

  node_to_node_encryption {
    enabled = true
  }

  domain_endpoint_options {
    enforce_https       = true
    tls_security_policy = "Policy-Min-TLS-1-2-PFS-2023-10"
  }

  advanced_security_options {
    enabled                        = true
    # IAM rather than an internal user database: the application authenticates
    # with its IRSA role, so there is no OpenSearch password to store or rotate.
    internal_user_database_enabled = false

    master_user_options {
      master_user_arn = var.application_role_arn
    }
  }

  log_publishing_options {
    log_type                 = "ES_APPLICATION_LOGS"
    cloudwatch_log_group_arn = aws_cloudwatch_log_group.search.arn
    enabled                  = true
  }

  # Deliberately NOT enabling SEARCH_SLOW_LOGS / INDEX_SLOW_LOGS: those record
  # the query body, and directory queries contain employee names and search
  # terms. Slow-query analysis goes through application metrics instead.

  auto_tune_options {
    desired_state = var.environment == "prod" ? "ENABLED" : "DISABLED"
  }

  off_peak_window_options {
    enabled = true
    off_peak_window {
      window_start_time {
        hours   = 18 # 02:00 Singapore
        minutes = 0
      }
    }
  }

  software_update_options {
    auto_software_update_enabled = true
  }

  tags = local.common_tags

  depends_on = [aws_iam_service_linked_role.opensearch]
}

# Access policy: only the application role, and only within the VPC. Belt and
# braces alongside the security group — a security group mistake alone is then
# not sufficient to reach the data.
resource "aws_opensearch_domain_policy" "this" {
  domain_name = aws_opensearch_domain.this.domain_name

  access_policies = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { AWS = var.application_role_arn }
      Action    = "es:ESHttp*"
      Resource  = "${aws_opensearch_domain.this.arn}/*"
    }]
  })
}

resource "aws_cloudwatch_log_group" "search" {
  name              = "/aws/opensearch/${local.name}"
  retention_in_days = var.environment == "prod" ? 30 : 7

  tags = local.common_tags
}

resource "aws_cloudwatch_log_resource_policy" "search" {
  policy_name = "${local.name}-logs"

  policy_document = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "es.amazonaws.com" }
      Action    = ["logs:PutLogEvents", "logs:CreateLogStream"]
      Resource  = "${aws_cloudwatch_log_group.search.arn}:*"
      Condition = {
        StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id }
      }
    }]
  })
}
