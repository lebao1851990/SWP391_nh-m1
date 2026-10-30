package com.codeassess.plagiarism.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AstTreeComparisonDto {
    private String pairId;
    private String submissionAId;
    private String submissionBId;
    private String studentAName;
    private String studentBName;

    private AstTreeNodeDto treeA;
    private AstTreeNodeDto treeB;

    private int displayedNodesA;
    private int displayedNodesB;
    private int totalCountedNodesA;
    private int totalCountedNodesB;
    private boolean countCompletedA;
    private boolean countCompletedB;
    private int matchedRegionNodesCount;

    private boolean isTruncated;
    private String truncationReason; // "MAX_NODES_EXCEEDED", "MAX_DEPTH_EXCEEDED", null
    private String truncationNotice;

    private String parseStatus; // "SUCCESS", "SYNTAX_ERROR_A", "SYNTAX_ERROR_B", "SYNTAX_ERROR_BOTH", "EMPTY_SOURCE", "FILE_TOO_LARGE"
    private String parseErrorMessage;
}
