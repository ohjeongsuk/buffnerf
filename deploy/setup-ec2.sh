#!/usr/bin/env bash
# EC2(Amazon Linux 2023)에서 한 번만 실행: Java 21, PostgreSQL 클라이언트, 서비스 사용자·폴더·systemd 등록 (DB 서버는 setup-db.sh)
# 사용: scp deploy/* ec2-user@<IP>:/tmp/ && ssh ec2-user@<IP> 'bash /tmp/setup-ec2.sh'
set -euo pipefail

sudo dnf install -y java-21-amazon-corretto-headless postgresql17
sudo timedatectl set-timezone Asia/Seoul

id buffnerf >/dev/null 2>&1 || sudo useradd --system --no-create-home --shell /sbin/nologin buffnerf
sudo mkdir -p /opt/buffnerf
sudo chown buffnerf:buffnerf /opt/buffnerf

# 환경변수 파일: 비밀값이 들어가므로 root만 읽기 (systemd가 root로 읽어서 넘긴다)
if [ ! -f /etc/buffnerf.env ]; then
  sudo tee /etc/buffnerf.env >/dev/null <<'EOF'
DB_URL=jdbc:postgresql://localhost:5432/buffnerf
DB_USERNAME=buffnerf
DB_PASSWORD=
LLM_PROVIDER=gemini
GEMINI_API_KEY=
GEMINI_MODEL=gemini-3.5-flash
ADMIN_KEY=
EOF
  sudo chmod 600 /etc/buffnerf.env
  echo ">> /etc/buffnerf.env 를 채우세요: sudo nano /etc/buffnerf.env"
fi

sudo cp /tmp/buffnerf.service /etc/systemd/system/buffnerf.service
sudo systemctl daemon-reload
sudo systemctl enable buffnerf
echo ">> 준비 완료. 로컬에서 deploy/deploy-backend.sh 로 jar를 올리면 시작됩니다."
