package com.codeassess.ai.client;

import com.codeassess.ai.exception.AiIntegrationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenAiClientTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;
    private OpenAiClient openAiClient;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        restClientBuilder = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer sk-test-secret-key-12345");

        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();

        openAiClient = new OpenAiClient(restClient, "gpt-4o-mini", objectMapper);
    }

    @Test
    @DisplayName("generateSimilarityExplanation returns assistant content on 200 OK")
    void generateSimilarityExplanation_success() {
        String mockResponseJson = """
                {
                  "id": "chatcmpl-test-1",
                  "object": "chat.completion",
                  "created": 1720000000,
                  "model": "gpt-4o-mini",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "The two code submissions share identical AST loop structures."
                      },
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 40,
                    "completion_tokens": 12,
                    "total_tokens": 52
                  }
                }
                """;

        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk-test-secret-key-12345"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        String result = openAiClient.generateSimilarityExplanation("Analyze pair 101");

        mockServer.verify();
        assertThat(result).isEqualTo("The two code submissions share identical AST loop structures.");
    }

    @Test
    @DisplayName("generateCodeReview returns assistant content on 200 OK")
    void generateCodeReview_success() {
        String mockResponseJson = """
                {
                  "id": "chatcmpl-test-2",
                  "object": "chat.completion",
                  "created": 1720000001,
                  "model": "gpt-4o-mini",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "Both methods implement an identical binary search traversal."
                      },
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 50,
                    "completion_tokens": 15,
                    "total_tokens": 65
                  }
                }
                """;

        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk-test-secret-key-12345"))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        String result = openAiClient.generateCodeReview("Review snippets A and B");

        mockServer.verify();
        assertThat(result).isEqualTo("Both methods implement an identical binary search traversal.");
    }

    @Test
    @DisplayName("401 Unauthorized maps to AiIntegrationException without leaking secrets")
    void authenticationFailure_throwsAiIntegrationExceptionWithoutSecrets() {
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> openAiClient.generateSimilarityExplanation("Analyze pair 101"))
                .isInstanceOf(AiIntegrationException.class)
                .hasMessage("AI provider authentication failed. Please check the configured API key.")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("sk-test-secret-key-12345"));

        mockServer.verify();
    }

    @Test
    @DisplayName("429 Too Many Requests maps to rate limit AiIntegrationException")
    void rateLimitExceeded_throwsAiIntegrationException() {
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withTooManyRequests());

        assertThatThrownBy(() -> openAiClient.generateSimilarityExplanation("Analyze pair 101"))
                .isInstanceOf(AiIntegrationException.class)
                .hasMessage("AI provider rate limit exceeded. Please try again shortly.");

        mockServer.verify();
    }

    @Test
    @DisplayName("500 Server Error maps to service unavailable AiIntegrationException")
    void serverError_throwsAiIntegrationException() {
        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> openAiClient.generateSimilarityExplanation("Analyze pair 101"))
                .isInstanceOf(AiIntegrationException.class)
                .hasMessage("AI provider service is currently unavailable. Please try again later.");

        mockServer.verify();
    }

    @Test
    @DisplayName("Response with empty choices list throws AiIntegrationException")
    void emptyChoices_throwsAiIntegrationException() {
        String mockResponseJson = """
                {
                  "id": "chatcmpl-empty",
                  "choices": []
                }
                """;

        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> openAiClient.generateSimilarityExplanation("Analyze pair 101"))
                .isInstanceOf(AiIntegrationException.class)
                .hasMessage("AI provider returned an empty response.");

        mockServer.verify();
    }

    @Test
    @DisplayName("Empty or null prompt throws IllegalArgumentException before sending request")
    void nullOrEmptyPrompt_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> openAiClient.generateSimilarityExplanation(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Prompt cannot be empty");

        assertThatThrownBy(() -> openAiClient.generateSimilarityExplanation("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Prompt cannot be empty");
    }

    @Test
    @DisplayName("Constructor throws IllegalArgumentException if apiKey is null or empty")
    void constructor_missingApiKey_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new OpenAiClient(null, "gpt-4o-mini", 30, "https://api.openai.com/v1", objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("OpenAI API key must not be null or empty");

        assertThatThrownBy(() -> new OpenAiClient("   ", "gpt-4o-mini", 30, "https://api.openai.com/v1", objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("OpenAI API key must not be null or empty");
    }
}
