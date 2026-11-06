package com.buffnerf.collect.lol;

import com.buffnerf.collect.lol.DataDragonClient.Champion;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChampionKeysTests {

	@Test
	void mapsKoreanNameAndIdIgnoringSpaces() {
		var index = ChampionKeys.index(Map.of(
				"LeeSin", new Champion("LeeSin", "리 신", Map.of()),
				"MonkeyKing", new Champion("MonkeyKing", "오공", Map.of())));
		assertEquals("LeeSin", index.get(ChampionKeys.normalize("리신")));
		assertEquals("MonkeyKing", index.get(ChampionKeys.normalize("오공")));
		assertEquals("LeeSin", index.get(ChampionKeys.normalize("leesin")));
	}
}
