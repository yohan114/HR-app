##############################################################################
# MSK (Kafka)
#
# Carries domain events to the three workers: payroll engine, attendance
# processor, notification dispatcher.
#
# Authentication is IAM only. SASL/SCRAM would mean another credential to
# store, rotate and leak; IAM means the workers authenticate with the same IRSA
# role they already have, and every connection appears in CloudTrail.
##############################################################################

locals {
  name = "${var.name_prefix}-kafka"

  common_tags = merge(var.tags, {
    Component = "messaging"
    Module    = "messaging"
  })
}

resource "aws_kms_key" "messaging" {
  description             = "Encryption at rest for ${local.name}"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = local.common_tags
}

resource "aws_kms_alias" "messaging" {
  name          = "alias/${local.name}"
  target_key_id = aws_kms_key.messaging.key_id
}

resource "aws_security_group" "messaging" {
  name        = "${local.name}-sg"
  description = "Kafka access for ${var.name_prefix}"
  vpc_id      = var.vpc_id

  tags = merge(local.common_tags, { Name = local.name })
}

# 9098 only: the IAM SASL port. The plaintext (9092) and TLS-without-auth
# (9094) ports are deliberately not opened.
resource "aws_vpc_security_group_ingress_rule" "from_application" {
  security_group_id            = aws_security_group.messaging.id
  referenced_security_group_id = var.application_security_group_id
  from_port                    = 9098
  to_port                      = 9098
  ip_protocol                  = "tcp"
  description                  = "Kafka IAM SASL from the application nodes"
}

resource "aws_msk_configuration" "this" {
  name              = local.name
  kafka_versions    = [var.kafka_version]
  server_properties = <<-PROPERTIES
    auto.create.topics.enable=false
    default.replication.factor=${var.broker_count >= 3 ? 3 : var.broker_count}
    min.insync.replicas=${var.broker_count >= 3 ? 2 : 1}
    num.partitions=6
    log.retention.hours=168
    unclean.leader.election.enable=false
  PROPERTIES

  lifecycle {
    create_before_destroy = true
  }
}

# Notes on the properties above, because two of them are load-bearing:
#
#   unclean.leader.election.enable=false
#     Permitting an out-of-sync replica to become leader trades durability for
#     availability. For attendance punches and payroll events that is the wrong
#     trade: a silently dropped punch is a payroll dispute weeks later.
#
#   auto.create.topics.enable=false
#     A typo in a topic name should fail loudly, not quietly create a topic
#     nobody consumes and lose every message published to it.

resource "aws_msk_cluster" "this" {
  cluster_name           = local.name
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.broker_count

  broker_node_group_info {
    instance_type   = var.broker_instance_type
    client_subnets  = slice(var.data_subnet_ids, 0, var.broker_count)
    security_groups = [aws_security_group.messaging.id]

    storage_info {
      ebs_storage_info {
        volume_size = var.broker_storage_gb

        # Grows storage automatically at 70% used. Without it, a consumer lag
        # incident turns into a broker running out of disk — which is a much
        # worse incident.
        provisioned_throughput {
          enabled = false
        }
      }
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.this.arn
    revision = aws_msk_configuration.this.latest_revision
  }

  encryption_info {
    encryption_at_rest_kms_key_arn = aws_kms_key.messaging.arn

    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  client_authentication {
    sasl {
      iam = true
    }
    # No unauthenticated access, and no TLS client certs to manage.
    unauthenticated = false
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.broker.name
      }
    }
  }

  open_monitoring {
    prometheus {
      jmx_exporter { enabled_in_broker = true }
      node_exporter { enabled_in_broker = true }
    }
  }

  tags = local.common_tags
}

resource "aws_cloudwatch_log_group" "broker" {
  name              = "/aws/msk/${local.name}"
  retention_in_days = var.environment == "prod" ? 30 : 7

  tags = local.common_tags
}

resource "aws_appautoscaling_target" "storage" {
  max_capacity       = var.broker_storage_max_gb
  min_capacity       = 1
  resource_id        = aws_msk_cluster.this.arn
  scalable_dimension = "kafka:broker-storage:VolumeSize"
  service_namespace  = "kafka"
}

resource "aws_appautoscaling_policy" "storage" {
  name               = "${local.name}-storage"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.storage.resource_id
  scalable_dimension = aws_appautoscaling_target.storage.scalable_dimension
  service_namespace  = aws_appautoscaling_target.storage.service_namespace

  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "KafkaBrokerStorageUtilization"
    }
    target_value = 70
  }
}
