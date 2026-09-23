package com.codeassess.auth.mapper;

import com.codeassess.auth.dto.response.LoginResponse;
import com.codeassess.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public LoginResponse.UserSessionDto toUserSessionDto(User user) {
        if (user == null) {
            return null;
        }

        return LoginResponse.UserSessionDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}
