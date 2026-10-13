# Baseline Experiment v1.0 — AST Plagiarism & Code Analytics Engine

## Overview
This directory contains the baseline experiment setup, code, raw results, and research analysis for **Module 4 (AST Plagiarism & Code Analytics Engine)** of the CodeAssess platform under the SWP391 Research-Based Learning (RBL) initiative.

## Research & Pipeline Specifications
The experiment directly invokes the production AST plagiarism detection classes located in `Back end/src/main/java/com/codeassess/plagiarism`:
- **Parser:** `JavaAstParser` (JavaParser 3.25.9)
- **Normalizer:** `AstNormalizer` (Strips identifiers and comments, preserves structural literals)
- **K-Gram Tokenizer:** `KGramGenerator` ($K = 5$)
- **Hashing:** `FingerprintHasher` (FNV-1a 64-bit algorithm)
- **Winnowing:** `Winnower` (Window size $W = 4$, rightmost minimum tie-break)
- **Similarity Metric:** `JaccardSimilarityCalculator`
- **Official Threshold:** **70.00%** ($isFlagged = similarity \ge 70.00$)

## Dataset v1.0 Structure
- **Dataset Path:** `dataset/ground-truth/pairs.csv` and `dataset/programs/`
- **Total Java Programs:** 110 (5 base programs + 105 synthetic variant programs)
- **Total Comparison Pairs:** 535 pairs
  - **PLAGIARISM Pairs:** 105 pairs across 7 transformation categories:
    - `A`: Exact copy (15 pairs)
    - `B`: Identifier renaming (15 pairs)
    - `C`: Comment & whitespace manipulation (15 pairs)
    - `D`: Literal changes (15 pairs)
    - `E1`: Dead code injection (15 pairs)
    - `E3`: Block wrapping (15 pairs)
    - `F1`: Control flow rewrite (For-to-While, 15 pairs)
  - **NON_PLAGIARISM Pairs:** 430 pairs (Category `G`: Base-vs-Base and Variant-vs-Unrelated Base)

## Execution & Reproducibility
To reproduce the experiment from the repository root:

### Windows Batch:
```bat
dataset\experiments\baseline-v1\run_baseline.bat
```

### PowerShell:
```powershell
powershell -ExecutionPolicy Bypass -File dataset\experiments\baseline-v1\run_baseline.ps1
```

## Directory Structure
```
baseline-v1/
├── BaselineExperimentRunner.java      # Java runner executing the exact production pipeline
├── run_baseline.bat                   # Batch script wrapper for execution
├── run_baseline.ps1                   # PowerShell script with classpath resolution
├── README.md                          # Documentation and reproducibility guide
├── reports/
│   └── BASELINE_EXPERIMENT_REPORT.md  # Comprehensive research analysis report for IEEE RBL paper
└── results/
    ├── baseline_pair_results.csv      # Granular similarity and outcome for each of the 535 pairs
    ├── category_metrics.csv           # Performance metrics broken down by category (A-G)
    ├── confusion_matrix.csv           # Confusion matrix and global classification metrics
    └── threshold_analysis.csv         # Diagnostic sensitivity analysis across thresholds (10%-100%)
```

## Summary of Empirical Findings
1. **Zero False Negatives in Structural Mutations (A–E3):** The AST pipeline achieved 100% recall and 100% detection rate for exact copies, identifier renamings, comment/whitespace stripping, literal modifications, dead code injection, and block wrapping.
2. **Control Flow Sensitivity (F1 - For-to-While):** When loops are restructured from `for` to `while`, AST structural representations diverge, resulting in a similarity of **40.91%**, which falls below the 70.00% threshold, yielding 15 False Negatives.
3. **Dataset v1.0 Template Artifact (High FPR):** In Dataset v1.0, all 5 base programs (`Base1.java` through `Base5.java`) were synthesized using identical algorithms (loop counter). Because identifier abstraction removes class and variable names, the engine mathematically detected 70%–100% similarity across negative pairs. This highlights a dataset design characteristic rather than an algorithmic flaw.
