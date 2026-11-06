package com.codeassess.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured exam outline response used for multi-question generation decomposition.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiExamOutlineResponse {

    @JsonAlias({"exam_title", "title", "name"})
    private String examTitle;

    @JsonAlias({"description", "summary", "desc"})
    private String overview;

    @Builder.Default
    @JsonAlias({"outline", "question_list", "items", "questionList"})
    private List<AiOutlineItemDto> questions = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AiOutlineItemDto {
        @JsonAlias({"index", "question_index", "id", "order"})
        private int questionIndex;

        @JsonAlias({"name", "questionTitle", "question_title"})
        private String title;

        @JsonAlias({"concept", "target_concept", "topic", "mainTopic"})
        private String targetConcept;

        @JsonAlias({"summary", "short_summary", "description", "desc"})
        private String shortSummary;

        @JsonAlias({"diff", "level"})
        private String difficulty;
    }
}
