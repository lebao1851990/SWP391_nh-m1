package com.codeassess.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiExamGenerateResponse {

    @NotBlank(message = "Tiêu đề bài thi không được để trống")
    private String title;

    @NotBlank(message = "Mô tả đề bài không được để trống")
    private String problemStatement;

    private String inputSpecification;

    private String outputSpecification;

    private String constraints;

    private String sampleInput;

    private String sampleOutput;

    @Builder.Default
    private List<AiTestCaseDraftDto> testCases = new ArrayList<>();

    @NotEmpty(message = "Danh sách rubric không được để trống")
    @Valid
    @Builder.Default
    private List<AiRubricItemDto> rubricCriteria = new ArrayList<>();

    @Builder.Default
    private BigDecimal totalRubricScore = BigDecimal.valueOf(10.00);
}
