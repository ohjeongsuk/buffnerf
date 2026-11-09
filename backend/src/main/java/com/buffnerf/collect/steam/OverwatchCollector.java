package com.buffnerf.collect.steam;

import com.buffnerf.collect.HtmlText;
import com.buffnerf.collect.PatchCollector;
import com.buffnerf.domain.Game;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 오버워치: Steam 공지에 패치노트가 없어서 공식 한국어 패치노트 페이지를 월별로 수집 (2026-11 확인).
 * robots.txt: 패치노트 경로 허용. 같은 날 핫픽스가 있어 버전은 패치 제목으로 구분한다.
 */
@Component
class OverwatchCollector implements PatchCollector {

	static final String APP_ID = "2357570";
	static final int MONTHS = 5; // 한 달 3개 안팎 → 10개 이상
	private static final String BASE = "https://overwatch.blizzard.com/ko-kr/news/patch-notes/live/";
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final Pattern KO_DATE = Pattern.compile("(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일");

	@Override
	public boolean supports(Game game) {
		return APP_ID.equals(game.getExternalId());
	}

	@Override
	public List<CollectedPatch> collect(Game game, Set<String> knownVersions) {
		List<CollectedPatch> newestFirst = new ArrayList<>();
		YearMonth month = YearMonth.now(KST);
		for (int i = 0; i < MONTHS; i++, month = month.minusMonths(1)) {
			for (CollectedPatch p : parse(fetch(month)))
				if (!knownVersions.contains(p.version())) newestFirst.add(p);
		}
		return newestFirst.reversed();
	}

	private Document fetch(YearMonth m) {
		try {
			Thread.sleep(1000); // 요청 간격
			// 끝의 '/'가 없으면 빈 페이지가 온다
			return Jsoup.connect(BASE + m.getYear() + "/" + m.getMonthValue() + "/")
					.userAgent("BuffNerfBot/0.1").timeout(15_000).get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		} catch (IOException e) {
			throw new IllegalStateException("overwatch patch notes " + m + ": " + e, e);
		}
	}

	static List<CollectedPatch> parse(Document doc) {
		List<CollectedPatch> out = new ArrayList<>();
		for (Element patch : doc.select(".PatchNotes-patch")) {
			Element title = patch.selectFirst(".PatchNotes-patchTitle");
			if (title == null) continue;
			Element date = patch.selectFirst(".PatchNotes-date");
			String text = HtmlText.lines(patch);
			out.add(new CollectedPatch(title.text(), title.text(), date == null ? null : koDate(date.text()),
					text.isBlank() ? null : text, text.isBlank() ? "empty patch notes body" : null, List.of(), doc.location()));
		}
		return out;
	}

	static Instant koDate(String s) {
		Matcher m = KO_DATE.matcher(s);
		if (!m.find()) return null;
		return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)))
				.atStartOfDay(KST).toInstant();
	}
}
