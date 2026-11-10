package com.buffnerf.collect.lol;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DataDragonClientTests {

	private static Double parse(String json) {
		return DataDragonClient.adPerLevel(JsonMapper.builder().build().readTree(json));
	}

	@Test
	void dropsCloneChampionsSharingKoreanName() {
		var ahri = new DataDragonClient.Champion("Ahri", "아리", Map.of());
		var clone = new DataDragonClient.Champion("Jade_Ahri", "아리", Map.of());
		var data = DataDragonClient.real(Map.of("Ahri", ahri, "Jade_Ahri", clone));
		assertEquals(Map.of("Ahri", ahri), data);
		assertEquals("Ahri", ChampionKeys.index(data).get("아리"));
	}

	@Test
	void readsAdPerLevelFromCommunityDragonFile() {
		assertEquals(3.4, parse("""
				{"other": {"x": 1},
				 "Characters/LeeSin/CharacterRecords/Root": {"damagePerLevelModifiable": {"baseValue": 3.4000000953674316}}}"""));
		assertEquals(5.0, parse("""
				{"Characters/Aatrox/CharacterRecords/Root": {"damagePerLevel": 5.0}}"""), "16.4 이전 형식");
		assertNull(parse("""
				{"Characters/Senna/CharacterRecords/Root": {"baseDamage": 50.0}}"""), "항목이 없으면 0이 아니라 모름");
		assertNull(parse("{\"x\": {}}"), "기록이 없으면 비교에서 뺀다");
	}
}
