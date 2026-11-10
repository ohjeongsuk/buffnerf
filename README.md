# BuffLog (버프로그)

**게임 패치노트, 빠르게 확인.** 리그 오브 레전드·오버워치·이터널 리턴의 공식 패치노트를 AI가 3줄로 요약하고, 캐릭터별 상향·하향을 타임라인으로 보여주는 웹 서비스입니다.

**🔗 서비스: https://main.d29bu1wqtdyiih.amplifyapp.com**

- 요구사항: [`docs/PRD.md`](docs/PRD.md) · 기술 스택: [`docs/TECH_STACK.md`](docs/TECH_STACK.md) · 배포: [`docs/DEPLOY.md`](docs/DEPLOY.md)

## 주요 기능

| 기능 | 설명 |
| --- | --- |
| 패치 수집 | 매일 06:00(KST) 게임별 공식 패치노트를 자동 수집. 실패하면 관리자가 원문을 직접 등록 |
| 롤 스탯 diff | Riot Data Dragon의 버전별 챔피언 기본 스탯을 코드로 비교 |
| AI 3줄 요약 | 패치노트를 한국어 3문장으로 요약 |
| AI 구조화 추출 | 캐릭터·항목·상향/하향·변경 전후 값·**원문 근거 문장**을 JSON 스키마로 추출 |
| 캐릭터 타임라인 | 캐릭터 하나의 패치별 변경 이력을 시간순으로 |
| AI 정확도 | 롤은 AI 추출 결과를 Data Dragon diff와 대조해 재현율·정밀도 계산 (패치 상세 API의 `accuracy`, 화면에는 표시하지 않음) |
| 원문 링크 | 모든 패치에 공식 패치노트 원문 링크 |

## 구성

```
[Next.js 15 (Amplify)] ──/api──▶ [Spring Boot 4 (EC2)] ──▶ [PostgreSQL (RDS)]
                                        │
                                        ├─▶ 공식 패치노트 페이지 (Jsoup 크롤링)
                                        ├─▶ Riot Data Dragon, Steam News API
                                        └─▶ LLM: Gemini API (기본) / Ollama / Claude
```

- 백엔드: Java 21, Spring Boot 4.1.2, JPA + Flyway, `backend/`
- 프론트: Next.js 15, Tailwind, shadcn/ui, `frontend/`
- LLM: `LlmClient` 인터페이스 뒤에 Gemini(기본, 무료 등급)·Ollama(로컬)·Claude 구현체. `LLM_PROVIDER`로 선택

## 배포

| 구성 | 서비스 (서울 리전) |
| --- | --- |
| 프론트 | AWS Amplify Hosting (Next.js SSR), main 브랜치 push 시 자동 빌드 |
| 백엔드 | EC2 t3.small, systemd 서비스, 매일 06:00 수집·AI 처리 |
| DB | RDS PostgreSQL 17, db.t4g.micro (외부 비공개, EC2에서만 접속) |

절차와 스크립트는 [`docs/DEPLOY.md`](docs/DEPLOY.md), [`deploy/`](deploy/) 참고.

## 데이터 출처

| 게임 | 패치노트 원문 | 보조 데이터 |
| --- | --- | --- |
| 리그 오브 레전드 | leagueoflegends.com/ko-kr 패치노트 | Data Dragon (챔피언 기본 스탯) |
| 오버워치 | overwatch.blizzard.com/ko-kr 패치노트 (월별 페이지) | - |
| 이터널 리턴 | playeternalreturn.com 패치노트 글 | Steam News API (새 패치 글 링크 찾기) |

## 결과 (로컬 수집 기준, 2026-11-09)

**자동 수집 성공률:** 51개 패치 모두 크롤링으로 원문 확보 (CRAWLED 51, MANUAL 0, 원문 대기 0)

| 게임 | 패치 수 | 크롤링 성공 |
| --- | --- | --- |
| 리그 오브 레전드 | 20 | 20 |
| 오버워치 | 19 | 19 |
| 이터널 리턴 | 12 | 12 |

**AI 추출 정확도 (롤 기본 스탯, Data Dragon·CommunityDragon diff 기준, 2026-11-10 운영 DB):**

현재 DB의 추출 결과는 Gemini 무료 한도(하루 20회) 때문에 Claude(`claude-fable-5-2`)가 Claude Code 세션에서 같은 프롬프트 규칙으로 직접 처리한 것입니다.

| 패치 | 재현율 | 정밀도 |
| --- | --- | --- |
| 26.3 | 93% (14/15) | 93% (14/15) |
| 26.4 | 82% (9/11) | 90% (9/10) |
| 26.6 | 21% (3/14) | 100% (3/3) |
| 26.20 | 100% (8/8) | 100% (8/8) |
| 26.21 | 100% (7/7) | 100% (7/7) |
| 26.22 | 100% (9/9) | 100% (9/9) |
| **20개 패치 합계** | **91% (165/182)** | **98% (165/168)** |

