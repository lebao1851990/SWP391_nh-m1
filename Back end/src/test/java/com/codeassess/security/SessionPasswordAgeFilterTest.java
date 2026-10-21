package com.codeassess.security;

import com.codeassess.auth.service.PasswordResetTokenStore;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionPasswordAgeFilterTest {

    @Mock
    private PasswordResetTokenStore tokenStore;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    @Mock
    private FilterChain filterChain;

    private SessionPasswordAgeFilter filter;
    private final String userEmail = "student@codeassess.edu.vn";
    private User testUser;

    @BeforeEach
    void setUp() {
        filter = new SessionPasswordAgeFilter(tokenStore, userRepository);
        SecurityContextHolder.clearContext();

        testUser = User.builder()
                .id("u-12345")
                .email(userEmail)
                .fullName("Test Student")
                .role(Role.STUDENT)
                .passwordHash("hashed")
                .updatedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(1200000L), ZoneId.systemDefault()))
                .passwordChangedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(1000000L), ZoneId.systemDefault()))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Unauthenticated request passes through filter chain without session check")
    void testUnauthenticatedRequest_PassesFilterChain() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(tokenStore);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Authenticated session created BEFORE password reset is rejected with 401 and invalidated (Redis hit)")
    void testOutdatedSession_RejectedWith401AndInvalidated_RedisHit() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userEmail, "pwd", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        when(request.getSession(false)).thenReturn(session);

        long tPasswordReset = 1000000L;
        long tSessionCreated = 900000L; // Session was created before reset

        when(tokenStore.getPasswordChangedAt(userEmail)).thenReturn(tPasswordReset);
        when(session.getAttribute(SessionPasswordAgeFilter.SESSION_AUTH_TIME_KEY)).thenReturn(tSessionCreated);

        StringWriter out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));

        filter.doFilterInternal(request, response, filterChain);

        verify(session).invalidate();
        assertNull(SecurityContextHolder.getContext().getAuthentication(), "Security context must be cleared");
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
        assertTrue(out.toString().contains("SESSION_REVOKED"));
    }

    @Test
    @DisplayName("Scope A: Durable session invalidation survives Redis data loss by checking persistent MySQL database")
    void testOutdatedSession_RejectedAfterRedisDataLoss_DurableDatabaseFallback() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userEmail, "pwd", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        when(request.getSession(false)).thenReturn(session);

        // Redis cache missed/lost (returns null)
        when(tokenStore.getPasswordChangedAt(userEmail)).thenReturn(null);

        // Fallback to MySQL returns updatedAt timestamp = 1000000L
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(testUser));

        long tSessionCreated = 850000L; // Stale session created before DB update
        when(session.getAttribute(SessionPasswordAgeFilter.SESSION_AUTH_TIME_KEY)).thenReturn(tSessionCreated);

        StringWriter out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));

        filter.doFilterInternal(request, response, filterChain);

        // Must still be rejected even without Redis!
        verify(session).invalidate();
        assertNull(SecurityContextHolder.getContext().getAuthentication(), "Security context must be cleared via DB check");
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
        assertTrue(out.toString().contains("SESSION_REVOKED"));

        // Verify that Redis was backfilled with the durable DB timestamp
        verify(tokenStore).recordPasswordChanged(eq(userEmail), any(Instant.class));
    }

    @Test
    @DisplayName("Authenticated session created AFTER password reset is accepted and passes filter chain")
    void testNewSessionAfterReset_AcceptedAndPassesFilterChain() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userEmail, "pwd", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        when(request.getSession(false)).thenReturn(session);

        long tPasswordReset = 1000000L;
        long tSessionCreated = 1100000L; // Session created after password reset

        when(tokenStore.getPasswordChangedAt(userEmail)).thenReturn(tPasswordReset);
        when(session.getAttribute(SessionPasswordAgeFilter.SESSION_AUTH_TIME_KEY)).thenReturn(tSessionCreated);

        filter.doFilterInternal(request, response, filterChain);

        verify(session, never()).invalidate();
        assertNotNull(SecurityContextHolder.getContext().getAuthentication(), "Security context must remain active");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Profile update (updated_at changes) does NOT revoke existing session when password_changed_at is unchanged/null")
    void testProfileUpdate_DoesNotRevokeActiveSession() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userEmail, "pwd", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        when(request.getSession(false)).thenReturn(session);
        when(tokenStore.getPasswordChangedAt(userEmail)).thenReturn(null);

        // User had profile updated at 1500000L, but passwordChangedAt is NULL (never reset)
        User profileUpdatedUser = User.builder()
                .id("u-12345")
                .email(userEmail)
                .fullName("Renamed Student")
                .role(Role.STUDENT)
                .passwordHash("hashed")
                .updatedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(1500000L), ZoneId.systemDefault()))
                .passwordChangedAt(null)
                .build();
        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(profileUpdatedUser));

        filter.doFilterInternal(request, response, filterChain);

        verify(session, never()).invalidate();
        assertNotNull(SecurityContextHolder.getContext().getAuthentication(), "Session must remain active across profile updates");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Newly authenticated session created immediately after password reset remains valid despite seconds truncation")
    void testNewlyAuthenticatedSession_ValidWithTimestampPrecision() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userEmail, "pwd", List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        when(request.getSession(false)).thenReturn(session);

        // Password reset at 10:00:00.000 (1000000000ms), session logged in at 10:00:00.250 (1000000250ms)
        long tPasswordReset = 1000000000L;
        long tSessionAuth = 1000000250L;

        when(tokenStore.getPasswordChangedAt(userEmail)).thenReturn(tPasswordReset);
        when(session.getAttribute(SessionPasswordAgeFilter.SESSION_AUTH_TIME_KEY)).thenReturn(tSessionAuth);

        filter.doFilterInternal(request, response, filterChain);

        verify(session, never()).invalidate();
        assertNotNull(SecurityContextHolder.getContext().getAuthentication(), "New session must be valid");
        verify(filterChain).doFilter(request, response);
    }
}
