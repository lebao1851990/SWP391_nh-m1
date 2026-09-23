package com.codeassess.plagiarism.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlagiarismRecordDto {
    private String recordId;
    private String examName;
    private String className;
    private SimilarityPairDto pair;
    private List<PlagiarismEvidenceDto> evidence;
}
