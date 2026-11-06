package com.buffnerf.collect.lol;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PatchNoteCrawlerTests {

	@Test
	void displayVersion() {
		assertEquals("26.22", PatchNoteCrawler.displayVersion("16.22.1"));
		assertEquals("25.5", PatchNoteCrawler.displayVersion("15.5.1"));
	}

	@Test
	void parsesTitleDateAndBody() {
		var doc = Jsoup.parse("""
				<h1 data-testid="title">리그 오브 레전드 26.22 패치 노트</h1>
				<time dateTime="2026-11-03T19:00:00.000Z"></time>
				<div id="patch-notes-container">
				  <h2>챔피언</h2><h3>아트록스</h3>
				  <ul><li>기본 공격력: 60 ⇒ 62</li><li><ul><li>중첩</li></ul></li></ul>
				  <p> </p>
				</div>""");
		var note = PatchNoteCrawler.parse(doc);
		assertEquals("리그 오브 레전드 26.22 패치 노트", note.title());
		assertEquals(Instant.parse("2026-11-03T19:00:00Z"), note.publishedAt());
		assertEquals("챔피언\n아트록스\n기본 공격력: 60 ⇒ 62\n중첩", note.content());
	}

	@Test
	void missingContainerFails() {
		assertThrows(IllegalStateException.class, () -> PatchNoteCrawler.parse(Jsoup.parse("<p>x</p>")));
	}
}
