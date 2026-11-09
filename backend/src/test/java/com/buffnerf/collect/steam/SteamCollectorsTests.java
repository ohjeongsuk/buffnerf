package com.buffnerf.collect.steam;

import com.buffnerf.collect.HtmlText;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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
		assertEquals("오버워치 패치 노트 - 2026년 11월 4일", p.version());
		assertEquals(Instant.parse("2026-11-02T15:00:00Z"), p.publishedAt()); // KST 자정
		assertEquals("https://overwatch.blizzard.com/ko-kr/news/patch-notes/live/2026/11/", p.sourceUrl());
		assertTrue(p.rawContent().contains("트레이서\n펄스 쌍권총 피해량이 6에서 5로 감소합니다."), p.rawContent());
	}

	@Test
	void htmlTextOneLinePerInnermostBlock() {
		var root = Jsoup.parse("<div><p>a <b>b</b></p><ul><li>c</li><li>&nbsp;</li></ul><div>d</div></div>").body();
		assertEquals("a b\nc\nd", HtmlText.lines(root));
	}
}
