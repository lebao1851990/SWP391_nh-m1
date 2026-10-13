# Baseline Experiment Report — AST Plagiarism Detection Engine
## SWP391 RBL — Milestone 2: Module 4 (AST Plagiarism & Code Analytics Engine)

- **Date of Execution:** 2026-10-12 10:22:11
- **Environment:** Java 21 LTS (`jdk-21.0.12.1`), Windows 11
- **Dataset:** Dataset v1.0 (5 base programs, 105 generated variants, 110 total Java files, 535 pairs)
- **Production Pipeline:** Reused exact production classes (`com.codeassess.plagiarism.*`)
  - Parser: `JavaAstParser` (JavaParser 3.25.9)
  - Normalizer: `AstNormalizer` (Identifier stripped, Literal preserved, Comment ignored)
  - K-Gram: `KGramGenerator` (K = 5)
  - Hasher: `FingerprintHasher` (FNV-1a 64-bit)
  - Winnower: `Winnower` (Window W = 4, rightmost minimum tie-break)
  - Similarity: `JaccardSimilarityCalculator`
  - Official Flag Threshold: **70.00%**

---

## 1. Executive Summary & Overall Metrics

| Metric | Value | Definition / Formula |
| :--- | :--- | :--- |
| **Total Pairs** | 535 | 105 PLAGIARISM + 430 NON_PLAGIARISM |
| **True Positives (TP)** | 90 | Correctly flagged plagiarism |
| **True Negatives (TN)** | 60 | Correctly unflagged non-plagiarism |
| **False Positives (FP)** | 370 | Non-plagiarism incorrectly flagged |
| **False Negatives (FN)** | 15 | Plagiarism incorrectly missed |
| **Accuracy** | **28.04%** (0.2804) | (TP + TN) / Total |
| **Precision** | **19.57%** (0.1957) | TP / (TP + FP) |
| **Recall (Sensitivity)** | **85.71%** (0.8571) | TP / (TP + FN) |
| **Specificity** | **13.95%** (0.1395) | TN / (TN + FP) |
| **F1-Score** | **0.3186** | 2 * P * R / (P + R) |
| **False Positive Rate (FPR)** | **86.05%** (0.8605) | FP / (FP + TN) |
| **False Negative Rate (FNR)** | **14.29%** (0.1429) | FN / (FN + TP) |

### Confusion Matrix

| | Predicted Plagiarism (>= 70%) | Predicted Non-Plagiarism (< 70%) |
| :--- | :---: | :---: |
| **Actual Plagiarism** | **90 (TP)** | **15 (FN)** |
| **Actual Non-Plagiarism** | **370 (FP)** | **60 (TN)** |

---

## 2. Category-Specific Results

| Category | Name / Transformation | Pairs | TP | TN | FP | FN | Recall | Min Sim | Max Sim | Mean Sim | Median Sim |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **A** | Exact copy | 15 | 15 | 0 | 0 | 0 | 100.00% | 100.00% | 100.00% | 100.00% | 100.00% |
| **B** | Identifier rename | 15 | 15 | 0 | 0 | 0 | 100.00% | 100.00% | 100.00% | 100.00% | 100.00% |
| **C** | Comment / Whitespace | 15 | 15 | 0 | 0 | 0 | 100.00% | 100.00% | 100.00% | 100.00% | 100.00% |
| **D** | Literal modifications | 15 | 15 | 0 | 0 | 0 | 100.00% | 72.22% | 72.22% | 72.22% | 72.22% |
| **E1** | Dead code injection | 15 | 15 | 0 | 0 | 0 | 100.00% | 88.89% | 88.89% | 88.89% | 88.89% |
| **E3** | Block wrapping | 15 | 15 | 0 | 0 | 0 | 100.00% | 83.33% | 83.33% | 83.33% | 83.33% |
| **F1** | For-to-while rewrite | 15 | 0 | 0 | 0 | 15 | 0.00% | 40.91% | 40.91% | 40.91% | 40.91% |
| **G** | Unrelated pairs (Negative) | 430 | 0 | 60 | 370 | 0 | 0.00% | 40.91% | 100.00% | 84.00% | 88.89% |

---

## 3. Ground Truth Similarity Distribution

| Ground Truth Group | Count | Min Sim | Max Sim | Mean Sim | Median Sim |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **PLAGIARISM** (A–F1) | 105 | 40.91% | 100.00% | 83.62% | 88.89% |
| **NON_PLAGIARISM** (G) | 430 | 40.91% | 100.00% | 84.00% | 88.89% |

---

## 4. Threshold Diagnostic Analysis

Note: The 70.00% threshold remains the official baseline result. The table below illustrates the sensitivity curve across varying threshold cutoffs:

