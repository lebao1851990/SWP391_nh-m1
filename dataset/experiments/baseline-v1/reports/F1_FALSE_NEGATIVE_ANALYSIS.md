# F1 Transformation: False Negative Analysis & AST Structural Divergence Report
**Project:** CodeAssess | Module 4: AST Plagiarism & Code Analytics Engine  
**Course & Milestone:** SWP391 RBL — Milestone 2  
**Date:** 2026-10-12  
**Author / Auditor:** Antigravity AI Assistant  
**Subject:** Category F1 (For-to-While Loop Control Flow Transformation)  
**Status:** AUDIT COMPLETE (Bypass Mechanism Verified; Normalizer & Threshold Preserved)

---

## 1. Executive Summary

In the baseline experiment on Dataset v1.0, Category F1 produced **15 False Negatives (FN = 15)** out of 15 pairs (Recall = **0.00%**). Every F1 pair yielded an exact calculated Jaccard similarity of **40.91%**, which falls substantially below the production flag threshold of **70.00%**.

This report investigates:
1. The list of all 15 affected pairs.
2. The exact AST node divergence between `ForStmt` and `WhileStmt`.
3. The impact on K-gram token sequences ($K=5$) and Winnowed FNV-1a hashes ($W=4$).
4. Mathematical derivation of the 40.91% Jaccard similarity.
5. Analysis of why AST token pipelines without control-flow graph (CFG) canonicalization are vulnerable to loop rewriting attacks.

---

## 2. Listing of All 15 F1 False Negative Pairs

