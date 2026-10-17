package com.codeassess.ai.config;

import com.codeassess.ai.client.AiClient;
import com.codeassess.ai.client.GeminiAiClient;
import com.codeassess.ai.client.OpenAiClient;
import com.codeassess.ai.client.StubAiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AiConfig {

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
    public AiClient openAiClient(
            @Value("${app.ai.api-key:}") String apiKey,
            @Value("${app.ai.model:gpt-4o-mini}") String model,
            @Value("${app.ai.timeout-seconds:30}") int timeoutSeconds,
            @Value("${app.ai.base-url:https://api.openai.com/v1}") String baseUrl,
            ObjectMapper objectMapper) {

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("OpenAI provider is enabled (app.ai.provider=openai), but app.ai.api-key is missing or empty.");
        }
        log.info("Configuring OpenAiClient bean with model '{}' and timeout {}s (baseUrl: {})", model, timeoutSeconds, baseUrl);
        return new OpenAiClient(apiKey.trim(), model, timeoutSeconds, baseUrl, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "gemini")
    public AiClient geminiAiClient(
            @Value("${app.ai.gemini.api-key:}") String geminiApiKey,
            @Value("${app.ai.api-key:}") String genericApiKey,
            @Value("${app.ai.gemini.model:}") String geminiModel,
            @Value("${app.ai.model:}") String genericModel,
            @Value("${app.ai.gemini.timeout-seconds:0}") int geminiTimeoutSeconds,
            @Value("${app.ai.timeout-seconds:30}") int genericTimeoutSeconds,
            @Value("${app.ai.gemini.base-url:}") String geminiBaseUrl,
            ObjectMapper objectMapper) {

        String apiKey = (geminiApiKey != null && !geminiApiKey.trim().isEmpty()) ? geminiApiKey.trim() : genericApiKey.trim();
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("Gemini provider is enabled (app.ai.provider=gemini), but GEMINI_API_KEY (or app.ai.gemini.api-key) is missing or empty.");
        }

        String model = (geminiModel != null && !geminiModel.trim().isEmpty())
                ? geminiModel.trim()
                : (genericModel != null && !genericModel.trim().isEmpty() ? genericModel.trim() : "gemini-2.5-flash");

        int timeoutSeconds = (geminiTimeoutSeconds > 0) ? geminiTimeoutSeconds : genericTimeoutSeconds;

        String baseUrl = (geminiBaseUrl != null && !geminiBaseUrl.trim().isEmpty())
                ? geminiBaseUrl.trim()
                : "https://generativelanguage.googleapis.com/v1beta";

        log.info("Configuring GeminiAiClient bean with model '{}' and timeout {}s (baseUrl: {})", model, timeoutSeconds, baseUrl);
        return new GeminiAiClient(apiKey, model, timeoutSeconds, baseUrl, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(AiClient.class)
    public AiClient stubAiClient() {
        log.info("Configuring StubAiClient bean (offline mode).");
        return new StubAiClient();
    }
}
