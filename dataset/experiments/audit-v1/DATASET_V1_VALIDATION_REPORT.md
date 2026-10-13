# Dataset v1 Validation Report
## CodeAssess — SWP391 RBL Module 4: AST Plagiarism & Code Analytics Engine

- **Audit Date:** 2026-10-12
- **Audit Tool:** `com.codeassess.audit.AuditRunner` (Java 21 LTS, JavaParser 3.25.9)
- **Target Dataset:** Dataset v1.0 (`dataset/`)
- **Status:** Complete & Independently Verified

---

## 1. Executive Summary

This report delivers an independent, empirical audit of **Dataset v1.0** and its ground-truth annotations in `dataset/ground-truth/pairs.csv`. The validation verified dataset file counts, schema integrity, generator scripts, and the exact AST structures of the base and variant programs using the production JavaParser and normalization pipeline.

### Key Audit Conclusions

1. **Dataset Integrity [VERIFIED]:**
   The dataset consists of exactly **110 Java source files** (5 base programs, 105 generated variants) and **535 pair rows** in `pairs.csv`. There are zero missing file references, zero duplicate pairs, zero self-pairs, and zero malformed rows.
2. **Missing Specification Documents [VERIFIED]:**
   The documents `DATASET_SPECIFICATION.md`, `DATASET_STRUCTURE.md`, `GROUND_TRUTH_SCHEMA.md`, and `DATASET_GENERATION_PLAN.md` mentioned in requirements **do not exist** in the repository. Dataset design is documented primarily in the generator scripts (`generate_dataset.py`, `generate_dataset.js`).
3. **Base Program Homogeneity [VERIFIED]:**
   All 5 base programs (`Base1.java` through `Base5.java`) are **character-for-character identical 254-byte files**, differing solely in their class declaration name (`Base1` to `Base5`). Because production `AstNormalizer` strips class and variable identifiers, all 5 base programs produce **100% identical normalized AST sequences (52 nodes), identical 5-grams (48 grams), and identical winnowed fingerprint sets (16 hashes)**.
4. **Ground-Truth Semantic Flaw [VERIFIED]:**
   The 430 negative pairs labeled `NON_PLAGIARISM` in `pairs.csv` were assigned based strictly on template index mismatch (`var_base_idx != base_idx`) rather than algorithmic independence. Because the underlying templates are identical, these negative pairs share 72.22% to 100.00% structural AST similarity.

---

## 2. Dataset Structure and File Inventory

### 2.1 File Counts and Directory Hierarchy

| Path | Expected Count | Verified Count | File Type | Status |
| :--- | :---: | :---: | :---: | :---: |
| `dataset/programs/base/` | 5 | **5** | `.java` (source) | **VERIFIED** |
| `dataset/programs/generated/` | 105 | **105** | `.java` (source) | **VERIFIED** |
| **Total Java Files** | 110 | **110** | `.java` | **VERIFIED** |
| `dataset/ground-truth/pairs.csv` | 535 pairs | **535 rows** (+1 header) | `.csv` | **VERIFIED** |
| `dataset/scripts/generate_dataset.py` | 1 | **1** | Python script | **VERIFIED** |
| `dataset/scripts/generate_dataset.js` | 1 | **1** | Node.js script | **VERIFIED** |
| Specification / Schema Docs | 4 | **0** | `.md` | **MISSING** |

### 2.2 Documentation Availability Audit

- `DATASET_SPECIFICATION.md`: **NOT FOUND** in repository.
- `DATASET_STRUCTURE.md`: **NOT FOUND** in repository.
- `GROUND_TRUTH_SCHEMA.md`: **NOT FOUND** in repository.
- `DATASET_GENERATION_PLAN.md`: **NOT FOUND** in repository.
- Manifest / Checksum files: **NOT FOUND** (no SHA-256 or MD5 checksum manifest existed prior to this audit).

*Finding:* Dataset specifications were previously implicit within the generator scripts. The actual generator used was `generate_dataset.js` (which uses PascalCase `Base1.java`), whereas `generate_dataset.py` references snake_case `base_1.java`.

---

## 3. Ground Truth Schema and Pair Integrity Audit

The file `dataset/ground-truth/pairs.csv` was audited line-by-line using `AuditRunner.java`.

### 3.1 Schema Verification

The CSV adheres to the six-column format:
```csv
pair_id,program_a,program_b,category,transformation,ground_truth
```

### 3.2 Integrity Checklist

