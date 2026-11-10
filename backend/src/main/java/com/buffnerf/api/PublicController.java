package com.buffnerf.api;

import com.buffnerf.ai.CharacterKeys;
import com.buffnerf.domain.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 프론트엔드용 조회 API (F5~F8).
 * 같은 패치에 AI와 DIFF 결과가 함께 있으면 화면에는 AI(근거 문장 있음)를 보여주고, AI가 없으면 DIFF를 보여준다.
 */
// ponytail: 응답 캐시 없음. 지금은 쿼리 1~2개라 빠름. 느려지면 패치 상세에 캐시 추가.
@RestController
@RequestMapping("/api")
class PublicController {

	private final GameRepository games;
	private final PatchRepository patches;
	private final BalanceChangeRepository changes;
	private final CharacterKeys keys;

	PublicController(GameRepository games, PatchRepository patches, BalanceChangeRepository changes, CharacterKeys keys) {
		this.games = games;
		this.patches = patches;
		this.changes = changes;
		this.keys = keys;
	}

	@GetMapping("/games")
	List<GameView> games() {
		return games.findAll().stream().map(g -> new GameView(g.getId(), g.getName(), g.getSource())).toList();
	}

	@GetMapping("/games/{gameId}/patches")
	Page<PatchSummary> patches(@PathVariable Long gameId, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		Page<Patch> result = patches.findByGameIdAndIsPatchNoteTrueOrderByIdDesc(game(gameId).getId(),
				PageRequest.of(page, Math.min(size, 100)));
		var byPatch = changesByPatch(result.getContent());
		return result.map(p -> PatchSummary.of(p, shown(byPatch.getOrDefault(p.getId(), List.of()))));
	}

	@GetMapping("/patches/{patchId}")
	PatchDetail patch(@PathVariable Long patchId) {
		Patch p = patches.findWithGame(patchId).orElseThrow(() -> new NoSuchElementException("patch " + patchId));
		var all = changes.findByPatchId(patchId);
		Game g = p.getGame();
		return new PatchDetail(p.getId(), g.getId(), g.getName(), p.getVersion(), p.getTitle(), p.getPublishedAt(),
				p.getSummaryKo(), p.getContentStatus(), p.getContentSource(), p.getSourceUrl(),
				all.stream().map(c -> ChangeView.of(c, keys.displayName(g, c.getCharacterName()))).toList(),
				g.getSource() == Game.Source.LOL ? Accuracy.of(all) : null);
	}

	@GetMapping("/games/{gameId}/characters")
	List<CharacterView> characters(@PathVariable Long gameId) {
		Game g = game(gameId);
		return changes.characterPatchCounts(gameId).stream()
				.map(r -> new CharacterView((String) r[0], keys.displayName(g, (String) r[0]), ((Number) r[1]).intValue()))
				.toList();
	}

	/** F6: 한 캐릭터의 패치별 변경, 오래된 순 */
	@GetMapping("/games/{gameId}/characters/{characterKey}/timeline")
	Timeline timeline(@PathVariable Long gameId, @PathVariable String characterKey) {
		Game g = game(gameId);
		var byPatch = changes.timeline(gameId, characterKey).stream()
				.collect(Collectors.groupingBy(BalanceChange::getPatch, LinkedHashMap::new, Collectors.toList()));
		var entries = byPatch.entrySet().stream().map(e -> {
			Patch p = e.getKey();
			String name = keys.displayName(g, characterKey);
			return new TimelineEntry(p.getId(), p.getVersion(), p.getTitle(), p.getPublishedAt(),
					shown(e.getValue()).stream().map(c -> ChangeView.of(c, name)).toList());
		}).toList();
		return new Timeline(characterKey, keys.displayName(g, characterKey), entries);
	}

	/** F7: 패치별 상향·하향 캐릭터 수, 오래된 순 */
	@GetMapping("/games/{gameId}/stats")
	List<PatchSummary> stats(@PathVariable Long gameId) {
		var list = patches.findByGameIdAndIsPatchNoteTrueOrderByIdAsc(game(gameId).getId());
		var byPatch = changesByPatch(list);
		return list.stream().map(p -> PatchSummary.of(p, shown(byPatch.getOrDefault(p.getId(), List.of())))).toList();
	}

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	Map<String, String> notFound(NoSuchElementException e) {
		return Map.of("error", e.getMessage());
	}

	private Game game(Long gameId) {
		return games.findById(gameId).orElseThrow(() -> new NoSuchElementException("game " + gameId));
	}

	private Map<Long, List<BalanceChange>> changesByPatch(List<Patch> list) {
		if (list.isEmpty()) return Map.of();
		return changes.findByPatchIds(list.stream().map(Patch::getId).toList()).stream()
				.collect(Collectors.groupingBy(c -> c.getPatch().getId()));
	}

	/** AI 결과가 있으면 AI만, 없으면 DIFF */
	static List<BalanceChange> shown(List<BalanceChange> cs) {
		boolean hasAi = cs.stream().anyMatch(c -> c.getSource() == BalanceChange.Source.AI);
		var want = hasAi ? BalanceChange.Source.AI : BalanceChange.Source.DIFF;
		return cs.stream().filter(c -> c.getSource() == want).toList();
	}

	record GameView(Long id, String name, Game.Source source) {
	}

	record PatchSummary(Long id, String version, String title, Instant publishedAt, String summaryKo,
			int buffs, int nerfs) {
		/** 상향·하향은 캐릭터 수 기준: 바뀐 항목이 전부 상향인 캐릭터, 전부 하향인 캐릭터. 섞였으면 어느 쪽에도 안 센다 (화면의 "조정") */
		static PatchSummary of(Patch p, List<BalanceChange> cs) {
			return new PatchSummary(p.getId(), p.getVersion(), p.getTitle(), p.getPublishedAt(), p.getSummaryKo(),
					count(cs, BalanceChange.ChangeType.BUFF), count(cs, BalanceChange.ChangeType.NERF));
		}

		private static int count(List<BalanceChange> cs, BalanceChange.ChangeType t) {
			// "[클래식] …"처럼 다른 게임 모드의 변경은 세지 않는다 (패치 상세 화면의 기본 모드 집계와 같은 기준)
			return (int) cs.stream().filter(c -> !c.getAttribute().startsWith("["))
					.collect(Collectors.groupingBy(BalanceChange::getCharacterName)).values().stream()
					.filter(mine -> mine.stream().allMatch(c -> c.getChangeType() == t)).count();
		}
	}

	record PatchDetail(Long id, Long gameId, String gameName, String version, String title, Instant publishedAt,
			String summaryKo, Patch.ContentStatus contentStatus, Patch.ContentSource contentSource, String sourceUrl,
			List<ChangeView> changes, Accuracy accuracy) {
	}

	record ChangeView(String characterKey, String characterName, String attribute, BalanceChange.ChangeType changeType,
			String before, String after, String evidence, BalanceChange.Source source) {
		static ChangeView of(BalanceChange c, String displayName) {
			return new ChangeView(c.getCharacterName(), displayName, c.getAttribute(), c.getChangeType(),
					c.getBeforeValue(), c.getAfterValue(), c.getEvidence(), c.getSource());
		}
	}

	record CharacterView(String key, String name, int patchCount) {
	}

	record Timeline(String key, String name, List<TimelineEntry> entries) {
	}

	record TimelineEntry(Long patchId, String version, String title, Instant publishedAt, List<ChangeView> changes) {
	}
}
