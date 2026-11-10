package com.buffnerf.collect.lol;

import com.buffnerf.TestcontainersConfiguration;
import com.buffnerf.collect.lol.DataDragonClient.Champion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AdPerLevelStoreTests {

	@Autowired DataDragonClient dataDragon;
	@Autowired JdbcClient db;

	@Test
	void usesStoredValueInsteadOfCommunityDragon() {
		db.sql("insert into champion_ad_per_level (version, champion, value) values ('99.1.1', 'Ahri', 3.4)").update();
		var zero = Map.of("Ahri", new Champion("Ahri", "아리", Map.of("attackdamageperlevel", 0.0, "hp", 600.0)));

		var out = dataDragon.withAdPerLevel("99.1.1", zero); // 저장값이 있으니 네트워크 요청 없음

		assertEquals(Map.of("attackdamageperlevel", 3.4, "hp", 600.0), out.get("Ahri").stats());
	}
}
