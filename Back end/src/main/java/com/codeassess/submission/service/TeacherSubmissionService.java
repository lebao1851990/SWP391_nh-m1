package com.codeassess.submission.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.submission.dto.response.SubmissionDetailResponse;
import com.codeassess.submission.dto.response.TeacherSubmissionListResponse;
import com.codeassess.submission.entity.Submission;
import com.codeassess.submission.mapper.SubmissionMapper;
import com.codeassess.submission.repository.SubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TeacherSubmissionService {

    private final SubmissionRepository submissionRepository;
    private final SubmissionMapper submissionMapper;

    public TeacherSubmissionService(SubmissionRepository submissionRepository, SubmissionMapper submissionMapper) {
        this.submissionRepository = submissionRepository;
        this.submissionMapper = submissionMapper;
    }

    @Transactional(readOnly = true)
    public List<TeacherSubmissionListResponse> getTeacherSubmissions(String teacherId) {
        return submissionRepository.findByExam_CourseClass_Teacher_Id(teacherId).stream()
                .map(sub -> TeacherSubmissionListResponse.builder()
                        .id(sub.getId())
                        .studentName(sub.getStudent() != null ? sub.getStudent().getFullName() : null)
                        .studentEmail(sub.getStudent() != null ? sub.getStudent().getEmail() : null)
                        .examTitle(sub.getExam() != null ? sub.getExam().getTitle() : null)
                        .courseCode(sub.getExam() != null && sub.getExam().getCourseClass() != null ? sub.getExam().getCourseClass().getCode() : null)
                        .status(sub.getStatus())
                        .totalScore(sub.getTotalScore())
                        .submittedAt(("COMPLETED".equalsIgnoreCase(sub.getStatus()) || "EVALUATED".equalsIgnoreCase(sub.getStatus())) && sub.getEvaluatedAt() != null
                                ? sub.getEvaluatedAt()
                                : sub.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SubmissionDetailResponse getTeacherSubmissionById(String id, String teacherId) {
        Submission submission = submissionRepository.findByIdAndExam_CourseClass_Teacher_Id(id, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found or access denied"));
        return submissionMapper.toDetailResponse(submission);
    }
}
