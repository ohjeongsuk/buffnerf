package com.buffnerf.collect;

import com.buffnerf.domain.BalanceChange;
import com.buffnerf.domain.Game;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 게임 소스별 수집기. 새 게임은 구현체 하나만 추가한다. */
public interface PatchCollector {

	boolean supports(Game game);

	/**
	 * 아직 저장되지 않은 패치를 오래된 순으로 돌려준다.
	 * @param knownVersions 이미 저장된 버전 (다시 받지 않는다)
	 */
	List<CollectedPatch> collect(Game game, Set<String> knownVersions);

	/**
	 * rawContent가 null이면 원문 미확보(PENDING_CONTENT)로 저장되고 contentError가 실패 이유로 남는다.
	 * changes는 코드로 계산한 DIFF 변경. sourceUrl은 공식 패치노트 주소 (모르면 null).
	 */
	record CollectedPatch(String version, String title, Instant publishedAt, String rawContent,
			String contentError, List<Change> changes, String sourceUrl) {
	}

	record Change(String characterName, String attribute, BalanceChange.ChangeType changeType,
			String before, String after, String evidence) {
	}
}
