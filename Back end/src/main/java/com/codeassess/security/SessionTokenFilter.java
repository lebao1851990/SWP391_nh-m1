package com.codeassess.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that detects tab-isolated auth session tokens in headers (X-Auth-Token or Authorization: Bearer).
 * Restores the corresponding SecurityContext and wraps the request so calls to request.getSession()
 * bind to the specific tab's session, enabling parallel multi-role testing in separate browser tabs.
 */
@Component
public class SessionTokenFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null) {
            HttpSession session = SessionTokenRegistry.getSession(token);
            if (session != null) {
                try {
                    Object secCtxObj = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                    if (secCtxObj instanceof SecurityContext securityContext) {
                        SecurityContextHolder.setContext(securityContext);
                    }

                    // Wrap request so downstream components resolve this specific session
                    HeaderSessionHttpServletRequestWrapper wrappedRequest = 
                            new HeaderSessionHttpServletRequestWrapper(request, session);
                    filterChain.doFilter(wrappedRequest, response);
                    return;
                } catch (IllegalStateException e) {
                    // Session was invalidated
                    SessionTokenRegistry.removeSession(token);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String xAuthToken = request.getHeader("X-Auth-Token");
        if (xAuthToken != null && !xAuthToken.isBlank()) {
            return xAuthToken.trim();
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authHeader.substring(7).trim();
            if (!token.isBlank()) {
                return token;
            }
        }

        return null;
    }

    private static class HeaderSessionHttpServletRequestWrapper extends HttpServletRequestWrapper {
        private final HttpSession session;

        public HeaderSessionHttpServletRequestWrapper(HttpServletRequest request, HttpSession session) {
            super(request);
            this.session = session;
        }

        @Override
        public HttpSession getSession(boolean create) {
            if (session != null) {
                return session;
            }
            return super.getSession(create);
        }

        @Override
        public HttpSession getSession() {
            return getSession(true);
        }

        @Override
        public String getRequestedSessionId() {
            if (session != null) {
                return session.getId();
            }
            return super.getRequestedSessionId();
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            if (session != null) {
                try {
                    session.getLastAccessedTime();
                    return true;
                } catch (IllegalStateException e) {
                    return false;
                }
            }
            return super.isRequestedSessionIdValid();
        }
    }
}
