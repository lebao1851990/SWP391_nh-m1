# Dataset v1.0 Ground Truth & False Positive Validation Report
**Project:** CodeAssess | Module 4: AST Plagiarism & Code Analytics Engine  
**Course & Milestone:** SWP391 RBL — Milestone 2  
**Date:** 2026-10-12  
**Author / Auditor:** Antigravity AI Assistant  
**Dataset Version:** Dataset v1.0 (Seed = 42, 5 Base Programs, 105 Variants, 535 Ground-Truth Pairs)  
**Status:** VALIDATION COMPLETE (Template Homogeneity Documented; Ground Truth Preserved Unaltered)

---

## 1. Executive Summary

This investigation independently audits the **370 False Positives (FP)** identified during the baseline experiment of the production AST plagiarism engine against Dataset v1.0.

### Key Finding:
The high False Positive Rate (**86.05%**, 370 FP out of 430 negative pairs) is **not an algorithmic error** of the AST pipeline. It is a mathematical consequence of **Template Homogeneity** in the synthetic dataset generator (`dataset/scripts/generate_dataset.py` / `generate_dataset.js`). All five base programs (`Base1.java` through `Base5.java`) were synthesized using identical loop-counter algorithmic bodies. Because `AstNormalizer` abstracts away class names and variable identifiers, the AST representations of the five base programs are structurally identical. Consequently, negative pairs (labeled `NON_PLAGIARISM`) have genuine AST Jaccard similarities between **72.22% and 100.00%**, triggering the production 70.00% flag threshold.

Per RBL integrity rules:
1. **Dataset v1.0 ground-truth labels remain strictly unaltered.**
2. **Production AST algorithms and threshold (70.00%) remain unchanged.**
3. **The baseline metrics are recorded honestly.**
4. A concrete specification for a corrected **Dataset v2.0** is provided.

---

## 2. Source Code Inspection of Base Programs

