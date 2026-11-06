# BuffNerf

롤·오버워치·이터널 리턴 패치노트를 AI로 요약하고 캐릭터별 버프/너프를 타임라인으로 보여주는 풀스택 웹 서비스. 2주 MVP.

- 요구사항: `docs/PRD.md` (원본: https://claude.ai/artifact/7WCh2biP4gU8fwjQb2vSQD)
- 확정: 프론트 Next.js(Amplify), 백엔드 EC2, DB RDS PostgreSQL, LLM Claude Sonnet 5.5(`claude-sonnet-5-5`)
- 미정: 백엔드 언어/프레임워크 → `docs/TECH_STACK.md`에서 결정

## 규칙

- API 키(LLM, Steam, 관리자 키)는 서버 환경변수로만. 커밋 금지.
- 같은 패치·같은 promptVersion으로 LLM 재호출 금지 (AiRun 확인).
