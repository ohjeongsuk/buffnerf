package com.buffnerf.ai;

import com.buffnerf.domain.Game;
import com.buffnerf.domain.Patch;

/** AI가 뽑은 캐릭터 이름 → 저장용 키 (롤: Data Dragon 영문 ID). 정확도 비교와 타임라인 조회의 기준. */
public interface CharacterKeys {

	String key(Patch patch, String name);

	/** 화면 표시용 이름 (롤: 영문 ID → 한국어 이름) */
	String displayName(Game game, String key);
}
