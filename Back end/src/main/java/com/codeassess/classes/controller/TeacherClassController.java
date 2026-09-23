package com.codeassess.classes.controller;

import com.codeassess.classes.dto.response.ClassResponse;
import com.codeassess.classes.service.TeacherClassService;
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
@RequestMapping("/api/teacher/classes")
@RequiredArgsConstructor
public class TeacherClassController {

    private final TeacherClassService teacherClassService;

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping
    public List<ClassResponse> getMyClasses(@AuthenticationPrincipal User currentUser) {
        return teacherClassService.getTeacherClasses(currentUser.getId());
    }

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/{id}")
    public ClassResponse getClassById(@PathVariable String id, @AuthenticationPrincipal User currentUser) {
        return teacherClassService.getTeacherClassById(id, currentUser.getId());
    }
}
