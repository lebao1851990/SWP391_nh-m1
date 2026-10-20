package com.codeassess.classes.controller;

import com.codeassess.classes.dto.request.CreateTeacherClassDto;
import com.codeassess.classes.dto.response.ClassResponse;
import com.codeassess.classes.service.TeacherClassService;
import com.codeassess.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping
    public ClassResponse createClass(
            @Valid @RequestBody CreateTeacherClassDto dto,
            @AuthenticationPrincipal User currentUser) {
        return teacherClassService.createTeacherClass(dto, currentUser.getId());
    }

    @PreAuthorize("hasRole('TEACHER')")
    @GetMapping("/{id}/students")
    public List<com.codeassess.classes.dto.response.ClassStudentResponse> getClassStudents(
            @PathVariable String id,
            @AuthenticationPrincipal User currentUser) {
        return teacherClassService.getClassStudents(id, currentUser.getId());
    }

    @PreAuthorize("hasRole('TEACHER')")
    @PostMapping("/{id}/students")
    public com.codeassess.classes.dto.response.ClassStudentResponse enrollStudent(
            @PathVariable String id,
            @Valid @RequestBody com.codeassess.classes.dto.request.EnrollStudentDto dto,
            @AuthenticationPrincipal User currentUser) {
        return teacherClassService.enrollStudent(id, dto, currentUser.getId());
    }

    @PreAuthorize("hasRole('TEACHER')")
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}/students/{studentId}")
    public org.springframework.http.ResponseEntity<Void> unenrollStudent(
            @PathVariable String id,
            @PathVariable String studentId,
            @AuthenticationPrincipal User currentUser) {
        teacherClassService.unenrollStudent(id, studentId, currentUser.getId());
        return org.springframework.http.ResponseEntity.noContent().build();
    }
}
