package com.codeassess.scoring.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.scoring.dto.request.ApproveGradeRequest;
import com.codeassess.scoring.dto.request.RequestRevisionRequest;
import com.codeassess.scoring.dto.response.*;
import com.codeassess.scoring.entity.GradeAuditLog;
import com.codeassess.scoring.entity.RuleScore;
import com.codeassess.scoring.repository.GradeAuditLogRepository;
import com.codeassess.scoring.repository.RuleScoreRepository;
import com.codeassess.scoring.repository.StudentQuestionScoreRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.testcase.entity.SubmissionTestResult;
import com.codeassess.testcase.repository.SubmissionTestResultRepository;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeacherGradingService {

    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final RuleScoreRepository ruleScoreRepository;
    private final GradeAuditLogRepository gradeAuditLogRepository;
    private final SubmissionTestResultRepository submissionTestResultRepository;
    private final StudentQuestionScoreRepository studentQuestionScoreRepository;

    @Transactional(readOnly = true)
    public List<TeacherGradingItemDto> getTeacherGradingSubmissions(String teacherId, String status) {
        String filterStatus = (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status.trim()))
                ? status.trim().toUpperCase()
                : null;

        List<Submission> submissions = submissionRepository.findByTeacherIdAndReviewStatus(teacherId, filterStatus);

        return submissions.stream()
                .map(this::toItemDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TeacherGradingDetailResponse getGradingDetail(String submissionId, String teacherId) {
        Submission submission = submissionRepository.findByIdAndTeacherAccess(submissionId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found or access denied"));

        List<RuleScore> ruleScores = ruleScoreRepository.findBySubmission_Id(submissionId);
        List<CriterionScoreDto> criteriaDtos = ruleScores.stream().map(rs -> CriterionScoreDto.builder()
                .criterionId(rs.getCriteria().getId())
                .name(rs.getCriteria().getName())
                .description(rs.getCriteria().getDescription())
                .score(rs.getScore())
                .maxScore(rs.getCriteria().getMaxScore())
                .build()).collect(Collectors.toList());

        List<SubmissionTestResult> testResults = submissionTestResultRepository != null
                ? submissionTestResultRepository.findBySubmission_IdOrderByIdAsc(submissionId)
                : Collections.emptyList();

        // Teachers are fully authorized to inspect both public and hidden test cases with outputs
        List<com.codeassess.testcase.dto.response.SubmissionTestResultDto> testResultDtos = testResults.stream().map(tr ->
                com.codeassess.testcase.dto.response.SubmissionTestResultDto.builder()
                        .id(tr.getId())
                        .testCaseId(tr.getTestCase() != null ? tr.getTestCase().getId() : null)
                        .testCaseName(tr.getTestCaseName())
                        .isHidden(tr.getIsHidden())
                        .status(tr.getStatus())
                        .actualOutput(tr.getActualOutput())
                        .expectedOutput(tr.getExpectedOutput())
                        .executionTimeMs(tr.getExecutionTimeMs())
                        .exitCode(tr.getExitCode())
                        .errorMessage(tr.getErrorMessage())
                        .pointsAwarded(tr.getPointsAwarded())
                        .maxPoints(tr.getTestCase() != null ? tr.getTestCase().getPoints() : null)
                        .build()
        ).collect(Collectors.toList());

        List<GradeAuditLog> auditLogs = gradeAuditLogRepository.findBySubmission_IdOrderByCreatedAtDesc(submissionId);
        List<GradeAuditLogDto> auditLogDtos = auditLogs.stream().map(auditRecord -> GradeAuditLogDto.builder()
                .id(auditRecord.getId())
                .submissionId(submissionId)
                .actorId(auditRecord.getActor() != null ? auditRecord.getActor().getId() : null)
                .actorName(auditRecord.getActor() != null ? auditRecord.getActor().getFullName() : null)
                .actorEmail(auditRecord.getActor() != null ? auditRecord.getActor().getEmail() : null)
                .actionType(auditRecord.getActionType())
                .preRubricScore(auditRecord.getPreRubricScore())
                .postRubricScore(auditRecord.getPostRubricScore())
                .preOverrideScore(auditRecord.getPreOverrideScore())
                .postOverrideScore(auditRecord.getPostOverrideScore())
                .preEffectiveScore(auditRecord.getPreEffectiveScore())
                .postEffectiveScore(auditRecord.getPostEffectiveScore())
                .rationale(auditRecord.getRationale())
                .createdAt(auditRecord.getCreatedAt())
                .build()).collect(Collectors.toList());

        // Previous attempts by the same student for this question/exam
        List<TeacherGradingItemDto> previousAttempts = Collections.emptyList();
        if (submission.getExam() != null && submission.getStudent() != null) {
            previousAttempts = submissionRepository.findByExam_IdAndStudent_IdOrderByCreatedAtDesc(
                    submission.getExam().getId(), submission.getStudent().getId()).stream()
                    .filter(s -> !s.getId().equals(submissionId))
                    .map(this::toItemDto)
                    .collect(Collectors.toList());
        }

        BigDecimal effectiveScore = submission.getOverrideScore() != null
                ? submission.getOverrideScore()
                : (submission.getTotalScore() != null ? submission.getTotalScore() : submission.getAutoScore());

        return TeacherGradingDetailResponse.builder()
                .submissionId(submission.getId())
                .examId(submission.getExam() != null ? submission.getExam().getId() : null)
                .examTitle(submission.getExam() != null ? submission.getExam().getTitle() : null)
                .className(submission.getExam() != null && submission.getExam().getCourseClass() != null
                        ? submission.getExam().getCourseClass().getName() : null)
                .questionId(submission.getQuestion() != null ? submission.getQuestion().getId() : null)
                .questionTitle(submission.getQuestion() != null ? submission.getQuestion().getTitle() : null)
                .questionDescription(submission.getQuestion() != null ? submission.getQuestion().getDescription() : null)
                .questionMaxScore(submission.getQuestion() != null ? submission.getQuestion().getMaxScore() : BigDecimal.valueOf(10.00))
                .studentId(submission.getStudent() != null ? submission.getStudent().getId() : null)
                .studentName(submission.getStudent() != null ? submission.getStudent().getFullName() : null)
                .studentEmail(submission.getStudent() != null ? submission.getStudent().getEmail() : null)
                .submissionTime(submission.getCreatedAt())
                .attemptNumber(1)
                .submissionType(submission.getSubmissionType())
                .originalFilename(submission.getOriginalFilename())
                .archiveHash(submission.getArchiveHash())
                .executionStatus(submission.getExecutionStatus())
                .exitCode(submission.getExitCode())
                .compileOutput(submission.getCompileOutput())
                .stdout(submission.getStdout())
                .stderr(submission.getStderr())
                .executionDurationMs(submission.getExecutionDurationMs())
                .provisionalScore(submission.getAutoScore())
                .autoScore(submission.getAutoScore())
                .overrideScore(submission.getOverrideScore())
                .effectiveScore(effectiveScore)
                .reviewStatus(submission.getReviewStatus())
                .publicationStatus(submission.getPublicationStatus())
                .reviewedById(submission.getReviewedBy() != null ? submission.getReviewedBy().getId() : null)
                .reviewedByName(submission.getReviewedBy() != null ? submission.getReviewedBy().getFullName() : null)
                .reviewedAt(submission.getReviewedAt())
                .reviewNotes(submission.getReviewNotes())
                .approvedScore(submission.getApprovedScore())
                .publishedById(submission.getPublishedBy() != null ? submission.getPublishedBy().getId() : null)
                .publishedByName(submission.getPublishedBy() != null ? submission.getPublishedBy().getFullName() : null)
                .publishedAt(submission.getPublishedAt())
                .publishedScore(submission.getPublishedScore())
                .scoreVersion(submission.getScoreVersion())
                .testCasesPassed(submission.getTestCasesPassed())
                .testCasesTotal(submission.getTestCasesTotal())
                .testResults(testResultDtos)
                .criteria(criteriaDtos)
                .auditLogs(auditLogDtos)
                .previousAttempts(previousAttempts)
                .build();
    }

    @Transactional
    public TeacherGradingDetailResponse approveGrade(String submissionId, String teacherId, ApproveGradeRequest request) {
        Submission submission = submissionRepository.findByIdAndTeacherAccess(submissionId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found or access denied"));

        if ("PENDING".equalsIgnoreCase(submission.getStatus()) || "PROCESSING".equalsIgnoreCase(submission.getStatus())) {
            throw new IllegalStateException("Không thể phê duyệt bài nộp đang trong quá trình chấm điểm tự động.");
        }

        User reviewer = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found: " + teacherId));

        BigDecimal currentEffective = submission.getOverrideScore() != null
                ? submission.getOverrideScore()
                : (submission.getTotalScore() != null ? submission.getTotalScore() : submission.getAutoScore());

        if (currentEffective == null) {
            currentEffective = BigDecimal.ZERO;
        }

        BigDecimal preEffective = submission.getApprovedScore() != null ? submission.getApprovedScore() : currentEffective;

        submission.setReviewStatus("APPROVED");
        submission.setReviewedBy(reviewer);
        submission.setReviewedAt(LocalDateTime.now());
        submission.setReviewNotes(request != null && request.getNotes() != null ? request.getNotes().trim() : null);
        submission.setApprovedScore(currentEffective);
        submission.setScoreVersion(submission.getScoreVersion() != null ? submission.getScoreVersion() + 1 : 1);

        submissionRepository.save(submission);

        String notes = (request != null && request.getNotes() != null && !request.getNotes().trim().isEmpty())
                ? request.getNotes().trim()
                : "Phê duyệt đạt chuẩn";

        GradeAuditLog auditLog = GradeAuditLog.builder()
                .submission(submission)
                .actor(reviewer)
                .actionType("GRADE_APPROVED")
                .preRubricScore(submission.getTotalScore())
                .postRubricScore(submission.getTotalScore())
                .preOverrideScore(submission.getOverrideScore())
                .postOverrideScore(submission.getOverrideScore())
                .preEffectiveScore(preEffective)
                .postEffectiveScore(currentEffective)
                .rationale("Giảng viên " + reviewer.getFullName() + " đã phê duyệt điểm (" + currentEffective + " đ). Ghi chú: " + notes)
                .build();

        gradeAuditLogRepository.save(auditLog);

        log.info("Teacher {} approved grade for submission {} with score {}", teacherId, submissionId, currentEffective);

        return getGradingDetail(submissionId, teacherId);
    }

    @Transactional
    public TeacherGradingDetailResponse requestRevision(String submissionId, String teacherId, RequestRevisionRequest request) {
        Submission submission = submissionRepository.findByIdAndTeacherAccess(submissionId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found or access denied"));

        User reviewer = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found: " + teacherId));

        if (request == null || request.getReason() == null || request.getReason().trim().length() < 10) {
            throw new IllegalArgumentException("Lý do yêu cầu làm lại/chỉnh sửa phải có ít nhất 10 ký tự.");
        }

        BigDecimal currentEffective = submission.getOverrideScore() != null
                ? submission.getOverrideScore()
                : (submission.getTotalScore() != null ? submission.getTotalScore() : submission.getAutoScore());

        submission.setReviewStatus("REVISION_REQUIRED");
        submission.setPublicationStatus("UNPUBLISHED");
        submission.setPublishedScore(null);
        submission.setReviewedBy(reviewer);
        submission.setReviewedAt(LocalDateTime.now());
        submission.setReviewNotes(request.getReason().trim());
        submission.setScoreVersion(submission.getScoreVersion() != null ? submission.getScoreVersion() + 1 : 1);

        submissionRepository.save(submission);

        if (studentQuestionScoreRepository != null && submission.getQuestion() != null && submission.getStudent() != null) {
            studentQuestionScoreRepository.unpublishQuestionScore(submission.getQuestion().getId(), submission.getStudent().getId());
        }

        GradeAuditLog auditLog = GradeAuditLog.builder()
                .submission(submission)
                .actor(reviewer)
                .actionType("GRADE_REVISION_REQUESTED")
                .preRubricScore(submission.getTotalScore())
                .postRubricScore(submission.getTotalScore())
                .preOverrideScore(submission.getOverrideScore())
                .postOverrideScore(submission.getOverrideScore())
                .preEffectiveScore(currentEffective)
                .postEffectiveScore(currentEffective)
                .rationale("Giảng viên " + reviewer.getFullName() + " yêu cầu làm lại: " + request.getReason().trim())
                .build();

        gradeAuditLogRepository.save(auditLog);

        log.info("Teacher {} requested revision for submission {}: {}", teacherId, submissionId, request.getReason().trim());

        return getGradingDetail(submissionId, teacherId);
    }

    @Transactional
    public TeacherGradingDetailResponse publishGrade(String submissionId, String teacherId) {
        Submission submission = submissionRepository.findByIdAndTeacherAccess(submissionId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found or access denied"));

        if (!"APPROVED".equalsIgnoreCase(submission.getReviewStatus())) {
            throw new IllegalStateException("Chỉ có thể công bố bài thi đã được phê duyệt (APPROVED). Trạng thái hiện tại: " + submission.getReviewStatus());
        }

        BigDecimal currentEffective = submission.getOverrideScore() != null
                ? submission.getOverrideScore()
                : (submission.getTotalScore() != null ? submission.getTotalScore() : submission.getAutoScore());

        if (submission.getApprovedScore() == null || currentEffective == null || currentEffective.compareTo(submission.getApprovedScore()) != 0) {
            throw new IllegalStateException("Điểm số đã thay đổi sau lần phê duyệt gần nhất. Vui lòng phê duyệt lại điểm số trước khi công bố.");
        }

        User publisher = userRepository.findById(teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found: " + teacherId));

        submission.setPublicationStatus("PUBLISHED");
        submission.setPublishedBy(publisher);
        submission.setPublishedAt(LocalDateTime.now());
        submission.setPublishedScore(submission.getApprovedScore());

        submissionRepository.save(submission);

        if (studentQuestionScoreRepository != null && submission.getQuestion() != null && submission.getStudent() != null) {
            studentQuestionScoreRepository.publishQuestionScore(
                    submission.getQuestion().getId(),
                    submission.getStudent().getId(),
                    submission.getApprovedScore()
            );
        }

        GradeAuditLog auditLog = GradeAuditLog.builder()
                .submission(submission)
                .actor(publisher)
                .actionType("GRADE_PUBLISHED")
                .preRubricScore(submission.getTotalScore())
                .postRubricScore(submission.getTotalScore())
                .preOverrideScore(submission.getOverrideScore())
                .postOverrideScore(submission.getOverrideScore())
                .preEffectiveScore(submission.getApprovedScore())
                .postEffectiveScore(submission.getApprovedScore())
                .rationale("Giảng viên " + publisher.getFullName() + " đã công bố điểm chính thức cho sinh viên: " + submission.getApprovedScore() + " đ.")
                .build();

        gradeAuditLogRepository.save(auditLog);

        log.info("Teacher {} published grade for submission {} with score {}", teacherId, submissionId, submission.getApprovedScore());

        return getGradingDetail(submissionId, teacherId);
    }

    private TeacherGradingItemDto toItemDto(Submission sub) {
        BigDecimal effective = sub.getOverrideScore() != null
                ? sub.getOverrideScore()
                : (sub.getTotalScore() != null ? sub.getTotalScore() : sub.getAutoScore());

        return TeacherGradingItemDto.builder()
                .submissionId(sub.getId())
                .examId(sub.getExam() != null ? sub.getExam().getId() : null)
                .examTitle(sub.getExam() != null ? sub.getExam().getTitle() : null)
                .questionId(sub.getQuestion() != null ? sub.getQuestion().getId() : null)
                .questionTitle(sub.getQuestion() != null ? sub.getQuestion().getTitle() : null)
                .questionOrder(sub.getQuestion() != null ? sub.getQuestion().getDisplayOrder() : 0)
                .studentId(sub.getStudent() != null ? sub.getStudent().getId() : null)
                .studentName(sub.getStudent() != null ? sub.getStudent().getFullName() : null)
                .studentEmail(sub.getStudent() != null ? sub.getStudent().getEmail() : null)
                .submissionTime(sub.getCreatedAt())
                .attemptNumber(1)
                .provisionalScore(sub.getAutoScore())
                .maxScore(sub.getQuestion() != null ? sub.getQuestion().getMaxScore() : BigDecimal.valueOf(10.00))
                .reviewStatus(sub.getReviewStatus() != null ? sub.getReviewStatus() : "PENDING_REVIEW")
                .publicationStatus(sub.getPublicationStatus() != null ? sub.getPublicationStatus() : "UNPUBLISHED")
                .executionStatus(sub.getExecutionStatus())
                .approvedScore(sub.getApprovedScore())
                .publishedScore(sub.getPublishedScore())
                .testCasesPassed(sub.getTestCasesPassed())
                .testCasesTotal(sub.getTestCasesTotal())
                .build();
    }
}
