package com.buffnerf.collect;

import com.buffnerf.TestcontainersConfiguration;
import com.buffnerf.ai.AiService;
import com.buffnerf.collect.PatchCollector.Change;
import com.buffnerf.collect.PatchCollector.CollectedPatch;
import com.buffnerf.domain.*;
import com.buffnerf.domain.BalanceChange.ChangeType;
import com.buffnerf.domain.BalanceChange.Source;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CollectServiceTests {

	@Autowired GameRepository games;
	@Autowired PatchRepository patches;
	@Autowired BalanceChangeRepository changes;
	@Autowired TransactionTemplate tx;
	@Autowired AiService ai;

	@Test
	void rediffReplacesOnlyDiffChangesOfStoredPatches() {
		Game game = games.findAll().getFirst();
		Patch p = patches.save(new Patch(game, "rediff-1", "제목 그대로", Instant.now()));
		changes.save(new BalanceChange(p, "Aatrox", "attackdamageperlevel", ChangeType.NERF, "5", "0", "옛 비교", Source.DIFF));
		changes.save(new BalanceChange(p, "Aatrox", "hp", ChangeType.BUFF, "650", "660", "체력 650 → 660", Source.AI));
		PatchCollector fake = new PatchCollector() {
			public boolean supports(Game g) {
				return true;
			}

			public List<CollectedPatch> collect(Game g, Set<String> known) {
				var c = List.of(new Change("Aatrox", "hp", ChangeType.BUFF, "650", "660", "새 비교"));
				return List.of(new CollectedPatch("rediff-1", "새 제목", null, null, null, c, null),
						new CollectedPatch("not-stored", "x", null, null, null, c, null));
			}
		};

		int n = new CollectService(List.of(fake), games, patches, changes, tx, ai).rediff(game.getId());

		assertEquals(1, n, "저장된 패치만 바꾼다");
		var mine = changes.findByPatchIds(List.of(p.getId()));
		assertEquals(List.of("새 비교"), mine.stream().filter(c -> c.getSource() == Source.DIFF).map(BalanceChange::getEvidence).toList());
		assertEquals(1, mine.stream().filter(c -> c.getSource() == Source.AI).count(), "AI 결과는 그대로");
		assertEquals("제목 그대로", patches.findById(p.getId()).orElseThrow().getTitle());
	}
}
