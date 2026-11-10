# 배포 체크리스트 (AWS 서울 리전 ap-northeast-2)

구성: Amplify(프론트) → EC2 t3.small(백엔드 :8080) → RDS PostgreSQL db.t4g.micro. 예상 월 약 $37.

## 0. 예산 알림 (먼저)
- [ ] Billing → Budgets → 월 예산 **$40**, 실제 비용 80%·100%에서 이메일 알림

## 1. RDS
- [ ] RDS → 데이터베이스 생성: PostgreSQL **17**, 템플릿 "프리 티어" 또는 "개발/테스트", **db.t4g.micro**, 스토리지 20GB gp3
- [ ] DB 인스턴스 식별자 `buffnerf`, 마스터 사용자 `buffnerf`
- [ ] 자격 증명 관리는 **자체 관리**, 비밀번호 직접 입력 (메모). Secrets Manager는 7일마다 비밀번호를 바꿔 `/etc/buffnerf.env`가 틀어진다
- [ ] **퍼블릭 액세스: 아니요**
- [ ] 추가 구성 → 초기 데이터베이스 이름 **`buffnerf`** (빠뜨리면 EC2에서 `psql ... dbname=postgres -c "create database buffnerf"`)
- [ ] 생성 후 **엔드포인트** 메모 (`buffnerf.xxxx.ap-northeast-2.rds.amazonaws.com`)

## 2. EC2
- [ ] EC2 → 인스턴스 시작: **Amazon Linux 2023**, **t3.small**, 스토리지 8GB 이상
- [ ] 키 페어 새로 생성 → `.pem` 저장 (예: `~/.ssh/buffnerf.pem`)
- [ ] 보안 그룹 인바운드:
  - SSH 22: **내 IP만**
  - TCP 8080: 0.0.0.0/0 (Amplify 서버가 호출. 관리자 API는 `ADMIN_KEY`로 보호)
- [ ] 탄력적 IP 할당 → 인스턴스에 연결 (재시작해도 주소 고정)

## 3. EC2 ↔ RDS 연결
- [ ] RDS 보안 그룹 인바운드: PostgreSQL 5432, **소스 = EC2 보안 그룹**

## 4. 백엔드 설치 (로컬 Git Bash에서)
```bash
IP=<탄력적 IP>; KEY=~/.ssh/buffnerf.pem
scp -i $KEY deploy/setup-ec2.sh deploy/buffnerf.service ec2-user@$IP:/tmp/
ssh -i $KEY ec2-user@$IP 'bash /tmp/setup-ec2.sh'
ssh -i $KEY ec2-user@$IP          # 접속해서 아래 파일 채우기
sudo nano /etc/buffnerf.env        # DB_URL(RDS 엔드포인트), DB_PASSWORD, GEMINI_API_KEY, ADMIN_KEY(새로 길게)
exit
deploy/deploy-backend.sh ec2-user@$IP $KEY     # "OK: 백엔드 응답"이 나오면 성공 (빈 DB에 Flyway가 테이블 생성)
```

## 5. 로컬 데이터 옮기기
로컬에서 AI 처리한 결과를 그대로 옮긴다. 이후 AI 처리는 EC2가 매일 06:00에 이어서 한다.
```bash
deploy/migrate-db.sh ec2-user@$IP $KEY <RDS 엔드포인트>
curl http://$IP:8080/api/games     # 게임 3개가 나오면 성공
```
- [ ] **로컬 백엔드는 끈다** (같은 Gemini 키의 하루 한도를 나눠 쓰지 않게)

## 6. Amplify (프론트)
- [ ] Amplify → 새 앱 → GitHub 연결 → 저장소 `ohjeongsuk/buffnerf`, 브랜치 `main`
- [ ] "모노레포" 체크 → 루트 디렉터리 **`frontend`** (빌드 설정은 저장소의 `amplify.yml` 사용)
- [ ] 환경 변수:
  - `API_BASE` = `http://<탄력적 IP>:8080`
  - `AMPLIFY_MONOREPO_APP_ROOT` = `frontend`
- [ ] 배포 → 발급된 `https://main.xxxx.amplifyapp.com` 접속 확인

## 7. 확인
- [ ] 홈에 게임 3개, 패치 목록, 패치 상세(AI 요약, 원문 링크), 캐릭터 타임라인
- [ ] `/admin`에서 관리자 키로 원문 대기 목록 조회
- [ ] 다음 날 06:00 이후 `sudo journalctl -u buffnerf --since today | grep -i "collected\|AI\|CommunityDragon"` 로 자동 수집·처리 확인 (CommunityDragon 경고가 있으면 롤 레벨당 공격력 비교가 빠진 것)

## 알려진 한계
- Amplify 서버 → EC2 구간은 HTTP(암호화 안 됨). 관리자 키가 이 구간을 평문으로 지난다. 공개 서비스로 키우면 EC2 앞에 HTTPS(ALB 또는 Caddy+도메인)를 둔다.
- 백엔드 재배포: `deploy/deploy-backend.sh ec2-user@$IP $KEY`
- `Prompts.VERSION`을 올린 코드를 배포하면 RDS의 모든 패치가 미처리로 잡혀 06:00에 Gemini로 다시 처리된다 (무료 한도 초과, 기존 결과 덮어씀). 로컬에서 새 버전으로 이미 처리해 뒀다면 06:00 전에 `deploy/migrate-db.sh`(5번)로 로컬 DB를 먼저 옮기고 바로 이어서 재배포한다. migrate-db는 RDS를 로컬 내용으로 통째로 바꾸므로, RDS에만 있는 패치가 없는지 먼저 확인한다.
