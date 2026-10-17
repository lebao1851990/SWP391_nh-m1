# CODEASSESS — GRADE AUDIT TRAIL FINAL VERIFICATION REPORT

**Verification Type:** READ-ONLY SYSTEM & CODE AUDIT  
**Date:** 2026-10-15  
**Reviewer:** Antigravity Engineering  
**Scope:** Action classification, Data integrity, Flyway migrations, Security/Transaction boundaries  

---

## 1. Action Type Classification Verification

### 1.1. Code Logic Inspection (`TeacherScoringService.java:149-160`)

```java
String actionType;
if (isInitialGrading) {
    actionType = "INITIAL_GRADING";
} else if (preOverrideScore == null && postOverrideScore != null) {
    actionType = "OVERRIDE_SET";
} else if (preOverrideScore != null && postOverrideScore == null) {
    actionType = "OVERRIDE_CLEARED";
} else if (preOverrideScore != null && postOverrideScore != null && preOverrideScore.compareTo(postOverrideScore) != 0) {
    actionType = "OVERRIDE_UPDATE";
} else {
    actionType = "RUBRIC_UPDATE";
}
```

### 1.2. Evaluation by Action Type

| Action Type | Condition / Transition | Expected Pre/Post Values | Verification Status | Evidence Type |
|---|---|---|:---:|---|
| **`INITIAL_GRADING`** | `isInitialGrading == true`<br>(`evaluatedAt == null` or status is `PENDING`) | `preRubricScore`: `null`<br>`postRubricScore`: total rubric points<br>`preOverrideScore`: `null`<br>`postOverrideScore`: null or override<br>`preEffectiveScore`: `null`<br>`postEffectiveScore`: effective score | **PASS** | Automated Unit Test:<br>`TeacherScoringServiceTest.testInitialGradingCreatesAuditLog` |
| **`OVERRIDE_SET`** | `isInitialGrading == false` &&<br>`preOverrideScore == null` &&<br>`postOverrideScore != null` | `preRubricScore`: existing rubric<br>`postRubricScore`: updated rubric<br>`preOverrideScore`: `null`<br>`postOverrideScore`: new override<br>`preEffectiveScore`: `preRubricScore`<br>`postEffectiveScore`: `postOverrideScore` | **PASS** | Automated Unit Test:<br>`TeacherScoringServiceTest.testOverrideSetCreatesAuditLog` |
| **`OVERRIDE_CLEARED`** | `isInitialGrading == false` &&<br>`preOverrideScore != null` &&<br>`postOverrideScore == null` | `preOverrideScore`: non-null<br>`postOverrideScore`: `null`<br>`preEffectiveScore`: `preOverrideScore`<br>`postEffectiveScore`: `postRubricScore` (reverts to rubric) | **PASS** | Automated Unit Test:<br>`TeacherScoringServiceTest.testOverrideClearedReturnsToRubricScoreAndLogs` |
| **`OVERRIDE_UPDATE`** | `isInitialGrading == false` &&<br>`preOverrideScore != null` &&<br>`postOverrideScore != null` &&<br>`preOverrideScore != postOverrideScore` | `preOverrideScore`: old override<br>`postOverrideScore`: new override<br>`preEffectiveScore`: old override<br>`postEffectiveScore`: new override | **PASS** | Automated Unit Test:<br>`TeacherScoringServiceTest.testOverrideUpdateCreatesAuditLog` |
| **`RUBRIC_UPDATE`** | `isInitialGrading == false` &&<br>Override unchanged (both `null` or both equal) | `preRubricScore`: old rubric<br>`postRubricScore`: new rubric<br>Override values remain identical | **PASS** | Automated Unit Test:<br>`TeacherScoringServiceTest.testRubricUpdateCreatesAuditLog` |

### 1.3. Specific Edge Cases & Findings

