package com.codeassess.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiExamGenerateResponse {

    @NotBlank(message = "Tiêu đề bài thi không được để trống")
    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    @JsonAlias({"examTitle", "questionTitle", "name"})
    private String title;

    @NotBlank(message = "Mô tả đề bài không được để trống")
    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    @JsonAlias({
            "problem_statement", "statement", "description", "problem", "questionDescription", "content",
            "problem_description", "problemDescription", "task", "task_description", "taskDescription",
            "questionText", "question_text", "details", "prompt", "exercise", "requirements", "body"
    })
    private String problemStatement;

    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    @JsonAlias({"input_specification", "inputSpec", "input_spec", "inputFormat", "input_format"})
    private String inputSpecification;

    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    @JsonAlias({"output_specification", "outputSpec", "output_spec", "outputFormat", "output_format"})
    private String outputSpecification;

    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    @JsonAlias({"constraint", "limits", "constraints"})
    private String constraints;

    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    @JsonAlias({"sample_input", "exampleInput", "example_input"})
    private String sampleInput;

    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    @JsonAlias({"sample_output", "exampleOutput", "example_output"})
    private String sampleOutput;

    @JsonAlias({"example", "sampleCases", "samples"})
    private Object examples;

    @JsonAlias({"max_score", "score", "totalScore"})
    private BigDecimal maxScore;

    @Builder.Default
    @JsonAlias({"test_cases", "testcases", "tests"})
    private List<AiTestCaseDraftDto> testCases = new ArrayList<>();

    @NotEmpty(message = "Danh sách rubric không được để trống")
    @Valid
    @Builder.Default
    @JsonAlias({"rubric_criteria", "rubric", "criteria"})
    private List<AiRubricItemDto> rubricCriteria = new ArrayList<>();

    @Builder.Default
    @JsonAlias({"total_rubric_score", "rubricScore"})
    private BigDecimal totalRubricScore = BigDecimal.valueOf(10.00);

    // Explicit tracking requirements for Phase B
    private Integer requestedQuestionCount;
    private Integer generatedQuestionCount;
    private Integer requestedTestCasesPerQuestion;
    private Integer generatedTestCasesPerQuestion;

    @Builder.Default
    @JsonAlias({"question_list", "items", "examQuestions"})
    @JsonIgnoreProperties({"questions"})
    private List<AiExamGenerateResponse> questions = new ArrayList<>();
}
