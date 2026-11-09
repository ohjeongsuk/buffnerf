package com.buffnerf.ai;

import com.buffnerf.TestcontainersConfiguration;
import com.buffnerf.ai.Prompts.Extraction;
import com.buffnerf.ai.Prompts.Item;
import com.buffnerf.ai.Prompts.Summary;
import com.buffnerf.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Import({TestcontainersConfiguration.class, AiServiceTests.FakeLlm.class})
@SpringBootTest
class AiServiceTests {

	static final AtomicInteger calls = new AtomicInteger();
	static volatile boolean quotaExhausted;

	@TestConfiguration
	static class FakeLlm {
		@Bean
		@Primary
		LlmClient fakeLlm() {
			return new LlmClient() {
				public String model() {
					return "fake";
				}

				@SuppressWarnings("unchecked")
				public <T> Result<T> generate(String system, String prompt, Class<T> schema, Effort effort) {
					calls.incrementAndGet();
					if (quotaExhausted)
						throw HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "quota", null, null, null);
					Object v = schema == Summary.class
							? new Summary(List.of("트레이서가 하향됐다.", "겐지가 상향됐다.", "맵이 바뀌었다."))
							: new Extraction(List.of(
									new Item("트레이서", "펄스 쌍권총 피해량", BalanceChange.ChangeType.NERF, "6", "5",
											"펄스 쌍권총 피해량:  6 → 5"),
									// 작은 모델이 같은 항목을 반복한 경우
									new Item("트레이서", "펄스 쌍권총 피해량", BalanceChange.ChangeType.NERF, "6", "5",
											"펄스 쌍권총 피해량:  6 → 5"),
									new Item("트레이서", "펄스 쌍권총 피해량", BalanceChange.ChangeType.NERF, "6", "5".repeat(201),
											"펄스 쌍권총 피해량:  6 → 5"),
									new Item("겐지", "질풍참 재사용 대기시간", BalanceChange.ChangeType.BUFF, "8", "7",
											"원문에 없는 문장")));
					return new Result<>((T) v, 100, 50);
				}
			};
		}
	}

	@Autowired AiService ai;
	@Autowired GameRepository games;
	@Autowired PatchRepository patches;
	@Autowired BalanceChangeRepository changes;
	@Autowired AiRunRepository aiRuns;

	@Test
	void summarizesExtractsAndNeverRecallsSamePromptVersion() {
		Game overwatch = games.findAll().stream().filter(g -> g.getName().equals("Overwatch")).findFirst().orElseThrow();
		Patch p = new Patch(overwatch, "ai-test", "t", Instant.now());
		p.setContent("트레이서\n펄스 쌍권총 피해량: 6 → 5", Patch.ContentSource.MANUAL);
		Long id = patches.save(p).getId();
		calls.set(0);

		ai.process(id);
		ai.process(id);

		assertEquals(2, calls.get(), "SUMMARY + EXTRACT 한 번씩만 호출");
		assertEquals("트레이서가 하향됐다.\n겐지가 상향됐다.\n맵이 바뀌었다.", patches.findById(id).orElseThrow().getSummaryKo());

		var saved = changes.findByPatchId(id);
		assertEquals(1, saved.size(), "근거가 원문에 없는 항목, 반복 항목, 길이 초과 항목은 버림");
		assertEquals("트레이서", saved.getFirst().getCharacterName());
		assertEquals(BalanceChange.Source.AI, saved.getFirst().getSource());
		assertEquals("5", saved.getFirst().getAfterValue());

		assertTrue(aiRuns.existsByPatchIdAndTaskAndPromptVersionAndStatus(id, AiRun.Task.EXTRACT, Prompts.VERSION,
				AiRun.Status.SUCCESS));
	}

	@Test
	void quotaExhaustedIsRecordedAndStopsTheBatch() {
		Game overwatch = games.findAll().stream().filter(g -> g.getName().equals("Overwatch")).findFirst().orElseThrow();
		Patch p = new Patch(overwatch, "ai-quota-test", "t", Instant.now());
		p.setContent("원문", Patch.ContentSource.MANUAL);
		Long id = patches.save(p).getId();
		calls.set(0);
		quotaExhausted = true;
		try {
			// 429는 위로 올라가야 processPendingAsync가 배치를 멈춘다
			assertThrows(HttpClientErrorException.TooManyRequests.class, () -> ai.process(id));
		} finally {
			quotaExhausted = false;
		}
		assertEquals(1, calls.get(), "SUMMARY에서 멈추고 EXTRACT는 부르지 않음");
		assertTrue(aiRuns.existsByPatchIdAndTaskAndPromptVersionAndStatus(id, AiRun.Task.SUMMARY, Prompts.VERSION,
				AiRun.Status.FAILED));
	}
}
