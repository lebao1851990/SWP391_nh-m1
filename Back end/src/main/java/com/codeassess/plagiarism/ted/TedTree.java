package com.codeassess.plagiarism.ted;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.*;

import java.util.*;

/**
 * Encapsulates an ordered labeled tree with Zhang-Shasha precomputations:
 * - 1-based postorder indexed nodes
 * - Leftmost leaf descendant (lld) array
 * - Keyroots array
 */
public class TedTree {

    private final TedNode root;
    private final List<TedNode> postorderNodes; // 1-based index (index 0 is null dummy)
    private final int[] lld; // 1-based
    private final int[] keyroots; // 1-based postorder indices

    public TedTree(TedNode root) {
        this.root = root;
        this.postorderNodes = new ArrayList<>();
        this.postorderNodes.add(null); // dummy for 1-based indexing

        if (root != null) {
            int[] counter = new int[]{0};
            computePostorderAndLld(root, counter, this.postorderNodes);
            int n = counter[0];
            this.lld = new int[n + 1];
            for (int i = 1; i <= n; i++) {
                this.lld[i] = this.postorderNodes.get(i).getLld();
            }
            this.keyroots = computeKeyroots(n, this.lld);
        } else {
            this.lld = new int[1];
            this.keyroots = new int[0];
        }
    }

    private void computePostorderAndLld(TedNode node, int[] counter, List<TedNode> list) {
        for (TedNode child : node.getChildren()) {
            computePostorderAndLld(child, counter, list);
        }
        counter[0]++;
        int currentId = counter[0];
        node.setId(currentId);
        list.add(node);

        if (node.getChildren().isEmpty()) {
            node.setLld(currentId);
        } else {
            // Leftmost leaf descendant of node is the lld of its first child
            node.setLld(node.getChildren().get(0).getLld());
        }
    }

    private int[] computeKeyroots(int n, int[] lldArray) {
        // In Zhang-Shasha: keyroots = { k | there is no k' > k such that lld(k') == lld(k) }
        boolean[] visited = new boolean[n + 1];
        List<Integer> krList = new ArrayList<>();
        for (int k = n; k >= 1; k--) {
            int leftLeaf = lldArray[k];
            if (!visited[leftLeaf]) {
                visited[leftLeaf] = true;
                krList.add(k);
            }
        }
        Collections.sort(krList);
        int[] result = new int[krList.size()];
        for (int i = 0; i < krList.size(); i++) {
            result[i] = krList.get(i);
        }
        return result;
    }

    public static TedTree fromCompilationUnit(CompilationUnit cu) {
        if (cu == null) {
            return new TedTree(null);
        }
        TedNode root = buildTedHierarchy(cu);
        return new TedTree(root);
    }

    private static TedNode buildTedHierarchy(Node astNode) {
        if (astNode == null || astNode instanceof Comment) {
            return null;
        }

        String label = determineNormalizedLabel(astNode);
        int startLine = astNode.getRange().map(r -> r.begin.line).orElse(1);
        int endLine = astNode.getRange().map(r -> r.end.line).orElse(startLine);

        TedNode tedNode = new TedNode(label, startLine, endLine);

        for (Node child : astNode.getChildNodes()) {
            if (child instanceof Comment) {
                continue;
            }
            TedNode childTed = buildTedHierarchy(child);
            if (childTed != null) {
                tedNode.addChild(childTed);
            }
        }

        return tedNode;
    }

    public static String determineNormalizedLabel(Node node) {
        String baseType = node.getClass().getSimpleName();

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
        } else if (node instanceof SimpleName || node instanceof Name) {
            return baseType;
        }

        return baseType;
    }

    public TedNode getRoot() {
        return root;
    }

    public int size() {
        return postorderNodes.size() - 1;
    }

    public TedNode getNode(int postorderId) {
        if (postorderId < 1 || postorderId > size()) {
            throw new IndexOutOfBoundsException("Invalid postorder id: " + postorderId);
        }
        return postorderNodes.get(postorderId);
    }

    public int getLld(int postorderId) {
        if (postorderId < 1 || postorderId > size()) {
            throw new IndexOutOfBoundsException("Invalid postorder id: " + postorderId);
        }
        return lld[postorderId];
    }

    public int[] getKeyroots() {
        return keyroots;
    }

    public int[] getLldArray() {
        return lld;
    }
}

