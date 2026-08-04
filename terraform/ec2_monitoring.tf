# 모니터링 서버 EC2 인스턴스
resource "aws_instance" "monitoring" {
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.instance_type
  key_name                    = "scommit-monitor-key"
  availability_zone           = "ap-southeast-2a"
  subnet_id                   = data.aws_subnet.monitoring_az.id
  vpc_security_group_ids      = [aws_security_group.monitoring.id]
  associate_public_ip_address = true

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.volume_size
    iops                  = var.volume_iops
    throughput            = var.volume_throughput
    delete_on_termination = true
  }

  user_data = base64encode(templatefile("${path.module}/scripts/user_data_monitoring.sh", {
    region = var.aws_region
  }))

  tags = {
    Name = "scommit-monitoring"
  }

  depends_on = [aws_security_group.monitoring]
}

# 기존 Elastic IP를 모니터링 서버에 연결
resource "aws_eip_association" "monitoring" {
  instance_id      = aws_instance.monitoring.id
  allocation_id    = data.aws_eip.monitoring.id
  private_ip_address = aws_instance.monitoring.private_ip
}

# 출력값
output "monitoring_instance_id" {
  value       = aws_instance.monitoring.id
  description = "Monitoring server instance ID"
}

output "monitoring_private_ip" {
  value       = aws_instance.monitoring.private_ip
  description = "Monitoring server private IP"
}

output "monitoring_public_ip" {
  value       = aws_eip_association.monitoring.public_ip
  description = "Monitoring server public IP (Elastic IP)"
}
