package com.codeassess.auth.service;

import com.codeassess.auth.dto.request.ForgotPasswordRequest;
import com.codeassess.auth.dto.request.ResetPasswordRequest;
import com.codeassess.auth.dto.request.VerifyOtpRequest;
import com.codeassess.auth.dto.response.ForgotPasswordResponse;
import com.codeassess.auth.dto.response.ResetPasswordResponse;
import com.codeassess.auth.dto.response.VerifyOtpResponse;
import com.codeassess.user.entity.Role;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    private PasswordResetTokenStore tokenStore;
    private PasswordResetService passwordResetService;

    private final String registeredEmail = "student@codeassess.edu.vn";
    private User registeredUser;

    @BeforeEach
    void setUp() {
        // Construct tokenStore without Redis (in-memory mode for fast deterministic unit tests)
        tokenStore = new PasswordResetTokenStore();
        passwordResetService = new PasswordResetService(userRepository, passwordEncoder, emailService, tokenStore);

        registeredUser = User.builder()
                .id("u-12345")
                .email(registeredEmail)
                .fullName("Nguyen Van A")
                .passwordHash("old_hashed_password")
                .role(Role.STUDENT)
                .build();
    }

    @Test
    @DisplayName("Request OTP with valid registered email generates OTP and calls email service")
    void testRequestOtp_ValidEmail_DispatchesEmail() {
        when(userRepository.findByEmail(registeredEmail)).thenReturn(Optional.of(registeredUser));

        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email(registeredEmail)
                .build();

        ForgotPasswordResponse response = passwordResetService.requestPasswordReset(request);

        assertNotNull(response);
        assertEquals("If an account exists with this email, a verification code has been sent.", response.getMessage());

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetOtp(eq(registeredEmail), otpCaptor.capture());

        String capturedOtp = otpCaptor.getValue();
        assertNotNull(capturedOtp);
        assertEquals(6, capturedOtp.length());
        assertTrue(capturedOtp.matches("^\\d{6}$"));
    }

    @Test
    @DisplayName("Request OTP with non-existent email returns neutral message without dispatching email")
    void testRequestOtp_NonExistentEmail_ReturnsNeutralResponse() {
        String nonExistent = "unknown@codeassess.edu.vn";
        when(userRepository.findByEmail(nonExistent)).thenReturn(Optional.empty());

        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email(nonExistent)
                .build();

        ForgotPasswordResponse response = passwordResetService.requestPasswordReset(request);

        assertNotNull(response);
        assertEquals("If an account exists with this email, a verification code has been sent.", response.getMessage());
        verify(emailService, never()).sendPasswordResetOtp(anyString(), anyString());
    }

    @Test
    @DisplayName("Request OTP rate limiting: second request within 60 seconds throws 429")
    void testRequestOtp_RateLimitExceeded_ThrowsTooManyRequests() {
        when(userRepository.findByEmail(registeredEmail)).thenReturn(Optional.of(registeredUser));

        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email(registeredEmail)
                .build();

        // First request succeeds
        passwordResetService.requestPasswordReset(request);

        // Second request triggers rate limiter
        assertThrows(ResponseStatusException.class, () -> passwordResetService.requestPasswordReset(request));
    }

    @Test
    @DisplayName("Verify OTP with correct code returns one-time reset token and deletes OTP")
    void testVerifyOtp_CorrectCode_ReturnsResetToken() {
        when(userRepository.findByEmail(registeredEmail)).thenReturn(Optional.of(registeredUser));
        ForgotPasswordRequest request = ForgotPasswordRequest.builder().email(registeredEmail).build();
        passwordResetService.requestPasswordReset(request);

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetOtp(eq(registeredEmail), otpCaptor.capture());
        String generatedOtp = otpCaptor.getValue();

        VerifyOtpRequest verifyRequest = VerifyOtpRequest.builder()
                .email(registeredEmail)
                .otp(generatedOtp)
                .build();

        VerifyOtpResponse verifyResponse = passwordResetService.verifyOtp(verifyRequest);

        assertNotNull(verifyResponse);
        assertNotNull(verifyResponse.getResetToken());
        assertEquals(64, verifyResponse.getResetToken().length()); // 32 bytes hex = 64 chars

        // OTP must now be deleted (Single-Use OTP)
        assertNull(tokenStore.getOtp(registeredEmail));

        // Attempting to reuse OTP immediately fails
        assertThrows(IllegalArgumentException.class, () -> passwordResetService.verifyOtp(verifyRequest));
    }

    @Test
    @DisplayName("Verify OTP with incorrect code increments attempt counter and fails")
    void testVerifyOtp_IncorrectCode_FailsAndIncrementsAttempts() {
        when(userRepository.findByEmail(registeredEmail)).thenReturn(Optional.of(registeredUser));
        passwordResetService.requestPasswordReset(ForgotPasswordRequest.builder().email(registeredEmail).build());

        VerifyOtpRequest wrongRequest = VerifyOtpRequest.builder()
                .email(registeredEmail)
                .otp("000000")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
                () -> passwordResetService.verifyOtp(wrongRequest));
        assertTrue(ex.getMessage().contains("Invalid verification code"));

        // Entry still exists with attempts incremented
        PasswordResetTokenStore.OtpEntry entry = tokenStore.getOtp(registeredEmail);
        assertNotNull(entry);
        assertEquals(1, entry.attempts());
    }

    @Test
    @DisplayName("Verify OTP exceeds maximum 5 attempts deletes OTP and rejects")
    void testVerifyOtp_ExceedsMaxAttempts_DeletesOtp() {
        when(userRepository.findByEmail(registeredEmail)).thenReturn(Optional.of(registeredUser));
        passwordResetService.requestPasswordReset(ForgotPasswordRequest.builder().email(registeredEmail).build());

        VerifyOtpRequest wrongRequest = VerifyOtpRequest.builder()
                .email(registeredEmail)
                .otp("000000")
                .build();

        // 5 wrong attempts
        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalArgumentException.class, () -> passwordResetService.verifyOtp(wrongRequest));
        }

        // 6th attempt should state too many invalid attempts or expired
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
                () -> passwordResetService.verifyOtp(wrongRequest));
        assertTrue(ex.getMessage().contains("Too many invalid attempts") || ex.getMessage().contains("expired"));

        // OTP is cleaned up
        assertNull(tokenStore.getOtp(registeredEmail));
    }

    @Test
    @DisplayName("Verify OTP when expired throws exception")
    void testVerifyOtp_ExpiredOtp_ThrowsException() {
        // Save expired OTP directly
        tokenStore.saveOtp(registeredEmail, PasswordResetService.sha256Hex("123456"), Duration.ofMillis(-1000));

        VerifyOtpRequest request = VerifyOtpRequest.builder()
                .email(registeredEmail)
                .otp("123456")
                .build();

        assertThrows(IllegalArgumentException.class, () -> passwordResetService.verifyOtp(request));
    }

    @Test
    @DisplayName("Reset Password successfully hashes password, updates user, and consumes reset token")
    void testResetPassword_ValidToken_UpdatesPasswordAndConsumesToken() {
        when(userRepository.findByEmail(registeredEmail)).thenReturn(Optional.of(registeredUser));
        passwordResetService.requestPasswordReset(ForgotPasswordRequest.builder().email(registeredEmail).build());

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetOtp(eq(registeredEmail), otpCaptor.capture());

        VerifyOtpResponse verifyResponse = passwordResetService.verifyOtp(
                VerifyOtpRequest.builder().email(registeredEmail).otp(otpCaptor.getValue()).build());

        String resetToken = verifyResponse.getResetToken();

        when(passwordEncoder.encode("NewSecretPassword123!")).thenReturn("new_bcrypt_hash_987");

        ResetPasswordRequest resetRequest = ResetPasswordRequest.builder()
                .token(resetToken)
                .password("NewSecretPassword123!")
                .confirmPassword("NewSecretPassword123!")
                .build();

        ResetPasswordResponse resetResponse = passwordResetService.resetPassword(resetRequest);

        assertNotNull(resetResponse);
        assertTrue(resetResponse.getMessage().contains("successfully reset"));

        verify(passwordEncoder).encode("NewSecretPassword123!");
        verify(userRepository).save(registeredUser);
        assertEquals("new_bcrypt_hash_987", registeredUser.getPasswordHash());

        // Token must now be consumed (Single-Use Token)
        assertThrows(IllegalArgumentException.class, () -> passwordResetService.resetPassword(resetRequest));
    }

    @Test
    @DisplayName("Reset Password fails when passwords do not match")
    void testResetPassword_PasswordMismatch_ThrowsException() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("any_token")
                .password("NewPassword123")
                .confirmPassword("DifferentPassword123")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
                () -> passwordResetService.resetPassword(request));
        assertEquals("Passwords do not match.", ex.getMessage());
    }

    @Test
    @DisplayName("Reset Password fails when token is invalid or expired")
    void testResetPassword_InvalidToken_ThrowsException() {
        ResetPasswordRequest request = ResetPasswordRequest.builder()
                .token("invalid_non_existent_token")
                .password("NewPassword123")
                .confirmPassword("NewPassword123")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, 
                () -> passwordResetService.resetPassword(request));
        assertTrue(ex.getMessage().contains("invalid or has expired"));
    }
}
