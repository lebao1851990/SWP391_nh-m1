package com.codeassess.plagiarism.normalizer;

import com.codeassess.plagiarism.parser.AstNode;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AstNormalizer {

    public List<AstNode> normalize(CompilationUnit cu) {
        List<AstNode> sequence = new ArrayList<>();
        traverse(cu, sequence);
        return sequence;
    }

    private void traverse(Node node, List<AstNode> sequence) {
        // Skip comments entirely to ensure they don't affect the structure
        if (node instanceof com.github.javaparser.ast.comments.Comment) {
            return;
        }

        String nodeType = determineNodeType(node);
        int startLine = node.getRange().map(r -> r.begin.line).orElse(-1);
        int endLine = node.getRange().map(r -> r.end.line).orElse(-1);

        // Only add nodes that have a valid line number and are not purely whitespace/comments
        if (startLine != -1) {
            sequence.add(new AstNode(nodeType, startLine, endLine));
        }

        for (Node child : node.getChildNodes()) {
            traverse(child, sequence);
        }
    }

    private String determineNodeType(Node node) {
        String baseType = node.getClass().getSimpleName();

        // Literals retain their values to ensure structurally distinct literals are differentiated.
        // E.g., price * 2 vs price * 100
        if (node instanceof IntegerLiteralExpr) {
            return baseType + "[" + ((IntegerLiteralExpr) node).getValue() + "]";
        } else if (node instanceof StringLiteralExpr) {
            return baseType + "[" + ((StringLiteralExpr) node).getValue() + "]";
        } else if (node instanceof BooleanLiteralExpr) {
            return baseType + "[" + ((BooleanLiteralExpr) node).getValue() + "]";
        } else if (node instanceof DoubleLiteralExpr) {
            return baseType + "[" + ((DoubleLiteralExpr) node).getValue() + "]";
        } else if (node instanceof LongLiteralExpr) {
            return baseType + "[" + ((LongLiteralExpr) node).getValue() + "]";
        } else if (node instanceof CharLiteralExpr) {
            return baseType + "[" + ((CharLiteralExpr) node).getValue() + "]";
        } else if (node instanceof NullLiteralExpr) {
            return baseType;
        } 
        
        // Identifiers and Names are normalized away (value stripped)
        else if (node instanceof SimpleName || node instanceof Name) {
            return baseType;
        }

        return baseType;
    }
}
