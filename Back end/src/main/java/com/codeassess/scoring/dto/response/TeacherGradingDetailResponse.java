package com.codeassess.scoring.dto.response;

import com.codeassess.testcase.dto.response.SubmissionTestResultDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherGradingDetailResponse {
    private String submissionId;
    private String examId;
    private String examTitle;
    private String className;
    private String questionId;
    private String questionTitle;
    private String questionDescription;
    private BigDecimal questionMaxScore;
    private String studentId;
    private String studentName;
    private String studentEmail;
    private LocalDateTime submissionTime;
    private Integer attemptNumber;
    private String submissionType;
    private String originalFilename;
    private String archiveHash;
    private String executionStatus;
    private Integer exitCode;
    private String compileOutput;
    private String stdout;
    private String stderr;
    private Long executionDurationMs;
    private BigDecimal provisionalScore;
    private BigDecimal autoScore;
    private BigDecimal overrideScore;
    private BigDecimal effectiveScore;
    private String reviewStatus;
    private String publicationStatus;
    private String reviewedById;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private String reviewNotes;
    private BigDecimal approvedScore;
    private String publishedById;
    private String publishedByName;
    private LocalDateTime publishedAt;
    private BigDecimal publishedScore;
    private Integer scoreVersion;
    private Integer testCasesPassed;
    private Integer testCasesTotal;
    private List<SubmissionTestResultDto> testResults;
    private List<CriterionScoreDto> criteria;
    private List<GradeAuditLogDto> auditLogs;
    private List<TeacherGradingItemDto> previousAttempts;
}

