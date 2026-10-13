# Category F1 False Negative Deep-Dive Analysis
## CodeAssess — SWP391 RBL Module 4: AST Plagiarism & Code Analytics Engine

- **Audit Date:** 2026-10-12
- **Audit Tool:** `com.codeassess.audit.AuditRunner` (Java 21 LTS, JavaParser 3.25.9)
- **Data Source:** `dataset/experiments/baseline-v1/results/baseline_pair_results.csv`, `dataset/experiments/audit-v1/fn_pairs.csv`
- **Target Category:** Category F1 (`for -> while` structural transformation)
- **Total F1 Positive Pairs:** 15
- **False Negative Count:** **15**
- **Category F1 Recall:** **0.00%** ($0 / 15$)

---

## 1. Executive Summary

In the baseline experiment, all 15 positive plagiarism pairs belonging to **Category F1 (For-to-While loop rewrite)** failed to reach the production flagging threshold of 70.00%. Every single pair yielded a similarity score of exactly **40.91%**, resulting in 15 False Negatives.

An AST-level trace through `JavaAstParser`, `AstNormalizer`, `KGramGenerator` ($K=5$), `FingerprintHasher` (FNV-1a 64-bit), `Winnower` ($W=4$), and `JaccardSimilarityCalculator` confirms:
1. Transforming `for (...)` into `while (...)` radically rearranges the pre-order traversal sequence of the AST.
2. The loop initialization statement moves outside the loop, the `ForStmt` parent node is replaced by `WhileStmt`, and the increment step moves into the body block.
3. Because K-grams span 5 consecutive nodes, the structural disruption invalidates almost half the K-grams.
4. Winnowing produces 16 fingerprints for the base program and 15 for the variant, with only **9 shared fingerprints** out of a union of **22**.
5. The resulting Jaccard coefficient is mathematically $9 / 22 = \mathbf{40.9091\% \approx 40.91\%}$, well below 70.00%.

---

## 2. Complete Inventory of the 15 F1 False Negative Pairs

| Pair ID | Program A (Base) | Program B (F1 Variant) | Ground Truth | Hashes A | Hashes B | Shared Hashes | Jaccard Sim | Predicted Flag | Outcome |
| :---: | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `pair_0019` | `Base1.java` | `Variant_1_F1_1.java` | `PLAGIARISM` | 16 | 15 | 9 | **40.91%** | `false` | **FN** |
| `pair_0020` | `Base1.java` | `Variant_1_F1_2.java` | `PLAGIARISM` | 16 | 15 | 9 | **40.91%** | `false` | **FN** |
| `pair_0021` | `Base1.java` | `Variant_1_F1_3.java` | `PLAGIARISM` | 16 | 15 | 9 | **40.91%** | `false` | **FN** |
| `pair_0040` | `Base2.java` | `Variant_2_F1_1.java` | `PLAGIARISM` | 16 | 15 | 9 | **40.91%** | `false` | **FN** |
| `pair_0041` | `Base2.java` | `Variant_2_F1_2.java` | `PLAGIARISM` | 16 | 15 | 9 | **40.91%** | `false` | **FN** |
| `pair_0042` | `Base2.java` | `Variant_2_F1_3.java` | `PLAGIARISM` | 16 | 15 | 9 | **40.91%** | `false` | **FN** |
| `pair_0061` | `Base3.java` | `Variant_3_F1_1.java` | `PLAGIARISM` | 16 | 15 | 9 | **40.91%** | `false` | **FN** |
| `pair_0062` | `Base3.java` | `Variant_3_F1_2.java` | `PLAGIARISM` | 16 | 15 | 9 | **40.91%** | `false` | **FN** |
| `pair_0063` | `Base3.java` | `Variant_3_F1_3.java` | `PLAGIARISM` | 16 | 15 | 9 | **40.91%** | `false` | **FN** |
| `pair_0082` | `Base4.java` | `Variant_4_F1_1.java` | `PLAGIARISM` | 16 | 15 | 9 | **40.91%** | `false` | **FN** |
| `pair_0083` | `Base4.java` | `Variant_4_F1_2.java` | `PLAGIARISM` | 16 | 15 | 9 | **40.91%** | `false` | **FN** |
| `pair_0084` | `Base4.java` | `Variant_4_F1_3.java` | `PLAGIARISM` | 16 | 15 | 9 | **40.91%** | `false` | **FN** |
| `pair_0103` | `Base5.java` | `Variant_5_F1_1.java` | `PLAGIARISM` | 16 | 15 | 9 | **40.91%** | `false` | **FN** |
| `pair_0104` | `Base5.java` | `Variant_5_F1_2.java` | `PLAGIARISM` | 16 | 15 | 9 | **40.91%** | `false` | **FN** |
| `pair_0105` | `Base5.java` | `Variant_5_F1_3.java` | `PLAGIARISM` | 16 | 15 | 9 | **40.91%** | `false` | **FN** |

