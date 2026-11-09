package com.buffnerf.ai;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LLM 없이 스키마 모양만 확인: 필수 필드, enum, 설명, 추가 속성 금지 */
class OllamaSchemaTests {

	private final JsonMapper json = new JsonMapper();

	@Test
	void extractionSchema() {
		String s = OllamaLlmClient.schemaFor(Prompts.Extraction.class, json).toString();
		assertTrue(s.contains("required=[characterName, attribute, changeType, before, after, evidence]"), s);
		assertTrue(s.contains("[BUFF, NERF, ADJUST]"), s);
		assertTrue(s.contains("근거가 된 원문 한 줄"), s);
		assertTrue(s.contains("additionalProperties=false"), s);
		assertFalse(s.contains("$schema"), s);
	}

	@Test
	void summarySchema() {
		String s = OllamaLlmClient.schemaFor(Prompts.Summary.class, json).toString();
		assertTrue(s.contains("required=[lines]") && s.contains("type=array"), s);
	}
}
