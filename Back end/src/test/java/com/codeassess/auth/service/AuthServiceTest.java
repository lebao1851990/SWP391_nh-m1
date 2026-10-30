package com.codeassess.auth.service;

import com.codeassess.auth.dto.request.ForgotPasswordRequest;
import com.codeassess.auth.dto.request.LoginRequest;
import com.codeassess.auth.dto.request.RegisterRequest;
import com.codeassess.auth.dto.request.ResetPasswordRequest;
import com.codeassess.auth.dto.response.LoginResponse;
import com.codeassess.auth.entity.PasswordResetToken;
import com.codeassess.auth.mapper.AuthMapper;
import com.codeassess.auth.repository.PasswordResetTokenRepository;
import com.codeassess.common.exception.ConflictException;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

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
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @InjectMocks
    private AuthService authService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ==========================================
    // --- Login Tests ---
    // ==========================================

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
        assertThat(response.getToken()).isEqualTo("mock-session-123");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(session).setAttribute(Mockito.eq("SPRING_SECURITY_CONTEXT"), any());
    }

    @Test
    void loginThrowsIllegalStateExceptionWhenUserNotFoundAfterAuth() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("missing@codeassess.com");
        loginRequest.setPassword("securepass");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(request.getSession(true)).thenReturn(session);
        when(userRepository.findByEmail("missing@codeassess.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("User not found after successful authentication");
    }

    @Test
    void loginPropagatesAuthenticationExceptionWhenAuthenticationFails() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("invalid@codeassess.com");
        loginRequest.setPassword("wrongpass");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid username or password"));

        assertThatThrownBy(() -> authService.login(loginRequest, request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");

        verifyNoInteractions(userRepository);
        verifyNoInteractions(request);
    }

    @Test
    void loginPropagatesDisabledExceptionWhenAccountIsDisabled() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("disabled@codeassess.com");
        loginRequest.setPassword("securepass");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new DisabledException("User account is disabled"));

        assertThatThrownBy(() -> authService.login(loginRequest, request))
                .isInstanceOf(DisabledException.class)
                .hasMessage("User account is disabled");

        verifyNoInteractions(userRepository);
    }

    // ==========================================
    // --- Register Tests ---
    // ==========================================

    @Test
    void registerWithValidDataSavesUser() {
        RegisterRequest registerRequest = new RegisterRequest(
                "John Doe", "john@student.com", "Password123!", "Password123!", "student", true
        );

        when(userRepository.existsByEmail("john@student.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed_password");

        authService.register(registerRequest);

        verify(userRepository).save(argThat(user ->
                user.getEmail().equals("john@student.com") &&
                user.getFullName().equals("John Doe") &&
                user.getPasswordHash().equals("hashed_password") &&
                user.getRole() == Role.STUDENT
        ));
    }

    @Test
    void registerWithValidTeacherRoleSavesUserWithTeacherRole() {
        RegisterRequest registerRequest = new RegisterRequest(
                "Jane Teacher", "jane@teacher.com", "Password123!", "Password123!", "teacher", true
        );

        when(userRepository.existsByEmail("jane@teacher.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed_teacher_pass");

        authService.register(registerRequest);

        verify(userRepository).save(argThat(user ->
                user.getEmail().equals("jane@teacher.com") &&
                user.getFullName().equals("Jane Teacher") &&
                user.getPasswordHash().equals("hashed_teacher_pass") &&
                user.getRole() == Role.TEACHER
        ));
    }

    @Test
    void registerWithCaseInsensitiveRoleSuccessfullyParsesRole() {
        RegisterRequest registerRequest = new RegisterRequest(
                "Bob Student", "bob@student.com", "Password123!", "Password123!", "STUDENT", true
        );

        when(userRepository.existsByEmail("bob@student.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed_bob_pass");

        authService.register(registerRequest);

        verify(userRepository).save(argThat(user ->
                user.getEmail().equals("bob@student.com") &&
                user.getRole() == Role.STUDENT
        ));
    }

    @Test
    void registerWithDuplicateEmailThrowsConflictException() {
        RegisterRequest registerRequest = new RegisterRequest(
                "John Doe", "john@student.com", "Password123!", "Password123!", "student", true
        );

        when(userRepository.existsByEmail("john@student.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerWithAdminRoleThrowsIllegalArgumentException() {
        RegisterRequest registerRequest = new RegisterRequest(
                "Admin", "admin@codeassess.com", "Password123!", "Password123!", "admin", true
        );

        when(userRepository.existsByEmail("admin@codeassess.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot register as ADMIN");

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerWithInvalidRoleThrowsIllegalArgumentException() {
        RegisterRequest registerRequest = new RegisterRequest(
                "Unknown", "unknown@codeassess.com", "Password123!", "Password123!", "superman", true
        );

        when(userRepository.existsByEmail("unknown@codeassess.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid role");

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerPropagatesExceptionWhenRepositorySaveFails() {
        RegisterRequest registerRequest = new RegisterRequest(
                "Error User", "error@student.com", "Password123!", "Password123!", "student", true
        );

        when(userRepository.existsByEmail("error@student.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("Database error"));

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database error");
    }

    // ==========================================
    // --- Forgot Password Tests ---
    // ==========================================

    @Test
    void forgotPasswordWhenEmailEmptyOrNullDoesNothing() {
        ForgotPasswordRequest forgotRequest = ForgotPasswordRequest.builder().identity("  ").build();

        authService.forgotPassword(forgotRequest);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordResetTokenRepository);
    }

    @Test
    void forgotPasswordWhenTargetEmailReturnsNullDoesNothing() {
        ForgotPasswordRequest forgotRequest = mock(ForgotPasswordRequest.class);
        when(forgotRequest.getTargetEmail()).thenReturn(null);

        authService.forgotPassword(forgotRequest);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordResetTokenRepository);
    }

    @Test
    void forgotPasswordAntiEnumerationForNonExistentEmailDoesNotSaveToken() {
        ForgotPasswordRequest forgotRequest = ForgotPasswordRequest.builder().identity("nonexistent@codeassess.com").build();

        when(userRepository.findByEmail("nonexistent@codeassess.com")).thenReturn(Optional.empty());

        authService.forgotPassword(forgotRequest);

        verify(userRepository).findByEmail("nonexistent@codeassess.com");
        verifyNoInteractions(passwordResetTokenRepository);
    }

    @Test
    void forgotPasswordWhenUserHasNoExistingActiveTokenCreatesNewTokenDirectly() {
        ForgotPasswordRequest forgotRequest = ForgotPasswordRequest.builder().identity("newuser@codeassess.com").build();

        User user = User.builder().id("u_999").email("newuser@codeassess.com").build();

        when(userRepository.findByEmail("newuser@codeassess.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findTopByUserIdAndUsedFalseOrderByCreatedAtDesc("u_999"))
                .thenReturn(Optional.empty());

        authService.forgotPassword(forgotRequest);

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository, times(1)).save(tokenCaptor.capture());

        PasswordResetToken createdToken = tokenCaptor.getValue();
        assertThat(createdToken.getUser()).isEqualTo(user);
        assertThat(createdToken.getToken()).hasSize(64);
        assertThat(createdToken.isUsed()).isFalse();
        assertThat(createdToken.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(25));
    }

    @Test
    void forgotPasswordInvalidatesExistingActiveTokenAndCreatesNewToken() {
        ForgotPasswordRequest forgotRequest = ForgotPasswordRequest.builder().identity("user@codeassess.com").build();

        User user = User.builder().id("u_100").email("user@codeassess.com").build();
        PasswordResetToken existingToken = PasswordResetToken.builder()
                .id("t_old")
                .user(user)
                .token("old_token_123")
                .used(false)
                .build();

        when(userRepository.findByEmail("user@codeassess.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findTopByUserIdAndUsedFalseOrderByCreatedAtDesc("u_100"))
                .thenReturn(Optional.of(existingToken));

        authService.forgotPassword(forgotRequest);

        assertThat(existingToken.isUsed()).isTrue();
        verify(passwordResetTokenRepository).save(existingToken);

        ArgumentCaptor<PasswordResetToken> newCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository, times(2)).save(newCaptor.capture());

        PasswordResetToken createdToken = newCaptor.getValue();
        assertThat(createdToken.getUser()).isEqualTo(user);
        assertThat(createdToken.getToken()).hasSize(64);
        assertThat(createdToken.isUsed()).isFalse();
        assertThat(createdToken.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(25));
    }

    @Test
    void forgotPasswordPropagatesExceptionWhenRepositoryFails() {
        ForgotPasswordRequest forgotRequest = ForgotPasswordRequest.builder().identity("dbfail@codeassess.com").build();

        when(userRepository.findByEmail("dbfail@codeassess.com"))
                .thenThrow(new RuntimeException("Database connection timeout"));

        assertThatThrownBy(() -> authService.forgotPassword(forgotRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database connection timeout");
    }

    // ==========================================
    // --- Reset Password Tests ---
    // ==========================================

    @Test
    void resetPasswordThrowsWhenTokenNull() {
        ResetPasswordRequest resetRequest = ResetPasswordRequest.builder()
                .token(null)
                .password("Password123!")
                .confirmPassword("Password123!")
                .build();

        assertThatThrownBy(() -> authService.resetPassword(resetRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token is required");
    }

    @Test
    void resetPasswordThrowsWhenTokenEmptyOrWhitespace() {
        ResetPasswordRequest resetRequest = ResetPasswordRequest.builder()
                .token("   ")
                .password("Password123!")
                .confirmPassword("Password123!")
                .build();

        assertThatThrownBy(() -> authService.resetPassword(resetRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token is required");
    }

    @Test
    void resetPasswordThrowsWhenPasswordIsNull() {
        ResetPasswordRequest resetRequest = ResetPasswordRequest.builder()
                .token("valid_token")
                .password(null)
                .confirmPassword("Password123!")
                .build();

        assertThatThrownBy(() -> authService.resetPassword(resetRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password must be at least 8 characters");
    }

    @Test
    void resetPasswordThrowsWhenPasswordTooShort() {
        ResetPasswordRequest resetRequest = ResetPasswordRequest.builder()
                .token("valid_token")
                .password("short")
                .confirmPassword("short")
                .build();

        assertThatThrownBy(() -> authService.resetPassword(resetRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password must be at least 8 characters");
    }

    @Test
    void resetPasswordThrowsWhenPasswordsDoNotMatch() {
        ResetPasswordRequest resetRequest = ResetPasswordRequest.builder()
                .token("valid_token")
                .password("Password123!")
                .confirmPassword("DifferentPassword123!")
                .build();

        assertThatThrownBy(() -> authService.resetPassword(resetRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Passwords do not match");
    }

    @Test
    void resetPasswordThrowsWhenTokenNotFoundInRepository() {
        ResetPasswordRequest resetRequest = ResetPasswordRequest.builder()
                .token("unknown_token")
                .password("Password123!")
                .confirmPassword("Password123!")
                .build();

        when(passwordResetTokenRepository.findByToken("unknown_token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(resetRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid or expired password reset token");
    }

    @Test
    void resetPasswordThrowsWhenTokenAlreadyUsed() {
        ResetPasswordRequest resetRequest = ResetPasswordRequest.builder()
                .token("used_token")
                .password("Password123!")
                .confirmPassword("Password123!")
                .build();

        PasswordResetToken token = PasswordResetToken.builder()
                .token("used_token")
                .used(true)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();

        when(passwordResetTokenRepository.findByToken("used_token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword(resetRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("This password reset token has already been used");
    }

    @Test
    void resetPasswordThrowsWhenTokenExpired() {
        ResetPasswordRequest resetRequest = ResetPasswordRequest.builder()
                .token("expired_token")
                .password("Password123!")
                .confirmPassword("Password123!")
                .build();

        PasswordResetToken token = PasswordResetToken.builder()
                .token("expired_token")
                .used(false)
                .expiresAt(LocalDateTime.now().minusMinutes(5)) // expired
                .build();

        when(passwordResetTokenRepository.findByToken("expired_token")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword(resetRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password reset token has expired");
    }

    @Test
    void resetPasswordSuccessUpdatesPasswordHashAndMarksTokenUsed() {
        ResetPasswordRequest resetRequest = ResetPasswordRequest.builder()
                .token("valid_reset_token")
                .password("NewSecurePassword123!")
                .confirmPassword("NewSecurePassword123!")
                .build();

        User user = User.builder().id("u_200").email("user200@codeassess.com").build();
        PasswordResetToken token = PasswordResetToken.builder()
                .token("valid_reset_token")
                .user(user)
                .used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .build();

        when(passwordResetTokenRepository.findByToken("valid_reset_token")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewSecurePassword123!")).thenReturn("encoded_new_password");

        authService.resetPassword(resetRequest);

        assertThat(user.getPasswordHash()).isEqualTo("encoded_new_password");
        assertThat(token.isUsed()).isTrue();

        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).save(token);
    }

    @Test
    void resetPasswordSuccessWhenConfirmPasswordIsNull() {
        ResetPasswordRequest resetRequest = ResetPasswordRequest.builder()
                .token("valid_reset_token_null_confirm")
                .password("NewSecurePassword123!")
                .confirmPassword(null)
                .build();

        User user = User.builder().id("u_201").email("user201@codeassess.com").build();
        PasswordResetToken token = PasswordResetToken.builder()
                .token("valid_reset_token_null_confirm")
                .user(user)
                .used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .build();

        when(passwordResetTokenRepository.findByToken("valid_reset_token_null_confirm")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewSecurePassword123!")).thenReturn("encoded_pass_201");

        authService.resetPassword(resetRequest);

        assertThat(user.getPasswordHash()).isEqualTo("encoded_pass_201");
        assertThat(token.isUsed()).isTrue();
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).save(token);
    }

    @Test
    void resetPasswordSuccessWhenConfirmPasswordIsBlank() {
        ResetPasswordRequest resetRequest = ResetPasswordRequest.builder()
                .token("valid_reset_token_blank_confirm")
                .password("NewSecurePassword123!")
                .confirmPassword("   ")
                .build();

        User user = User.builder().id("u_202").email("user202@codeassess.com").build();
        PasswordResetToken token = PasswordResetToken.builder()
                .token("valid_reset_token_blank_confirm")
                .user(user)
                .used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .build();

        when(passwordResetTokenRepository.findByToken("valid_reset_token_blank_confirm")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewSecurePassword123!")).thenReturn("encoded_pass_202");

        authService.resetPassword(resetRequest);

        assertThat(user.getPasswordHash()).isEqualTo("encoded_pass_202");
        assertThat(token.isUsed()).isTrue();
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).save(token);
    }

    @Test
    void resetPasswordPropagatesExceptionWhenRepositorySaveFails() {
        ResetPasswordRequest resetRequest = ResetPasswordRequest.builder()
                .token("valid_token_save_fail")
                .password("NewSecurePassword123!")
                .confirmPassword("NewSecurePassword123!")
                .build();

        User user = User.builder().id("u_203").email("user203@codeassess.com").build();
        PasswordResetToken token = PasswordResetToken.builder()
                .token("valid_token_save_fail")
                .user(user)
                .used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .build();

        when(passwordResetTokenRepository.findByToken("valid_token_save_fail")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode(any())).thenReturn("hashed_fail");
        when(userRepository.save(user)).thenThrow(new RuntimeException("Database error saving user password"));

        assertThatThrownBy(() -> authService.resetPassword(resetRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database error saving user password");
    }
}
