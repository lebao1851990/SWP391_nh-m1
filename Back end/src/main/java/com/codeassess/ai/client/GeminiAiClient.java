package com.codeassess.ai.client;

import com.codeassess.ai.dto.GeminiGenerateContentRequest;
import com.codeassess.ai.dto.GeminiGenerateContentResponse;
import com.codeassess.ai.exception.AiIntegrationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
public class GeminiAiClient implements AiClient {

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper;

    public GeminiAiClient(String apiKey, String model, int timeoutSeconds, String baseUrl, ObjectMapper objectMapper) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Gemini API key must not be null or empty");
        }
        String rawModel = (model != null && !model.trim().isEmpty()) ? model.trim() : "gemini-2.5-flash";
        this.model = rawModel.startsWith("models/") ? rawModel.substring("models/".length()) : rawModel;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMs = Math.max(1, timeoutSeconds) * 1000;
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        String endpoint = (baseUrl != null && !baseUrl.trim().isEmpty())
                ? baseUrl.trim()
                : "https://generativelanguage.googleapis.com/v1beta";

        this.restClient = RestClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("x-goog-api-key", apiKey.trim())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(requestFactory)
                .build();
    }

    public GeminiAiClient(RestClient restClient, String model, ObjectMapper objectMapper) {
        if (restClient == null) {
            throw new IllegalArgumentException("RestClient must not be null");
        }
        this.restClient = restClient;
        String rawModel = (model != null && !model.trim().isEmpty()) ? model.trim() : "gemini-2.5-flash";
        this.model = rawModel.startsWith("models/") ? rawModel.substring("models/".length()) : rawModel;
        this.objectMapper = objectMapper;
    }

    @Override
    public String generateSimilarityExplanation(String prompt) throws AiIntegrationException {
        return executeGenerateContent(
                "You are an academic advisory assistant analyzing source code similarity for instructional staff. " +
                "Provide a neutral, objective, and educational explanation. " +
                "Do not declare guilt or make definitive plagiarism accusations.",
                prompt
        );
    }

    @Override
    public String generateCodeReview(String prompt) throws AiIntegrationException {
        return executeGenerateContent(
                "You are an advisory AI code reviewer. " +
                "Analyze the provided code blocks objectively to explain syntactic or logical alignments. " +
                "Maintain a neutral, professional, and educational tone.",
                prompt
        );
    }

    @Override
    public String generateStudentFeedback(String systemPrompt, String userPrompt) throws AiIntegrationException {
        return executeGenerateContent(systemPrompt, userPrompt);
    }

    @Override
    public <T> T generateStructuredOutput(String systemPrompt, String userPrompt, Class<T> targetClass) throws AiIntegrationException {
        String structuredSystemPrompt = systemPrompt + "\n\nCRITICAL: Respond ONLY with valid, raw JSON adhering to standard format without markdown triple backticks. Do not prepend ```json or append ```. Provide all required fields.";
        String rawResponse = executeGenerateContent(structuredSystemPrompt, userPrompt);
        
        // Strip markdown code fences if LLM inadvertently wrapped it
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
            log.error("Failed to parse structured JSON response from Gemini for {}: {}", targetClass.getSimpleName(), ex.getMessage());
            throw new AiIntegrationException("AI provider returned invalid structured JSON output: " + ex.getMessage());
        }
    }

    private String executeGenerateContent(String systemPrompt, String userPrompt) throws AiIntegrationException {
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be empty");
        }

        GeminiGenerateContentRequest requestPayload = new GeminiGenerateContentRequest(
                new GeminiGenerateContentRequest.SystemInstruction(
                        List.of(new GeminiGenerateContentRequest.Part(systemPrompt))
                ),
                List.of(
                        new GeminiGenerateContentRequest.Content(
                                "user",
                                List.of(new GeminiGenerateContentRequest.Part(userPrompt))
                        )
                ),
                new GeminiGenerateContentRequest.GenerationConfig(0.2, 1500)
        );

        try {
            log.debug("Sending generateContent request to Gemini model: {}", this.model);
            String uri = "/models/" + this.model + ":generateContent";

            GeminiGenerateContentResponse response = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        int code = res.getStatusCode().value();
                        if (code == 401 || code == 403) {
                            log.error("Gemini authentication/authorization failed (HTTP {}). Verify configured GEMINI_API_KEY.", code);
                            throw new AiIntegrationException("AI provider authentication failed. Please check the configured API key.");
                        } else if (code == 429) {
                            log.warn("Gemini rate limit or quota exceeded (HTTP 429).");
                            throw new AiIntegrationException("AI provider rate limit exceeded. Please try again shortly.");
                        } else if (code == 400) {
                            log.error("Gemini client error (HTTP 400). Invalid parameters or unsupported model.");
                            throw new AiIntegrationException("AI provider rejected the request due to invalid parameters.");
                        } else {
                            log.error("Gemini client error: HTTP {}", code);
                            throw new AiIntegrationException("AI service rejected the request (HTTP " + code + ").");
                        }
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        log.error("Gemini server error: HTTP {}", res.getStatusCode().value());
                        throw new AiIntegrationException("AI provider service is currently unavailable. Please try again later.");
                    })
                    .body(GeminiGenerateContentResponse.class);

            if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
                if (response != null && response.promptFeedback() != null && response.promptFeedback().blockReason() != null) {
                    log.warn("Gemini prompt blocked by feedback filter: {}", response.promptFeedback().blockReason());
                    throw new AiIntegrationException("AI request was blocked by provider safety filters: " + response.promptFeedback().blockReason());
                }
                log.error("Gemini response contains no candidates");
                throw new AiIntegrationException("AI provider returned an empty response.");
            }

            GeminiGenerateContentResponse.Candidate candidate = response.candidates().get(0);
            if (candidate.content() == null || candidate.content().parts() == null || candidate.content().parts().isEmpty()) {
                String finishReason = candidate.finishReason();
                if (finishReason != null && !finishReason.equalsIgnoreCase("STOP")) {
                    log.warn("Gemini candidate finished with non-STOP reason: {}", finishReason);
                    throw new AiIntegrationException("AI response was blocked by safety filters (finishReason: " + finishReason + ").");
                }
                log.error("Gemini candidate content parts is null or empty");
                throw new AiIntegrationException("AI provider returned an empty message content.");
            }

            StringBuilder textBuilder = new StringBuilder();
            for (GeminiGenerateContentResponse.Part part : candidate.content().parts()) {
                if (part.text() != null) {
                    textBuilder.append(part.text());
                }
            }

            String content = textBuilder.toString().trim();
            if (content.isEmpty()) {
                log.error("Gemini aggregated text content is blank");
                throw new AiIntegrationException("AI provider returned an empty message content.");
            }

            return content;

        } catch (AiIntegrationException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.warn("Gemini connection/timeout error: {}", e.getMessage());
            throw new AiIntegrationException("AI provider request timed out or connection failed. Please try again later.");
        } catch (Exception e) {
            log.error("Unexpected error communicating with Gemini: {}", e.getClass().getSimpleName());
            throw new AiIntegrationException("An error occurred while communicating with the AI provider.");
        }
    }
}
