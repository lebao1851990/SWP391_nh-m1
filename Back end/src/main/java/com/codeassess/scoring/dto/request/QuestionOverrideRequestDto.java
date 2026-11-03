package com.codeassess.scoring.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
public class QuestionOverrideRequestDto {

    @NotNull(message = "Override score cannot be null")
    @DecimalMin(value = "0.00", message = "Override score cannot be negative")
    @DecimalMax(value = "10.00", message = "Override score cannot exceed 10.00")
    @Digits(integer = 3, fraction = 2, message = "Override score cannot have more than 2 decimal places")
    private BigDecimal overrideScore;

    private String rationale;
}

