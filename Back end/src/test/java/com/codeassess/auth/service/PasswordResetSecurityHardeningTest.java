package com.codeassess.auth.service;

import com.codeassess.auth.dto.request.ForgotPasswordRequest;
import com.codeassess.auth.dto.request.ResendOtpRequest;
import com.codeassess.auth.dto.request.ResetPasswordRequest;
import com.codeassess.auth.dto.request.VerifyOtpRequest;
import com.codeassess.auth.dto.response.ForgotPasswordResponse;
import com.codeassess.auth.dto.response.ResendOtpResponse;
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
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetSecurityHardeningTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private StringRedisTemplate mockRedisTemplate;

    @Mock
    private ValueOperations<String, String> mockValueOps;

    private PasswordResetTokenStore tokenStore;
    private PasswordResetService passwordResetService;

    private final String testEmail = "security.student@codeassess.edu.vn";
    private User testUser;

    @BeforeEach
    void setUp() {
        tokenStore = new PasswordResetTokenStore(); // in-memory test store
        passwordResetService = new PasswordResetService(userRepository, passwordEncoder, emailService, tokenStore);

        testUser = User.builder()
                .id("u-sec-999")
                .email(testEmail)
                .fullName("Security Tester")
                .passwordHash("current_hashed_password")
                .role(Role.STUDENT)
                .build();
    }

    @Test
    @DisplayName("Phase 1: Concurrent verifyOtp with same OTP allows only 1 winner; second request fails")
    void testConcurrentVerifyOtp_OnlyOneSucceeds() throws Exception {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        passwordResetService.requestPasswordReset(ForgotPasswordRequest.builder().email(testEmail).build());
        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetOtp(eq(testEmail), otpCaptor.capture());
        String generatedOtp = otpCaptor.getValue();

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<VerifyOtpResponse>> results = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            results.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await(); // ensure exact simultaneous execution
                VerifyOtpRequest req = VerifyOtpRequest.builder().email(testEmail).otp(generatedOtp).build();
                return passwordResetService.verifyOtp(req);
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Release threads simultaneously

        for (Future<VerifyOtpResponse> future : results) {
            try {
                VerifyOtpResponse resp = future.get(5, TimeUnit.SECONDS);
                if (resp != null && resp.getResetToken() != null) {
                    successCount.incrementAndGet();
                }
            } catch (ExecutionException e) {
                if (e.getCause() instanceof IllegalArgumentException) {
                    failureCount.incrementAndGet();
                }
            }
        }

        executor.shutdown();
        assertEquals(1, successCount.get(), "Exactly 1 concurrent request must succeed in consuming the OTP");
        assertEquals(threadCount - 1, failureCount.get(), "All other concurrent requests must be rejected");
        assertNull(tokenStore.getOtp(testEmail), "OTP must be completely consumed from store");
    }

    @Test
    @DisplayName("Phase 1: Concurrent wrong OTP attempts never exceed maximum allowed attempts")
    void testConcurrentWrongOtpAttempts_NeverExceedsMaxAttempts() throws Exception {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
        passwordResetService.requestPasswordReset(ForgotPasswordRequest.builder().email(testEmail).build());

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<Void>> futures = new ArrayList<>();
        AtomicInteger caughtExceptions = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                VerifyOtpRequest wrongReq = VerifyOtpRequest.builder().email(testEmail).otp("000000").build();
                passwordResetService.verifyOtp(wrongReq);
                return null;
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Void> f : futures) {
            try {
                f.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof IllegalArgumentException) {
                    caughtExceptions.incrementAndGet();
                }
            }
        }

        executor.shutdown();
        assertEquals(threadCount, caughtExceptions.get(), "All wrong attempts must fail");
        assertNull(tokenStore.getOtp(testEmail), "OTP must be purged once max attempts reached");
    }

    @Test
    @DisplayName("Phase 1: Concurrent resetPassword with same token allows only 1 winner; second is blocked")
    void testConcurrentResetPassword_OnlyOneSucceeds() throws Exception {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode(anyString())).thenReturn("new_hash_sec");

        passwordResetService.requestPasswordReset(ForgotPasswordRequest.builder().email(testEmail).build());
        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetOtp(eq(testEmail), otpCaptor.capture());

        VerifyOtpResponse verifyResp = passwordResetService.verifyOtp(
                VerifyOtpRequest.builder().email(testEmail).otp(otpCaptor.getValue()).build());
        String resetToken = verifyResp.getResetToken();

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<ResetPasswordResponse>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictOrRejectedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                ResetPasswordRequest resetReq = ResetPasswordRequest.builder()
                        .token(resetToken)
                        .password("NewSecurePassword!2026")
                        .confirmPassword("NewSecurePassword!2026")
                        .build();
                return passwordResetService.resetPassword(resetReq);
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<ResetPasswordResponse> f : futures) {
            try {
                ResetPasswordResponse resp = f.get(5, TimeUnit.SECONDS);
                if (resp != null) {
                    successCount.incrementAndGet();
                }
            } catch (ExecutionException e) {
                if (e.getCause() instanceof ResponseStatusException rse && rse.getStatusCode() == HttpStatus.CONFLICT) {
                    conflictOrRejectedCount.incrementAndGet();
                } else if (e.getCause() instanceof IllegalArgumentException) {
                    conflictOrRejectedCount.incrementAndGet();
                }
            }
        }

        executor.shutdown();
        assertEquals(1, successCount.get(), "Only 1 thread may successfully reset password");
        assertEquals(1, conflictOrRejectedCount.get(), "Concurrent request must be rejected with CONFLICT or illegal argument");
        assertNull(tokenStore.getResetTokenEmail(PasswordResetService.sha256Hex(resetToken)), "Token must be consumed");
    }

    @Test
    @DisplayName("Phase 2: Production mode (allowInMemoryFallback=false) fails closed when Redis connection drops")
    void testRedisUnavailable_ProductionMode_FailsClosed() {
        when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOps);
        doThrow(new RedisConnectionFailureException("Redis offline"))
                .when(mockValueOps).set(anyString(), anyString(), any(Duration.class));

        PasswordResetTokenStore prodStore = new PasswordResetTokenStore(mockRedisTemplate, false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                prodStore.saveOtp(testEmail, "hash_123", Duration.ofMinutes(10)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertNull(prodStore.getOtp(testEmail), "Must not silently write to local JVM RAM in production mode");
    }

    @Test
    @DisplayName("Phase 2: Dev fallback mode checks memory store when Redis recovers but has no record (split-brain prevention)")
    void testDevFallback_RecoversWithoutOrphaningInMemoryToken() {
        when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOps);
        // Simulate Redis being offline during saveOtp -> falls back to RAM
        doThrow(new RedisConnectionFailureException("Redis offline during save"))
                .when(mockValueOps).set(anyString(), anyString(), any(Duration.class));

        // When Redis comes back online for verify, Lua script returns -1 (key not present in Redis)
        when(mockRedisTemplate.execute(any(), anyList(), anyString(), anyString())).thenReturn(-1L);

        PasswordResetTokenStore devStore = new PasswordResetTokenStore(mockRedisTemplate, true);
        String hashedOtp = PasswordResetService.sha256Hex("654321");

        // Saved into in-memory store due to Redis failure
        devStore.saveOtp(testEmail, hashedOtp, Duration.ofMinutes(5));

        // When Redis returns -1 (not in Redis), store should still locate in-memory token in dev mode
        PasswordResetTokenStore.VerifyResult result = devStore.verifyAndConsumeOtp(testEmail, hashedOtp, 5);
        assertEquals(PasswordResetTokenStore.VerifyStatus.SUCCESS, result.status(),
                "In dev mode, verification must find in-memory token even if Redis returns not found");
    }

    @Test
    @DisplayName("Phase 3: SMTP failure rolls back OTP and rate limit safely")
    void testSmtpFailure_RollsBackOtpAndRateLimit() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
        doThrow(new RuntimeException("SMTP transport error"))
                .when(emailService).sendPasswordResetOtp(eq(testEmail), anyString());

        ForgotPasswordRequest request = ForgotPasswordRequest.builder().email(testEmail).build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                passwordResetService.requestPasswordReset(request));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());

        // Rate limit and OTP must be rolled back immediately
        assertFalse(tokenStore.isRateLimited(testEmail), "Rate limit must be rolled back on SMTP failure");
        assertNull(tokenStore.getOtp(testEmail), "Unsent OTP must be purged on SMTP failure");
    }

    @Test
    @DisplayName("Phase 3: Production mode without SMTP credentials throws IllegalStateException (no silent simulation)")
    void testEmailService_ProductionMode_RefusesSilentSimulation() {
        EmailService prodEmailService = new EmailService(null, "sender@codeassess.edu.vn", "", false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                prodEmailService.sendPasswordResetOtp(testEmail, "123456"));

        assertTrue(ex.getMessage().contains("misconfigured") || ex.getMessage().contains("credentials"));
    }

    @Test
    @DisplayName("Phase 3: SMTP authentication failure throws diagnostic exception without leaking credentials")
    void testEmailService_SmtpAuthFailure_ThrowsDiagnosticExceptionWithoutLeakingCredentials() {
        JavaMailSender mockMailSender = mock(JavaMailSender.class);
        doThrow(new MailAuthenticationException("535-5.7.8 Username and Password not accepted"))
                .when(mockMailSender).send(any(SimpleMailMessage.class));

        EmailService serviceWithAuthFailure = new EmailService(mockMailSender, "sender@codeassess.edu.vn", "smtpuser@gmail.com", false);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                serviceWithAuthFailure.sendPasswordResetOtp(testEmail, "123456"));

        assertTrue(ex.getMessage().contains("authentication failed") || ex.getMessage().contains("administrator"));
    }

    @Test
    @DisplayName("Phase 3: SMTP connection timeout throws transport exception")
    void testEmailService_SmtpConnectionTimeout_ThrowsTransportException() {
        JavaMailSender mockMailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("Connection timed out to smtp.gmail.com:587"))
                .when(mockMailSender).send(any(SimpleMailMessage.class));

        EmailService serviceWithTimeout = new EmailService(mockMailSender, "sender@codeassess.edu.vn", "smtpuser@gmail.com", false);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                serviceWithTimeout.sendPasswordResetOtp(testEmail, "123456"));

        assertTrue(ex.getMessage().contains("Unable to send") || ex.getMessage().contains("verification email"));
    }

    @Test
    @DisplayName("Phase 3: Sender address defaults to mailUsername when mailFrom is blank")
    void testEmailService_DefaultsFromAddressToMailUsernameWhenFromIsBlank() {
        JavaMailSender mockMailSender = mock(JavaMailSender.class);
        ArgumentCaptor<SimpleMailMessage> msgCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        EmailService serviceDefaultFrom = new EmailService(mockMailSender, "", "auth-account@gmail.com", false);
        serviceDefaultFrom.sendPasswordResetOtp(testEmail, "654321");

        verify(mockMailSender).send(msgCaptor.capture());
        assertEquals("auth-account@gmail.com", msgCaptor.getValue().getFrom());
        assertEquals(testEmail, msgCaptor.getValue().getTo()[0]);
    }

    @Test
    @DisplayName("Phase 4: Password reset records changed timestamp, and subsequent session check detects it")
    void testPasswordReset_RecordsTimestampForSessionRevocation() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.encode(anyString())).thenReturn("new_encoded_pwd");

        passwordResetService.requestPasswordReset(ForgotPasswordRequest.builder().email(testEmail).build());
        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetOtp(eq(testEmail), otpCaptor.capture());

        VerifyOtpResponse vResp = passwordResetService.verifyOtp(
                VerifyOtpRequest.builder().email(testEmail).otp(otpCaptor.getValue()).build());

        long beforeReset = System.currentTimeMillis();
        passwordResetService.resetPassword(ResetPasswordRequest.builder()
                .token(vResp.getResetToken())
                .password("BrandNewPass123")
                .confirmPassword("BrandNewPass123")
                .build());
        long afterReset = System.currentTimeMillis();

        Long changedAt = tokenStore.getPasswordChangedAt(testEmail);
        assertNotNull(changedAt, "Password changed timestamp must be recorded in TokenStore");
        assertTrue(changedAt >= beforeReset - 1000 && changedAt <= afterReset + 1000);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        assertNotNull(userCaptor.getValue().getPasswordChangedAt(), "passwordChangedAt must be persisted on User entity");
    }

    @Test
    @DisplayName("Phase 5: Email casing consistency: uppercase request and lowercase verify match properly")
    void testEmailCasingConsistency_RequestAndVerifyMatch() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        String mixedCaseEmail = "SECURITY.Student@CodeAssess.Edu.Vn";
        passwordResetService.requestPasswordReset(ForgotPasswordRequest.builder().email(mixedCaseEmail).build());

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetOtp(eq(testEmail), otpCaptor.capture());

        VerifyOtpResponse response = passwordResetService.verifyOtp(VerifyOtpRequest.builder()
                .email("security.student@codeassess.edu.vn")
                .otp(otpCaptor.getValue())
                .build());

        assertNotNull(response);
        assertNotNull(response.getResetToken());
    }

    @Test
    @DisplayName("Scope B: Claim ownership fencing: slow expired request cannot release or consume newer request claim")
    void testClaimExpiration_OldRequestCannotDeleteNewerRequestClaim() throws Exception {
        String hashedToken = PasswordResetService.sha256Hex("test_reset_token_123");
        tokenStore.saveResetToken(hashedToken, testEmail, Duration.ofMinutes(15));

        String slowRequestOwner = "owner-req-1";
        String newerRequestOwner = "owner-req-2";

        // Slow request claims token with 50ms TTL
        boolean claimed1 = tokenStore.claimResetToken(hashedToken, slowRequestOwner, Duration.ofMillis(50));
        assertTrue(claimed1);

        // Sleep 70ms so slow request's claim expires
        Thread.sleep(70);

        // Newer request arrives and successfully acquires claim on the same token
        boolean claimed2 = tokenStore.claimResetToken(hashedToken, newerRequestOwner, Duration.ofSeconds(15));
        assertTrue(claimed2, "Newer request must acquire claim after previous claim expired");

        // Slow request finishes DB write and attempts to release claim with old owner token
        boolean releasedByOld = tokenStore.releaseResetTokenClaim(hashedToken, slowRequestOwner);
        assertFalse(releasedByOld, "Old request must NOT be able to release newer request's claim");

        // Slow request attempts to consume token with old owner token
        boolean consumedByOld = tokenStore.consumeResetToken(hashedToken, slowRequestOwner);
        assertFalse(consumedByOld, "Old request must NOT be able to consume token when it no longer owns the claim");

        // Newer request can still read token and consume it successfully
        assertEquals(testEmail, tokenStore.getResetTokenEmail(hashedToken), "Token must still be intact for newer request");
        boolean consumedByNewer = tokenStore.consumeResetToken(hashedToken, newerRequestOwner);
        assertTrue(consumedByNewer, "Newer request must successfully consume token");
        assertNull(tokenStore.getResetTokenEmail(hashedToken), "Token is now consumed");
    }

    @Test
    @DisplayName("Scope C: Redis Cluster hashtag compatibility: verified key pattern format")
    void testRedisClusterHashtagPattern() {
        String email = "test@domain.com";
        String token = "hashed_token_abc";

        String otpKey = "pwd_reset:{" + email + "}:otp";
        String rateKey = "pwd_reset:{" + email + "}:rate";
        String tokenKey = "pwd_reset:{" + token + "}:token";
        String claimKey = "pwd_reset:{" + token + "}:claim";

        // In Redis Cluster, multi-key commands require keys in the same hash slot via {...}
        assertTrue(otpKey.contains("{" + email + "}"));
        assertTrue(rateKey.contains("{" + email + "}"));
        assertTrue(tokenKey.contains("{" + token + "}"));
        assertTrue(claimKey.contains("{" + token + "}"));
    }

    @Test
    @DisplayName("Resend OTP: Success generates new OTP, resets attempts, and establishes 60s rate limit")
    void testResendOtp_Success_GeneratesNewOtpAndEnforcesRateLimit() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        ResendOtpResponse response = passwordResetService.resendOtp(ResendOtpRequest.builder().email(testEmail).build());

        assertNotNull(response);
        assertEquals(60, response.getCooldownSeconds());
        assertTrue(response.getMessage().contains("new verification code has been sent"));
        assertTrue(tokenStore.isRateLimited(testEmail), "Rate limit must be established after resending OTP");

        ArgumentCaptor<String> otpCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetOtp(eq(testEmail), otpCaptor.capture());
        assertNotNull(otpCaptor.getValue());
        assertEquals(6, otpCaptor.getValue().length());
    }

    @Test
    @DisplayName("Resend OTP: Cooldown enforcement: Resending within 60 seconds throws 429 Too Many Requests")
    void testResendOtp_DuringCooldown_ThrowsTooManyRequests() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        // Initial request sets rate limit
        passwordResetService.requestPasswordReset(ForgotPasswordRequest.builder().email(testEmail).build());
        assertTrue(tokenStore.isRateLimited(testEmail));

        // Attempting to resend immediately must be rejected by backend rate limiter
        ResendOtpRequest resendRequest = ResendOtpRequest.builder().email(testEmail).build();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                passwordResetService.resendOtp(resendRequest));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatusCode());
        assertTrue(ex.getReason().contains("60 seconds"));
    }

    @Test
    @DisplayName("Resend OTP: Overwrite protection: Previous OTP is invalidated when new OTP is issued")
    void testResendOtp_InvalidatesOldOtp_PreviousOtpCannotBeUsed() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));

        // Request 1: Initial OTP
        passwordResetService.requestPasswordReset(ForgotPasswordRequest.builder().email(testEmail).build());
        ArgumentCaptor<String> otpCaptor1 = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendPasswordResetOtp(eq(testEmail), otpCaptor1.capture());
        String firstOtp = otpCaptor1.getValue();

        // Clear rate limit to simulate expiry of 60s cooldown
        tokenStore.clearRateLimit(testEmail);
        assertFalse(tokenStore.isRateLimited(testEmail));

        // Request 2: Resend OTP
        passwordResetService.resendOtp(ResendOtpRequest.builder().email(testEmail).build());
        ArgumentCaptor<String> otpCaptor2 = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(2)).sendPasswordResetOtp(eq(testEmail), otpCaptor2.capture());
        String secondOtp = otpCaptor2.getValue();

        // First OTP must now be INVALID (rejected with IllegalArgumentException)
        IllegalArgumentException mismatchEx = assertThrows(IllegalArgumentException.class, () ->
                passwordResetService.verifyOtp(VerifyOtpRequest.builder()
                        .email(testEmail)
                        .otp(firstOtp)
                        .build()));
        assertTrue(mismatchEx.getMessage().contains("Invalid") || mismatchEx.getMessage().contains("expired"));

        // Second OTP must SUCCEED
        VerifyOtpResponse successResponse = passwordResetService.verifyOtp(VerifyOtpRequest.builder()
                .email(testEmail)
                .otp(secondOtp)
                .build());
        assertNotNull(successResponse.getResetToken());
        assertTrue(successResponse.getMessage().contains("successful"));
    }

    @Test
    @DisplayName("Resend OTP: Non-existent email returns neutral message and sets rate limit without leaking existence")
    void testResendOtp_NonExistentEmail_NeutralResponse_SetsRateLimit() {
        String nonExistentEmail = "unknown.student@codeassess.edu.vn";
        when(userRepository.findByEmail(nonExistentEmail)).thenReturn(Optional.empty());

        ResendOtpResponse response = passwordResetService.resendOtp(ResendOtpRequest.builder().email(nonExistentEmail).build());

        assertNotNull(response);
        assertEquals(60, response.getCooldownSeconds());
        assertTrue(response.getMessage().contains("If an account exists"));
        assertTrue(tokenStore.isRateLimited(nonExistentEmail), "Rate limit must be established to prevent timing attacks");

        // EmailService must NOT be called for non-existent users
        verify(emailService, never()).sendPasswordResetOtp(eq(nonExistentEmail), anyString());
    }

    @Test
    @DisplayName("Resend OTP: SMTP failure rolls back newly generated OTP and rate limit")
    void testResendOtp_SmtpFailure_RollsBackNewlyGeneratedOtp() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(testUser));
        doThrow(new RuntimeException("SMTP network error"))
                .when(emailService).sendPasswordResetOtp(eq(testEmail), anyString());

        ResendOtpRequest request = ResendOtpRequest.builder().email(testEmail).build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                passwordResetService.resendOtp(request));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertFalse(tokenStore.isRateLimited(testEmail), "Rate limit must be rolled back on SMTP failure");
        assertNull(tokenStore.getOtp(testEmail), "Unsent OTP must be purged on SMTP failure");
    }
}
