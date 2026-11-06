package com.buffnerf.api;

import com.buffnerf.domain.BalanceChange;
import com.buffnerf.domain.BalanceChange.ChangeType;
import com.buffnerf.domain.BalanceChange.Source;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccuracyTests {

	static BalanceChange c(String champ, String attr, String after, Source s) {
		return new BalanceChange(null, champ, attr, ChangeType.BUFF, null, after, null, s);
	}

	@Test
	void recallAndPrecisionOverBaseStatsOnly() {
		var acc = Accuracy.of(List.of(
				c("Mel", "hp", "590", Source.DIFF),
				c("Mel", "hpperlevel", "107", Source.DIFF),
				c("Mel", "hp", "590.0", Source.AI),          // 맞음 (숫자 비교)
				c("Mel", "hpperlevel", "100", Source.AI),     // 수치 틀림
				c("Mel", "Q - 빛의 세례: 피해량", "60", Source.AI))); // 스킬은 비교 제외
		assertEquals(1, acc.matched());
		assertEquals(0.5, acc.recall());
		assertEquals(0.5, acc.precision());
	}

	@Test
	void nullBeforeAiRuns() {
		assertNull(Accuracy.of(List.of(c("Mel", "hp", "590", Source.DIFF))));
	}
}
