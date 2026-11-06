package com.codeassess.exam.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
public class ExamQuestionDraftDto {

    private String id;
    private String title;

    @JsonAlias({"description", "problemStatement"})
    private String description;

    @Builder.Default
    private BigDecimal maxScore = BigDecimal.valueOf(10.00);

    @Builder.Default
    private Integer displayOrder = 0;

    @Builder.Default
    private String status = "DRAFT";

    @Builder.Default
    private List<TestCaseDraftDto> testCases = new ArrayList<>();
}

