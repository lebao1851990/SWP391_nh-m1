# Dataset v2.0 Ground-Truth & Semantic Validity Audit
## CodeAssess — SWP391 RBL Module 4: AST Plagiarism & Code Analytics Engine

- **Audit Date:** 2026-10-12
- **Audit Directory:** `dataset/experiments/dataset-v2-audit/`
- **Audit Tool:** `com.codeassess.audit.v2.DatasetV2Auditor`
- **Scope:** Complete semantic analysis of transformation categories A, B, C, D, E1, E3, F1, and G
- **Status:** Independently Verified

---

## 1. Executive Summary

This audit examined the generative provenance, semantic equivalence, behavioral impact, and labeling validity of all transformation categories in **Dataset v2.0**.

### Key Conclusions:
1. **Decoupling from Detector Predictions [VERIFIED]:**
   Zero ground-truth labels in Dataset v2.0 rely on detector similarity scores. All 105 positive pairs are labeled `PLAGIARISM` based solely on verified generative lineage from the base program. All 430 negative pairs are labeled `NON_PLAGIARISM` based on independent algorithmic provenance.
2. **Behavioral Impact of Category D [VERIFIED]:**
   Category D (`MODIFIED_LITERALS`) changes test constants and input data (e.g., target numbers, initial array elements). While the core computational logic (e.g., sorting loop, binary search branching) is preserved, the runtime input/output behavior for that specific execution run differs. In educational plagiarism benchmarks, this simulates student literal obfuscation (adapting a classmate's solution to different test numbers).
3. **Semantic Equivalence of Categories A, B, C, E1, E3, F1 [VERIFIED]:**
   Transformations in Categories A, B, C, E1, E3, and F1 preserve full input-output algorithmic equivalence with the base programs.

---

## 2. Category-by-Category Semantic Audit

The table below summarizes the semantic characteristics, reproducibility, and labeling validity across all seven transformation categories and the negative baseline group:

| Cat | Transformation Name | Reproducibility | Program Behavior Impact | Semantic Classification | Ground Truth Label | Labeling Basis |
| :---: | :--- | :---: | :--- | :--- | :---: | :--- |
| **A** | Exact Copy | Deterministic | Strictly Preserved | `EQUIVALENT_COPY` | `PLAGIARISM` | Verbatim provenance (class rename only) |
| **B** | Identifier Renaming | Deterministic | Strictly Preserved | `EQUIVALENT_RENAMED` | `PLAGIARISM` | Systematic variable/parameter substitution |
| **C** | Comments & Whitespace | Deterministic | Strictly Preserved | `EQUIVALENT_FORMATTED` | `PLAGIARISM` | Formatting and comment injection |
| **D** | Literal Modifications | Deterministic | **Modified Test Inputs** | `MODIFIED_LITERALS` | `PLAGIARISM` | Direct provenance with changed constants |
| **E1** | Dead Code Insertion | Deterministic | Strictly Preserved | `EQUIVALENT_DEAD_CODE` | `PLAGIARISM` | Unreachable branch insertion (`if (false)`) |
| **E3** | Block Restructuring | Deterministic | Strictly Preserved | `EQUIVALENT_RESTRUCTURED` | `PLAGIARISM` | Scope-wrapped statement blocks (`{ ... }`) |
| **F1** | Control-Flow Rewrite | Deterministic | Strictly Preserved | `EQUIVALENT_CFG` | `PLAGIARISM` | Equivalent loop rewrite (`for` $\leftrightarrow$ `while`) |
| **G** | Unrelated Algorithms | Deterministic | Fundamentally Different | `INDEPENDENT_ALGORITHM` | `NON_PLAGIARISM` | Distinct computational problem domains |

---

## 3. Deep Dive: Category D (`MODIFIED_LITERALS`)

A critical scientific audit requirement is to verify whether literal changes alter program behavior:

### 3.1 Concrete Code Excerpts by Problem

#### 1. `BinarySearch` (`BinarySearch_D_1.java` vs `BinarySearch.java`)
```diff
  public static void main(String[] args) {
      int[] data = {2, 5, 8, 12, 16, 23, 38, 56, 72, 91};
-     int result = search(data, 23);
+     int result = search(data, 56);
      System.out.println("Found at index: " + result);
  }
```
- **Semantic Effect:** Searches for element `56` instead of `23`. The method returns index `7` instead of `5`.
- **Algorithmic Logic:** The `search()` function is identical character-for-character.

#### 2. `BubbleSort` (`BubbleSort_D_1.java` vs `BubbleSort.java`)
```diff
  public static void main(String[] args) {
-     int[] data = {64, 34, 25, 12, 22, 11, 90};
+     int[] data = {99, 88, 77, 66, 55, 44, 33};
      sort(data);
      System.out.println("Sorted first element: " + data[0]);
  }
```
- **Semantic Effect:** Sorts an array with different values. Output changes from `11` to `33`.
- **Algorithmic Logic:** The nested-loop sorting routine is identical.

#### 3. `MatrixMultiply` (`MatrixMultiply_D_1.java` vs `MatrixMultiply.java`)
```diff
  public static void main(String[] args) {
-     int[][] m1 = {{1, 2}, {3, 4}};
+     int[][] m1 = {{2, 3}, {4, 5}};
      int[][] m2 = {{5, 6}, {7, 8}};
      int[][] res = multiply(m1, m2);
```
- **Semantic Effect:** Multiplies a different initial matrix; numerical product output changes.
- **Algorithmic Logic:** The triple-nested loop multiplication routine is identical.

#### 4. `SinglyLinkedList` (`SinglyLinkedList_D_1.java` vs `SinglyLinkedList.java`)
```diff
  public static void main(String[] args) {
-     Node first = new Node(10);
-     first.next = new Node(20);
-     first.next.next = new Node(30);
+     Node first = new Node(110);
+     first.next = new Node(220);
+     first.next.next = new Node(330);
      int total = countNodes(first);
```
- **Semantic Effect:** Node payloads change; however, list length returned is still `3`.
- **Algorithmic Logic:** Node class and pointer traversal loop are identical.

#### 5. `PalindromeChecker` (`PalindromeChecker_D_1.java` vs `PalindromeChecker.java`)
```diff
  public static void main(String[] args) {
-     String testWord = "racecar";
+     String testWord = "radar";
      boolean valid = isPalindrome(testWord);
```
- **Semantic Effect:** Tests a different valid palindrome (`"radar"` vs `"racecar"`). Both return `true`.
- **Algorithmic Logic:** Two-pointer palindrome validation loop is identical.

### 3.2 Evaluation Label Justification
In academic benchmarks, Category D variants are correctly labeled **`PLAGIARISM`** because:
1. The entire algorithmic procedure (functions `search()`, `sort()`, `multiply()`, `countNodes()`, `isPalindrome()`) was directly cloned from the base program.
2. In university programming exams, copying an entire method and merely altering literal values in test calls or default parameters is textbook Type-2/Type-3 plagiarism.
3. The dataset explicitly records this distinction via the `semantic_relationship` field as `MODIFIED_LITERALS` rather than pretending it is strict bytecode-equivalent code.

---

## 4. Audit of Negative Pairs (Category G)

Category G represents 430 negative pairs labeled `NON_PLAGIARISM`:
- **10 Base-vs-Base pairs:** Compare five completely distinct computer science problems:
  - Binary Search (search algorithm) vs Bubble Sort (sorting algorithm)
  - Binary Search vs Matrix Multiplication (numeric linear algebra)
  - Binary Search vs Singly Linked List (data structure traversal)
  - Binary Search vs Palindrome Checker (string processing)
  - etc.
- **420 Base-vs-Unrelated-Variant pairs:** Compare a base problem against variants generated from a completely different problem.

### Scientific Validity of Negative Pairs:
Unlike Dataset v1.0 where negative pairs were identical loop counters, in Dataset v2.0 every negative pair compares **genuinely independent algorithms solving fundamentally different computational tasks**. The label `NON_PLAGIARISM` is scientifically valid and reflects true algorithmic independence.
