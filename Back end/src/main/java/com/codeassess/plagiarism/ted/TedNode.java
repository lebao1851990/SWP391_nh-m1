package com.codeassess.plagiarism.ted;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Node representation for Zhang-Shasha Tree Edit Distance algorithm.
 * Uses 1-based postorder indexing and tracks leftmost leaf descendants (lld).
 */
public class TedNode {
    private int id; // 1-based postorder index
    private String label;
    private int startLine;
    private int endLine;
    private int lld; // 1-based postorder index of leftmost leaf descendant
    private final List<TedNode> children = new ArrayList<>();
    private TedNode parent;

    public TedNode() {}

    public TedNode(String label) {
        this.label = label;
    }

    public TedNode(String label, int startLine, int endLine) {
        this.label = label;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    public void addChild(TedNode child) {
        if (child != null) {
            children.add(child);
            child.setParent(this);
        }
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
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

    public int getLld() {
        return lld;
    }

    public void setLld(int lld) {
        this.lld = lld;
    }

    public List<TedNode> getChildren() {
        return children;
    }

    public TedNode getParent() {
        return parent;
    }

    public void setParent(TedNode parent) {
        this.parent = parent;
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TedNode tedNode = (TedNode) o;
        return id == tedNode.id && Objects.equals(label, tedNode.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, label);
    }

    @Override
    public String toString() {
        return "TedNode(id=" + id + ", label=" + label + ", lld=" + lld + ")";
    }
}