- 26.6의 놓친 11건은 모두 쉬바나입니다. 26.6에서 쉬바나 챔피언 업데이트(외형·스킬 전면 개편)가 출시됐는데, 패치노트는 새 스킬 설명만 싣고 기본 스탯(체력, 방어력, 사거리 등) 수치는 적지 않아 원문만 보는 AI가 찾을 수 없는 변경입니다. 이를 빼면 재현율은 97% (165/171)입니다.
- Data Dragon은 16.5.1부터 레벨당 공격력을 전 챔피언 0으로 줍니다. 이 항목은 CommunityDragon(게임 데이터 원본)의 값으로 비교하며, 26.6~26.22 패치의 레벨당 공격력 변경 11건을 AI가 모두 같은 수치로 찾았습니다.

## 신뢰성·비용 설계

- **근거 문장 검증:** AI가 낸 근거 문장이 원문에 실제로 없으면 환각으로 보고 버린다. 같은 항목 반복, 컬럼 길이 초과도 버린다.
- **중복 호출 금지:** 같은 패치·같은 프롬프트 버전(`Prompts.VERSION`)으로 성공한 기록(AiRun)이 있으면 LLM을 다시 부르지 않는다.
- **무료 한도 대응:** Gemini 무료 등급은 모델당 하루 20회(패치 10개). 매일 06:00 배치가 미처리 패치를 최신순으로 이어서 처리하고, 한도(429)에 걸리면 그날은 멈춘다. 일시적 과부하(503)는 5초·10초 쉬고 재시도.

## 크롤링 정책

- 요청 사이 1초 간격, User-Agent `BuffNerfBot/0.1`, 하루 1회 새 패치만 수집
- robots.txt 확인 결과 (2026-11-09): 수집하는 경로는 모두 허용
  - leagueoflegends.com: `Allow: /`
  - overwatch.blizzard.com: 로그인·navbar 경로만 금지, 패치노트 허용
  - playeternalreturn.com: 전체 허용
- 화면에는 원문 전문을 싣지 않고 AI 요약, 근거 문장, 원문 링크만 보여준다
- 비상업 팬 프로젝트이며, 각 게임사의 팬 콘텐츠·상표 정책을 따른다. 이 내용은 공개된 약관을 읽고 정리한 것으로 법률 자문이 아니다.

## 로컬 실행

필요: Java 21, Node.js, Docker

```bash
# 1. DB (호스트 포트 5433)
docker compose up -d

# 2. 환경변수: .env.example을 .env로 복사하고 GEMINI_API_KEY, ADMIN_KEY 입력
#    (Gemini 키: https://aistudio.google.com/apikey 에서 무료 발급)
cp .env.example .env

# 3. 백엔드 (http://localhost:8080)
set -a; . ./.env; set +a
cd backend && ./mvnw spring-boot:run

# 4. 프론트 (http://localhost:3000, /api/*는 API_BASE로 프록시)
cd frontend && npm install && npm run dev
```

- 데이터 채우기: `/admin`에서 관리자 키 입력 후 게임별 수동 수집 (화면에 링크가 없으니 주소를 직접 입력, 검색 엔진 제외). 원문이 있는 패치는 AI 처리가 자동으로 이어진다.
- 테스트: `cd backend && ./mvnw verify` (Testcontainers로 Docker 필요)

## 환경변수

| 이름 | 설명 |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL 접속 정보 |
| `LLM_PROVIDER` | `gemini`(기본) / `ollama` / `claude` |
| `GEMINI_API_KEY`, `GEMINI_MODEL` | Gemini 키, 모델 (기본 `gemini-3.5-flash`) |
| `ANTHROPIC_API_KEY` | Claude 키 (`LLM_PROVIDER=claude`일 때) |
| `STEAM_API_KEY` | 현재 미사용 (Steam News API는 키 없이 호출) |
| `ADMIN_KEY` | 관리자 화면·API 보호 키 (요청 헤더 `X-Admin-Key`) |
| `API_BASE` | 프론트가 프록시할 백엔드 주소 |

API 키는 서버 환경변수로만 관리하고 커밋하지 않는다 (`.env`는 gitignore).

---

League of Legends는 Riot Games, Inc., Overwatch는 Blizzard Entertainment, Inc., 이터널 리턴은 님블뉴런의 상표입니다.

BuffLog was created under Riot Games' "Legal Jibber Jabber" policy using assets owned by Riot Games. Riot Games does not endorse or sponsor this project.
