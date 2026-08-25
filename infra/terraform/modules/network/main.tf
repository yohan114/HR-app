##############################################################################
# VPC and networking
#
# Three subnet tiers:
#
#   public   — load balancers and NAT gateways only. Nothing else ever lands
#              here; a workload in a public subnet is one security-group
#              mistake away from being on the internet.
#   private  — EKS nodes and everything that runs code. Egress via NAT.
#   data     — RDS, ElastiCache, MSK, OpenSearch. No route to a NAT at all,
#              so a compromised database cannot call out.
#
# The data tier having no egress route is deliberate and worth defending: it
# is the difference between "an attacker read your database" and "an attacker
# read your database and exfiltrated it".
##############################################################################

locals {
  name = var.name_prefix

  # Three AZs in prod for genuine fault tolerance; two elsewhere, because a
  # third AZ in staging costs money and proves nothing.
  az_count = var.environment == "prod" ? 3 : 2
  azs      = slice(data.aws_availability_zones.available.names, 0, local.az_count)

  common_tags = merge(var.tags, {
    Component = "network"
    Module    = "network"
  })
}

data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "this" {
  cidr_block           = var.cidr_block
  enable_dns_support   = true
  # Required by EKS: without private DNS hostnames, in-cluster service
  # discovery and VPC endpoint resolution both break in confusing ways.
  enable_dns_hostnames = true

  tags = merge(local.common_tags, { Name = local.name })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id
  tags   = merge(local.common_tags, { Name = local.name })
}

# ---------------------------------------------------------------------------
# Subnets
#
# /20 per subnet out of a /16 gives ~4,090 usable addresses each. That sounds
# excessive until you remember the VPC CNI assigns a VPC address to every pod,
# so a node running 30 pods consumes 30 addresses. Undersizing here is painful
# to fix later — subnet CIDRs cannot be resized.
# ---------------------------------------------------------------------------
resource "aws_subnet" "public" {
  count = local.az_count

  vpc_id                  = aws_vpc.this.id
  availability_zone       = local.azs[count.index]
  cidr_block              = cidrsubnet(var.cidr_block, 4, count.index)
  map_public_ip_on_launch = false # Explicit EIPs only. Nothing gets a public IP by accident.

  tags = merge(local.common_tags, {
    Name                     = "${local.name}-public-${local.azs[count.index]}"
    Tier                     = "public"
    "kubernetes.io/role/elb" = "1"
  })
}

resource "aws_subnet" "private" {
  count = local.az_count

  vpc_id            = aws_vpc.this.id
  availability_zone = local.azs[count.index]
  cidr_block        = cidrsubnet(var.cidr_block, 4, count.index + 4)

  tags = merge(local.common_tags, {
    Name                              = "${local.name}-private-${local.azs[count.index]}"
    Tier                              = "private"
    "kubernetes.io/role/internal-elb" = "1"
  })
}

resource "aws_subnet" "data" {
  count = local.az_count

  vpc_id            = aws_vpc.this.id
  availability_zone = local.azs[count.index]
  cidr_block        = cidrsubnet(var.cidr_block, 4, count.index + 8)

  tags = merge(local.common_tags, {
    Name = "${local.name}-data-${local.azs[count.index]}"
    Tier = "data"
  })
}

# ---------------------------------------------------------------------------
# NAT
#
# One NAT gateway per AZ in production: a NAT is zonal, so a single shared one
# makes an AZ failure take down egress for every AZ. Non-production runs one,
# because ~$35/month each adds up and staging can tolerate the coupling.
# ---------------------------------------------------------------------------
locals {
  nat_count = var.environment == "prod" ? local.az_count : 1
}

resource "aws_eip" "nat" {
  count  = local.nat_count
  domain = "vpc"

  tags = merge(local.common_tags, { Name = "${local.name}-nat-${count.index}" })

  depends_on = [aws_internet_gateway.this]
}

resource "aws_nat_gateway" "this" {
  count = local.nat_count

  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = merge(local.common_tags, { Name = "${local.name}-${count.index}" })

  depends_on = [aws_internet_gateway.this]
}

# ---------------------------------------------------------------------------
# Routing
# ---------------------------------------------------------------------------
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id
  tags   = merge(local.common_tags, { Name = "${local.name}-public" })
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

