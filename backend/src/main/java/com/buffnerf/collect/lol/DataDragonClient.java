package com.buffnerf.collect.lol;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
class DataDragonClient {

	static final String AD_PER_LEVEL = "attackdamageperlevel";

	private final RestClient http;
	private final RestClient cdragon;

	DataDragonClient() {
		this.http = RestClient.create("https://ddragon.leagueoflegends.com");
		this.cdragon = RestClient.create("https://raw.communitydragon.org");
	}

	/** 최신순 버전 목록 */
	List<String> versions() {
		return http.get().uri("/api/versions.json").retrieve().body(new ParameterizedTypeReference<>() {
		});
	}

	/** 챔피언 영문 ID → 챔피언 (한국어 이름, 기본 스탯) */
	Map<String, Champion> champions(String version) {
		return http.get().uri("/cdn/{v}/data/ko_KR/champion.json", version)
				.retrieve().body(ChampionFile.class).data();
	}

	/**
	 * 스탯 비교용. Data Dragon은 16.5.1부터 레벨당 공격력을 전 챔피언 0으로 준다.
	 * 그럴 때만 그 값을 CommunityDragon(게임 데이터 원본)에서 채운다. 못 구한 챔피언은 그 항목을 비교에서 뺀다.
	 */
	// ponytail: 챔피언마다 요청 1번(새 패치 하나에 약 350번, 2주에 한 번). 느려지면 병렬로 바꾼다.
	Map<String, Champion> championsForDiff(String version) {
		var data = champions(version);
		if (data.values().stream().anyMatch(c -> !Double.valueOf(0).equals(c.stats().get(AD_PER_LEVEL)))) return data;
		String patch = version.replaceFirst("^(\\d+\\.\\d+)\\..*", "$1"); // 16.22.1 → 16.22
		Map<String, Champion> out = new HashMap<>();
		for (Champion c : data.values()) {
			Map<String, Double> stats = new HashMap<>(c.stats());
			Double v = null;
			try {
				String id = c.id().toLowerCase();
				v = adPerLevel(cdragon.get().uri("/{p}/game/data/characters/{id}/{id}.bin.json", patch, id, id)
						.retrieve().body(JsonNode.class));
			} catch (RuntimeException e) {
				// 한 챔피언 실패로 수집 전체를 멈추지 않는다
			}
			if (v == null) stats.remove(AD_PER_LEVEL);
			else stats.put(AD_PER_LEVEL, v);
			out.put(c.id(), new Champion(c.id(), c.name(), stats));
		}
		return out;
	}

	/** CommunityDragon 챔피언 파일에서 레벨당 공격력. 기록은 있는데 항목이 없으면 0 (세나), 기록을 못 찾으면 null */
	static Double adPerLevel(JsonNode file) {
		if (file == null) return null;
		for (var e : file.properties()) {
			if (!e.getKey().endsWith("/CharacterRecords/Root")) continue;
			JsonNode v = e.getValue().path("damagePerLevelModifiable").path("baseValue");
			if (v.isMissingNode()) v = e.getValue().path("damagePerLevel"); // 16.4 이전 형식
			return v.isNumber() ? Math.round(v.asDouble() * 10000) / 10000.0 : 0.0; // 3.4000000953 → 3.4
		}
		return null;
	}

	record ChampionFile(Map<String, Champion> data) {
	}

	record Champion(String id, String name, Map<String, Double> stats) {
	}
}
