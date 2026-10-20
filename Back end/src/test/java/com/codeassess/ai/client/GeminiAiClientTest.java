package com.codeassess.ai.client;

import com.codeassess.ai.exception.AiIntegrationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiAiClientTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;
    private GeminiAiClient geminiAiClient;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        restClientBuilder = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("x-goog-api-key", "gemini-secret-api-key-xyz123");

        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();

        geminiAiClient = new GeminiAiClient(restClient, "gemini-2.5-flash", objectMapper);
    }

    @Test
    @DisplayName("generateSimilarityExplanation returns generated text on 200 OK")
    void generateSimilarityExplanation_success() {
        String mockResponseJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "The two code submissions share identical AST loop structures and logic."
                          }
                        ],
                        "role": "model"
                      },
                      "finishReason": "STOP",
                      "index": 0
                    }
                  ],
                  "usageMetadata": {
                    "promptTokenCount": 35,
                    "candidatesTokenCount": 14,
                    "totalTokenCount": 49
                  }
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "gemini-secret-api-key-xyz123"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        String result = geminiAiClient.generateSimilarityExplanation("Analyze AST similarity for pair #123");

        mockServer.verify();
        assertThat(result).isEqualTo("The two code submissions share identical AST loop structures and logic.");
    }

    @Test
    @DisplayName("generateCodeReview returns generated text on 200 OK")
    void generateCodeReview_success() {
        String mockResponseJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "Both implementations use identical recursive traversal algorithms."
                          }
                        ],
                        "role": "model"
                      },
                      "finishReason": "STOP",
                      "index": 0
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "gemini-secret-api-key-xyz123"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(mockResponseJson, MediaType.APPLICATION_JSON));

        String result = geminiAiClient.generateCodeReview("Review syntax match between code snippet A and B");

        mockServer.verify();
        assertThat(result).isEqualTo("Both implementations use identical recursive traversal algorithms.");
    }

    @Test
    @DisplayName("HTTP 401/403 maps to AiIntegrationException without leaking the API key")
    void authenticationFailure_handledSafelyWithoutLeakingSecret() {
        String mockErrorJson = """
                {
                  "error": {
                    "code": 403,
                    "message": "The provided API key is expired or invalid",
                    "status": "PERMISSION_DENIED"
                  }
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(mockErrorJson));

        assertThatThrownBy(() -> geminiAiClient.generateSimilarityExplanation("Test prompt"))
                .isInstanceOf(AiIntegrationException.class)
                .hasMessage("AI provider authentication failed. Please check the configured API key.")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("gemini-secret-api-key-xyz123"));

        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 429 maps to rate limit AiIntegrationException")
    void rateLimit_handledSafely() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":429,\"message\":\"Quota exceeded\"}}"));

        assertThatThrownBy(() -> geminiAiClient.generateSimilarityExplanation("Test prompt"))
                .isInstanceOf(AiIntegrationException.class)
                .hasMessage("AI provider rate limit exceeded. Please try again shortly.");

        mockServer.verify();
    }

    @Test
    @DisplayName("HTTP 500 maps to provider unavailable AiIntegrationException")
    void serverError_handledSafely() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> geminiAiClient.generateSimilarityExplanation("Test prompt"))
                .isInstanceOf(AiIntegrationException.class)
                .hasMessage("AI provider service is currently unavailable. Please try again later.");

        mockServer.verify();
    }

    @Test
    @DisplayName("Empty candidates in response maps to clear AiIntegrationException")
    void emptyCandidates_handledSafely() {
        String mockEmptyResponseJson = """
                {
                  "candidates": []
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockEmptyResponseJson, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> geminiAiClient.generateSimilarityExplanation("Test prompt"))
                .isInstanceOf(AiIntegrationException.class)
                .hasMessage("AI provider returned an empty response.");

        mockServer.verify();
    }

    @Test
    @DisplayName("Response blocked by safety filter maps to clear AiIntegrationException")
    void safetyBlock_handledSafely() {
        String mockSafetyResponseJson = """
                {
                  "candidates": [
                    {
                      "finishReason": "SAFETY",
                      "index": 0
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(mockSafetyResponseJson, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> geminiAiClient.generateSimilarityExplanation("Test prompt"))
                .isInstanceOf(AiIntegrationException.class)
                .hasMessage("AI response was blocked by safety filters (finishReason: SAFETY).");

        mockServer.verify();
    }

    @Test
    @DisplayName("Blank prompt is rejected before HTTP request")
    void blankPrompt_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> geminiAiClient.generateSimilarityExplanation("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Prompt cannot be empty");
    }

    @Test
    @DisplayName("Constructor rejects empty or null API key")
    void constructor_rejectsEmptyApiKey() {
        assertThatThrownBy(() -> new GeminiAiClient("", "gemini-2.5-flash", 30, null, objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Gemini API key must not be null or empty");

        assertThatThrownBy(() -> new GeminiAiClient(null, "gemini-2.5-flash", 30, null, objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Gemini API key must not be null or empty");
    }
}
