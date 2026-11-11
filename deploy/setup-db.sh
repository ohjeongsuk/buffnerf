#!/usr/bin/env bash
# 로컬에서 실행: EC2에 PostgreSQL 17을 설치하고 백엔드가 쓰게 한다 (RDS 비용 절감).
# /etc/buffnerf.env 의 DB_URL이 RDS를 가리키면 RDS 데이터를 복사한 뒤 DB_URL을 localhost로 바꾼다. 이미 localhost면 설치·설정만 한다(새 서버).
# 매일 05:30 pg_dump 백업(7일 보관, /var/backups/buffnerf)도 등록한다. 여러 번 실행해도 안전하다.
# 사용: deploy/setup-db.sh ec2-user@<IP> <key.pem>
set -euo pipefail
HOST=${1:?사용법: setup-db.sh ec2-user@<IP> <key.pem>}
KEY=${2:?키페어 .pem 경로가 필요합니다}

ssh -i "$KEY" "$HOST" bash -s <<'REMOTE'
set -euo pipefail
ENV=/etc/buffnerf.env
DB_HOST=$(sudo grep '^DB_URL=' $ENV | sed -E 's#.*//([^:/]+).*#\1#')
PW=$(sudo grep '^DB_PASSWORD=' $ENV | cut -d= -f2-)
[ -n "$PW" ] || { echo "FAIL: $ENV 의 DB_PASSWORD가 비어 있음"; exit 1; }

sudo dnf install -y postgresql17-server
[ -f /var/lib/pgsql/data/PG_VERSION ] || sudo postgresql-setup --initdb
# 기본 pg_hba는 localhost를 ident로 막는다: JDBC 비밀번호 접속이 되게 scram으로
sudo sed -i -E 's/^(host\s+all\s+all\s+(127\.0\.0\.1\/32|::1\/128)\s+)ident/\1scram-sha-256/' /var/lib/pgsql/data/pg_hba.conf
sudo systemctl enable postgresql
sudo systemctl restart postgresql

# 비밀번호는 stdin으로만 넘긴다 (명령줄에 두면 ps에 보인다)
{ echo "select 'create role buffnerf login' where not exists (select from pg_roles where rolname='buffnerf') \gexec"
  printf "alter role buffnerf password '%s';\n" "${PW//\'/\'\'}"
  echo "select 'create database buffnerf owner buffnerf' where not exists (select from pg_database where datname='buffnerf') \gexec"
} | sudo -u postgres psql -q -v ON_ERROR_STOP=1

if [ "$DB_HOST" != localhost ]; then
  echo ">> $DB_HOST → localhost 데이터 복사 (백엔드 잠시 중지)"
  sudo systemctl stop buffnerf
  trap 'sudo systemctl start buffnerf' EXIT   # 실패해도 백엔드는 다시 켠다 (DB_URL을 안 바꿨으면 RDS 그대로)
  export PGPASSWORD="$PW"
  sudo install -d -o postgres -g postgres -m 700 /var/backups/buffnerf
  pg_dump -h "$DB_HOST" -U buffnerf -d buffnerf -Fc --no-owner --no-privileges -f /tmp/rds.dump
  pg_restore -h localhost -U buffnerf -d buffnerf --no-owner --no-privileges --clean --if-exists /tmp/rds.dump
  q="select (select count(*) from patch)||'/'||(select count(*) from balance_change)||'/'||(select count(*) from ai_run)"
  OLD=$(psql -h "$DB_HOST" -U buffnerf -d buffnerf -Atc "$q"); NEW=$(psql -h localhost -U buffnerf -d buffnerf -Atc "$q")
  echo "patch/balance_change/ai_run: RDS $OLD, 로컬 $NEW"
  [ "$OLD" = "$NEW" ] || { echo "FAIL: 행 수가 다름. DB_URL은 그대로 RDS"; exit 1; }
  sudo install -o postgres -g postgres -m 600 /tmp/rds.dump /var/backups/buffnerf/rds-final.dump && rm /tmp/rds.dump
  # 되돌리기: 주석 처리한 RDS 줄을 다시 살리고 sudo systemctl restart buffnerf
  sudo sed -i -E 's|^DB_URL=(.*)$|# RDS: DB_URL=\1\nDB_URL=jdbc:postgresql://localhost:5432/buffnerf|' $ENV
fi

sudo install -d -o postgres -g postgres -m 700 /var/backups/buffnerf
sudo tee /etc/systemd/system/buffnerf-backup.service >/dev/null <<'UNIT'
[Unit]
Description=BuffNerf DB daily dump
[Service]
Type=oneshot
User=postgres
ExecStart=/bin/bash -c 'pg_dump -d buffnerf -Fc -f /var/backups/buffnerf/buffnerf-$(date +%%F).dump && find /var/backups/buffnerf -name "buffnerf-*.dump" -mtime +7 -delete'
UNIT
sudo tee /etc/systemd/system/buffnerf-backup.timer >/dev/null <<'UNIT'
[Unit]
Description=BuffNerf DB daily dump (06:00 수집 전)
[Timer]
OnCalendar=*-*-* 05:30:00
Persistent=true
[Install]
WantedBy=timers.target
UNIT
# 이미 설치된 서버의 유닛도 재부팅 때 DB 다음에 뜨게 (deploy/buffnerf.service와 맞춤)
sudo sed -i -E 's/^After=network-online.target$/After=network-online.target postgresql.service/' /etc/systemd/system/buffnerf.service
sudo systemctl daemon-reload
sudo systemctl enable --now buffnerf-backup.timer
sudo systemctl start buffnerf-backup.service && sudo ls -lh /var/backups/buffnerf

[ -f /opt/buffnerf/backend.jar ] || { echo "OK: DB 준비 완료. 이제 deploy/deploy-backend.sh 로 백엔드를 올린다"; exit 0; }
sudo systemctl start buffnerf
for i in $(seq 1 30); do curl -sf localhost:8080/api/games >/dev/null && echo "OK: 백엔드가 $(sudo grep '^DB_URL=' $ENV | cut -d= -f2-) 로 응답" && exit 0; sleep 3; done
echo "FAIL: 90초 안에 응답 없음. 로그: sudo journalctl -u buffnerf -n 100"; exit 1
REMOTE
