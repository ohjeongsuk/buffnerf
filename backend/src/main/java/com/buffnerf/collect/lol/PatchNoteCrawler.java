package com.buffnerf.collect.lol;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/** 롤 공식 한국어 패치노트 수집 (F1-b). robots.txt: Allow / (2026-11-06 확인) */
@Component
class PatchNoteCrawler {

	private static final String BASE = "https://www.leagueoflegends.com/ko-kr/news/game-updates/";
	private static final long DELAY_MS = 1000; // 요청 간격: 서버 부담 방지

	record Note(String title, Instant publishedAt, String content) {
	}

	/** @throws NoSuchElementException 페이지 없음, IllegalStateException 본문 파싱 실패 */
	Note fetch(String ddragonVersion) throws IOException, InterruptedException {
		String patch = displayVersion(ddragonVersion);
		String[] p = patch.split("\\.");
		String minor = p[1], padded = minor.length() == 1 ? "0" + minor : minor;
		// 사이트의 URL 규칙이 패치마다 다름 (2026-11 기준 실제로 섞여 있음)
		List<String> slugs = List.of(
				"league-of-legends-patch-" + p[0] + "-" + minor,
				"patch-" + p[0] + "-" + minor,
				"patch-" + p[0] + "-" + padded,
				"league-of-legends-patch-" + p[0] + "-" + padded);
		for (String slug : slugs.stream().distinct().toList()) {
			Thread.sleep(DELAY_MS);
			try {
				return parse(Jsoup.connect(BASE + slug + "-notes/").userAgent("BuffNerfBot/0.1").timeout(15_000).get());
			} catch (HttpStatusException e) {
				if (e.getStatusCode() != 404) throw e;
			}
		}
		throw new NoSuchElementException("patch notes page not found: " + patch);
	}

	// ponytail: Data Dragon 메이저 + 10 = 패치 연도 (15→25, 16→26). Riot이 규칙을 바꾸면 매핑 표로 교체.
	static String displayVersion(String ddragonVersion) {
		String[] v = ddragonVersion.split("\\.");
		return (Integer.parseInt(v[0]) + 10) + "." + v[1];
	}

	static Note parse(Document doc) {
		Element container = doc.getElementById("patch-notes-container");
		if (container == null) throw new IllegalStateException("patch-notes-container not found");
		String content = container.select("h2, h3, h4, p, li:not(:has(li))").stream()
				.map(Element::text).filter(t -> !t.isBlank())
				.collect(Collectors.joining("\n"));
		if (content.isBlank()) throw new IllegalStateException("empty patch notes body");
		Element h1 = doc.selectFirst("h1");
		Element time = doc.selectFirst("time[datetime]");
		return new Note(h1 == null ? null : h1.text(),
				time == null ? null : Instant.parse(time.attr("datetime")),
				content);
	}
}
