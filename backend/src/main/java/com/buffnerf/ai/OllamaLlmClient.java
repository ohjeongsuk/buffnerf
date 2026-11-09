package com.buffnerf.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.impl.PropertySortUtils;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * 로컬 Ollama(/api/chat) 호출. 무료지만 이 PC(GPU)에서만 돈다: 여기서 처리한 결과를 배포 DB로 옮긴다.
 * 구조화 출력은 Ollama의 format(JSON 스키마)으로 강제한다.
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama")
class OllamaLlmClient implements LlmClient {

	// 가장 긴 패치노트(롤, 약 2.4만 자)와 추출 결과가 함께 들어가는 크기. 8B 모델 + 이 컨텍스트가 12GB VRAM에 들어간다.
	private static final int NUM_CTX = 32_768;

	private static final SchemaGenerator SCHEMAS;

	static {
		var b = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
				.with(new JacksonModule()) // @JsonPropertyDescription → description
				.with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT)
				.without(Option.SCHEMA_VERSION_INDICATOR); // "$schema" 키는 Gemini가 받지 않을 수 있어 뺀다
		b.forFields().withRequiredCheck(f -> true); // 레코드 필드는 모두 필수 (빈 값은 프롬프트대로 빈 문자열)
		// 모델은 스키마의 속성 순서대로 생성한다: 선언 순서(캐릭터 → 항목 → … → 근거) 유지
		b.forTypesInGeneral().withPropertySorter(PropertySortUtils.SORT_PROPERTIES_FIELDS_BEFORE_METHODS);
		SCHEMAS = new SchemaGenerator(b.build());
	}

	private final String model;
	private final RestClient http;
	private final JsonMapper json;

	OllamaLlmClient(@Value("${ollama.url}") String url, @Value("${ollama.model}") String model, JsonMapper json) {
		this.model = model;
		this.http = RestClient.create(url);
		this.json = json;
	}

	@Override
	public String model() {
		return model;
	}

	@Override
	public <T> Result<T> generate(String system, String prompt, Class<T> schema, Effort effort) {
		var body = Map.of(
				"model", model,
				"stream", false,
				"think", false, // qwen3의 생각 모드 끄기: 출력 토큰과 시간 절약
				"format", schemaFor(schema, json),
				// num_predict: 작은 모델이 같은 항목을 끝없이 반복하는 경우를 끊는다 (실측 정상 최대 약 2.1만 토큰)
				// ponytail: 한도에 걸린 큰 패치는 실패로 남는다. 자주 나오면 패치를 나눠 추출.
				"options", Map.of("num_ctx", NUM_CTX, "num_predict", 24_576, "temperature", 0),
				"messages", List.of(
						Map.of("role", "system", "content", system),
						Map.of("role", "user", "content", prompt)));
		String raw = http.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON)
				.body(json.writeValueAsString(body)).retrieve().body(String.class);
		var res = json.readValue(raw, ChatResponse.class);
		if (!"stop".equals(res.doneReason())) throw new IllegalStateException("done_reason=" + res.doneReason());
		return new Result<>(json.readValue(res.message().content(), schema), res.promptEvalCount(), res.evalCount());
	}

	/** 레코드 → JSON 스키마 (Ollama format, Gemini responseJsonSchema 값) */
	static Map<?, ?> schemaFor(Class<?> c, JsonMapper json) {
		return json.readValue(SCHEMAS.generateSchema(c).toString(), Map.class);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ChatResponse(Message message, @JsonProperty("done_reason") String doneReason,
			@JsonProperty("prompt_eval_count") long promptEvalCount, @JsonProperty("eval_count") long evalCount) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Message(String content) {
	}
}
