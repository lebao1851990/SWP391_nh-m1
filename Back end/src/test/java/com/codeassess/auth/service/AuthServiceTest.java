package com.codeassess.auth.service;

import com.codeassess.auth.dto.request.LoginRequest;
import com.codeassess.auth.dto.response.LoginResponse;
import com.codeassess.auth.mapper.AuthMapper;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthMapper authMapper;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpSession session;

    @Mock
    private Authentication authentication;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginSetsSecurityContextAndReturnsUserSession() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("teacher@codeassess.com");
        loginRequest.setPassword("securepass");

        User user = new User();
        user.setId("u_123");
        user.setEmail("teacher@codeassess.com");
        user.setRole(Role.TEACHER);

        LoginResponse.UserSessionDto sessionDto = new LoginResponse.UserSessionDto();
        sessionDto.setId("u_123");
        sessionDto.setEmail("teacher@codeassess.com");
        sessionDto.setRole("TEACHER");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(session.getId()).thenReturn("mock-session-123");
        when(request.getSession(true)).thenReturn(session);
        when(userRepository.findByEmail("teacher@codeassess.com")).thenReturn(Optional.of(user));
        when(authMapper.toUserSessionDto(user)).thenReturn(sessionDto);

        LoginResponse response = authService.login(loginRequest, request);

        assertThat(response).isNotNull();
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getEmail()).isEqualTo("teacher@codeassess.com");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(session).setAttribute(Mockito.eq("SPRING_SECURITY_CONTEXT"), any());
    }

    @Test
    void registerWithValidDataSavesUser() {
        com.codeassess.auth.dto.request.RegisterRequest request = new com.codeassess.auth.dto.request.RegisterRequest(
                "John Doe", "john@student.com", "Password123!", "Password123!", "student", true
        );

        when(userRepository.existsByEmail("john@student.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed_password");

        authService.register(request);

        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user -> 
                user.getEmail().equals("john@student.com") &&
                user.getFullName().equals("John Doe") &&
                user.getPasswordHash().equals("hashed_password") &&
                user.getRole() == Role.STUDENT
        ));
    }

    @Test
    void registerWithDuplicateEmailThrowsConflictException() {
        com.codeassess.auth.dto.request.RegisterRequest request = new com.codeassess.auth.dto.request.RegisterRequest(
                "John Doe", "john@student.com", "Password123!", "Password123!", "student", true
        );

        when(userRepository.existsByEmail("john@student.com")).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(com.codeassess.common.exception.ConflictException.class)
                .hasMessage("Email already exists");
    }

    @Test
    void registerWithAdminRoleThrowsIllegalArgumentException() {
        com.codeassess.auth.dto.request.RegisterRequest request = new com.codeassess.auth.dto.request.RegisterRequest(
                "Admin", "admin@codeassess.com", "Password123!", "Password123!", "admin", true
        );

        when(userRepository.existsByEmail("admin@codeassess.com")).thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot register as ADMIN");
    }
}
