package com.codeassess.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class SaveAiExamRequest {

    @NotBlank(message = "Tên lớp học không được để trống")
    private String classId;

    @NotBlank(message = "Tiêu đề bài thi không được để trống")
    private String title;

    @NotNull(message = "Thời gian thi không được để trống")
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.codeassess.config.FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime examDate;

    @NotNull(message = "Thời lượng thi không được để trống")
    private Integer durationMinutes;

    private String problemStatement;
    private String inputSpecification;
    private String outputSpecification;
    private String constraints;
    private String sampleInput;
    private String sampleOutput;
    private String programmingLanguage;
    private String sourceDocumentName;

    private List<AiTestCaseDraftDto> testCases;
    private List<AiRubricItemDto> rubricCriteria;
}
