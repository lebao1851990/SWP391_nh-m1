package com.codeassess.exam.controller;

import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.exam.service.StudentExamService;
import com.codeassess.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/student/exams")
@RequiredArgsConstructor
public class StudentExamController {

    private final StudentExamService studentExamService;

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping
    public List<ExamResponse> getMyExams(@AuthenticationPrincipal User currentUser) {
        return studentExamService.getStudentExams(currentUser.getId());
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/{id}")
    public ExamResponse getExamById(@PathVariable String id, @AuthenticationPrincipal User currentUser) {
        return studentExamService.getStudentExamById(id, currentUser.getId());
    }
}
