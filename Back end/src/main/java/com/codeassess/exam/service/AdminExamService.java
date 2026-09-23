package com.codeassess.exam.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.exam.dto.request.CreateExamDto;
import com.codeassess.exam.dto.request.UpdateExamDto;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.mapper.ExamMapper;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.classes.entity.CourseClass;
import com.codeassess.classes.repository.CourseClassRepository;
import com.codeassess.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminExamService {

    private final ExamRepository examRepository;
    private final ExamMapper examMapper;
    private final CourseClassRepository courseClassRepository;

    @Transactional(readOnly = true)
    public List<ExamResponse> getAllExams() {
        return examRepository.findAll().stream()
                .map(examMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExamResponse getExamById(String id) {
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found with id: " + id));
        return examMapper.toResponse(exam);
    }

    @Transactional
    public ExamResponse createExam(CreateExamDto dto) {
        CourseClass courseClass = courseClassRepository.findById(dto.getClassId())
                .orElseThrow(() -> new ResourceNotFoundException("Class not found"));

        Exam newExam = Exam.builder()
                .title(dto.getTitle())
                .courseClass(courseClass)
                .examDate(dto.getDate())
                .duration(dto.getDurationMinutes())
                .status("upcoming") // Default status for new exams
                .build();
        
        return examMapper.toResponse(examRepository.save(newExam));
    }

    @Transactional
    public ExamResponse updateExam(String id, UpdateExamDto dto) {
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found with id: " + id));
        
        if (dto.getTitle() != null) exam.setTitle(dto.getTitle());
        if (dto.getDate() != null) exam.setExamDate(dto.getDate());
        if (dto.getDurationMinutes() != null) exam.setDuration(dto.getDurationMinutes());
        if (dto.getStatus() != null) exam.setStatus(dto.getStatus().toLowerCase());
        
        if (dto.getClassId() != null) {
            CourseClass courseClass = courseClassRepository.findById(dto.getClassId())
                    .orElseThrow(() -> new ResourceNotFoundException("Class not found"));
            exam.setCourseClass(courseClass);
        }
        
        return examMapper.toResponse(examRepository.save(exam));
    }
}
