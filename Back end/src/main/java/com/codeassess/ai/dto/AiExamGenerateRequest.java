package com.codeassess.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiExamGenerateRequest {

    @NotBlank(message = "Chủ đề bài thi không được để trống")
    private String topic;

    @NotBlank(message = "Độ khó không được để trống")
    private String difficulty; // EASY, MEDIUM, HARD

    @NotBlank(message = "Ngôn ngữ lập trình không được để trống")
    private String programmingLanguage; // Java, Python, C++, etc.

    private String additionalRequirements;

    @Min(value = 1, message = "Số lượng tiêu chí rubric tối thiểu là 1")
    @Max(value = 8, message = "Số lượng tiêu chí rubric tối đa là 8")
    @Builder.Default
    private Integer criteriaCount = 4;

    @Min(value = 1, message = "Số lượng câu hỏi tối thiểu là 1")
    @Max(value = 5, message = "Số lượng câu hỏi tối đa là 5")
    @Builder.Default
    private Integer questionCount = 1;

    @Min(value = 2, message = "Số lượng test cases tối thiểu là 2")
    @Max(value = 6, message = "Số lượng test cases tối đa là 6")
    @Builder.Default
    private Integer testCasesPerQuestion = 3;
}
