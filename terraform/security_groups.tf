# 앱 서버 보안 그룹
resource "aws_security_group" "app" {
  name        = "scommit-app-sg"
  description = "Security group for SCommit app server"
  vpc_id      = data.aws_vpc.default.id

  tags = {
    Name = "scommit-app-sg"
  }
}

# 앱 서버 - 인바운드 규칙
resource "aws_vpc_security_group_ingress_rule" "app_http" {
  security_group_id = aws_security_group.app.id

  description = "HTTP from anywhere"
  from_port   = 80
  to_port     = 80
  ip_protocol = "tcp"
  cidr_ipv4   = "0.0.0.0/0"

  tags = {
    Name = "http"
  }
}

resource "aws_vpc_security_group_ingress_rule" "app_https" {
  security_group_id = aws_security_group.app.id

  description = "HTTPS from anywhere"
  from_port   = 443
  to_port     = 443
  ip_protocol = "tcp"
  cidr_ipv4   = "0.0.0.0/0"

  tags = {
    Name = "https"
  }
}

resource "aws_vpc_security_group_ingress_rule" "app_ssh" {
  security_group_id = aws_security_group.app.id

  description = "SSH from anywhere"
  from_port   = 22
  to_port     = 22
  ip_protocol = "tcp"
  cidr_ipv4   = "0.0.0.0/0"

  tags = {
    Name = "ssh"
  }
}

# 모니터링 서버에서 MySQL Exporter 접근 (EIP 사용)
resource "aws_vpc_security_group_ingress_rule" "app_mysql_exporter" {
  security_group_id = aws_security_group.app.id

  description = "MySQL Exporter from monitoring server"
  from_port   = 9104
  to_port     = 9104
  ip_protocol = "tcp"
  cidr_ipv4   = "${data.aws_eip.monitoring.public_ip}/32"

  tags = {
    Name = "mysqld_exporter"
  }
}

# 앱 서버 - 아웃바운드 (모든 트래픽)
resource "aws_vpc_security_group_egress_rule" "app_all" {
  security_group_id = aws_security_group.app.id

  description = "Allow all outbound traffic"
  ip_protocol = "-1"
  cidr_ipv4   = "0.0.0.0/0"

  tags = {
    Name = "all"
  }
}

# 모니터링 서버 보안 그룹
resource "aws_security_group" "monitoring" {
  name        = "scommit-monitoring-sg"
  description = "Security group for SCommit monitoring server"
  vpc_id      = data.aws_vpc.default.id

  tags = {
    Name = "scommit-monitoring-sg"
  }
}

# 모니터링 서버 - 인바운드 규칙
resource "aws_vpc_security_group_ingress_rule" "monitoring_ssh" {
  security_group_id = aws_security_group.monitoring.id

  description = "SSH from anywhere"
  from_port   = 22
  to_port     = 22
  ip_protocol = "tcp"
  cidr_ipv4   = "0.0.0.0/0"

  tags = {
    Name = "ssh"
  }
}

resource "aws_vpc_security_group_ingress_rule" "monitoring_prometheus" {
  security_group_id = aws_security_group.monitoring.id

  description = "Prometheus UI"
  from_port   = 9090
  to_port     = 9090
  ip_protocol = "tcp"
  cidr_ipv4   = "0.0.0.0/0"

  tags = {
    Name = "prometheus"
  }
}

resource "aws_vpc_security_group_ingress_rule" "monitoring_grafana" {
  security_group_id = aws_security_group.monitoring.id

  description = "Grafana UI"
  from_port   = 3000
  to_port     = 3000
  ip_protocol = "tcp"
  cidr_ipv4   = "0.0.0.0/0"

  tags = {
    Name = "grafana"
  }
}

# 앱 서버에서 Loki 접근 (EIP 사용)
resource "aws_vpc_security_group_ingress_rule" "monitoring_loki" {
  security_group_id = aws_security_group.monitoring.id

  description = "Loki from app server promtail"
  from_port   = 3100
  to_port     = 3100
  ip_protocol = "tcp"
  cidr_ipv4   = "${data.aws_eip.app.public_ip}/32"

  tags = {
    Name = "loki"
  }
}

# 모니터링 서버 - 아웃바운드 (모든 트래픽)
resource "aws_vpc_security_group_egress_rule" "monitoring_all" {
  security_group_id = aws_security_group.monitoring.id

  description = "Allow all outbound traffic"
  ip_protocol = "-1"
  cidr_ipv4   = "0.0.0.0/0"

  tags = {
    Name = "all"
  }
}

# 출력값
output "app_sg_id" {
  value       = aws_security_group.app.id
  description = "App server security group ID"
}

output "monitoring_sg_id" {
  value       = aws_security_group.monitoring.id
  description = "Monitoring server security group ID"
}
