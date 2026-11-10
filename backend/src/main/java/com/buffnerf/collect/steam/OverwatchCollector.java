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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 오버워치: Steam 공지에 패치노트가 없어서 공식 한국어 패치노트 페이지를 월별로 수집 (2026-11 확인).
 * robots.txt: 패치노트 경로 허용. 버전은 게시일(같은 날 핫픽스는 날짜-순번)로 구분한다 (parse 참고).
 */
@Component
class OverwatchCollector implements PatchCollector {

	static final String APP_ID = "2357570";
	static final int MONTHS = 5; // 한 달 3개 안팎 → 10개 이상
	private static final String BASE = "https://overwatch.blizzard.com/ko-kr/news/patch-notes/live/";
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final Pattern KO_DATE = Pattern.compile("(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일");
	private static final Pattern EN_DATE = Pattern.compile("([A-Z][a-z]+)\\s+(\\d{1,2}),\\s*(\\d{4})");

	@Override
	public boolean supports(Game game) {
		return APP_ID.equals(game.getExternalId());
	}

	@Override
	public List<CollectedPatch> collect(Game game, Set<String> knownVersions) {
		List<CollectedPatch> newestFirst = new ArrayList<>();
		YearMonth month = YearMonth.now(KST);
		for (int i = 0; i < MONTHS; i++, month = month.minusMonths(1)) newestFirst.addAll(parse(fetch(month)));
		// 저장된 패치도 돌려준다: 블리자드가 영문 제목을 나중에 한국어(날짜가 하루 다름)로 바꾸면 제목·날짜를 따라간다
		return newOnly(newestFirst, Set.of()).reversed();
	}

	/** knownVersions에 있는 version은 빼고, 한 번의 수집에서 같은 version이 두 번이면 하나만 (DB 고유 조건 위반으로 수집 전체가 실패하지 않게) */
	static List<CollectedPatch> newOnly(List<CollectedPatch> newestFirst, Set<String> knownVersions) {
		Set<String> seen = new HashSet<>(knownVersions);
		return newestFirst.stream().filter(p -> seen.add(p.version())).toList();
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

	/**
	 * 월별 페이지(최신순) → 패치 목록(최신순).
	 * version(중복 수집 기준)은 게시일: 블리자드가 영문 제목을 나중에 한국어로 바꿔도 같은 패치로 본다.
	 * 같은 날 글이 여럿이면(핫픽스) 오래된 글부터 "날짜", "날짜-2", "날짜-3"… 으로 따로 저장한다.
	 */
	static List<CollectedPatch> parse(Document doc) {
		List<CollectedPatch> oldestFirst = new ArrayList<>();
		Map<LocalDate, Integer> perDay = new HashMap<>();
		for (Element patch : doc.select(".PatchNotes-patch").reversed()) {
			Element title = patch.selectFirst(".PatchNotes-patchTitle");
			if (title == null) continue;
			Element date = patch.selectFirst(".PatchNotes-date");
			Instant pageDate = date == null ? null : koDate(date.text());
			String text = HtmlText.lines(patch);
			String version = title.text();
			if (pageDate != null) {
				LocalDate d = pageDate.atZone(KST).toLocalDate();
				int n = perDay.merge(d, 1, Integer::sum);
				version = n == 1 ? d.toString() : d + "-" + n;
			}
			// 화면 날짜는 제목 날짜 (페이지 게시일은 제목보다 하루 이른 경우가 많다). 제목에 날짜가 없으면 게시일
			LocalDate shown = titleDate(title.text());
			Instant published = shown != null ? shown.atStartOfDay(KST).toInstant() : pageDate;
			oldestFirst.add(new CollectedPatch(version, title(title.text(), published), published,
					text.isBlank() ? null : text, text.isBlank() ? "empty patch notes body" : null, List.of(), doc.location()));
		}
		return oldestFirst.reversed();
	}

	/** "2026년 7월 30일 패치 노트": 제목의 날짜(한/영), 없으면 게시일 */
	static String title(String raw, Instant published) {
		LocalDate d = titleDate(raw);
		if (d == null && published != null) d = published.atZone(KST).toLocalDate();
		return d == null ? raw : "%d년 %d월 %d일 패치 노트".formatted(d.getYear(), d.getMonthValue(), d.getDayOfMonth());
	}

	/** 제목 속 날짜 (한국어 "2026년 7월 30일" 또는 영어 "July 30, 2026"), 없으면 null */
	static LocalDate titleDate(String raw) {
		Matcher ko = KO_DATE.matcher(raw), en = EN_DATE.matcher(raw);
		try {
			if (ko.find())
				return LocalDate.of(Integer.parseInt(ko.group(1)), Integer.parseInt(ko.group(2)), Integer.parseInt(ko.group(3)));
			if (en.find())
				return LocalDate.of(Integer.parseInt(en.group(3)), Month.valueOf(en.group(1).toUpperCase()), Integer.parseInt(en.group(2)));
		} catch (IllegalArgumentException | DateTimeException e) {
			// 날짜처럼 보이지만 아닌 경우 (예: "Season 3, 2026")
		}
		return null;
	}

	static Instant koDate(String s) {
		Matcher m = KO_DATE.matcher(s);
		if (!m.find()) return null;
		return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)))
				.atStartOfDay(KST).toInstant();
	}
}
