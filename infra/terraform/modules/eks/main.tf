##############################################################################
# EKS cluster
#
# The piece that matters most here is the OIDC provider. It is what lets a pod
# assume an IAM role via its service account (IRSA), which is why no pod in
# this estate holds static AWS credentials — including the External Secrets
# Operator, which would otherwise need an access key with read access to every
# secret we own.
##############################################################################

locals {
  name = "${var.name_prefix}-eks"

  common_tags = merge(var.tags, {
    Component = "eks"
    Module    = "eks"
  })
}

data "aws_partition" "current" {}

# ---------------------------------------------------------------------------
# Cluster IAM
# ---------------------------------------------------------------------------
resource "aws_iam_role" "cluster" {
  name = "${local.name}-cluster"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "eks.amazonaws.com" }
      Action    = ["sts:AssumeRole", "sts:TagSession"]
    }]
  })

  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "cluster" {
  for_each = toset([
    "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonEKSClusterPolicy",
    "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonEKSVPCResourceController",
  ])

  role       = aws_iam_role.cluster.name
  policy_arn = each.value
}

# ---------------------------------------------------------------------------
# Envelope encryption for Kubernetes secrets
#
# Without this, Kubernetes Secrets are only base64-encoded in etcd. We do route
# most secrets through External Secrets rather than storing them in etcd
# long-term, but the materialised Secrets still land there — so this is the
# difference between "encrypted at rest" being true and being nearly true.
# ---------------------------------------------------------------------------
resource "aws_kms_key" "cluster" {
  description             = "Envelope encryption for ${local.name} Kubernetes secrets"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = local.common_tags
}

resource "aws_kms_alias" "cluster" {
  name          = "alias/${local.name}"
  target_key_id = aws_kms_key.cluster.key_id
}

# ---------------------------------------------------------------------------
# Cluster
# ---------------------------------------------------------------------------
resource "aws_security_group" "cluster" {
  name        = "${local.name}-cluster"
  description = "EKS control plane"
  vpc_id      = var.vpc_id

  tags = merge(local.common_tags, { Name = "${local.name}-cluster" })
}

resource "aws_eks_cluster" "this" {
  name     = local.name
  role_arn = aws_iam_role.cluster.arn
  version  = var.kubernetes_version

  vpc_config {
    subnet_ids              = concat(var.private_subnet_ids, var.public_subnet_ids)
    security_group_ids      = [aws_security_group.cluster.id]
    endpoint_private_access = true
    # Public endpoint access stays on so CI can deploy without a self-hosted
    # runner inside the VPC, but it is CIDR-restricted. Fully private would be
    # stronger and requires a VPN or a runner in-VPC — revisit before prod.
    endpoint_public_access  = var.endpoint_public_access
    public_access_cidrs     = var.public_access_cidrs
  }

  encryption_config {
    provider { key_arn = aws_kms_key.cluster.arn }
    resources = ["secrets"]
  }

  # `audit` is the one that matters after an incident: it records who called
  # what against the API server. `authenticator` records how they authenticated.
  enabled_cluster_log_types = ["api", "audit", "authenticator", "controllerManager", "scheduler"]

  access_config {
    # API rather than the aws-auth ConfigMap. The ConfigMap approach is a
    # single shared object with no audit trail and a well-known failure mode:
    # a bad edit locks everyone out of the cluster with no way back in.
    authentication_mode                         = "API"
    bootstrap_cluster_creator_admin_permissions = true
  }

  tags = local.common_tags

  depends_on = [
    aws_iam_role_policy_attachment.cluster,
    aws_cloudwatch_log_group.cluster,
  ]
}

# Created explicitly so retention is controlled. EKS creates this group itself
# otherwise, with no expiry — audit logs then accumulate indefinitely and the
# bill is noticed long before the logs are.
resource "aws_cloudwatch_log_group" "cluster" {
  name              = "/aws/eks/${local.name}/cluster"
  retention_in_days = var.environment == "prod" ? 90 : 14
  kms_key_id        = aws_kms_key.cluster.arn

  tags = local.common_tags
}

# ---------------------------------------------------------------------------
# OIDC provider — the basis of IRSA
# ---------------------------------------------------------------------------
data "tls_certificate" "oidc" {
  url = aws_eks_cluster.this.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "this" {
  url             = aws_eks_cluster.this.identity[0].oidc[0].issuer
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.oidc.certificates[0].sha1_fingerprint]

  tags = local.common_tags
}

# ---------------------------------------------------------------------------
# Node group
# ---------------------------------------------------------------------------
resource "aws_iam_role" "node" {
  name = "${local.name}-node"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "node" {
  for_each = toset([
    "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonEKSWorkerNodePolicy",
    "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonEKS_CNI_Policy",
    "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly",
    "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonSSMManagedInstanceCore",
  ])

  role       = aws_iam_role.node.name
  policy_arn = each.value
}

# A launch template rather than the node group's inline settings, so IMDSv2 can
# be enforced. This is the single highest-value node hardening available: with
# IMDSv1, any SSRF in a pod can read the node's IAM credentials with a plain
# GET. Requiring tokens and setting the hop limit to 2 stops a container
# reaching the metadata service at all.
resource "aws_launch_template" "node" {
  name_prefix = "${local.name}-node-"

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
    instance_metadata_tags      = "disabled"
  }

  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      volume_size           = var.node_disk_gb
      volume_type           = "gp3"
      encrypted             = true
      delete_on_termination = true
    }
  }

  monitoring { enabled = true }

  tag_specifications {
    resource_type = "instance"
    tags          = merge(local.common_tags, { Name = "${local.name}-node" })
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_eks_node_group" "default" {
  cluster_name    = aws_eks_cluster.this.name
  node_group_name = "default"
  node_role_arn   = aws_iam_role.node.arn
  # Private subnets only. A node in a public subnet is one security-group
  # mistake away from being reachable from the internet.
  subnet_ids = var.private_subnet_ids

  # Graviton by default: roughly 20% better price-performance for a JVM
  # workload, and the backend image is multi-arch.
  instance_types = var.node_instance_types
  ami_type       = var.node_ami_type
  capacity_type  = "ON_DEMAND"

  scaling_config {
    desired_size = var.node_desired_size
    min_size     = var.node_min_size
    max_size     = var.node_max_size
  }

  launch_template {
    id      = aws_launch_template.node.id
    version = aws_launch_template.node.latest_version
  }

  update_config {
    # One node at a time. Combined with the PodDisruptionBudget, this keeps the
    # API available throughout a rolling node replacement.
    max_unavailable = 1
  }

  tags = local.common_tags

  lifecycle {
    # The cluster autoscaler owns desired_size once it is running; Terraform
    # must not fight it on every apply.
    ignore_changes = [scaling_config[0].desired_size]
  }

  depends_on = [aws_iam_role_policy_attachment.node]
}

# ---------------------------------------------------------------------------
# Add-ons
#
# Managed rather than self-installed so upgrades are an AWS concern.
# `OVERWRITE` on conflict: these are AWS-owned components and a hand-edit that
# blocks an upgrade is a worse outcome than losing the edit.
# ---------------------------------------------------------------------------
resource "aws_eks_addon" "this" {
  for_each = toset(["vpc-cni", "coredns", "kube-proxy", "eks-pod-identity-agent"])

  cluster_name                = aws_eks_cluster.this.name
  addon_name                  = each.value
  resolve_conflicts_on_create = "OVERWRITE"
  resolve_conflicts_on_update = "OVERWRITE"

  tags = local.common_tags

  depends_on = [aws_eks_node_group.default]
}
