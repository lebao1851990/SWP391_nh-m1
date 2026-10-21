package com.codeassess.auth.controller;

import com.codeassess.auth.dto.request.LoginRequest;
import com.codeassess.auth.dto.response.LoginResponse;
import com.codeassess.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final com.codeassess.auth.mapper.AuthMapper authMapper;
    private final com.codeassess.auth.service.PasswordResetService passwordResetService;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody com.codeassess.auth.dto.request.RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response
    ) {
        LoginResponse loginResponse = authService.login(loginRequest, request);
        
        jakarta.servlet.http.Cookie roleCookie = new jakarta.servlet.http.Cookie("codeassess_role", loginResponse.getUser().getRole());
        roleCookie.setPath("/");
        roleCookie.setHttpOnly(false);
        roleCookie.setMaxAge(86400); // 1 day
        response.addCookie(roleCookie);

        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<com.codeassess.auth.dto.response.ForgotPasswordResponse> forgotPassword(
            @RequestBody com.codeassess.auth.dto.request.ForgotPasswordRequest request) {
        return ResponseEntity.ok(passwordResetService.requestPasswordReset(request));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<com.codeassess.auth.dto.response.ResendOtpResponse> resendOtp(
            @Valid @RequestBody com.codeassess.auth.dto.request.ResendOtpRequest request) {
        return ResponseEntity.ok(passwordResetService.resendOtp(request));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<com.codeassess.auth.dto.response.VerifyOtpResponse> verifyOtp(
            @Valid @RequestBody com.codeassess.auth.dto.request.VerifyOtpRequest request) {
        return ResponseEntity.ok(passwordResetService.verifyOtp(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<com.codeassess.auth.dto.response.ResetPasswordResponse> resetPassword(
            @Valid @RequestBody com.codeassess.auth.dto.request.ResetPasswordRequest request) {
        return ResponseEntity.ok(passwordResetService.resetPassword(request));
    }

    @org.springframework.web.bind.annotation.GetMapping("/me")
    public ResponseEntity<LoginResponse.UserSessionDto> getCurrentUser(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.codeassess.user.entity.User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authMapper.toUserSessionDto(currentUser));
    }
}
