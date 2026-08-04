variable "aws_region" {
  description = "AWS Region"
  type        = string
  default     = "ap-southeast-2"
}

variable "environment" {
  description = "Environment name (prod, dr, etc)"
  type        = string
  default     = "dr"
}

variable "project_name" {
  description = "Project name"
  type        = string
  default     = "scommit"
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t3.small"
}

variable "volume_size" {
  description = "Root volume size in GB"
  type        = number
  default     = 20
}

variable "volume_iops" {
  description = "EBS volume IOPS"
  type        = number
  default     = 3000
}

variable "volume_throughput" {
  description = "EBS volume throughput (MB/s)"
  type        = number
  default     = 125
}

variable "db_password" {
  description = "Database password"
  type        = string
  sensitive   = true
}

variable "db_root_password" {
  description = "Database root password"
  type        = string
  sensitive   = true
}

variable "jwt_secret_key" {
  description = "JWT secret key"
  type        = string
  sensitive   = true
}

variable "cloudinary_cloud_name" {
  description = "Cloudinary cloud name"
  type        = string
}

variable "cloudinary_api_key" {
  description = "Cloudinary API key"
  type        = string
  sensitive   = true
}

variable "cloudinary_api_secret" {
  description = "Cloudinary API secret"
  type        = string
  sensitive   = true
}

variable "mysql_exporter_password" {
  description = "MySQL exporter password"
  type        = string
  sensitive   = true
}
