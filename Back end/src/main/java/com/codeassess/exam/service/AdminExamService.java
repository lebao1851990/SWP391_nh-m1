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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
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
        List<String> targetIds = new ArrayList<>();
        if (dto.getClassIds() != null && !dto.getClassIds().isEmpty()) {
            targetIds.addAll(dto.getClassIds());
        } else if (dto.getClassId() != null && !dto.getClassId().isBlank()) {
            targetIds.add(dto.getClassId());
        }

        List<String> distinctIds = targetIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        if (distinctIds.isEmpty()) {
            throw new IllegalArgumentException("At least one class must be selected for the exam");
        }

        List<CourseClass> classes = courseClassRepository.findAllById(distinctIds);
        if (classes.size() != distinctIds.size()) {
            throw new ResourceNotFoundException("One or more classes not found");
        }

        Exam newExam = Exam.builder()
                .title(dto.getTitle())
                .courseClass(classes.get(0))
                .classes(new HashSet<>(classes))
                .examDate(dto.getDate())
                .duration(dto.getDurationMinutes())
                .status("upcoming")
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
        
        if (dto.getClassIds() != null || dto.getClassId() != null) {
            List<String> targetIds = new ArrayList<>();
            if (dto.getClassIds() != null) {
                targetIds.addAll(dto.getClassIds());
            } else if (dto.getClassId() != null) {
                targetIds.add(dto.getClassId());
            }

            List<String> distinctIds = targetIds.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());

            if (distinctIds.isEmpty()) {
                throw new IllegalArgumentException("Exam must have at least one assigned class");
            }

            List<CourseClass> classes = courseClassRepository.findAllById(distinctIds);
            if (classes.size() != distinctIds.size()) {
                throw new ResourceNotFoundException("One or more classes not found");
            }

            exam.setCourseClass(classes.get(0));
            exam.setClasses(new HashSet<>(classes));
        }
        
        return examMapper.toResponse(examRepository.save(exam));
    }
}
