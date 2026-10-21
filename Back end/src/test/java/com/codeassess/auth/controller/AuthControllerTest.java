package com.codeassess.auth.controller;

import com.codeassess.auth.dto.request.LoginRequest;
import com.codeassess.auth.dto.response.LoginResponse;
import com.codeassess.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private com.codeassess.auth.service.PasswordResetService passwordResetService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginWithValidCredentialsReturnsUserSession() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("student@codeassess.com");
        request.setPassword("password123");

        LoginResponse.UserSessionDto sessionDto = LoginResponse.UserSessionDto.builder()
                .id("sub_123")
                .email("student@codeassess.com")
                .role("STUDENT")
                .build();
        LoginResponse response = LoginResponse.builder().user(sessionDto).build();

        Mockito.when(authService.login(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("student@codeassess.com"))
                .andExpect(jsonPath("$.user.role").value("STUDENT"));
    }

    @Test
    void loginWithInvalidCredentialsReturns401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("student@codeassess.com");
        request.setPassword("wrongpassword");

        Mockito.when(authService.login(any(), any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/metrics")) // Non-existent but matches /api/** rule
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCurrentUserWhenUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "student@codeassess.com", roles = "STUDENT")
    void studentCannotAccessTeacherEndpointsReturns403() throws Exception {
        mockMvc.perform(get("/api/teacher/exams"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerWithValidDataReturns201() throws Exception {
        com.codeassess.auth.dto.request.RegisterRequest request = new com.codeassess.auth.dto.request.RegisterRequest(
                "John Doe", "john@student.com", "Password123!", "Password123!", "student", true
        );

        Mockito.doNothing().when(authService).register(any());

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void registerWithDuplicateEmailReturns409() throws Exception {
        com.codeassess.auth.dto.request.RegisterRequest request = new com.codeassess.auth.dto.request.RegisterRequest(
                "John Doe", "john@student.com", "Password123!", "Password123!", "student", true
        );

        Mockito.doThrow(new com.codeassess.common.exception.ConflictException("Email already exists"))
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
        com.codeassess.auth.dto.request.RegisterRequest request = new com.codeassess.auth.dto.request.RegisterRequest(
                "", "not-an-email", "short", "short", "invalidRole", true
        );

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPasswordReturns200WithNeutralMessage() throws Exception {
        com.codeassess.auth.dto.request.ForgotPasswordRequest request = 
                com.codeassess.auth.dto.request.ForgotPasswordRequest.builder()
                        .email("student@codeassess.edu.vn")
                        .build();

        com.codeassess.auth.dto.response.ForgotPasswordResponse response = 
                com.codeassess.auth.dto.response.ForgotPasswordResponse.builder()
                        .message("If an account exists with this email, a verification code has been sent.")
                        .build();

        Mockito.when(passwordResetService.requestPasswordReset(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If an account exists with this email, a verification code has been sent."));
    }

    @Test
    void resendOtpReturns200WithCooldown() throws Exception {
        com.codeassess.auth.dto.request.ResendOtpRequest request =
                com.codeassess.auth.dto.request.ResendOtpRequest.builder()
                        .email("student@codeassess.edu.vn")
                        .build();

        com.codeassess.auth.dto.response.ResendOtpResponse response =
                com.codeassess.auth.dto.response.ResendOtpResponse.builder()
                        .message("If an account exists with this email, a new verification code has been sent.")
                        .cooldownSeconds(60)
                        .build();

        Mockito.when(passwordResetService.resendOtp(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/resend-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If an account exists with this email, a new verification code has been sent."))
                .andExpect(jsonPath("$.cooldownSeconds").value(60));
    }

    @Test
    void verifyOtpReturns200WithResetToken() throws Exception {
        com.codeassess.auth.dto.request.VerifyOtpRequest request = 
                com.codeassess.auth.dto.request.VerifyOtpRequest.builder()
                        .email("student@codeassess.edu.vn")
                        .otp("123456")
                        .build();

        com.codeassess.auth.dto.response.VerifyOtpResponse response = 
                com.codeassess.auth.dto.response.VerifyOtpResponse.builder()
                        .resetToken("token_abc_123")
                        .message("Verification code verified successfully.")
                        .build();

        Mockito.when(passwordResetService.verifyOtp(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").value("token_abc_123"));
    }

    @Test
    void resetPasswordReturns200OnSuccess() throws Exception {
        com.codeassess.auth.dto.request.ResetPasswordRequest request = 
                com.codeassess.auth.dto.request.ResetPasswordRequest.builder()
                        .token("token_abc_123")
                        .password("NewSecretPassword123")
                        .confirmPassword("NewSecretPassword123")
                        .build();

        com.codeassess.auth.dto.response.ResetPasswordResponse response = 
                com.codeassess.auth.dto.response.ResetPasswordResponse.builder()
                        .message("Password has been successfully reset.")
                        .build();

        Mockito.when(passwordResetService.resetPassword(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been successfully reset."));
    }
}
