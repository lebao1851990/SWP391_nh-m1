package com.codeassess.plagiarism.tokenizer;

import com.codeassess.plagiarism.parser.AstNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KGram {
    private String concatenatedTypeString;
    private String representativeNodeType;
    private int startLine;
    private int endLine;
}
