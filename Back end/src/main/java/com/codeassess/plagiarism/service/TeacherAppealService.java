package com.codeassess.plagiarism.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.plagiarism.dto.AppealResolveRequestDto;
import com.codeassess.plagiarism.dto.AppealResponseDto;
import com.codeassess.plagiarism.entity.Appeal;
import com.codeassess.plagiarism.repository.AppealRepository;
import com.codeassess.scoring.entity.GradeAuditLog;
import com.codeassess.scoring.repository.GradeAuditLogRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherAppealService {

    private final AppealRepository appealRepository;
    private final GradeAuditLogRepository gradeAuditLogRepository;

    @Transactional(readOnly = true)
    public List<AppealResponseDto> getTeacherAppeals(User teacher) {
        return appealRepository.findByTeacherId(teacher.getId()).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AppealResponseDto getTeacherAppeal(String opaqueId, User teacher) {
        Long id = OpaqueIdEncoder.decode(opaqueId);
        if (id == null) {
            throw new ResourceNotFoundException("Appeal not found");
        }

        Appeal appeal = appealRepository.findByIdAndTeacherId(id, teacher.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Appeal not found"));

        return toDto(appeal);
    }

    /**
     * Deprecated: Teachers cannot resolve appeals. Throws AccessDeniedException.
     */
    @Transactional
    public AppealResponseDto resolveAppeal(String opaqueId, AppealResolveRequestDto dto, User teacher) {
        throw new AccessDeniedException("Giảng viên không có quyền phê duyệt hoặc từ chối khiếu nại. Thao tác này thuộc thẩm quyền của Quản trị viên (Admin).");
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
