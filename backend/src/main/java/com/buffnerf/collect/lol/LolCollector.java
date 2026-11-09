package com.buffnerf.collect.lol;

import com.buffnerf.collect.PatchCollector;
import com.buffnerf.collect.lol.DataDragonClient.Champion;
import com.buffnerf.collect.PatchCollector.Change;
import com.buffnerf.domain.Game;
import org.springframework.stereotype.Component;

import java.util.*;

/** 롤: Data Dragon 버전 목록 + 버전 간 기본 스탯 diff (F1, F2) + 공식 패치노트 원문 (F1-b) */
@Component
class LolCollector implements PatchCollector {

	static final int RECENT = 20;

	private final DataDragonClient dataDragon;
	private final PatchNoteCrawler crawler;

	LolCollector(DataDragonClient dataDragon, PatchNoteCrawler crawler) {
		this.dataDragon = dataDragon;
		this.crawler = crawler;
	}

	@Override
	public boolean supports(Game game) {
		return game.getSource() == Game.Source.LOL;
	}

	@Override
	public List<CollectedPatch> collect(Game game, Set<String> knownVersions) {
		// 최신순. 마지막 1개는 diff 기준으로만 쓴다.
		List<String> versions = dataDragon.versions().stream()
				.filter(v -> v.matches("\\d+\\.\\d+\\.\\d+"))
				.limit(RECENT + 1)
				.toList();

		Map<String, Map<String, Champion>> cache = new HashMap<>();
		List<CollectedPatch> out = new ArrayList<>();
		for (int i = versions.size() - 2; i >= 0; i--) {
			String v = versions.get(i);
			if (knownVersions.contains(v)) continue;
			String prev = versions.get(i + 1);
			var changes = StatDiff.diff(
					cache.computeIfAbsent(prev, dataDragon::champions),
					cache.computeIfAbsent(v, dataDragon::champions),
					"Data Dragon " + prev + " → " + v);
			out.add(withNote(v, changes));
		}
		return out;
	}

	/** 원문 크롤링. 실패해도 패치는 저장하고 이유만 남긴다 (관리자가 수동 등록). */
	private CollectedPatch withNote(String version, List<Change> changes) {
		try {
			var note = crawler.fetch(version);
			return new CollectedPatch(version, note.title(), note.publishedAt(), note.content(), null, changes, note.url());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		} catch (Exception e) {
			return new CollectedPatch(version, "패치 " + PatchNoteCrawler.displayVersion(version), null, null,
					e.toString(), changes, null);
		}
	}
}
