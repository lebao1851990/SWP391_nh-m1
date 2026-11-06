package com.codeassess.git.controller;

import com.codeassess.git.config.GitSecurityEvaluator;
import com.codeassess.git.dto.*;
import com.codeassess.git.model.GitCommitModel;
import com.codeassess.git.model.GitContributorMappingModel;
import com.codeassess.git.service.GitAnalyticsService;
import com.codeassess.git.service.GitStorageService;
import com.codeassess.git.service.GitSyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/git")
@RequiredArgsConstructor
@Slf4j
public class GitAnalyticsController {

    private final GitAnalyticsService analyticsService;
    private final GitSyncService syncService;
    private final GitStorageService storageService;
    private final GitSecurityEvaluator securityEvaluator;

    @PostMapping("/repositories")
    public ResponseEntity<GitRepositoryResponseDto> registerRepository(@Valid @RequestBody GitRepositoryRequestDto req) {
        log.info("Registering repository: {} for project: {}", req.getName(), req.getProjectId());
        GitRepositoryResponseDto response = analyticsService.registerRepository(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/repositories")
    public ResponseEntity<List<GitRepositoryResponseDto>> listRepositories() {
        String currentUserId = securityEvaluator.getCurrentUserId();
        boolean isTeacherOrAdmin = securityEvaluator.isTeacherOrAdmin();
        List<GitRepositoryResponseDto> list = analyticsService.listRepositoriesForUser(currentUserId, isTeacherOrAdmin);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/repositories/{id}")
    public ResponseEntity<GitRepositoryResponseDto> getRepository(@PathVariable String id) {
        securityEvaluator.checkAccess(id);
        return analyticsService.getRepository(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/repositories/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    public ResponseEntity<Void> deleteRepository(@PathVariable String id) {
        boolean deleted = storageService.deleteRepository(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/repositories/{id}/sync")
    public ResponseEntity<?> syncRepositoryCommits(
            @PathVariable String id,
            @RequestBody(required = false) GitSyncRequestDto syncRequest) {
        securityEvaluator.checkAccess(id);
        log.info("Triggering commit sync for repository: {}", id);
        int synced = syncService.syncCommits(id, syncRequest);
        return ResponseEntity.ok(new SyncResponse(id, synced, "SUCCESS", Instant.now()));
    }

    @GetMapping("/repositories/{id}/analytics")
    public ResponseEntity<RepositoryAnalyticsResponseDto> getRepositoryAnalytics(
            @PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until) {
        securityEvaluator.checkAccess(id);
        RepositoryAnalyticsResponseDto analytics = analyticsService.calculateAnalytics(id, since, until);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/repositories/{id}/commits")
    public ResponseEntity<List<GitCommitDto>> getRepositoryCommits(
            @PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until) {
        securityEvaluator.checkAccess(id);
        List<GitCommitModel> commits = storageService.findCommitsByRepository(id, since, until);
        List<GitCommitDto> dtos = commits.stream().map(c -> GitCommitDto.builder()
                .id(c.getId())
                .repositoryId(c.getRepositoryId())
                .commitHash(c.getCommitHash())
                .authorName(c.getAuthorName())
                .authorEmail(c.getAuthorEmail())
                .userId(c.getUserId())
                .commitDate(c.getCommitDate())
                .commitMessage(c.getCommitMessage())
                .linesAdded(c.getLinesAdded())
                .linesDeleted(c.getLinesDeleted())
                .filesChanged(c.getFilesChanged())
                .isMergeCommit(c.isMergeCommit())
                .isRevertCommit(c.isRevertCommit())
                .parentHashes(c.getParentHashes())
                .build()).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/repositories/{id}/contributors/map")
    public ResponseEntity<GitContributorMappingModel> mapContributor(
            @PathVariable String id,
            @Valid @RequestBody ContributorMappingRequestDto req) {
        securityEvaluator.checkAccess(id);
        GitContributorMappingModel mapping = analyticsService.mapContributor(id, req);
        return ResponseEntity.ok(mapping);
    }

    @GetMapping("/repositories/{id}/contributors")
    public ResponseEntity<List<GitContributorMappingModel>> getMappings(@PathVariable String id) {
        securityEvaluator.checkAccess(id);
        return ResponseEntity.ok(analyticsService.getMappings(id));
    }

    @GetMapping("/methodology")
    public ResponseEntity<CalculationMethodologyDto> getMethodology() {
        return ResponseEntity.ok(analyticsService.getMethodologyExplanation());
    }

    public record SyncResponse(String repositoryId, int syncedCommits, String status, Instant timestamp) {}
}

