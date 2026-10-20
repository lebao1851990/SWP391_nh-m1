package com.codeassess.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordRequest {

    @JsonAlias({"email", "identity"})
    private String identity;

    public String getTargetEmail() {
        if (identity == null) {
            return "";
        }
        return identity.trim();
    }
}
