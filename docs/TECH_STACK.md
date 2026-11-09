# BuffNerf 기술 스택

PRD(`docs/PRD.md`)의 요구사항을 기준으로 기술을 하나씩 정한다. 정한 항목은 이유와 함께 기록한다.

## 확정

| 영역 | 선택 | 이유 |
| --- | --- | --- |
| 백엔드 언어·프레임워크 | Java + Spring Boot | 국내 백엔드 채용에서 가장 흔함. PatchCollector 인터페이스·구현체, 엔티티 구조와 잘 맞음 |
| 프론트엔드 | Next.js (Amplify 지원 범위 안, 밖이면 15 고정) | PRD 결정 |
| DB | PostgreSQL (RDS db.t4g.micro) | PRD 결정 |
| LLM | Gemini API 무료 등급 `gemini-3.5-flash` (기본), 로컬 Ollama `qwen3:8b` (`LLM_PROVIDER=ollama`), Claude Sonnet 5.5 (`LLM_PROVIDER=claude`). LlmClient 인터페이스로 감쌈 | PRD 결정 (2026-11-09 변경: 무료이면서 EC2에서 동작) |
| 배포 | AWS 서울: Amplify + EC2 t3.small + RDS | PRD 결정 |

## 백엔드 세부 (확정 2026-11-06)

| 영역 | 선택 | 버전 |
| --- | --- | --- |
| Java | 21 LTS | 21 |
| Spring Boot | Spring Initializr 기본값 | 4.1.2 |
| 빌드 | Maven (`backend/pom.xml`, 수업 자료와 통일) | 3.9 이상 |
| DB 접근 | Spring Data JPA (`ddl-auto=validate`) | Boot 관리 |
| 마이그레이션 | Flyway (`src/main/resources/db/migration`) | Boot 관리 |
| 스케줄러 | Spring `@Scheduled` | Boot 내장 |
| 크롤링 | Jsoup | 1.24.1 |
| 외부 API 호출 | Spring `RestClient` | Boot 내장 |
| LLM 호출 | Gemini·Ollama: `RestClient`로 REST 직접 호출 (JSON 스키마는 victools jsonschema-generator), Claude: Anthropic Java SDK | 4.38.0 / 3.6.0 |
| 검증 | Jackson + Bean Validation | Boot 관리 |
| 테스트 | JUnit 5 + Testcontainers (postgres:17) | Boot 관리 |
| 로컬 DB | Docker Compose (`compose.yaml`, postgres:17) | - |
