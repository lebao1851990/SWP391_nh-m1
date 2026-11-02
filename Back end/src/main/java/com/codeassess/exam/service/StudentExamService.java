package com.codeassess.exam.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.mapper.ExamMapper;
import com.codeassess.exam.repository.ExamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class StudentExamService {

    private final ExamRepository examRepository;
    private final ExamMapper examMapper;
    private final com.codeassess.scoring.repository.ExamRubricCriteriaRepository examRubricCriteriaRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public StudentExamService(
            ExamRepository examRepository,
            ExamMapper examMapper,
            com.codeassess.scoring.repository.ExamRubricCriteriaRepository examRubricCriteriaRepository) {
        this.examRepository = examRepository;
        this.examMapper = examMapper;
        this.examRubricCriteriaRepository = examRubricCriteriaRepository;
    }

    public StudentExamService(ExamRepository examRepository, ExamMapper examMapper) {
        this(examRepository, examMapper, null);
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> getStudentExams(String studentId) {
        return examRepository.findByStudentId(studentId).stream()
                .map(examMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExamResponse getStudentExamById(String id, String studentId) {
        Exam exam = examRepository.findByIdAndStudentId(id, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found or access denied"));
        ExamResponse response = examMapper.toResponse(exam);
        if (examRubricCriteriaRepository != null && response != null) {
            var criteria = examRubricCriteriaRepository.findByExam_IdOrderByDisplayOrderAsc(id).stream()
                    .map(c -> new com.codeassess.ai.dto.AiRubricItemDto(c.getName(), c.getDescription(), c.getMaxScore()))
                    .collect(Collectors.toList());
            response.setRubricCriteria(criteria);
        }
        return response;
    }
}
