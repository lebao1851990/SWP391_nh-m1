package com.codeassess.submission.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SubmissionDetailResponse {
    private String id;
    private String examId;
    private String studentId;
    private String status;
    private String sourceCode;
    private BigDecimal totalScore;
    private BigDecimal overrideScore;
    private LocalDateTime evaluatedAt;
    private LocalDateTime createdAt;
}
