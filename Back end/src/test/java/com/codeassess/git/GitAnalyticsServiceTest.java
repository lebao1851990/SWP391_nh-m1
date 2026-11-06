package com.codeassess.git;

import com.codeassess.git.client.GitClientService;
import com.codeassess.git.client.MockGitProviderClient;
import com.codeassess.git.dto.*;
import com.codeassess.git.model.GitCommitModel;
import com.codeassess.git.model.GitContributorMappingModel;
import com.codeassess.git.model.GitRepositoryModel;
import com.codeassess.git.service.GitAnalyticsService;
import com.codeassess.git.service.GitStorageService;
import com.codeassess.git.service.GitSyncService;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GitAnalyticsServiceTest {

    private GitStorageService storageService;
    private GitSyncService syncService;
    private GitAnalyticsService analyticsService;

    @Mock
    private UserRepository userRepository;

    private User aliceUser;
    private User bobUser;

    @BeforeEach
    void setUp() {
        storageService = new GitStorageService();
        storageService.clearAll();
        syncService = new GitSyncService(storageService, userRepository, new GitClientService(List.of(new MockGitProviderClient())));
        analyticsService = new GitAnalyticsService(storageService, userRepository);

        aliceUser = User.builder()
                .id("user-alice-123")
                .email("alice@university.edu")
                .fullName("Alice Johnson")
                .role(Role.STUDENT)
                .build();

        bobUser = User.builder()
                .id("user-bob-456")
                .email("bob@university.edu")
                .fullName("Bob Smith")
                .role(Role.STUDENT)
                .build();
    }

    @Test
    @DisplayName("1. Repository Registration and Credential Sanitization")
    void testRegisterRepository() {
        GitRepositoryRequestDto req = GitRepositoryRequestDto.builder()
                .name("Team Alpha Frontend")
                .projectId("proj-001")
                .classId("class-cs101")
                .repoUrl("https://github.com/org/team-alpha.git")
                .defaultBranch("main")
                .provider("GITHUB")
                .accessToken("ghp_SecretTokenNeverExposed")
                .memberUserIds(Set.of("user-alice-123", "user-bob-456"))
                .build();

        GitRepositoryResponseDto resp = analyticsService.registerRepository(req);

        assertNotNull(resp.getId());
        assertEquals("Team Alpha Frontend", resp.getName());
        assertEquals("proj-001", resp.getProjectId());
        assertEquals("PENDING", resp.getSyncStatus());
        assertEquals(2, resp.getMemberUserIds().size());

        // Verify stored repo
        Optional<GitRepositoryModel> stored = storageService.findRepositoryById(resp.getId());
        assertTrue(stored.isPresent());
        assertEquals("ghp_SecretTokenNeverExposed", stored.get().getAccessTokenEncrypted());
    }

    @Test
    @DisplayName("2. Contributor Identity Resolution: Direct, Prefix, Manual Override, Unmatched")
    void testContributorIdentityResolution() {
        when(userRepository.findAll()).thenReturn(List.of(aliceUser, bobUser));

        String repoId = "repo-test-1";
        storageService.saveRepository(GitRepositoryModel.builder().id(repoId).name("Repo 1").build());

        // Case A: Direct email match
        String resolvedAlice = syncService.resolveUserId(repoId, "alice@university.edu", "Alice J.");
        assertEquals("user-alice-123", resolvedAlice);

        // Case B: Username prefix match (e.g. personal email with student username)
        String resolvedBobPrefix = syncService.resolveUserId(repoId, "bob@gmail.com", "Bob S.");
        assertEquals("user-bob-456", resolvedBobPrefix);

        // Case C: Unmatched external contributor
        String resolvedStranger = syncService.resolveUserId(repoId, "charlie@external.com", "Charlie Brown");
        assertNull(resolvedStranger, "Unknown email and username should result in null (unmatched)");

        // Case D: Manual Alias Mapping Override
        storageService.saveMapping(GitContributorMappingModel.builder()
                .repositoryId(repoId)
                .gitAuthorEmail("charlie@external.com")
                .userId("user-alice-123") // Charlie was an alias of Alice
                .build());

        String resolvedAfterMapping = syncService.resolveUserId(repoId, "charlie@external.com", "Charlie Brown");
        assertEquals("user-alice-123", resolvedAfterMapping, "Manual mapping must override automatic lookup");
    }

    @Test
    @DisplayName("3. Idempotent Synchronization: Duplicate commits do not alter count or line stats")
    void testIdempotentSync() {
        when(userRepository.findAll()).thenReturn(List.of(aliceUser));

        String repoId = "repo-idempotent";
        storageService.saveRepository(GitRepositoryModel.builder().id(repoId).name("Repo Idempotent").build());

        GitCommitDto commit1 = GitCommitDto.builder()
                .commitHash("abc111")
                .authorName("Alice")
                .authorEmail("alice@university.edu")
                .commitDate(Instant.now().minus(2, ChronoUnit.DAYS))
                .commitMessage("feat: Initial scaffold")
                .linesAdded(120)
                .linesDeleted(0)
                .filesChanged(3)
                .build();

        GitCommitDto commit2 = GitCommitDto.builder()
                .commitHash("abc222")
                .authorName("Alice")
                .authorEmail("alice@university.edu")
                .commitDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .commitMessage("fix: Resolved parsing defect")
                .linesAdded(15)
                .linesDeleted(5)
                .filesChanged(1)
                .build();

        GitSyncRequestDto syncReq = GitSyncRequestDto.builder()
                .customCommits(List.of(commit1, commit2))
                .build();

        // First sync
        int count1 = syncService.syncCommits(repoId, syncReq);
        assertEquals(2, count1);

        RepositoryAnalyticsResponseDto analytics1 = analyticsService.calculateAnalytics(repoId, null, null);
        assertEquals(2, analytics1.getTotalCommits());
        assertEquals(135, analytics1.getTotalLinesAdded());
        assertEquals(5, analytics1.getTotalLinesDeleted());

        // Second sync (same commits)
        int count2 = syncService.syncCommits(repoId, syncReq);
        assertEquals(2, count2);

        RepositoryAnalyticsResponseDto analytics2 = analyticsService.calculateAnalytics(repoId, null, null);
        assertEquals(2, analytics2.getTotalCommits(), "Commit count must not double on resync");
        assertEquals(135, analytics2.getTotalLinesAdded(), "Lines added must not double on resync");
        assertEquals(5, analytics2.getTotalLinesDeleted(), "Lines deleted must not double on resync");
    }

    @Test
    @DisplayName("4. Merge Commits: Excluded from member additions/deletions, segregated in stats")
    void testMergeCommitHandling() {
        when(userRepository.findAll()).thenReturn(List.of(aliceUser, bobUser));

        String repoId = "repo-merge";
        storageService.saveRepository(GitRepositoryModel.builder().id(repoId).name("Repo Merge").build());

        GitCommitDto regularCommit = GitCommitDto.builder()
                .commitHash("reg001")
                .authorName("Alice")
                .authorEmail("alice@university.edu")
                .commitDate(Instant.now().minus(3, ChronoUnit.DAYS))
                .commitMessage("feat: Implement core module")
                .linesAdded(200)
                .linesDeleted(10)
                .filesChanged(4)
                .parentHashes(List.of("parent000"))
                .isMergeCommit(false)
                .build();

        GitCommitDto mergeCommit = GitCommitDto.builder()
                .commitHash("merge002")
                .authorName("Bob")
                .authorEmail("bob@university.edu")
                .commitDate(Instant.now().minus(2, ChronoUnit.DAYS))
                .commitMessage("Merge pull request #1 from feature-branch")
                .linesAdded(500) // inflated merge lines
                .linesDeleted(50)
                .filesChanged(8)
                .parentHashes(List.of("p1", "p2")) // Multiple parents -> merge commit
                .build();

        syncService.syncCommits(repoId, GitSyncRequestDto.builder().customCommits(List.of(regularCommit, mergeCommit)).build());

        RepositoryAnalyticsResponseDto analytics = analyticsService.calculateAnalytics(repoId, null, null);

        assertEquals(2, analytics.getTotalCommits());
        assertEquals(1, analytics.getNonMergeCommits());
        assertEquals(1, analytics.getMergeCommits());

        // Total lines must only count non-merge commits (200 additions, not 200 + 500)
        assertEquals(200, analytics.getTotalLinesAdded(), "Merge commit lines must be excluded from totals to prevent inflation");
        assertEquals(10, analytics.getTotalLinesDeleted());

        // Check Bob's summary: 1 merge commit, 0 regular additions
        ContributorSummaryDto bobSummary = analytics.getContributors().stream()
                .filter(c -> c.getGitAuthorEmail().equals("bob@university.edu"))
                .findFirst().orElseThrow();

        assertEquals(1, bobSummary.getTotalCommits());
        assertEquals(1, bobSummary.getMergeCommits());
        assertEquals(0, bobSummary.getLinesAdded(), "Merge additions must not be attributed to individual code volume");
    }

    @Test
    @DisplayName("5. Revert Commits: Accurately detected and tracked")
    void testRevertCommitHandling() {
        when(userRepository.findAll()).thenReturn(List.of(aliceUser));

        String repoId = "repo-revert";
        storageService.saveRepository(GitRepositoryModel.builder().id(repoId).name("Repo Revert").build());

        GitCommitDto c1 = GitCommitDto.builder()
                .commitHash("c1")
                .authorName("Alice")
                .authorEmail("alice@university.edu")
                .commitDate(Instant.now().minus(2, ChronoUnit.DAYS))
                .commitMessage("feat: Experimental engine")
                .linesAdded(50)
                .linesDeleted(0)
                .build();

        GitCommitDto c2 = GitCommitDto.builder()
                .commitHash("c2")
                .authorName("Alice")
                .authorEmail("alice@university.edu")
                .commitDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .commitMessage("Revert \"feat: Experimental engine\"")
                .linesAdded(0)
                .linesDeleted(50)
                .build();

        syncService.syncCommits(repoId, GitSyncRequestDto.builder().customCommits(List.of(c1, c2)).build());

        RepositoryAnalyticsResponseDto analytics = analyticsService.calculateAnalytics(repoId, null, null);
        assertEquals(2, analytics.getTotalCommits());
        assertEquals(1, analytics.getRevertCommits());

        ContributorSummaryDto aliceSummary = analytics.getContributors().get(0);
        assertEquals(1, aliceSummary.getRevertCommits());
    }

    @Test
    @DisplayName("6. Date-Range Filtering: Commits outside bounds are strictly excluded")
    void testDateRangeFiltering() {
        when(userRepository.findAll()).thenReturn(List.of(aliceUser));

        String repoId = "repo-date";
        storageService.saveRepository(GitRepositoryModel.builder().id(repoId).name("Repo Date").build());

        Instant now = Instant.now();
        Instant day1 = now.minus(10, ChronoUnit.DAYS);
        Instant day2 = now.minus(5, ChronoUnit.DAYS);
        Instant day3 = now.minus(1, ChronoUnit.DAYS);

        GitCommitDto c1 = GitCommitDto.builder().commitHash("h1").authorName("Alice").authorEmail("alice@university.edu").commitDate(day1).linesAdded(10).build();
        GitCommitDto c2 = GitCommitDto.builder().commitHash("h2").authorName("Alice").authorEmail("alice@university.edu").commitDate(day2).linesAdded(20).build();
        GitCommitDto c3 = GitCommitDto.builder().commitHash("h3").authorName("Alice").authorEmail("alice@university.edu").commitDate(day3).linesAdded(30).build();

        syncService.syncCommits(repoId, GitSyncRequestDto.builder().customCommits(List.of(c1, c2, c3)).build());

        // Full history
        assertEquals(3, analyticsService.calculateAnalytics(repoId, null, null).getTotalCommits());

        // Filter: since day2 minus 1 hour, until day2 plus 1 hour -> only c2
        Instant since = day2.minus(1, ChronoUnit.HOURS);
        Instant until = day2.plus(1, ChronoUnit.HOURS);

        RepositoryAnalyticsResponseDto filtered = analyticsService.calculateAnalytics(repoId, since, until);
        assertEquals(1, filtered.getTotalCommits());
        assertEquals(20, filtered.getTotalLinesAdded());
        assertEquals(since, filtered.getFilteredSince());
        assertEquals(until, filtered.getFilteredUntil());
    }

    @Test
    @DisplayName("7. Calculation Accuracy: Proportions, Active Days and Ratios")
    void testCalculationAccuracy() {
        when(userRepository.findAll()).thenReturn(List.of(aliceUser, bobUser));

        String repoId = "repo-calc";
        storageService.saveRepository(GitRepositoryModel.builder().id(repoId).name("Repo Calc").build());

        Instant now = Instant.now();
        Instant day1 = now.minus(3, ChronoUnit.DAYS);
        Instant day2 = now.minus(2, ChronoUnit.DAYS);

        // Alice: 3 commits (2 on day1, 1 on day2) -> 300 additions, 50 deletions
        GitCommitDto a1 = GitCommitDto.builder().commitHash("a1").authorName("Alice").authorEmail("alice@university.edu").commitDate(day1).linesAdded(100).linesDeleted(20).build();
        GitCommitDto a2 = GitCommitDto.builder().commitHash("a2").authorName("Alice").authorEmail("alice@university.edu").commitDate(day1.plusSeconds(3600)).linesAdded(100).linesDeleted(10).build();
        GitCommitDto a3 = GitCommitDto.builder().commitHash("a3").authorName("Alice").authorEmail("alice@university.edu").commitDate(day2).linesAdded(100).linesDeleted(20).build();

        // Bob: 1 commit on day2 -> 100 additions, 0 deletions
        GitCommitDto b1 = GitCommitDto.builder().commitHash("b1").authorName("Bob").authorEmail("bob@university.edu").commitDate(day2).linesAdded(100).linesDeleted(0).build();

        syncService.syncCommits(repoId, GitSyncRequestDto.builder().customCommits(List.of(a1, a2, a3, b1)).build());

        RepositoryAnalyticsResponseDto analytics = analyticsService.calculateAnalytics(repoId, null, null);

        assertEquals(4, analytics.getTotalCommits());
        assertEquals(400, analytics.getTotalLinesAdded());
        assertEquals(50, analytics.getTotalLinesDeleted());
        assertEquals(350, analytics.getNetLinesTotal());
        assertEquals(2, analytics.getTimeline().size(), "Should have exactly 2 calendar day points in timeline");

        ContributorSummaryDto alice = analytics.getContributors().stream().filter(c -> c.getGitAuthorEmail().equals("alice@university.edu")).findFirst().orElseThrow();
        assertEquals(3, alice.getTotalCommits());
        assertEquals(75.0, alice.getCommitPercentage(), 1e-1); // 3/4 = 75%
        assertEquals(75.0, alice.getLineAdditionPercentage(), 1e-1); // 300/400 = 75%
        assertEquals(2, alice.getActiveDays());
        assertEquals(1.0, alice.getConsistencyRatio(), 1e-1); // active 2 out of 2 total project days = 1.0

        ContributorSummaryDto bob = analytics.getContributors().stream().filter(c -> c.getGitAuthorEmail().equals("bob@university.edu")).findFirst().orElseThrow();
        assertEquals(1, bob.getTotalCommits());
        assertEquals(25.0, bob.getCommitPercentage(), 1e-1); // 1/4 = 25%
        assertEquals(25.0, bob.getLineAdditionPercentage(), 1e-1); // 100/400 = 25%
        assertEquals(1, bob.getActiveDays());
        assertEquals(0.5, bob.getConsistencyRatio(), 1e-1); // active 1 out of 2 total project days = 0.5
    }

    @Test
    @DisplayName("8. Methodology Explanation: Complete guidelines and ethical warnings present")
    void testMethodologyExplanation() {
        CalculationMethodologyDto meth = analyticsService.getMethodologyExplanation();

        assertNotNull(meth.getEthicalNotice());
        assertTrue(meth.getEthicalNotice().contains("MUST NOT be equated directly with student effort"));
        assertNotNull(meth.getMergeCommitRule());
        assertNotNull(meth.getRevertCommitRule());
        assertFalse(meth.getLimitations().isEmpty());
        assertFalse(meth.getRecommendedInstructorGuidelines().isEmpty());
    }
}

