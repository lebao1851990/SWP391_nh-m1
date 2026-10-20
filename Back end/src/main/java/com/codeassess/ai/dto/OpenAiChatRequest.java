package com.codeassess.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenAiChatRequest(
    String model,
    List<ChatMessage> messages,
    Double temperature,
    @JsonProperty("max_tokens") Integer maxTokens
) {
    public record ChatMessage(String role, String content) {}
}
