# False Positive Analysis (370 Pairs)
## CodeAssess — SWP391 RBL Module 4: AST Plagiarism & Code Analytics Engine

- **Audit Date:** 2026-10-12
- **Data Source:** `dataset/experiments/baseline-v1/results/baseline_pair_results.csv`, `dataset/experiments/audit-v1/fp_pairs.csv`
- **Official Threshold:** $\ge 70.00\%$
- **Total Evaluated Negative Pairs:** 430
- **Total False Positives:** **370**
- **False Positive Rate (FPR):** **86.05%** ($370 / 430$)

---

## 1. Executive Summary

In the baseline experiment on Dataset v1.0, 370 out of 430 negative pairs were flagged as plagiarism, yielding an apparent False Positive Rate of 86.05%. 

An exhaustive pair-by-pair audit confirms that **all 370 False Positives originate from synthetic dataset template homogeneity**, rather than an arithmetic or algorithmic bug in the production pipeline:

1. **The 5 Base programs are structurally identical clones** (an identical 10-line loop counter).
2. The dataset generator (`generate_dataset.js`) labeled pairs as `NON_PLAGIARISM` solely because their class filenames differed (`Base1` vs `Base2`, or `Base1` vs `Variant_2_*`).
3. The production engine's normalizer (`AstNormalizer`) correctly stripped class names and variable identifiers to resist renaming attacks.
4. Consequently, the AST pipeline measured the genuine structural similarity between these isomorphic programs (scoring 72.22% to 100.00%), which exceeded the 70.00% threshold.

---

## 2. False Positive Group Breakdown

All 370 false positive pairs are recorded in `dataset/experiments/audit-v1/fp_pairs.csv`. They partition into seven distinct structural pairing groups:

| Group | Pairing Description | Pair Count | Hashes A | Hashes B | Shared | Union | Jaccard Sim | Predicted | Ground Truth |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Group 1** | Base vs Base | **10** | 16 | 16 | 16 | 16 | **100.00%** | `true` | `NON_PLAGIARISM` |
| **Group 2** | Base vs Variant A (Exact Copy) | **60** | 16 | 16 | 16 | 16 | **100.00%** | `true` | `NON_PLAGIARISM` |
| **Group 3** | Base vs Variant B (Renamed) | **60** | 16 | 16 | 16 | 16 | **100.00%** | `true` | `NON_PLAGIARISM` |
| **Group 4** | Base vs Variant C (Comments/Spaces) | **60** | 16 | 16 | 16 | 16 | **100.00%** | `true` | `NON_PLAGIARISM` |
| **Group 5** | Base vs Variant D (Literal Changed) | **60** | 16 | 15 | 13 | 18 | **72.22%** | `true` | `NON_PLAGIARISM` |
| **Group 6** | Base vs Variant E1 (Dead Code) | **60** | 16 | 18 | 16 | 18 | **88.89%** | `true` | `NON_PLAGIARISM` |
| **Group 7** | Base vs Variant E3 (Block Wrapping) | **60** | 16 | 17 | 15 | 18 | **83.33%** | `true` | `NON_PLAGIARISM` |
| **TOTAL** | | **370** | | | | | | | |

### Why Did Variant F1 Yield 0 False Positives?
Of the 430 negative pairs, exactly $430 - 370 = 60$ pairs were **True Negatives** (correctly unflagged). Every single one of these 60 True Negatives consists of an unrelated Base compared against an **F1 variant** (`for -> while` rewrite):
$$15 \text{ F1 variants} \times 4 \text{ unrelated base programs} = 60 \text{ pairs}$$
Because F1 variants score **40.91%**, which is below the 70.00% threshold, all 60 pairs received `predicted = false`, matching the ground-truth label `NON_PLAGIARISM`.

---

## 3. Representative Case Studies with Concrete Evidence

### 3.1 Case 1: Base vs Base (`pair_0106` — `Base1.java` vs `Base2.java`)

