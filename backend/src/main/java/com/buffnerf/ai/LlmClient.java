package com.buffnerf.ai;

/** LLM 호출 추상화. 다른 모델/공급자로 바꿀 때 구현체만 교체한다. */
public interface LlmClient {

	enum Effort { LOW, MEDIUM, HIGH }

	String model();

	/** schema 클래스 모양의 JSON으로 응답받는다. 실패(거절, 잘림, 스키마 위반)는 예외. */
	<T> Result<T> generate(String system, String prompt, Class<T> schema, Effort effort);

	record Result<T>(T value, long inputTokens, long outputTokens) {
	}
}
