package com.codeassess.exam.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.dto.response.ExamQuestionResponse;
import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.entity.ExamQuestion;
import com.codeassess.exam.mapper.ExamMapper;
import com.codeassess.exam.repository.ExamQuestionRepository;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.testcase.dto.response.TestCaseResponse;
import com.codeassess.testcase.entity.TestCase;
import com.codeassess.testcase.repository.TestCaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StudentExamService {

    private final ExamRepository examRepository;
    private final ExamMapper examMapper;
    private final com.codeassess.scoring.repository.ExamRubricCriteriaRepository examRubricCriteriaRepository;

    @Autowired(required = false)
    private ExamQuestionRepository examQuestionRepository;

    @Autowired(required = false)
    private TestCaseRepository testCaseRepository;

    @Autowired
    public StudentExamService(
            ExamRepository examRepository,
            ExamMapper examMapper,
            @Autowired(required = false) com.codeassess.scoring.repository.ExamRubricCriteriaRepository examRubricCriteriaRepository,
            @Autowired(required = false) ExamQuestionRepository examQuestionRepository,
            @Autowired(required = false) TestCaseRepository testCaseRepository) {
        this.examRepository = examRepository;
        this.examMapper = examMapper;
        this.examRubricCriteriaRepository = examRubricCriteriaRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.testCaseRepository = testCaseRepository;
    }

    public StudentExamService(ExamRepository examRepository, ExamMapper examMapper) {
        this(examRepository, examMapper, null, null, null);
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
        if ("upcoming".equalsIgnoreCase(response.getStatus())) {
            response.setQuestions(Collections.emptyList());
            response.setTotalQuestions(0);
            response.setProblemStatement(null);
            return response;
        }
        if (examRubricCriteriaRepository != null && response != null) {
            var criteria = examRubricCriteriaRepository.findByExam_IdOrderByDisplayOrderAsc(id).stream()
                    .map(c -> new com.codeassess.ai.dto.AiRubricItemDto(c.getName(), c.getDescription(), c.getMaxScore()))
                    .collect(Collectors.toList());
            response.setRubricCriteria(criteria);
        }
        populateQuestionsAndTestCases(response, exam);
        return response;
    }

    private void populateQuestionsAndTestCases(ExamResponse response, Exam exam) {
        if (response == null || exam == null) return;
        List<ExamQuestion> qList = (examQuestionRepository != null)
                ? examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(exam.getId())
                : Collections.emptyList();

        if (qList.isEmpty()) {
            List<TestCase> tcs = (testCaseRepository != null)
                    ? testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(exam.getId())
                    : Collections.emptyList();
            ExamQuestionResponse defaultQ = ExamQuestionResponse.builder()
                    .id(exam.getId())
                    .examId(exam.getId())
                    .title(exam.getTitle())
                    .description(exam.getProblemStatement())
                    .maxScore(BigDecimal.valueOf(10.00))
                    .displayOrder(0)
                    .status(exam.getStatus() != null ? exam.getStatus().toUpperCase() : "PUBLISHED")
                    .createdAt(exam.getCreatedAt())
                    .testCases(mapStudentTestCases(tcs))
                    .build();
            response.setQuestions(List.of(defaultQ));
            response.setTotalQuestions(1);
            response.setTotalAllocatedPoints(BigDecimal.valueOf(10.00));
            return;
        }

        List<ExamQuestionResponse> qDtos = new ArrayList<>();
        BigDecimal sumAllocated = BigDecimal.ZERO;
        for (ExamQuestion eq : qList) {
            List<TestCase> tcs = (testCaseRepository != null)
                    ? testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc(eq.getId())
                    : Collections.emptyList();
            ExamQuestionResponse qDto = ExamQuestionResponse.builder()
                    .id(eq.getId())
                    .examId(exam.getId())
                    .title(eq.getTitle())
                    .description(eq.getDescription())
                    .maxScore(eq.getMaxScore() != null ? eq.getMaxScore() : BigDecimal.valueOf(10.00))
                    .displayOrder(eq.getDisplayOrder())
                    .status(eq.getStatus())
                    .createdAt(eq.getCreatedAt())
                    .testCases(mapStudentTestCases(tcs))
                    .build();
            qDtos.add(qDto);
            if (eq.getMaxScore() != null) {
                sumAllocated = sumAllocated.add(eq.getMaxScore());
            }
        }
        response.setQuestions(qDtos);
        response.setTotalQuestions(qDtos.size());
        response.setTotalAllocatedPoints(sumAllocated.setScale(2, RoundingMode.HALF_UP));
    }

    private List<TestCaseResponse> mapStudentTestCases(List<TestCase> tcs) {
        if (tcs == null || tcs.isEmpty()) return Collections.emptyList();
        return tcs.stream().map(tc -> {
            boolean isHidden = Boolean.TRUE.equals(tc.getIsHidden());
            return TestCaseResponse.builder()
                    .id(tc.getId())
                    .examId(tc.getExam() != null ? tc.getExam().getId() : null)
                    .questionId(tc.getQuestion() != null ? tc.getQuestion().getId() : null)
                    .name(tc.getName())
                    .description(tc.getDescription())
                    .inputData(isHidden ? "[BẢO MẬT: TEST CASE ẨN]" : tc.getInputData())
                    .expectedOutput(isHidden ? "[BẢO MẬT: TEST CASE ẨN]" : tc.getExpectedOutput())
                    .points(tc.getPoints())
                    .isHidden(isHidden)
                    .displayOrder(tc.getDisplayOrder())
                    .createdAt(tc.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }
}