- **Pair ID:** `pair_0106`
- **Program A:** `dataset/programs/base/Base1.java`
- **Program B:** `dataset/programs/base/Base2.java`
- **Category:** `G` (`unrelated_base`)
- **Ground Truth:** `NON_PLAGIARISM`
- **Engine Similarity:** **100.00%** (16 shared / 16 union hashes)
- **Outcome:** **FP**

#### Source Code Comparison:
```java
// Base1.java                                // Base2.java
public class Base1 {                         public class Base2 {
    public static void main(String[] args) {     public static void main(String[] args) {
        int myCounter = 0;                           int myCounter = 0;
        for (int loopVar = 0; loopVar < 10; loopVar++) { for (int loopVar = 0; loopVar < 10; loopVar++) {
            myCounter += loopVar;                        myCounter += loopVar;
        }                                            }
        System.out.println("Result: " + myCounter);  System.out.println("Result: " + myCounter);
    }                                            }
}                                            }
```

#### Root Cause:
The only differing token in the entire source file is the class name identifier (`Base1` vs `Base2`). In `AstNormalizer.java`:
```java
else if (node instanceof SimpleName || node instanceof Name) {
    return baseType; // Returns "SimpleName" for both Base1 and Base2
}
```
`SimpleName` strips both identifiers. The normalized ASTs are 100% identical. The ground-truth label `NON_PLAGIARISM` claims these two programs are independent, but they are in fact identical copies. The engine correctly detected plagiarism of structure; the dataset label is invalid.

---

### 3.2 Case 2: Base vs Unrelated Renamed Variant (`pair_0188` — `Base2.java` vs `Variant_1_B_1.java`)

