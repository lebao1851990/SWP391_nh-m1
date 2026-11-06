package com.codeassess.websocket;

import com.codeassess.security.SessionTokenRegistry;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import com.codeassess.websocket.interceptor.WebSocketHandshakeInterceptor;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WebSocketHandshakeInterceptorTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WebSocketHandler wsHandler;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private ServletServerHttpRequest serverRequest;

    @Mock
    private HttpServletRequest httpServletRequest;

    @Mock
    private HttpSession httpSession;

    private WebSocketHandshakeInterceptor interceptor;
    private User testUser;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketHandshakeInterceptor(userRepository);
        testUser = User.builder()
                .id("user-test-123")
                .email("test@university.edu")
                .role(Role.TEACHER)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Handshake succeeds when user is authenticated in SecurityContext")
    void testHandshakeWithSecurityContext() throws Exception {
        SecurityContext secCtx = new SecurityContextImpl();
        secCtx.setAuthentication(new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities()));
        SecurityContextHolder.setContext(secCtx);

        lenient().when(serverRequest.getURI()).thenReturn(new URI("http://localhost:8081/api/ws"));
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(serverRequest, response, wsHandler, attributes);

        assertTrue(result, "Handshake should succeed for authenticated SecurityContext");
        assertEquals("user-test-123", attributes.get("userId"));
        assertEquals("test@university.edu", attributes.get("email"));
        assertEquals("TEACHER", attributes.get("role"));
    }

    @Test
    @DisplayName("Handshake succeeds via query parameter ?userId= for direct local/test fallback")
    void testHandshakeWithQueryParam() throws Exception {
        when(serverRequest.getURI()).thenReturn(new URI("http://localhost:8081/api/ws?userId=user-test-123"));
        when(serverRequest.getServletRequest()).thenReturn(httpServletRequest);
        when(httpServletRequest.getSession(false)).thenReturn(null);
        when(userRepository.findById("user-test-123")).thenReturn(Optional.of(testUser));

        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(serverRequest, response, wsHandler, attributes);

        assertTrue(result);
        assertEquals("user-test-123", attributes.get("userId"));
    }

    @Test
    @DisplayName("Handshake rejects unauthenticated connection with HTTP 401")
    void testHandshakeRejectsUnauthenticated() throws Exception {
        when(serverRequest.getURI()).thenReturn(new URI("http://localhost:8081/api/ws"));
        when(serverRequest.getServletRequest()).thenReturn(httpServletRequest);
        when(httpServletRequest.getSession(false)).thenReturn(null);

        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(serverRequest, response, wsHandler, attributes);

        assertFalse(result, "Handshake must be rejected for unauthenticated request");
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
