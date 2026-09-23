package com.codeassess.exam.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.mapper.ExamMapper;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.classes.repository.ClassStudentRepository;
import com.codeassess.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherExamService {

    private final ExamRepository examRepository;
    private final ExamMapper examMapper;
    private final ClassStudentRepository classStudentRepository;
    private final SubmissionRepository submissionRepository;

    private ExamResponse populateCounts(ExamResponse response) {
        if (response != null && response.getClassId() != null) {
            response.setEnrolledCount((int) classStudentRepository.countByCourseClass_Id(response.getClassId()));
        }
        if (response != null && response.getId() != null) {
            response.setSubmittedCount((int) submissionRepository.countByExam_Id(response.getId()));
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> getTeacherExams(String teacherId) {
        return examRepository.findByCourseClass_Teacher_Id(teacherId).stream()
                .map(examMapper::toResponse)
                .map(this::populateCounts)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExamResponse getTeacherExamById(String id, String teacherId) {
        Exam exam = examRepository.findByIdAndCourseClass_Teacher_Id(id, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found or access denied"));
        return populateCounts(examMapper.toResponse(exam));
    }

    @Transactional(readOnly = true)
    public List<com.codeassess.submission.dto.response.TeacherSubmissionListResponse> getExamSubmissions(String examId, String teacherId) {
        Exam exam = examRepository.findByIdAndCourseClass_Teacher_Id(examId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found or access denied"));
        
        return submissionRepository.findByExam_Id(examId).stream()
                .map(sub -> com.codeassess.submission.dto.response.TeacherSubmissionListResponse.builder()
                        .id(sub.getId())
                        .studentName(sub.getStudent().getFullName())
                        .studentEmail(sub.getStudent().getEmail())
                        .examTitle(exam.getTitle())
                        .courseCode(exam.getCourseClass().getCode())
                        .status(sub.getStatus())
                        .totalScore(sub.getTotalScore())
                        .submittedAt(sub.getEvaluatedAt() != null ? sub.getEvaluatedAt() : sub.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
