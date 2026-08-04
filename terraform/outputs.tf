output "deployment_summary" {
  value = <<-EOT
    ============================================
    SCommit Infrastructure Deployment Complete
    ============================================

    APP SERVER (ap-southeast-2c):
      Instance ID:     ${aws_instance.app.id}
      Private IP:      ${aws_instance.app.private_ip}
      Public IP (EIP): ${aws_eip_association.app.public_ip}
      Security Group:  ${aws_security_group.app.id}

    MONITORING SERVER (ap-southeast-2a):
      Instance ID:     ${aws_instance.monitoring.id}
      Private IP:      ${aws_instance.monitoring.private_ip}
      Public IP (EIP): ${aws_eip_association.monitoring.public_ip}
      Security Group:  ${aws_security_group.monitoring.id}

    VPC:
      VPC ID:          ${data.aws_vpc.default.id}
      CIDR Block:      ${data.aws_vpc.default.cidr_block}

    NEXT STEPS:
      1. SSH into app server and run docker-compose:
         ssh -i <key> ubuntu@${aws_eip_association.app.public_ip}
         cd /app && docker-compose up -d

      2. SSH into monitoring server and run docker-compose:
         ssh -i <key> ubuntu@${aws_eip_association.monitoring.public_ip}
         cd /monitoring && docker-compose up -d

      3. Configure Nginx & SSL (app server)
         sudo certbot certonly --standalone -d api.scommit.store

      4. Verify services:
         - App:    http://${aws_eip_association.app.public_ip}:8080/actuator/health
         - Prometheus: http://${aws_eip_association.monitoring.public_ip}:9090
         - Grafana:    http://${aws_eip_association.monitoring.public_ip}:3000 (admin/admin)
         - Loki:       http://${aws_eip_association.monitoring.public_ip}:3100
  EOT

  description = "Deployment summary with instance details"
}

output "instance_details" {
  value = {
    app = {
      id          = aws_instance.app.id
      private_ip  = aws_instance.app.private_ip
      public_ip   = aws_eip_association.app.public_ip
      sg_id       = aws_security_group.app.id
      az          = aws_instance.app.availability_zone
    }
    monitoring = {
      id          = aws_instance.monitoring.id
      private_ip  = aws_instance.monitoring.private_ip
      public_ip   = aws_eip_association.monitoring.public_ip
      sg_id       = aws_security_group.monitoring.id
      az          = aws_instance.monitoring.availability_zone
    }
  }
  description = "Structured instance details for scripting"
}

output "ssh_commands" {
  value = {
    app        = "ssh -i <key> ubuntu@${aws_eip_association.app.public_ip}"
    monitoring = "ssh -i <key> ubuntu@${aws_eip_association.monitoring.public_ip}"
  }
  description = "SSH commands for accessing instances"
}

output "service_urls" {
  value = {
    app_health    = "http://${aws_eip_association.app.public_ip}:8080/actuator/health"
    prometheus    = "http://${aws_eip_association.monitoring.public_ip}:9090"
    grafana       = "http://${aws_eip_association.monitoring.public_ip}:3000"
    loki          = "http://${aws_eip_association.monitoring.public_ip}:3100"
  }
  description = "Service URLs for verification"
}
