##############################################################################
# ElastiCache for Redis
#
# Used for three things, in descending order of how badly a failure hurts:
#
#   1. Distributed locks on payroll runs. Two concurrent runs against the same
#      pay period would double-pay people. The lock is what prevents it.
#   2. Rate limiting.
#   3. Hot lookup caching (tenant registry, permission resolution).
#
# Because of (1) this is not a pure cache and cannot simply be flushed. It
# runs with persistence and a replica.
##############################################################################

locals {
  name = "${var.name_prefix}-redis"

  common_tags = merge(var.tags, {
    Component = "cache"
    Module    = "cache"
  })
}

resource "aws_kms_key" "cache" {
  description             = "Encryption at rest for ${local.name}"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = local.common_tags
}

resource "aws_kms_alias" "cache" {
  name          = "alias/${local.name}"
  target_key_id = aws_kms_key.cache.key_id
}

resource "aws_elasticache_subnet_group" "this" {
  name       = local.name
  subnet_ids = var.data_subnet_ids

  tags = local.common_tags
}

resource "aws_security_group" "cache" {
  name        = "${local.name}-sg"
  description = "Redis access for ${var.name_prefix}"
  vpc_id      = var.vpc_id

  tags = merge(local.common_tags, { Name = local.name })
}

resource "aws_vpc_security_group_ingress_rule" "from_application" {
  security_group_id            = aws_security_group.cache.id
  referenced_security_group_id = var.application_security_group_id
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
  description                  = "Redis from the application nodes"
}

# ---------------------------------------------------------------------------
# Auth token
#
# Required whenever transit encryption is on. Generated here and stored in
# Secrets Manager, never surfaced as a module output — an output lands in the
# root module's state and in `terraform output` for anyone with read access.
# ---------------------------------------------------------------------------
resource "random_password" "auth_token" {
  length = 64
  # ElastiCache rejects most punctuation in auth tokens; only these are safe.
  # Using the default special set produces a confusing "invalid token" error at
  # apply time rather than anything that points at the cause.
  special          = true
  override_special = "!&#$^<>-"
}

resource "aws_secretsmanager_secret" "auth_token" {
  name        = "${var.name_prefix}/redis/auth-token"
  description = "Redis AUTH token for ${local.name}"
  kms_key_id  = var.secrets_kms_key_arn

  recovery_window_in_days = var.environment == "prod" ? 30 : 0

  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "auth_token" {
  secret_id = aws_secretsmanager_secret.auth_token.id

  secret_string = jsonencode({
    authToken = random_password.auth_token.result
    host      = aws_elasticache_replication_group.this.primary_endpoint_address
    readerHost = aws_elasticache_replication_group.this.reader_endpoint_address
    port      = 6379
    tls       = true
  })
}

resource "aws_elasticache_parameter_group" "this" {
  name   = local.name
  family = var.parameter_group_family

  # Evict the least-recently-used key that has a TTL, and never evict keys
  # without one. Distributed locks and rate-limit counters all carry TTLs;
  # anything without one is there deliberately and must not silently vanish.
  parameter {
    name  = "maxmemory-policy"
    value = "volatile-lru"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_elasticache_replication_group" "this" {
  replication_group_id = local.name
  description          = "Cache, locks and rate limiting for ${var.name_prefix}"

  engine         = "redis"
  engine_version = var.engine_version
  node_type      = var.node_type
  port           = 6379

  parameter_group_name = aws_elasticache_parameter_group.this.name
  subnet_group_name    = aws_elasticache_subnet_group.this.name
  security_group_ids   = [aws_security_group.cache.id]

  num_cache_clusters         = var.replica_count + 1
  automatic_failover_enabled = var.replica_count > 0
  multi_az_enabled           = var.replica_count > 0 && var.environment == "prod"

  # Both are non-negotiable. Without transit encryption the auth token crosses
  # the network in clear text, which makes it worse than no token at all.
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  kms_key_id                 = aws_kms_key.cache.arn
  auth_token                 = random_password.auth_token.result

  snapshot_retention_limit = var.snapshot_retention_days
  snapshot_window          = "18:00-19:00" # 02:00–03:00 Singapore
  maintenance_window       = "sun:19:30-sun:20:30"

  # Applied during the maintenance window rather than immediately: a failover
  # mid-payroll-run would drop the lock.
  apply_immediately          = var.environment != "prod"
  auto_minor_version_upgrade = true

  log_delivery_configuration {
    destination      = aws_cloudwatch_log_group.slow.name
    destination_type = "cloudwatch-logs"
    log_format       = "json"
    log_type         = "slow-log"
  }

  tags = local.common_tags
}

resource "aws_cloudwatch_log_group" "slow" {
  name              = "/aws/elasticache/${local.name}/slow-log"
  retention_in_days = var.environment == "prod" ? 30 : 7

  tags = local.common_tags
}
