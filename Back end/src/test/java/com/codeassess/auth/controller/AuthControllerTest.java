package com.codeassess.auth.controller;

import com.codeassess.auth.dto.request.ForgotPasswordRequest;
import com.codeassess.auth.dto.request.LoginRequest;
import com.codeassess.auth.dto.request.RegisterRequest;
import com.codeassess.auth.dto.request.ResetPasswordRequest;
import com.codeassess.auth.dto.response.LoginResponse;
import com.codeassess.auth.service.AuthService;
import com.codeassess.common.exception.ConflictException;
import com.codeassess.common.exception.GlobalExceptionHandler;
import com.codeassess.security.CustomUserDetailsService;
import com.codeassess.security.SecurityConfig;
import com.codeassess.security.SessionTokenRegistry;
import com.codeassess.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthController authController;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    // ==========================================
    // --- Login Tests ---
    // ==========================================

    @Test
    void loginWithValidCredentialsReturnsUserSessionAndRoleCookie() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("student@codeassess.com");
        request.setPassword("password123");

        LoginResponse.UserSessionDto sessionDto = LoginResponse.UserSessionDto.builder()
                .id("sub_123")
                .email("student@codeassess.com")
                .role("STUDENT")
                .build();
        LoginResponse response = LoginResponse.builder()
                .user(sessionDto)
                .token("test-session-id-456")
                .build();

        when(authService.login(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("codeassess_role"))
                .andExpect(cookie().value("codeassess_role", "STUDENT"))
                .andExpect(cookie().path("codeassess_role", "/"))
                .andExpect(cookie().maxAge("codeassess_role", 86400))
                .andExpect(cookie().httpOnly("codeassess_role", false))
                .andExpect(jsonPath("$.user.email").value("student@codeassess.com"))
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.token").value("test-session-id-456"));

        verify(authService).login(any(), any());
    }

    @Test
    void loginWithTeacherCredentialsSetsTeacherRoleCookie() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("teacher@codeassess.com");
        request.setPassword("teacherpass123");

        LoginResponse.UserSessionDto sessionDto = LoginResponse.UserSessionDto.builder()
                .id("tea_456")
                .email("teacher@codeassess.com")
                .role("TEACHER")
                .build();
        LoginResponse response = LoginResponse.builder()
                .user(sessionDto)
                .token("teacher-session-token-789")
                .build();

        when(authService.login(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("codeassess_role"))
                .andExpect(cookie().value("codeassess_role", "TEACHER"))
                .andExpect(cookie().path("codeassess_role", "/"))
                .andExpect(cookie().maxAge("codeassess_role", 86400))
                .andExpect(cookie().httpOnly("codeassess_role", false))
                .andExpect(jsonPath("$.user.role").value("TEACHER"))
                .andExpect(jsonPath("$.token").value("teacher-session-token-789"));
    }

    @Test
    void loginWithInvalidCredentialsReturns401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("student@codeassess.com");
        request.setPassword("wrongpassword");

        when(authService.login(any(), any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void loginWithDisabledAccountReturns401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("disabled@codeassess.com");
        request.setPassword("password123");

        when(authService.login(any(), any())).thenThrow(new DisabledException("Account disabled"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void loginWithInvalidPayloadReturns400() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("not-an-email");
        request.setPassword("");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void loginWithBlankEmailReturns400() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("   ");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void loginWithBlankPasswordReturns400() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("valid@codeassess.com");
        request.setPassword("   ");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void loginWithMalformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ invalid json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void directLoginMethodSetsExpectedRoleCookieAttributes() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@codeassess.com");
        request.setPassword("secret");

        LoginResponse response = LoginResponse.builder()
                .user(LoginResponse.UserSessionDto.builder().id("adm_1").email("admin@codeassess.com").role("ADMIN").build())
                .token("adm-token-1")
                .build();

        when(authService.login(any(), any())).thenReturn(response);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        ResponseEntity<LoginResponse> result = authController.login(request, httpRequest, httpResponse);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(response);
        assertThat(httpResponse.getCookie("codeassess_role")).isNotNull();
        assertThat(httpResponse.getCookie("codeassess_role").getValue()).isEqualTo("ADMIN");
        assertThat(httpResponse.getCookie("codeassess_role").getPath()).isEqualTo("/");
        assertThat(httpResponse.getCookie("codeassess_role").getMaxAge()).isEqualTo(86400);
        assertThat(httpResponse.getCookie("codeassess_role").isHttpOnly()).isFalse();
    }

    // ==========================================
    // --- Register Tests ---
    // ==========================================

    @Test
    void registerWithValidDataReturns201() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "John Doe", "john@student.com", "Password123!", "Password123!", "student", true
        );

        doNothing().when(authService).register(any());

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(authService).register(any());
    }

    @Test
    void registerWithValidTeacherRoleReturns201() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Jane Teacher", "jane@teacher.com", "Password123!", "Password123!", "teacher", true
        );

        doNothing().when(authService).register(any());

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(authService).register(any());
    }

    @Test
    void registerWithDuplicateEmailReturns409() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "John Doe", "john@student.com", "Password123!", "Password123!", "student", true
        );

        doThrow(new ConflictException("Email already exists"))
                .when(authService).register(any());

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void registerWithInvalidDataReturns400() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "", "not-an-email", "short", "short", "invalidRole", true
        );

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void registerWithShortPasswordReturns400() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Valid Name", "valid@student.com", "Pass123", "Pass123", "student", true
        );

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void registerWithInvalidRolePatternReturns400() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Valid Name", "valid@student.com", "Password123!", "Password123!", "admin", true
        );

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void registerWhenServiceThrowsIllegalArgumentExceptionReturns400() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Valid Name", "valid@student.com", "Password123!", "Password123!", "student", true
        );

        doThrow(new IllegalArgumentException("Cannot register as ADMIN"))
                .when(authService).register(any());

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Cannot register as ADMIN"));
    }

    // ==========================================
    // --- Logout Tests ---
    // ==========================================

    @Test
    void logoutViaMockMvcClearsSessionAndCookiesReturns200() throws Exception {
        MockHttpSession mockSession = new MockHttpSession();

        mockMvc.perform(post("/api/auth/logout")
                .header("X-Auth-Token", "mock-token-to-remove")
                .session(mockSession))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("codeassess_session"))
                .andExpect(cookie().maxAge("codeassess_session", 0))
                .andExpect(cookie().path("codeassess_session", "/"))
                .andExpect(cookie().exists("codeassess_role"))
                .andExpect(cookie().maxAge("codeassess_role", 0))
                .andExpect(cookie().path("codeassess_role", "/"));
    }

    @Test
    void logoutViaMockMvcWithoutTokenOrSessionClearsCookiesReturns200() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("codeassess_session"))
                .andExpect(cookie().maxAge("codeassess_session", 0))
                .andExpect(cookie().path("codeassess_session", "/"))
                .andExpect(cookie().exists("codeassess_role"))
                .andExpect(cookie().maxAge("codeassess_role", 0))
                .andExpect(cookie().path("codeassess_role", "/"));
    }

    @Test
    void directLogoutMethodWithTokenAndSessionInvalidatesAndClearsCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        request.addHeader("X-Auth-Token", "active-tab-token");

        ResponseEntity<Void> result = authController.logout(request, response);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(session.isInvalid()).isTrue();
        assertThat(response.getCookies()).hasSize(2);
        assertThat(response.getCookie("codeassess_session").getMaxAge()).isEqualTo(0);
        assertThat(response.getCookie("codeassess_role").getMaxAge()).isEqualTo(0);
    }

    @Test
    void directLogoutMethodWithoutTokenAndSessionClearsCookiesGracefully() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<Void> result = authController.logout(request, response);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getCookies()).hasSize(2);
        assertThat(response.getCookie("codeassess_session").getMaxAge()).isEqualTo(0);
        assertThat(response.getCookie("codeassess_role").getMaxAge()).isEqualTo(0);
    }

    @Test
    void directLogoutMethodRemovesTokenFromSessionTokenRegistry() {
        MockHttpSession session = new MockHttpSession();
        SessionTokenRegistry.registerSession(session);
        assertThat(SessionTokenRegistry.getSession(session.getId())).isNotNull();

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setSession(session);
        request.addHeader("X-Auth-Token", session.getId());

        authController.logout(request, response);

        assertThat(SessionTokenRegistry.getSession(session.getId())).isNull();
    }

    // ==========================================
    // --- Forgot Password Tests ---
    // ==========================================

    @Test
    void forgotPasswordReturns200WithAntiEnumerationMessage() throws Exception {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .identity("student@codeassess.com")
                .build();

        doNothing().when(authService).forgotPassword(any());

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Nếu tài khoản tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi."));

        verify(authService).forgotPassword(any());
    }

    @Test
    void forgotPasswordWithEmailAliasReturns200WithAntiEnumerationMessage() throws Exception {
        doNothing().when(authService).forgotPassword(any());

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"alias-user@codeassess.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Nếu tài khoản tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi."));

        verify(authService).forgotPassword(any());
    }

    @Test
    void forgotPasswordWhenAccountDoesNotExistStillReturns200AntiEnumerationMessage() throws Exception {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .identity("nonexistent@codeassess.com")
                .build();

        // AuthService handles non-existent silently (anti-enumeration)
        doNothing().when(authService).forgotPassword(any());

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Nếu tài khoản tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi."));
    }

    @Test
    void forgotPasswordWhenServiceThrowsUnexpectedExceptionReturns500() throws Exception {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .identity("error@codeassess.com")
                .build();

        doThrow(new RuntimeException("Mail service unexpected error"))
                .when(authService).forgotPassword(any());

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
    }

    // ==========================================
    // --- Reset Password Tests ---
    // ==========================================

    @Test
    void resetPasswordWithValidPayloadReturns200() throws Exception {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("valid_token_123")
                .password("NewPassword123!")
                .confirmPassword("NewPassword123!")
                .build();

        doNothing().when(authService).resetPassword(any());

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Mật khẩu đã được cập nhật thành công. Vui lòng đăng nhập lại."));

        verify(authService).resetPassword(any());
    }

    @Test
    void resetPasswordWithJsonAliasesReturns200() throws Exception {
        doNothing().when(authService).resetPassword(any());

        String jsonPayload = "{\"token\": \"alias_token_456\", \"newPassword\": \"NewPassword123!\", \"confirm_password\": \"NewPassword123!\"}";

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Mật khẩu đã được cập nhật thành công. Vui lòng đăng nhập lại."));

        verify(authService).resetPassword(any());
    }

    @Test
    void resetPasswordWithInvalidPayloadReturns400() throws Exception {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("") // blank token
                .password("short") // < 8 characters
                .confirmPassword("short")
                .build();

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void resetPasswordWithBlankPasswordReturns400() throws Exception {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("valid_token")
                .password("   ")
                .confirmPassword("   ")
                .build();

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void resetPasswordWhenServiceThrowsExpiredTokenExceptionReturns400() throws Exception {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("expired_token")
                .password("ValidNewPassword123!")
                .confirmPassword("ValidNewPassword123!")
                .build();

        doThrow(new IllegalArgumentException("Password reset token has expired"))
                .when(authService).resetPassword(any());

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Password reset token has expired"));

        verify(authService).resetPassword(any());
    }

    @Test
    void resetPasswordWhenPasswordsDoNotMatchReturns400() throws Exception {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("valid_token")
                .password("ValidNewPassword123!")
                .confirmPassword("MismatchPassword123!")
                .build();

        doThrow(new IllegalArgumentException("Passwords do not match"))
                .when(authService).resetPassword(any());

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Passwords do not match"));
    }

    @Test
    void resetPasswordWhenTokenAlreadyUsedReturns400() throws Exception {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("used_token")
                .password("ValidNewPassword123!")
                .confirmPassword("ValidNewPassword123!")
                .build();

        doThrow(new IllegalArgumentException("This password reset token has already been used"))
                .when(authService).resetPassword(any());

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("This password reset token has already been used"));
    }

    @Test
    void resetPasswordWhenTokenNotFoundReturns400() throws Exception {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("unknown_token")
                .password("ValidNewPassword123!")
                .confirmPassword("ValidNewPassword123!")
                .build();

        doThrow(new IllegalArgumentException("Invalid or expired password reset token"))
                .when(authService).resetPassword(any());

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid or expired password reset token"));
    }

    @Test
    void resetPasswordWhenServiceThrowsUnexpectedExceptionReturns500() throws Exception {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("valid_token")
                .password("ValidNewPassword123!")
                .confirmPassword("ValidNewPassword123!")
                .build();

        doThrow(new RuntimeException("Unexpected database error"))
                .when(authService).resetPassword(any());

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
    }
}
