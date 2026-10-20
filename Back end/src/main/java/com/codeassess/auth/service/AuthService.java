package com.codeassess.auth.service;

import com.codeassess.auth.dto.request.LoginRequest;
import com.codeassess.auth.dto.response.LoginResponse;
import com.codeassess.auth.mapper.AuthMapper;
import com.codeassess.user.entity.User;
import com.codeassess.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final AuthMapper authMapper;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.codeassess.auth.repository.PasswordResetTokenRepository passwordResetTokenRepository;

    public void register(com.codeassess.auth.dto.request.RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new com.codeassess.common.exception.ConflictException("Email already exists");
        }

        com.codeassess.user.entity.Role role;
        try {
            role = com.codeassess.user.entity.Role.valueOf(request.getAccountRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role");
        }

        if (role == com.codeassess.user.entity.Role.ADMIN) {
            throw new IllegalArgumentException("Cannot register as ADMIN");
        }

        User user = User.builder()
                .email(request.getEmail())
                .fullName(request.getFullName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .build();

        userRepository.save(user);
    }

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        // Authenticate via Spring Security
        UsernamePasswordAuthenticationToken authToken = 
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword());
        
        Authentication authentication = authenticationManager.authenticate(authToken);

        // Establish Security Context
        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(authentication);

        // Establish HTTP Session
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        // Fetch User and Map to Response
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException("User not found after successful authentication"));

        return LoginResponse.builder()
                .user(authMapper.toUserSessionDto(user))
                .build();
    }

    @org.springframework.transaction.annotation.Transactional
    public void forgotPassword(com.codeassess.auth.dto.request.ForgotPasswordRequest request) {
        String email = request.getTargetEmail();
        if (email == null || email.isBlank()) {
            return;
        }

        java.util.Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            // Anti-enumeration: do not reveal that user doesn't exist
            return;
        }

        User user = userOpt.get();

        // Expire / invalidate any existing active tokens for this user
        passwordResetTokenRepository.findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
                .ifPresent(existingToken -> {
                    existingToken.setUsed(true);
                    passwordResetTokenRepository.save(existingToken);
                });

        String rawToken = java.util.UUID.randomUUID().toString().replace("-", "") 
                + java.util.UUID.randomUUID().toString().replace("-", "");

        com.codeassess.auth.entity.PasswordResetToken tokenEntity = com.codeassess.auth.entity.PasswordResetToken.builder()
                .token(rawToken)
                .user(user)
                .expiresAt(java.time.LocalDateTime.now().plusMinutes(30))
                .used(false)
                .build();

        passwordResetTokenRepository.save(tokenEntity);
        org.slf4j.LoggerFactory.getLogger(AuthService.class).info(
                "[AUTH-DEV] Password reset token generated for {}: token={}", user.getEmail(), rawToken);
    }

    @org.springframework.transaction.annotation.Transactional
    public void resetPassword(com.codeassess.auth.dto.request.ResetPasswordRequest request) {
        if (request.getToken() == null || request.getToken().trim().isEmpty()) {
            throw new IllegalArgumentException("Token is required");
        }
        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (request.getConfirmPassword() != null && !request.getConfirmPassword().isBlank()
                && !request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        com.codeassess.auth.entity.PasswordResetToken tokenEntity = passwordResetTokenRepository
                .findByToken(request.getToken().trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired password reset token"));

        if (tokenEntity.isUsed()) {
            throw new IllegalArgumentException("This password reset token has already been used");
        }

        if (tokenEntity.isExpired()) {
            throw new IllegalArgumentException("Password reset token has expired");
        }

        User user = tokenEntity.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        tokenEntity.setUsed(true);
        passwordResetTokenRepository.save(tokenEntity);
        org.slf4j.LoggerFactory.getLogger(AuthService.class).info(
                "[AUTH] Password successfully updated for user {}", user.getEmail());
    }
}
