package com.buffnerf.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gemini API(generateContent) 호출. 무료 등급 키로 EC2에서도 돌아간다.
 * 구조화 출력은 responseJsonSchema로 강제한다.
 */
// ponytail: 무료 등급은 일일 호출 수 제한이 있다(패치 1개 = 2회 호출). 429가 나면 다음 날 reprocess로 다시 돌린다.
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "gemini", matchIfMissing = true)
class GeminiLlmClient implements LlmClient {

	private final String model;
	private final String apiKey;
	private final RestClient http = RestClient.create("https://generativelanguage.googleapis.com/v1beta");
	private final JsonMapper json;

	GeminiLlmClient(@Value("${gemini.api-key:}") String apiKey, @Value("${gemini.model}") String model,
			JsonMapper json) {
		this.apiKey = apiKey;
		this.model = model;
		this.json = json;
	}

	@Override
	public String model() {
		return model;
	}

	@Override
	public <T> Result<T> generate(String system, String prompt, Class<T> schema, Effort effort) {
		if (apiKey.isBlank()) throw new IllegalStateException("GEMINI_API_KEY not set");
		var body = Map.of(
				"systemInstruction", Map.of("parts", List.of(Map.of("text", system))),
				"contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
				"generationConfig", Map.of(
						"responseMimeType", "application/json",
						"responseJsonSchema", OllamaLlmClient.schemaFor(schema, json),
						"temperature", 0,
						"maxOutputTokens", 65_536)); // 생각 토큰 포함 한도: 큰 패치 추출도 잘리지 않게
		String raw = http.post().uri("/models/{model}:generateContent", model)
				.header("x-goog-api-key", apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(json.writeValueAsString(body)).retrieve().body(String.class);
		var res = json.readValue(raw, Response.class);
		if (res.candidates() == null || res.candidates().isEmpty())
			throw new IllegalStateException("no candidates: " + raw);
		var c = res.candidates().getFirst();
		if (!"STOP".equals(c.finishReason())) throw new IllegalStateException("finishReason=" + c.finishReason());
		String text = c.content().parts().stream().map(Part::text).filter(t -> t != null)
				.collect(Collectors.joining());
		var u = res.usageMetadata();
		return new Result<>(json.readValue(text, schema), u == null ? 0 : u.promptTokenCount(),
				u == null ? 0 : u.candidatesTokenCount());
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Response(List<Candidate> candidates, Usage usageMetadata) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Candidate(Content content, String finishReason) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Content(List<Part> parts) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Part(String text) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Usage(long promptTokenCount, long candidatesTokenCount) {
	}
}
