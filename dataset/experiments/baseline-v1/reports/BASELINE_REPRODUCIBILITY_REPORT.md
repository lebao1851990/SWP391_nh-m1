# Baseline Reproducibility & Environment Audit Report
**Project:** CodeAssess | Module 4: AST Plagiarism & Code Analytics Engine  
**Course & Milestone:** SWP391 RBL — Milestone 2  
**Date:** 2026-10-12  
**Author / Auditor:** Antigravity AI Assistant  
**Subject:** Execution Reproducibility, Determinism, Code Coverage, and Sandbox Audit  
**Status:** AUDIT COMPLETE (100% Deterministic Execution; Gaps Documented)

---

## 1. Executive Summary

This report documents the verification of experimental reproducibility for Module 4's baseline experiment on Dataset v1.0, along with a formal audit of testing coverage and runtime execution sandboxing.

### Key Conclusions:
1. **100% Deterministic Results:** Re-running the baseline experiment produced bit-for-bit identical results across all 535 pairs. `git diff` on the results directory yielded zero discrepancies.
2. **Production Pipeline Integrity:** The experiment directly executes the compiled `.class` files of the deployed production package `com.codeassess.plagiarism.*`. No duplicate or surrogate algorithms were introduced.
3. **Test Coverage Status:** Code coverage tooling (JaCoCo) is **NOT CONFIGURED** in `Back end/pom.xml`. Test suite contains 25 passing unit tests (0 failures), but automated line/branch coverage percentage cannot be measured without the plugin.
4. **Docker Sandbox Status:** Container-based runtime isolation is **NOT IMPLEMENTED / NOT VERIFIED** in this repository.

---

## 2. Experimental Reproducibility Verification

### 2.1. Execution Runs Comparison

| Parameter | Initial Baseline Run | Re-execution Verification Run | Status / Delta |
| :--- | :---: | :---: | :---: |
| **Execution Timestamp** | 2026-10-12 10:14:28 | 2026-10-12 10:22:11 | Valid |
| **JDK Version** | Oracle JDK 21.0.12.1 | Oracle JDK 21.0.12.1 | Identical |
| **Operating System** | Windows 11 (build 10.0) | Windows 11 (build 10.0) | Identical |
| **Input Dataset** | `dataset/ground-truth/pairs.csv` | `dataset/ground-truth/pairs.csv` | Unmodified |
| **Total Evaluated Pairs** | 535 pairs | 535 pairs | Identical (0 missed) |
| **True Positives (TP)** | 90 | 90 | $\Delta = 0$ |
| **True Negatives (TN)** | 60 | 60 | $\Delta = 0$ |
| **False Positives (FP)** | 370 | 370 | $\Delta = 0$ |
| **False Negatives (FN)** | 15 | 15 | $\Delta = 0$ |
| **Overall Accuracy** | 28.04% | 28.04% | $\Delta = 0.00\%$ |
| **Overall Recall** | 85.71% | 85.71% | $\Delta = 0.00\%$ |
| **Overall Precision** | 19.57% | 19.57% | $\Delta = 0.00\%$ |
| **Git Diff Output** | N/A | `0 files changed, 0 insertions, 0 deletions` | **100% Deterministic** |

### 2.2. Reproduction Procedure
The experiment can be reproduced at any time from the repository root via:

```bat
dataset\experiments\baseline-v1\run_baseline.bat
```
*(Or via PowerShell: `powershell -ExecutionPolicy Bypass -File dataset\experiments\baseline-v1\run_baseline.ps1`)*

---

## 3. Production Architecture Verification

The experiment runner [BaselineExperimentRunner.java](file:///d:/AST%20Plagiarism%20Detection/dataset/experiments/baseline-v1/BaselineExperimentRunner.java) imports and executes the exact production classes:

- `com.codeassess.plagiarism.parser.JavaAstParser`
- `com.codeassess.plagiarism.normalizer.AstNormalizer`
- `com.codeassess.plagiarism.tokenizer.KGramGenerator` (K = 5)
- `com.codeassess.plagiarism.winnowing.FingerprintHasher` (FNV-1a 64-bit)
- `com.codeassess.plagiarism.winnowing.Winnower` (Window = 4, rightmost tie-break)
- `com.codeassess.plagiarism.similarity.JaccardSimilarityCalculator` (Threshold = 70.00%)

### Differences Between Runner and Deployed Backend:
| Dimension | Deployed Spring Boot API | Baseline Experiment Runner | Impact on Math/Results |
| :--- | :--- | :--- | :--- |
| **Trigger Mechanism** | Spring `@Async` Event on `POST /api/student/submissions` | Direct in-memory batch iterator | **None** (identical AST math) |
| **Persistence** | MySQL JPA repository (`ast_fingerprints`, `similarity_matrix`) | In-memory maps $\rightarrow$ CSV export | **None** (avoids database pollution) |
| **Transactions** | Spring `@Transactional` | Standalone Java process | **None** |
| **Input Format** | Database column `source_code LONGTEXT` | Local `.java` UTF-8 file contents | **None** |

---

## 4. Test Coverage Audit (Phase 5)

An audit of [Back end/pom.xml](file:///d:/AST%20Plagiarism%20Detection/Back%20end/pom.xml) confirmed:
- **Build Plugins Present:**
  - `spring-boot-maven-plugin` (version 3.3.4)
  - `maven-surefire-plugin` (default Maven provider)
- **Coverage Plugins Present:** **None**. JaCoCo (`jacoco-maven-plugin`) is **not configured** in `pom.xml`.
- **Existing Test Execution:** Running `mvnw.cmd test -Dtest=*Plagiarism*Test` executed **25 tests with 0 failures** across:
  - `AstNormalizerTest` (4 tests)
  - `JavaAstParserTest` (4 tests)
  - `AstPlagiarismServiceTest` (1 test)
  - `JaccardSimilarityCalculatorTest` (6 tests)
  - `KGramGeneratorTest` (3 tests)
  - `FingerprintHasherTest` (3 tests)
  - `WinnowerTest` (4 tests)
- **Audit Conclusion:** Because JaCoCo is not configured, exact line and branch coverage percentages **cannot be honestly reported**. In accordance with RBL integrity guidelines, coverage percentage is marked as **NOT CONFIGURED** rather than inferred.

---

## 5. Docker Sandbox & Security Isolation Audit (Phase 5)

An audit of the entire repository for containerization, runtime sandboxing, and resource limits revealed:
1. **Container Files:** No `Dockerfile`, `docker-compose.yml`, or container manifests exist for code compilation or execution.
2. **Execution Boundary:** Code submission and plagiarism detection operate strictly as **static syntax analysis (AST parsing)** on the server thread pool. Untrusted Java programs are **parsed as text data**, never compiled or executed natively.
3. **Resource Controls:** No Linux cgroups, memory caps, CPU time limits, or network namespaces are configured in the backend.
4. **Audit Conclusion:** Marked as **NOT IMPLEMENTED / NOT VERIFIED**. This static analyzer does not execute code; if future requirements mandate dynamic code running, a containerized sandbox must be built in a subsequent milestone.
