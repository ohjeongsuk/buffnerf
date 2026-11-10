package com.buffnerf.collect.lol;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DataDragonClientTests {

	private static Double parse(String json) {
		return DataDragonClient.adPerLevel(JsonMapper.builder().build().readTree(json));
	}

	@Test
	void readsAdPerLevelFromCommunityDragonFile() {
		assertEquals(3.4, parse("""
				{"other": {"x": 1},
				 "Characters/LeeSin/CharacterRecords/Root": {"damagePerLevelModifiable": {"baseValue": 3.4000000953674316}}}"""));
		assertEquals(5.0, parse("""
				{"Characters/Aatrox/CharacterRecords/Root": {"damagePerLevel": 5.0}}"""), "16.4 이전 형식");
		assertEquals(0.0, parse("""
				{"Characters/Senna/CharacterRecords/Root": {"baseDamage": 50.0}}"""), "항목이 없는 챔피언은 0");
		assertNull(parse("{\"x\": {}}"), "기록이 없으면 비교에서 뺀다");
	}
}