> [!NOTE]
> **Check: Setting Override from null to a new value**  
> When `isInitialGrading` is false, and `preOverrideScore == null` while `postOverrideScore != null`:  
> The condition `else if (preOverrideScore == null && postOverrideScore != null)` strictly intercepts this state before reaching `else`. Therefore, setting an Override on an already graded submission will **never** be classified as `RUBRIC_UPDATE`. It is guaranteed to be `OVERRIDE_SET`.

> [!WARNING]
> **Finding 1 (Edge Case — No-op / Idempotent Commit):**  
> When a submission is already graded, has an existing override (e.g. 95.00), and a teacher submits the form again without changing neither rubric nor override (`preRubricScore == postRubricScore` AND `preOverrideScore == postOverrideScore`):  
> The `else` branch classifies this transaction as `RUBRIC_UPDATE`, even though no numeric values changed.  
> **Impact:** Audit log records `action_type = RUBRIC_UPDATE` for no-op commits.  
> **Proposed Refinement (Awaiting PO Approval):** Add a condition checking if rubric scores actually changed, or assign `NO_CHANGE` / suppress redundant audit log if delta is zero.

---

## 2. Actual Audit Data Verification

### 2.1. Live Database Inspection

Query executed against `plagiarism_system.grade_audit_logs`:
```sql
SELECT id, submission_id, actor_id, action_type,
       pre_rubric_score, post_rubric_score,
       pre_override_score, post_override_score,
       pre_effective_score, post_effective_score,
       rationale, created_at
FROM grade_audit_logs;
```

**Actual Database Record Found:**
```
id:                   a393cb11-e08b-4dd1-b807-78b63350fbbf
submission_id:        007cf9ae-4385-46dc-b492-ba891af6a535
actor_id:             706fb15a-1b16-4aac-8841-623e54d798c8
action_type:          RUBRIC_UPDATE
pre_rubric_score:     0.00
post_rubric_score:    0.00
pre_override_score:   95.00
post_override_score:  95.00
pre_effective_score:  95.00
post_effective_score: 95.00
rationale:            ""
created_at:           2026-10-15 20:16:42
```

### 2.2. Verification Checklist

| Requirement | Verified Value / Behavior | Status | Evidence |
|---|---|:---:|---|
| `pre_effective_score` calculation | `95.00` correctly derived from `pre_override_score` | **PASS** | Real DB record & unit test |
| `post_effective_score` calculation | `95.00` correctly derived from `post_override_score` | **PASS** | Real DB record & unit test |
| `post_effective_score` on Override Clear | Reverts strictly to `post_rubric_score` | **PASS** | Verified in `TeacherScoringServiceTest.testOverrideClearedReturnsToRubricScoreAndLogs` |
| `actor_id` authentication match | `706fb15a-1b16-4aac-8841-623e54d798c8` strictly matches authenticated `teachera@fpt.edu.vn` | **PASS** | Real DB record matched to `users` table |
| `rationale` storage completeness | Storage only captures `overrideRationale`; `instructorEvaluationComments` is omitted | **FAIL** | See Finding 2 below |
| `action_type` reflects change | Accurately distinguishes states; defaults to `RUBRIC_UPDATE` when override unchanged | **PASS** | Code logic & unit tests |

### 2.3. Critical Finding on Rationale Storage

