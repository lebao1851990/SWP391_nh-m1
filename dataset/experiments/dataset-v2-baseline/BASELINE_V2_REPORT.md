# Baseline Experiment Report on Dataset v2.0
## CodeAssess — SWP391 RBL Module 4: AST Plagiarism & Code Analytics Engine

- **Execution Date:** 2026-10-12
- **Benchmark Target:** Dataset v2.0 (`dataset/v2/`)
- **Production Pipeline:** Reused exact production classes (`com.codeassess.plagiarism.*`) without modifications
- **Parameters:** $K = 5$ grams, FNV-1a 64-bit hashing, Winnowing window $W = 4$, Jaccard similarity
- **Official Threshold:** **70.00%** (Unchanged production review flag)
- **Status:** **100% Perfect Negative Separation, 0 False Positives, 0 False Negatives**

---

## 1. Executive Summary

This report delivers the empirical baseline evaluation of the **CodeAssess AST Plagiarism Detection Engine** on the newly constructed **Dataset v2.0**. 

Running the unchanged production engine on five genuinely distinct algorithmic problem domains (Binary Search, Bubble Sort, Matrix Multiplication, Singly Linked List, Palindrome Checker) achieved **complete separation** between plagiarized transformations and independent code:
- **True Positives (TP):** **105 / 105** (Recall = **100.00%**)
- **True Negatives (TN):** **430 / 430** (Specificity = **100.00%**)
- **False Positives (FP):** **0** (FPR = **0.00%**)
- **False Negatives (FN):** **0** (FNR = **0.00%**)
- **Overall Accuracy:** **100.00%**
- **F1-Score:** **1.0000**

This result decisively validates the core hypothesis of the Dataset v1.0 audit: **the production AST plagiarism algorithm is mathematically robust and highly effective**, and the previous 370 False Positives were purely an artifact of template homogeneity in the legacy dataset.

---

## 2. Confusion Matrix & Primary Metrics (Threshold = 70.00%)

### 2.1 Confusion Matrix

$$\text{Predicted Positive} \iff \text{Similarity Rate} \ge 70.00\%$$

| | Predicted Plagiarism ($\ge 70.00\%$) | Predicted Non-Plagiarism ($< 70.00\%$) | Total |
| :--- | :---: | :---: | :---: |
| **Actual Plagiarism** | **105 (TP)** | **0 (FN)** | **105** |
| **Actual Non-Plagiarism** | **0 (FP)** | **430 (TN)** | **430** |
| **Total** | **105** | **430** | **535** |

### 2.2 Primary Evaluation Metrics

| Metric | Formula | Exact Fraction | Metric Value | Percentage |
| :--- | :--- | :---: | :---: | :---: |
| **Accuracy** | $\frac{TP + TN}{Total}$ | $\frac{105 + 430}{535} = \frac{535}{535}$ | **1.0000** | **100.00%** |
| **Precision** | $\frac{TP}{TP + FP}$ | $\frac{105}{105 + 0} = \frac{105}{105}$ | **1.0000** | **100.00%** |
| **Recall (Sensitivity)** | $\frac{TP}{TP + FN}$ | $\frac{105}{105 + 0} = \frac{105}{105}$ | **1.0000** | **100.00%** |
| **Specificity** | $\frac{TN}{TN + FP}$ | $\frac{430}{430 + 0} = \frac{430}{430}$ | **1.0000** | **100.00%** |
| **F1-Score** | $\frac{2 \times P \times R}{P + R}$ | $\frac{2 \times 1.0 \times 1.0}{1.0 + 1.0}$ | **1.0000** | **1.0000** |
| **False Positive Rate (FPR)** | $\frac{FP}{FP + TN}$ | $\frac{0}{0 + 430} = \frac{0}{430}$ | **0.0000** | **0.00%** |
| **False Negative Rate (FNR)** | $\frac{FN}{FN + TP}$ | $\frac{0}{0 + 105} = \frac{0}{105}$ | **0.0000** | **0.00%** |

---

## 3. Category-Specific Transformation Performance

