package com.buffnerf.ai;

import com.buffnerf.ai.Prompts.Extraction;
import com.buffnerf.ai.Prompts.Summary;
import com.buffnerf.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** F3 요약 + F4 구조화 추출. 비동기 실행은 단일 스레드(application.properties)라 같은 패치를 동시에 처리하지 않는다. */
@Service
public class AiService {

	private static final Logger log = LoggerFactory.getLogger(AiService.class);

	private final LlmClient llm;
	private final CharacterKeys keys;
	private final PatchRepository patches;
	private final BalanceChangeRepository changes;
	private final AiRunRepository aiRuns;
	private final TransactionTemplate tx;

	AiService(LlmClient llm, CharacterKeys keys, PatchRepository patches, BalanceChangeRepository changes,
			AiRunRepository aiRuns, TransactionTemplate tx) {
		this.llm = llm;
		this.keys = keys;
		this.patches = patches;
		this.changes = changes;
		this.aiRuns = aiRuns;
		this.tx = tx;
	}

	@Async
	public void processAsync(Long patchId) {
		process(patchId);
	}

	/** 원문이 있는 모든 패치. 이미 끝난 작업은 AiRun 확인으로 건너뛴다. */
	@Async
	public void processPendingAsync() {
		for (Patch p : patches.findByContentStatus(Patch.ContentStatus.READY)) process(p.getId());
	}

	public void process(Long patchId) {
		Patch p = patches.findWithGame(patchId).orElseThrow(() -> new NoSuchElementException("patch " + patchId));
		if (p.getContentStatus() != Patch.ContentStatus.READY) return;

		run(p, AiRun.Task.SUMMARY,
				() -> llm.generate(Prompts.SYSTEM, Prompts.summary(p), Summary.class, LlmClient.Effort.LOW),
				s -> {
					if (s.lines() == null || s.lines().size() != 3 || s.lines().stream().anyMatch(String::isBlank))
						throw new IllegalStateException("summary must be 3 lines: " + s.lines());
					p.setSummaryKo(String.join("\n", s.lines()));
					patches.save(p);
				});

		run(p, AiRun.Task.EXTRACT,
				() -> llm.generate(Prompts.SYSTEM, Prompts.extract(p), Extraction.class, LlmClient.Effort.MEDIUM),
				e -> {
					changes.deleteByPatchIdAndSource(p.getId(), BalanceChange.Source.AI); // 새 promptVersion 재처리
					String raw = squash(p.getRawContent());
					int dropped = 0;
					var seen = new HashSet<List<String>>();
					for (var it : e.changes()) {
						// 근거 문장이 원문에 없으면 환각으로 보고 버린다. 컬럼 길이 초과 값, 같은 항목 반복도 버린다.
						if (blank(it.characterName()) || blank(it.attribute()) || it.changeType() == null
								|| blank(it.evidence()) || !raw.contains(squash(it.evidence()))
								|| it.characterName().length() > 100 || tooLong(it.attribute()) || tooLong(it.before())
								|| tooLong(it.after())
								|| !seen.add(Arrays.asList(it.characterName(), it.attribute(), it.before(), it.after()))) {
							dropped++;
							continue;
						}
						changes.save(new BalanceChange(p, keys.key(p, it.characterName()), it.attribute().trim(),
								it.changeType(), emptyToNull(it.before()), emptyToNull(it.after()),
								it.evidence().trim(), BalanceChange.Source.AI));
					}
					if (dropped > 0) log.warn("patch {}: dropped {} AI changes without valid evidence", p.getId(), dropped);
				});
	}

	/** CLAUDE.md 규칙: 같은 패치·같은 promptVersion으로 성공한 기록이 있으면 LLM을 다시 부르지 않는다. */
	private <T> void run(Patch p, AiRun.Task task, Supplier<LlmClient.Result<T>> call, Consumer<T> save) {
		if (aiRuns.existsByPatchIdAndTaskAndPromptVersionAndStatus(p.getId(), task, Prompts.VERSION, AiRun.Status.SUCCESS))
			return;
		LlmClient.Result<T> r = null;
		try {
			r = call.get();
			var result = r;
			tx.executeWithoutResult(s -> {
				save.accept(result.value());
				aiRuns.save(new AiRun(p, task, llm.model(), Prompts.VERSION,
						(int) result.inputTokens(), (int) result.outputTokens(), AiRun.Status.SUCCESS));
			});
		} catch (Exception e) {
			// 검증 실패 응답은 저장하지 않고 실패 기록만 (토큰은 비용 추적용으로 남김)
			log.warn("AI {} failed for patch {}: {}", task, p.getId(), e.toString());
			aiRuns.save(new AiRun(p, task, llm.model(), Prompts.VERSION,
					r == null ? null : (int) r.inputTokens(), r == null ? null : (int) r.outputTokens(),
					AiRun.Status.FAILED));
		}
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}

	/** balance_change의 attribute, before_value, after_value는 VARCHAR(200) */
	private static boolean tooLong(String s) {
		return s != null && s.trim().length() > 200;
	}

	private static String emptyToNull(String s) {
		return blank(s) ? null : s.trim();
	}

	private static String squash(String s) {
		return s.replaceAll("\\s+", "");
	}
}
