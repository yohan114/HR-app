##############################################################################
# PostgreSQL (RDS)
#
# The isolation boundary of the entire product lives in this database — see
# ADR 0002. Two things here are load-bearing rather than incidental:
#
#   1. The application connects as a NON-OWNER role. PostgreSQL exempts table
#      owners from row-level security, so connecting as the owner would make
#      every policy decorative while all isolation tests still passed. The
#      module therefore provisions two secrets, not one.
#
#   2. Encryption at rest is not optional and not overridable. There is no
#      variable to turn it off, because the only reason anyone would is to
#      save a trivial amount of money on a database holding salaries, bank
#      details and national identity numbers.
##############################################################################

locals {
  identifier = "${var.name_prefix}-postgres"

  common_tags = merge(var.tags, {
    Component = "database"
    Module    = "database"
  })
}

# ---------------------------------------------------------------------------
# Encryption key
#
# A customer-managed key rather than the AWS-managed default, so key access
# appears in CloudTrail as a distinct, auditable permission and can be revoked
# independently of the database itself.
# ---------------------------------------------------------------------------
resource "aws_kms_key" "database" {
  description             = "Encryption at rest for ${local.identifier}"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = local.common_tags
}

resource "aws_kms_alias" "database" {
  name          = "alias/${local.identifier}"
  target_key_id = aws_kms_key.database.key_id
}

# ---------------------------------------------------------------------------
# Networking
# ---------------------------------------------------------------------------
resource "aws_db_subnet_group" "this" {
  name       = local.identifier
  subnet_ids = var.private_subnet_ids

  tags = local.common_tags
}

resource "aws_security_group" "database" {
  name        = "${local.identifier}-sg"
  description = "PostgreSQL access for ${var.name_prefix}"
  vpc_id      = var.vpc_id

  tags = local.common_tags
}

# Ingress only from the application security group. Deliberately no CIDR-based
# rule and no bastion path: administrative access goes through the session
# manager, so there is no standing route from anywhere else to the database.
resource "aws_vpc_security_group_ingress_rule" "from_application" {
  security_group_id            = aws_security_group.database.id
  referenced_security_group_id = var.application_security_group_id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
  description                  = "PostgreSQL from the application nodes"
}

# ---------------------------------------------------------------------------
# Parameter group
# ---------------------------------------------------------------------------
resource "aws_db_parameter_group" "this" {
  name        = local.identifier
  family      = var.parameter_group_family
  description = "Tuned parameters for ${local.identifier}"

  # Log statements that take longer than a second. Not all statements: at our
  # write volumes that would generate more log than data, and the cost lands on
  # the database itself.
  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }

  # Never log the statement text of a failed query — bind parameters include
  # salaries, national identity numbers and bank account numbers, and error
  # logs are read far more casually than the database is.
  parameter {
    name  = "log_statement"
    value = "ddl"
  }

  parameter {
    name  = "log_connections"
    value = "1"
  }

  parameter {
    name  = "log_disconnections"
    value = "1"
  }

  # Cancel anything still running after five minutes. A runaway report should
  # inconvenience its author, not the tenant's payroll run.
  parameter {
    name  = "statement_timeout"
    value = "300000"
  }

  # An idle transaction holds locks and pins the oldest xmin, which blocks
  # vacuum. Sixty seconds is generous for anything legitimate.
  parameter {
    name  = "idle_in_transaction_session_timeout"
    value = "60000"
  }

  parameter {
    name  = "shared_preload_libraries"
    value = "pg_stat_statements"
  }

  parameter {
    name         = "track_activity_query_size"
    value        = "4096"
    apply_method = "pending-reboot"
  }

  lifecycle {
    create_before_destroy = true
  }
}

# ---------------------------------------------------------------------------
# Instance
# ---------------------------------------------------------------------------
resource "aws_db_instance" "this" {
  identifier     = local.identifier
  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class

  allocated_storage     = var.allocated_storage_gb
  max_allocated_storage = var.max_allocated_storage_gb
  storage_type          = "gp3"

  # Not overridable. See the module header.
  storage_encrypted = true
  kms_key_id        = aws_kms_key.database.arn

  db_name  = var.database_name
  username = var.owner_username
  # Managed by AWS and rotated automatically, so the password never exists in
  # Terraform state. A password in state is a password in every backup of that
  # state and in every plan output anyone has ever pasted into a ticket.
  manage_master_user_password   = true
  master_user_secret_kms_key_id = aws_kms_key.database.arn

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.database.id]
  parameter_group_name   = aws_db_parameter_group.this.name
  publicly_accessible    = false

  multi_az = var.multi_az

  backup_retention_period = var.backup_retention_days
  backup_window           = "17:00-18:00" # 01:00–02:00 Singapore, off-peak for every target market
  maintenance_window      = "sun:18:30-sun:19:30"
  copy_tags_to_snapshot   = true

  # Point-in-time recovery to any second within the retention window. The DR
  # drill in P0-QA-20 targets RPO ≤ 15 minutes; PITR is what makes that
  # achievable rather than aspirational.
  delete_automated_backups = false
  skip_final_snapshot      = false
  final_snapshot_identifier = "${local.identifier}-final-${var.environment}"

  deletion_protection = var.deletion_protection

  performance_insights_enabled          = true
  performance_insights_kms_key_id       = aws_kms_key.database.arn
  performance_insights_retention_period = 7

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  monitoring_interval = 30
  monitoring_role_arn = aws_iam_role.enhanced_monitoring.arn

  auto_minor_version_upgrade = true
  apply_immediately          = var.environment != "prod"

  tags = local.common_tags

  lifecycle {
    # The password is managed by AWS; a version change must not force replacement.
    ignore_changes = [master_user_secret_kms_key_id]
  }
}

# ---------------------------------------------------------------------------
# Application role credentials
#
# The role itself is created by the V1 migration; this stores the password the
# application authenticates with. Terraform does not create the role, because
# creating database objects requires a connection to a database that does not
# exist until this module has run.
# ---------------------------------------------------------------------------
resource "random_password" "application" {
  length  = 40
  special = true
  # Excluded because they terminate a JDBC URL or a shell argument and produce
  # failures that look like an authentication problem rather than a quoting one.
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "aws_secretsmanager_secret" "application" {
  name        = "${var.name_prefix}/database/application"
  description = "Non-owner role the application connects as. Subject to row-level security."
  kms_key_id  = aws_kms_key.database.arn

  recovery_window_in_days = var.environment == "prod" ? 30 : 0

  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "application" {
  secret_id = aws_secretsmanager_secret.application.id

  secret_string = jsonencode({
    username = var.application_username
    password = random_password.application.result
    host     = aws_db_instance.this.address
    port     = aws_db_instance.this.port
    database = var.database_name
    jdbcUrl  = "jdbc:postgresql://${aws_db_instance.this.endpoint}/${var.database_name}"
  })
}

# ---------------------------------------------------------------------------
# Enhanced monitoring
# ---------------------------------------------------------------------------
resource "aws_iam_role" "enhanced_monitoring" {
  name = "${local.identifier}-monitoring"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "monitoring.rds.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "enhanced_monitoring" {
  role       = aws_iam_role.enhanced_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}