| Integrity Check | Target Condition | Verified Result | Anomaly Count | Status |
| :--- | :--- | :--- | :---: | :---: |
| Row Count | 535 data rows + 1 header | 536 total lines | 0 | **PASSED** |
| Unique Pair IDs | `pair_0001` .. `pair_0535` | 535 unique IDs | 0 | **PASSED** |
| Directed Duplicate Pairs | No repeated `(A, B)` | 535 unique directed | 0 | **PASSED** |
| Undirected Duplicate Pairs | No `(A, B)` with `(B, A)` | 535 unique undirected | 0 | **PASSED** |
| Self-Comparison Pairs | No `(A, A)` | 0 self pairs | 0 | **PASSED** |
| Target File Existence | Every `A` and `B` exists on disk | 110 / 110 files exist | 0 | **PASSED** |
| Valid Ground Truth Labels | Either `PLAGIARISM` or `NON_PLAGIARISM` | 105 PLAG / 430 NON | 0 | **PASSED** |
| Malformed Delimiters / Quotes | Clean CSV formatting | 0 malformed lines | 0 | **PASSED** |

### 3.3 Distribution by Category and Ground Truth

| Category | Description | Ground Truth | Expected Pairs | Verified Pairs | Provenance Rule |
| :---: | :--- | :---: | :---: | :---: | :--- |
| **A** | Exact copy (class rename only) | `PLAGIARISM` | 15 | **15** | 5 bases × 3 variants |
| **B** | Identifier renaming | `PLAGIARISM` | 15 | **15** | 5 bases × 3 variants |
| **C** | Comment / whitespace injection | `PLAGIARISM` | 15 | **15** | 5 bases × 3 variants |
| **D** | Literal modification | `PLAGIARISM` | 15 | **15** | 5 bases × 3 variants |
| **E1** | Dead code injection | `PLAGIARISM` | 15 | **15** | 5 bases × 3 variants |
| **E3** | Block wrapping (`{ ... }`) | `PLAGIARISM` | 15 | **15** | 5 bases × 3 variants |
| **F1** | For-to-while loop rewrite | `PLAGIARISM` | 15 | **15** | 5 bases × 3 variants |
| **G** | Unrelated Base vs Base | `NON_PLAGIARISM` | 10 | **10** | $\binom{5}{2} = 10$ combinations |
| **G** | Unrelated Base vs Variant | `NON_PLAGIARISM` | 420 | **420** | 105 variants × 4 unrelated bases |
| **TOTAL** | | | **535** | **535** | **105 Positive / 430 Negative** |

---

## 4. Empirical Investigation of the 5 Base Programs

### 4.1 Source Code Comparison

The five base files located in `dataset/programs/base/` were inspected:
- `Base1.java` (254 bytes)
- `Base2.java` (254 bytes)
- `Base3.java` (254 bytes)
- `Base4.java` (254 bytes)
- `Base5.java` (254 bytes)

