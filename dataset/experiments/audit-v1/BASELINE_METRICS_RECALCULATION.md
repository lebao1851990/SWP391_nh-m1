# Baseline Metrics Recalculation & Threshold Audit
## CodeAssess — SWP391 RBL Module 4: AST Plagiarism & Code Analytics Engine

- **Audit Date:** 2026-10-12
- **Audit Tool:** `com.codeassess.audit.AuditRunner` (Java 21 LTS)
- **Input Data:** `dataset/experiments/baseline-v1/results/baseline_pair_results.csv`
- **Output Recalculation:** `dataset/experiments/audit-v1/recalculated_threshold_metrics.csv`
- **Status:** 100% Mathematically Verified & Validated

---

## 1. Executive Summary

This report presents an independent recalculation of the classification metrics for the CodeAssess AST plagiarism detection baseline. Using raw pair-level similarity scores from all 535 evaluated pairs, the confusion matrix and derivative performance metrics were recomputed from first principles.

### Key Conclusions:
1. **Metric Verification [VERIFIED]:**
   The baseline report's metrics at the official 70.00% threshold ($TP = 90$, $TN = 60$, $FP = 370$, $FN = 15$, $Accuracy = 28.04\%$, $Precision = 19.57\%$, $Recall = 85.71\%$, $F1 = 0.3186$) are **100% mathematically correct**.
2. **Threshold Flatness Explanation [VERIFIED]:**
   The identical confusion matrix results across thresholds 50.0%, 60.0%, 65.0%, and 70.0% are not an algorithm error. The empirical similarity distribution in Dataset v1 contains **zero pairs with similarity between 40.91% and 72.22%**. Hence, any threshold within this interval produces identical classifications.
3. **Inclusivity Convention [VERIFIED]:**
   The classification rule uses $\ge$ (inclusive), i.e., `predicted_positive = similarity >= threshold`.

---

## 2. Official Threshold (70.00%) Recalculation

### 2.1 Confusion Matrix

$$\text{Predicted Positive} \iff \text{Similarity Rate} \ge 70.00\%$$

| | Predicted Plagiarism ($\ge 70.00\%$) | Predicted Non-Plagiarism ($< 70.00\%$) | Total |
| :--- | :---: | :---: | :---: |
| **Actual Plagiarism** | **90 (TP)** | **15 (FN)** | **105** |
| **Actual Non-Plagiarism** | **370 (FP)** | **60 (TN)** | **430** |
| **Total** | **460** | **75** | **535** |

### 2.2 First-Principles Formulas & Recalculated Values

| Metric | Formula | Exact Fraction | Recalculated Decimal | Percentage |
| :--- | :--- | :---: | :---: | :---: |
| **Accuracy** | $\frac{TP + TN}{Total}$ | $\frac{90 + 60}{535} = \frac{150}{535}$ | $0.2803738...$ | **28.04%** |
| **Precision** | $\frac{TP}{TP + FP}$ | $\frac{90}{90 + 370} = \frac{90}{460}$ | $0.1956521...$ | **19.57%** |
| **Recall (Sensitivity)** | $\frac{TP}{TP + FN}$ | $\frac{90}{90 + 15} = \frac{90}{105}$ | $0.8571428...$ | **85.71%** |
| **Specificity** | $\frac{TN}{TN + FP}$ | $\frac{60}{60 + 370} = \frac{60}{430}$ | $0.1395348...$ | **13.95%** |
| **F1-Score** | $\frac{2 \times P \times R}{P + R}$ | $\frac{2 \times \frac{90}{460} \times \frac{90}{105}}{\frac{90}{460} + \frac{90}{105}}$ | $0.3185840...$ | **0.3186** |
| **False Positive Rate (FPR)** | $\frac{FP}{FP + TN}$ | $\frac{370}{370 + 60} = \frac{370}{430}$ | $0.8604651...$ | **86.05%** |
| **False Negative Rate (FNR)** | $\frac{FN}{FN + TP}$ | $\frac{15}{15 + 90} = \frac{15}{105}$ | $0.1428571...$ | **14.29%** |

### 2.3 Edge Case & Zero Denominator Convention
All metric calculation code enforces explicit zero-denominator protection:
$$\text{If } (TP + FP) = 0 \implies \text{Precision} = 0.0$$
$$\text{If } (TP + FN) = 0 \implies \text{Recall} = 0.0$$
$$\text{If } (TN + FP) = 0 \implies \text{Specificity} = 0.0$$
$$\text{If } (Precision + Recall) = 0.0 \implies F_1 = 0.0$$

---

## 3. Full Diagnostic Threshold Analysis

The table below presents the recalculation across all 14 evaluated thresholds from 10.0% to 100.0%.

