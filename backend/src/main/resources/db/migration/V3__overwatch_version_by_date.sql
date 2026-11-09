-- 오버워치 패치의 중복 수집 기준(version)을 사이트 제목에서 게시일(KST)로 바꾼다.
-- 블리자드가 영문 제목을 나중에 한국어로 바꾸면 같은 패치가 새 패치로 다시 수집되던 문제 (OverwatchCollector와 같은 규칙)
UPDATE patch p
SET version = to_char(p.published_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD')
FROM game g
WHERE g.id = p.game_id
  AND g.external_id = '2357570'
  AND p.published_at IS NOT NULL;
