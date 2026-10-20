package com.codeassess.testcase.service;

import com.codeassess.common.exception.ResourceNotFoundException;
import com.codeassess.exam.entity.Exam;
import com.codeassess.exam.repository.ExamRepository;
import com.codeassess.submission.repository.SubmissionRepository;
import com.codeassess.testcase.dto.request.CreateTestCaseRequest;
import com.codeassess.testcase.dto.request.UpdateTestCaseRequest;
import com.codeassess.testcase.dto.response.TestCaseResponse;
import com.codeassess.testcase.entity.TestCase;
import com.codeassess.testcase.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeacherTestCaseService {

    private final TestCaseRepository testCaseRepository;
    private final ExamRepository examRepository;
    private final SubmissionRepository submissionRepository;

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
        if (points.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Điểm test case không được âm");
        }

        TestCase testCase = TestCase.builder()
                .exam(exam)
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
        if (points != null && points.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Điểm test case không được âm");
        }

        testCase.setName(request.getName().trim());
        testCase.setDescription(request.getDescription());
        testCase.setInputData(request.getInputData());
        testCase.setExpectedOutput(request.getExpectedOutput());
        testCase.setPoints(points);
        testCase.setIsHidden(Boolean.TRUE.equals(request.getIsHidden()));
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
                .examId(tc.getExam().getId())
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