*Finding:* All 15 pairs exhibit identical fingerprint counts ($|\mathcal{F}_A|=16$, $|\mathcal{F}_B|=15$, $|\mathcal{F}_A \cap \mathcal{F}_B|=9$), confirming 100% determinism across all five problem instances and three variant repetitions.

---

## 3. Source Code and AST Transformation Walkthrough

### 3.1 Source Code Diff (`Base1.java` vs `Variant_1_F1_1.java`)

```diff
  public class Base1 {
      public static void main(String[] args) {
          int myCounter = 0;
-         for (int loopVar = 0; loopVar < 10; loopVar++) {
+         int loopVar = 0;
+         while (loopVar < 10) {
              myCounter += loopVar;
+             loopVar++;
          }
          System.out.println("Result: " + myCounter);
      }
  }
```

### 3.2 AST Hierarchy Restructuring

In JavaParser:

#### 1. Base Program (`ForStmt` Hierarchy):
Under `MethodDeclaration.BlockStmt`:
- Statement 1: `ExpressionStmt` (`int myCounter = 0;`)
- Statement 2: `ForStmt`
  - Initialization: `VariableDeclarationExpr` (`int loopVar = 0`)
  - Compare: `BinaryExpr` (`loopVar < 10`)
  - Update: `UnaryExpr` (`loopVar++`)
  - Body: `BlockStmt`
    - Statement: `ExpressionStmt` (`myCounter += loopVar;`)
- Statement 3: `ExpressionStmt` (`System.out.println(...)`)

#### 2. F1 Transformed Program (`WhileStmt` Hierarchy):
Under `MethodDeclaration.BlockStmt`:
- Statement 1: `ExpressionStmt` (`int myCounter = 0;`)
- Statement 2: **`ExpressionStmt` (`int loopVar = 0;`)** $\leftarrow$ Promoted to independent sibling statement
- Statement 3: **`WhileStmt`** $\leftarrow$ Replaces `ForStmt`
  - Condition: `BinaryExpr` (`loopVar < 10`)
  - Body: `BlockStmt`
    - Statement 1: `ExpressionStmt` (`myCounter += loopVar;`)
    - Statement 2: **`ExpressionStmt` (`loopVar++;`)** $\leftarrow$ Nested at end of loop body
- Statement 4: `ExpressionStmt` (`System.out.println(...)`)

### 3.3 Traversal Token Sequence Comparison

The pre-order AST traversal changes as follows:
- **Base1 Node Count:** 52 AST nodes
- **F1 Variant Node Count:** 54 AST nodes

Node comparison in the loop region:

| Index in Base1 | Base1 Node Type | Index in F1 | F1 Variant Node Type | Match? |
| :---: | :--- | :---: | :--- | :---: |
| 20 | `IntegerLiteralExpr[0]` | 20 | `IntegerLiteralExpr[0]` | YES |
| **21** | **`ForStmt`** | **21** | **`ExpressionStmt`** | **NO** |
| 22 | `VariableDeclarationExpr` | 22 | `VariableDeclarationExpr` | YES |
| 23 | `VariableDeclarator` | 23 | `VariableDeclarator` | YES |
| 24 | `PrimitiveType` | 24 | `PrimitiveType` | YES |
| 25 | `SimpleName` | 25 | `SimpleName` | YES |
| 26 | `IntegerLiteralExpr[0]` | 26 | `IntegerLiteralExpr[0]` | YES |
| 27 | `BinaryExpr` | **27** | **`WhileStmt`** | **NO** |
| 28 | `NameExpr` | 28 | `BinaryExpr` | **Shifted** |
| 29 | `SimpleName` | 29 | `NameExpr` | **Shifted** |
| 30 | `IntegerLiteralExpr[10]` | 30 | `SimpleName` | **Shifted** |
| 31 | `UnaryExpr` | 31 | `IntegerLiteralExpr[10]` | **Shifted** |
| 32 | `NameExpr` | 32 | `BlockStmt` | **Shifted** |
| 33 | `SimpleName` | 33 | `ExpressionStmt` | **Shifted** |
| 34 | `BlockStmt` | 34 | `AssignExpr` | **Shifted** |

---

## 4. K-Gram and Fingerprint Disruption Analysis

With $K = 5$, any window of 5 consecutive tokens that intersects a changed or shifted node produces an entirely new hash value.

### 4.1 Fingerprint Accounting

