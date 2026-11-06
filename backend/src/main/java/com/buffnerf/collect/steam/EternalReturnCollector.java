package com.buffnerf.collect.steam;

import com.buffnerf.collect.HtmlText;
import com.buffnerf.collect.PatchCollector;
import com.buffnerf.domain.Game;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 이터널 리턴: Steam 공지에서 "PATCH NOTES x.y" 글을 찾고, 본문 링크의 공식 사이트 한국어 페이지를 수집.
 * Steam 공지에는 요약과 링크만 있다 (2026-11 확인). robots.txt: 전체 허용.
 */
@Component
class EternalReturnCollector implements PatchCollector {

	static final String APP_ID = "1049590";
	static final int RECENT = 12;
	private static final Pattern TITLE = Pattern.compile("(?i)^\\s*patch notes\\s+(\\d+\\.\\d+)\\b");
	private static final Pattern LINK = Pattern.compile("playeternalreturn\\.com/posts/news/(\\d+)");

	private final RestClient steam = RestClient.create("https://api.steampowered.com");

	@Override
	public boolean supports(Game game) {
		return APP_ID.equals(game.getExternalId());
	}

	@Override
	public List<CollectedPatch> collect(Game game, Set<String> knownVersions) {
		News news = steam.get()
				.uri("/ISteamNews/GetNewsForApp/v2/?appid={id}&count=400&maxlength=0&feeds=steam_community_announcements",
						APP_ID)
				.retrieve().body(News.class);

		// 최신순 → 버전별 첫 글만, 최근 RECENT개
		Map<String, NewsItem> byVersion = new LinkedHashMap<>();
		for (NewsItem n : news.appnews().newsitems()) {
			String v = version(n.title());
			if (v != null) byVersion.putIfAbsent(v, n);
		}
		List<CollectedPatch> out = new ArrayList<>();
		var recent = new ArrayList<>(byVersion.entrySet()).subList(0, Math.min(RECENT, byVersion.size()));
		for (var e : recent.reversed()) {
			if (knownVersions.contains(e.getKey())) continue;
			out.add(fetch(e.getKey(), e.getValue()));
		}
		return out;
	}

	private CollectedPatch fetch(String version, NewsItem n) {
		Instant published = Instant.ofEpochSecond(n.date());
		Matcher m = LINK.matcher(n.contents());
		if (!m.find())
			return new CollectedPatch(version, n.title(), published, null, "no patch notes link in Steam post", List.of());
		try {
			Thread.sleep(1000); // 요청 간격
			var doc = Jsoup.connect("https://playeternalreturn.com/posts/news/" + m.group(1) + "?hl=ko-KR")
					.userAgent("BuffNerfBot/0.1").timeout(15_000).get();
			Element body = doc.selectFirst(".er-article-detail__content");
			if (body == null) throw new IllegalStateException("er-article-detail__content not found");
			String text = HtmlText.lines(body);
			if (text.isBlank()) throw new IllegalStateException("empty patch notes body");
			Element title = doc.selectFirst(".er-article-detail__title");
			return new CollectedPatch(version, title == null ? n.title() : title.text(), published, text, null, List.of());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		} catch (Exception e) {
			return new CollectedPatch(version, n.title(), published, null, e.toString(), List.of());
		}
	}

	static String version(String title) {
		Matcher m = TITLE.matcher(title);
		return m.find() ? m.group(1) : null;
	}

	record News(AppNews appnews) {
	}

	record AppNews(List<NewsItem> newsitems) {
	}

	record NewsItem(String title, String contents, long date) {
	}
}
