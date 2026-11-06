package com.codeassess.git.client;

import com.codeassess.git.dto.GitCommitDto;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class MockGitProviderClient implements GitProviderClient {

    @Override
    public String getProviderName() {
        return "MOCK";
    }

    @Override
    public List<GitCommitDto> fetchCommits(String repoUrl, String branch, String accessToken, Instant since, Instant until, int maxPages) {
        List<GitCommitDto> list = new ArrayList<>();
        Instant base = Instant.now().truncatedTo(ChronoUnit.HOURS);

        // Commit 1: Initial commit
        list.add(GitCommitDto.builder()
                .commitHash("mock-commit-001")
                .authorName("Alice Johnson")
                .authorEmail("alice@university.edu")
                .commitDate(base.minus(5, ChronoUnit.DAYS))
                .commitMessage("feat: Initial project structure and README")
                .linesAdded(150)
                .linesDeleted(0)
                .filesChanged(4)
                .parentHashes(List.of())
                .isMergeCommit(false)
                .build());

        // Commit 2: Feature branch work
        list.add(GitCommitDto.builder()
                .commitHash("mock-commit-002")
                .authorName("Bob Smith")
                .authorEmail("bob@university.edu")
                .commitDate(base.minus(3, ChronoUnit.DAYS))
                .commitMessage("feat: Implement user authentication service")
                .linesAdded(220)
                .linesDeleted(15)
                .filesChanged(6)
                .parentHashes(List.of("mock-commit-001"))
                .isMergeCommit(false)
                .build());

        // Commit 3: PR Merge
        list.add(GitCommitDto.builder()
                .commitHash("mock-commit-003")
                .authorName("Alice Johnson")
                .authorEmail("alice@university.edu")
                .commitDate(base.minus(2, ChronoUnit.DAYS))
                .commitMessage("Merge pull request #1 from auth-service")
                .linesAdded(0) // Merge commits have 0 non-merge additions
                .linesDeleted(0)
                .filesChanged(6)
                .parentHashes(List.of("mock-commit-001", "mock-commit-002"))
                .isMergeCommit(true)
                .build());

        // Commit 4: Bug fix
        list.add(GitCommitDto.builder()
                .commitHash("mock-commit-004")
                .authorName("Charlie Brown")
                .authorEmail("charlie@external.com")
                .commitDate(base.minus(1, ChronoUnit.DAYS))
                .commitMessage("fix: Resolved session timeout edge case")
                .linesAdded(35)
                .linesDeleted(10)
                .filesChanged(2)
                .parentHashes(List.of("mock-commit-003"))
                .isMergeCommit(false)
                .build());

        return list.stream()
                .filter(c -> since == null || !c.getCommitDate().isBefore(since))
                .filter(c -> until == null || !c.getCommitDate().isAfter(until))
                .toList();
    }

    @Override
    public boolean validateAccess(String repoUrl, String accessToken) {
        return repoUrl != null && !repoUrl.trim().isEmpty();
    }
}

