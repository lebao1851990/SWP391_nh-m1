package com.codeassess.plagiarism.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppealResolveRequestDto {

    @NotBlank(message = "Decision is required (APPROVED or REJECTED)")
    @Pattern(regexp = "APPROVED|REJECTED", message = "Decision must be APPROVED or REJECTED")
    private String decision;

    @NotBlank(message = "Giải trình của người duyệt là bắt buộc khi xử lý khiếu nại")
    private String reviewerNotes;

    @DecimalMin(value = "0.0", message = "Điểm điều chỉnh không được nhỏ hơn 0")
    @DecimalMax(value = "100.0", message = "Điểm điều chỉnh không được vượt quá 100")
    private BigDecimal adjustedScore;
}
