data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-resolute-26.04-amd64-server-*"]
  }

  filter {
    name   = "state"
    values = ["available"]
  }
}

# 앱 서버 EC2 인스턴스
resource "aws_instance" "app" {
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.instance_type
  key_name                    = "scommit-key"
  availability_zone           = "ap-southeast-2c"
  subnet_id                   = data.aws_subnet.app_az.id
  vpc_security_group_ids      = [aws_security_group.app.id]
  associate_public_ip_address = true

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.volume_size
    iops                  = var.volume_iops
    throughput            = var.volume_throughput
    delete_on_termination = true
  }

  user_data = base64encode(templatefile("${path.module}/scripts/user_data_app.sh", {
    region = var.aws_region
  }))

  tags = {
    Name = "scommit-app"
  }

  depends_on = [aws_security_group.app]
}

# 기존 Elastic IP를 앱 서버에 연결
resource "aws_eip_association" "app" {
  instance_id      = aws_instance.app.id
  allocation_id    = data.aws_eip.app.id
  private_ip_address = aws_instance.app.private_ip
}

# 출력값
output "app_instance_id" {
  value       = aws_instance.app.id
  description = "App server instance ID"
}

output "app_private_ip" {
  value       = aws_instance.app.private_ip
  description = "App server private IP"
}

output "app_public_ip" {
  value       = aws_eip_association.app.public_ip
  description = "App server public IP (Elastic IP)"
}
