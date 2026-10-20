package com.codeassess.testcase.dto.request;

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
public class CreateTestCaseRequest {

    @NotBlank(message = "Tên test case không được để trống")
    private String name;

    private String description;

    private String inputData;

    private String expectedOutput;

    @NotNull(message = "Điểm test case là bắt buộc")
    @DecimalMin(value = "0.0", inclusive = true, message = "Điểm test case phải lớn hơn hoặc bằng 0")
    private BigDecimal points;

    private Boolean isHidden;

    private Integer displayOrder;
}
