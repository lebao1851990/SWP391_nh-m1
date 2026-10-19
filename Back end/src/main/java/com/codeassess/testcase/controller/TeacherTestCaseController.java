package com.codeassess.testcase.controller;

import com.codeassess.testcase.dto.request.CreateTestCaseRequest;
import com.codeassess.testcase.dto.request.UpdateTestCaseRequest;
import com.codeassess.testcase.dto.response.TestCaseResponse;
import com.codeassess.testcase.service.TeacherTestCaseService;
import com.codeassess.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teacher/exams/{examId}/test-cases")
@RequiredArgsConstructor
public class TeacherTestCaseController {

    private final TeacherTestCaseService teacherTestCaseService;

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping
    public List<TestCaseResponse> getTestCases(
            @PathVariable String examId,
            @AuthenticationPrincipal User currentUser) {
        return teacherTestCaseService.getTestCases(examId, currentUser.getId());
    }

    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping
    public ResponseEntity<TestCaseResponse> createTestCase(
            @PathVariable String examId,
            @Valid @RequestBody CreateTestCaseRequest request,
            @AuthenticationPrincipal User currentUser) {
        TestCaseResponse response = teacherTestCaseService.createTestCase(examId, currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasRole('TEACHER')")
    @PutMapping("/{id}")
    public TestCaseResponse updateTestCase(
            @PathVariable String examId,
            @PathVariable String id,
            @Valid @RequestBody UpdateTestCaseRequest request,
            @AuthenticationPrincipal User currentUser) {
        return teacherTestCaseService.updateTestCase(examId, id, currentUser.getId(), request);
    }

    @PreAuthorize("hasRole('TEACHER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTestCase(
            @PathVariable String examId,
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {
        teacherTestCaseService.deleteTestCase(examId, id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}