All five base programs located in `dataset/programs/base/` were inspected:
- [Base1.java](file:///d:/AST%20Plagiarism%20Detection/dataset/programs/base/Base1.java) (254 bytes)
- [Base2.java](file:///d:/AST%20Plagiarism%20Detection/dataset/programs/base/Base2.java) (254 bytes)
- [Base3.java](file:///d:/AST%20Plagiarism%20Detection/dataset/programs/base/Base3.java) (254 bytes)
- [Base4.java](file:///d:/AST%20Plagiarism%20Detection/dataset/programs/base/Base4.java) (254 bytes)
- [Base5.java](file:///d:/AST%20Plagiarism%20Detection/dataset/programs/base/Base5.java) (254 bytes)

### Code Excerpt Comparison:

```java
// Base1.java
public class Base1 {
    public static void main(String[] args) {
        int myCounter = 0;
        for (int loopVar = 0; loopVar < 10; loopVar++) {
            myCounter += loopVar;
        }
        System.out.println("Result: " + myCounter);
    }
}
```

```java
// Base2.java
public class Base2 {
    public static void main(String[] args) {
        int myCounter = 0;
        for (int loopVar = 0; loopVar < 10; loopVar++) {
            myCounter += loopVar;
        }
        System.out.println("Result: " + myCounter);
    }
}
```

*(Base3.java, Base4.java, and Base5.java are identical to the above, varying only in `public class Base{i}`)*.

### Generator Implementation Evidence:
From `dataset/scripts/generate_dataset.py` (lines 68–85):
```python
def write_base_programs():
    for i in range(1, 6):
        base_name = f'base_{i}.java'
        base_path = os.path.join(BASE_PROGRAMS_DIR, base_name)
        content = f"""public class Base{i} {{
    public static void main(String[] args) {{
        int myCounter = 0;
        for (int loopVar = 0; loopVar < 10; loopVar++) {{
            myCounter += loopVar;
        }}
        System.out.println("Result: " + myCounter);
    }}
}}
"""
        with open(base_path, 'w') as f:
            f.write(content)
```

**Verdict:** The five base programs are **not genuinely unrelated algorithms**. They are isomorphic copies of a single loop-counter algorithm.

---

## 3. AST Normalization & Hash Impact

The production normalization class [AstNormalizer.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/plagiarism/normalizer/AstNormalizer.java) traverses the AST and applies the following rule:

```java
// AstNormalizer.java (lines 63-65)
else if (node instanceof SimpleName || node instanceof Name) {
    return baseType; // Identifiers stripped: Base1, Base2, myCounter, loopVar all map to "SimpleName"
}
```

1. **AST Representation:** Both `Base1.java` and `Base2.java` produce an identical sequence of normalized AST tokens:
   - `CompilationUnit -> ClassOrInterfaceDeclaration -> SimpleName -> MethodDeclaration -> ... -> ForStmt -> ... -> MethodCallExpr`
2. **K-Gram Generation ($K=5$):** Both produce identical sets of 5-gram token strings.
3. **FNV-1a 64-bit Fingerprints:** Both produce identical sets of 16 winnowed hashes.
4. **Jaccard Similarity Calculation:**
   $$\text{Jaccard}(Base_1, Base_2) = \frac{|\text{Hashes}(Base_1) \cap \text{Hashes}(Base_2)|}{|\text{Hashes}(Base_1) \cup \text{Hashes}(Base_2)|} = \frac{16}{16} = 100.00\%$$
5. Because $100.00\% \ge 70.00\%$, the production engine flags the pair as similar.

---

## 4. Breakdown of the 370 False Positives

Category G (Negative pairs) in `dataset/ground-truth/pairs.csv` consists of **430 pairs**:
- **10 Base-vs-Base pairs** (`pair_0106` to `pair_0115`)
- **420 Base-vs-Unrelated-Variant pairs** (`pair_0116` to `pair_0535`)

### 4.1. Base-vs-Base Pairs (10 Pairs — All 10 are FP)

| Pair ID | Program A | Program B | Ground Truth | Hashes A | Hashes B | Shared | Sim Rate | Predicted | Outcome |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `pair_0106` | Base1.java | Base2.java | NON_PLAGIARISM | 16 | 16 | 16 | 100.00% | Flagged | **FP** |
| `pair_0107` | Base1.java | Base3.java | NON_PLAGIARISM | 16 | 16 | 16 | 100.00% | Flagged | **FP** |
| `pair_0108` | Base1.java | Base4.java | NON_PLAGIARISM | 16 | 16 | 16 | 100.00% | Flagged | **FP** |
| `pair_0109` | Base1.java | Base5.java | NON_PLAGIARISM | 16 | 16 | 16 | 100.00% | Flagged | **FP** |
| `pair_0110` | Base2.java | Base3.java | NON_PLAGIARISM | 16 | 16 | 16 | 100.00% | Flagged | **FP** |
| `pair_0111` | Base2.java | Base4.java | NON_PLAGIARISM | 16 | 16 | 16 | 100.00% | Flagged | **FP** |
| `pair_0112` | Base2.java | Base5.java | NON_PLAGIARISM | 16 | 16 | 16 | 100.00% | Flagged | **FP** |
| `pair_0113` | Base3.java | Base4.java | NON_PLAGIARISM | 16 | 16 | 16 | 100.00% | Flagged | **FP** |
| `pair_0114` | Base3.java | Base5.java | NON_PLAGIARISM | 16 | 16 | 16 | 100.00% | Flagged | **FP** |
| `pair_0115` | Base4.java | Base5.java | NON_PLAGIARISM | 16 | 16 | 16 | 100.00% | Flagged | **FP** |

### 4.2. Base-vs-Unrelated-Variant Pairs (420 Pairs — 360 FP, 60 TN)
Each of the 5 base programs is paired against the variants generated from the other 4 bases:
$5 \times (105 - 21) = 5 \times 84 = 420$ pairs.

Because variants inherit the structural template of their parent base:
- Base vs Variant A (Exact Copy template): $100.00\%$ similarity $\rightarrow$ **FP** (60 pairs)
- Base vs Variant B (Identifier Rename template): $100.00\%$ similarity $\rightarrow$ **FP** (60 pairs)
- Base vs Variant C (Comment/Whitespace template): $100.00\%$ similarity $\rightarrow$ **FP** (60 pairs)
- Base vs Variant D (Literal Change template): $72.22\%$ similarity $\rightarrow$ **FP** (60 pairs)
- Base vs Variant E1 (Dead Code template): $88.89\%$ similarity $\rightarrow$ **FP** (60 pairs)
- Base vs Variant E3 (Block Wrapping template): $83.33\%$ similarity $\rightarrow$ **FP** (60 pairs)
- Base vs Variant F1 (For-to-While template): **$40.91\%$ similarity** $\rightarrow$ **TN** (60 pairs)

**Total FP Count:** $10 \text{ (Base-Base)} + 360 \text{ (Base-Variant)} = \mathbf{370\text{ FP}}$.  
**Total TN Count:** $\mathbf{60\text{ TN}}$ (all from F1 variant comparisons).

---

## 5. Distinction Between Algorithmic Failure and Dataset Flaw

| Aspect | What the Engine Measured | What Ground Truth Assumed | Evaluation |
| :--- | :--- | :--- | :--- |
| **Code Structure** | Both programs execute the identical loop-counter logic. | Programs with different file names (`Base1` vs `Base2`) are independent assignments. | The engine correctly recognized structural identity. |
| **Identifier Stripping** | Normalizer stripped class names `Base1` and `Base2`. | Ground truth relied on different class names to declare "non-plagiarism". | Normalizer operated as designed to defeat rename attacks. |
| **False Positive Verdict** | Mathematically identical ASTs yielded $100\%$ similarity. | Labeled `NON_PLAGIARISM` in `pairs.csv`. | Ground truth labels conflict with source reality. |

---

## 6. Proposed Specification for Dataset v2.0 (Future Work)

To evaluate real-world False Positive rates without synthetic artifacts, Dataset v2.0 should be constructed using **five distinct algorithmic problems**:

1. **Base 1 (Binary Search):** Divide-and-conquer search on sorted arrays.
2. **Base 2 (Bubble Sort):** Nested loops with swap condition.
3. **Base 3 (Matrix Multiplication):** Triple nested loop over 2D integer arrays.
4. **Base 4 (Singly Linked List Insertion):** Node references and pointer traversal.
5. **Base 5 (String Palindrome Check):** Two-pointer character array comparison.

### Projected Impact on Dataset v2.0:
- Distinct algorithms have fundamentally different AST node structures.
- Negative pairs (Base 1 vs Base 2, Base 1 vs Base 3, etc.) will have natural AST Jaccard similarities of **$0.00\% - 15.00\%$**, well below the $70.00\%$ threshold.
- Projected FPR on Dataset v2.0: **$< 1.00\%$**.
