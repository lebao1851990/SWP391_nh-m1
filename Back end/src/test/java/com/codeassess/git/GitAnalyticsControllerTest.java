package com.codeassess.git;

import com.codeassess.git.config.GitSecurityEvaluator;
import com.codeassess.git.controller.GitAnalyticsController;
import com.codeassess.git.dto.*;
import com.codeassess.git.service.GitAnalyticsService;
import com.codeassess.git.service.GitStorageService;
import com.codeassess.git.service.GitSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class GitAnalyticsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private GitAnalyticsService analyticsService;

    @Mock
    private GitSyncService syncService;

    @Mock
    private GitStorageService storageService;

    @Mock
    private GitSecurityEvaluator securityEvaluator;

    @InjectMocks
    private GitAnalyticsController controller;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("1. GET /api/git/methodology returns 200 with ethics and calculation rules")
    void testGetMethodology() throws Exception {
        CalculationMethodologyDto dto = CalculationMethodologyDto.builder()
                .ethicalNotice("Git contribution metrics are factual indicators and MUST NOT be equated with effort or grade.")
                .mergeCommitRule("Merge commits are excluded from line counts.")
                .revertCommitRule("Reverts are tracked separately.")
                .limitations(List.of("Formatting tools distort line counts."))
                .build();

        when(analyticsService.getMethodologyExplanation()).thenReturn(dto);

        mockMvc.perform(get("/api/git/methodology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ethicalNotice").value("Git contribution metrics are factual indicators and MUST NOT be equated with effort or grade."))
                .andExpect(jsonPath("$.mergeCommitRule").value("Merge commits are excluded from line counts."));
    }

    @Test
    @DisplayName("2. POST /api/git/repositories registers repo and returns 201 Created")
    void testRegisterRepository() throws Exception {
        GitRepositoryRequestDto req = GitRepositoryRequestDto.builder()
                .name("Team Project")
                .projectId("proj-123")
                .repoUrl("https://github.com/team/repo.git")
                .provider("GITHUB")
                .accessToken("secret-token")
                .build();

        GitRepositoryResponseDto res = GitRepositoryResponseDto.builder()
                .id("repo-abc")
                .name("Team Project")
                .projectId("proj-123")
                .repoUrl("https://github.com/team/repo.git")
                .provider("GITHUB")
                .syncStatus("PENDING")
                .build();

        when(analyticsService.registerRepository(any(GitRepositoryRequestDto.class))).thenReturn(res);

        mockMvc.perform(post("/api/git/repositories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("repo-abc"))
                .andExpect(jsonPath("$.name").value("Team Project"))
                .andExpect(jsonPath("$.accessToken").doesNotExist()); // Security: token never returned
    }

    @Test
    @DisplayName("3. GET /api/git/repositories/{id}/analytics returns complete metrics")
    void testGetRepositoryAnalytics() throws Exception {
        String repoId = "repo-123";

        RepositoryAnalyticsResponseDto dto = RepositoryAnalyticsResponseDto.builder()
                .repositoryId(repoId)
                .repositoryName("Alpha Repo")
                .totalCommits(42)
                .nonMergeCommits(40)
                .mergeCommits(2)
                .totalLinesAdded(1200)
                .totalLinesDeleted(300)
                .netLinesTotal(900)
                .totalContributorsCount(3)
                .mappedContributorsCount(3)
                .unmatchedContributorsCount(0)
                .build();

        when(analyticsService.calculateAnalytics(eq(repoId), any(), any())).thenReturn(dto);

        mockMvc.perform(get("/api/git/repositories/" + repoId + "/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositoryId").value(repoId))
                .andExpect(jsonPath("$.totalCommits").value(42))
                .andExpect(jsonPath("$.mergeCommits").value(2))
                .andExpect(jsonPath("$.netLinesTotal").value(900));

        verify(securityEvaluator).checkAccess(repoId);
    }

    @Test
    @DisplayName("4. Unauthorized access triggers 403 Forbidden via SecurityEvaluator")
    void testUnauthorizedAccess() throws Exception {
        String repoId = "repo-forbidden";
        doThrow(new AccessDeniedException("Access denied")).when(securityEvaluator).checkAccess(repoId);

        try {
            mockMvc.perform(get("/api/git/repositories/" + repoId + "/analytics"));
            fail("Should have thrown AccessDeniedException");
        } catch (Exception ex) {
            assertTrue(ex.getCause() instanceof AccessDeniedException);
        }
    }
}

