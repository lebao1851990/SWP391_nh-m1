package com.codeassess.git.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContributorMappingRequestDto {
    @NotBlank(message = "Git author email is required")
    private String gitAuthorEmail;

    private String gitAuthorName;

    @NotBlank(message = "User ID is required")
    private String userId;
}

