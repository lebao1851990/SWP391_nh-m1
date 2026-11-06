package com.codeassess.git;

import com.codeassess.git.config.GitSecurityEvaluator;
import com.codeassess.git.model.GitRepositoryModel;
import com.codeassess.git.service.GitStorageService;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GitSecurityEvaluatorTest {

    private GitStorageService storageService;
    private GitSecurityEvaluator securityEvaluator;

    @Mock
    private UserRepository userRepository;

    private final String repoId = "repo-security-test";

    @BeforeEach
    void setUp() {
        storageService = new GitStorageService();
        storageService.clearAll();
        securityEvaluator = new GitSecurityEvaluator(storageService, userRepository);

        // Repo belongs to team Alpha with member "user-alice"
        storageService.saveRepository(GitRepositoryModel.builder()
                .id(repoId)
                .name("Alpha Project")
                .projectId("proj-alpha")
                .memberUserIds(Set.of("user-alice"))
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("1. Teachers have global access to repositories")
    void testTeacherAccess() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("teacher1", "pass", List.of(new SimpleGrantedAuthority("ROLE_TEACHER")))
        );

        assertTrue(securityEvaluator.canAccessRepository(repoId));
        assertDoesNotThrow(() -> securityEvaluator.checkAccess(repoId));
    }

    @Test
    @DisplayName("2. Admins have global access to repositories")
    void testAdminAccess() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin1", "pass", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        assertTrue(securityEvaluator.canAccessRepository(repoId));
        assertDoesNotThrow(() -> securityEvaluator.checkAccess(repoId));
    }

    @Test
    @DisplayName("3. Students in repository member list are granted access")
    void testStudentMemberAccess() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@university.edu", "pass", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")))
        );

        User alice = User.builder().id("user-alice").email("alice@university.edu").role(Role.STUDENT).build();
        when(userRepository.findAll()).thenReturn(List.of(alice));

        assertTrue(securityEvaluator.canAccessRepository(repoId));
        assertDoesNotThrow(() -> securityEvaluator.checkAccess(repoId));
    }

    @Test
    @DisplayName("4. Students NOT in repository member list are denied access with 403 AccessDeniedException")
    void testStudentNonMemberAccessDenied() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("mallory@university.edu", "pass", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")))
        );

        User mallory = User.builder().id("user-mallory").email("mallory@university.edu").role(Role.STUDENT).build();
        when(userRepository.findAll()).thenReturn(List.of(mallory));

        assertFalse(securityEvaluator.canAccessRepository(repoId));
        assertThrows(AccessDeniedException.class, () -> securityEvaluator.checkAccess(repoId));
    }
}

