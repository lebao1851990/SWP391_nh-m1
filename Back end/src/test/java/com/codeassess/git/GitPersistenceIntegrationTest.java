package com.codeassess.git;

import com.codeassess.git.client.GitClientService;
import com.codeassess.git.client.MockGitProviderClient;
import com.codeassess.git.dto.GitCommitDto;
import com.codeassess.git.dto.GitSyncRequestDto;
import com.codeassess.git.model.GitCommitModel;
import com.codeassess.git.model.GitContributorMappingModel;
import com.codeassess.git.model.GitRepositoryModel;
import com.codeassess.git.service.GitStorageService;
import com.codeassess.git.service.GitSyncService;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class GitPersistenceIntegrationTest {

    private GitStorageService storageService;
    private GitSyncService syncService;

    @Mock
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        storageService = new GitStorageService();
        storageService.clearAll();
        MockGitProviderClient mockClient = new MockGitProviderClient();
        GitClientService clientService = new GitClientService(List.of(mockClient));
        syncService = new GitSyncService(storageService, userRepository, clientService);
    }

    @Test
    @DisplayName("Repository metadata is persisted and retrievable")
    void testRepositoryPersistence() {
        GitRepositoryModel repo = GitRepositoryModel.builder()
                .name("team-capstone-project")
                .projectId("proj-001")
                .classId("class-101")
                .repoUrl("https://github.com/org/team-capstone")
                .defaultBranch("main")
                .provider("GITHUB")
                .memberUserIds(Set.of("user-1", "user-2"))
                .build();

        GitRepositoryModel saved = storageService.saveRepository(repo);
        assertNotNull(saved.getId());

        Optional<GitRepositoryModel> retrieved = storageService.findRepositoryById(saved.getId());
        assertTrue(retrieved.isPresent());
        assertEquals("team-capstone-project", retrieved.get().getName());
        assertEquals(2, retrieved.get().getMemberUserIds().size());
        assertEquals("PENDING", retrieved.get().getSyncStatus());
    }

    @Test
    @DisplayName("Idempotent commit persistence ensures no duplicate commit hashes per repository")
    void testCommitIdempotentPersistence() {
        String repoId = "repo-idempotent-test";

        GitCommitModel c1 = GitCommitModel.builder()
                .repositoryId(repoId)
                .commitHash("hash-abc-123")
                .authorName("Alice")
                .authorEmail("alice@test.edu")
                .commitDate(Instant.now().minus(2, ChronoUnit.HOURS))
                .commitMessage("Initial implementation")
                .linesAdded(100)
                .linesDeleted(0)
                .filesChanged(3)
                .build();

        storageService.saveCommit(c1);
        assertEquals(1, storageService.getCommitCount(repoId));

        // Resave same commit hash with updated stats (e.g. recalculated lines)
        GitCommitModel c1Updated = GitCommitModel.builder()
                .repositoryId(repoId)
                .commitHash("hash-abc-123")
                .authorName("Alice")
                .authorEmail("alice@test.edu")
                .commitDate(c1.getCommitDate())
                .commitMessage("Initial implementation (edited)")
                .linesAdded(120)
                .linesDeleted(5)
                .filesChanged(3)
                .build();

        storageService.saveCommit(c1Updated);

        // Count must remain 1
        assertEquals(1, storageService.getCommitCount(repoId));
        List<GitCommitModel> commits = storageService.findCommitsByRepository(repoId, null, null);
        assertEquals(1, commits.size());
        assertEquals(120, commits.get(0).getLinesAdded());
        assertEquals(5, commits.get(0).getLinesDeleted());
    }

    @Test
    @DisplayName("Contributor mappings persist and normalize email addresses case-insensitively")
    void testContributorMappingPersistence() {
        String repoId = "repo-map-test";

        GitContributorMappingModel mapping = GitContributorMappingModel.builder()
                .repositoryId(repoId)
                .gitAuthorName("Dev Bob")
                .gitAuthorEmail("BOB.DEV@External.ORG")
                .userId("codeassess-user-bob-99")
                .build();

        storageService.saveMapping(mapping);

        // Query with lowercase email
        Optional<GitContributorMappingModel> found = storageService.findMapping(repoId, "bob.dev@external.org");
        assertTrue(found.isPresent());
        assertEquals("codeassess-user-bob-99", found.get().getUserId());

        // Update mapping
        GitContributorMappingModel updatedMapping = GitContributorMappingModel.builder()
                .repositoryId(repoId)
                .gitAuthorName("Dev Bob")
                .gitAuthorEmail("bob.dev@external.org")
                .userId("codeassess-user-bob-NEW")
                .build();
        storageService.saveMapping(updatedMapping);

        List<GitContributorMappingModel> allMappings = storageService.findMappingsByRepository(repoId);
        assertEquals(1, allMappings.size(), "Mapping for the same email must be replaced idempotently");
        assertEquals("codeassess-user-bob-NEW", allMappings.get(0).getUserId());
    }

    @Test
    @DisplayName("Provider-driven synchronization persists commits idempotently and updates sync status")
    void testSyncCommitsViaProvider() {
        GitRepositoryModel repo = GitRepositoryModel.builder()
                .id("repo-mock-sync")
                .name("mock-sync-repo")
                .projectId("proj-sync")
                .repoUrl("mock://repo/sync")
                .defaultBranch("main")
                .provider("MOCK")
                .build();
        storageService.saveRepository(repo);

        // Initial sync
        int count1 = syncService.syncCommits(repo.getId(), new GitSyncRequestDto());
        assertEquals(4, count1, "Mock client should synchronize 4 commits");
        assertEquals(4, storageService.getCommitCount(repo.getId()));

        GitRepositoryModel updatedRepo = storageService.findRepositoryById(repo.getId()).orElseThrow();
        assertEquals("SYNCED", updatedRepo.getSyncStatus());
        assertNotNull(updatedRepo.getLastSyncedAt());

        // Second sync (idempotency test)
        int count2 = syncService.syncCommits(repo.getId(), new GitSyncRequestDto());
        assertEquals(4, count2);
        assertEquals(4, storageService.getCommitCount(repo.getId()), "Commit count must not double on resync");
    }
}

