# 기존 Elastic IP 참조 (IP 및 DNS 보존)
data "aws_eip" "app" {
  id = "eipalloc-0429e429aaba54677"  # 앱 서버 EIP: 13.238.245.254
}

data "aws_eip" "monitoring" {
  id = "eipalloc-0cec25cb681fccb8a"  # 모니터링 서버 EIP: 54.153.142.128
}

# 참조용 출력값
output "app_eip" {
  value       = data.aws_eip.app.public_ip
  description = "App server public IP (13.238.245.254)"
}

output "monitoring_eip" {
  value       = data.aws_eip.monitoring.public_ip
  description = "Monitoring server public IP (54.153.142.128)"
}

output "app_eip_alloc_id" {
  value       = data.aws_eip.app.id
  description = "App server EIP allocation ID"
}

output "monitoring_eip_alloc_id" {
  value       = data.aws_eip.monitoring.id
  description = "Monitoring server EIP allocation ID"
}
