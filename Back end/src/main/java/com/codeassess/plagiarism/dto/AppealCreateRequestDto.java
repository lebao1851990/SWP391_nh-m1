package com.codeassess.plagiarism.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppealCreateRequestDto {
    @NotBlank(message = "Submission ID is required")
    private String submissionId;

    @NotBlank(message = "Please select a basis for your appeal")
    private String basis;

    @NotBlank(message = "Please provide an explanation")
    @Size(min = 20, max = 1000, message = "Giải trình khiếu nại phải có độ dài từ 20 đến 1000 ký tự")
    private String explanation;
}
