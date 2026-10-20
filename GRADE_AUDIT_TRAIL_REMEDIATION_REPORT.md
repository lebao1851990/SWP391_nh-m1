# CODEASSESS — GRADE AUDIT TRAIL REMEDIATION REPORT

**Author:** Antigravity Engineering  
**Date:** 2026-10-15  
**Status:** COMPLETED & VERIFIED WITH LIVE EVIDENCE  
**Remediation Scope:**  
1. Issue 1: Preserve both `overrideRationale` and `instructorEvaluationComments` with structured labels.  
2. Issue 2: Prevent misclassification of no-op saves as `RUBRIC_UPDATE`.  

---

## 1. Root Cause Analysis & Solution Description

### 1.1. Issue 1: Preservation of Both Rationale and Comments

#### Root Cause:
In [`TeacherScoringService.java:182`](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/scoring/service/TeacherScoringService.java#L182), the audit log creation previously passed only `payload.getOverrideRationale()`.  
`SaveScoringPayload` defines two distinct comment fields:
- `overrideRationale`: Explanation specifically justifying a manual score override.
- `instructorEvaluationComments`: Qualitative evaluation remarks provided by the instructor regarding student performance.

When an instructor graded a submission without an override, `overrideRationale` was empty/null while `instructorEvaluationComments` carried substantial feedback. As a result, the instructor's feedback was dropped and never reached the audit log.

#### Implementation Fix:
Added `buildConsolidatedRationale(String overrideRationale, String instructorComments)` in [`TeacherScoringService.java`](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/scoring/service/TeacherScoringService.java#L187-L201):
1. **Both fields present:** Consolidates with explicit, clear labels to avoid semantic ambiguity:
   ```
   Override Rationale: <overrideRationale>
   Instructor Comments: <instructorEvaluationComments>
   ```
2. **Only `overrideRationale` present:** Saves the trimmed override rationale directly.
3. **Only `instructorEvaluationComments` present:** Saves the trimmed instructor comments directly.
4. **Both null or blank:** Sets `rationale` to `null`.
5. **Zero Schema Alteration:** Reuses the existing `grade_audit_logs.rationale TEXT` column without altering API contracts or requiring database schema changes.

---

### 1.2. Issue 2: Elimination of No-Op Save Misclassification

#### Root Cause:
In [`TeacherScoringService.java`](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/scoring/service/TeacherScoringService.java), the classification logic for already-graded submissions checked:
```java
else if (preOverrideScore == null && postOverrideScore != null) { ... }
else if (preOverrideScore != null && postOverrideScore == null) { ... }
else if (preOverrideScore != null && postOverrideScore != null && preOverrideScore.compareTo(postOverrideScore) != 0) { ... }
else {
    actionType = "RUBRIC_UPDATE";
}
```
If an instructor opened an already-graded submission with an existing override (e.g., 95.00 pts) and clicked "Commit" without changing any rubric criteria or override values, `preOverrideScore.compareTo(postOverrideScore) == 0`. The execution fell into the unconditional `else` branch, creating a false `RUBRIC_UPDATE` log with `pre_rubric_score == post_rubric_score` and `pre_override_score == post_override_score`.

#### Implementation Fix:
1. **Pre-State Capture:**
   Before deleting existing `RuleScore` records during scoring updates, the service retrieves and maps existing criteria scores:
   ```java
   List<RuleScore> existingRuleScores = ruleScoreRepository.findBySubmission_Id(submissionId);
   Map<String, BigDecimal> preCriteriaMap = existingRuleScores.stream()
           .filter(rs -> rs.getCriteria() != null)
           .collect(Collectors.toMap(rs -> rs.getCriteria().getId(), RuleScore::getScore, (a, b) -> a));
   ```
2. **Comprehensive Score Delta Comparison:**
   Calculates boolean flags comparing pre and post states:
   - `isOverrideChanged`: Checks if override was added, removed, or changed.
   - `isRubricChanged`: Checks if rubric total score changed OR if individual criterion scores changed (`!preCriteriaMap.equals(postCriteriaMap)`).
3. **Strict Conditional Audit Logging:**
   - If `isInitialGrading` is true -> `INITIAL_GRADING`.
   - Else if override added -> `OVERRIDE_SET`.
   - Else if override removed -> `OVERRIDE_CLEARED`.
   - Else if override changed -> `OVERRIDE_UPDATE`.
   - Else if rubric changed (`isRubricChanged`) -> `RUBRIC_UPDATE`.
   - **If no changes occurred:** `actionType` remains `null`.
   - The service wraps `gradeAuditLogRepository.save(auditLog)` inside `if (actionType != null)`. A no-op save persists submission state but **does not create a false or redundant audit log**.

---

## 2. Real Evidence: Live Database & API Verification

### 2.1. Evidence 1: Consolidated Rationale (Both Fields Present)

Submission `040b61a8-3c7a-4ea2-aace-18a0ba2d9d32` was graded via live API with both fields:
- `overrideRationale = "Bonus for optimal time complexity"`
- `instructorEvaluationComments = "Excellent code structure and modularity"`

**Real Audit Log Response (`GET /api/teacher/submissions/{id}/scoring/audit-logs`):**
```json
{
  "id": "c5f1c638-bfa4-4068-8aa4-fef88a01f986",
  "submissionId": "040b61a8-3c7a-4ea2-aace-18a0ba2d9d32",
  "actorId": "706fb15a-1b16-4aac-8841-623e54d798c8",
  "actorName": "Teacher A",
  "actorEmail": "teachera@fpt.edu.vn",
  "actionType": "INITIAL_GRADING",
  "preRubricScore": null,
  "postRubricScore": 0.00,
  "preOverrideScore": null,
  "postOverrideScore": 88.00,
  "preEffectiveScore": null,
  "postEffectiveScore": 88.00,
  "rationale": "Override Rationale: Bonus for optimal time complexity\nInstructor Comments: Excellent code structure and modularity",
  "createdAt": "2026-10-15T20:33:04"
}
```
Both fields were preserved intact with distinct, unambiguous labels.

---

### 2.2. Evidence 2: No-Op Save Does Not Generate Audit Log

Immediately following the initial grading of `040b61a8-3c7a-4ea2-aace-18a0ba2d9d32`, an identical PUT request with exact same scores and comments was sent.

**Audit Log Count Result:**
- Count before identical save: **1**
- Count after identical save: **1**
- **Outcome:** Zero duplicate or misclassified `RUBRIC_UPDATE` records were generated.

---

### 2.3. Evidence 3: Sequential Transitions (`OVERRIDE_UPDATE` -> `OVERRIDE_CLEARED`)

The same submission was subsequently modified in two steps:
1. Override changed from 88.00 to 92.00 (`actionType = OVERRIDE_UPDATE`).
2. Override cleared (`null`) to revert to rubric score (`actionType = OVERRIDE_CLEARED`).

**Real Audit Trail Returned by Teacher API:**
```json
[
  {
    "id": "ae217d29-9611-4e30-9846-387cbbee4cb3",
    "actionType": "OVERRIDE_CLEARED",
    "preOverrideScore": 92.00,
    "postOverrideScore": null,
    "preEffectiveScore": 92.00,
    "postEffectiveScore": 0.00,
    "rationale": "Cleared manual override to revert to rubric score",
    "createdAt": "2026-10-15T20:34:11"
  },
  {
    "id": "dc7f6db9-cac9-414c-8b12-8062a4451279",
    "actionType": "OVERRIDE_UPDATE",
    "preOverrideScore": 88.00,
    "postOverrideScore": 92.00,
    "preEffectiveScore": 88.00,
    "postEffectiveScore": 92.00,
    "rationale": "Adjusted bonus after code review",
    "createdAt": "2026-10-15T20:33:59"
  },
  {
    "id": "c5f1c638-bfa4-4068-8aa4-fef88a01f986",
    "actionType": "INITIAL_GRADING",
    "preEffectiveScore": null,
    "postEffectiveScore": 88.00,
    "rationale": "Override Rationale: Bonus for optimal time complexity\nInstructor Comments: Excellent code structure and modularity",
    "createdAt": "2026-10-15T20:33:04"
  }
]
```

---

## 3. Automated Test Suite Results

| Test Class / Suite | Command | Result | Details |
|---|---|:---:|---|
| **`TeacherScoringServiceTest`** | `mvnw test -Dtest=TeacherScoringServiceTest` | **PASS (17/17)** | Includes: `testNoOpSaveDoesNotRecordAuditLog`, `testConsolidatedRationaleBothFieldsPresent`, `testConsolidatedRationaleOnlyOverrideRationale`, `testConsolidatedRationaleOnlyInstructorComments`, `testConsolidatedRationaleBothNullOrBlank`, `testInitialGradingCreatesAuditLog`, `testRubricUpdateCreatesAuditLog`, `testOverrideSetCreatesAuditLog`, `testOverrideClearedReturnsToRubricScoreAndLogs`, `testOverrideUpdateCreatesAuditLog`. |
| **`ScoringControllerTest`** | `mvnw test -Dtest=ScoringControllerTest` | **PASS (7/7)** | 401 unauthenticated, 403 student access forbidden, 200 teacher access and audit-logs DTO serialization. |
| **`CleanDatabaseMigrationTest`** | `mvnw test -Dtest=CleanDatabaseMigrationTest` | **PASS (1/1)** | Clean DB migrated through all 7 migrations (V1 to V6), Hibernate SchemaValidator validated all 12 entities with 0 schema errors. |
| **Combined Regression** | `mvnw test -Dtest=TeacherScoringServiceTest,ScoringControllerTest,CleanDatabaseMigrationTest` | **PASS (25/25)** | All 25 unit and integration tests passed with 0 failures and 0 errors. |
| **Backend Packaging** | `mvnw package -DskipTests` | **PASS** | JAR generated at `Back end/target/codeassess-backend-0.0.1-SNAPSHOT.jar`. |
| **Frontend TypeScript** | `npx tsc --noEmit` | **PASS** | 0 TypeScript errors. |
| **Frontend Lint** | `npm run lint -- --quiet` | **PASS** | 0 lint warnings/errors. |
| **Playwright E2E** | `npx playwright test tests/e2e/scoring-override.spec.ts` | **PASS (2/2)** | 2/2 tests passed on live Spring Boot + Next.js servers. |

---

## 4. Safety & Non-Destructive Invariant Guarantees

1. **Migrations Untouched:**
   - Existing migrations `V1` through `V5` and `V6` remain completely unchanged.
   - No migration files were edited or rolled back.
2. **Historical Data Preserved:**
   - Pre-existing audit records (including test record `a393cb11-e08b-4dd1-b807-78b63350fbbf`) remain safely preserved in the database.
   - No tables were dropped and no historical data was deleted.
3. **Plagiarism & Core Logic Intact:**
   - AST plagiarism detection algorithms, fingerprint calculations, and similarity matrices were completely unreferenced and untouched.
4. **Git Tree Preservation:**
   - No destructive Git commands were executed: no `git reset`, `git checkout`, `git clean`, `git commit`, or `git push`.
   - All uncommitted work remains safe and intact.
