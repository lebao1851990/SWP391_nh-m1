# Ground-Truth Schema & Provenance Specification
## CodeAssess — SWP391 RBL Module 4: AST Plagiarism & Code Analytics Engine

- **Schema Version:** 2.0.0
- **Applies To:** `dataset/v2/pairs.csv` and `dataset/v2/manifest.csv`
- **Location:** `dataset/v2/`

---

## 1. Principles of Ground-Truth Separation

In empirical software engineering and plagiarism research, labeling must avoid circular reasoning. A critical rule enforced in Dataset v2.0 is:
> **The detector's similarity score is NEVER used to assign or adjust ground-truth labels.**

Ground truth is defined using four orthogonal, decoupled dimensions:
1. **Provenance:** Direct historical derivation relationship (Did code $B$ copy/derive from code $A$?).
2. **Semantic Relationship:** Algorithmic equivalence or behavioral modification.
3. **Transformation Category:** The specific syntactic or structural alteration applied.
4. **Evaluation Ground Truth:** The formal binary classification benchmark label (`PLAGIARISM` vs `NON_PLAGIARISM`).

---

## 2. Schema: `pairs.csv`

The file `dataset/v2/pairs.csv` defines all 535 comparison pairs evaluated in the benchmark.

```csv
pair_id,program_a,program_b,category,transformation,provenance,semantic_relationship,ground_truth
```

### 2.1 Field Definitions

| Column | Type | Allowed Values / Format | Description |
| :--- | :--- | :--- | :--- |
| **`pair_id`** | String | `pair_0001` to `pair_0535` | Globally unique, zero-padded identifier for the evaluation pair. |
| **`program_a`** | String | File name (e.g., `BinarySearch.java`) | Baseline comparison program (must exist in `dataset/v2/programs/base/` or `generated/`). |
| **`program_b`** | String | File name (e.g., `BinarySearch_A_1.java`) | Target comparison program (must exist in `dataset/v2/programs/base/` or `generated/`). |
| **`category`** | Enum | `A`, `B`, `C`, `D`, `E1`, `E3`, `F1`, `G` | Plagiarism transformation category (`A`–`F1` for positive, `G` for negative). |
| **`transformation`** | String | `transformation_A_1`, `unrelated_base`, etc. | Specific transformation rule and repetition index. |
| **`provenance`** | Enum | `DERIVED`, `INDEPENDENT` | Lineage of program B relative to program A. |
| **`semantic_relationship`**| Enum | See Table 2.2 below | Semantic and behavioral relationship between programs A and B. |
| **`ground_truth`** | Enum | `PLAGIARISM`, `NON_PLAGIARISM` | Official academic benchmark target label. |

### 2.2 Enumeration Values for `semantic_relationship`

| Value | Definition | Ground Truth | Typical Categories |
| :--- | :--- | :---: | :---: |
| **`EQUIVALENT_COPY`** | Identical program logic; only class identifier changed. | `PLAGIARISM` | Category A |
| **`EQUIVALENT_RENAMED`** | Identical algorithm and control flow; identifiers systematically renamed. | `PLAGIARISM` | Category B |
| **`EQUIVALENT_FORMATTED`** | Identical AST; formatting, whitespace, or comments modified. | `PLAGIARISM` | Category C |
| **`MODIFIED_LITERALS`** | Identical algorithmic logic; literal values / test constants modified. | `PLAGIARISM` | Category D |
| **`EQUIVALENT_DEAD_CODE`**| Identical live logic; benign dead code injected into compilation unit. | `PLAGIARISM` | Category E1 |
| **`EQUIVALENT_RESTRUCTURED`**| Identical logic; statements wrapped in equivalent block scopes. | `PLAGIARISM` | Category E3 |
| **`EQUIVALENT_CFG`** | Identical algorithm; loop control structures transformed (`for` $\leftrightarrow$ `while`). | `PLAGIARISM` | Category F1 |
| **`INDEPENDENT_ALGORITHM`** | Two distinct algorithms solving different computational problems. | `NON_PLAGIARISM` | Category G |

---

## 3. Schema: `manifest.csv`

The file `dataset/v2/manifest.csv` inventories all 110 Java source files comprising the dataset, guaranteeing cryptographic integrity and provenance tracking.

```csv
program_id,file_name,program_type,base_program,category,transformation,line_count,byte_count,sha256
```

### 3.1 Field Definitions

| Column | Type | Description | Example |
| :--- | :--- | :--- | :--- |
| **`program_id`** | String | Unique program identifier (`prog_001` .. `prog_110`) | `prog_001` |
| **`file_name`** | String | Exact Java source filename on disk | `BinarySearch.java` |
| **`program_type`** | Enum | `BASE` or `VARIANT` | `BASE` |
| **`base_program`** | String | Associated problem domain name | `BinarySearch` |
| **`category`** | Enum | Transformation category (`NONE` for base, `A`..`F1` for variant) | `B` |
| **`transformation`**| String | Specific generator transformation applied | `transformation_B_1` |
| **`line_count`** | Integer | Total line count of the source file | `23` |
| **`byte_count`** | Integer | Exact size in bytes | `760` |
| **`sha256`** | String | Cryptographic SHA-256 hash digest of the file content | `89f2c72ffe3dd...` |

---

## 4. Integrity Constraints & Invariants

1. **Existence Invariant:** Every file referenced in `pairs.csv` (`program_a` and `program_b`) MUST exist in `manifest.csv` and on disk.
2. **Irreflexivity Invariant:** For all rows in `pairs.csv`, $\text{program\_a} \neq \text{program\_b}$ (no self-comparisons).
3. **Symmetry Invariant:** For any two files $A$ and $B$, only one directed pair $(A, B)$ exists in `pairs.csv`. There are no symmetric duplicates $(B, A)$.
4. **Class Balance Invariant:** The positive-to-negative pair ratio is exactly $105 : 430$ ($19.63\% : 80.37\%$), preserving the exact class distribution of Dataset v1.0.