- Total 5-grams in Base1: $52 - 5 + 1 = 48$
- Total 5-grams in F1: $54 - 5 + 1 = 50$
- Winnowed fingerprints in Base1: **16**
- Winnowed fingerprints in F1: **15**
- Shared fingerprints (Intersection): **9**
- Fingerprints unique to Base1: **7**
- Fingerprints unique to F1: **6**
- Total unique fingerprints (Union): $16 + 15 - 9 = \mathbf{22}$

### 4.2 Exact Fingerprint Hashes

#### 1. Retained / Shared Hashes (9 hashes):
These represent the invariant parts of the program (the class definition, method signature, and outer `println` call):
1. `0x9F4DADDFDADFF68F` (`-6967721871510866289`)
2. `0x973C04EA76B40740` (`-7549153470319425728`)
3. `0xB03EDCE5A73FEBCC` (`-5746913195566568500`)
4. `0x0BF4A3D8FA1F0EBB` (`861493581039210171`)
5. `0x9F62D60670F21E7E` (`-6961766750808301954`)
6. `0xB6C4129377004959` (`-5277072438805378727`)
7. `0x925783E036F2D57D` (`-7901702022180448899`)
8. `0x87A4AC677312F64D` (`-8672617422096501171`)
9. `0xACD1115A28DD3148` (`-5993990550123105976`)

#### 2. Lost Hashes (Present in Base1 only, 7 hashes):
Disrupted by the elimination of `ForStmt` and repositioning of `UnaryExpr`:
1. `0xA8FBD0CFE7046EB5` (`-6270188464830648651`)
2. `0xCB6D56E3D154EEBD` (`-3788276175079149891`)
3. `0x9E08B6B98CECE177` (`-7059191507853713033`)
4. `0xA56E62D0B38B7543` (`-6526170161508289213`)
5. `0xC5CA8D9BEE17DB31` (`-4194384402088207567`)
6. `0xDB1BEF83F2033987` (`-2658267805052094073`)
7. `0x97E3D5B09B0683A8` (`-7501917599777258584`)

#### 3. New Hashes (Present in F1 only, 6 hashes):
Created by the introduction of `WhileStmt` and the additional `UnaryExpr` inside `BlockStmt`:
1. `0xA427C38DED8364A9` (`-6618106113055038295`)
2. `0x85964FD2F52D9098` (`-8820775052683407208`)
3. `0x9B2CAF7FD8B47391` (`-7265239135224040559`)
4. `0x954E90912FA768CF` (`-7688048560630699825`)
5. `0xBD16E620A300D6CA` (`-4821413323205454134`)
6. `0x8C86373DCAA42882` (`-8320902522988124030`)

### 4.3 Jaccard Similarity Calculation

$$J(\mathcal{F}_{Base1}, \mathcal{F}_{F1}) = \frac{|\mathcal{F}_{Base1} \cap \mathcal{F}_{F1}|}{|\mathcal{F}_{Base1} \cup \mathcal{F}_{F1}|} = \frac{9}{16 + 15 - 9} = \frac{9}{22} = 0.4090909...$$

Formatted to two decimal places: **`40.91%`**.

Because $40.91\% < 70.00\%$, the production engine classifies the pair as `isFlagged = false`, generating a **False Negative**.

---

## 5. Potential Improvement Strategies (Post-Milestone 2)

Per constraints, **no production code is modified in this task**. However, the audit identifies three concrete, evidence-based architectural improvements for subsequent milestones:

### Option 1: AST Loop Canonicalization (Desugaring)
In `AstNormalizer`, normalize loop structures before generating the node sequence:
- Canonicalize `ForStmt` into an equivalent sequence: an initialization statement followed by a canonical `WhileStmt` containing the loop increment at the end of its body.
- *Expected Impact:* F1 variants would produce 100% matching normalized sequences with their base programs, boosting F1 similarity from 40.91% to 100.00%, eliminating all 15 False Negatives.

### Option 2: Hybrid Tree Edit Distance (TED / Zhang-Shasha)
For pairs scoring in the intermediate zone (35%–69%), trigger a secondary AST Tree Edit Distance comparator:
- While linear K-gram tokenization suffers from node order shifts, TED operates directly on tree hierarchies and penalizes small structural permutations lightly.
- *Expected Impact:* Tree edit similarity between `ForStmt` and `WhileStmt` trees is typically $\ge 85\%$.

### Option 3: Variable K-Gram Windowing ($K = 3$ or $K = 4$)
A smaller K-gram size reduces sensitivity to local statement boundaries:
- With $K = 3$, fewer grams span across the `for`/`while` demarcation.
- *Caveat:* Smaller $K$ decreases specificity and increases accidental collisions on larger codebases.
