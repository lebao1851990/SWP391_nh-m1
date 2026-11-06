package com.codeassess.submission.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionCreateRequest {

    @NotBlank(message = "Source code cannot be empty")
    private String sourceCode;

    /**
     * Optional questionId for multi-question submission.
     * If null, resolves to the exam's default/first question.
     */
    private String questionId;

    public SubmissionCreateRequest(String sourceCode) {
        this.sourceCode = sourceCode;
        this.questionId = null;
    }
}
