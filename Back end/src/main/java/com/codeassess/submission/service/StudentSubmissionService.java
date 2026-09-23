package com.codeassess.submission.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.dto.response.SubmissionListResponse;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.mapper.SubmissionMapper;
import com.codeassess.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.exam.entity.Exam;
import com.codeassess.submission.dto.request.SubmissionCreateRequest;
import com.codeassess.submission.event.SubmissionCreatedEvent;
import com.codeassess.user.repository.UserRepository;
import com.codeassess.user.entity.User;
import org.springframework.context.ApplicationEventPublisher;

@Service
@RequiredArgsConstructor
public class StudentSubmissionService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionMapper submissionMapper;
    private final ExamRepository examRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<SubmissionListResponse> getStudentSubmissions(String studentId) {
        return submissionRepository.findByStudent_Id(studentId).stream()
                .map(submissionMapper::toListResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SubmissionDetailResponse getStudentSubmissionById(String id, String studentId) {
        Submission submission = submissionRepository.findByIdAndStudent_Id(id, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found or access denied"));
        return submissionMapper.toDetailResponse(submission);
    }

    @Transactional
    public SubmissionDetailResponse submitCode(String examId, String studentId, SubmissionCreateRequest request) {
        Exam exam = examRepository.findByIdAndStudentId(examId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found or you are not enrolled in this class"));

        if (exam.getStatus() == null || !exam.getStatus().equalsIgnoreCase("active")) {
            throw new IllegalStateException("Exam is not currently active for submissions");
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));

        Submission submission = Submission.builder()
                .exam(exam)
                .student(student)
                .sourceCode(request.getSourceCode())
                .status("PENDING")
                .build();

        submission = submissionRepository.save(submission);

        // Publish event for AST processing
        eventPublisher.publishEvent(new SubmissionCreatedEvent(this, exam.getId(), submission.getId()));

        return submissionMapper.toDetailResponse(submission);
    }
}
