package com.buffnerf.ai;

import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** API 키 없이 SDK의 로컬 스키마 검증만 확인 (structured outputs가 받아들이는 모양인지) */
class PromptSchemaTests {

	@Test
	void schemasAreAcceptedBySdk() {
		assertSchema(Prompts.Summary.class);
		assertSchema(Prompts.Extraction.class);
	}

	private static <T> void assertSchema(Class<T> c) {
		String body = MessageCreateParams.builder().model("m").maxTokens(1).addUserMessage("x")
				.outputConfig(StructuredOutputConfig.<T>builder().format(c).build())
				.build().toString();
		assertTrue(body.contains("json_schema"), body);
	}
}