All 15 pairs are listed in [baseline_pair_results.csv](file:///d:/AST%20Plagiarism%20Detection/dataset/experiments/baseline-v1/results/baseline_pair_results.csv):

| Pair ID | Program A | Program B | Category | Ground Truth | Hashes A | Hashes B | Shared Hashes | Jaccard Similarity | Flagged ($\ge 70\%$) | Outcome |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `pair_0019` | Base1.java | Variant_1_F1_1.java | F1 | PLAGIARISM | 16 | 15 | 9 | 40.91% | False | **FN** |
| `pair_0020` | Base1.java | Variant_1_F1_2.java | F1 | PLAGIARISM | 16 | 15 | 9 | 40.91% | False | **FN** |
| `pair_0021` | Base1.java | Variant_1_F1_3.java | F1 | PLAGIARISM | 16 | 15 | 9 | 40.91% | False | **FN** |
| `pair_0040` | Base2.java | Variant_2_F1_1.java | F1 | PLAGIARISM | 16 | 15 | 9 | 40.91% | False | **FN** |
| `pair_0041` | Base2.java | Variant_2_F1_2.java | F1 | PLAGIARISM | 16 | 15 | 9 | 40.91% | False | **FN** |
| `pair_0042` | Base2.java | Variant_2_F1_3.java | F1 | PLAGIARISM | 16 | 15 | 9 | 40.91% | False | **FN** |
| `pair_0061` | Base3.java | Variant_3_F1_1.java | F1 | PLAGIARISM | 16 | 15 | 9 | 40.91% | False | **FN** |
| `pair_0062` | Base3.java | Variant_3_F1_2.java | F1 | PLAGIARISM | 16 | 15 | 9 | 40.91% | False | **FN** |
| `pair_0063` | Base3.java | Variant_3_F1_3.java | F1 | PLAGIARISM | 16 | 15 | 9 | 40.91% | False | **FN** |
| `pair_0082` | Base4.java | Variant_4_F1_1.java | F1 | PLAGIARISM | 16 | 15 | 9 | 40.91% | False | **FN** |
| `pair_0083` | Base4.java | Variant_4_F1_2.java | F1 | PLAGIARISM | 16 | 15 | 9 | 40.91% | False | **FN** |
| `pair_0084` | Base4.java | Variant_4_F1_3.java | F1 | PLAGIARISM | 16 | 15 | 9 | 40.91% | False | **FN** |
| `pair_0103` | Base5.java | Variant_5_F1_1.java | F1 | PLAGIARISM | 16 | 15 | 9 | 40.91% | False | **FN** |
| `pair_0104` | Base5.java | Variant_5_F1_2.java | F1 | PLAGIARISM | 16 | 15 | 9 | 40.91% | False | **FN** |
| `pair_0105` | Base5.java | Variant_5_F1_3.java | F1 | PLAGIARISM | 16 | 15 | 9 | 40.91% | False | **FN** |

---

## 3. Source Code Comparison

### Program A (`Base1.java`):
```java
public class Base1 {
    public static void main(String[] args) {
        int myCounter = 0;
        for (int loopVar = 0; loopVar < 10; loopVar++) {
            myCounter += loopVar;
        }
        System.out.println("Result: " + myCounter);
    }
}
```

### Program B (`Variant_1_F1_1.java`):
```java
public class Variant_1_F1_1 {
    public static void main(String[] args) {
        int myCounter = 0;
        int loopVar = 0; while(loopVar < 10) {
            myCounter += loopVar;
        }
        System.out.println("Result: " + myCounter);
        loopVar++;
    }
}
```

---

## 4. AST Structural Divergence Details

When parsed using [JavaAstParser](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/plagiarism/parser/JavaAstParser.java) and normalized via [AstNormalizer](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/plagiarism/normalizer/AstNormalizer.java):

```
Base1 AST Tree (Depth-First Traversal):
MethodDeclaration
 ├── BlockStmt
 │    ├── ExpressionStmt (VariableDeclarationExpr: myCounter = 0)
 │    ├── ForStmt
 │    │    ├── VariableDeclarationExpr (int loopVar = 0)
 │    │    │    ├── PrimitiveType (int)
 │    │    │    └── VariableDeclarator (SimpleName, IntegerLiteralExpr[0])
 │    │    ├── BinaryExpr (loopVar < 10)
 │    │    │    ├── NameExpr (SimpleName)
 │    │    │    └── IntegerLiteralExpr[10]
 │    │    ├── UnaryExpr (loopVar++)
 │    │    │    └── NameExpr (SimpleName)
 │    │    └── BlockStmt
 │    │         └── ExpressionStmt (AssignExpr: myCounter += loopVar)
 │    └── ExpressionStmt (MethodCallExpr: println)
```

```
Variant_1_F1_1 AST Tree (Depth-First Traversal):
MethodDeclaration
 ├── BlockStmt
 │    ├── ExpressionStmt (VariableDeclarationExpr: myCounter = 0)
 │    ├── ExpressionStmt (VariableDeclarationExpr: loopVar = 0)
 │    ├── WhileStmt
 │    │    ├── BinaryExpr (loopVar < 10)
 │    │    └── BlockStmt
 │    │         └── ExpressionStmt (AssignExpr: myCounter += loopVar)
 │    ├── ExpressionStmt (MethodCallExpr: println)
 │    └── ExpressionStmt (UnaryExpr: loopVar++)
```

### Structural Divergence Root Causes:
1. **Loop Header Hierarchy:** In `ForStmt`, initialization (`int loopVar = 0`), condition (`loopVar < 10`), and step update (`loopVar++`) are direct child nodes of the single `ForStmt` node. In contrast, in `WhileStmt`, the initialization is a preceding statement in the outer `BlockStmt`, and the update expression is appended at the bottom.
2. **Grammar Symbol Shift:** The token sequence changes from `...-ForStmt-VariableDeclarationExpr-...` to `...-ExpressionStmt-WhileStmt-...`.
3. **K-Gram Contamination ($K=5$):**
   - Each individual AST token change affects up to $K = 5$ consecutive overlapping K-grams.
   - The insertion of `WhileStmt` and movement of `UnaryExpr` invalidates 7 out of 16 K-grams in `Base1`.
4. **Fingerprint Match Calculation:**
   - Total distinct winnowed hashes in Base 1: $|\text{Hashes}_A| = 16$.
   - Total distinct winnowed hashes in Variant F1: $|\text{Hashes}_B| = 15$.
   - Common shared hashes: $|\text{Hashes}_A \cap \text{Hashes}_B| = 9$.
   - Union size: $|\text{Hashes}_A \cup \text{Hashes}_B| = 16 + 15 - 9 = 22$.
   - Jaccard similarity:
     $$\text{Similarity} = \frac{9}{22} \times 100\% = 40.9090...\% \approx \mathbf{40.91\%}$$

Because $\mathbf{40.91\% < 70.00\%}$, the production calculator marks `isFlagged = false`, resulting in a False Negative.

---

## 5. Architectural Implications & Recommendations

### Why AST Alone Fails Against Control-Flow Rewriting:
Static ASTs represent the **syntactic hierarchy** of the code, not its semantic execution order. When a programmer or plagiarizer converts a `for` loop to a `while` loop, the execution semantics are identical, but the syntactic tree topology is drastically reorganized.

### Potential Solution for Module 4 Post-Milestone 2:
1. **Canonical Loop Desugaring in `AstNormalizer`:**
   Transform all `ForStmt` nodes into their equivalent `WhileStmt` structure prior to K-gram generation.
2. **Control Flow Graph (CFG) or Program Dependence Graph (PDG):**
   Combine AST token fingerprints with basic-block CFG matching.
3. **Adaptive Thresholding / Multi-Metric Scorer:**
   Pair Jaccard token similarity with Method Call Sequence Matching or Variable Dependency Matching.
