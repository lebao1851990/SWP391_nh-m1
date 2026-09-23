package com.codeassess.plagiarism.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Component;

@Component
public class JavaAstParser {

    public CompilationUnit parse(String sourceCode) {
        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Source code cannot be empty");
        }
        try {
            return StaticJavaParser.parse(sourceCode);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Java source code", e);
        }
    }
}
