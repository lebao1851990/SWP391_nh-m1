package com.codeassess.git.model;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitContributorMappingModel {
    private String id;
    private String repositoryId;
    private String gitAuthorName;
    private String gitAuthorEmail;
    private String userId;
    @Builder.Default
    private Instant mappedAt = Instant.now();
}

