package com.buffnerf.admin;

import com.buffnerf.ai.AiService;
import com.buffnerf.collect.CollectService;
import com.buffnerf.domain.Patch;
import com.buffnerf.domain.PatchRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/admin")
class AdminController {

	private final CollectService collectService;
	private final PatchRepository patches;
	private final AiService ai;

	AdminController(CollectService collectService, PatchRepository patches, AiService ai) {
		this.collectService = collectService;
		this.patches = patches;
		this.ai = ai;
	}

	/** F12: 게임별 수집 즉시 실행 */
	@PostMapping("/collect/{gameId}")
	Map<String, Integer> collect(@PathVariable Long gameId) {
		return Map.of("collected", collectService.collect(gameId));
	}

	@GetMapping("/patches")
	List<PatchStatus> patches(@RequestParam Patch.ContentStatus status) {
		return patches.findByContentStatus(status).stream().map(PatchStatus::of).toList();
	}

	/** F1-b: 원문 수동 등록 → READY → AI 요약·추출 자동 시작 */
	@PutMapping("/patches/{patchId}/content")
	PatchStatus putContent(@PathVariable Long patchId, @Valid @RequestBody ContentRequest body) {
		Patch p = patches.findById(patchId).orElseThrow(() -> new NoSuchElementException("patch " + patchId));
		p.setContent(body.content(), Patch.ContentSource.MANUAL);
		PatchStatus saved = PatchStatus.of(patches.save(p));
		ai.processAsync(patchId);
		return saved;
	}

	/** 프롬프트 수정(Prompts.VERSION 올림) 후 재처리. 같은 버전이면 AiRun 확인으로 아무것도 안 한다. */
	@PostMapping("/patches/{patchId}/reprocess")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void reprocess(@PathVariable Long patchId) {
		if (!patches.existsById(patchId)) throw new NoSuchElementException("patch " + patchId);
		ai.processAsync(patchId);
	}

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	Map<String, String> notFound(NoSuchElementException e) {
		return Map.of("error", e.getMessage());
	}

	record ContentRequest(@NotBlank String content) {
	}

	record PatchStatus(Long id, Long gameId, String version, String title, Patch.ContentStatus contentStatus,
			Patch.ContentSource contentSource, String contentError) {
		static PatchStatus of(Patch p) {
			return new PatchStatus(p.getId(), p.getGame().getId(), p.getVersion(), p.getTitle(),
					p.getContentStatus(), p.getContentSource(), p.getContentError());
		}
	}
}
