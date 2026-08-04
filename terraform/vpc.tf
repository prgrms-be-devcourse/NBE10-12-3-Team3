# 기본 VPC 참조 (172.31.0.0/16)
data "aws_vpc" "default" {
  default = true
}

# 기본 VPC의 모든 서브넷 조회
data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

# ap-southeast-2c의 특정 서브넷 조회 (앱 서버용)
data "aws_subnet" "app_az" {
  vpc_id            = data.aws_vpc.default.id
  availability_zone = "ap-southeast-2c"

  filter {
    name   = "state"
    values = ["available"]
  }
}

# ap-southeast-2a의 특정 서브넷 조회 (모니터링 서버용)
data "aws_subnet" "monitoring_az" {
  vpc_id            = data.aws_vpc.default.id
  availability_zone = "ap-southeast-2a"

  filter {
    name   = "state"
    values = ["available"]
  }
}

# VPC 정보 참조용 출력값
output "vpc_id" {
  value       = data.aws_vpc.default.id
  description = "Default VPC ID"
}

output "vpc_cidr" {
  value       = data.aws_vpc.default.cidr_block
  description = "Default VPC CIDR"
}
