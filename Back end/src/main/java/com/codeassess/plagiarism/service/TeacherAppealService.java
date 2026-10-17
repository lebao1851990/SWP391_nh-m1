package com.codeassess.plagiarism.service;

import com.codeassess.common.exception.ConflictException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.plagiarism.dto.AppealResolveRequestDto;
import com.codeassess.plagiarism.dto.AppealResponseDto;
import com.codeassess.plagiarism.entity.Appeal;
import com.codeassess.plagiarism.repository.AppealRepository;
import com.codeassess.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherAppealService {

    private final AppealRepository appealRepository;

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

    @Transactional
    public AppealResponseDto resolveAppeal(String opaqueId, AppealResolveRequestDto dto, User teacher) {
        Long id = OpaqueIdEncoder.decode(opaqueId);
        if (id == null) {
            throw new ResourceNotFoundException("Appeal not found");
        }

        Appeal appeal = appealRepository.findByIdAndTeacherId(id, teacher.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Appeal not found"));

        // Guard: Prevent re-resolution or illegal modifications after resolution
        if (!"PENDING".equalsIgnoreCase(appeal.getStatus())) {
            throw new ConflictException("Appeal has already been resolved and cannot be modified.");
        }

        appeal.setStatus(dto.getDecision().toUpperCase());
        appeal.setReviewerNotes(dto.getReviewerNotes());
        appeal.setResolvedAt(LocalDateTime.now());

        Appeal saved = appealRepository.save(appeal);
        return toDto(saved);
    }

    private AppealResponseDto toDto(Appeal appeal) {
        return AppealResponseDto.builder()
                .id(OpaqueIdEncoder.encode(appeal.getId()))
                .submissionId(appeal.getSubmission().getId())
                .examName(appeal.getSubmission().getExam().getTitle())
                .status(appeal.getStatus())
                .basis(appeal.getBasis())
                .explanation(appeal.getReason())
                .studentName(appeal.getStudent() != null ? appeal.getStudent().getFullName() : null)
                .studentEmail(appeal.getStudent() != null ? appeal.getStudent().getEmail() : null)
                .reviewerNotes(appeal.getReviewerNotes())
                .createdAt(appeal.getCreatedAt())
                .resolvedAt(appeal.getResolvedAt())
                .build();
    }
}
