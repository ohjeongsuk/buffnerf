package com.buffnerf.collect.lol;

import com.buffnerf.collect.PatchCollector.Change;
import com.buffnerf.collect.lol.DataDragonClient.Champion;
import com.buffnerf.collect.lol.DataDragonClient.Spell;
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
				// Data Dragon이 항목을 더는 채우지 않으면 전 챔피언이 0이 된다 (16.5.1의 attackdamageperlevel). 실제 변경이 아니다
				if (after == 0 && curr.values().stream().allMatch(x -> Double.valueOf(0).equals(x.stats().get(e.getKey())))) continue;
				// 기본 스탯은 모두 값이 클수록 유리
				ChangeType type = after > before ? ChangeType.BUFF : ChangeType.NERF;
				out.add(new Change(c.id(), e.getKey(), type, fmt(before), fmt(after), evidence));
			}
		}
		return out;
	}

	/**
	 * F10: 연속 두 버전의 스킬 재사용 대기시간·소모량 비교. 둘 다 낮을수록 유리, 레벨마다 방향이 섞이면 조정.
	 * 스킬 ID가 바뀐 경우(리워크)와 새 값이 전부 0인 경우(데이터 누락)는 비교하지 않는다.
	 */
	static List<Change> skills(Map<String, List<Spell>> prev, Map<String, List<Spell>> curr, String evidence) {
		List<Change> out = new ArrayList<>();
		for (var e : new TreeMap<>(curr).entrySet()) {
			List<Spell> before = prev.get(e.getKey());
			if (before == null) continue;
			for (int i = 0; i < Math.min(4, Math.min(before.size(), e.getValue().size())); i++) {
				Spell p = before.get(i), c = e.getValue().get(i);
				if (!p.id().equals(c.id())) continue;
				String name = "QWER".charAt(i) + " - " + c.name() + ": ";
				add(out, e.getKey(), name + "재사용 대기시간", p.cooldown(), c.cooldown(), evidence);
				add(out, e.getKey(), name + "소모량", p.cost(), c.cost(), evidence);
			}
		}
		return out;
	}

	private static void add(List<Change> out, String id, String attribute, List<Double> before, List<Double> after,
			String evidence) {
		if (before == null || after == null || before.equals(after) || after.stream().allMatch(v -> v == 0)) return;
		boolean down = false, up = false;
		for (int i = 0; i < Math.min(before.size(), after.size()); i++) {
			down |= after.get(i) < before.get(i);
			up |= after.get(i) > before.get(i);
		}
		ChangeType type = down && !up ? ChangeType.BUFF : up && !down ? ChangeType.NERF : ChangeType.ADJUST;
		out.add(new Change(id, attribute, type, join(before), join(after), evidence));
	}

	private static String join(List<Double> vs) {
		return String.join("/", vs.stream().map(StatDiff::fmt).toList());
	}

	/** 60.0 → "60", 0.625 → "0.625" */
	static String fmt(double v) {
		return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
	}
}
