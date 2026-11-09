package com.buffnerf.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 503은 재시도, 그 밖의 오류는 바로 실패 */
class GeminiLlmClientTests {

	private static final String OK = """
			{"candidates":[{"content":{"parts":[{"text":"{\\"lines\\":[\\"a\\",\\"b\\",\\"c\\"]}"}]},"finishReason":"STOP"}],
			 "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":5}}""";

	private final RestClient.Builder builder = RestClient.builder();
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final GeminiLlmClient client = new GeminiLlmClient("k", "m", new JsonMapper(), builder, 0);

	@Test
	void retries503ThenSucceeds() {
		server.expect(ExpectedCount.twice(), header("x-goog-api-key", "k")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		server.expect(header("x-goog-api-key", "k")).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));

		var r = client.generate("s", "p", Prompts.Summary.class, LlmClient.Effort.LOW);
		assertEquals(3, r.value().lines().size());
		assertEquals(10, r.inputTokens());
		server.verify();
	}

	@Test
	void givesUpAfterThree503s() {
		server.expect(ExpectedCount.times(3), header("x-goog-api-key", "k")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		assertThrows(HttpServerErrorException.ServiceUnavailable.class,
				() -> client.generate("s", "p", Prompts.Summary.class, LlmClient.Effort.LOW));
		server.verify();
	}

	@Test
	void doesNotRetry404() {
		server.expect(ExpectedCount.once(), header("x-goog-api-key", "k")).andRespond(withStatus(HttpStatus.NOT_FOUND));
		assertThrows(HttpClientErrorException.NotFound.class,
				() -> client.generate("s", "p", Prompts.Summary.class, LlmClient.Effort.LOW));
		server.verify();
	}
}
