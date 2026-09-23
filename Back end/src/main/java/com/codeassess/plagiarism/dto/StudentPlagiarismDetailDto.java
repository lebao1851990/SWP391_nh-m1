package com.codeassess.plagiarism.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentPlagiarismDetailDto {
    private String recordId;
    private String examName;
    private BigDecimal similarityRate;
    private boolean isFlagged;
    private List<PlagiarismEvidenceDto> evidence;
}
