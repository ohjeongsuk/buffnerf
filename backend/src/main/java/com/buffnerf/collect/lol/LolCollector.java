package com.buffnerf.collect.lol;

import com.buffnerf.collect.PatchCollector;
import com.buffnerf.collect.lol.DataDragonClient.Champion;
import com.buffnerf.collect.lol.DataDragonClient.Spell;
import com.buffnerf.collect.PatchCollector.Change;
import com.buffnerf.domain.Game;
import org.springframework.stereotype.Component;

import java.util.*;

/** 롤: Data Dragon 버전 목록 + 버전 간 기본 스탯·스킬 diff (F1, F2, F10) + 공식 패치노트 원문 (F1-b) */
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
		Map<String, Map<String, List<Spell>>> spellCache = new HashMap<>();
		List<CollectedPatch> out = new ArrayList<>();
		for (int i = versions.size() - 2; i >= 0; i--) {
			String v = versions.get(i);
			if (knownVersions.contains(v)) continue;
			String prev = versions.get(i + 1);
			String evidence = "Data Dragon " + prev + " → " + v;
			var changes = new ArrayList<>(StatDiff.diff(
					cache.computeIfAbsent(prev, dataDragon::championsForDiff),
					cache.computeIfAbsent(v, dataDragon::championsForDiff), evidence));
			changes.addAll(StatDiff.skills(
					spellCache.computeIfAbsent(prev, dataDragon::spells),
					spellCache.computeIfAbsent(v, dataDragon::spells), evidence));
			out.add(withNote(v, changes));
		}
		return out;
	}

	/** 화면 제목 통일: "26.22 패치 노트" (사이트 제목은 "리그 오브 레전드 " 접두가 있다 없다 한다) */
	static String title(String ddragonVersion) {
		return PatchNoteCrawler.displayVersion(ddragonVersion) + " 패치 노트";
	}

	/** 원문 크롤링. 실패해도 패치는 저장하고 이유만 남긴다 (관리자가 수동 등록). */
	private CollectedPatch withNote(String version, List<Change> changes) {
		try {
			var note = crawler.fetch(version);
			return new CollectedPatch(version, title(version), note.publishedAt(), note.content(), null, changes, note.url());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		} catch (Exception e) {
			return new CollectedPatch(version, title(version), null, null,
					e.toString(), changes, null);
		}
	}
}
