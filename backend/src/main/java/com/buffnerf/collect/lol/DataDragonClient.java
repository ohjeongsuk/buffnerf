package com.buffnerf.collect.lol;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
class DataDragonClient {

	private final RestClient http;

	DataDragonClient() {
		this.http = RestClient.create("https://ddragon.leagueoflegends.com");
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

	record ChampionFile(Map<String, Champion> data) {
	}

	record Champion(String id, String name, Map<String, Double> stats) {
	}
}
