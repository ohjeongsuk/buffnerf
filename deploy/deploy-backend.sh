#!/usr/bin/env bash
# 로컬에서 실행: 백엔드 jar 빌드 → EC2 업로드 → 서비스 재시작 → 헬스 체크
# 사용: deploy/deploy-backend.sh ec2-user@<EC2 퍼블릭 IP> <키페어.pem>
set -euo pipefail
HOST=${1:?사용법: deploy-backend.sh ec2-user@<IP> <key.pem>}
KEY=${2:?키페어 .pem 경로가 필요합니다}

cd "$(dirname "$0")/../backend"
./mvnw -q -DskipTests package
JAR=target/backend-0.0.1-SNAPSHOT.jar

scp -i "$KEY" "$JAR" "$HOST:/tmp/backend.jar"
ssh -i "$KEY" "$HOST" 'set -e
  sudo install -o buffnerf -g buffnerf -m 644 /tmp/backend.jar /opt/buffnerf/backend.jar
  sudo systemctl restart buffnerf
  for i in $(seq 1 30); do curl -sf localhost:8080/api/games >/dev/null && echo "OK: 백엔드 응답" && exit 0; sleep 3; done
  echo "FAIL: 90초 안에 응답 없음. 로그: sudo journalctl -u buffnerf -n 100"; exit 1'