- **Pair ID:** `pair_0188`
- **Program A:** `Base2.java` (Base #2)
- **Program B:** `Variant_1_B_1.java` (Renamed variant generated from Base #1)
- **Ground Truth:** `NON_PLAGIARISM`
- **Engine Similarity:** **100.00%** (16 shared / 16 union hashes)
- **Outcome:** **FP**

#### Source Code Comparison:
```java
// Base2.java                                // Variant_1_B_1.java
public class Base2 {                         public class Variant_1_B_1 {
    public static void main(String[] args) {     public static void main(String[] args) {
        int myCounter = 0;                           int totalCount = 0;
        for (int loopVar = 0; loopVar < 10; loopVar++) { for (int idx = 0; idx < 10; idx++) {
            myCounter += loopVar;                        totalCount += idx;
        }                                            }
        System.out.println("Result: " + myCounter);  System.out.println("Result: " + totalCount);
    }                                            }
}                                            }
```

#### Root Cause:
The ground truth considered `Variant_1_B_1.java` unrelated to `Base2.java` because it was derived from `Base1.java`. However, because `Base1` is identical to `Base2`, `Variant_1_B_1` is also an exact identifier-renamed clone of `Base2`. 

`AstNormalizer` abstracts `myCounter` and `totalCount` to `SimpleName`, and `loopVar` and `idx` to `SimpleName`. Therefore, the pair has 100% structural identity. Flagging it is mathematically unavoidable given the engine's design specification.

---

### 3.3 Case 3: Base vs Literal Modified Variant (`pair_0224` — `Base2.java` vs `Variant_1_D_1.java`)

- **Pair ID:** `pair_0224`
- **Program A:** `Base2.java`
- **Program B:** `Variant_1_D_1.java`
- **Ground Truth:** `NON_PLAGIARISM`
- **Engine Similarity:** **72.22%** (13 shared / 18 union hashes)
- **Outcome:** **FP**

#### Transformation D in `generate_dataset.js`:
```javascript
code = code.replace(/\b10\b/g, '100');
code = code.replace(/"Result: "/g, '"Final Output: "');
```

#### AST Impact:
Because `AstNormalizer` deliberately preserves literal values (`IntegerLiteralExpr[10]` vs `IntegerLiteralExpr[100]` and `StringLiteralExpr[Result: ]` vs `StringLiteralExpr[Final Output: ]`), K-grams spanning those literals change their hash values.
- Hashes in Base2: 16
- Hashes in Variant 1_D_1: 15
- Shared Hashes: 13
- Union Hashes: $16 + 15 - 13 = 18$
- Jaccard Similarity: $13 / 18 = \mathbf{72.22\%}$

Because $72.22\% \ge 70.00\%$, the pair is flagged as plagiarism. In a genuine exam setting, two students independently solving different problems would not share 72.22% of AST K-grams. The high score exists solely because Variant D is a clone of the same 10-line loop.

---

### 3.4 Case 4: Base vs Dead Code Injection (`pair_0260` — `Base2.java` vs `Variant_1_E1_1.java`)

- **Pair ID:** `pair_0260`
- **Ground Truth:** `NON_PLAGIARISM`
- **Engine Similarity:** **88.89%** (16 shared / 18 union hashes)
- **Outcome:** **FP**

#### Transformation E1 in `generate_dataset.js`:
```javascript
code = code.replace(';', ';\n        if(false){int dead_var=0;}');
```

#### AST Impact:
The dead `if(false)` block inserts an `IfStmt`, `BooleanLiteralExpr[false]`, and `BlockStmt` into the AST, producing 2 extra winnowed hashes (Hashes B = 18). All 16 original base hashes remain intact (Shared = 16).
- Jaccard Similarity: $16 / 18 = \mathbf{88.89\%}$
- Outcome: Flagged ($\ge 70\%$) $\rightarrow$ FP against ground truth.

---

### 3.5 Case 5: Base vs Block Wrapping (`pair_0296` — `Base2.java` vs `Variant_1_E3_1.java`)

- **Pair ID:** `pair_0296`
- **Ground Truth:** `NON_PLAGIARISM`
- **Engine Similarity:** **83.33%** (15 shared / 18 union hashes)
- **Outcome:** **FP**

#### Transformation E3 in `generate_dataset.js`:
Wraps the method body in an extra `{ ... }` block (`BlockStmt`).
- Hashes A = 16, Hashes B = 17, Shared = 15, Union = 18
- Jaccard Similarity: $15 / 18 = \mathbf{83.33\%}$
- Outcome: Flagged ($\ge 70\%$) $\rightarrow$ FP against ground truth.

---

## 4. Algorithmic Tradeoffs vs Dataset Artifacts

It is vital to distinguish between a defect in the AST engine and an artifact of the benchmark dataset:

| Dimension | Production Engine Behavior | Synthetic Dataset v1.0 Reality | Conclusion |
| :--- | :--- | :--- | :--- |
| **Identifier Renaming** | Strips `SimpleName` to defeat variable/class renaming attacks. | All 5 base programs differ only by class name `Base1`..`Base5`. | Engine behaves exactly as designed; dataset fails to provide structurally distinct base programs. |
| **Literal Differentiation** | Preserves literal constants (`IntegerLiteralExpr[val]`) to separate distinct formulas. | Base programs share identical constants (`0`, `10`, `"Result: "`). | Correct engine design; dataset contains no variety in constants across problems. |
| **Control Flow Sensitivity** | Tracks exact statement sequencing via K-grams ($K=5$). | Negative pairs feature identical control-flow topologies. | Correct engine design; negative pairs are not topologically independent. |

---

## 5. Recommended Dataset Correction (Dataset v2.0 Design)

To evaluate real-world False Positive Rate and Specificity accurately, Dataset v2.0 must replace the 5 identical loop templates with **five genuinely distinct computer science algorithmic problems**:

1. **Problem 1:** `BinarySearch.java` (Iterative divide-and-conquer searching)
2. **Problem 2:** `BubbleSort.java` (Nested-loop comparison-based sorting)
3. **Problem 3:** `MatrixMultiply.java` (Triple nested loops with 2D array indexing)
4. **Problem 4:** `SinglyLinkedList.java` (Object-oriented reference chaining and node insertion)
5. **Problem 5:** `PalindromeChecker.java` (Two-pointer string indexing and conditional recursion)

### Projected Separation on Dataset v2.0:
- Cross-problem Jaccard similarity: $< 15.00\%$
- False Positives at 70.00% threshold: **0**
- Projected Specificity: **100.00%**
- Projected Accuracy: **$\ge 97\%$**
