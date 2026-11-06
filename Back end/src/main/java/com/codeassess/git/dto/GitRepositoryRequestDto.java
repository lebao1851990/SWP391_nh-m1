package com.codeassess.git.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitRepositoryRequestDto {
    @NotBlank(message = "Repository name is required")
    private String name;

    @NotBlank(message = "Project ID is required")
    private String projectId;

    private String classId;

    @NotBlank(message = "Repository URL is required")
    private String repoUrl;

    private String defaultBranch;
    private String provider;
    private String accessToken; // Sanitized on storage, never echoed back
    private Set<String> memberUserIds;
}

