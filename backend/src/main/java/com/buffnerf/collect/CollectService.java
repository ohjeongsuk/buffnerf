package com.buffnerf.collect;

import com.buffnerf.ai.AiService;
import com.buffnerf.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class CollectService {

	private static final Logger log = LoggerFactory.getLogger(CollectService.class);

	private final List<PatchCollector> collectors;
	private final GameRepository games;
	private final PatchRepository patches;
	private final BalanceChangeRepository changes;
	private final TransactionTemplate tx;
	private final AiService ai;

	CollectService(List<PatchCollector> collectors, GameRepository games, PatchRepository patches,
			BalanceChangeRepository changes, TransactionTemplate tx, AiService ai) {
		this.collectors = collectors;
		this.games = games;
		this.patches = patches;
		this.changes = changes;
		this.tx = tx;
		this.ai = ai;
	}

	/** 게임 하나 수집. 새로 저장한 패치 수를 돌려준다. */
	public int collect(Long gameId) {
		Game game = games.findById(gameId).orElseThrow(() -> new NoSuchElementException("game " + gameId));
		PatchCollector collector = collectors.stream().filter(c -> c.supports(game)).findFirst()
				.orElseThrow(() -> new NoSuchElementException("no collector for " + game.getName()));

		// 외부 호출은 트랜잭션 밖에서, 저장만 한 트랜잭션으로 (중간 실패 시 반쯤 저장되지 않게)
		var collected = collector.collect(game, patches.findVersionsByGameId(gameId));
		tx.executeWithoutResult(s -> {
			for (var cp : collected) {
				Patch p = new Patch(game, cp.version(), cp.title(), cp.publishedAt());
				p.setSourceUrl(cp.sourceUrl());
				if (cp.rawContent() != null) p.setContent(cp.rawContent(), Patch.ContentSource.CRAWLED);
				else p.markContentFailed(cp.contentError());
				patches.save(p);
				changes.saveAll(cp.changes().stream().map(ch -> new BalanceChange(p, ch.characterName(),
						ch.attribute(), ch.changeType(), ch.before(), ch.after(), ch.evidence(),
						BalanceChange.Source.DIFF)).toList());
			}
		});
		log.info("collected {} patches for {}", collected.size(), game.getName());
		if (!collected.isEmpty()) ai.processPendingAsync();
		return collected.size();
	}

	// ponytail: 실패 시 재시도 없이 기록만 하고 다음 게임으로. 외부 API가 자주 끊기면 재시도 추가.
	@Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
	public void collectAll() {
		for (Game g : games.findAll()) {
			try {
				collect(g.getId());
			} catch (Exception e) {
				log.warn("collect failed for {}: {}", g.getName(), e.toString());
			}
		}
	}
}
