# Dataset v2.0 Metrics Verification & Discrepancy Audit
## CodeAssess — SWP391 RBL Module 4: AST Plagiarism & Code Analytics Engine

- **Audit Date:** 2026-10-12
- **Audit Directory:** `dataset/experiments/dataset-v2-audit/`
- **Audit Tool:** `com.codeassess.audit.v2.DatasetV2Auditor`
- **Source Data:** `dataset/v2/pairs.csv`, `dataset/experiments/dataset-v2-baseline/results/baseline_v2_pair_results.csv`
- **Discrepancy Log:** `dataset/experiments/dataset-v2-audit/discrepancies.csv`
- **Status:** **100% Mathematically Verified (0 Discrepancies Found)**

---

## 1. Executive Summary

This audit independently verified the pair-level evaluation records and recalculated the classification metrics from first principles.

### Key Audit Conclusions:
1. **Row Mapping & Prediction Invariance [VERIFIED]:**
   Every row in `baseline_v2_pair_results.csv` maps 1-to-1 to a corresponding pair in `pairs.csv`. Every prediction strictly follows the rule:
   $$\text{predicted\_flag} = (\text{similarity\_rate} \ge 70.00\%)$$
2. **Discrepancy Audit [VERIFIED]:**
   The automated discrepancy audit scanned all 535 rows for file mismatches, ID drift, out-of-bounds similarity scores, incorrect prediction flags, and outcome misclassifications. Exactly **zero discrepancies** were detected (`discrepancies.csv` has 0 error entries).
3. **Metric Calculations [VERIFIED]:**
   The reported metrics at the official 70.00% threshold ($TP = 105$, $TN = 430$, $FP = 0$, $FN = 0$, $Accuracy = 100.00\%$, $Precision = 100.00\%$, $Recall = 100.00\%$, $Specificity = 100.00\%$, $F_1 = 1.0000$) are **100% mathematically correct**.

---

## 2. Row-by-Row Integrity Verification

| Verification Dimension | Expected Standard | Audited Result | Discrepancies | Status |
| :--- | :--- | :--- | :---: | :---: |
| **Row Count Alignment** | Exactly 535 evaluation rows | 535 rows in both CSVs | 0 | **PASSED** |
| **Pair ID Alignment** | `pair_0001` to `pair_0535` | 100% exact match | 0 | **PASSED** |
| **Ground Truth Alignment** | `pairs.csv` ground truth matches results CSV | 100% exact match | 0 | **PASSED** |
| **Similarity Score Range** | $0.00\% \le \text{sim} \le 100.00\%$ | Range: $7.61\% \dots 100.00\%$ | 0 | **PASSED** |
| **Threshold Decision Rule**| `predicted_flag == (sim >= 70.00)` | 100% exact compliance | 0 | **PASSED** |
| **Outcome Labeling** | `TP`, `TN`, `FP`, `FN` follow standard truth table | 100% exact compliance | 0 | **PASSED** |

---

## 3. Recalculated Primary Confusion Matrix & Rates

### 3.1 Confusion Matrix (Official Threshold = 70.00%)

| | Predicted Plagiarism ($\ge 70.00\%$) | Predicted Non-Plagiarism ($< 70.00\%$) | Total |
| :--- | :---: | :---: | :---: |
| **Actual Plagiarism (Positive)** | **105 (TP)** | **0 (FN)** | **105** |
| **Actual Non-Plagiarism (Negative)** | **0 (FP)** | **430 (TN)** | **430** |
| **Total** | **105** | **430** | **535** |

### 3.2 Formal Rate Calculations

$$\text{Accuracy} = \frac{TP + TN}{Total} = \frac{105 + 430}{535} = \frac{535}{535} = \mathbf{1.0000 \quad (100.00\%)}$$

$$\text{Precision} = \frac{TP}{TP + FP} = \frac{105}{105 + 0} = \mathbf{1.0000 \quad (100.00\%)}$$

$$\text{Recall (Sensitivity)} = \frac{TP}{TP + FN} = \frac{105}{105 + 0} = \mathbf{1.0000 \quad (100.00\%)}$$

$$\text{Specificity} = \frac{TN}{TN + FP} = \frac{430}{430 + 0} = \mathbf{1.0000 \quad (100.00\%)}$$

$$F_1\text{-Score} = \frac{2 \times Precision \times Recall}{Precision + Recall} = \frac{2 \times 1.0 \times 1.0}{1.0 + 1.0} = \mathbf{1.0000}$$

$$\text{False Positive Rate (FPR)} = \frac{FP}{FP + TN} = \frac{0}{0 + 430} = \mathbf{0.0000 \quad (0.00\%)}$$

$$\text{False Negative Rate (FNR)} = \frac{FN}{FN + TP} = \frac{0}{0 + 105} = \mathbf{0.0000 \quad (0.00\%)}$$

---

## 4. Category-Level Metric Agreement Audit

The audited category-level aggregations in `category_metrics_v2.csv` were verified against pair-level records:

| Category | Pair Count | TP | TN | FP | FN | Recall | Precision | F1-Score | Mean Similarity | Verified? |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **A** | 15 | 15 | 0 | 0 | 0 | **1.0000** | **1.0000** | **1.0000** | 100.00% | **CONFIRMED** |
| **B** | 15 | 15 | 0 | 0 | 0 | **1.0000** | **1.0000** | **1.0000** | 98.74% | **CONFIRMED** |
| **C** | 15 | 15 | 0 | 0 | 0 | **1.0000** | **1.0000** | **1.0000** | 100.00% | **CONFIRMED** |
| **D** | 15 | 15 | 0 | 0 | 0 | **1.0000** | **1.0000** | **1.0000** | 82.06% | **CONFIRMED** |
| **E1** | 15 | 15 | 0 | 0 | 0 | **1.0000** | **1.0000** | **1.0000** | 89.49% | **CONFIRMED** |
| **E3** | 15 | 15 | 0 | 0 | 0 | **1.0000** | **1.0000** | **1.0000** | 89.70% | **CONFIRMED** |
| **F1** | 15 | 15 | 0 | 0 | 0 | **1.0000** | **1.0000** | **1.0000** | 85.19% | **CONFIRMED** |
| **G** | 430 | 0 | 430 | 0 | 0 | N/A | N/A | N/A | 13.42% | **CONFIRMED** |

All per-category counts sum exactly to 535 total pairs, and per-category outcomes sum to $TP = 105, TN = 430$.

---

## 5. Threshold Analysis Agreement Audit

Recalculating all 14 cutoffs in `threshold_analysis_v2.csv` directly from pair scores confirms:
- Cutoffs between **30.00% and 70.00%** exhibit identical optimal performance ($TP = 105, TN = 430, FP = 0, FN = 0$).
- At **20.00%**, 34 negative pairs are falsely flagged ($FP = 34, Specificity = 92.09\%$).
- At **10.00%**, 256 negative pairs are falsely flagged ($FP = 256, Specificity = 40.47\%$).
- Above **70.00%** (e.g., 75.00%), 1 positive pair from Category D drops below the threshold ($FN = 1, Recall = 99.05\%$).
- At **100.00%**, 64 positive pairs drop below the threshold ($FN = 64, Recall = 39.05\%$), leaving only exact copies (Categories A and C).
