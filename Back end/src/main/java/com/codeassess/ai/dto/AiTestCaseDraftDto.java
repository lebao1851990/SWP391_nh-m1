package com.codeassess.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class AiTestCaseDraftDto {

    @NotBlank
    private String name;

    private String description;

    private String inputData;

    private String expectedOutput;

    @Builder.Default
    private BigDecimal points = BigDecimal.valueOf(2.50);

    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonAlias({"hidden", "isHidden"})
    private Boolean isHidden = false;
}
