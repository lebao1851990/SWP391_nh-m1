package com.codeassess.ai.client;

import com.codeassess.ai.exception.AiIntegrationException;

public class StubAiClient implements AiClient {

    @Override
    public String generateSimilarityExplanation(String prompt) throws AiIntegrationException {
        throw new AiIntegrationException("AI provider is not configured. Service is pending integration.");
    }

    @Override
    public String generateCodeReview(String prompt) throws AiIntegrationException {
        throw new AiIntegrationException("AI provider is not configured. Service is pending integration.");
    }

    @Override
    public String generateStudentFeedback(String systemPrompt, String userPrompt) throws AiIntegrationException {
        throw new AiIntegrationException("AI provider is not configured. Service is pending integration.");
    }
}
