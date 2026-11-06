package com.codeassess.plagiarism.service;

import com.codeassess.common.exception.ConflictException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.plagiarism.dto.AppealResolveRequestDto;
import com.codeassess.plagiarism.dto.AppealResponseDto;
import com.codeassess.plagiarism.entity.Appeal;
import com.codeassess.plagiarism.repository.AppealRepository;
import com.codeassess.scoring.entity.GradeAuditLog;
import com.codeassess.scoring.repository.GradeAuditLogRepository;
import com.codeassess.scoring.repository.StudentQuestionScoreRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.submission.service.SubmissionStorageService;
import com.codeassess.user.entity.User;
import com.codeassess.websocket.service.WebSocketBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAppealService {

    private final AppealRepository appealRepository;
    private final SubmissionRepository submissionRepository;
    private final GradeAuditLogRepository gradeAuditLogRepository;
    private final SubmissionStorageService submissionStorageService;

    @Autowired(required = false)
    private StudentQuestionScoreRepository studentQuestionScoreRepository;

    @Autowired(required = false)
    private com.codeassess.testcase.service.AutomatedTestRunnerService automatedTestRunnerService;

    @Autowired(required = false)
    private WebSocketBroadcaster webSocketBroadcaster;

    @Transactional(readOnly = true)
    public List<AppealResponseDto> getAdminAppeals(String status) {
        List<Appeal> appeals;
        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status.trim())) {
            appeals = appealRepository.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase());
        } else {
            appeals = appealRepository.findAllByOrderByCreatedAtDesc();
        }

        return appeals.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AppealResponseDto getAdminAppeal(String opaqueId) {
        Long id = OpaqueIdEncoder.decode(opaqueId);
        if (id == null) {
            throw new ResourceNotFoundException("Appeal not found");
        }

        Appeal appeal = appealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appeal not found"));

        return toDto(appeal);
    }

    @Transactional
    public AppealResponseDto startReview(String opaqueId, User admin) {
        Long id = OpaqueIdEncoder.decode(opaqueId);
        if (id == null) {
            throw new ResourceNotFoundException("Appeal not found");
        }

        Appeal appeal = appealRepository.findByIdForUpdate(id)
                .or(() -> appealRepository.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException("Appeal not found"));

        if (!"PENDING".equalsIgnoreCase(appeal.getStatus())) {
            throw new ConflictException("Chỉ có thể bắt đầu thẩm định đơn khiếu nại đang ở trạng thái PENDING. Trạng thái hiện tại: " + appeal.getStatus());
        }

        appeal.setStatus("UNDER_REVIEW");
        appeal.setAdmin(admin);
        Appeal saved = appealRepository.save(appeal);

        Submission submission = appeal.getSubmission();
        if (submission != null) {
            BigDecimal currentEffective = submission.getOverrideScore() != null ? submission.getOverrideScore()
                    : (submission.getTotalScore() != null ? submission.getTotalScore() : submission.getAutoScore());
            String actorName = admin != null ? (admin.getFullName() != null ? admin.getFullName() : admin.getEmail()) : "Admin";

            GradeAuditLog auditLog = GradeAuditLog.builder()
                    .submission(submission)
                    .actor(admin)
                    .actionType("APPEAL_UNDER_REVIEW")
                    .preRubricScore(submission.getTotalScore())
                    .postRubricScore(submission.getTotalScore())
                    .preOverrideScore(submission.getOverrideScore())
                    .postOverrideScore(submission.getOverrideScore())
                    .preEffectiveScore(currentEffective)
                    .postEffectiveScore(currentEffective)
                    .rationale("Quản trị viên " + actorName + " đã tiếp nhận và bắt đầu thẩm định khiếu nại #" + appeal.getId())
                    .build();
            gradeAuditLogRepository.save(auditLog);
        }

        log.info("Admin {} started review for appeal {}", admin != null ? admin.getId() : "null", appeal.getId());
        return toDto(saved);
    }

    @Transactional
    public AppealResponseDto resolveAppeal(String opaqueId, AppealResolveRequestDto dto, User admin) {
        Long id = OpaqueIdEncoder.decode(opaqueId);
        if (id == null) {
            throw new ResourceNotFoundException("Appeal not found");
        }

        Appeal appeal = appealRepository.findByIdForUpdate(id)
                .or(() -> appealRepository.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException("Appeal not found"));

        // Guard: Prevent re-resolution or illegal modifications after resolution
        if (!"PENDING".equalsIgnoreCase(appeal.getStatus()) && !"UNDER_REVIEW".equalsIgnoreCase(appeal.getStatus())) {
            throw new ConflictException("Đơn khiếu nại này đã được giải quyết trước đó và không thể thay đổi.");
        }

        if (dto.getReviewerNotes() == null || dto.getReviewerNotes().trim().isEmpty()) {
            throw new IllegalArgumentException("Giải trình của người duyệt là bắt buộc khi xử lý khiếu nại.");
        }

        String decision = dto.getDecision() != null ? dto.getDecision().trim().toUpperCase() : "";
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            throw new IllegalArgumentException("Quyết định phải là APPROVED hoặc REJECTED.");
        }

        Submission submission = appeal.getSubmission();
        BigDecimal preRubricScore = submission != null ? submission.getTotalScore() : null;
        BigDecimal preOverrideScore = submission != null ? submission.getOverrideScore() : null;
        BigDecimal preEffectiveScore = preOverrideScore != null ? preOverrideScore : preRubricScore;
        String actorName = admin != null ? (admin.getFullName() != null ? admin.getFullName() : admin.getEmail()) : "Admin";

        appeal.setAdmin(admin);
        appeal.setReviewerNotes(dto.getReviewerNotes().trim());
        appeal.setResolvedAt(LocalDateTime.now());
        String resAction = dto.getResolutionAction() != null ? dto.getResolutionAction().trim().toUpperCase() : null;
        appeal.setResolutionAction(resAction);

        if ("APPROVED".equals(decision)) {
            appeal.setStatus("APPROVED");

            if ("REGRADE".equals(resAction)) {
                // Controlled resolution path 1: Trigger regrading
                if (submission != null) {
                    submission.setReviewStatus("PENDING_REVIEW");
                    submission.setPublicationStatus("UNPUBLISHED");
                    submission.setApprovedScore(null);
                    submission.setPublishedScore(null);
                    submission.setScoreVersion(submission.getScoreVersion() != null ? submission.getScoreVersion() + 1 : 1);
                    submissionRepository.save(submission);

                    if (studentQuestionScoreRepository != null && submission.getQuestion() != null && submission.getStudent() != null) {
                        studentQuestionScoreRepository.unpublishQuestionScore(submission.getQuestion().getId(), submission.getStudent().getId());
                    }

                    if (automatedTestRunnerService != null) {
                        try {
                            automatedTestRunnerService.runAndGrade(submission);
                        } catch (Exception ex) {
                            log.error("Failed to rerun automated grading during appeal resolution for submission {}: {}",
                                    submission.getId(), ex.getMessage());
                        }
                    }

                    GradeAuditLog auditLog = GradeAuditLog.builder()
                            .submission(submission)
                            .actor(admin)
                            .actionType("APPEAL_APPROVED_REGRADE")
                            .preRubricScore(preRubricScore)
                            .postRubricScore(preRubricScore)
                            .preOverrideScore(preOverrideScore)
                            .postOverrideScore(preOverrideScore)
                            .preEffectiveScore(preEffectiveScore)
                            .postEffectiveScore(preEffectiveScore)
                            .rationale("Khiếu nại #" + appeal.getId() + " được Admin chấp thuận - Kích hoạt chấm lại: " + dto.getReviewerNotes().trim())
                            .build();
                    gradeAuditLogRepository.save(auditLog);
                }
            } else if ("FORWARD_ACADEMIC_REVIEW".equals(resAction)) {
                // Controlled resolution path 2: Forward to authorized teacher for academic review
                if (submission != null) {
                    submission.setReviewStatus("REVISION_REQUIRED");
                    submission.setPublicationStatus("UNPUBLISHED");
                    submission.setPublishedScore(null);
                    submission.setReviewNotes("Chuyển tiếp từ Hội đồng Khảo thí / Admin: " + dto.getReviewerNotes().trim());
                    submission.setScoreVersion(submission.getScoreVersion() != null ? submission.getScoreVersion() + 1 : 1);
                    submissionRepository.save(submission);

                    if (studentQuestionScoreRepository != null && submission.getQuestion() != null && submission.getStudent() != null) {
                        studentQuestionScoreRepository.unpublishQuestionScore(submission.getQuestion().getId(), submission.getStudent().getId());
                    }

                    GradeAuditLog auditLog = GradeAuditLog.builder()
                            .submission(submission)
                            .actor(admin)
                            .actionType("APPEAL_FORWARDED_ACADEMIC_REVIEW")
                            .preRubricScore(preRubricScore)
                            .postRubricScore(preRubricScore)
                            .preOverrideScore(preOverrideScore)
                            .postOverrideScore(preOverrideScore)
                            .preEffectiveScore(preEffectiveScore)
                            .postEffectiveScore(preEffectiveScore)
                            .rationale("Khiếu nại #" + appeal.getId() + " được Admin chuyển tiếp cho Giảng viên xem xét lại học thuật: " + dto.getReviewerNotes().trim())
                            .build();
                    gradeAuditLogRepository.save(auditLog);
                }
            } else if (dto.getAdjustedScore() != null) {
                // Controlled resolution path 3: Authorized score adjustment
                BigDecimal newScore = dto.getAdjustedScore();
                if (newScore.compareTo(BigDecimal.ZERO) < 0 || newScore.compareTo(BigDecimal.valueOf(10)) > 0) {
                    throw new IllegalArgumentException("Điểm điều chỉnh phải nằm trong khoảng từ 0 đến 10.");
                }

                if (submission != null) {
                    submission.setOverrideScore(newScore);
                    submission.setStatus("GRADED");
                    submission.setEvaluatedAt(LocalDateTime.now());
                    // Business rule: Require authorized teacher approval before republishing any adjusted score
                    submission.setReviewStatus("PENDING_REVIEW");
                    submission.setPublicationStatus("UNPUBLISHED");
                    submission.setApprovedScore(null);
                    submission.setPublishedScore(null);
                    submission.setScoreVersion(submission.getScoreVersion() != null ? submission.getScoreVersion() + 1 : 1);
                    submissionRepository.save(submission);

                    if (studentQuestionScoreRepository != null && submission.getQuestion() != null && submission.getStudent() != null) {
                        studentQuestionScoreRepository.setQuestionOverride(
                                submission.getQuestion().getId(),
                                submission.getStudent().getId(),
                                newScore,
                                "Admin Appeal Resolution #" + appeal.getId() + ": " + dto.getReviewerNotes().trim(),
                                admin != null ? admin.getId() : "ADMIN"
                        );
                        studentQuestionScoreRepository.unpublishQuestionScore(submission.getQuestion().getId(), submission.getStudent().getId());
                    }

                    GradeAuditLog auditLog = GradeAuditLog.builder()
                            .submission(submission)
                            .actor(admin)
                            .actionType("APPEAL_APPROVED")
                            .preRubricScore(preRubricScore)
                            .postRubricScore(preRubricScore)
                            .preOverrideScore(preOverrideScore)
                            .postOverrideScore(newScore)
                            .preEffectiveScore(preEffectiveScore)
                            .postEffectiveScore(newScore)
                            .rationale("Khiếu nại #" + appeal.getId() + " được Admin chấp thuận với điểm điều chỉnh: " + dto.getReviewerNotes().trim())
                            .build();
                    gradeAuditLogRepository.save(auditLog);
                }
            } else if (submission != null) {
                // Approved maintaining current score
                GradeAuditLog auditLog = GradeAuditLog.builder()
                        .submission(submission)
                        .actor(admin)
                        .actionType("APPEAL_APPROVED")
                        .preRubricScore(preRubricScore)
                        .postRubricScore(preRubricScore)
                        .preOverrideScore(preOverrideScore)
                        .postOverrideScore(preOverrideScore)
                        .preEffectiveScore(preEffectiveScore)
                        .postEffectiveScore(preEffectiveScore)
                        .rationale("Khiếu nại #" + appeal.getId() + " được Admin chấp thuận (giữ nguyên điểm): " + dto.getReviewerNotes().trim())
                        .build();
                gradeAuditLogRepository.save(auditLog);
            }
        } else {
            appeal.setStatus("REJECTED");

            if (submission != null) {
                GradeAuditLog auditLog = GradeAuditLog.builder()
                        .submission(submission)
                        .actor(admin)
                        .actionType("APPEAL_REJECTED")
                        .preRubricScore(preRubricScore)
                        .postRubricScore(preRubricScore)
                        .preOverrideScore(preOverrideScore)
                        .postOverrideScore(preOverrideScore)
                        .preEffectiveScore(preEffectiveScore)
                        .postEffectiveScore(preEffectiveScore)
                        .rationale("Khiếu nại #" + appeal.getId() + " bị Admin từ chối: " + dto.getReviewerNotes().trim())
                        .build();
                gradeAuditLogRepository.save(auditLog);
            }
        }

        Appeal saved = appealRepository.save(appeal);
        if (webSocketBroadcaster != null && saved.getStudent() != null) {
            try {
                webSocketBroadcaster.broadcastAppealStatus(
                        String.valueOf(saved.getId()),
                        saved.getStudent().getId(),
                        saved.getStatus(),
                        saved.getReviewerNotes()
                );
            } catch (Exception wsEx) {
                log.debug("WebSocket broadcast error for appeal: {}", wsEx.getMessage());
            }
        }
        log.info("Admin {} resolved appeal {} with decision {}", actorName, appeal.getId(), decision);
        return toDto(saved);
    }

    @Transactional
    public byte[] getAppealSubmissionArchive(String opaqueId, User admin) {
        Long id = OpaqueIdEncoder.decode(opaqueId);
        if (id == null) {
            throw new ResourceNotFoundException("Appeal not found");
        }

        Appeal appeal = appealRepository.findByIdForUpdate(id)
                .or(() -> appealRepository.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException("Appeal not found"));

        Submission submission = appeal.getSubmission();
        if (submission == null) {
            throw new ResourceNotFoundException("Submission not found for this appeal");
        }

        byte[] zipBytes = null;
        if (submissionStorageService.hasZip(submission.getId())) {
            zipBytes = submissionStorageService.getZipBytes(submission.getId());
        } else if (submission.getSourceCode() != null && !submission.getSourceCode().isBlank()) {
            String entryName = submission.getOriginalFilename() != null && !submission.getOriginalFilename().isBlank()
                    ? submission.getOriginalFilename()
                    : "Main.java";
            zipBytes = submissionStorageService.createZipFromSourceCode(entryName, submission.getSourceCode());
        }

        if (zipBytes == null || zipBytes.length == 0) {
            throw new ResourceNotFoundException("Không tìm thấy tệp mã nguồn hoặc file nộp của bài thi này.");
        }

        // Record audit trail for admin archive download
        BigDecimal currentEffective = submission.getOverrideScore() != null ? submission.getOverrideScore() 
                : (submission.getTotalScore() != null ? submission.getTotalScore() : submission.getAutoScore());
        String actorName = admin != null ? (admin.getFullName() != null ? admin.getFullName() : admin.getEmail()) : "Admin";

        GradeAuditLog auditLog = GradeAuditLog.builder()
                .submission(submission)
                .actor(admin)
                .actionType("APPEAL_ARCHIVE_DOWNLOADED")
                .preRubricScore(submission.getTotalScore())
                .postRubricScore(submission.getTotalScore())
                .preOverrideScore(submission.getOverrideScore())
                .postOverrideScore(submission.getOverrideScore())
                .preEffectiveScore(currentEffective)
                .postEffectiveScore(currentEffective)
                .rationale("Quản trị viên " + actorName + " đã tải file ZIP bài nộp để thẩm định khiếu nại #" + appeal.getId())
                .build();
        gradeAuditLogRepository.save(auditLog);

        log.info("Admin {} downloaded submission archive {} for appeal {}", 
                actorName, submission.getId(), appeal.getId());

        return zipBytes;
    }

    @Transactional(readOnly = true)
    public String getSubmissionArchiveFilename(String opaqueId) {
        Long id = OpaqueIdEncoder.decode(opaqueId);
        if (id == null) {
            return "submission.zip";
        }
        Appeal appeal = appealRepository.findById(id).orElse(null);
        if (appeal != null && appeal.getSubmission() != null) {
            Submission sub = appeal.getSubmission();
            if (sub.getOriginalFilename() != null && !sub.getOriginalFilename().isBlank()) {
                String fn = sub.getOriginalFilename().trim();
                return fn.toLowerCase().endsWith(".zip") ? fn : fn + ".zip";
            }
            return "submission-" + sub.getId() + ".zip";
        }
        return "submission.zip";
    }

    private AppealResponseDto toDto(Appeal appeal) {
        Submission sub = appeal.getSubmission();
        BigDecimal originalScore = sub != null ? sub.getTotalScore() : null;
        BigDecimal updatedScore = sub != null ? sub.getOverrideScore() : null;
        BigDecimal autoScore = sub != null ? sub.getAutoScore() : null;
        BigDecimal currentScore = updatedScore != null ? updatedScore : (originalScore != null ? originalScore : autoScore);

        String reviewerName = null;
        String reviewerEmail = null;
        if (sub != null && !"PENDING".equalsIgnoreCase(appeal.getStatus())) {
            List<GradeAuditLog> logs = gradeAuditLogRepository.findBySubmission_IdOrderByCreatedAtDesc(sub.getId());
            if (!logs.isEmpty()) {
                User actor = logs.get(0).getActor();
                if (actor != null) {
                    reviewerName = actor.getFullName();
                    reviewerEmail = actor.getEmail();
                }
            }
        }

        Long fileSize = null;
        boolean hasArchive = false;
        if (sub != null) {
            hasArchive = submissionStorageService.hasZip(sub.getId())
                    || (sub.getSourceCode() != null && !sub.getSourceCode().isBlank());
            fileSize = submissionStorageService.getZipSizeBytes(sub.getId());
            if (fileSize == null && sub.getSourceCode() != null) {
                fileSize = (long) sub.getSourceCode().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            }
        }

        String adminName = appeal.getAdmin() != null ? appeal.getAdmin().getFullName() : null;

        return AppealResponseDto.builder()
                .id(OpaqueIdEncoder.encode(appeal.getId()))
                .submissionId(sub != null ? sub.getId() : null)
                .examName(sub != null && sub.getExam() != null ? sub.getExam().getTitle() : null)
                .questionId(sub != null && sub.getQuestion() != null ? sub.getQuestion().getId() : null)
                .questionTitle(sub != null && sub.getQuestion() != null ? sub.getQuestion().getTitle() : null)
                .status(appeal.getStatus())
                .basis(appeal.getBasis())
                .explanation(appeal.getReason())
                .studentName(appeal.getStudent() != null ? appeal.getStudent().getFullName() : null)
                .studentEmail(appeal.getStudent() != null ? appeal.getStudent().getEmail() : null)
                .reviewerNotes(appeal.getReviewerNotes())
                .currentScore(currentScore)
                .originalScore(originalScore)
                .updatedScore(updatedScore)
                .autoScore(autoScore)
                .publishedScore(sub != null ? sub.getPublishedScore() : null)
                .publicationStatus(sub != null ? sub.getPublicationStatus() : null)
                .reviewStatus(sub != null ? sub.getReviewStatus() : null)
                .sourceCode(sub != null ? sub.getSourceCode() : null)
                .testCasesPassed(sub != null ? sub.getTestCasesPassed() : null)
                .testCasesTotal(sub != null ? sub.getTestCasesTotal() : null)
                .submissionStatus(sub != null ? sub.getStatus() : null)
                .originalFilename(sub != null ? sub.getOriginalFilename() : null)
                .submissionType(sub != null ? sub.getSubmissionType() : null)
                .fileSize(fileSize)
                .hasArchive(hasArchive)
                .executionStatus(sub != null ? sub.getExecutionStatus() : null)
                .compileOutput(sub != null ? sub.getCompileOutput() : null)
                .submittedAt(sub != null ? (sub.getEvaluatedAt() != null ? sub.getEvaluatedAt() : sub.getCreatedAt()) : null)
                .reviewerName(reviewerName)
                .reviewerEmail(reviewerEmail)
                .adminName(adminName)
                .resolutionAction(appeal.getResolutionAction())
                .createdAt(appeal.getCreatedAt())
                .resolvedAt(appeal.getResolvedAt())
                .build();
    }
}
