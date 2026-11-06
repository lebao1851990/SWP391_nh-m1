package com.codeassess.exam.dto.response;

import com.codeassess.testcase.dto.response.TestCaseResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamQuestionResponse {
    private String id;
    private String examId;
    private String title;
    private String description;
    private BigDecimal maxScore;
    private Integer displayOrder;
    private String status;
    private LocalDateTime createdAt;
    private List<TestCaseResponse> testCases;
}

