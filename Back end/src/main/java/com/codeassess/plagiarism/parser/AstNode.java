package com.codeassess.plagiarism.parser;

import java.util.Objects;

public class AstNode {
    private String nodeType;
    private int startLine;
    private int endLine;

    public AstNode() {}

    public AstNode(String nodeType, int startLine, int endLine) {
        this.nodeType = nodeType;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AstNode astNode = (AstNode) o;
        return startLine == astNode.startLine && endLine == astNode.endLine && Objects.equals(nodeType, astNode.nodeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeType, startLine, endLine);
    }

    @Override
    public String toString() {
        return "AstNode(nodeType=" + nodeType + ", startLine=" + startLine + ", endLine=" + endLine + ")";
    }

    public static AstNodeBuilder builder() {
        return new AstNodeBuilder();
    }

    public static class AstNodeBuilder {
        private String nodeType;
        private int startLine;
        private int endLine;

        public AstNodeBuilder nodeType(String nodeType) {
            this.nodeType = nodeType;
            return this;
        }

        public AstNodeBuilder startLine(int startLine) {
            this.startLine = startLine;
            return this;
        }

        public AstNodeBuilder endLine(int endLine) {
            this.endLine = endLine;
            return this;
        }

        public AstNode build() {
            return new AstNode(nodeType, startLine, endLine);
        }
    }
}
