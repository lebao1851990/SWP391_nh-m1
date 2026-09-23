package com.codeassess.exam.controller;

import com.codeassess.exam.dto.response.ExamResponse;
import com.codeassess.exam.service.TeacherExamService;
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
@RequestMapping("/api/teacher/exams")
@RequiredArgsConstructor
public class TeacherExamController {

    private final TeacherExamService teacherExamService;

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping
    public List<ExamResponse> getMyExams(@AuthenticationPrincipal User currentUser) {
        return teacherExamService.getTeacherExams(currentUser.getId());
    }

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/{id}")
    public ExamResponse getExamById(@PathVariable String id, @AuthenticationPrincipal User currentUser) {
        return teacherExamService.getTeacherExamById(id, currentUser.getId());
    }

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/{id}/submissions")
    public List<com.codeassess.submission.dto.response.TeacherSubmissionListResponse> getExamSubmissions(
            @PathVariable String id, 
            @AuthenticationPrincipal User currentUser) {
        return teacherExamService.getExamSubmissions(id, currentUser.getId());
    }
}
