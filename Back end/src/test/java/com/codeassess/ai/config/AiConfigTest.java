package com.codeassess.ai.config;

import com.codeassess.ai.client.AiClient;
import com.codeassess.ai.client.OpenAiClient;
import com.codeassess.ai.client.StubAiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AiConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AiConfig.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    @DisplayName("When provider is 'none', StubAiClient is loaded")
    void providerNone_loadsStubAiClient() {
        contextRunner
                .withPropertyValues("app.ai.provider=none")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiClient.class);
                    assertThat(context.getBean(AiClient.class)).isInstanceOf(StubAiClient.class);
                });
    }

    @Test
    @DisplayName("When provider is unset, StubAiClient is loaded by default")
    void providerUnset_loadsStubAiClientByDefault() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(AiClient.class);
                    assertThat(context.getBean(AiClient.class)).isInstanceOf(StubAiClient.class);
                });
    }

    @Test
    @DisplayName("When provider is 'openai' and API key is provided, OpenAiClient is loaded")
    void providerOpenAi_withApiKey_loadsOpenAiClient() {
        contextRunner
                .withPropertyValues(
                        "app.ai.provider=openai",
                        "app.ai.api-key=sk-test-1234567890",
                        "app.ai.model=gpt-4o-mini",
                        "app.ai.timeout-seconds=15"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(AiClient.class);
                    assertThat(context.getBean(AiClient.class)).isInstanceOf(OpenAiClient.class);
                });
    }

    @Test
    @DisplayName("When provider is 'gemini' and API key is provided, GeminiAiClient is loaded")
    void providerGemini_withApiKey_loadsGeminiAiClient() {
        contextRunner
                .withPropertyValues(
                        "app.ai.provider=gemini",
                        "app.ai.gemini.api-key=AIzaSyTest123456789",
                        "app.ai.gemini.model=gemini-2.5-flash",
                        "app.ai.gemini.timeout-seconds=20"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(AiClient.class);
                    assertThat(context.getBean(AiClient.class)).isInstanceOf(com.codeassess.ai.client.GeminiAiClient.class);
                });
    }

    @Test
    @DisplayName("When provider is 'gemini' but API key is missing, startup fails safely")
    void providerGemini_missingApiKey_failsFast() {
        contextRunner
                .withPropertyValues("app.ai.provider=gemini", "app.ai.gemini.api-key=", "app.ai.api-key=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("Gemini provider is enabled (app.ai.provider=gemini), but GEMINI_API_KEY (or app.ai.gemini.api-key) is missing or empty.");
                });
    }

    @Test
    @DisplayName("When provider is 'openai' but API key is missing, startup fails safely")
    void providerOpenAi_missingApiKey_failsFast() {
        contextRunner
                .withPropertyValues("app.ai.provider=openai", "app.ai.api-key=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("OpenAI provider is enabled (app.ai.provider=openai), but app.ai.api-key is missing or empty.");
                });
    }
}
