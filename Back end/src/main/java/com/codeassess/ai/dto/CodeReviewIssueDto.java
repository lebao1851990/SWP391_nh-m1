package com.codeassess.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CodeReviewIssueDto {

    private Integer line;

    @NotNull
    private ReviewIssueSeverity severity;

    @NotNull
    private ReviewIssueCategory category;

    @NotNull
    private String message;

    private String suggestion;
}
