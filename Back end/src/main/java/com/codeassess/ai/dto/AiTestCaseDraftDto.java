package com.codeassess.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiTestCaseDraftDto {

    @NotBlank
    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    @JsonAlias({"test_case_name", "testCaseName", "title", "test_name", "testName", "caseName", "case_name", "name", "id"})
    private String name;

    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    @JsonAlias({"desc", "description", "details", "explanation"})
    private String description;

    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    @JsonAlias({"input_data", "inputData", "input", "stdin", "sampleInput", "input_value", "inputValue", "args", "arguments", "parameters", "inputs", "data", "in"})
    private String inputData;

    @JsonDeserialize(using = FlexibleStringDeserializer.class)
    @JsonAlias({"expected_output", "expectedOutput", "output", "stdout", "sampleOutput", "expected", "target", "result", "expected_result", "expectedResult", "output_data", "outputData", "returnValue", "return_value", "answer", "out"})
    private String expectedOutput;

    @Builder.Default
    @JsonAlias({"score", "weight", "point", "points"})
    private BigDecimal points = BigDecimal.valueOf(2.50);

    @Builder.Default
    @JsonAlias({"hidden", "isHidden", "is_hidden", "private", "visibility"})
    private Boolean isHidden = false;

    @JsonSetter("visibility")
    public void setVisibilityValue(Object value) {
        if (value instanceof Boolean b) {
            this.isHidden = !b;
        } else if (value instanceof String s) {
            String val = s.trim().toLowerCase();
            if (val.contains("hide") || val.contains("hidden") || val.contains("private") || val.contains("secret")) {
                this.isHidden = true;
            } else if (val.contains("public") || val.contains("visible") || val.contains("open")) {
                this.isHidden = false;
            }
        }
    }
}
