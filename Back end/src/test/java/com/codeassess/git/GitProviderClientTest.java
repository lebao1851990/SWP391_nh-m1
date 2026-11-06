package com.codeassess.git;

import com.codeassess.git.client.GitHubProviderClient;
import com.codeassess.git.client.GitLabProviderClient;
import com.codeassess.git.client.MockGitProviderClient;
import com.codeassess.git.client.GitClientService;
import com.codeassess.git.dto.GitCommitDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GitProviderClientTest {

    private final GitHubProviderClient gitHubClient = new GitHubProviderClient();
    private final GitLabProviderClient gitLabClient = new GitLabProviderClient();
    private final MockGitProviderClient mockClient = new MockGitProviderClient();
    private final GitClientService clientService = new GitClientService(List.of(gitHubClient, gitLabClient, mockClient));

    @Test
    @DisplayName("GitHub URL slug extraction handles HTTPS and SSH formats")
    void testGitHubUrlExtraction() {
        assertEquals("owner/repo", gitHubClient.extractRepoSlug("https://github.com/owner/repo"));
        assertEquals("owner/repo", gitHubClient.extractRepoSlug("https://github.com/owner/repo.git"));
        assertEquals("owner/repo", gitHubClient.extractRepoSlug("git@github.com:owner/repo.git"));
        assertNull(gitHubClient.extractRepoSlug("https://gitlab.com/owner/repo"));
        assertNull(gitHubClient.extractRepoSlug("invalid-url"));
    }

    @Test
    @DisplayName("GitLab URL project path extraction handles nested subgroups")
    void testGitLabUrlExtraction() {
        assertEquals("group/subgroup/project", gitLabClient.extractProjectPath("https://gitlab.com/group/subgroup/project.git"));
        assertEquals("group/project", gitLabClient.extractProjectPath("https://gitlab.com/group/project"));
        assertEquals("group/project", gitLabClient.extractProjectPath("git@gitlab.com:group/project.git"));
        assertNull(gitLabClient.extractProjectPath("https://github.com/group/project"));
    }

    @Test
    @DisplayName("Mock Git Provider generates deterministic commits with date range filtering")
    void testMockGitProviderCommits() {
        List<GitCommitDto> allCommits = mockClient.fetchCommits("mock://repo", "main", null, null, null, 1);
        assertEquals(4, allCommits.size(), "Mock client should generate 4 deterministic commits");

        // Verify merge commit flag
        assertTrue(allCommits.stream().anyMatch(GitCommitDto::isMergeCommit), "At least one commit should be a merge commit");

        // Verify date filter
        Instant since = Instant.now().minus(2, ChronoUnit.DAYS);
        List<GitCommitDto> recentCommits = mockClient.fetchCommits("mock://repo", "main", null, since, null, 1);
        assertTrue(recentCommits.size() <= 3, "Filtered commits should be subset of all commits");
    }

    @Test
    @DisplayName("GitClientService correctly resolves client by provider name and URL fallback")
    void testGitClientServiceResolution() {
        assertEquals("GITHUB", clientService.resolveClient("GITHUB", null).getProviderName());
        assertEquals("GITLAB", clientService.resolveClient("GITLAB", null).getProviderName());
        assertEquals("MOCK", clientService.resolveClient("MOCK", null).getProviderName());

        // URL auto-detection fallback
        assertEquals("GITHUB", clientService.resolveClient(null, "https://github.com/foo/bar").getProviderName());
        assertEquals("GITLAB", clientService.resolveClient(null, "https://gitlab.com/foo/bar").getProviderName());
        assertEquals("MOCK", clientService.resolveClient(null, "mock://dummy/repo").getProviderName());
    }
}