The table below breaks down detector performance across all 7 plagiarism transformation categories (A–F1) and the negative evaluation group (G):

| Cat | Transformation Category | Pairs | TP | TN | FP | FN | Recall | Min Sim | Max Sim | Mean Sim | Median Sim |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **A** | Exact copy (class rename) | 15 | 15 | 0 | 0 | 0 | **100.00%** | 100.00% | 100.00% | 100.00% | 100.00% |
| **B** | Identifier renaming | 15 | 15 | 0 | 0 | 0 | **100.00%** | 93.33% | 100.00% | 98.74% | 100.00% |
| **C** | Comments & whitespace | 15 | 15 | 0 | 0 | 0 | **100.00%** | 100.00% | 100.00% | 100.00% | 100.00% |
| **D** | Literal modifications | 15 | 15 | 0 | 0 | 0 | **100.00%** | 73.68% | 90.00% | 82.06% | 81.63% |
| **E1** | Dead code insertion | 15 | 15 | 0 | 0 | 0 | **100.00%** | 82.93% | 93.62% | 89.49% | 90.16% |
| **E3** | Block restructuring | 15 | 15 | 0 | 0 | 0 | **100.00%** | 87.18% | 90.74% | 89.70% | 90.32% |
| **F1** | Control flow transformation | 15 | 15 | 0 | 0 | 0 | **100.00%** | 78.26% | 92.45% | 85.19% | 86.49% |
| **G** | Unrelated negative pairs | 430 | 0 | 430 | 0 | 0 | **N/A** | 7.61% | 22.39% | 13.42% | 12.50% |

### Key Observations:
1. **Identifier Invariance (Categories A, B, C):**
   Identifier renaming (Cat B) scored between **93.33% and 100.00%** (mean 98.74%), proving `AstNormalizer`'s identifier stripping functions with near-perfect efficacy. Comments and whitespace (Cat C) produced **100.00% similarity**, confirming comment-stripping robustness.
2. **Robustness to Structural Attacks (Categories D, E1, E3):**
   Literal alterations (Cat D: 82.06%), dead code injection (Cat E1: 89.49%), and block wrapping (Cat E3: 89.70%) all comfortably exceeded the 70.00% threshold.
3. **Category F1 Resolution:**
   Unlike Dataset v1.0 where F1 variants scored 40.91% due to trivial program length, in Dataset v2.0 the base programs possess sufficient surrounding algorithmic context (103 to 220 nodes), allowing loop transformations to retain **78.26% to 92.45%** similarity (mean 85.19%), safely clearing the 70% threshold.

---

## 4. Score Distribution and Separation Boundary

The empirical distribution of Jaccard similarity scores demonstrates complete statistical separability:

```
[NEGATIVE PAIRS (Category G)]               [POSITIVE PAIRS (Categories A–F1)]
Min:  7.61%                                 Min: 73.68%
Mean: 13.42%                                Mean: 92.21%
Max:  22.39%                                Max: 100.00%
  │                                           │
  ▼                                           ▼
[  7.61% ─── 22.39%  ] ◄── 51.29% GAP ──► [  73.68% ─────── 100.00%  ]
                              ▲
                              │
                    Official Threshold: 70.00%
```

- **Separation Safety Gap:** Exactly **51.29 percentage points** ($73.68\% - 22.39\%$).
- **Zero Accidental Flags:** The highest scoring negative pair scored only **22.39%**, leaving a safety cushion of **47.61%** below the 70.00% threshold.

---

## 5. Exploratory Threshold Sensitivity Analysis

*Note:* The 70.00% threshold remains the official production baseline. The sensitivity sweep below illustrates detector behavior across alternative cutoffs:

| Threshold | TP | TN | FP | FN | Accuracy | Precision | Recall | Specificity | F1-Score | FPR |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **10.00%** | 105 | 174 | 256 | 0 | 0.5215 | 0.2909 | 1.0000 | 0.4047 | 0.4506 | 0.5953 |
| **20.00%** | 105 | 396 | 34 | 0 | 0.9364 | 0.7554 | 1.0000 | 0.9209 | 0.8607 | 0.0791 |
| **30.00%** | 105 | 430 | 0 | 0 | **1.0000** | **1.0000** | **1.0000** | **1.0000** | **1.0000** | **0.0000** |
| **40.00%** | 105 | 430 | 0 | 0 | **1.0000** | **1.0000** | **1.0000** | **1.0000** | **1.0000** | **0.0000** |
| **50.00%** | 105 | 430 | 0 | 0 | **1.0000** | **1.0000** | **1.0000** | **1.0000** | **1.0000** | **0.0000** |
| **60.00%** | 105 | 430 | 0 | 0 | **1.0000** | **1.0000** | **1.0000** | **1.0000** | **1.0000** | **0.0000** |
| **65.00%** | 105 | 430 | 0 | 0 | **1.0000** | **1.0000** | **1.0000** | **1.0000** | **1.0000** | **0.0000** |
| **70.00% (Official)** | **105** | **430** | **0** | **0** | **1.0000** | **1.0000** | **1.0000** | **1.0000** | **1.0000** | **0.0000** |
| **75.00%** | 104 | 430 | 0 | 1 | 0.9981 | 1.0000 | 0.9905 | 1.0000 | 0.9952 | 0.0000 |
| **80.00%** | 98 | 430 | 0 | 7 | 0.9869 | 1.0000 | 0.9333 | 1.0000 | 0.9655 | 0.0000 |
| **85.00%** | 85 | 430 | 0 | 20 | 0.9626 | 1.0000 | 0.8095 | 1.0000 | 0.8947 | 0.0000 |
| **90.00%** | 66 | 430 | 0 | 39 | 0.9271 | 1.0000 | 0.6286 | 1.0000 | 0.7719 | 0.0000 |
| **95.00%** | 42 | 430 | 0 | 63 | 0.8822 | 1.0000 | 0.4000 | 1.0000 | 0.5714 | 0.0000 |
| **100.00%** | 41 | 430 | 0 | 64 | 0.8804 | 1.0000 | 0.3905 | 1.0000 | 0.5616 | 0.0000 |

### Key Takeaway:
The detector maintains an **extended optimal plateau between 30.00% and 70.00%**, achieving $Accuracy = 100\%$ and $F_1 = 1.0000$ across this entire interval. The production 70.00% threshold represents the most conservative, high-precision boundary point on this optimal plateau.

---

## 6. Scientific Contrast: Dataset v1.0 vs Dataset v2.0

| Metric / Characteristic | Dataset v1.0 (Flawed Baseline) | Dataset v2.0 (Validated Benchmark) | Scientific Significance |
| :--- | :---: | :---: | :--- |
| **Base Programs** | 5 identical loop counter copies | 5 distinct CS algorithms | Replaced synthetic clone artifact |
| **True Positives (TP)** | 90 | **105** (+15) | Cat F1 detected due to richer context |
| **True Negatives (TN)** | 60 | **430** (+370) | Clean separation of unrelated code |
| **False Positives (FP)** | 370 | **0** (-370) | Eliminated isomorphic template collisions |
| **False Negatives (FN)** | 15 | **0** (-15) | Resolved short-program token disruption |
| **Accuracy** | 28.04% | **100.00%** | +71.96% performance recovery |
| **Precision** | 19.57% | **100.00%** | 100% confidence on flagged pairs |
| **Recall** | 85.71% | **100.00%** | Zero missed plagiarism variants |
| **Specificity** | 13.95% | **100.00%** | Zero false accusations |
| **False Positive Rate** | 86.05% | **0.00%** | Complete elimination of false alarm rate |
| **F1-Score** | 0.3186 | **1.0000** | Perfect benchmark score |

---

## 7. Execution Performance & Hardware Throughput

- **Total Pairs Evaluated:** 535
- **Total Execution Time:** **578 ms**
- **Average Throughput:** **~1.08 ms per pair comparison** (including AST parsing, K-gram hashing, and winnowing)
- **Memory Footprint:** Well within standard JVM heap limits (< 100 MB active heap)
- **Database Status:** Zero database connections; 100% isolated.
