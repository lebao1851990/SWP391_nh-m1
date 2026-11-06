package com.codeassess.testcase.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.entity.ExamQuestion;
import com.codeassess.exam.repository.ExamQuestionRepository;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.testcase.dto.request.CreateTestCaseRequest;
import com.codeassess.testcase.dto.request.UpdateTestCaseRequest;
import com.codeassess.testcase.dto.response.TestCaseResponse;
import com.codeassess.testcase.entity.TestCase;
import com.codeassess.testcase.repository.TestCaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TeacherTestCaseService {

    private final TestCaseRepository testCaseRepository;
    private final ExamRepository examRepository;
    private final SubmissionRepository submissionRepository;
    private final ExamQuestionRepository examQuestionRepository;

    @Autowired
    public TeacherTestCaseService(
            TestCaseRepository testCaseRepository,
            ExamRepository examRepository,
            SubmissionRepository submissionRepository,
            @Autowired(required = false) ExamQuestionRepository examQuestionRepository) {
        this.testCaseRepository = testCaseRepository;
        this.examRepository = examRepository;
        this.submissionRepository = submissionRepository;
        this.examQuestionRepository = examQuestionRepository;
    }

    public TeacherTestCaseService(
            TestCaseRepository testCaseRepository,
            ExamRepository examRepository,
            SubmissionRepository submissionRepository) {
        this(testCaseRepository, examRepository, submissionRepository, null);
    }

    @Transactional(readOnly = true)
    public List<TestCaseResponse> getTestCases(String examId, String teacherId) {
        verifyTeacherOwnsExam(examId, teacherId);

        return testCaseRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(examId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public TestCaseResponse createTestCase(String examId, String teacherId, CreateTestCaseRequest request) {
        Exam exam = verifyTeacherOwnsExam(examId, teacherId);

        long subCount = submissionRepository.countByExam_Id(examId);
        if (subCount > 0) {
            throw new IllegalStateException("Không thể thêm test case mới vì bài thi đã có sinh viên nộp bài.");
        }

        BigDecimal points = request.getPoints() != null ? request.getPoints() : BigDecimal.valueOf(10.00);
        if (points.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Điểm test case không được âm và phải lớn hơn 0");
        }
        if (points.scale() > 2) {
            throw new IllegalArgumentException("Điểm test case không được vượt quá 2 chữ số thập phân (chuẩn DECIMAL(5,2))");
        }
        if (points.compareTo(BigDecimal.valueOf(999.99)) > 0) {
            throw new IllegalArgumentException("Điểm test case vượt quá giới hạn tối đa cho phép");
        }

        if (request.getInputData() != null && !request.getInputData().isEmpty() && request.getInputData().trim().isEmpty()) {
            throw new IllegalArgumentException("Dữ liệu đầu vào (Input Data) không được chỉ chứa ký tự khoảng trắng");
        }
        if (request.getExpectedOutput() != null && !request.getExpectedOutput().isEmpty() && request.getExpectedOutput().trim().isEmpty()) {
            throw new IllegalArgumentException("Kết quả mong đợi (Expected Output) không được chỉ chứa ký tự khoảng trắng");
        }

        ExamQuestion question = null;
        if (request.getQuestionId() != null && !request.getQuestionId().isBlank() && examQuestionRepository != null) {
            question = examQuestionRepository.findByIdAndExam_IdForUpdate(request.getQuestionId().trim(), examId)
                    .orElseThrow(() -> new ResourceNotFoundException("Câu hỏi không tồn tại trong bài thi này"));
        } else if (examQuestionRepository != null) {
            List<ExamQuestion> questions = examQuestionRepository.findByExam_IdOrderByDisplayOrderAscCreatedAtAsc(examId);
            if (!questions.isEmpty()) {
                question = examQuestionRepository.findByIdAndExam_IdForUpdate(questions.get(0).getId(), examId).orElse(questions.get(0));
            }
        }

        if (question != null) {
            BigDecimal questionMaxScore = question.getMaxScore() != null ? question.getMaxScore() : BigDecimal.valueOf(10.00);

            // Rule 1: 0 < testCase.points <= question.maxScore
            if (points.compareTo(questionMaxScore) > 0) {
                throw new IllegalArgumentException(String.format(
                        "Điểm test case (%s) không được vượt quá điểm tối đa của câu hỏi (%s) [QuestionId: %s]",
                        points.setScale(2, RoundingMode.HALF_UP),
                        questionMaxScore.setScale(2, RoundingMode.HALF_UP),
                        question.getId()
                ));
            }

            // Rule 2: SUM(testCase.points) <= question.maxScore
            List<TestCase> existing = testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc(question.getId());
            BigDecimal currentTotal = existing.stream()
                    .map(TestCase::getPoints)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal newTotal = currentTotal.add(points).setScale(2, RoundingMode.HALF_UP);
            if (newTotal.compareTo(questionMaxScore) > 0) {
                BigDecimal remaining = questionMaxScore.subtract(currentTotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                throw new IllegalArgumentException(String.format(
                        "Tổng điểm các test case (%s) vượt quá điểm tối đa của câu hỏi (%s). [QuestionId: %s, Điểm hiện tại: %s, Điểm yêu cầu: %s, Điểm còn lại: %s]",
                        newTotal,
                        questionMaxScore.setScale(2, RoundingMode.HALF_UP),
                        question.getId(),
                        currentTotal,
                        points.setScale(2, RoundingMode.HALF_UP),
                        remaining
                ));
            }
        }

        TestCase testCase = TestCase.builder()
                .exam(exam)
                .question(question)
                .name(request.getName().trim())
                .description(request.getDescription())
                .inputData(request.getInputData())
                .expectedOutput(request.getExpectedOutput())
                .points(points)
                .isHidden(Boolean.TRUE.equals(request.getIsHidden()))
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();

        testCase = testCaseRepository.save(testCase);
        return toResponse(testCase);
    }

    @Transactional
    public TestCaseResponse updateTestCase(String examId, String testCaseId, String teacherId, UpdateTestCaseRequest request) {
        verifyTeacherOwnsExam(examId, teacherId);

        long subCount = submissionRepository.countByExam_Id(examId);
        if (subCount > 0) {
            throw new IllegalStateException("Không thể sửa test case vì bài thi đã có sinh viên nộp bài.");
        }

        TestCase testCase = testCaseRepository.findByIdAndExam_Id(testCaseId, examId)
                .orElseThrow(() -> new ResourceNotFoundException("Test case không tồn tại trong bài thi này"));

        BigDecimal points = request.getPoints() != null ? request.getPoints() : testCase.getPoints();
        if (points != null) {
            if (points.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Điểm test case không được âm và phải lớn hơn 0");
            }
            if (points.scale() > 2) {
                throw new IllegalArgumentException("Điểm test case không được vượt quá 2 chữ số thập phân (chuẩn DECIMAL(5,2))");
            }
            if (points.compareTo(BigDecimal.valueOf(999.99)) > 0) {
                throw new IllegalArgumentException("Điểm test case vượt quá giới hạn tối đa cho phép");
            }
        }

        if (request.getInputData() != null && !request.getInputData().isEmpty() && request.getInputData().trim().isEmpty()) {
            throw new IllegalArgumentException("Dữ liệu đầu vào (Input Data) không được chỉ chứa ký tự khoảng trắng");
        }
        if (request.getExpectedOutput() != null && !request.getExpectedOutput().isEmpty() && request.getExpectedOutput().trim().isEmpty()) {
            throw new IllegalArgumentException("Kết quả mong đợi (Expected Output) không được chỉ chứa ký tự khoảng trắng");
        }

        ExamQuestion currentQuestion = testCase.getQuestion();
        String sourceQuestionId = (currentQuestion != null) ? currentQuestion.getId() : null;
        String targetQuestionId = (request.getQuestionId() != null && !request.getQuestionId().isBlank())
                ? request.getQuestionId().trim()
                : sourceQuestionId;

        if (targetQuestionId != null && examQuestionRepository != null) {
            final ExamQuestion targetQuestion;

            // Scenario A: Question Transfer (source and target are distinct and non-null)
            if (sourceQuestionId != null && !sourceQuestionId.equals(targetQuestionId)) {
                // Lock both questions in deterministic ascending ID order to prevent deadlocks (ABBA deadlock prevention)
                String firstLockId = sourceQuestionId.compareTo(targetQuestionId) < 0 ? sourceQuestionId : targetQuestionId;
                String secondLockId = sourceQuestionId.compareTo(targetQuestionId) < 0 ? targetQuestionId : sourceQuestionId;

                ExamQuestion firstQ = examQuestionRepository.findByIdAndExam_IdForUpdate(firstLockId, examId)
                        .orElseThrow(() -> new ResourceNotFoundException("Câu hỏi không tồn tại trong bài thi này"));
                ExamQuestion secondQ = examQuestionRepository.findByIdAndExam_IdForUpdate(secondLockId, examId)
                        .orElseThrow(() -> new ResourceNotFoundException("Câu hỏi không tồn tại trong bài thi này"));

                targetQuestion = targetQuestionId.equals(firstLockId) ? firstQ : secondQ;
            } else {
                // Scenario B: Same question or initial question assignment
                targetQuestion = examQuestionRepository.findByIdAndExam_IdForUpdate(targetQuestionId, examId)
                        .orElseThrow(() -> new ResourceNotFoundException("Câu hỏi không tồn tại trong bài thi này"));
            }

            BigDecimal questionMaxScore = targetQuestion.getMaxScore() != null ? targetQuestion.getMaxScore() : BigDecimal.valueOf(10.00);

            // Rule 1: 0 < testCase.points <= targetQuestion.maxScore
            if (points != null && points.compareTo(questionMaxScore) > 0) {
                throw new IllegalArgumentException(String.format(
                        "Điểm test case (%s) không được vượt quá điểm tối đa của câu hỏi (%s) [QuestionId: %s]",
                        points.setScale(2, RoundingMode.HALF_UP),
                        questionMaxScore.setScale(2, RoundingMode.HALF_UP),
                        targetQuestion.getId()
                ));
            }

            // Rule 2: SUM(testCase.points) <= targetQuestion.maxScore
            List<TestCase> existing = testCaseRepository.findByQuestion_IdOrderByDisplayOrderAscCreatedAtAsc(targetQuestion.getId());
            BigDecimal currentTotalWithoutThis = existing.stream()
                    .filter(t -> !t.getId().equals(testCaseId))
                    .map(TestCase::getPoints)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal newTotal = currentTotalWithoutThis.add(points != null ? points : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            if (newTotal.compareTo(questionMaxScore) > 0) {
                BigDecimal remaining = questionMaxScore.subtract(currentTotalWithoutThis).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                throw new IllegalArgumentException(String.format(
                        "Tổng điểm các test case (%s) vượt quá điểm tối đa của câu hỏi (%s). [QuestionId: %s, Điểm hiện tại: %s, Điểm yêu cầu: %s, Điểm còn lại: %s]",
                        newTotal,
                        questionMaxScore.setScale(2, RoundingMode.HALF_UP),
                        targetQuestion.getId(),
                        currentTotalWithoutThis,
                        points != null ? points.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                        remaining
                ));
            }
            testCase.setQuestion(targetQuestion);
        }

        if (request.getName() != null) {
            testCase.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            testCase.setDescription(request.getDescription());
        }
        if (request.getInputData() != null) {
            testCase.setInputData(request.getInputData());
        }
        if (request.getExpectedOutput() != null) {
            testCase.setExpectedOutput(request.getExpectedOutput());
        }
        if (points != null) {
            testCase.setPoints(points);
        }
        if (request.getIsHidden() != null) {
            testCase.setIsHidden(request.getIsHidden());
        }
        if (request.getDisplayOrder() != null) {
            testCase.setDisplayOrder(request.getDisplayOrder());
        }

        testCase = testCaseRepository.save(testCase);
        return toResponse(testCase);
    }

    @Transactional
    public void deleteTestCase(String examId, String testCaseId, String teacherId) {
        verifyTeacherOwnsExam(examId, teacherId);

        long subCount = submissionRepository.countByExam_Id(examId);
        if (subCount > 0) {
            throw new IllegalStateException("Không thể xóa test case vì bài thi đã có sinh viên nộp bài.");
        }

        TestCase testCase = testCaseRepository.findByIdAndExam_Id(testCaseId, examId)
                .orElseThrow(() -> new ResourceNotFoundException("Test case không tồn tại trong bài thi này"));

        testCaseRepository.delete(testCase);
    }

    private Exam verifyTeacherOwnsExam(String examId, String teacherId) {
        return examRepository.findByIdAndCourseClass_Teacher_Id(examId, teacherId)
                .orElseThrow(() -> new ResourceNotFoundException("Bài thi không tồn tại hoặc bạn không có quyền truy cập"));
    }

    private TestCaseResponse toResponse(TestCase tc) {
        return TestCaseResponse.builder()
                .id(tc.getId())
                .examId(tc.getExam() != null ? tc.getExam().getId() : null)
                .questionId(tc.getQuestion() != null ? tc.getQuestion().getId() : null)
                .name(tc.getName())
                .description(tc.getDescription())
                .inputData(tc.getInputData())
                .expectedOutput(tc.getExpectedOutput())
                .points(tc.getPoints())
                .isHidden(tc.getIsHidden())
                .displayOrder(tc.getDisplayOrder())
                .createdAt(tc.getCreatedAt())
                .updatedAt(tc.getUpdatedAt())
                .build();
    }
}
