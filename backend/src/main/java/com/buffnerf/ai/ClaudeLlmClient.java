package com.buffnerf.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredOutputConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// ponytail: 거절(refusal) 시 서버 측 fallback 없이 실패로 기록. 패치노트에서 거절이 실제로 나오면 fallbacks 베타 추가.
@Component
class ClaudeLlmClient implements LlmClient {

	private static final long MAX_TOKENS = 32_000; // 변경 항목이 많은 패치(100건+) 추출도 잘리지 않게

	private final String model;
	private final AnthropicClient client; // 키가 없으면 null: 앱은 뜨고 호출만 실패

	ClaudeLlmClient(@Value("${anthropic.api-key:}") String apiKey, @Value("${anthropic.model}") String model) {
		this.model = model;
		this.client = apiKey.isBlank() ? null : AnthropicOkHttpClient.builder().apiKey(apiKey).build();
	}

	@Override
	public String model() {
		return model;
	}

	@Override
	public <T> Result<T> generate(String system, String prompt, Class<T> schema, Effort effort) {
		if (client == null) throw new IllegalStateException("ANTHROPIC_API_KEY not set");
		var params = MessageCreateParams.builder()
				.model(model)
				.maxTokens(MAX_TOKENS)
				.system(system)
				.outputConfig(StructuredOutputConfig.<T>builder()
						.format(schema)
						.effort(OutputConfig.Effort.of(effort.name().toLowerCase()))
						.build())
				.addUserMessage(prompt)
				.build();
		var res = client.messages().create(params);

		StopReason stop = res.stopReason().orElse(null);
		if (!StopReason.END_TURN.equals(stop)) throw new IllegalStateException("stop_reason=" + stop);
		T value = res.content().stream()
				.flatMap(b -> b.text().stream())
				.map(t -> t.text())
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("no text block"));
		return new Result<>(value, res.usage().inputTokens(), res.usage().outputTokens());
	}
}
