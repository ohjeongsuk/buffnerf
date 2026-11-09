#!/usr/bin/env bash
# 로컬 DB(docker)의 데이터를 RDS로 옮긴다: 로컬 덤프 → EC2 업로드 → EC2에서 RDS로 복원 (RDS는 외부 비공개라 EC2를 거친다)
# 반드시 EC2 백엔드를 멈춘 상태에서, RDS가 비어 있을 때 실행 (Flyway 이력까지 같이 옮긴다)
# 사용: deploy/migrate-db.sh ec2-user@<IP> <key.pem> <RDS 엔드포인트>
set -euo pipefail
HOST=${1:?사용법: migrate-db.sh ec2-user@<IP> <key.pem> <RDS 엔드포인트>}
KEY=${2:?}
RDS=${3:?}

cd "$(dirname "$0")/.."
docker compose exec -T postgres pg_dump -U buffnerf -d buffnerf -Fc --no-owner --no-privileges > /tmp/buffnerf.dump
ls -lh /tmp/buffnerf.dump

scp -i "$KEY" /tmp/buffnerf.dump "$HOST:/tmp/buffnerf.dump"
# 비밀번호는 EC2의 /etc/buffnerf.env 에서 읽는다 (로컬에 남기지 않음)
ssh -i "$KEY" "$HOST" "set -e
  sudo systemctl stop buffnerf || true
  export PGPASSWORD=\$(sudo grep '^DB_PASSWORD=' /etc/buffnerf.env | cut -d= -f2-)
  pg_restore -h '$RDS' -U buffnerf -d buffnerf --no-owner --no-privileges --clean --if-exists /tmp/buffnerf.dump
  rm /tmp/buffnerf.dump
  sudo systemctl start buffnerf
  echo 'OK: 복원 완료, 백엔드 재시작'"
rm /tmp/buffnerf.dump