resource "aws_route_table_association" "public" {
  count = local.az_count

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private" {
  count = local.az_count

  vpc_id = aws_vpc.this.id
  tags   = merge(local.common_tags, { Name = "${local.name}-private-${local.azs[count.index]}" })
}

resource "aws_route" "private_nat" {
  count = local.az_count

  route_table_id         = aws_route_table.private[count.index].id
  destination_cidr_block = "0.0.0.0/0"
  # Falls back to NAT 0 when running a single shared gateway.
  nat_gateway_id = aws_nat_gateway.this[min(count.index, local.nat_count - 1)].id
}

resource "aws_route_table_association" "private" {
  count = local.az_count

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[count.index].id
}

# The data tier gets a route table with no default route. Local VPC routing
# only — no path to the internet in either direction.
resource "aws_route_table" "data" {
  vpc_id = aws_vpc.this.id
  tags   = merge(local.common_tags, { Name = "${local.name}-data" })
}

resource "aws_route_table_association" "data" {
  count = local.az_count

  subnet_id      = aws_subnet.data[count.index].id
  route_table_id = aws_route_table.data.id
}

# ---------------------------------------------------------------------------
# VPC endpoints
#
# Two reasons, and the second matters more than the first:
#
#   1. Cost. S3 traffic through a NAT gateway is billed per gigabyte. Payslip
#      PDFs and payroll snapshots are not small.
#   2. Security. Traffic to Secrets Manager, ECR and S3 never leaves the AWS
#      network, so it cannot be intercepted at an internet boundary and does
#      not depend on egress being open.
# ---------------------------------------------------------------------------
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"

  route_table_ids = concat(
    aws_route_table.private[*].id,
    [aws_route_table.data.id],
  )

  tags = merge(local.common_tags, { Name = "${local.name}-s3" })
}

resource "aws_security_group" "vpc_endpoints" {
  name        = "${local.name}-vpc-endpoints"
  description = "HTTPS from within the VPC to interface endpoints"
  vpc_id      = aws_vpc.this.id

  tags = merge(local.common_tags, { Name = "${local.name}-vpc-endpoints" })
}

resource "aws_vpc_security_group_ingress_rule" "endpoints_https" {
  security_group_id = aws_security_group.vpc_endpoints.id
  cidr_ipv4         = var.cidr_block
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
  description       = "HTTPS from the VPC"
}

locals {
  interface_endpoints = toset([
    "secretsmanager", # External Secrets Operator
    "kms",            # decrypting those secrets
    "ecr.api",        # image pulls
    "ecr.dkr",
    "logs",           # CloudWatch Logs
    "sts",            # IRSA token exchange
  ])
}

resource "aws_vpc_endpoint" "interface" {
  for_each = local.interface_endpoints

  vpc_id              = aws_vpc.this.id
  service_name        = "com.amazonaws.${var.region}.${each.value}"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = aws_subnet.private[*].id
  security_group_ids  = [aws_security_group.vpc_endpoints.id]
  private_dns_enabled = true

  tags = merge(local.common_tags, { Name = "${local.name}-${each.value}" })
}

# ---------------------------------------------------------------------------
# Flow logs
#
# The record you need after an incident, and the one nobody enables in advance.
# Rejected traffic only in non-production: ACCEPT records are voluminous and
# the cost is real, but a REJECT is what tells you something tried to reach
# somewhere it should not.
# ---------------------------------------------------------------------------
resource "aws_cloudwatch_log_group" "flow_logs" {
  name              = "/aws/vpc/${local.name}/flow-logs"
  retention_in_days = var.environment == "prod" ? 90 : 14

  tags = local.common_tags
}

resource "aws_iam_role" "flow_logs" {
  name = "${local.name}-flow-logs"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "vpc-flow-logs.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = local.common_tags
}

resource "aws_iam_role_policy" "flow_logs" {
  name = "write-flow-logs"
  role = aws_iam_role.flow_logs.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:DescribeLogGroups",
        "logs:DescribeLogStreams",
      ]
      Resource = "${aws_cloudwatch_log_group.flow_logs.arn}:*"
    }]
  })
}

resource "aws_flow_log" "this" {
  vpc_id               = aws_vpc.this.id
  traffic_type         = var.environment == "prod" ? "ALL" : "REJECT"
  log_destination_type = "cloud-watch-logs"
  log_destination      = aws_cloudwatch_log_group.flow_logs.arn
  iam_role_arn         = aws_iam_role.flow_logs.arn

  tags = local.common_tags
}
