package com.buffnerf.collect.steam;

import com.buffnerf.collect.HtmlText;
import com.buffnerf.collect.PatchCollector;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SteamCollectorsTests {

	@Test
	void eternalReturnVersionFromSteamTitle() {
		assertEquals("12.7", EternalReturnCollector.version("PATCH NOTES 12.7 - OCTOBER 29TH, 2026"));
		assertEquals("11.6", EternalReturnCollector.version("Patch Notes 11.6 - July 9th, 2026"));
		assertNull(EternalReturnCollector.version("Season 12 Ranked Rewards"));
		assertNull(EternalReturnCollector.version("Hotfix Patch Notes - Oct 30"));
	}

	@Test
	void overwatchPatchesFromMonthPage() {
		var doc = Jsoup.parse("""
				<div class="PatchNotes-patch PatchNotes-live">
				  <div class="PatchNotes-date">2026년 11월 3일</div>
				  <h3 class="PatchNotes-patchTitle">오버워치 패치 노트 - 2026년 11월 4일</h3>
				  <div class="PatchNotes-section"><h4>영웅 업데이트</h4>
				    <div class="PatchNotesHeroUpdate-name">트레이서</div>
				    <ul><li>펄스 쌍권총 피해량이 6에서 <b>5</b>로 감소합니다.</li></ul></div>
				</div>""", "https://overwatch.blizzard.com/ko-kr/news/patch-notes/live/2026/11/");
		var patches = OverwatchCollector.parse(doc);
		assertEquals(1, patches.size());
		var p = patches.getFirst();
		assertEquals("2026-11-03", p.version(), "중복 수집 기준은 게시일(KST)");
		assertEquals(Instant.parse("2026-11-02T15:00:00Z"), p.publishedAt()); // KST 자정
		assertEquals("https://overwatch.blizzard.com/ko-kr/news/patch-notes/live/2026/11/", p.sourceUrl());
		assertEquals("2026년 11월 4일 패치 노트", p.title());
		assertTrue(p.rawContent().contains("트레이서\n펄스 쌍권총 피해량이 6에서 5로 감소합니다."), p.rawContent());
	}

	@Test
	void overwatchRetitledPatchIsNotCollectedAgain() {
		// 영문 제목으로 저장된 패치를 블리자드가 한국어 제목으로 바꿔도 게시일이 같으면 같은 패치
		var retitled = new PatchCollector.CollectedPatch("2026-11-05", "2026년 11월 6일 패치 노트", null, "본문", null, List.of(), null);
		var fresh = new PatchCollector.CollectedPatch("2026-11-12", "2026년 11월 12일 패치 노트", null, "본문", null, List.of(), null);
		var sameDaySecond = new PatchCollector.CollectedPatch("2026-11-12", "2026년 11월 12일 패치 노트", null, "다른 글", null, List.of(), null);

		var result = OverwatchCollector.newOnly(List.of(fresh, sameDaySecond, retitled), Set.of("2026-11-05"));
		assertEquals(List.of(fresh), result, "저장된 날짜는 빼고, 같은 날짜가 두 번이면 최신 글 하나만");
	}

	@Test
	void overwatchTitleUnified() {
		Instant aug4 = Instant.parse("2026-08-03T15:00:00Z"); // KST 8월 4일
		assertEquals("2026년 8월 12일 패치 노트", OverwatchCollector.title("오버워치 2 패치 노트 - 2026년 8월 12일", aug4));
		assertEquals("2026년 7월 30일 패치 노트", OverwatchCollector.title("Overwatch Retail Patch Notes – July 30, 2026", aug4));
		assertEquals("2026년 8월 4일 패치 노트", OverwatchCollector.title("콘솔 플랫폼에서의 비허가 주변기기 사용 관련 업데이트", aug4));
		assertEquals("2026년 8월 4일 패치 노트", OverwatchCollector.title("Season 3, 2026 update", aug4)); // 날짜 아님 → 게시일
		assertEquals("이름만 있는 글", OverwatchCollector.title("이름만 있는 글", null));
	}

	@Test
	void htmlTextOneLinePerInnermostBlock() {
		var root = Jsoup.parse("<div><p>a <b>b</b></p><ul><li>c</li><li>&nbsp;</li></ul><div>d</div></div>").body();
		assertEquals("a b\nc\nd", HtmlText.lines(root));
	}
}
