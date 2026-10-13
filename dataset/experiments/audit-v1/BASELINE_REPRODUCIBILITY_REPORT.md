# Baseline Reproducibility Audit Report
## CodeAssess — SWP391 RBL Module 4: AST Plagiarism & Code Analytics Engine

- **Audit Date:** 2026-10-12
- **Audit Tool:** `com.codeassess.audit.AuditRunner`
- **Original Output Location:** `dataset/experiments/baseline-v1/results/`
- **Reproduction Output Location:** `dataset/experiments/audit-v1-run-2/`
- **Result:** **100% Deterministic (Bit-for-Bit Identical)**

---

## 1. Executive Summary

This reproducibility audit examined the execution pipeline, classpath resolution, production code reuse, determinism, and runtime isolation of the CodeAssess baseline experiment runner.

### Key Audit Conclusions:
1. **Production Code Reuse [VERIFIED]:**
   The runner (`com.codeassess.experiment.BaselineExperimentRunner` and `com.codeassess.audit.AuditRunner`) imports the compiled production classes directly from `Back end/target/classes`:
   - `com.codeassess.plagiarism.parser.JavaAstParser`
   - `com.codeassess.plagiarism.normalizer.AstNormalizer`
   - `com.codeassess.plagiarism.tokenizer.KGramGenerator`
   - `com.codeassess.plagiarism.winnowing.FingerprintHasher`
   - `com.codeassess.plagiarism.winnowing.Winnower`
   - `com.codeassess.plagiarism.similarity.JaccardSimilarityCalculator`
   Zero algorithms were cloned, mocked, or independently reimplemented.
2. **Determinism Verification [VERIFIED]:**
   An independent rerun (`audit-v1-run-2`) was executed without touching original outputs. A binary byte comparison (`Arrays.equals`) against the original `baseline_pair_results.csv` confirmed **bit-for-bit identity across all 535 rows (0 differences)**.
3. **Database Isolation [VERIFIED]:**
   The runner operates strictly in memory and on disk. It loads no Spring Data JPA context, executes no Flyway migrations, and connects to no MySQL instance. No production database records were altered.

---

## 2. Execution Environment & Dependencies

| Component | Verified Specification |
| :--- | :--- |
| **Operating System** | Windows 11 Pro 64-bit |
| **Java Development Kit** | OpenJDK 21 LTS (`jdk-21.0.12.1` at `C:\Program Files\Java\jdk-21.0.12.1`) |
| **Java Parser Library** | `com.github.javaparser:javaparser-core:3.25.9` |
| **Build Tool** | Apache Maven 3.9 (via `Back end/mvnw.cmd`) |
| **Locale Setting** | `Locale.US` (enforced programmatically to avoid comma decimal separators) |
| **Dataset Version** | Dataset v1.0 (110 Java files, 535 pairs) |

---

## 3. Production Pipeline Class Mapping

The table below confirms the exact production classes invoked by the experiment runner:

| Pipeline Stage | Production Class | Source Path | Verification Evidence |
| :--- | :--- | :--- | :--- |
| **AST Parsing** | `JavaAstParser` | `Back end/src/main/java/com/codeassess/plagiarism/parser/` | Direct instance instantiation via `new JavaAstParser()` |
| **Normalization** | `AstNormalizer` | `Back end/src/main/java/com/codeassess/plagiarism/normalizer/` | Direct call `normalizer.normalize(cu)` |
| **K-Gram Generation** | `KGramGenerator` | `Back end/src/main/java/com/codeassess/plagiarism/tokenizer/` | Direct call `kGramGenerator.generateKGrams(seq)` with $K=5$ |
| **FNV-1a Hashing** | `FingerprintHasher` | `Back end/src/main/java/com/codeassess/plagiarism/winnowing/` | Direct call `hasher.hash(typeString)` (64-bit) |
| **Winnowing** | `Winnower` | `Back end/src/main/java/com/codeassess/plagiarism/winnowing/` | Direct call `winnower.winnow(hashes)` with $W=4$ |
| **Jaccard Similarity**| `JaccardSimilarityCalculator` | `Back end/src/main/java/com/codeassess/plagiarism/similarity/` | Direct call `calculator.calculate(fpA, fpB)` |

---

## 4. Independent Reproduction Run (`audit-v1-run-2`)

### 4.1 Methodology
To guarantee that the original baseline files were not touched, `AuditRunner.java` performed a full execution into a separate, clean directory: `dataset/experiments/audit-v1-run-2/`.

### 4.2 Comparison Results

| Check | Baseline v1 | Run-2 | Diff Count | Determinism Status |
| :--- | :---: | :---: | :---: | :---: |
| Total Pair Results | 535 rows | 535 rows | 0 | **PASSED** |
| Pair IDs & Ordering | Identical | Identical | 0 | **PASSED** |
| Hashes A & Hashes B | Identical | Identical | 0 | **PASSED** |
| Shared Hashes Count | Identical | Identical | 0 | **PASSED** |
| Similarity Rates | Identical | Identical | 0 | **PASSED** |
| Predicted Flags | Identical | Identical | 0 | **PASSED** |
| Classification Outcomes | Identical | Identical | 0 | **PASSED** |
| Binary File Diff (`byte[]`) | `51,757 bytes` | `51,757 bytes` | **0 bytes** | **BIT-FOR-BIT IDENTICAL** |

---

## 5. Performance & Throughput Benchmarking

Execution timings measured during the audit:

| Stage | Operations | Wall-Clock Time | Average Throughput |
| :--- | :---: | :---: | :---: |
| **AST Parsing & Fingerprinting** | 110 Java files | 382 ms | ~3.47 ms / program |
| **Pairwise Jaccard Evaluation** | 535 comparisons | 16 ms | ~0.030 ms / pair |
| **Full Pipeline Throughput** | 535 pairs total | ~400 ms | > 1,300 pairs / second |

*Note on Timing Variations:* Wall-clock timing fluctuates slightly between JVM invocations due to CPU frequency scaling, thread scheduling, and HotSpot JIT tier compilation. Absolute timing numbers in reports should be treated as hardware-dependent benchmarks, whereas fingerprint outputs and metrics are mathematically invariant.

---

## 6. Reproduction Instructions

To reproduce the audit run independently from the command line:

```powershell
# Navigate to workspace root
cd "d:\AST Plagiarism Detection"

# Set Java 21 environment
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Build classpath
$mavenCp = Get-Content "Back end\classpath.txt"
$fullCp = "Back end\target\classes;$mavenCp;dataset\experiments\audit-v1"

# Compile and run AuditRunner
javac -proc:none -cp $fullCp -d "dataset\experiments\audit-v1" "dataset\experiments\audit-v1\AuditRunner.java"
java -cp $fullCp com.codeassess.audit.AuditRunner
```
