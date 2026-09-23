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
}
