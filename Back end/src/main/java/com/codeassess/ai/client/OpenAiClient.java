package com.codeassess.ai.client;

import com.codeassess.ai.dto.OpenAiChatRequest;
import com.codeassess.ai.dto.OpenAiChatResponse;
import com.codeassess.ai.exception.AiIntegrationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
public class OpenAiClient implements AiClient {

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper;

    public OpenAiClient(String apiKey, String model, int timeoutSeconds, String baseUrl, ObjectMapper objectMapper) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("OpenAI API key must not be null or empty");
        }
        this.model = (model != null && !model.trim().isEmpty()) ? model.trim() : "gpt-4o-mini";
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMs = Math.max(1, timeoutSeconds) * 1000;
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        String endpoint = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl.trim() : "https://api.openai.com/v1";

        this.restClient = RestClient.builder()
                .baseUrl(endpoint)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory)
                .build();
    }

    public OpenAiClient(RestClient restClient, String model, ObjectMapper objectMapper) {
        if (restClient == null) {
            throw new IllegalArgumentException("RestClient must not be null");
        }
        this.restClient = restClient;
        this.model = (model != null && !model.trim().isEmpty()) ? model.trim() : "gpt-4o-mini";
        this.objectMapper = objectMapper;
    }

    @Override
    public String generateSimilarityExplanation(String prompt) throws AiIntegrationException {
        return executeChatCompletion(
                "You are an academic advisory assistant analyzing source code similarity for instructional staff. " +
                "Provide a neutral, objective, and educational explanation. " +
                "Do not declare guilt or make definitive plagiarism accusations.",
                prompt
        );
    }

    @Override
    public String generateCodeReview(String prompt) throws AiIntegrationException {
        return executeChatCompletion(
                "You are an advisory AI code reviewer. " +
                "Analyze the provided code blocks objectively to explain syntactic or logical alignments. " +
                "Maintain a neutral, professional, and educational tone.",
                prompt
        );
    }

    @Override
    public String generateStudentFeedback(String systemPrompt, String userPrompt) throws AiIntegrationException {
        return executeChatCompletion(systemPrompt, userPrompt);
    }

    @Override
    public <T> T generateStructuredOutput(String systemPrompt, String userPrompt, Class<T> targetClass) throws AiIntegrationException {
        String structuredSystemPrompt = systemPrompt + "\n\nCRITICAL: Respond ONLY with valid, raw JSON adhering to standard format without markdown triple backticks. Do not prepend ```json or append ```. Provide all required fields.";
        String rawResponse = executeChatCompletion(structuredSystemPrompt, userPrompt);
        
        String cleanedJson = rawResponse.trim();
        if (cleanedJson.startsWith("```json")) {
            cleanedJson = cleanedJson.substring(7);
        } else if (cleanedJson.startsWith("```")) {
            cleanedJson = cleanedJson.substring(3);
        }
        if (cleanedJson.endsWith("```")) {
            cleanedJson = cleanedJson.substring(0, cleanedJson.length() - 3);
        }
        cleanedJson = cleanedJson.trim();

        try {
            return objectMapper.readValue(cleanedJson, targetClass);
        } catch (Exception ex) {
            log.error("Failed to parse structured JSON response from OpenAI for {}: {}", targetClass.getSimpleName(), ex.getMessage());
            throw new AiIntegrationException("AI provider returned invalid structured JSON output: " + ex.getMessage());
        }
    }

    private String executeChatCompletion(String systemPrompt, String userPrompt) throws AiIntegrationException {
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be empty");
        }

        OpenAiChatRequest requestPayload = new OpenAiChatRequest(
                this.model,
                List.of(
                        new OpenAiChatRequest.ChatMessage("system", systemPrompt),
                        new OpenAiChatRequest.ChatMessage("user", userPrompt)
                ),
                0.2,
                1500
        );

        try {
            log.debug("Sending chat completion request to OpenAI model: {}", this.model);
            OpenAiChatResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        int code = res.getStatusCode().value();
                        if (code == 401) {
                            log.error("OpenAI authentication failed (HTTP 401). Verify the configured API key.");
                            throw new AiIntegrationException("AI provider authentication failed. Please check the configured API key.");
                        } else if (code == 429) {
                            log.warn("OpenAI rate limit or quota exceeded (HTTP 429).");
                            throw new AiIntegrationException("AI provider rate limit exceeded. Please try again shortly.");
                        } else {
                            log.error("OpenAI client error: HTTP {}", code);
                            throw new AiIntegrationException("AI service rejected the request (HTTP " + code + ").");
                        }
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        log.error("OpenAI server error: HTTP {}", res.getStatusCode().value());
                        throw new AiIntegrationException("AI provider service is currently unavailable. Please try again later.");
                    })
                    .body(OpenAiChatResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                log.error("OpenAI response contains no choices");
                throw new AiIntegrationException("AI provider returned an empty response.");
            }

            OpenAiChatResponse.Choice choice = response.choices().get(0);
            if (choice.message() == null || choice.message().content() == null) {
                log.error("OpenAI response choice message is null or empty");
                throw new AiIntegrationException("AI provider returned an empty message content.");
            }

            return choice.message().content().trim();

        } catch (AiIntegrationException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.warn("OpenAI connection/timeout error: {}", e.getMessage());
            throw new AiIntegrationException("AI provider request timed out or connection failed. Please try again later.");
        } catch (Exception e) {
            log.error("Unexpected error communicating with OpenAI: {}", e.getClass().getSimpleName());
            throw new AiIntegrationException("An error occurred while communicating with the AI provider.");
        }
    }
}
