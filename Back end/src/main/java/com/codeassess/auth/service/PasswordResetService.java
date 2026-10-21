package com.codeassess.auth.service;

import com.codeassess.auth.dto.request.ForgotPasswordRequest;
import com.codeassess.auth.dto.request.ResendOtpRequest;
import com.codeassess.auth.dto.request.ResetPasswordRequest;
import com.codeassess.auth.dto.request.VerifyOtpRequest;
import com.codeassess.auth.dto.response.ForgotPasswordResponse;
import com.codeassess.auth.dto.response.ResendOtpResponse;
import com.codeassess.auth.dto.response.ResetPasswordResponse;
import com.codeassess.auth.dto.response.VerifyOtpResponse;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final PasswordResetTokenStore tokenStore;
    private final SecureRandom secureRandom;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Duration OTP_EXPIRY = Duration.ofMinutes(10);
    private static final Duration RATE_LIMIT_EXPIRY = Duration.ofSeconds(60);
    private static final Duration RESET_TOKEN_EXPIRY = Duration.ofMinutes(15);
    private static final Duration RESET_TOKEN_CLAIM_TTL = Duration.ofSeconds(15);
    private static final int MAX_OTP_ATTEMPTS = 5;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            PasswordResetTokenStore tokenStore
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.tokenStore = tokenStore;
        this.secureRandom = new SecureRandom();
    }

    public ForgotPasswordResponse requestPasswordReset(ForgotPasswordRequest request) {
        String email = request.getResolvedEmail();
        if (email.isBlank() || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("A valid university email address is required.");
        }

        if (tokenStore.isRateLimited(email)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, 
                    "Please wait 60 seconds before requesting another verification code.");
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            // Cryptographically secure 6-digit numeric OTP
            int code = 100_000 + secureRandom.nextInt(900_000);
            String otp = String.valueOf(code);
            String hashedOtp = sha256Hex(otp);

            tokenStore.saveOtp(email, hashedOtp, OTP_EXPIRY);
            tokenStore.setRateLimit(email, RATE_LIMIT_EXPIRY);

            // Send OTP via configured email service (Never logs plaintext OTP)
            try {
                emailService.sendPasswordResetOtp(email, otp);
            } catch (Exception e) {
                log.error("Failed to send OTP verification email for masked email {}: {}", maskEmail(email), e.getMessage());
                // Atomically roll back OTP and rate limit so user is not locked out for 60 seconds
                tokenStore.rollbackOtpAndRateLimit(email, hashedOtp);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Unable to send verification email. Please try again later.");
            }
        } else {
            // Neutral behavior: Record rate limit to prevent timing attacks, do not leak user existence
            tokenStore.setRateLimit(email, RATE_LIMIT_EXPIRY);
        }

        return ForgotPasswordResponse.builder()
                .message("If an account exists with this email, a verification code has been sent.")
                .build();
    }

    public ResendOtpResponse resendOtp(ResendOtpRequest request) {
        String email = request.getResolvedEmail();
        if (email.isBlank() || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("A valid university email address is required.");
        }

        if (tokenStore.isRateLimited(email)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait 60 seconds before requesting another verification code.");
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            // Cryptographically secure 6-digit numeric OTP
            int code = 100_000 + secureRandom.nextInt(900_000);
            String otp = String.valueOf(code);
            String hashedOtp = sha256Hex(otp);

            // Atomically overwrite previous OTP and reset attempt counter
            tokenStore.saveOtp(email, hashedOtp, OTP_EXPIRY);
            tokenStore.setRateLimit(email, RATE_LIMIT_EXPIRY);

            // Send OTP via configured email service (Never logs plaintext OTP)
            try {
                emailService.sendPasswordResetOtp(email, otp);
            } catch (Exception e) {
                log.error("Failed to resend OTP verification email for masked email {}: {}", maskEmail(email), e.getMessage());
                // Atomically roll back OTP and rate limit so user is not locked out for 60 seconds
                tokenStore.rollbackOtpAndRateLimit(email, hashedOtp);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Unable to send verification email. Please try again later.");
            }
        } else {
            // Neutral behavior: Record rate limit to prevent timing attacks, do not leak user existence
            tokenStore.setRateLimit(email, RATE_LIMIT_EXPIRY);
        }

        return ResendOtpResponse.builder()
                .message("If an account exists with this email, a new verification code has been sent.")
                .cooldownSeconds((int) RATE_LIMIT_EXPIRY.toSeconds())
                .build();
    }

    public VerifyOtpResponse verifyOtp(VerifyOtpRequest request) {
        String email = request.getResolvedEmail();
        String otp = request.getResolvedOtp();

        if (email.isBlank() || otp.length() != 6) {
            throw new IllegalArgumentException("Valid email and 6-digit verification code are required.");
        }

        String inputHashed = sha256Hex(otp);
        PasswordResetTokenStore.VerifyResult result = tokenStore.verifyAndConsumeOtp(email, inputHashed, MAX_OTP_ATTEMPTS);

        switch (result.status()) {
            case SUCCESS -> {
                // Generate one-time short-lived cryptographically secure reset token
                byte[] tokenBytes = new byte[32];
                secureRandom.nextBytes(tokenBytes);
                String resetToken = HexFormat.of().formatHex(tokenBytes);
                String hashedToken = sha256Hex(resetToken);

                tokenStore.saveResetToken(hashedToken, email, RESET_TOKEN_EXPIRY);

                return VerifyOtpResponse.builder()
                        .resetToken(resetToken)
                        .message("Verification code verified successfully.")
                        .build();
            }
            case MAX_ATTEMPTS_EXCEEDED -> 
                throw new IllegalArgumentException("Too many invalid attempts. Please request a new verification code.");
            case MISMATCH -> 
                throw new IllegalArgumentException("Invalid verification code. Please check and try again.");
            case NOT_FOUND_OR_EXPIRED -> 
                throw new IllegalArgumentException("Verification code has expired or is invalid. Please request a new one.");
            default -> 
                throw new IllegalArgumentException("Unable to verify verification code.");
        }
    }

    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request) {
        String token = request.getToken();
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Reset token is required.");
        }

        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }

        String hashedToken = sha256Hex(token);

        // Atomic claim lock with unique ownership token (fencing)
        String claimOwner = java.util.UUID.randomUUID().toString();
        boolean claimed = tokenStore.claimResetToken(hashedToken, claimOwner, RESET_TOKEN_CLAIM_TTL);
        if (!claimed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Password reset is already in progress for this token. Please wait.");
        }

        try {
            String email = tokenStore.getResetTokenEmail(hashedToken);
            if (email == null) {
                tokenStore.releaseResetTokenClaim(hashedToken, claimOwner);
                throw new IllegalArgumentException("Reset token is invalid or has expired. Please request a new one.");
            }

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("Associated user account not found."));

            Instant nowInstant = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            LocalDateTime nowDateTime = LocalDateTime.ofInstant(nowInstant, ZoneId.systemDefault());

            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            user.setPasswordChangedAt(nowDateTime);
            userRepository.save(user);

            // Record password update timestamp to invalidate active sessions across all instances
            tokenStore.recordPasswordChanged(email, nowInstant);

            // Single-Use Token: Mark consumed only if current request still owns the claim
            tokenStore.consumeResetToken(hashedToken, claimOwner);

            return ResetPasswordResponse.builder()
                    .message("Password has been successfully reset. You may now log in with your new password.")
            .build();
        } catch (IllegalArgumentException | ResponseStatusException e) {
            tokenStore.releaseResetTokenClaim(hashedToken, claimOwner);
            throw e;
        } catch (Exception e) {
            log.error("Error executing database password reset for token: {}", e.getMessage());
            tokenStore.releaseResetTokenClaim(hashedToken, claimOwner);
            throw e;
        }
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
    }
}
