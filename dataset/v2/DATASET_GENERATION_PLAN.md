# Dataset v2.0 Generation Plan & Reproducibility Guide
## CodeAssess — SWP391 RBL Module 4: AST Plagiarism & Code Analytics Engine

- **Document Version:** 2.0.0
- **Location:** `dataset/v2/`
- **Lead Tool:** `com.codeassess.v2.DatasetV2Generator`

---

## 1. Generation Pipeline Architecture

Dataset v2.0 is generated through a fully deterministic, self-contained pipeline:

```
[5 Base Java Programs] 
         │
         ├───> Preliminary Pairwise Similarity Audit (BaseProgramComparator)
         │     (Validates negative separation before generating variants)
         │
         ▼
[DatasetV2Generator.java]
         │
         ├───> Synthesize 105 Controlled Variants (7 Categories × 3 Variants × 5 Problems)
         │     (Uses regex word boundaries, safe desugaring, and compilable Java)
         │
         ├───> Generate Cryptographic manifest.csv (110 programs with SHA-256 digests)
         │
         └───> Generate Ground-Truth pairs.csv (535 evaluation pairs with provenance metadata)
         │
         ▼
[Automated Compilation Check (javac)]
         │
         └───> Confirms 100% of base and generated Java programs compile cleanly
```

---

## 2. Detailed Transformation Engineering

To prevent syntax corruption (such as partial word replacement observed in early regex passes), all transformations in `DatasetV2Generator.java` adhere to strict rules:

### 2.1 Category B (Identifier Renaming)
Uses `Pattern.quote` and word boundaries `\b`:
```java
private String safeReplaceWord(String text, String word, String replacement) {
    return text.replaceAll("\\b" + Pattern.quote(word) + "\\b", replacement);
}
```
- Variable names, loop indices, and method parameters are renamed without altering reserved keywords (`public`, `class`, `void`, `int`, etc.).

### 2.2 Category C (Comments & Formatting)
- Variant 1: Injects single-line explanatory comments.
- Variant 2: Injects multi-line block comments describing algorithm complexity.
- Variant 3: Replaces 4-space indentation with hard tabs (`\t`).

### 2.3 Category D (Literal Modification)
- Modifies integer literals and string constants in `main` without changing core loop boundary invariants.

### 2.4 Category E1 (Dead Code Insertion)
- Injects benign unreachable branches (`if (false) { int unreached = 0; }`) at the method header.

### 2.5 Category E3 (Block Restructuring)
- Wraps standalone invocation statements inside nested `{ ... }` blocks to introduce `BlockStmt` nodes into the AST.

### 2.6 Category F1 (Control-Flow Transformation)
- For `BubbleSort` and `MatrixMultiply`: Transforms outer `for` loop to equivalent canonical `while` loop with loop counter increment at the end of the body.
- For `BinarySearch`, `SinglyLinkedList`, and `PalindromeChecker`: Transforms `while` loop to equivalent `for` loop (`for (; condition; )`).

---

## 3. Exact Reproduction Commands

The entire dataset can be regenerated and verified in seconds using the command line:

```powershell
# Navigate to workspace root
cd "d:\AST Plagiarism Detection"

# Set JDK 21 environment
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Step 1: Compile base programs
javac "dataset\v2\programs\base\*.java"

# Step 2: Compile and run BaseProgramComparator
$mavenCp = Get-Content "Back end\classpath.txt"
$fullCp = "Back end\target\classes;$mavenCp;dataset\v2"
javac -proc:none -cp $fullCp -d "dataset\v2" "dataset\v2\BaseProgramComparator.java"
java -cp $fullCp com.codeassess.v2.BaseProgramComparator

# Step 3: Compile and run DatasetV2Generator
javac -proc:none -d "dataset\v2" "dataset\v2\DatasetV2Generator.java"
java -cp "dataset\v2" com.codeassess.v2.DatasetV2Generator

# Step 4: Verify 100% compilation of all generated variants
javac "dataset\v2\programs\generated\*.java"
```

---

## 4. Quality Gates & Validation Checklist

| Quality Gate | Acceptance Criteria | Verified Result | Status |
| :--- | :--- | :--- | :---: |
| **Base Compilation** | All 5 base programs compile with zero errors | 5 / 5 compiled | **PASSED** |
| **Negative Separation** | All 10 base-vs-base pairs score $< 30\%$ | Max score: **21.43%** | **PASSED** |
| **Variant Count** | Exactly 105 generated files | 105 files created | **PASSED** |
| **Variant Compilation** | All 105 generated programs compile with zero errors | 105 / 105 compiled | **PASSED** |
| **Manifest Integrity** | 110 unique programs with valid SHA-256 hashes | 110 rows in manifest.csv | **PASSED** |
| **Pairs Schema** | Exactly 535 valid pairs with 0 missing file references | 535 rows in pairs.csv | **PASSED** |
| **Immutability Check** | Zero changes made to `dataset/programs/` or `baseline-v1/` | Git diff confirmed clean | **PASSED** |
