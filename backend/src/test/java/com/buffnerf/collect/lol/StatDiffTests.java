package com.buffnerf.collect.lol;

import com.buffnerf.collect.PatchCollector.Change;
import com.buffnerf.collect.lol.DataDragonClient.Champion;
import com.buffnerf.domain.BalanceChange.ChangeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatDiffTests {

	@Test
	void reportsOnlyChangedStatsOfExistingChampions() {
		var prev = Map.of("Aatrox", new Champion("Aatrox", "아트록스", Map.of("attackdamage", 60.0, "armor", 38.0)));
		var curr = Map.of(
				"Aatrox", new Champion("Aatrox", "아트록스", Map.of("attackdamage", 62.0, "armor", 36.5)),
				"NewChamp", new Champion("NewChamp", "신규", Map.of("armor", 30.0)));

		List<Change> out = StatDiff.diff(prev, curr, "e");

		assertEquals(List.of(
				new Change("Aatrox", "armor", ChangeType.NERF, "38", "36.5", "e"),
				new Change("Aatrox", "attackdamage", ChangeType.BUFF, "60", "62", "e")), out);
	}
}