> [!CAUTION]
> **Finding 2 (Defect in Rationale Consolidation):**  
> - **File:** [`Back end/src/main/java/com/codeassess/scoring/service/TeacherScoringService.java`](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/scoring/service/TeacherScoringService.java#L182)  
> - **Current Line:** `.rationale(payload.getOverrideRationale())`  
> - **Problem:** `SaveScoringPayload` contains both `overrideRationale` and `instructorEvaluationComments`. When a teacher provides only general evaluation comments (e.g., during `INITIAL_GRADING` or `RUBRIC_UPDATE` without setting an override), `payload.getOverrideRationale()` is null/empty. As a consequence, `instructorEvaluationComments` is **silently ignored and not saved to the audit log**.  
> - **Impact:** Audit logs for rubric-only scoring updates will have an empty `rationale`, losing the teacher's evaluation remarks.  
> - **Proposed Resolution (Read-Only phase: awaiting user approval before applying):**  
>   Consolidate both fields into `rationale` using a clean helper:
>   ```java
>   String consolidatedRationale = Stream.of(
>           payload.getOverrideRationale(),
>           payload.getInstructorEvaluationComments()
>   ).filter(s -> s != null && !s.isBlank())
>    .collect(Collectors.joining(" | "));
>   ```

---

## 3. Flyway Migration & Schema Verification

### 3.1. Migration Status on Database

Query executed against `plagiarism_system_test.flyway_schema_history`:
```sql
SELECT installed_rank, version, description, type, script, checksum, installed_on, success 
FROM flyway_schema_history ORDER BY installed_rank;
```

**Output:**
```
Rank 1: V1   - initial schema                          (success = 1, checksum: -1427622716)
Rank 2: V1.1 - extend users schema                     (success = 1, checksum: -1755917060)
Rank 3: V2   - create classes and extend exams         (success = 1, checksum:  970641341)
Rank 4: V3   - extend submissions schema               (success = 1, checksum: -1662375168)
Rank 5: V4   - extend appeals schema                   (success = 1, checksum: -223741892)
Rank 6: V5   - create scoring and plagiarism evidence  (success = 1, checksum: -1737628979)
Rank 7: V6   - create grade audit logs                 (success = 1, checksum:  1902036047)
```
- **V6 applied successfully**: `success = 1`, execution time = 54ms.
- **V1–V5 untouched**: Checksums and install timestamps are identical to baseline.

### 3.2. Clean Database Migration Test

Execution: `.\mvnw.cmd test "-Dtest=CleanDatabaseMigrationTest"`
- **Result:** **PASS (1/1)**
- Clean database `plagiarism_system_clean_test` created from scratch.
- Applied all 7 migrations (`V1` through `V6`). Target schema version reached: **6**.
- Verified 12 core tables (including `grade_audit_logs`) and 17 foreign key constraints.
- Bootstrapped Hibernate `SchemaValidator` against all 12 JPA entity classes (including `GradeAuditLog.java`).
- Hibernate validation result: **PASSED with ZERO schema errors**.

### 3.3. Entity-Schema Mapping Alignment

| Column in MySQL (`grade_audit_logs`) | JPA Mapping in `GradeAuditLog.java` | Match |
|---|---|:---:|
| `id VARCHAR(36) PRIMARY KEY` | `@Id @GeneratedValue(strategy = GenerationType.UUID) private String id;` | MATCH |
| `submission_id VARCHAR(36) NOT NULL FK` | `@ManyToOne @JoinColumn(name = "submission_id", nullable = false) private Submission submission;` | MATCH |
| `actor_id VARCHAR(36) NOT NULL FK` | `@ManyToOne @JoinColumn(name = "actor_id", nullable = false) private User actor;` | MATCH |
| `action_type VARCHAR(50) NOT NULL` | `@Column(name = "action_type", nullable = false, length = 50) private String actionType;` | MATCH |
| `pre_rubric_score DECIMAL(5,2)` | `@Column(name = "pre_rubric_score", precision = 5, scale = 2) private BigDecimal preRubricScore;` | MATCH |
| `post_rubric_score DECIMAL(5,2)` | `@Column(name = "post_rubric_score", precision = 5, scale = 2) private BigDecimal postRubricScore;` | MATCH |
| `pre_override_score DECIMAL(5,2)` | `@Column(name = "pre_override_score", precision = 5, scale = 2) private BigDecimal preOverrideScore;` | MATCH |
| `post_override_score DECIMAL(5,2)` | `@Column(name = "post_override_score", precision = 5, scale = 2) private BigDecimal postOverrideScore;` | MATCH |
| `pre_effective_score DECIMAL(5,2)` | `@Column(name = "pre_effective_score", precision = 5, scale = 2) private BigDecimal preEffectiveScore;` | MATCH |
| `post_effective_score DECIMAL(5,2)` | `@Column(name = "post_effective_score", precision = 5, scale = 2) private BigDecimal postEffectiveScore;` | MATCH |
| `rationale TEXT` | `@Column(columnDefinition = "TEXT") private String rationale;` | MATCH |
| `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP` | `@CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;` | MATCH |

---

## 4. Security & Transaction Boundary Verification

### 4.1. Security Checks

| Security Boundary | Verification Method | Result | Evidence |
|---|---|:---:|---|
| **Student Access Blocked** | `GET /api/teacher/submissions/{id}/scoring/audit-logs` using student session | **PASS** | HTTP `403 Forbidden` returned on live server & `ScoringControllerTest.studentCannotAccessTeacherScoring` |
| **Cross-Class Isolation** | Teacher requests audit log for submission belonging to another course class | **PASS** | Throws `ResourceNotFoundException` (`404 Not Found / Access Denied`) via `TeacherScoringServiceTest.testOtherClassTeacherCannotAccessScoringOrAuditLogs` |
| **Actor Identity Forgery Prevention** | Client sends scoring payload without user identity; actor derived strictly from Spring Security `@AuthenticationPrincipal User currentUser` | **PASS** | `TeacherScoringServiceTest.testAuditLogActorMatchesAuthenticatedTeacherNotForged` asserts saved actor ID equals authenticated teacher ID |

### 4.2. Transaction & Rollback Checks

| Rollback Trigger | Expected Behavior | Result | Evidence |
|---|---|:---:|---|
| **Negative Criterion Score** | Throws `IllegalArgumentException`; zero audit logs saved; no score change | **PASS** | `TeacherScoringServiceTest.testNegativeScoreRejected`: `verify(gradeAuditLogRepository, never()).save(any())` |
| **Score Exceeds Criterion Max** | Throws `IllegalArgumentException`; zero audit logs saved | **PASS** | `TeacherScoringServiceTest.testScoreGreaterThanMaxRejected`: `verify(gradeAuditLogRepository, never()).save(any())` |
| **Negative Override Score** | Throws `IllegalArgumentException`; zero audit logs saved | **PASS** | `TeacherScoringServiceTest.testNegativeOverrideScoreRejected`: `verify(gradeAuditLogRepository, never()).save(any())` |
| **Override Exceeds Assessment Max** | Throws `IllegalArgumentException`; zero audit logs saved | **PASS** | `TeacherScoringServiceTest.testOverrideScoreExceedingScaleRejected`: `verify(gradeAuditLogRepository, never()).save(any())` |
| **Unauthorized Class Submission** | Throws `ResourceNotFoundException`; zero audit logs saved | **PASS** | `TeacherScoringServiceTest.testOtherClassTeacherCannotAccessScoringOrAuditLogs`: `verify(gradeAuditLogRepository, never()).save(any())` |

---

## 5. Summary Scorecard

| Area | Status | Notes |
|---|:---:|---|
| **1. Action Type Classification** | **PASS** | 5 action types accurately mapped and tested. Edge case for no-op commits documented. |
| **2. Audit Data Integrity** | **PARTIAL** | Pre/post effective scores, override clear, and actor ID verified. `instructorEvaluationComments` not consolidated into `rationale` (Defect identified). |
| **3. Flyway Migration & Schema** | **PASS** | V6 applied cleanly, V1-V5 intact, `CleanDatabaseMigrationTest` 100% PASS, Hibernate validation PASS. |
| **4. Security & Rollback** | **PASS** | 403 for students, cross-class isolation enforced, actor non-forgeable, full transaction rollback on failure. |

---

## 6. Findings Requiring PO/User Direction

1. **Defect in Rationale Consolidation:**  
   Currently, `TeacherScoringService.java:182` only saves `payload.getOverrideRationale()`.  
   **Recommendation:** Consolidate `overrideRationale` and `instructorEvaluationComments` when either or both are present.
2. **No-op Re-commit Classification:**  
   When a teacher re-submits identical scores without changing anything, it currently logs `RUBRIC_UPDATE`.  
   **Recommendation:** Clarify whether no-op commits should be skipped or logged under a dedicated status.
