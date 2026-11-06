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

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Import({TestcontainersConfiguration.class, AiServiceTests.FakeLlm.class})
@SpringBootTest
class AiServiceTests {

	static final AtomicInteger calls = new AtomicInteger();

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
					Object v = schema == Summary.class
							? new Summary(List.of("트레이서가 하향됐다.", "겐지가 상향됐다.", "맵이 바뀌었다."))
							: new Extraction(List.of(
									new Item("트레이서", "펄스 쌍권총 피해량", BalanceChange.ChangeType.NERF, "6", "5",
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
		assertEquals(1, saved.size(), "근거가 원문에 없는 항목은 버림");
		assertEquals("트레이서", saved.getFirst().getCharacterName());
		assertEquals(BalanceChange.Source.AI, saved.getFirst().getSource());
		assertEquals("5", saved.getFirst().getAfterValue());

		assertTrue(aiRuns.existsByPatchIdAndTaskAndPromptVersionAndStatus(id, AiRun.Task.EXTRACT, Prompts.VERSION,
				AiRun.Status.SUCCESS));
	}
}
