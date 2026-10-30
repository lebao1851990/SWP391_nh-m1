package com.codeassess.plagiarism.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AstTreeNodeDto {
    private String nodeId;
    private String nodeType;
    private String label;
    private int startLine;
    private int endLine;
    private String matchStatus; // "MATCHED_REGION", "CONTEXT", "UNMATCHED"
    private String matchedNodeId; // Always null (no artificial 1-to-1 links)
    private String evidenceId; // Nullable
    private boolean hasHiddenChildren;
    
    @Builder.Default
    private List<AstTreeNodeDto> children = new ArrayList<>();
}
