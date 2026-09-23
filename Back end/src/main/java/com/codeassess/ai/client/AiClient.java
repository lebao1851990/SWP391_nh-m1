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
}
