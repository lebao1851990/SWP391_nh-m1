package com.codeassess.plagiarism.service;

import com.codeassess.common.exception.ConflictException;
import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.plagiarism.dto.AppealCreateRequestDto;
import com.codeassess.plagiarism.dto.AppealResponseDto;
import com.codeassess.plagiarism.entity.Appeal;
import com.codeassess.plagiarism.repository.AppealRepository;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentAppealService {

    private final AppealRepository appealRepository;
    private final SubmissionRepository submissionRepository;

    @Transactional
    public AppealResponseDto createAppeal(AppealCreateRequestDto dto, User student) {
        // Fetch submission with pessimistic write lock to prevent concurrent creations
        Submission submission = submissionRepository.findByIdAndStudent_IdForUpdate(dto.getSubmissionId(), student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found"));

        if (appealRepository.existsBySubmission_IdAndStatus(submission.getId(), "PENDING")) {
            throw new ConflictException("A pending appeal already exists for this submission.");
        }

        Appeal appeal = Appeal.builder()
                .submission(submission)
                .student(student)
                .reason(dto.getExplanation()) // Map explanation to reason
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
        return AppealResponseDto.builder()
                .id(OpaqueIdEncoder.encode(appeal.getId()))
                .submissionId(appeal.getSubmission().getId())
                .examName(appeal.getSubmission().getExam().getTitle())
                .status(appeal.getStatus())
                .basis(appeal.getBasis())
                .explanation(appeal.getReason()) // Map reason back to explanation
                .studentName(appeal.getStudent() != null ? appeal.getStudent().getFullName() : null)
                .studentEmail(appeal.getStudent() != null ? appeal.getStudent().getEmail() : null)
                .reviewerNotes(appeal.getReviewerNotes())
                .createdAt(appeal.getCreatedAt())
                .resolvedAt(appeal.getResolvedAt())
                .build();
    }
}
