package com.buffnerf.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRunRepository extends JpaRepository<AiRun, Long> {
	/** CLAUDE.md 규칙: 같은 패치·promptVersion으로 성공한 호출이 있으면 LLM 재호출 금지 */
	boolean existsByPatchIdAndTaskAndPromptVersionAndStatus(Long patchId, AiRun.Task task,
			String promptVersion, AiRun.Status status);
}
