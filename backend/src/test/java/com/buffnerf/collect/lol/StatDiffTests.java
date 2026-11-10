package com.buffnerf.collect.lol;

import com.buffnerf.collect.PatchCollector.Change;
import com.buffnerf.collect.lol.DataDragonClient.Champion;
import com.buffnerf.collect.lol.DataDragonClient.Spell;
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

	@Test
	void skillCooldownAndCostChanges() {
		var prev = Map.of("Mel", List.of(
				new Spell("MelQ", "빛의 폭격", List.of(10.0, 9.0), List.of(70.0, 75.0)),
				new Spell("MelW", "반박", List.of(38.0, 35.0), List.of(80.0, 60.0)),
				new Spell("MelE", "옛 스킬", List.of(9.0), List.of(1.0))));
		var curr = Map.of("Mel", List.of(
				new Spell("MelQ", "빛의 폭격", List.of(10.0, 9.0), List.of(60.0, 65.0)),
				new Spell("MelW", "반박", List.of(30.0, 36.0), List.of(0.0, 0.0)),
				new Spell("MelE2", "새 스킬", List.of(1.0), List.of(1.0))));

		assertEquals(List.of(
				new Change("Mel", "Q - 빛의 폭격: 소모량", ChangeType.BUFF, "70/75", "60/65", "e"),
				new Change("Mel", "W - 반박: 재사용 대기시간", ChangeType.ADJUST, "38/35", "30/36", "e")),
				StatDiff.skills(prev, curr, "e"), "소모량 전부 0(누락)과 ID가 바뀐 스킬(리워크)은 뺀다");
	}

	@Test
	void ignoresStatThatDataDragonZeroedForEveryChampion() {
		var prev = Map.of(
				"Aatrox", new Champion("Aatrox", "아트록스", Map.of("attackdamageperlevel", 5.0, "armor", 38.0)),
				"Ahri", new Champion("Ahri", "아리", Map.of("attackdamageperlevel", 3.0, "armor", 21.0)));
		var curr = Map.of(
				"Aatrox", new Champion("Aatrox", "아트록스", Map.of("attackdamageperlevel", 0.0, "armor", 0.0)),
				"Ahri", new Champion("Ahri", "아리", Map.of("attackdamageperlevel", 0.0, "armor", 21.0)));

		assertEquals(List.of(new Change("Aatrox", "armor", ChangeType.NERF, "38", "0", "e")),
				StatDiff.diff(prev, curr, "e"), "전원 0이 된 항목만 뺀다");
	}
}
