package com.codeassess.auth.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Locale;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordRequest {

    private String email;
    private String identity;

    public String getResolvedEmail() {
        if (email != null && !email.isBlank()) {
            return email.trim().toLowerCase(Locale.ROOT);
        }
        if (identity != null && !identity.isBlank()) {
            return identity.trim().toLowerCase(Locale.ROOT);
        }
        return "";
    }
}
