package com.codeassess.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SessionTokenFilterTest {

    private SessionTokenFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new SessionTokenFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        SessionTokenRegistry.removeSession("test-token-1");
        SessionTokenRegistry.removeSession("test-token-2");
        SessionTokenRegistry.removeSession("bearer-token-1");
        SessionTokenRegistry.removeSession("invalidated-token");
    }

    // ==========================================
    // --- Token Resolution Tests ---
    // ==========================================

    @Test
    void doFilterWithoutAnyAuthHeadersPassesOriginalRequestToChain() throws ServletException, IOException {
        filter.doFilter(request, response, filterChain);

        ServletRequest chainedRequest = filterChain.getRequest();
        assertThat(chainedRequest).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterWithEmptyOrBlankXAuthTokenPassesOriginalRequest() throws ServletException, IOException {
        request.addHeader("X-Auth-Token", "   ");

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterWithEmptyOrBlankAuthorizationHeaderPassesOriginalRequest() throws ServletException, IOException {
        request.addHeader("Authorization", "   ");

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterWithNonBearerAuthorizationHeaderPassesOriginalRequest() throws ServletException, IOException {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterWithBearerPrefixOnlyWithoutTokenPassesOriginalRequest() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer    ");

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterWithUnregisteredXAuthTokenPassesOriginalRequest() throws ServletException, IOException {
        request.addHeader("X-Auth-Token", "unknown-token-xyz");

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterWithUnregisteredBearerTokenPassesOriginalRequest() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer unknown-bearer-token");

        filter.doFilter(request, response, filterChain);

        assertThat(filterChain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ==========================================
    // --- Successful Authentication Tests ---
    // ==========================================

    @Test
    void doFilterWithValidXAuthTokenAndSecurityContextEstablishesContextAndWrapsRequest() throws ServletException, IOException {
        MockHttpSession session = new MockHttpSession(null, "test-token-1");
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "student@codeassess.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
        SecurityContext secCtx = new SecurityContextImpl(auth);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, secCtx);

        SessionTokenRegistry.registerSession(session);
        request.addHeader("X-Auth-Token", "  test-token-1  ");

        filter.doFilter(request, response, filterChain);

        // SecurityContext was restored
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("student@codeassess.com");

        // Request was wrapped
        ServletRequest chainedRequest = filterChain.getRequest();
        assertThat(chainedRequest).isNotSameAs(request);
        assertThat(chainedRequest).isInstanceOf(HttpServletRequest.class);

        HttpServletRequest wrapped = (HttpServletRequest) chainedRequest;
        assertThat(wrapped.getSession()).isSameAs(session);
        assertThat(wrapped.getSession(false)).isSameAs(session);
        assertThat(wrapped.getSession(true)).isSameAs(session);
        assertThat(wrapped.getRequestedSessionId()).isEqualTo("test-token-1");
        assertThat(wrapped.isRequestedSessionIdValid()).isTrue();
    }

    @Test
    void doFilterWithValidBearerTokenCaseInsensitiveRestoresSecurityContext() throws ServletException, IOException {
        MockHttpSession session = new MockHttpSession(null, "bearer-token-1");
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "teacher@codeassess.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );
        SecurityContext secCtx = new SecurityContextImpl(auth);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, secCtx);

        SessionTokenRegistry.registerSession(session);
        request.addHeader("Authorization", "bearer bearer-token-1"); // lowercase "bearer"

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("teacher@codeassess.com");
    }

    @Test
    void doFilterWithUppercaseBearerPrefixRestoresSecurityContext() throws ServletException, IOException {
        MockHttpSession session = new MockHttpSession(null, "bearer-token-1");
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin@codeassess.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContext secCtx = new SecurityContextImpl(auth);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, secCtx);

        SessionTokenRegistry.registerSession(session);
        request.addHeader("Authorization", "BEARER bearer-token-1"); // uppercase "BEARER"

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("admin@codeassess.com");
    }

    @Test
    void doFilterWhenBothHeadersPresentPrefersXAuthToken() throws ServletException, IOException {
        MockHttpSession sessionA = new MockHttpSession(null, "test-token-1");
        Authentication authA = new UsernamePasswordAuthenticationToken("userA", "N/A", List.of());
        sessionA.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, new SecurityContextImpl(authA));

        MockHttpSession sessionB = new MockHttpSession(null, "test-token-2");
        Authentication authB = new UsernamePasswordAuthenticationToken("userB", "N/A", List.of());
        sessionB.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, new SecurityContextImpl(authB));

        SessionTokenRegistry.registerSession(sessionA);
        SessionTokenRegistry.registerSession(sessionB);

        request.addHeader("X-Auth-Token", "test-token-1");
        request.addHeader("Authorization", "Bearer test-token-2");

        filter.doFilter(request, response, filterChain);

        // X-Auth-Token takes precedence
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("userA");
        assertThat(((HttpServletRequest) filterChain.getRequest()).getSession()).isSameAs(sessionA);
    }

    @Test
    void doFilterWithValidTokenAndSessionWithoutSecurityContextDoesNotOverrideSecurityContext() throws ServletException, IOException {
        MockHttpSession session = new MockHttpSession(null, "test-token-1");
        // No SPRING_SECURITY_CONTEXT attribute
        SessionTokenRegistry.registerSession(session);
        request.addHeader("X-Auth-Token", "test-token-1");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // Request is still wrapped to bind session
        HttpServletRequest wrapped = (HttpServletRequest) filterChain.getRequest();
        assertThat(wrapped.getSession()).isSameAs(session);
    }

    @Test
    void doFilterWithSessionHavingNonSecurityContextAttributeDoesNotCastOrThrow() throws ServletException, IOException {
        MockHttpSession session = new MockHttpSession(null, "test-token-1");
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, "not-a-security-context");
        SessionTokenRegistry.registerSession(session);
        request.addHeader("X-Auth-Token", "test-token-1");

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ==========================================
    // --- Invalidation & Concurrency Tests ---
    // ==========================================

    @Test
    void doFilterWhenSessionThrowsIllegalStateExceptionEvictsTokenFromRegistryAndContinues() throws ServletException, IOException {
        HttpSession mockSession = mock(HttpSession.class);
        when(mockSession.getId()).thenReturn("invalidated-token");
        // Registration check passes
        when(mockSession.getLastAccessedTime()).thenReturn(System.currentTimeMillis());
        // Concurrent invalidation occurs when getAttribute is called inside filter
        when(mockSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .thenThrow(new IllegalStateException("Session invalidated concurrently"));

        SessionTokenRegistry.registerSession(mockSession);
        request.addHeader("X-Auth-Token", "invalidated-token");

        filter.doFilter(request, response, filterChain);

        // Session was evicted from registry
        assertThat(SessionTokenRegistry.getSession("invalidated-token")).isNull();
        // Filter fell through with original request
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    void doFilterPreservesPreExistingSecurityContextWhenNoTokenProvided() throws ServletException, IOException {
        Authentication preExisting = new UsernamePasswordAuthenticationToken("pre-auth-user", "N/A", List.of());
        SecurityContextHolder.getContext().setAuthentication(preExisting);

        filter.doFilter(request, response, filterChain);

        // Pre-existing authentication must be retained
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(preExisting);
        assertThat(filterChain.getRequest()).isSameAs(request);
    }

    @Test
    void doFilterDoesNotClearPreExistingAuthenticationWhenTokenIsInvalid() throws ServletException, IOException {
        Authentication preExisting = new UsernamePasswordAuthenticationToken("pre-auth-user", "N/A", List.of());
        SecurityContextHolder.getContext().setAuthentication(preExisting);

        request.addHeader("X-Auth-Token", "invalid-or-unregistered-token");
        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(preExisting);
    }

    // ==========================================
    // --- HeaderSessionHttpServletRequestWrapper Tests ---
    // ==========================================

    @Test
    void wrappedRequestReturnsFalseWhenSessionInvalidated() throws ServletException, IOException {
        HttpSession mockSession = mock(HttpSession.class);
        when(mockSession.getId()).thenReturn("test-token-1");
        Authentication auth = new UsernamePasswordAuthenticationToken("user", "N/A", List.of());
        when(mockSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .thenReturn(new SecurityContextImpl(auth));

        // When registry checks, it returns timestamp normally.
        // When downstream checks isRequestedSessionIdValid, session was invalidated concurrently.
        when(mockSession.getLastAccessedTime())
                .thenReturn(System.currentTimeMillis())
                .thenThrow(new IllegalStateException("Session invalidated"));

        SessionTokenRegistry.registerSession(mockSession);
        request.addHeader("X-Auth-Token", "test-token-1");

        filter.doFilter(request, response, filterChain);

        HttpServletRequest wrapped = (HttpServletRequest) filterChain.getRequest();
        assertThat(wrapped.isRequestedSessionIdValid()).isFalse();
    }

    @Test
    void wrapperWithNullSessionDelegatesToSuperRequest() throws Exception {
        // Test edge-case branch where session is null in HeaderSessionHttpServletRequestWrapper
        Class<?> wrapperClass = Class.forName("com.codeassess.security.SessionTokenFilter$HeaderSessionHttpServletRequestWrapper");
        Constructor<?> constructor = wrapperClass.getDeclaredConstructor(HttpServletRequest.class, HttpSession.class);
        constructor.setAccessible(true);

        request.setRequestedSessionId("super-session-id");
        request.setRequestedSessionIdValid(true);

        HttpServletRequest wrapper = (HttpServletRequest) constructor.newInstance(request, null);

        assertThat(wrapper.getSession(false)).isNull();
        assertThat(wrapper.getSession()).isNotNull(); // super.getSession(true) creates a mock session
        assertThat(wrapper.getRequestedSessionId()).isEqualTo("super-session-id");
        assertThat(wrapper.isRequestedSessionIdValid()).isTrue();

        request.setRequestedSessionIdValid(false);
        assertThat(wrapper.isRequestedSessionIdValid()).isFalse();
    }
}
