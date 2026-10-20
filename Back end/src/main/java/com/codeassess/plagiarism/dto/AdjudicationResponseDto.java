package com.codeassess.plagiarism.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjudicationResponseDto {
    private String pairId;
    private String status;
    private String notes;
    private String adjudicatedBy;
    private LocalDateTime adjudicatedAt;
}
