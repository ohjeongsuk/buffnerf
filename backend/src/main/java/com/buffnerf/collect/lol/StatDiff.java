package com.buffnerf.collect.lol;

import com.buffnerf.collect.PatchCollector.Change;
import com.buffnerf.collect.lol.DataDragonClient.Champion;
import com.buffnerf.domain.BalanceChange.ChangeType;

import java.math.BigDecimal;
import java.util.*;

/** 연속 두 버전의 챔피언 기본 스탯 비교 (F2) */
final class StatDiff {

	private StatDiff() {
	}

	static List<Change> diff(Map<String, Champion> prev, Map<String, Champion> curr, String evidence) {
		List<Change> out = new ArrayList<>();
		for (Champion c : new TreeMap<>(curr).values()) {
			Champion p = prev.get(c.id());
			if (p == null) continue; // 신규 챔피언은 비교 대상 아님
			for (var e : new TreeMap<>(c.stats()).entrySet()) {
				Double before = p.stats().get(e.getKey());
				Double after = e.getValue();
				if (before == null || after == null || before.equals(after)) continue;
				// 기본 스탯은 모두 값이 클수록 유리
				ChangeType type = after > before ? ChangeType.BUFF : ChangeType.NERF;
				out.add(new Change(c.id(), e.getKey(), type, fmt(before), fmt(after), evidence));
			}
		}
		return out;
	}

	/** 60.0 → "60", 0.625 → "0.625" */
	static String fmt(double v) {
		return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
	}
}
