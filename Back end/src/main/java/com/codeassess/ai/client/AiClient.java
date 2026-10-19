package com.codeassess.ai.client;

import com.codeassess.ai.exception.AiIntegrationException;

public interface AiClient {
    
    /**
     * Generates a similarity explanation.
     * @param prompt The privacy-safe prompt constructed by the service.
     * @return AI generated text.
     * @throws AiIntegrationException if integration fails.
     */
    String generateSimilarityExplanation(String prompt) throws AiIntegrationException;
    
    /**
     * Generates a code review for a specific evidence snippet.
     * @param prompt The privacy-safe prompt constructed by the service.
     * @return AI generated text.
     * @throws AiIntegrationException if integration fails.
     */
    String generateCodeReview(String prompt) throws AiIntegrationException;

    /**
     * Generates student-facing educational feedback (compiler error explanations, code explanations, or debug hints).
     * @param systemPrompt System instructions specifying role, pedagogical tone, and safety constraints.
     * @param userPrompt Bounded student code and/or error output.
     * @return AI generated guidance.
     * @throws AiIntegrationException if provider integration fails.
     */
    String generateStudentFeedback(String systemPrompt, String userPrompt) throws AiIntegrationException;
}
