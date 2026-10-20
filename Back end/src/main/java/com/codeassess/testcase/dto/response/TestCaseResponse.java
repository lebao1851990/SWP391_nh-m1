package com.codeassess.testcase.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseResponse {

    private String id;
    private String examId;
    private String name;
    private String description;
    private String inputData;
    private String expectedOutput;
    private BigDecimal points;
    private Boolean isHidden;
    private Integer displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
