# Dataset v2.0 Validation & Integrity Report
## CodeAssess — SWP391 RBL Module 4: AST Plagiarism & Code Analytics Engine

- **Validation Date:** 2026-10-12
- **Validation Scope:** `dataset/v2/`
- **Compiler:** JDK 21 LTS (`javac 21.0.12.1`)
- **Status:** **100% Passed (Zero Anomalies, Zero Warnings)**

---

## 1. Executive Summary

This report documents the formal validation of **Dataset v2.0**, confirming its structural integrity, programmatic compilability, cryptographic manifest consistency, and clean negative separation prior to baseline benchmarking.

### Validation Highlights:
1. **Compilation [PASSED]:** All **110 Java programs** (5 base programs + 105 generated variants) compile with **zero compilation errors** under JDK 21 LTS.
2. **Cryptographic Manifest [PASSED]:** The manifest `manifest.csv` records all 110 programs with exact line counts, byte sizes, and SHA-256 digests.
3. **Pair Manifest Integrity [PASSED]:** `pairs.csv` contains exactly 535 valid evaluation pairs (105 positive, 430 negative) with 0 self-pairs, 0 duplicate pairs, and 0 missing file references.
4. **Pre-Generation Negative Separation [PASSED]:** The 10 pairwise comparisons between the five base programs yielded Jaccard similarities between **9.18% and 21.43%** (mean: **13.81%**), confirming that independently authored programs score well below the 70.00% threshold.

---

## 2. File Inventory & Compilability Audit

```
dataset/v2/
├── BaseProgramComparator.class
├── BaseProgramComparator.java
├── DATASET_GENERATION_PLAN.md
├── DATASET_V2_SPECIFICATION.md
├── DatasetV2Generator$BaseInfo.class
├── DatasetV2Generator$VariantInfo.class
├── DatasetV2Generator.class
├── DatasetV2Generator.java
├── GROUND_TRUTH_SCHEMA.md
├── manifest.csv
├── pairs.csv
└── programs/
    ├── base/           (5 .java source files + 5 .class files)
    └── generated/      (105 .java source files + 105 .class files)
```

### 2.1 Base Programs Audit

| Program Name | File Path | Line Count | Byte Count | Compilation Status | SHA-256 Digest |
| :--- | :--- | :---: | :---: | :---: | :--- |
| `BinarySearch` | `dataset/v2/programs/base/BinarySearch.java` | 23 | 667 | **OK** (0 errors) | `a44cb0eace48a658defb5ee96d6622e...` |
| `BubbleSort` | `dataset/v2/programs/base/BubbleSort.java` | 20 | 586 | **OK** (0 errors) | `103abdacf70446fb8f183e79bd9f60f...` |
| `MatrixMultiply` | `dataset/v2/programs/base/MatrixMultiply.java` | 25 | 789 | **OK** (0 errors) | `c81a11bd63fd038a348f32025677f2b...` |
| `SinglyLinkedList` | `dataset/v2/programs/base/SinglyLinkedList.java` | 28 | 667 | **OK** (0 errors) | `5b2389e77f6034aee1f9da5707a997d...` |
| `PalindromeChecker`| `dataset/v2/programs/base/PalindromeChecker.java`| 20 | 554 | **OK** (0 errors) | `72b92927fd8bdb22bce35eba4159774...` |

### 2.2 Generated Variants Audit
- **Total Generated Files:** **105 Java files** in `dataset/v2/programs/generated/`.
- **Distribution:** Exactly 21 variants per base program across 7 categories ($5 \times 7 \times 3$).
- **Compilation Check:** Command `javac dataset/v2/programs/generated/*.java` executed with **exit code 0**. All 105 classes produced corresponding `.class` bytecode without errors.

---

## 3. Preliminary Base-vs-Base Separation Verification

Before generating variants, the 5 base programs were compared using the production AST pipeline (`JavaAstParser`, `AstNormalizer`, `KGramGenerator(5)`, `FingerprintHasher`, `Winnower(4)`, `JaccardSimilarityCalculator`):

| Pair ID | Program A | Program B | Hashes A | Hashes B | Shared Hashes | Jaccard Similarity | Exceeds 70% Cutoff? |
| :---: | :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| `pair_0106` | `BinarySearch.java` | `BubbleSort.java` | 44 | 41 | 12 | **16.44%** | `false` |
| `pair_0107` | `BinarySearch.java` | `MatrixMultiply.java` | 44 | 57 | 9 | **9.78%** | `false` |
| `pair_0108` | `BinarySearch.java` | `SinglyLinkedList.java` | 44 | 50 | 8 | **9.30%** | `false` |
| `pair_0109` | `BinarySearch.java` | `PalindromeChecker.java` | 44 | 35 | 13 | **19.70%** | `false` |
| `pair_0110` | `BubbleSort.java` | `MatrixMultiply.java` | 41 | 57 | 14 | **16.67%** | `false` |
| `pair_0111` | `BubbleSort.java` | `SinglyLinkedList.java` | 41 | 50 | 8 | **9.64%** | `false` |
| `pair_0112` | `BubbleSort.java` | `PalindromeChecker.java` | 41 | 35 | 10 | **15.15%** | `false` |
| `pair_0113` | `MatrixMultiply.java` | `SinglyLinkedList.java` | 57 | 50 | 9 | **9.18%** | `false` |
| `pair_0114` | `MatrixMultiply.java` | `PalindromeChecker.java` | 57 | 35 | 9 | **10.84%** | `false` |
| `pair_0115` | `SinglyLinkedList.java` | `PalindromeChecker.java` | 50 | 35 | 15 | **21.43%** | `false` |

### Key Observation:
- **Maximum Negative Base Similarity:** **21.43%**
- **Minimum Negative Base Similarity:** **9.18%**
- **Average Negative Base Similarity:** **13.81%**
- **Safety Margin:** Every unrelated base pair scores at least **48.57 percentage points below the 70.00% threshold**.

---

## 4. Manifest and Pairs CSV Validation

### 4.1 Schema Validation Checklist

| Check | Requirement | Result | Status |
| :--- | :--- | :--- | :---: |
| Row Count in `manifest.csv` | 1 header + 110 data rows | 111 total lines | **PASSED** |
| Unique `program_id` | `prog_001` .. `prog_110` | 110 unique IDs | **PASSED** |
| Row Count in `pairs.csv` | 1 header + 535 data rows | 536 total lines | **PASSED** |
| Unique `pair_id` | `pair_0001` .. `pair_0535` | 535 unique IDs | **PASSED** |
| Directed Duplicate Pairs | No repeated `(A, B)` | 535 unique directed | **PASSED** |
| Undirected Duplicate Pairs | No `(A, B)` with `(B, A)` | 535 unique undirected | **PASSED** |
| Self-Comparisons | No `(A, A)` pairs | 0 self-pairs | **PASSED** |
| Target File References | Every referenced file exists on disk | 110 / 110 verified | **PASSED** |
| Ground-Truth Labels | Only `PLAGIARISM` or `NON_PLAGIARISM` | 105 PLAG / 430 NON | **PASSED** |

---

## 5. Conclusion

Dataset v2.0 satisfies all quality, integrity, and safety requirements. The dataset successfully eliminates the template homogeneity flaw of Dataset v1.0, establishes a clean negative separation boundary, and is fully validated for formal baseline evaluation.
