package com.codeassess.plagiarism.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AstNode {
    private String nodeType;
    private int startLine;
    private int endLine;
}
