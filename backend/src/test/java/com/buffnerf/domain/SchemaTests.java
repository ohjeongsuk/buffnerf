package com.buffnerf.domain;

import com.buffnerf.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SchemaTests {

	@Autowired GameRepository games;
	@Autowired PatchRepository patches;
	@Autowired AiRunRepository aiRuns;

	@Test
	void seedsThreeGames() {
		assertEquals(3, games.count());
	}

	@Test
	void duplicateVersionIsRejected() {
		Game lol = games.findAll().getFirst();
		patches.saveAndFlush(new Patch(lol, "25.21", "t", Instant.now()));
		assertThrows(DataIntegrityViolationException.class,
				() -> patches.saveAndFlush(new Patch(lol, "25.21", "t", Instant.now())));
	}

	@Test
	void contentFlowAndAiRunGuard() {
		Patch p = patches.saveAndFlush(new Patch(games.findAll().getFirst(), "25.22", "t", Instant.now()));
		assertEquals(Patch.ContentStatus.PENDING_CONTENT, p.getContentStatus());
		p.setContent("본문", Patch.ContentSource.MANUAL);
		assertEquals(Patch.ContentStatus.READY, p.getContentStatus());

		aiRuns.saveAndFlush(new AiRun(p, AiRun.Task.SUMMARY, "claude-sonnet-5-5", "v1", 10, 5, AiRun.Status.SUCCESS));
		assertTrue(aiRuns.existsByPatchIdAndTaskAndPromptVersionAndStatus(
				p.getId(), AiRun.Task.SUMMARY, "v1", AiRun.Status.SUCCESS));
		// 같은 promptVersion 성공 기록 두 번은 DB가 막는다
		assertThrows(DataIntegrityViolationException.class, () -> aiRuns.saveAndFlush(
				new AiRun(p, AiRun.Task.SUMMARY, "claude-sonnet-5-5", "v1", 10, 5, AiRun.Status.SUCCESS)));
	}
}
