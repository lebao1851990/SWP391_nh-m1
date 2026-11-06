package com.codeassess.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class AiRubricItemDto {

    @NotBlank(message = "Tên tiêu chí không được để trống")
    @JsonAlias({"criteria", "criterion", "title", "criterionName", "criterion_name"})
    private String name;

    @JsonAlias({"desc"})
    private String description;

    @NotNull(message = "Điểm tối đa không được để trống")
    @DecimalMin(value = "0.01", message = "Điểm tối đa phải lớn hơn 0")
    @JsonAlias({"score", "max_score", "points", "weight"})
    private BigDecimal maxScore;
}
