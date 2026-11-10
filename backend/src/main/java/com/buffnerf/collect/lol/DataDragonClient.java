package com.buffnerf.collect.lol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
class DataDragonClient {

	private static final Logger log = LoggerFactory.getLogger(DataDragonClient.class);

	static final String AD_PER_LEVEL = "attackdamageperlevel";
	/** 이보다 많은 챔피언의 값을 못 구하면 CommunityDragon 파일 구조가 바뀐 것으로 보고 경고 (세나는 원래 항목이 없다) */
	static final int MISSING_WARN = 5;

	private final RestClient http;
	private final RestClient cdragon;

	DataDragonClient() {
		// 응답이 멈춘 요청 하나가 수집 전체를 붙잡지 않게 (기본값은 무제한)
		var factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(15));
		factory.setReadTimeout(Duration.ofSeconds(15));
		this.http = RestClient.builder().requestFactory(factory).baseUrl("https://ddragon.leagueoflegends.com").build();
		this.cdragon = RestClient.builder().requestFactory(factory).baseUrl("https://raw.communitydragon.org").build();
	}

	/** 최신순 버전 목록 */
	List<String> versions() {
		return http.get().uri("/api/versions.json").retrieve().body(new ParameterizedTypeReference<>() {
		});
	}

	/** 챔피언 영문 ID → 챔피언 (한국어 이름, 기본 스탯) */
	Map<String, Champion> champions(String version) {
		return real(http.get().uri("/cdn/{v}/data/ko_KR/champion.json", version)
				.retrieve().body(ChampionFile.class).data());
	}

	/**
	 * 16.15.1에는 이벤트 모드용 복제 챔피언 60개(Jade_Ahri 등)가 진짜와 같은 한국어 이름으로 섞여 있다.
	 * 그대로 두면 "아리"가 Jade_Ahri로 연결된다. 진짜 ID는 영문·숫자뿐이라 그것만 남긴다.
	 */
	static Map<String, Champion> real(Map<String, Champion> data) {
		Map<String, Champion> out = new HashMap<>(data);
		out.keySet().removeIf(id -> !id.matches("[A-Za-z0-9]+"));
		return out;
	}

	/**
	 * 스탯 비교용. Data Dragon은 16.5.1부터 레벨당 공격력을 전 챔피언 0으로 준다.
	 * 그럴 때만 그 값을 CommunityDragon(게임 데이터 원본)에서 채운다. 못 구한 챔피언은 그 항목을 비교에서 뺀다.
	 */
	// ponytail: 챔피언마다 요청 1번(새 패치 하나에 이전·새 버전 합쳐 약 700번, 2주에 한 번). 느려지면 병렬로 바꾼다.
	Map<String, Champion> championsForDiff(String version) {
		var data = champions(version);
		if (data.values().stream().anyMatch(c -> !Double.valueOf(0).equals(c.stats().get(AD_PER_LEVEL)))) return data;
		String patch = version.replaceFirst("^(\\d+\\.\\d+)\\..*", "$1"); // 16.22.1 → 16.22
		Map<String, Champion> out = new HashMap<>();
		List<String> missing = new ArrayList<>();
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
			if (v == null) {
				stats.remove(AD_PER_LEVEL);
				missing.add(c.id());
			} else stats.put(AD_PER_LEVEL, v);
			out.put(c.id(), new Champion(c.id(), c.name(), stats));
		}
		if (missing.size() > MISSING_WARN)
			log.warn("CommunityDragon {}: 레벨당 공격력을 {}/{}명 못 구해 비교에서 뺐다. 파일 구조 확인 필요: {}",
					patch, missing.size(), data.size(), missing);
		return out;
	}

	/** CommunityDragon 챔피언 파일에서 레벨당 공격력. 기록이나 항목을 못 찾으면 null (구조가 바뀌어도 0으로 오인하지 않게) */
	static Double adPerLevel(JsonNode file) {
		if (file == null) return null;
		for (var e : file.properties()) {
			if (!e.getKey().endsWith("/CharacterRecords/Root")) continue;
			JsonNode v = e.getValue().path("damagePerLevelModifiable").path("baseValue");
			if (v.isMissingNode()) v = e.getValue().path("damagePerLevel"); // 16.4 이전 형식
			return v.isNumber() ? Math.round(v.asDouble() * 10000) / 10000.0 : null; // 3.4000000953 → 3.4
		}
		return null;
	}

	record ChampionFile(Map<String, Champion> data) {
	}

	record Champion(String id, String name, Map<String, Double> stats) {
	}
}
