package com.codeassess.plagiarism.service;

import com.codeassess.common.exception.ConflictException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.plagiarism.dto.AppealResolveRequestDto;
import com.codeassess.plagiarism.dto.AppealResponseDto;
import com.codeassess.plagiarism.entity.Appeal;
import com.codeassess.plagiarism.repository.AppealRepository;
import com.codeassess.scoring.entity.GradeAuditLog;
import com.codeassess.scoring.repository.GradeAuditLogRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.submission.service.SubmissionStorageService;
import com.codeassess.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public AppealResponseDto resolveAppeal(String opaqueId, AppealResolveRequestDto dto, User admin) {
        Long id = OpaqueIdEncoder.decode(opaqueId);
        if (id == null) {
            throw new ResourceNotFoundException("Appeal not found");
        }

        Appeal appeal = appealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appeal not found"));

        // Guard: Prevent re-resolution or illegal modifications after resolution
        if (!"PENDING".equalsIgnoreCase(appeal.getStatus())) {
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

        if ("APPROVED".equals(decision)) {
            appeal.setStatus("APPROVED");

            if (dto.getAdjustedScore() != null) {
                BigDecimal newScore = dto.getAdjustedScore();
                if (newScore.compareTo(BigDecimal.ZERO) < 0 || newScore.compareTo(BigDecimal.valueOf(10)) > 0) {
                    throw new IllegalArgumentException("Điểm điều chỉnh phải nằm trong khoảng từ 0 đến 10.");
                }

                if (submission != null) {
                    submission.setOverrideScore(newScore);
                    submission.setEvaluatedAt(LocalDateTime.now());
                    if (submission.getStatus() == null || "PENDING".equalsIgnoreCase(submission.getStatus())) {
                        submission.setStatus("GRADED");
                    }
                    submissionRepository.save(submission);

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
                            .rationale("Khiếu nại #" + appeal.getId() + " được Admin chấp thuận: " + dto.getReviewerNotes().trim())
                            .build();
                    gradeAuditLogRepository.save(auditLog);
                }
            } else if (submission != null) {
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

        appeal.setReviewerNotes(dto.getReviewerNotes().trim());
        appeal.setResolvedAt(LocalDateTime.now());

        Appeal saved = appealRepository.save(appeal);
        return toDto(saved);
    }

    @Transactional
    public byte[] getAppealSubmissionArchive(String opaqueId, User admin) {
        Long id = OpaqueIdEncoder.decode(opaqueId);
        if (id == null) {
            throw new ResourceNotFoundException("Appeal not found");
        }

        Appeal appeal = appealRepository.findById(id)
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

        return AppealResponseDto.builder()
                .id(OpaqueIdEncoder.encode(appeal.getId()))
                .submissionId(sub != null ? sub.getId() : null)
                .examName(sub != null && sub.getExam() != null ? sub.getExam().getTitle() : null)
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
                .createdAt(appeal.getCreatedAt())
                .resolvedAt(appeal.getResolvedAt())
                .build();
    }
}
