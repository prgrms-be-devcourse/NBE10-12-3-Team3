#!/bin/bash
set -e

# 시스템 업데이트
apt-get update
apt-get upgrade -y

# Docker 설치
apt-get install -y \
    apt-transport-https \
    ca-certificates \
    curl \
    gnupg \
    lsb-release

# Docker GPG 키 및 저장소 추가
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null

# Docker 최신 버전 설치
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Docker 서비스 활성화 및 시작
systemctl enable docker
systemctl start docker

# 모니터링 디렉토리 생성 및 권한 설정
mkdir -p /home/ubuntu/monitoring
cd /home/ubuntu/monitoring
chown -R ubuntu:ubuntu /home/ubuntu/monitoring

echo "Docker 설치 완료"
