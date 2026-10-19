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
    public ResponseEntity<java.util.Map<String, String>> forgotPassword(
            @Valid @RequestBody com.codeassess.auth.dto.request.ForgotPasswordRequest request
    ) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(java.util.Map.of(
                "message", "Nếu tài khoản tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi."
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<java.util.Map<String, String>> resetPassword(
            @Valid @RequestBody com.codeassess.auth.dto.request.ResetPasswordRequest request
    ) {
        authService.resetPassword(request);
        return ResponseEntity.ok(java.util.Map.of(
                "message", "Mật khẩu đã được cập nhật thành công. Vui lòng đăng nhập lại."
        ));
    }
}
