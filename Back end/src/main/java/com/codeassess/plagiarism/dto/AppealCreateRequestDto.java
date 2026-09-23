package com.codeassess.plagiarism.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AppealCreateRequestDto {
    @NotBlank(message = "Submission ID is required")
    private String submissionId;

    @NotBlank(message = "Please select a basis for your appeal")
    private String basis;

    @NotBlank(message = "Please provide an explanation")
    private String explanation;
}
