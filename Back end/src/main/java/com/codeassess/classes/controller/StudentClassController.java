package com.codeassess.classes.controller;

import com.codeassess.classes.dto.response.ClassResponse;
import com.codeassess.classes.service.StudentClassService;
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
@RequestMapping("/api/student/classes")
@RequiredArgsConstructor
public class StudentClassController {

    private final StudentClassService studentClassService;

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping
    public List<ClassResponse> getMyClasses(@AuthenticationPrincipal User currentUser) {
        return studentClassService.getStudentClasses(currentUser.getId());
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/{id}")
    public ClassResponse getClassById(@PathVariable String id, @AuthenticationPrincipal User currentUser) {
        return studentClassService.getStudentClassById(id, currentUser.getId());
    }
}
