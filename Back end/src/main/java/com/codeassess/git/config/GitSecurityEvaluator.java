package com.codeassess.git.config;

import com.codeassess.git.model.GitRepositoryModel;
import com.codeassess.git.service.GitStorageService;
import com.codeassess.security.CustomUserDetailsService;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("gitSecurity")
@RequiredArgsConstructor
public class GitSecurityEvaluator {

    private final GitStorageService storageService;
    private final UserRepository userRepository;

    public boolean canAccessRepository(String repositoryId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        // Admins and Teachers have global visibility across repositories
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String role = ga.getAuthority();
            if ("ROLE_ADMIN".equals(role) || "ROLE_TEACHER".equals(role)) {
                return true;
            }
        }

        // Students must be designated members of the repository's project
        String currentPrincipal = auth.getName();
        Optional<User> currentUser = userRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(currentPrincipal))
                .findFirst();

        if (currentUser.isEmpty()) {
            return false;
        }

        String userId = currentUser.get().getId();
        Optional<GitRepositoryModel> repoOpt = storageService.findRepositoryById(repositoryId);
        if (repoOpt.isEmpty()) {
            return false;
        }

        GitRepositoryModel repo = repoOpt.get();
        return repo.getMemberUserIds() != null && repo.getMemberUserIds().contains(userId);
    }

    public void checkAccess(String repositoryId) {
        if (!canAccessRepository(repositoryId)) {
            throw new AccessDeniedException("Access denied: You do not have permission to view or manage this repository's analytics.");
        }
    }

    public boolean isTeacherOrAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String role = ga.getAuthority();
            if ("ROLE_ADMIN".equals(role) || "ROLE_TEACHER".equals(role)) {
                return true;
            }
        }
        return false;
    }

    public String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        return userRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(auth.getName()))
                .map(User::getId)
                .findFirst()
                .orElse(null);
    }
}

