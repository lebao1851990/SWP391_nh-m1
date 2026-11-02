package com.codeassess.plagiarism.controller;

import com.codeassess.plagiarism.dto.AppealResolveRequestDto;
import com.codeassess.plagiarism.dto.AppealResponseDto;
import com.codeassess.plagiarism.service.AdminAppealService;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/appeals")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAppealController {

    private final AdminAppealService adminAppealService;
    private final UserRepository userRepository;

    private User getAuthenticatedAdmin(User principalUser) {
        if (principalUser != null && principalUser.getEmail() != null) {
            return userRepository.findByEmail(principalUser.getEmail()).orElse(principalUser);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return userRepository.findByEmail(auth.getName()).orElse(null);
        }
        return null;
    }

    @GetMapping
    public ResponseEntity<List<AppealResponseDto>> getAdminAppeals(
            @RequestParam(value = "status", required = false) String status) {
        return ResponseEntity.ok(adminAppealService.getAdminAppeals(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppealResponseDto> getAdminAppeal(@PathVariable("id") String opaqueId) {
        return ResponseEntity.ok(adminAppealService.getAdminAppeal(opaqueId));
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<AppealResponseDto> resolveAppealPut(
            @PathVariable("id") String opaqueId,
            @Valid @RequestBody AppealResolveRequestDto dto,
            @AuthenticationPrincipal User currentUser) {
        User admin = getAuthenticatedAdmin(currentUser);
        return ResponseEntity.ok(adminAppealService.resolveAppeal(opaqueId, dto, admin));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<AppealResponseDto> resolveAppealPost(
            @PathVariable("id") String opaqueId,
            @Valid @RequestBody AppealResolveRequestDto dto,
            @AuthenticationPrincipal User currentUser) {
        User admin = getAuthenticatedAdmin(currentUser);
        return ResponseEntity.ok(adminAppealService.resolveAppeal(opaqueId, dto, admin));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<AppealResponseDto> approveAppeal(
            @PathVariable("id") String opaqueId,
            @Valid @RequestBody AppealResolveRequestDto dto,
            @AuthenticationPrincipal User currentUser) {
        dto.setDecision("APPROVED");
        User admin = getAuthenticatedAdmin(currentUser);
        return ResponseEntity.ok(adminAppealService.resolveAppeal(opaqueId, dto, admin));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<AppealResponseDto> rejectAppeal(
            @PathVariable("id") String opaqueId,
            @Valid @RequestBody AppealResolveRequestDto dto,
            @AuthenticationPrincipal User currentUser) {
        dto.setDecision("REJECTED");
        User admin = getAuthenticatedAdmin(currentUser);
        return ResponseEntity.ok(adminAppealService.resolveAppeal(opaqueId, dto, admin));
    }

    @GetMapping("/{id}/submission/archive")
    public ResponseEntity<byte[]> downloadSubmissionArchive(
            @PathVariable("id") String opaqueId,
            @AuthenticationPrincipal User currentUser) {
        User admin = getAuthenticatedAdmin(currentUser);
        byte[] zipBytes = adminAppealService.getAppealSubmissionArchive(opaqueId, admin);
        String filename = adminAppealService.getSubmissionArchiveFilename(opaqueId);
        String encodedFilename = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encodedFilename)
                .contentType(org.springframework.http.MediaType.parseMediaType("application/zip"))
                .body(zipBytes);
    }
}
