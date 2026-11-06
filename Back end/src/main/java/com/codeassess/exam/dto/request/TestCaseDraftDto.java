package com.codeassess.exam.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestCaseDraftDto {

    private String id;
    private String name;
    private String description;
    private String inputData;
    private String expectedOutput;
    private BigDecimal points;
    private Boolean isHidden;
    private Integer displayOrder;
}

