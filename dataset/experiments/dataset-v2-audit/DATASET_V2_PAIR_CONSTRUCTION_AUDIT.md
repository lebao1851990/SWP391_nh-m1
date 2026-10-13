# Dataset v2.0 Pair Construction & Topology Audit
## CodeAssess — SWP391 RBL Module 4: AST Plagiarism & Code Analytics Engine

- **Audit Date:** 2026-10-12
- **Audit Directory:** `dataset/experiments/dataset-v2-audit/`
- **Audit Tool:** `com.codeassess.audit.v2.DatasetV2Auditor`
- **Data Source:** `dataset/v2/pairs.csv`, `dataset/experiments/dataset-v2-audit/pair_type_counts.csv`
- **Total Evaluated Pairs:** 535

---

## 1. Executive Summary

This audit evaluated the mathematical construction, sampling topology, and combinatorial completeness of the 535 evaluation pairs in **Dataset v2.0**.

### Key Findings:
1. **Structural Sizing [VERIFIED]:**
   Dataset v2.0 contains exactly **535 pairs**, structured as:
   - **105 Positive Pairs** (`BASE_VS_OWN_VARIANT`, Categories A–F1)
   - **10 Negative Pairs** (`BASE_VS_BASE`, Category G)
   - **420 Negative Pairs** (`BASE_VS_UNRELATED_VARIANT`, Category G)
2. **Intentional Combinatorial Design [VERIFIED]:**
   Out of the theoretical universe of $\binom{110}{2} = 5,995$ possible program pairings, Dataset v2.0 purposefully evaluates a curated subset of 535 pairs. This design maintains exact statistical parity with Dataset v1.0 (105 positive, 430 negative, 535 total), enabling an unconfounded comparative evaluation of the algorithm.
3. **Variant-vs-Variant Space [VERIFIED]:**
   Pairings between variants of the same problem (1,050 pairs) and cross-problem variants (4,410 pairs) are omitted from the primary benchmark.

---

## 2. Combinatorial Pair Universe Analysis

For a dataset of $N = 110$ source files (5 base programs and 105 variants across 5 problem domains), the complete theoretical space of distinct unordered pairs is:

$$\binom{110}{2} = \frac{110 \times 109}{2} = \mathbf{5,995 \text{ pairs}}$$

### 2.1 Theoretical Universe Decomposition

| Pair Sub-Universe | Definition | Ground Truth | Theoretical Total | Included in Dataset v2.0 | Coverage Ratio |
| :--- | :--- | :---: | :---: | :---: | :---: |
| **`BASE_VS_BASE`** | Unrelated base programs across distinct problems | `NON_PLAGIARISM` | $\binom{5}{2} = 10$ | **10** | **100.0%** |
| **`BASE_VS_OWN_VARIANT`** | Base program vs its own generated variants | `PLAGIARISM` | $5 \times 21 = 105$ | **105** | **100.0%** |
| **`BASE_VS_UNRELATED_VARIANT`**| Base program vs variants of another problem | `NON_PLAGIARISM` | $5 \times (105 - 21) = 420$ | **420** | **100.0%** |
| **`VARIANT_VS_SAME_PROBLEM`** | Two variants derived from the same base program | `PLAGIARISM` | $5 \times \binom{21}{2} = 1,050$ | **0** (Omitted) | **0.0%** |
| **`VARIANT_VS_CROSS_PROBLEM`** | Two variants derived from different base problems | `NON_PLAGIARISM` | $\binom{105}{2} - 1,050 = 4,410$ | **0** (Omitted) | **0.0%** |
| **TOTAL UNIVERSE** | All unordered combinations of 110 programs | Mixed | **5,995** | **535** | **8.92%** |

---

## 3. Pair-Type Count & Category Cross-Tabulation

### 3.1 Pair Count by Type

| Pair Type Category | Pair Count | Evaluation Label | Lineage / Provenance |
| :--- | :---: | :---: | :--- |
| `BASE_VS_OWN_VARIANT` | **105** | `PLAGIARISM` | Direct generative derivation from base program |
| `BASE_VS_BASE` | **10** | `NON_PLAGIARISM` | Independent algorithmic problem solutions |
| `BASE_VS_UNRELATED_VARIANT` | **420** | `NON_PLAGIARISM` | Cross-domain evaluation against independent variants |
| **TOTAL EVALUATED** | **535** | **105 Pos / 430 Neg** | |

### 3.2 Category-by-Pair-Type Cross-Tabulation

| Category | Category Description | `BASE_VS_OWN_VARIANT` | `BASE_VS_BASE` | `BASE_VS_UNRELATED_VARIANT` | Total Pairs | Ground Truth |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: |
| **A** | Exact Copy | 15 | 0 | 0 | **15** | `PLAGIARISM` |
| **B** | Identifier Renaming | 15 | 0 | 0 | **15** | `PLAGIARISM` |
| **C** | Comments & Formatting | 15 | 0 | 0 | **15** | `PLAGIARISM` |
| **D** | Literal Modifications | 15 | 0 | 0 | **15** | `PLAGIARISM` |
| **E1** | Dead Code Injection | 15 | 0 | 0 | **15** | `PLAGIARISM` |
| **E3** | Block Restructuring | 15 | 0 | 0 | **15** | `PLAGIARISM` |
| **F1** | Control-Flow Rewrite | 15 | 0 | 0 | **15** | `PLAGIARISM` |
| **G** | Unrelated Negative Pairs | 0 | 10 | 420 | **430** | `NON_PLAGIARISM` |
| **TOTAL** | | **105** | **10** | **420** | **535** | |

---

## 4. Evaluation of Pair Selection Rationale

### 4.1 Why Retain 535 Pairs Instead of 5,995 Pairs?
1. **Scientific Comparability with Dataset v1.0:**
   In Dataset v1.0, the benchmark evaluated exactly 535 pairs (105 positive, 430 negative). Keeping the exact same sample size and class proportions ($19.63\%$ positive vs $80.37\%$ negative) ensures that performance deltas reflect **improved dataset validity**, not sample-size or class-imbalance artifacts.
2. **Computational Tractability:**
   Evaluating 535 pairs requires ~580 ms, making automated regression testing and validation instantaneous.
3. **Primary Plagiarism Use Case:**
   In an academic exam, the primary question is: *"Does submission B plagiarize the instructor's reference solution or a specific peer's solution A?"* Comparing variants against base references represents the canonical retrieval task.

### 4.2 Known Limitation: Variant-to-Variant Transitivity
- The current 535-pair manifest does not test whether two student variants that copied from the same base independently (e.g., Student 1 renamed variables, while Student 2 injected dead code) will flag each other as plagiarism without the base program present.
- In a production university exam where the instructor reference is missing, detecting student-to-student copying requires the full $\binom{N}{2}$ matrix.
- *Recommendation:* Future Milestone benchmarks may expand into a complete $\binom{110}{2} = 5,995$ pair suite (`Dataset v2.1-FullMatrix`) to evaluate variant-variant transitivity.
