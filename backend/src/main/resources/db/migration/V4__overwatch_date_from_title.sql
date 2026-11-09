-- 오버워치 화면 날짜를 제목 날짜에 맞춘다 (페이지 게시일은 제목보다 하루 이른 경우가 많았다).
-- version(중복 수집 기준)은 V3에서 페이지 게시일로 정했으므로 그대로 둔다. OverwatchCollector와 같은 규칙.
-- 제목은 "2026년 11월 6일 패치 노트" 형식으로 통일돼 있다.
UPDATE patch p
SET published_at = make_date((regexp_match(p.title, '^(\d{4})년 (\d{1,2})월 (\d{1,2})일'))[1]::int,
                             (regexp_match(p.title, '^(\d{4})년 (\d{1,2})월 (\d{1,2})일'))[2]::int,
                             (regexp_match(p.title, '^(\d{4})년 (\d{1,2})월 (\d{1,2})일'))[3]::int)::timestamp
                   AT TIME ZONE 'Asia/Seoul'
FROM game g
WHERE g.id = p.game_id
  AND g.external_id = '2357570'
  AND p.title ~ '^\d{4}년 \d{1,2}월 \d{1,2}일';
