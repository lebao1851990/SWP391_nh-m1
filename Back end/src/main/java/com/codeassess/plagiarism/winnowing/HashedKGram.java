package com.codeassess.plagiarism.winnowing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HashedKGram {
    private long hash;
    private int position;
    private String nodeType;
    private int startLine;
    private int endLine;
}
