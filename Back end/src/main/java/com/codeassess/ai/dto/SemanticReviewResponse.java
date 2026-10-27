package com.codeassess.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SemanticReviewResponse {

    @NotBlank
    private String summary;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("10.0")
    private BigDecimal cleanCodeScore;

    @Builder.Default
    private List<CodeReviewIssueDto> issues = new ArrayList<>();

    @Builder.Default
    private String advisoryNotice = "Đánh giá chất lượng mã nguồn bằng AI mang tính chất tham khảo giáo dục và hỗ trợ cải thiện kỹ năng lập trình.";
}
