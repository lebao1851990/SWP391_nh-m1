# Dataset v2.0 Specification — Multi-Algorithmic AST Plagiarism Benchmark
## CodeAssess — SWP391 RBL Module 4: AST Plagiarism & Code Analytics Engine

- **Specification Version:** 2.0.0
- **Creation Date:** 2026-10-12
- **Author:** CodeAssess RBL Research Team
- **Location:** `dataset/v2/`
- **Benchmark Purpose:** Robust, scientifically sound evaluation of AST-based plagiarism detection under distinct algorithmic problem domains and controlled program transformations.

---

## 1. Motivation and Problem Statement

### 1.1 Limitations of Dataset v1.0
As documented in the audit (`DATASET_V1_VALIDATION_REPORT.md`), **Dataset v1.0 suffered from fundamental template homogeneity**:
- All five base programs (`Base1.java` through `Base5.java`) shared the identical 10-line loop counter:
  ```java
  int myCounter = 0;
  for (int loopVar = 0; loopVar < 10; loopVar++) {
      myCounter += loopVar;
  }
  ```
- Because production AST normalization (`AstNormalizer`) deliberately removes class names and identifiers to resist renaming attacks, `Base1` through `Base5` produced **100% identical ASTs**.
- Consequently, negative pairs (`NON_PLAGIARISM`) scored between 72.22% and 100.00% similarity, producing 370 False Positives and distorting the False Positive Rate to 86.05%.

### 1.2 Objectives of Dataset v2.0
Dataset v2.0 resolves this limitation by providing:
1. **Five genuinely distinct computer science algorithmic problems**, each exhibiting unique control-flow topologies, syntax structures, and data representations.
2. **Proven negative separation**, ensuring that independently authored solutions to different problems score safely below the production threshold ($< 25\% \ll 70\%$).
3. **Controlled transformation taxonomy**, maintaining identical experimental scale (110 Java files, 535 pairs) for rigorous comparative evaluation against Dataset v1.0.
4. **Explicit provenance and semantic metadata**, decoupling ground-truth labeling from engine similarity scores.

---

## 2. Base Program Specifications

Dataset v2.0 defines five canonical base programs in `dataset/v2/programs/base/`:

```
dataset/v2/programs/base/
├── BinarySearch.java       (149 AST nodes, 44 winnowed fingerprints)
├── BubbleSort.java         (153 AST nodes, 41 winnowed fingerprints)
├── MatrixMultiply.java     (220 AST nodes, 57 winnowed fingerprints)
├── SinglyLinkedList.java   (143 AST nodes, 50 winnowed fingerprints)
└── PalindromeChecker.java  (103 AST nodes, 35 winnowed fingerprints)
```

### 2.1 Problem Descriptions & Algorithmic Typology

| Program | Algorithmic Problem | Control Structures | Data Structures | AST Node Count | Winnowed Fingerprints |
| :--- | :--- | :--- | :--- | :---: | :---: |
| **`BinarySearch.java`** | Divide-and-conquer binary search on sorted integer array | `while (low <= high)`, 3-way `if / else if / else` branching | 1D primitive array (`int[]`), integer arithmetic | 149 | 44 |
| **`BubbleSort.java`** | Comparison-based adjacent element sorting | 2 nested `for` loops, conditional swap branch | 1D primitive array (`int[]`), temporary variable swap | 153 | 41 |
| **`MatrixMultiply.java`** | Standard matrix-matrix dot product | 3 nested `for` loops, accumulation statement | 2D primitive arrays (`int[][]`), nested indexing | 220 | 57 |
| **`SinglyLinkedList.java`** | Object reference traversal and counting | `while (curr != null)` pointer traversal | Static inner class `Node` (`val`, `next`), object references | 143 | 50 |
| **`PalindromeChecker.java`** | Two-pointer character symmetry verification | Two-pointer `while (left < right)`, early `return false` | Java `String` API (`charAt()`, `length()`), boolean logic | 103 | 35 |

---

## 3. Transformation Taxonomy & Variant Generation

Each of the 5 base programs undergoes seven controlled transformations, producing 3 deterministic variants per category ($5 \times 7 \times 3 = \mathbf{105 \text{ variants}}$):

| Category | Transformation Name | Description & Mechanism | Semantic Effect |
| :---: | :--- | :--- | :--- |
| **A** | Exact Copy | Program duplicated verbatim; only public class declaration renamed to match filename. | Identical semantics (`EQUIVALENT_COPY`) |
| **B** | Identifier Renaming | Systematically renames local variables, method parameters, and helper variables using regex word boundaries. | Identical semantics (`EQUIVALENT_RENAMED`) |
| **C** | Comments & Whitespace | Injects JavaDoc comments, multiline block comments, and converts spaces to tabs. | Identical semantics (`EQUIVALENT_FORMATTED`) |
| **D** | Literal Modifications | Changes search targets, initialization constants, test arrays, or string messages in `main`. | Preserves algorithm; alters test constants (`MODIFIED_LITERALS`) |
| **E1** | Dead Code Insertion | Injects safe dead statements (`if (false) { int unreached = 0; }` or unexecuted helper math). | Identical semantics (`EQUIVALENT_DEAD_CODE`) |
| **E3** | Block Restructuring | Wraps method statements or initialization logic in nested `{ ... }` statement blocks. | Identical semantics (`EQUIVALENT_RESTRUCTURED`) |
| **F1** | Control Flow Rewrite | Transforms loop syntax (`for` $\leftrightarrow$ `while`) while preserving execution order. | Identical semantics (`EQUIVALENT_CFG`) |

---

## 4. Dataset Inventory & Sizing

### 4.1 Program Summary
- **Base Programs:** 5 files
- **Variants per Base:** 21 files (3 variants $\times$ 7 categories)
- **Total Variant Programs:** 105 files
- **Total Java Files:** **110 files**

### 4.2 Pair Manifest Summary (`pairs.csv`)
- **Positive Pairs (`PLAGIARISM`):**
  - Base vs its own 21 variants: $5 \times 21 = \mathbf{105 \text{ pairs}}$.
  - Provenance: `DERIVED`.
- **Negative Pairs (`NON_PLAGIARISM`):**
  - Base vs Base: $\binom{5}{2} = \mathbf{10 \text{ pairs}}$.
  - Base vs Unrelated Variant: 105 variants $\times$ 4 unrelated bases = $\mathbf{420 \text{ pairs}}$.
  - Provenance: `INDEPENDENT`.
- **Total Evaluation Pairs:** **535 pairs** ($105 \text{ positive} + 430 \text{ negative}$).

---

## 5. Comparative Structural Contrast: v1.0 vs v2.0

| Benchmark Characteristic | Dataset v1.0 | Dataset v2.0 | Scientific Advantage |
| :--- | :---: | :---: | :--- |
| **Base Programs** | 5 identical loop counters | 5 distinct CS algorithms | Real-world problem diversity |
| **Base AST Node Range** | Exactly 52 nodes in all | 103 to 220 nodes | Natural structural variance |
| **Negative Pair Separation** | 72.22% – 100.00% (High FP) | **7.61% – 22.39%** | **Perfect negative separation** |
| **Safety Margin to 70% Cutoff**| Negative margin (-30%) | **+47.61% positive safety gap** | Prevents accidental False Positives |
| **Total Evaluation Pairs** | 535 pairs | 535 pairs | Identical statistical sample size |
| **Positive / Negative Ratio** | 105 / 430 (19.63% / 80.37%) | 105 / 430 (19.63% / 80.37%) | Controlled comparative baseline |