| Threshold | TP | TN | FP | FN | Accuracy | Precision | Recall | Specificity | F1-Score | FPR | FNR |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **10.00%** | 105 | 0 | 430 | 0 | 0.1963 | 0.1963 | 1.0000 | 0.0000 | 0.3281 | 1.0000 | 0.0000 |
| **20.00%** | 105 | 0 | 430 | 0 | 0.1963 | 0.1963 | 1.0000 | 0.0000 | 0.3281 | 1.0000 | 0.0000 |
| **30.00%** | 105 | 0 | 430 | 0 | 0.1963 | 0.1963 | 1.0000 | 0.0000 | 0.3281 | 1.0000 | 0.0000 |
| **40.00%** | 105 | 0 | 430 | 0 | 0.1963 | 0.1963 | 1.0000 | 0.0000 | 0.3281 | 1.0000 | 0.0000 |
| **50.00%** | 90 | 60 | 370 | 15 | 0.2804 | 0.1957 | 0.8571 | 0.1395 | 0.3186 | 0.8605 | 0.1429 |
| **60.00%** | 90 | 60 | 370 | 15 | 0.2804 | 0.1957 | 0.8571 | 0.1395 | 0.3186 | 0.8605 | 0.1429 |
| **65.00%** | 90 | 60 | 370 | 15 | 0.2804 | 0.1957 | 0.8571 | 0.1395 | 0.3186 | 0.8605 | 0.1429 |
| **70.00% (Official)** | **90** | **60** | **370** | **15** | **0.2804** | **0.1957** | **0.8571** | **0.1395** | **0.3186** | **0.8605** | **0.1429** |
| **75.00%** | 75 | 120 | 310 | 30 | 0.3645 | 0.1948 | 0.7143 | 0.2791 | 0.3061 | 0.7209 | 0.2857 |
| **80.00%** | 75 | 120 | 310 | 30 | 0.3645 | 0.1948 | 0.7143 | 0.2791 | 0.3061 | 0.7209 | 0.2857 |
| **85.00%** | 60 | 180 | 250 | 45 | 0.4486 | 0.1935 | 0.5714 | 0.4186 | 0.2892 | 0.5814 | 0.4286 |
| **90.00%** | 45 | 240 | 190 | 60 | 0.5327 | 0.1915 | 0.4286 | 0.5581 | 0.2647 | 0.4419 | 0.5714 |
| **95.00%** | 45 | 240 | 190 | 60 | 0.5327 | 0.1915 | 0.4286 | 0.5581 | 0.2647 | 0.4419 | 0.5714 |
| **100.00%** | 45 | 240 | 190 | 60 | 0.5327 | 0.1915 | 0.4286 | 0.5581 | 0.2647 | 0.4419 | 0.5714 |

---

## 4. Analysis of Score Distribution and Plateau Effects

A common question in audit is why thresholds **50.0% through 70.0%** produce identical metrics, and why thresholds **75.0% and 80.0%** or **90.0% through 100.0%** are also identical.

### 4.1 Empirical Score Distribution
Because Dataset v1 was produced deterministically by a template-based transformation generator, the similarity scores do not form a continuous distribution. They collapse into exactly **five discrete values**:

| Similarity Rate | Positive Pairs (A–F1) | Negative Pairs (G) | Total Pairs | Categories Represented |
| :---: | :---: | :---: | :---: | :--- |
| **40.91%** | 15 | 60 | **75** | F1 (Positive) & F1 vs Unrelated Base (Negative) |
| **72.22%** | 15 | 60 | **75** | D (Positive) & D vs Unrelated Base (Negative) |
| **83.33%** | 15 | 60 | **75** | E3 (Positive) & E3 vs Unrelated Base (Negative) |
| **88.89%** | 15 | 60 | **75** | E1 (Positive) & E1 vs Unrelated Base (Negative) |
| **100.00%** | 45 | 190 | **235** | A, B, C (Positive) + Base-vs-Base (10) + A, B, C vs Base (180) |
| **TOTAL** | **105** | **430** | **535** | |

### 4.2 Mathematical Plateau Explanation

1. **Between 40.91% and 72.22% (Thresholds 50%, 60%, 65%, 70%):**
   There is a **gap of 31.31 percentage points** with exactly zero pairs. Any threshold $t \in (40.91\%, 72.22\%]$ flags all pairs with scores $\ge 72.22\%$ (460 pairs) and rejects all pairs with score $40.91\%$ (75 pairs). Therefore, the confusion matrix is guaranteed to be invariant across this entire range.
2. **Between 72.22% and 83.33% (Thresholds 75%, 80%):**
   There is an 11.11% gap with zero pairs. Both 75% and 80% exclude Category D (72.22%), dropping 15 TPs to FNs and moving 60 FPs to TNs ($TP = 75, TN = 120, FP = 310, FN = 30$).
3. **Between 88.89% and 100.00% (Thresholds 90%, 95%, 100%):**
   There is an 11.11% gap with zero pairs. All three thresholds retain only the 100.00% pairs ($TP = 45, TN = 240, FP = 190, FN = 60$).
