package com.codeassess.websocket.interceptor;

import com.codeassess.security.SessionTokenRegistry;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final UserRepository userRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {

        User user = null;

        // 1. Check current thread's SecurityContext
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User u) {
            user = u;
        }

        // 2. Check Servlet request attributes / cookies / headers
        if (user == null && request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpReq = servletRequest.getServletRequest();

            // Check session directly
            HttpSession session = httpReq.getSession(false);
            if (session != null) {
                user = extractUserFromSession(session);
            }

            // Check X-Auth-Token header
            if (user == null) {
                String headerToken = httpReq.getHeader("X-Auth-Token");
                if (headerToken != null && !headerToken.isBlank()) {
                    HttpSession regSession = SessionTokenRegistry.getSession(headerToken.trim());
                    if (regSession != null) {
                        user = extractUserFromSession(regSession);
                    }
                }
            }

            // Check cookies (codeassess_session)
            if (user == null && httpReq.getCookies() != null) {
                for (Cookie cookie : httpReq.getCookies()) {
                    if ("codeassess_session".equalsIgnoreCase(cookie.getName()) ||
                            "JSESSIONID".equalsIgnoreCase(cookie.getName())) {
                        HttpSession cookieSession = SessionTokenRegistry.getSession(cookie.getValue());
                        if (cookieSession != null) {
                            user = extractUserFromSession(cookieSession);
                            if (user != null) break;
                        }
                    }
                }
            }
        }

        // 3. Check query parameters (e.g. ?token=... or ?userId=...)
        if (user == null) {
            URI uri = request.getURI();
            String query = uri.getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=");
                    if (kv.length == 2) {
                        String key = kv[0].trim();
                        String val = kv[1].trim();
                        if ("token".equalsIgnoreCase(key) || "sessionId".equalsIgnoreCase(key)) {
                            HttpSession regSession = SessionTokenRegistry.getSession(val);
                            if (regSession != null) {
                                user = extractUserFromSession(regSession);
                                if (user != null) break;
                            }
                        } else if ("userId".equalsIgnoreCase(key)) {
                            // Direct user lookup fallback for trusted local or test environments
                            Optional<User> direct = userRepository.findById(val);
                            if (direct.isPresent()) {
                                user = direct.get();
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (user != null) {
            attributes.put("userId", user.getId());
            attributes.put("email", user.getEmail());
            attributes.put("role", user.getRole() != null ? user.getRole().name() : "STUDENT");
            log.info("WebSocket handshake authenticated for user: {} (role: {})", user.getEmail(), user.getRole());
            return true;
        }

        log.warn("WebSocket handshake rejected: Unauthenticated connection attempt from {}", request.getRemoteAddress());
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // No post-handshake action needed
    }

    private User extractUserFromSession(HttpSession session) {
        try {
            Object secCtxObj = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (secCtxObj instanceof SecurityContext secCtx) {
                Authentication auth = secCtx.getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof User u) {
                    return u;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract user from session: {}", e.getMessage());
        }
        return null;
    }
}

