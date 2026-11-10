package com.buffnerf.api;

import com.buffnerf.TestcontainersConfiguration;
import com.buffnerf.domain.*;
import com.buffnerf.domain.BalanceChange.ChangeType;
import com.buffnerf.domain.BalanceChange.Source;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PublicApiTests {

	@Autowired WebApplicationContext ctx;
	@Autowired GameRepository games;
	@Autowired PatchRepository patches;
	@Autowired BalanceChangeRepository changes;

	@Test
	void patchListDetailTimelineAndStats() throws Exception {
		// 롤이 아닌 게임: Data Dragon 호출 없이 이름 그대로
		Game er = games.findAll().stream().filter(g -> g.getName().equals("Eternal Return")).findFirst().orElseThrow();
		Patch p1 = patches.save(new Patch(er, "pub-1", "패치 1", Instant.parse("2026-10-01T00:00:00Z")));
		Patch p2 = patches.save(new Patch(er, "pub-2", "패치 2", Instant.parse("2026-10-15T00:00:00Z")));
		changes.save(new BalanceChange(p1, "재키", "공격력", ChangeType.BUFF, "30", "32", "공격력 30 → 32", Source.AI));
		changes.save(new BalanceChange(p1, "재키", "방어력", ChangeType.BUFF, "20", "21", "방어력 20 → 21", Source.AI));
		changes.save(new BalanceChange(p2, "재키", "공격력", ChangeType.NERF, "32", "31", "공격력 32 → 31", Source.AI));
		changes.save(new BalanceChange(p2, "아야", "체력", ChangeType.NERF, "700", "680", "체력 700 → 680", Source.AI));
		// 다른 게임 모드 변경은 목록의 상향·하향 수에 넣지 않는다
		changes.save(new BalanceChange(p2, "얀", "[아레나] 체력", ChangeType.NERF, "700", "680", "체력 700 → 680", Source.AI));
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(ctx).build();

		mvc.perform(get("/api/games")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));

		mvc.perform(get("/api/games/" + er.getId() + "/patches"))
				.andExpect(jsonPath("$.content[0].version").value("pub-2")) // 최신순
				.andExpect(jsonPath("$.content[0].nerfs").value(2))
				.andExpect(jsonPath("$.content[1].buffs").value(1))        // 재키 2항목 = 1명
				.andExpect(jsonPath("$.page.totalPages").value(1));         // 프론트가 쓰는 고정 형식

		mvc.perform(get("/api/patches/" + p1.getId()))
				.andExpect(jsonPath("$.changes.length()").value(2))
				.andExpect(jsonPath("$.accuracy").doesNotExist());

		mvc.perform(get("/api/games/" + er.getId() + "/characters"))
				.andExpect(jsonPath("$[?(@.key == '재키')].patchCount").value(hasItem(2)));

		mvc.perform(get("/api/games/" + er.getId() + "/characters/재키/timeline"))
				.andExpect(jsonPath("$.entries.length()").value(2))
				.andExpect(jsonPath("$.entries[0].version").value("pub-1")) // 오래된 순
				.andExpect(jsonPath("$.entries[1].changes[0].changeType").value("NERF"));

		mvc.perform(get("/api/games/" + er.getId() + "/stats"))
				.andExpect(jsonPath("$[0].version").value("pub-1"));

		mvc.perform(get("/api/patches/999999")).andExpect(status().isNotFound());
	}
}