```java
// Exact source code of Base1.java:
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

*Finding:* Files `Base2.java` through `Base5.java` are identical character-by-character, except the token `Base1` is replaced with `Base2`, `Base3`, `Base4`, and `Base5`.

### 4.2 Production AST Pipeline Normalization

When parsed with `com.codeassess.plagiarism.parser.JavaAstParser` and normalized with `com.codeassess.plagiarism.normalizer.AstNormalizer`, every base program generates an identical 52-node pre-order traversal sequence:

```
[00] CompilationUnit
[01] ClassOrInterfaceDeclaration
[02] Modifier
[03] SimpleName                  <-- Class name (Base1..Base5) normalized to "SimpleName"
[04] MethodDeclaration
[05] Modifier
[06] Modifier
[07] VoidType
[08] SimpleName                  <-- "main" normalized to "SimpleName"
[09] Parameter
[10] ArrayType
[11] ClassOrInterfaceType
[12] SimpleName                  <-- "String" normalized to "SimpleName"
[13] SimpleName                  <-- "args" normalized to "SimpleName"
[14] BlockStmt
[15] ExpressionStmt
[16] VariableDeclarationExpr
[17] VariableDeclarator
[18] PrimitiveType
[19] SimpleName                  <-- "myCounter" normalized to "SimpleName"
[20] IntegerLiteralExpr[0]       <-- Literal preserved with value [0]
[21] ForStmt
[22] VariableDeclarationExpr
[23] VariableDeclarator
[24] PrimitiveType
[25] SimpleName                  <-- "loopVar" normalized to "SimpleName"
[26] IntegerLiteralExpr[0]       <-- Literal preserved with value [0]
[27] BinaryExpr
[28] NameExpr
[29] SimpleName                  <-- "loopVar" normalized to "SimpleName"
[30] IntegerLiteralExpr[10]      <-- Literal preserved with value [10]
[31] UnaryExpr
[32] NameExpr
[33] SimpleName                  <-- "loopVar" normalized to "SimpleName"
[34] BlockStmt
[35] ExpressionStmt
[36] AssignExpr
[37] NameExpr
[38] SimpleName                  <-- "myCounter" normalized to "SimpleName"
[39] NameExpr
[40] SimpleName                  <-- "loopVar" normalized to "SimpleName"
[41] ExpressionStmt
[42] MethodCallExpr
[43] FieldAccessExpr
[44] NameExpr
[45] SimpleName                  <-- "System" normalized to "SimpleName"
[46] SimpleName                  <-- "out" normalized to "SimpleName"
[47] SimpleName                  <-- "println" normalized to "SimpleName"
[48] BinaryExpr
[49] StringLiteralExpr[Result: ] <-- String literal preserved
[50] NameExpr
[51] SimpleName                  <-- "myCounter" normalized to "SimpleName"
```

### 4.3 K-Gram and Fingerprint Identity

- **5-Gram Count:** Each base program produces exactly **48 K-grams** ($52 - 5 + 1 = 48$).
- **Winnowing (Window W = 4):** Each base program yields exactly **16 winnowed hashes**.
- **Hash Values:** All 16 hashes are identical across all five base programs:
  1. `0x9F4DADDFDADFF68F` (`-6967721871510866289`)
  2. `0x973C04EA76B40740` (`-7549153470319425728`)
  3. `0xA8FBD0CFE7046EB5` (`-6270188464830648651`)
  4. `0xCB6D56E3D154EEBD` (`-3788276175079149891`)
  5. `0xB03EDCE5A73FEBCC` (`-5746913195566568500`)
  6. `0x0BF4A3D8FA1F0EBB` (`861493581039210171`)
  7. `0x9F62D60670F21E7E` (`-6961766750808301954`)
  8. `0x9E08B6B98CECE177` (`-7059191507853713033`)
  9. `0xB6C4129377004959` (`-5277072438805378727`)
  10. `0xA56E62D0B38B7543` (`-6526170161508289213`)
  11. `0xC5CA8D9BEE17DB31` (`-4194384402088207567`)
  12. `0x925783E036F2D57D` (`-7901702022180448899`)
  13. `0x87A4AC677312F64D` (`-8672617422096501171`)
  14. `0xDB1BEF83F2033987` (`-2658267805052094073`)
  15. `0x97E3D5B09B0683A8` (`-7501917599777258584`)
  16. `0xACD1115A28DD3148` (`-5993990550123105976`)

### 4.4 Pairwise Base-vs-Base Similarity Results

All 10 pairwise comparisons between `Base1.java` through `Base5.java` yield:
- **Shared Hashes:** 16 / 16
- **Jaccard Similarity:** **100.00%**
- **Predicted Flag ($\ge 70.00\%$):** `true`
- **Ground Truth Label:** `NON_PLAGIARISM`
- **Result:** **10 False Positives**

---

## 5. Ground Truth Semantics & Conflict Analysis

### 5.1 Definition of Labels in Dataset v1.0

- **`PLAGIARISM`:** Defined strictly by direct generative provenance. Variant $V$ was produced by applying transformation script $T$ to program $Base_X$.
- **`NON_PLAGIARISM`:** Defined purely by source file identifier mismatch ($Base_i \neq Base_j$ or $Variant_{i, ...} \neq Base_j$).

### 5.2 The Fundamental Semantic Conflict

In academic plagiarism detection benchmarks (e.g., SOCO, PAN), negative pairs (`NON_PLAGIARISM`) represent **independently authored, structurally distinct solutions** to the same or different problem specifications. 

In Dataset v1.0:
1. The generator script author explicitly placed the comment:
   `// Write 5 identical structural base programs for predictable regex targeting` (in `generate_dataset.py`, line 69).
2. Because the base programs share identical control flow and statements, programs labeled `NON_PLAGIARISM` are **structurally identical isomorphic clones**.
3. The AST engine's task is specifically designed to recognize identical structure despite identifier renaming. Therefore, flagging these pairs at 100% similarity is mathematically correct behavior for the algorithm, but produces 370 False Positives against the flawed ground-truth labels.

### 5.3 Preservation Rule

As mandated:
- **Dataset v1.0 and `pairs.csv` remain strictly immutable.**
- The baseline results ($FP = 370$, $Accuracy = 28.04\%$) are preserved as the historical benchmark.
- A future **Dataset v2.0** with five genuinely distinct problem domains (e.g., Binary Search, Bubble Sort, Matrix Multiplication, Singly Linked List, Palindrome Checker) must be created separately to measure true negative separation.
