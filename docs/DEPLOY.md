# 배포 체크리스트 (AWS 서울 리전 ap-northeast-2)

구성: Amplify(프론트) → HTTPS → EC2 t3.small(Caddy :443 → 백엔드 :8080 → PostgreSQL 17 같은 서버). 예상 월 약 $22 (EC2 $19 + 공인 IPv4 $3.6 + EBS).

> 2026-11 RDS(db.t4g.micro, 월 약 $20)를 없애고 EC2에 PostgreSQL을 직접 설치했다. 기존 서버 전환은 `deploy/setup-db.sh` 한 번 실행(RDS 데이터 복사 → DB_URL을 localhost로). 전환 후 며칠 지켜보고 RDS를 삭제한다(최종 덤프는 서버의 `/var/backups/buffnerf/rds-final.dump`).

## 0. 예산 알림 (먼저)
- [ ] Billing → Budgets → 월 예산 **$40**, 실제 비용 80%·100%에서 이메일 알림

## 1. DB 백업 (EBS 스냅샷)
- [ ] DB가 EC2 디스크에 있으므로, 서버를 만든 뒤 EC2 → 수명 주기 관리자(Data Lifecycle Manager) → 정책: 이 인스턴스의 볼륨 매일 스냅샷, 7개 보관 (8GB 기준 월 $1 미만). 서버 안의 매일 `pg_dump`(05:30, 7일)는 실수로 지운 데이터용이고, 디스크·인스턴스를 잃을 때는 스냅샷이 필요하다

## 2. EC2
- [ ] EC2 → 인스턴스 시작: **Amazon Linux 2023**, **t3.small**, 스토리지 8GB 이상
- [ ] 키 페어 새로 생성 → `.pem` 저장 (예: `~/.ssh/buffnerf.pem`)
- [ ] 보안 그룹 인바운드:
  - SSH 22: **내 IP만**
  - TCP 80, 443: 0.0.0.0/0 (Caddy. 80은 인증서 발급·HTTPS 전환용)
  - TCP 8080은 열지 않는다 (백엔드는 Caddy를 거쳐서만 받는다. 설치 직후 확인용으로 잠깐 열었다면 6번 뒤에 닫는다)
- [ ] 탄력적 IP 할당 → 인스턴스에 연결 (재시작해도 주소 고정)

## 4. 백엔드 설치 (로컬 Git Bash에서)
```bash
IP=<탄력적 IP>; KEY=~/.ssh/buffnerf.pem
scp -i $KEY deploy/setup-ec2.sh deploy/buffnerf.service ec2-user@$IP:/tmp/
ssh -i $KEY ec2-user@$IP 'bash /tmp/setup-ec2.sh'
ssh -i $KEY ec2-user@$IP          # 접속해서 아래 파일 채우기
sudo nano /etc/buffnerf.env        # DB_PASSWORD(새로 정함), GEMINI_API_KEY, ADMIN_KEY(새로 길게). DB_URL은 localhost 그대로
exit
deploy/setup-db.sh ec2-user@$IP $KEY          # PostgreSQL 설치, buffnerf 사용자·DB 생성, 매일 백업 등록
deploy/deploy-backend.sh ec2-user@$IP $KEY     # "OK: 백엔드 응답"이 나오면 성공 (빈 DB에 Flyway가 테이블 생성)
```

## 5. 로컬 데이터 옮기기
로컬에서 AI 처리한 결과를 그대로 옮긴다. 이후 AI 처리는 EC2가 매일 06:00에 이어서 한다.
```bash
deploy/migrate-db.sh ec2-user@$IP $KEY
deploy/setup-https.sh ec2-user@$IP $KEY       # Caddy 설치, https://<IP의 . 을 - 로>.sslip.io 인증서 자동 발급
curl https://${IP//./-}.sslip.io/api/games     # 게임 3개가 나오면 성공
```
- [ ] **로컬 백엔드는 끈다** (같은 Gemini 키의 하루 한도를 나눠 쓰지 않게)

## 6. Amplify (프론트)
- [ ] Amplify → 새 앱 → GitHub 연결 → 저장소 `ohjeongsuk/buffnerf`, 브랜치 `main`
- [ ] "모노레포" 체크 → 루트 디렉터리 **`frontend`** (빌드 설정은 저장소의 `amplify.yml` 사용)
- [ ] 환경 변수:
  - `API_BASE` = `https://<탄력적 IP의 . 을 - 로>.sslip.io` (예: `https://13-125-179-206.sslip.io`)
  - `AMPLIFY_MONOREPO_APP_ROOT` = `frontend`
- [ ] 배포 → 발급된 `https://main.xxxx.amplifyapp.com` 접속 확인

## 7. 확인
- [ ] 홈에 게임 3개, 패치 목록, 패치 상세(AI 요약, 원문 링크), 캐릭터 타임라인
- [ ] `/admin`에서 관리자 키로 원문 대기 목록 조회
- [ ] 다음 날 06:00 이후 `sudo journalctl -u buffnerf --since today | grep -i "collected\|AI\|CommunityDragon"` 로 자동 수집·처리 확인 (CommunityDragon 경고가 있으면 롤 레벨당 공격력 비교가 빠진 것)

## 알려진 한계
- HTTPS 도메인은 sslip.io(IP를 그대로 가리키는 무료 DNS)를 쓴다. 여러 사람이 같이 쓰는 도메인이라 Let's Encrypt 발급 한도에 걸릴 수 있다(Caddy가 시간을 두고 계속 재시도, 로그: `sudo journalctl -u caddy`). 자체 도메인을 사면 Caddyfile의 주소만 바꾼다.
- 백엔드 재배포: `deploy/deploy-backend.sh ec2-user@$IP $KEY`
- `Prompts.VERSION`을 올린 코드를 배포하면 운영 DB의 모든 패치가 미처리로 잡혀 06:00에 Gemini로 다시 처리된다 (무료 한도 초과, 기존 결과 덮어씀). 로컬에서 새 버전으로 이미 처리해 뒀다면 06:00 전에 `deploy/migrate-db.sh`(5번)로 로컬 DB를 먼저 옮기고 바로 이어서 재배포한다. migrate-db는 운영 DB를 로컬 내용으로 통째로 바꾸므로, 운영에만 있는 패치가 없는지 먼저 확인한다.
