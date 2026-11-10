package com.buffnerf.collect.lol;

import com.buffnerf.ai.CharacterKeys;
import com.buffnerf.collect.lol.DataDragonClient.Champion;
import com.buffnerf.domain.Game;
import com.buffnerf.domain.Patch;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 롤은 한국어 챔피언 이름을 Data Dragon ID로, 다른 게임은 이름 그대로. */
@Component
class ChampionKeys implements CharacterKeys {

	private final DataDragonClient dataDragon;
	private final Map<String, Map<String, String>> byVersion = new ConcurrentHashMap<>();

	ChampionKeys(DataDragonClient dataDragon) {
		this.dataDragon = dataDragon;
	}

	@Override
	public String key(Patch patch, String name) {
		String trimmed = name.trim();
		if (patch.getGame().getSource() != Game.Source.LOL) return trimmed;
		// 롤 Patch.version은 Data Dragon 버전 그대로
		var index = byVersion.computeIfAbsent(patch.getVersion(), v -> index(dataDragon.champions(v)));
		return index.getOrDefault(normalize(trimmed), trimmed);
	}

	private volatile Map<String, String> koNames; // 영문 ID → 한국어 이름 (최신 버전)
	private volatile long loadedAt;

	@Override
	public String displayName(Game game, String key) {
		if (game.getSource() != Game.Source.LOL) return key;
		// 모르는 ID(새 챔피언 출시)면 다시 받는다. 이름이 원래 없는 키 때문에 매 요청 받지 않게 1시간에 한 번까지
		if (koNames == null || (!koNames.containsKey(key) && System.currentTimeMillis() - loadedAt > 3_600_000)) {
			loadedAt = System.currentTimeMillis();
			Map<String, String> m = new HashMap<>();
			try {
				dataDragon.champions(dataDragon.versions().getFirst()).values().forEach(c -> m.put(c.id(), c.name()));
				koNames = m;
			} catch (RuntimeException e) {
				if (koNames == null) throw e; // 다시 받기 실패는 기존 이름으로 계속 (화면이 깨지지 않게)
			}
		}
		return koNames.getOrDefault(key, key);
	}

	/** 한국어 이름과 영문 ID 둘 다 받는다. 띄어쓰기는 무시 ("리 신" = "리신"). */
	static Map<String, String> index(Map<String, Champion> champions) {
		Map<String, String> m = new HashMap<>();
		for (Champion c : champions.values()) {
			m.put(normalize(c.name()), c.id());
			m.put(normalize(c.id()), c.id());
		}
		return m;
	}

	static String normalize(String s) {
		return s.replace(" ", "").toLowerCase();
	}
}
