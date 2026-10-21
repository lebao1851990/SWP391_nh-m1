package com.codeassess.security;

import com.codeassess.auth.service.PasswordResetTokenStore;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Validates that authenticated HTTP sessions were established AFTER the user's
 * most recent password reset event.
 *
 * Implements Durable Session Invalidation:
 * 1. Checks fast Redis cache first.
 * 2. If Redis key is missing (e.g. Redis restart / data loss / cache expiration),
 *    falls back to persistent MySQL database (`user.getUpdatedAt()`).
 * 3. If database or cache lookup fails, fails closed (HTTP 503) to prevent
 *    compromised sessions from bypassing security checks.
 */
@Slf4j
@Component
public class SessionPasswordAgeFilter extends OncePerRequestFilter {

    public static final String SESSION_AUTH_TIME_KEY = "CODEASSESS_SESSION_AUTH_TIME";
    private final PasswordResetTokenStore tokenStore;
    private final UserRepository userRepository;

    public SessionPasswordAgeFilter(
            @org.springframework.beans.factory.annotation.Autowired(required = false) PasswordResetTokenStore tokenStore,
            @org.springframework.beans.factory.annotation.Autowired(required = false) UserRepository userRepository
    ) {
        this.tokenStore = tokenStore;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // In lightweight WebMvcTest slices where auth beans are omitted, pass through safely
        if (tokenStore == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && auth.getName() != null && !auth.getName().isBlank() && !"anonymousUser".equals(auth.getName())) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                String email = auth.getName();
                Long passwordChangedAtMillis = null;

                try {
                    // Step 1: Query fast Redis cache
                    passwordChangedAtMillis = tokenStore.getPasswordChangedAt(email);

                    // Step 2: Durable Fallback - If Redis has no record (data loss/restart/outage),
                    // query the persistent MySQL database to retrieve the ground truth timestamp
                    if (passwordChangedAtMillis == null) {
                        Optional<User> userOpt = userRepository.findByEmail(email);
                        if (userOpt.isPresent()) {
                            User user = userOpt.get();
                            if (user.getPasswordChangedAt() != null) {
                                passwordChangedAtMillis = user.getPasswordChangedAt()
                                        .atZone(ZoneId.systemDefault())
                                        .toInstant()
                                        .toEpochMilli();
                                // Self-healing: Backfill Redis cache so subsequent requests avoid DB overhead
                                try {
                                    tokenStore.recordPasswordChanged(email, Instant.ofEpochMilli(passwordChangedAtMillis));
                                } catch (Exception e) {
                                    log.debug("Non-fatal error backfilling Redis passwordChanged timestamp: {}", e.getMessage());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to verify durable session age for user {}: {}", email, e.getMessage());
                    // Fail-Closed: If we cannot verify session validity due to DB/store failure, reject request
                    response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":\"AUTH_STORE_UNAVAILABLE\",\"message\":\"Authentication verification service is temporarily unavailable.\"}");
                    return;
                }

                if (passwordChangedAtMillis != null) {
                    Long sessionAuthTime = (Long) session.getAttribute(SESSION_AUTH_TIME_KEY);
                    long authTime = sessionAuthTime != null ? sessionAuthTime : session.getCreationTime();

                    // If session was created or authenticated before the password changed, revoke it immediately
                    if (authTime < passwordChangedAtMillis) {
                        log.warn("Revoking outdated session for user {} (authTime: {}, passwordChangedAt: {})",
                                email, authTime, passwordChangedAtMillis);

                        session.invalidate();
                        SecurityContextHolder.clearContext();

                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"code\":\"SESSION_REVOKED\",\"message\":\"Session expired due to password change. Please log in again.\"}");
                        return;
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
