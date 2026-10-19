package com.codeassess.submission.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalGradingResponse {
    private String submissionId;
    private String status;
    private String executionStatus;
    private Integer exitCode;
    private String message;
}
