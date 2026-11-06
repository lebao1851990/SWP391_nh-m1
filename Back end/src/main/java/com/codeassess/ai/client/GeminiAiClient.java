package com.codeassess.ai.client;

import com.codeassess.ai.dto.GeminiGenerateContentRequest;
import com.codeassess.ai.dto.GeminiGenerateContentResponse;
import com.codeassess.ai.exception.AiIntegrationException;
import com.codeassess.ai.exception.AiTruncationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.databind.JsonMappingException;
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

    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 8192;

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper;
    private final int maxOutputTokens;

    public GeminiAiClient(String apiKey, String model, int timeoutSeconds, String baseUrl, int maxOutputTokens,
            ObjectMapper objectMapper) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Gemini API key must not be null or empty");
        }
        String rawModel = (model != null && !model.trim().isEmpty()) ? model.trim() : "gemini-2.5-flash";
        this.model = rawModel.startsWith("models/") ? rawModel.substring("models/".length()) : rawModel;
        this.objectMapper = objectMapper;
        this.maxOutputTokens = maxOutputTokens > 0 ? maxOutputTokens : DEFAULT_MAX_OUTPUT_TOKENS;

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

    public GeminiAiClient(String apiKey, String model, int timeoutSeconds, String baseUrl, ObjectMapper objectMapper) {
        this(apiKey, model, timeoutSeconds, baseUrl, DEFAULT_MAX_OUTPUT_TOKENS, objectMapper);
    }

    public GeminiAiClient(RestClient restClient, String model, int maxOutputTokens, ObjectMapper objectMapper) {
        if (restClient == null) {
            throw new IllegalArgumentException("RestClient must not be null");
        }
        this.restClient = restClient;
        String rawModel = (model != null && !model.trim().isEmpty()) ? model.trim() : "gemini-2.5-flash";
        this.model = rawModel.startsWith("models/") ? rawModel.substring("models/".length()) : rawModel;
        this.maxOutputTokens = maxOutputTokens > 0 ? maxOutputTokens : DEFAULT_MAX_OUTPUT_TOKENS;
        this.objectMapper = objectMapper;
    }

    public GeminiAiClient(RestClient restClient, String model, ObjectMapper objectMapper) {
        this(restClient, model, DEFAULT_MAX_OUTPUT_TOKENS, objectMapper);
    }

    @Override
    public String generateSimilarityExplanation(String prompt) throws AiIntegrationException {
        return executeGenerateContent(
                "You are an academic advisory assistant analyzing source code similarity for instructional staff. " +
                        "Provide a neutral, objective, and educational explanation. " +
                        "Do not declare guilt or make definitive plagiarism accusations.",
                prompt,
                null,
                this.maxOutputTokens);
    }

    @Override
    public String generateCodeReview(String prompt) throws AiIntegrationException {
        return executeGenerateContent(
                "You are an advisory AI code reviewer. " +
                        "Analyze the provided code blocks objectively to explain syntactic or logical alignments. " +
                        "Maintain a neutral, professional, and educational tone.",
                prompt,
                null,
                this.maxOutputTokens);
    }

    @Override
    public String generateStudentFeedback(String systemPrompt, String userPrompt) throws AiIntegrationException {
        return executeGenerateContent(systemPrompt, userPrompt, null, this.maxOutputTokens);
    }

    @Override
    public <T> T generateStructuredOutput(String systemPrompt, String userPrompt, Class<T> targetClass)
            throws AiIntegrationException {
        String structuredSystemPrompt = systemPrompt
                + "\n\nCRITICAL: Respond ONLY with valid, raw JSON adhering strictly to RFC 8259 format without markdown triple backticks. All string values, newlines, and quotes must be properly escaped. Provide all required fields.";

        // Execute generateContent with explicit responseMimeType="application/json" and
        // token budget
        String rawResponse = executeGenerateContent(structuredSystemPrompt, userPrompt, "application/json",
                this.maxOutputTokens);

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

        if (cleanedJson.isEmpty()) {
            throw new AiIntegrationException("AI provider returned an empty structured response.");
        }

        try {
            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(cleanedJson);
            if (rootNode.isArray() && !targetClass.isArray() && !java.util.List.class.isAssignableFrom(targetClass)) {
                log.info("Gemini returned JSON array for target {}. Attempting fallback conversion...",
                        targetClass.getSimpleName());
                if (targetClass.equals(com.codeassess.ai.dto.AiTestCaseBatchResponse.class)) {
                    java.util.List<com.codeassess.ai.dto.AiTestCaseDraftDto> tcs = new java.util.ArrayList<>();
                    for (com.fasterxml.jackson.databind.JsonNode item : rootNode) {
                        try {
                            if (item.isObject()) {
                                normalizeTestCaseNode((com.fasterxml.jackson.databind.node.ObjectNode) item);
                            }
                            tcs.add(objectMapper.treeToValue(item, com.codeassess.ai.dto.AiTestCaseDraftDto.class));
                        } catch (Exception ex) {
                            log.warn("Failed to parse test case item from array: {}", ex.getMessage());
                        }
                    }
                    return targetClass.cast(new com.codeassess.ai.dto.AiTestCaseBatchResponse(tcs));
                }
                if (targetClass.equals(com.codeassess.ai.dto.AiExamOutlineResponse.class)) {
                    java.util.List<com.codeassess.ai.dto.AiExamOutlineResponse.AiOutlineItemDto> outlineItems = new java.util.ArrayList<>();
                    for (com.fasterxml.jackson.databind.JsonNode item : rootNode) {
                        try {
                            outlineItems.add(objectMapper.treeToValue(item,
                                    com.codeassess.ai.dto.AiExamOutlineResponse.AiOutlineItemDto.class));
                        } catch (Exception ignored) {
                        }
                    }
                    return targetClass.cast(com.codeassess.ai.dto.AiExamOutlineResponse.builder()
                            .examTitle("Dàn ý đề thi")
                            .overview("Dàn ý đề thi dựa trên phân tích tài liệu")
                            .questions(outlineItems)
                            .build());
                }
                if (rootNode.size() > 0 && rootNode.get(0).isObject()) {
                    if (rootNode.get(0).has("summary") || rootNode.get(0).has("cleanCodeScore")
                            || targetClass.equals(com.codeassess.ai.dto.AiDocumentExamGenerateResponse.class)
                            || targetClass.equals(com.codeassess.ai.dto.AiExamGenerateResponse.class)) {
                        return objectMapper.treeToValue(rootNode.get(0), targetClass);
                    }
                }
                if (targetClass.equals(com.codeassess.ai.dto.SemanticReviewResponse.class)) {
                    java.util.List<com.codeassess.ai.dto.CodeReviewIssueDto> issues = new java.util.ArrayList<>();
                    for (com.fasterxml.jackson.databind.JsonNode item : rootNode) {
                        try {
                            issues.add(objectMapper.treeToValue(item, com.codeassess.ai.dto.CodeReviewIssueDto.class));
                        } catch (Exception ignored) {
                        }
                    }
                    com.codeassess.ai.dto.SemanticReviewResponse fallback = com.codeassess.ai.dto.SemanticReviewResponse
                            .builder()
                            .summary("Semantic review generated based on code analysis.")
                            .cleanCodeScore(java.math.BigDecimal.valueOf(7.50))
                            .issues(issues)
                            .build();
                    return targetClass.cast(fallback);
                }
            }

            // Phase B: Unwrap nested object wrapper if model returned {"question": {...}}
            // or {"problem": {...}} or {"questions": [{...}]}
            if (rootNode.isObject() && (targetClass.equals(com.codeassess.ai.dto.AiDocumentExamGenerateResponse.class)
                    || targetClass.equals(com.codeassess.ai.dto.AiExamGenerateResponse.class))) {
                if (!rootNode.has("problemStatement") && !rootNode.has("problem_statement")
                        && !rootNode.has("description") && !rootNode.has("statement")) {
                    for (String wrapperKey : java.util.List.of("question", "problem", "exam", "data", "result",
                            "exercise", "item")) {
                        if (rootNode.has(wrapperKey) && rootNode.get(wrapperKey).isObject()) {
                            log.info("Unwrapping inner question object under key '{}' for {}", wrapperKey,
                                    targetClass.getSimpleName());
                            rootNode = rootNode.get(wrapperKey);
                            break;
                        }
                    }
                    if (rootNode.has("questions") && rootNode.get("questions").isArray()
                            && rootNode.get("questions").size() > 0) {
                        if (rootNode.get("questions").get(0).isObject()) {
                            log.info("Unwrapping first question from 'questions' array for {}",
                                    targetClass.getSimpleName());
                            rootNode = rootNode.get("questions").get(0);
                        }
                    }
                }
            }

            if (rootNode.isObject()) {
                if (targetClass.equals(com.codeassess.ai.dto.AiDocumentExamGenerateResponse.class)
                        || targetClass.equals(com.codeassess.ai.dto.AiExamGenerateResponse.class)) {
                    rootNode = normalizeExamJsonNode(rootNode);
                } else if (targetClass.equals(com.codeassess.ai.dto.AiTestCaseBatchResponse.class)) {
                    for (String tcField : java.util.List.of("testCases", "test_cases", "tests", "cases", "data", "items")) {
                        if (rootNode.has(tcField) && rootNode.get(tcField).isArray()) {
                            for (com.fasterxml.jackson.databind.JsonNode tcNode : rootNode.get(tcField)) {
                                if (tcNode.isObject()) {
                                    normalizeTestCaseNode((com.fasterxml.jackson.databind.node.ObjectNode) tcNode);
                                }
                            }
                        }
                    }
                }
            }

            return objectMapper.treeToValue(rootNode, targetClass);
        } catch (AiIntegrationException ex) {
            throw ex;
        } catch (JsonEOFException ex) {
            log.error("Truncated JSON end-of-input detected while parsing Gemini response for {}: {}",
                    targetClass.getSimpleName(), ex.getMessage());
            throw new AiTruncationException("AI provider returned truncated structured JSON output: " + ex.getMessage(),
                    ex);
        } catch (JsonParseException | JsonMappingException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("Unexpected end-of-input")) {
                log.error("Unexpected end-of-input detected while parsing Gemini response for {}: {}",
                        targetClass.getSimpleName(), ex.getMessage());
                throw new AiTruncationException(
                        "AI provider returned truncated structured JSON output: " + ex.getMessage(), ex);
            }
            log.error("Failed to parse structured JSON response from Gemini for {}: {}", targetClass.getSimpleName(),
                    ex.getMessage());
            throw new AiIntegrationException("AI provider returned invalid structured JSON output: " + ex.getMessage(),
                    ex);
        } catch (Exception ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("Unexpected end-of-input")) {
                log.error("Unexpected end-of-input detected while parsing Gemini response for {}: {}",
                        targetClass.getSimpleName(), ex.getMessage());
                throw new AiTruncationException(
                        "AI provider returned truncated structured JSON output: " + ex.getMessage(), ex);
            }
            log.error("Unexpected error deserializing structured JSON response from Gemini for {}: {}",
                    targetClass.getSimpleName(), ex.getMessage());
            throw new AiIntegrationException("AI provider returned invalid structured JSON output: " + ex.getMessage(),
                    ex);
        }
    }

    private String executeGenerateContent(String systemPrompt, String userPrompt, String responseMimeType,
            Integer maxTokens) throws AiIntegrationException {
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be empty");
        }

        int tokenBudget = (maxTokens != null && maxTokens > 0) ? maxTokens : this.maxOutputTokens;
        GeminiGenerateContentRequest.GenerationConfig genConfig = new GeminiGenerateContentRequest.GenerationConfig(0.2,
                tokenBudget, responseMimeType);

        GeminiGenerateContentRequest requestPayload = new GeminiGenerateContentRequest(
                new GeminiGenerateContentRequest.SystemInstruction(
                        List.of(new GeminiGenerateContentRequest.Part(systemPrompt))),
                List.of(
                        new GeminiGenerateContentRequest.Content(
                                "user",
                                List.of(new GeminiGenerateContentRequest.Part(userPrompt)))),
                genConfig);

        try {
            log.debug("Sending generateContent request to Gemini model: {} (maxTokens={}, responseMimeType={})",
                    this.model, tokenBudget, responseMimeType);
            String uri = "/models/" + this.model + ":generateContent";

            GeminiGenerateContentResponse response = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        int code = res.getStatusCode().value();
                        if (code == 401 || code == 403) {
                            throw new AiIntegrationException("AI provider authentication failed. Please check the configured API key.");
                        } else if (code == 429) {
                            throw new AiIntegrationException("AI provider rate limit exceeded. Please try again shortly.");
                        } else {
                            throw new AiIntegrationException("AI provider rejected the request (HTTP " + code + ").");
                        }
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
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

            // Log diagnostic info for debugging & observability
            if (response.usageMetadata() != null) {
                log.info(
                        "Gemini generation usage: promptTokens={}, candidateTokens={}, totalTokens={}, finishReason={}",
                        response.usageMetadata().promptTokenCount(),
                        response.usageMetadata().candidatesTokenCount(),
                        response.usageMetadata().totalTokenCount(),
                        candidate.finishReason());
            }

            // Check finishReason for token truncation
            if ("MAX_TOKENS".equalsIgnoreCase(candidate.finishReason())) {
                log.warn("Gemini generation hit MAX_TOKENS limit (finishReason=MAX_TOKENS). Output was truncated.");
                throw new AiTruncationException("Gemini output was truncated at MAX_TOKENS limit. Output was truncated.");
            }

            if (candidate.content() == null ||
                    candidate.content().parts() == null ||
                    candidate.content().parts().isEmpty()) {
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

            String text = textBuilder.toString().trim();
            if (text.isEmpty()) {
                log.error("Gemini aggregated text content is blank");
                throw new AiIntegrationException("AI provider returned an empty message content.");
            }

            return text;

        } catch (AiTruncationException ex) {
            throw ex;
        } catch (AiIntegrationException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            log.warn("Gemini connection/timeout error: {}", ex.getMessage());
            throw new AiIntegrationException("AI provider request timed out or connection failed. Please try again later.");
        } catch (Exception ex) {
            log.error("Unexpected error communicating with Gemini: {}", ex.getClass().getSimpleName());
            throw new AiIntegrationException("An error occurred while communicating with the AI provider.");
        }
    }

    private com.fasterxml.jackson.databind.JsonNode normalizeExamJsonNode(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || !node.isObject()) {
            return node;
        }
        com.fasterxml.jackson.databind.node.ObjectNode obj = (com.fasterxml.jackson.databind.node.ObjectNode) node;

        // Convert array string fields to newline-separated text
        for (String field : java.util.List.of("constraints", "constraint", "limits", "inputSpecification", "input_specification", "inputSpec", "outputSpecification", "output_specification", "outputSpec", "sampleInput", "sample_input", "sampleOutput", "sample_output", "problemStatement", "problem_statement", "description", "statement")) {
            if (obj.has(field) && obj.get(field).isArray()) {
                java.util.List<String> lines = new java.util.ArrayList<>();
                for (com.fasterxml.jackson.databind.JsonNode item : obj.get(field)) {
                    if (item != null && !item.isNull()) {
                        lines.add(item.asText());
                    }
                }
                obj.put(field, String.join("\n", lines));
            }
        }

        // Normalize test cases if embedded
        for (String tcField : java.util.List.of("testCases", "test_cases", "tests")) {
            if (obj.has(tcField) && obj.get(tcField).isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode tcNode : obj.get(tcField)) {
                    if (tcNode.isObject()) {
                        normalizeTestCaseNode((com.fasterxml.jackson.databind.node.ObjectNode) tcNode);
                    }
                }
            }
        }

        return obj;
    }

    private void normalizeTestCaseNode(com.fasterxml.jackson.databind.node.ObjectNode tcObj) {
        // Normalize expectedOutput / result
        for (String outField : java.util.List.of("expectedOutput", "expected_output", "output", "result", "stdout", "expected", "target", "expectedResult", "returnValue", "return_value", "output_data", "outputData")) {
            if (tcObj.has(outField)) {
                com.fasterxml.jackson.databind.JsonNode val = tcObj.get(outField);
                if (val.isArray()) {
                    java.util.List<String> lines = new java.util.ArrayList<>();
                    for (com.fasterxml.jackson.databind.JsonNode item : val) {
                        if (item != null && !item.isNull()) {
                            lines.add(item.asText());
                        }
                    }
                    tcObj.put("expectedOutput", String.join("\n", lines));
                } else if (val.isValueNode() && !val.isTextual()) {
                    tcObj.put("expectedOutput", val.asText());
                }
            }
        }
        // Normalize inputData / input
        for (String inField : java.util.List.of("inputData", "input_data", "input", "stdin", "sampleInput", "inputValue", "input_value", "args", "arguments", "parameters", "inputs", "data")) {
            if (tcObj.has(inField)) {
                com.fasterxml.jackson.databind.JsonNode val = tcObj.get(inField);
                if (val.isArray()) {
                    java.util.List<String> lines = new java.util.ArrayList<>();
                    for (com.fasterxml.jackson.databind.JsonNode item : val) {
                        if (item != null && !item.isNull()) {
                            lines.add(item.asText());
                        }
                    }
                    tcObj.put("inputData", String.join("\n", lines));
                } else if (val.isValueNode() && !val.isTextual()) {
                    tcObj.put("inputData", val.asText());
                }
            }
        }
    }
}
