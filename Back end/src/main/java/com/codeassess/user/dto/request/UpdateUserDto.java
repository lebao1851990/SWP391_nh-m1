package com.codeassess.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserDto {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "^(?i)(STUDENT|TEACHER|ADMIN)$", message = "Role must be student, teacher, or admin")
    private String role;
}
