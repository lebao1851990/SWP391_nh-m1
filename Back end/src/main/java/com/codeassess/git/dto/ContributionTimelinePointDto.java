package com.codeassess.git.dto;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContributionTimelinePointDto {
    private String date; // YYYY-MM-DD
    private int totalCommits;
    private int linesAdded;
    private int linesDeleted;
    private int netLines;
    private Map<String, Integer> commitsByContributor; // authorEmail -> count
}

