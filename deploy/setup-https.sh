#!/usr/bin/env bash
# 로컬에서 실행: EC2에 Caddy를 설치해 https://<IP 대시>.sslip.io → localhost:8080 프록시 (인증서 자동 발급·갱신)
# 사용: deploy/setup-https.sh ec2-user@<IP> <key.pem>   (보안 그룹에 TCP 80, 443 인바운드가 열려 있어야 인증서가 나온다)
set -euo pipefail
HOST=${1:?사용법: setup-https.sh ec2-user@<IP> <key.pem>}
KEY=${2:?키페어 .pem 경로가 필요합니다}
DOMAIN="$(echo "${HOST#*@}" | tr . -).sslip.io"
VER=2.11.7

ssh -i "$KEY" "$HOST" "DOMAIN=$DOMAIN VER=$VER bash -s" <<'REMOTE'
set -euo pipefail
cd /tmp
curl -sSfLO https://github.com/caddyserver/caddy/releases/download/v$VER/caddy_${VER}_linux_amd64.tar.gz
curl -sSfL https://github.com/caddyserver/caddy/releases/download/v$VER/caddy_${VER}_checksums.txt -o caddy_sums.txt
grep " caddy_${VER}_linux_amd64.tar.gz$" caddy_sums.txt | sha512sum -c -
tar xzf caddy_${VER}_linux_amd64.tar.gz caddy && sudo install -m 755 caddy /usr/local/bin/caddy
rm -f caddy caddy_${VER}_linux_amd64.tar.gz caddy_sums.txt
id caddy >/dev/null 2>&1 || sudo useradd --system --home /var/lib/caddy --create-home --shell /sbin/nologin caddy
sudo mkdir -p /etc/caddy
printf '%s {\n\treverse_proxy localhost:8080\n}\n' "$DOMAIN" | sudo tee /etc/caddy/Caddyfile >/dev/null
sudo tee /etc/systemd/system/caddy.service >/dev/null <<'UNIT'
[Unit]
Description=Caddy (HTTPS reverse proxy for buffnerf)
After=network-online.target
Wants=network-online.target

[Service]
User=caddy
Group=caddy
ExecStart=/usr/local/bin/caddy run --environ --config /etc/caddy/Caddyfile
ExecReload=/usr/local/bin/caddy reload --config /etc/caddy/Caddyfile --force
Restart=on-failure
AmbientCapabilities=CAP_NET_BIND_SERVICE
Environment=XDG_DATA_HOME=/var/lib/caddy XDG_CONFIG_HOME=/var/lib/caddy

[Install]
WantedBy=multi-user.target
UNIT
sudo systemctl daemon-reload
sudo systemctl enable --now caddy
sudo systemctl restart caddy
echo "OK: Caddy 실행. 인증서 확인: curl https://$DOMAIN/api/games"
REMOTE