| Threshold | TP | TN | FP | FN | Accuracy | Precision | Recall | Specificity | F1-Score | FPR |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **10.0%** | 105 | 0 | 430 | 0 | 19.63% | 19.63% | 100.00% | 0.00% | 0.3281 | 100.00% |
| **20.0%** | 105 | 0 | 430 | 0 | 19.63% | 19.63% | 100.00% | 0.00% | 0.3281 | 100.00% |
| **30.0%** | 105 | 0 | 430 | 0 | 19.63% | 19.63% | 100.00% | 0.00% | 0.3281 | 100.00% |
| **40.0%** | 105 | 0 | 430 | 0 | 19.63% | 19.63% | 100.00% | 0.00% | 0.3281 | 100.00% |
| **50.0%** | 90 | 60 | 370 | 15 | 28.04% | 19.57% | 85.71% | 13.95% | 0.3186 | 86.05% |
| **60.0%** | 90 | 60 | 370 | 15 | 28.04% | 19.57% | 85.71% | 13.95% | 0.3186 | 86.05% |
| **65.0%** | 90 | 60 | 370 | 15 | 28.04% | 19.57% | 85.71% | 13.95% | 0.3186 | 86.05% |
| **70.0%** | 90 | 60 | 370 | 15 | 28.04% | 19.57% | 85.71% | 13.95% | 0.3186 | 86.05% |
| **75.0%** | 75 | 120 | 310 | 30 | 36.45% | 19.48% | 71.43% | 27.91% | 0.3061 | 72.09% |
| **80.0%** | 75 | 120 | 310 | 30 | 36.45% | 19.48% | 71.43% | 27.91% | 0.3061 | 72.09% |
| **85.0%** | 60 | 180 | 250 | 45 | 44.86% | 19.35% | 57.14% | 41.86% | 0.2892 | 58.14% |
| **90.0%** | 45 | 240 | 190 | 60 | 53.27% | 19.15% | 42.86% | 55.81% | 0.2647 | 44.19% |
| **95.0%** | 45 | 240 | 190 | 60 | 53.27% | 19.15% | 42.86% | 55.81% | 0.2647 | 44.19% |
| **100.0%** | 45 | 240 | 190 | 60 | 53.27% | 19.15% | 42.86% | 55.81% | 0.2647 | 44.19% |

---

## 5. False Positive & False Negative Analysis

### 5.1 False Negatives (FN)
- Total FN: 15
  - pair_0019: Base1.java vs Variant_1_F1_1.java (F1) => Sim: 40.91%
  - pair_0020: Base1.java vs Variant_1_F1_2.java (F1) => Sim: 40.91%
  - pair_0021: Base1.java vs Variant_1_F1_3.java (F1) => Sim: 40.91%
  - pair_0040: Base2.java vs Variant_2_F1_1.java (F1) => Sim: 40.91%
  - pair_0041: Base2.java vs Variant_2_F1_2.java (F1) => Sim: 40.91%
  - pair_0042: Base2.java vs Variant_2_F1_3.java (F1) => Sim: 40.91%
  - pair_0061: Base3.java vs Variant_3_F1_1.java (F1) => Sim: 40.91%
  - pair_0062: Base3.java vs Variant_3_F1_2.java (F1) => Sim: 40.91%
  - pair_0063: Base3.java vs Variant_3_F1_3.java (F1) => Sim: 40.91%
  - pair_0082: Base4.java vs Variant_4_F1_1.java (F1) => Sim: 40.91%
  - pair_0083: Base4.java vs Variant_4_F1_2.java (F1) => Sim: 40.91%
  - pair_0084: Base4.java vs Variant_4_F1_3.java (F1) => Sim: 40.91%
  - pair_0103: Base5.java vs Variant_5_F1_1.java (F1) => Sim: 40.91%
  - pair_0104: Base5.java vs Variant_5_F1_2.java (F1) => Sim: 40.91%
  - pair_0105: Base5.java vs Variant_5_F1_3.java (F1) => Sim: 40.91%

### 5.2 False Positives (FP) & Structural Equivalence Finding
- Total FP: 370 out of 430 negative pairs (FPR = 86.05%)

> [!IMPORTANT]
> **Critical Research Finding on Dataset v1.0:**
> In the current synthetic dataset generator (`generate_dataset.py` / `generate_dataset.js`), all 5 base programs (`Base1.java` through `Base5.java`) were generated with **structurally identical code templates** (only the class name differed: `class Base1`, `class Base2`, etc.).
> Because `AstNormalizer` intentionally abstracts away class names and identifiers (a core design requirement to defeat identifier renaming attacks), the AST fingerprints for `Base1` through `Base5` are **100% structurally identical**.
> As a result, when negative pairs (BaseX vs BaseY, or BaseX vs Variant_Y) are evaluated by the AST engine, their actual AST similarity is mathematically 70%–100%, causing the engine to flag them.
> This is not an algorithm flaw; rather, it is a **synthetic dataset artifact** where programs labeled as `NON_PLAGIARISM` in the ground truth are in fact isomorphic algorithmic copies of each other. In real-world educational exams, different problems or independent non-plagiarized student solutions have distinct algorithmic structures.

---

## 6. Performance & Execution Benchmark

| Stage | Items Processed | Execution Time | Average Throughput |
| :--- | :---: | :---: | :---: |
| **AST Parsing & Fingerprinting** | 110 programs | 438 ms | 3.98 ms / program |
| **Pair Similarity Evaluation** | 535 pairs | 19 ms | 0.036 ms / pair |
| **Total Pipeline Execution** | 535 pairs | 457 ms | - |

---

## 7. Limitations & Reproducibility

1. **Language Scope:** The current production pipeline parses Java source code (`JavaAstParser` based on JavaParser 3.25.9). C++ and Python are not yet supported.
2. **Exact Production Reuse:** Zero algorithm cloning was used. The experiment loaded the compiled `com.codeassess.plagiarism.*` classes directly via classpath.
3. **Reproducibility Command:**
   ```bat
   cd "d:\AST Plagiarism Detection"
   dataset\experiments\baseline-v1\run_baseline.bat
   ```
