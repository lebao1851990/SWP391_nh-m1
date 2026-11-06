package com.codeassess.git.service;

import com.codeassess.git.client.GitClientService;
import com.codeassess.git.dto.GitCommitDto;
import com.codeassess.git.dto.GitSyncRequestDto;
import com.codeassess.git.model.GitCommitModel;
import com.codeassess.git.model.GitContributorMappingModel;
import com.codeassess.git.model.GitRepositoryModel;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class GitSyncService {

    private final GitStorageService storageService;
    private final UserRepository userRepository;
    private final GitClientService gitClientService;

    /**
     * Synchronizes commits into the repository store idempotently.
     * Evaluates merge commits, revert commits, and maps contributor identities.
     */
    public int syncCommits(String repositoryId, GitSyncRequestDto syncRequest) {
        GitRepositoryModel repo = storageService.findRepositoryById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found with ID: " + repositoryId));

        List<GitCommitDto> incomingCommits = (syncRequest != null && syncRequest.getCustomCommits() != null && !syncRequest.getCustomCommits().isEmpty())
                ? syncRequest.getCustomCommits()
                : Collections.emptyList();

        // If no custom commits provided in request, fetch from Git provider
        if (incomingCommits.isEmpty()) {
            String branch = (syncRequest != null && syncRequest.getBranch() != null && !syncRequest.getBranch().trim().isEmpty())
                    ? syncRequest.getBranch()
                    : repo.getDefaultBranch();
            Instant since = syncRequest != null ? syncRequest.getSince() : null;
            Instant until = syncRequest != null ? syncRequest.getUntil() : null;
            int maxPages = (syncRequest != null && syncRequest.getMaxPages() != null) ? syncRequest.getMaxPages() : 5;

            log.info("Fetching commits from provider '{}' for repo: {}", repo.getProvider(), repo.getRepoUrl());
            incomingCommits = gitClientService.fetchCommits(
                    repo.getProvider(),
                    repo.getRepoUrl(),
                    branch,
                    repo.getAccessTokenEncrypted(),
                    since,
                    until,
                    maxPages
            );
        }

        int syncedCount = 0;
        try {
            for (GitCommitDto cDto : incomingCommits) {
                if (cDto.getCommitHash() == null || cDto.getCommitHash().trim().isEmpty()) {
                    continue;
                }

                // Merge commit detection (multiple parents)
                boolean isMerge = cDto.isMergeCommit() ||
                        (cDto.getParentHashes() != null && cDto.getParentHashes().size() > 1);

                // Revert commit detection
                boolean isRevert = cDto.isRevertCommit() ||
                        detectRevertMessage(cDto.getCommitMessage());

                // Contributor identity resolution
                String resolvedUserId = resolveUserId(repositoryId, cDto.getAuthorEmail(), cDto.getAuthorName());

                GitCommitModel model = GitCommitModel.builder()
                        .repositoryId(repositoryId)
                        .commitHash(cDto.getCommitHash().trim())
                        .authorName(cDto.getAuthorName() != null ? cDto.getAuthorName().trim() : "Unknown")
                        .authorEmail(cDto.getAuthorEmail() != null ? cDto.getAuthorEmail().toLowerCase().trim() : "unknown@git")
                        .userId(resolvedUserId)
                        .commitDate(cDto.getCommitDate() != null ? cDto.getCommitDate() : Instant.now())
                        .commitMessage(cDto.getCommitMessage() != null ? cDto.getCommitMessage() : "")
                        .linesAdded(Math.max(0, cDto.getLinesAdded()))
                        .linesDeleted(Math.max(0, cDto.getLinesDeleted()))
                        .filesChanged(Math.max(0, cDto.getFilesChanged()))
                        .isMergeCommit(isMerge)
                        .isRevertCommit(isRevert)
                        .parentHashes(cDto.getParentHashes() != null ? cDto.getParentHashes() : new ArrayList<>())
                        .build();

                storageService.saveCommit(model);
                syncedCount++;
            }

            repo.setSyncStatus("SYNCED");
            repo.setLastSyncedAt(Instant.now());
            storageService.saveRepository(repo);

            log.info("Successfully synced {} commits for repository: {}", syncedCount, repositoryId);
            return syncedCount;
        } catch (Exception ex) {
            repo.setSyncStatus("FAILED");
            storageService.saveRepository(repo);
            log.error("Failed to synchronize commits for repository: " + repositoryId, ex);
            throw new RuntimeException("Git synchronization failed: " + ex.getMessage(), ex);
        }
    }

    public String resolveUserId(String repositoryId, String authorEmail, String authorName) {
        if (authorEmail == null) return null;
        String cleanEmail = authorEmail.toLowerCase().trim();

        // 1. Check manual mapping override
        Optional<GitContributorMappingModel> mapping = storageService.findMapping(repositoryId, cleanEmail);
        if (mapping.isPresent() && mapping.get().getUserId() != null) {
            return mapping.get().getUserId();
        }

        // 2. Direct email match against CodeAssess User database
        try {
            Optional<User> directUser = userRepository.findAll().stream()
                    .filter(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(cleanEmail))
                    .findFirst();
            if (directUser.isPresent()) {
                return directUser.get().getId();
            }

            // 3. Prefix match on email (e.g. username from local-part)
            String localPart = cleanEmail.contains("@") ? cleanEmail.substring(0, cleanEmail.indexOf('@')) : cleanEmail;
            Optional<User> prefixUser = userRepository.findAll().stream()
                    .filter(u -> (u.getEmail() != null && u.getEmail().toLowerCase().startsWith(localPart)) ||
                            (u.getFullName() != null && authorName != null && u.getFullName().equalsIgnoreCase(authorName.trim())))
                    .findFirst();
            if (prefixUser.isPresent()) {
                return prefixUser.get().getId();
            }
        } catch (Exception e) {
            log.warn("User repository lookup skipped or failed during contributor resolution: {}", e.getMessage());
        }

        return null; // Unmatched
    }

    private boolean detectRevertMessage(String message) {
        if (message == null) return false;
        String trimmed = message.trim().toLowerCase();
        return trimmed.startsWith("revert \"") ||
                trimmed.startsWith("revert:") ||
                trimmed.contains("this reverts commit ");
    }
}
