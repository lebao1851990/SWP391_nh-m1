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
import com.codeassess.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminAppealService {

    private final AppealRepository appealRepository;
    private final SubmissionRepository submissionRepository;
    private final GradeAuditLogRepository gradeAuditLogRepository;

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
                if (newScore.compareTo(BigDecimal.ZERO) < 0 || newScore.compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new IllegalArgumentException("Điểm điều chỉnh phải nằm trong khoảng từ 0 đến 100.");
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
                .reviewerName(reviewerName)
                .reviewerEmail(reviewerEmail)
                .createdAt(appeal.getCreatedAt())
                .resolvedAt(appeal.getResolvedAt())
                .build();
    }
}
