package com.buffnerf.api;

import com.buffnerf.domain.BalanceChange;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * F8: 롤 기본 스탯 AI 추출 정확도. 같은 챔피언·스탯 키·변경 후 수치면 '맞음'.
 * 재현율 = 맞음 / DIFF 개수, 정밀도 = 맞음 / AI가 뽑은 기본 스탯 개수.
 */
record Accuracy(int matched, int diffCount, int aiCount, Double recall, Double precision) {

	/** Data Dragon 기본 스탯 키 (Prompts.LOL_RULES와 같은 목록) */
	static final Set<String> STAT_KEYS = Set.of("hp", "hpperlevel", "mp", "mpperlevel", "movespeed", "armor",
			"armorperlevel", "spellblock", "spellblockperlevel", "attackrange", "hpregen", "hpregenperlevel", "mpregen",
			"mpregenperlevel", "attackdamage", "attackdamageperlevel", "attackspeed", "attackspeedperlevel");

	/** AI 결과가 아직 없으면 null */
	static Accuracy of(List<BalanceChange> changes) {
		// 스킬 diff(F10)는 AI 항목명과 형식이 달라 맞춰 볼 수 없으므로 기본 스탯만 비교
		var diff = changes.stream()
				.filter(c -> c.getSource() == BalanceChange.Source.DIFF && STAT_KEYS.contains(c.getAttribute()))
				.toList();
		var ai = changes.stream()
				.filter(c -> c.getSource() == BalanceChange.Source.AI && STAT_KEYS.contains(c.getAttribute()))
				.toList();
		boolean aiRan = changes.stream().anyMatch(c -> c.getSource() == BalanceChange.Source.AI);
		if (!aiRan) return null;
		int matched = (int) diff.stream().filter(d -> ai.stream().anyMatch(a -> same(a, d))).count();
		return new Accuracy(matched, diff.size(), ai.size(),
				diff.isEmpty() ? null : (double) matched / diff.size(),
				ai.isEmpty() ? null : (double) matched / ai.size());
	}

	private static boolean same(BalanceChange a, BalanceChange d) {
		return a.getCharacterName().equals(d.getCharacterName())
				&& a.getAttribute().equals(d.getAttribute())
				&& sameNumber(a.getAfterValue(), d.getAfterValue());
	}

	static boolean sameNumber(String x, String y) {
		if (x == null || y == null) return false;
		try {
			return new BigDecimal(x.replace(",", "").trim()).compareTo(new BigDecimal(y.trim())) == 0;
		} catch (NumberFormatException e) {
			return x.trim().equals(y.trim());
		}
	}
}
