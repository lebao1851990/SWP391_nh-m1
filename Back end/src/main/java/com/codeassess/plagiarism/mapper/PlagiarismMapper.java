package com.codeassess.plagiarism.mapper;

import com.codeassess.plagiarism.dto.CodeComparisonDataDto;
import com.codeassess.plagiarism.dto.PlagiarismEvidenceDto;
import com.codeassess.plagiarism.dto.PlagiarismRecordDto;
import com.codeassess.plagiarism.dto.SimilarityPairDto;
import com.codeassess.plagiarism.dto.StudentPlagiarismDetailDto;
import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import com.codeassess.plagiarism.entity.SimilarityPair;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class PlagiarismMapper {

    public SimilarityPairDto toPairDto(SimilarityPair pair) {
        return SimilarityPairDto.builder()
                .pairId(String.valueOf(pair.getId()))
                .submissionAId(pair.getSubmissionA().getId())
                .submissionBId(pair.getSubmissionB().getId())
                .studentAId(pair.getSubmissionA().getStudent().getId())
                .studentBId(pair.getSubmissionB().getStudent().getId())
                .studentAName(pair.getSubmissionA().getStudent().getFullName())
                .studentBName(pair.getSubmissionB().getStudent().getFullName())
                .similarityRate(pair.getSimilarityRate())
                .isFlagged(pair.getIsFlagged())
                .examId(pair.getExam() != null ? pair.getExam().getId() : null)
                .examTitle(pair.getExam() != null ? pair.getExam().getTitle() : null)
                .className(pair.getExam() != null && pair.getExam().getCourseClass() != null ? pair.getExam().getCourseClass().getName() : null)
                .adjudicationStatus(pair.getAdjudicationStatus() != null ? pair.getAdjudicationStatus() : "PENDING")
                .adjudicationNotes(pair.getAdjudicationNotes())
                .adjudicatedBy(pair.getAdjudicatedBy() != null ? 
                        (pair.getAdjudicatedBy().getFullName() != null ? pair.getAdjudicatedBy().getFullName() : pair.getAdjudicatedBy().getEmail()) : null)
                .adjudicatedAt(pair.getAdjudicatedAt())
                .build();
    }

    public PlagiarismEvidenceDto toEvidenceDto(PlagiarismEvidence evidence) {
        // We will assign a neutral severity of 'medium' or 'high' based on the system requirements.
        // For E3.6, 'medium' is a fine default since flagged handles the overall flag.
        return PlagiarismEvidenceDto.builder()
                .evidenceId(evidence.getId())
                .description(evidence.getDescription())
                .severity("medium")
                .build();
    }

    public PlagiarismRecordDto toRecordDto(SimilarityPair pair, List<PlagiarismEvidence> evidences) {
        return PlagiarismRecordDto.builder()
                .recordId(String.valueOf(pair.getId()))
                .examName(pair.getExam().getTitle())
                .className(pair.getExam().getCourseClass().getName())
                .pair(toPairDto(pair))
                .evidence(evidences.stream().map(this::toEvidenceDto).collect(Collectors.toList()))
                .build();
    }

    public CodeComparisonDataDto toComparisonDto(SimilarityPair pair, List<PlagiarismEvidence> evidences) {
        List<CodeComparisonDataDto.MatchDto> matches = evidences.stream().map(e ->
                CodeComparisonDataDto.MatchDto.builder()
                        .startLineA(e.getStartLineA())
                        .endLineA(e.getEndLineA())
                        .startLineB(e.getStartLineB())
                        .endLineB(e.getEndLineB())
                        .type(e.getEvidenceType())
                        .build()
        ).collect(Collectors.toList());

        return CodeComparisonDataDto.builder()
                .pairId(String.valueOf(pair.getId()))
                .fileA("StudentA.java")
                .fileB("StudentB.java")
                .sourceCodeA(pair.getSubmissionA().getSourceCode())
                .sourceCodeB(pair.getSubmissionB().getSourceCode())
                .matches(matches)
                .adjudicationStatus(pair.getAdjudicationStatus() != null ? pair.getAdjudicationStatus() : "PENDING")
                .adjudicationNotes(pair.getAdjudicationNotes())
                .adjudicatedBy(pair.getAdjudicatedBy() != null ? 
                        (pair.getAdjudicatedBy().getFullName() != null ? pair.getAdjudicatedBy().getFullName() : pair.getAdjudicatedBy().getEmail()) : null)
                .adjudicatedAt(pair.getAdjudicatedAt())
                .build();
    }

    public StudentPlagiarismDetailDto toStudentDetailDto(SimilarityPair pair, List<PlagiarismEvidence> evidences) {
        return StudentPlagiarismDetailDto.builder()
                .recordId(String.valueOf(pair.getId()))
                .examName(pair.getExam().getTitle())
                .similarityRate(pair.getSimilarityRate())
                .isFlagged(pair.getIsFlagged())
                .evidence(evidences.stream().map(this::toEvidenceDto).collect(Collectors.toList()))
                .build();
    }
}
