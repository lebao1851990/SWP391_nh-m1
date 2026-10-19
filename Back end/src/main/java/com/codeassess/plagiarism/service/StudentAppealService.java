package com.codeassess.plagiarism.service;

import com.codeassess.common.exception.ConflictException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.plagiarism.dto.AppealCreateRequestDto;
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
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentAppealService {

    private final AppealRepository appealRepository;
    private final SubmissionRepository submissionRepository;
    private final GradeAuditLogRepository gradeAuditLogRepository;

    @Transactional
    public AppealResponseDto createAppeal(AppealCreateRequestDto dto, User student) {
        if (dto.getExplanation() == null || dto.getExplanation().trim().isEmpty()) {
            throw new IllegalArgumentException("Nội dung giải trình không được để trống.");
        }

        // Fetch submission with pessimistic write lock to prevent concurrent creations
        Submission submission = submissionRepository.findByIdAndStudent_IdForUpdate(dto.getSubmissionId(), student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found"));

        if (appealRepository.existsBySubmission_IdAndStatus(submission.getId(), "PENDING")) {
            throw new ConflictException("Đã có một đơn khiếu nại đang chờ xử lý cho bài nộp này.");
        }

        if (submission.getStatus() == null || (!"GRADED".equalsIgnoreCase(submission.getStatus()) && !"COMPLETED".equalsIgnoreCase(submission.getStatus()))) {
            throw new IllegalArgumentException("Chỉ có thể tạo khiếu nại đối với bài nộp đã được chấm điểm.");
        }

        Appeal appeal = Appeal.builder()
                .submission(submission)
                .student(student)
                .reason(dto.getExplanation().trim())
                .basis(dto.getBasis())
                .status("PENDING")
                .build();

        Appeal savedAppeal = appealRepository.save(appeal);

        return toDto(savedAppeal);
    }

    @Transactional(readOnly = true)
    public List<AppealResponseDto> getStudentAppeals(User student) {
        return appealRepository.findByStudent_Id(student.getId()).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AppealResponseDto getStudentAppeal(String opaqueId, User student) {
        Long id = OpaqueIdEncoder.decode(opaqueId);
        if (id == null) {
            throw new ResourceNotFoundException("Appeal not found");
        }
        
        Appeal appeal = appealRepository.findByIdAndStudent_Id(id, student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Appeal not found"));
                
        return toDto(appeal);
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
