package com.codeassess.plagiarism.controller;

import com.codeassess.plagiarism.dto.AppealResolveRequestDto;
import com.codeassess.plagiarism.dto.AppealResponseDto;
import com.codeassess.plagiarism.service.TeacherAppealService;
import com.codeassess.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teacher/appeals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class TeacherAppealController {

    private final TeacherAppealService teacherAppealService;

    @GetMapping
    public ResponseEntity<List<AppealResponseDto>> getTeacherAppeals(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(teacherAppealService.getTeacherAppeals(currentUser));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppealResponseDto> getTeacherAppeal(
            @PathVariable("id") String opaqueId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(teacherAppealService.getTeacherAppeal(opaqueId, currentUser));
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<AppealResponseDto> resolveAppealPut(
            @PathVariable("id") String opaqueId,
            @Valid @RequestBody AppealResolveRequestDto dto,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(teacherAppealService.resolveAppeal(opaqueId, dto, currentUser));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<AppealResponseDto> resolveAppealPost(
            @PathVariable("id") String opaqueId,
            @Valid @RequestBody AppealResolveRequestDto dto,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(teacherAppealService.resolveAppeal(opaqueId, dto, currentUser));
    }
}
