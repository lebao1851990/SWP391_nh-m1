package com.codeassess.plagiarism.service;

import com.codeassess.plagiarism.dto.AstTreeComparisonDto;
import com.codeassess.plagiarism.dto.AstTreeNodeDto;
import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import com.codeassess.plagiarism.entity.SimilarityPair;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class AstTreeBuilderService {

    public static final int MAX_INPUT_SIZE_BYTES = 500 * 1024; // 500 KB
    public static final int MAX_INPUT_LINES = 5000;
    public static final int MAX_DISPLAYED_NODES = 500;
    public static final int MAX_DEPTH = 15;

    public AstTreeComparisonDto buildComparisonTree(SimilarityPair pair, List<PlagiarismEvidence> evidences) {
        if (pair == null) {
            throw new IllegalArgumentException("SimilarityPair cannot be null");
        }

        String pairId = pair.getId() != null ? String.valueOf(pair.getId()) : "";
        String subAId = pair.getSubmissionA() != null ? pair.getSubmissionA().getId() : "";
        String subBId = pair.getSubmissionB() != null ? pair.getSubmissionB().getId() : "";
        String studentAName = (pair.getSubmissionA() != null && pair.getSubmissionA().getStudent() != null)
                ? pair.getSubmissionA().getStudent().getFullName() : "Sinh viên A";
        String studentBName = (pair.getSubmissionB() != null && pair.getSubmissionB().getStudent() != null)
                ? pair.getSubmissionB().getStudent().getFullName() : "Sinh viên B";

        String codeA = pair.getSubmissionA() != null ? pair.getSubmissionA().getSourceCode() : null;
        String codeB = pair.getSubmissionB() != null ? pair.getSubmissionB().getSourceCode() : null;

        List<PlagiarismEvidence> safeEvidences = evidences != null ? evidences : Collections.emptyList();

        // 1. Validate Input Size & Emptiness
        String preCheckStatus = validateInputSizes(codeA, codeB);
        if (preCheckStatus != null) {
            return AstTreeComparisonDto.builder()
                    .pairId(pairId)
                    .submissionAId(subAId)
                    .submissionBId(subBId)
                    .studentAName(studentAName)
                    .studentBName(studentBName)
                    .parseStatus(preCheckStatus)
                    .parseErrorMessage(getPreCheckErrorMessage(preCheckStatus))
                    .build();
        }

        // 2. Parse JavaParser ASTs
        ParsedResult resultA = parseSourceSafely(codeA, "A");
        ParsedResult resultB = parseSourceSafely(codeB, "B");

        if (!resultA.success || !resultB.success) {
            String combinedStatus = determineSyntaxErrorStatus(resultA.success, resultB.success);
            String combinedError = (!resultA.success ? "Bài A: " + resultA.errorMessage : "")
                    + (!resultA.success && !resultB.success ? " | " : "")
                    + (!resultB.success ? "Bài B: " + resultB.errorMessage : "");

            return AstTreeComparisonDto.builder()
                    .pairId(pairId)
                    .submissionAId(subAId)
                    .submissionBId(subBId)
                    .studentAName(studentAName)
                    .studentBName(studentBName)
                    .parseStatus(combinedStatus)
                    .parseErrorMessage(combinedError)
                    .build();
        }

        // 3. Build Presentation Trees with Deterministic Truncation & Classification
        TreeBuildContext ctxA = new TreeBuildContext("A", safeEvidences, true);
        AstTreeNodeDto treeA = buildTreeNode(resultA.cu, ctxA, 0, "A-0");

        TreeBuildContext ctxB = new TreeBuildContext("B", safeEvidences, false);
        AstTreeNodeDto treeB = buildTreeNode(resultB.cu, ctxB, 0, "B-0");

        boolean isTruncated = ctxA.isTruncated || ctxB.isTruncated;
        String truncationReason = ctxA.truncationReason != null ? ctxA.truncationReason : ctxB.truncationReason;
        String truncationNotice = isTruncated
                ? String.format("Cây cú pháp đã được rút gọn an toàn (Bài A: %d/%d nút, Bài B: %d/%d nút) để bảo đảm hiệu năng trình duyệt.",
                ctxA.displayedNodes, ctxA.totalCounted, ctxB.displayedNodes, ctxB.totalCounted)
                : null;

        int matchedCount = ctxA.matchedRegionCount + ctxB.matchedRegionCount;

        return AstTreeComparisonDto.builder()
                .pairId(pairId)
                .submissionAId(subAId)
                .submissionBId(subBId)
                .studentAName(studentAName)
                .studentBName(studentBName)
                .treeA(treeA)
                .treeB(treeB)
                .displayedNodesA(ctxA.displayedNodes)
                .displayedNodesB(ctxB.displayedNodes)
                .totalCountedNodesA(ctxA.totalCounted)
                .totalCountedNodesB(ctxB.totalCounted)
                .countCompletedA(ctxA.countCompleted)
                .countCompletedB(ctxB.countCompleted)
                .matchedRegionNodesCount(matchedCount)
                .isTruncated(isTruncated)
                .truncationReason(truncationReason)
                .truncationNotice(truncationNotice)
                .parseStatus("SUCCESS")
                .build();
    }

    private String validateInputSizes(String codeA, String codeB) {
        if (codeA == null || codeA.trim().isEmpty() || codeB == null || codeB.trim().isEmpty()) {
            return "EMPTY_SOURCE";
        }
        if (codeA.length() > MAX_INPUT_SIZE_BYTES || codeB.length() > MAX_INPUT_SIZE_BYTES) {
            return "FILE_TOO_LARGE";
        }
        if (countLines(codeA) > MAX_INPUT_LINES || countLines(codeB) > MAX_INPUT_LINES) {
            return "FILE_TOO_LARGE";
        }
        return null;
    }

    private int countLines(String text) {
        if (text == null) return 0;
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') lines++;
        }
        return lines;
    }

    private String getPreCheckErrorMessage(String status) {
        if ("EMPTY_SOURCE".equals(status)) {
            return "Một hoặc cả hai bài nộp không chứa nội dung mã nguồn để phân tích cây cú pháp.";
        }
        if ("FILE_TOO_LARGE".equals(status)) {
            return "Kích thước tập tin vượt quá giới hạn an toàn (tối đa 500 KB hoặc 5.000 dòng).";
        }
        return null;
    }

    private String determineSyntaxErrorStatus(boolean successA, boolean successB) {
        if (!successA && !successB) return "SYNTAX_ERROR_BOTH";
        if (!successA) return "SYNTAX_ERROR_A";
        return "SYNTAX_ERROR_B";
    }

    private ParsedResult parseSourceSafely(String sourceCode, String label) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(sourceCode);
            return new ParsedResult(true, cu, null);
        } catch (Throwable t) {
            log.warn("Không thể phân tích cây cú pháp AST cho bài nộp {}: {}", label, t.getMessage());
            return new ParsedResult(false, null, t.getMessage());
        }
    }

    private AstTreeNodeDto buildTreeNode(Node node, TreeBuildContext ctx, int depth, String nodeId) {
        if (node == null) return null;

        ctx.totalCounted++;

        String nodeType = node.getClass().getSimpleName();
        int startLine = node.getRange().map(r -> r.begin.line).orElse(1);
        int endLine = node.getRange().map(r -> r.end.line).orElse(startLine);
        String label = generateSafeNodeLabel(node, nodeType);

        // Classify Match Status based on evidence spans
        ClassificationResult classification = classifyNode(startLine, endLine, ctx);

        if (classification.status.equals("MATCHED_REGION")) {
            ctx.matchedRegionCount++;
        }

        // Check Truncation Caps
        if (ctx.displayedNodes >= MAX_DISPLAYED_NODES || depth >= MAX_DEPTH) {
            ctx.isTruncated = true;
            if (ctx.displayedNodes >= MAX_DISPLAYED_NODES && ctx.truncationReason == null) {
                ctx.truncationReason = "MAX_NODES_EXCEEDED";
            } else if (ctx.truncationReason == null) {
                ctx.truncationReason = "MAX_DEPTH_EXCEEDED";
            }
            return AstTreeNodeDto.builder()
                    .nodeId(nodeId)
                    .nodeType(nodeType)
                    .label(label)
                    .startLine(startLine)
                    .endLine(endLine)
                    .matchStatus(classification.status)
                    .matchedNodeId(null) // Truthful: always null
                    .evidenceId(classification.evidenceId)
                    .hasHiddenChildren(!getMeaningfulChildren(node).isEmpty())
                    .children(new ArrayList<>())
                    .build();
        }

        ctx.displayedNodes++;

        List<Node> children = getMeaningfulChildren(node);
        List<AstTreeNodeDto> childDtos = new ArrayList<>();

        int childIdx = 0;
        for (Node child : children) {
            String childNodeId = nodeId + "-" + childIdx;
            AstTreeNodeDto childDto = buildTreeNode(child, ctx, depth + 1, childNodeId);
            if (childDto != null) {
                childDtos.add(childDto);
                childIdx++;
            }
        }

        return AstTreeNodeDto.builder()
                .nodeId(nodeId)
                .nodeType(nodeType)
                .label(label)
                .startLine(startLine)
                .endLine(endLine)
                .matchStatus(classification.status)
                .matchedNodeId(null) // Truthful: always null
                .evidenceId(classification.evidenceId)
                .hasHiddenChildren(false)
                .children(childDtos)
                .build();
    }

    private List<Node> getMeaningfulChildren(Node node) {
        List<Node> meaningful = new ArrayList<>();
        for (Node child : node.getChildNodes()) {
            if (child instanceof Comment) {
                continue;
            }
            // Skip pure micro-token leaf nodes that are folded into parent labels
            if (child instanceof SimpleName || child instanceof Modifier) {
                continue;
            }
            meaningful.add(child);
        }
        return meaningful;
    }

    private ClassificationResult classifyNode(int startLine, int endLine, TreeBuildContext ctx) {
        PlagiarismEvidence bestMatchingEvidence = null;
        int narrowestSpan = Integer.MAX_VALUE;

        boolean isEnclosingAncestor = false;

        for (PlagiarismEvidence ev : ctx.evidences) {
            int evStart = ctx.isSubmissionA ? (ev.getStartLineA() != null ? ev.getStartLineA() : 1)
                    : (ev.getStartLineB() != null ? ev.getStartLineB() : 1);
            int evEnd = ctx.isSubmissionA ? (ev.getEndLineA() != null ? ev.getEndLineA() : evStart)
                    : (ev.getEndLineB() != null ? ev.getEndLineB() : evStart);

            // 1. Is node completely inside evidence span?
            if (evStart <= startLine && endLine <= evEnd) {
                int span = evEnd - evStart;
                if (span < narrowestSpan) {
                    narrowestSpan = span;
                    bestMatchingEvidence = ev;
                }
            }

            // 2. Is node an enclosing ancestor of the evidence?
            if (startLine <= evStart && endLine >= evEnd && (startLine < evStart || endLine > evEnd)) {
                isEnclosingAncestor = true;
            }
        }

        if (bestMatchingEvidence != null) {
            return new ClassificationResult("MATCHED_REGION", bestMatchingEvidence.getId());
        }

        if (isEnclosingAncestor) {
            return new ClassificationResult("CONTEXT", null);
        }

        return new ClassificationResult("UNMATCHED", null);
    }

    private String generateSafeNodeLabel(Node node, String nodeType) {
        try {
            if (node instanceof CompilationUnit) {
                return "CompilationUnit (Root)";
            }
            if (node instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration cid = (ClassOrInterfaceDeclaration) node;
                return (cid.isInterface() ? "interface " : "class ") + cid.getNameAsString();
            }
            if (node instanceof MethodDeclaration) {
                MethodDeclaration md = (MethodDeclaration) node;
                return md.getType() + " " + md.getNameAsString() + "(" + md.getParameters().size() + " params)";
            }
            if (node instanceof ConstructorDeclaration) {
                ConstructorDeclaration cd = (ConstructorDeclaration) node;
                return cd.getNameAsString() + "(" + cd.getParameters().size() + " params)";
            }
            if (node instanceof ForStmt) {
                ForStmt fs = (ForStmt) node;
                return "for (" + fs.getCompare().map(Object::toString).orElse("...") + ")";
            }
            if (node instanceof ForEachStmt) {
                ForEachStmt fes = (ForEachStmt) node;
                return "for (" + fes.getVariable() + " : " + fes.getIterable() + ")";
            }
            if (node instanceof WhileStmt) {
                WhileStmt ws = (WhileStmt) node;
                return "while (" + ws.getCondition() + ")";
            }
            if (node instanceof DoStmt) {
                DoStmt ds = (DoStmt) node;
                return "do ... while (" + ds.getCondition() + ")";
            }
            if (node instanceof IfStmt) {
                IfStmt is = (IfStmt) node;
                return "if (" + is.getCondition() + ")";
            }
            if (node instanceof ReturnStmt) {
                ReturnStmt rs = (ReturnStmt) node;
                return "return " + rs.getExpression().map(Object::toString).orElse("");
            }
            if (node instanceof VariableDeclarationExpr) {
                VariableDeclarationExpr vde = (VariableDeclarationExpr) node;
                return vde.toString().replaceAll("\\s+", " ").trim();
            }
            if (node instanceof BlockStmt) {
                return "{ ... } (" + ((BlockStmt) node).getStatements().size() + " stmts)";
            }
            if (node instanceof BinaryExpr) {
                BinaryExpr be = (BinaryExpr) node;
                return be.getOperator() + " (" + nodeType + ")";
            }

            // Fallback representation truncated safely
            String raw = node.toString().replaceAll("\\s+", " ").trim();
            if (raw.length() > 60) {
                raw = raw.substring(0, 57) + "...";
            }
            return raw.isEmpty() ? nodeType : raw;
        } catch (Exception e) {
            return nodeType;
        }
    }

    private static class TreeBuildContext {
        final String label;
        final List<PlagiarismEvidence> evidences;
        final boolean isSubmissionA;

        int displayedNodes = 0;
        int totalCounted = 0;
        int matchedRegionCount = 0;
        boolean countCompleted = true;
        boolean isTruncated = false;
        String truncationReason = null;

        TreeBuildContext(String label, List<PlagiarismEvidence> evidences, boolean isSubmissionA) {
            this.label = label;
            this.evidences = evidences;
            this.isSubmissionA = isSubmissionA;
        }
    }

    private static class ClassificationResult {
        final String status;
        final String evidenceId;

        ClassificationResult(String status, String evidenceId) {
            this.status = status;
            this.evidenceId = evidenceId;
        }
    }

    private static class ParsedResult {
        final boolean success;
        final CompilationUnit cu;
        final String errorMessage;

        ParsedResult(boolean success, CompilationUnit cu, String errorMessage) {
            this.success = success;
            this.cu = cu;
            this.errorMessage = errorMessage;
        }
    }
}
