# CODEASSESS — GRADE AUDIT TRAIL IMPLEMENTATION REPORT

**Author:** Antigravity Engineering  
**Date:** 2026-10-15  
**Status:** IMPLEMENTED, VERIFIED & PASSING  
**Scope:** Backend Grade Audit Trail Architecture, Database Migration, Authorization & Automated Test Suite  

---

## 1. Executive Summary

This report documents the design, verification, and end-to-end implementation of the **Grade Audit Trail** for CodeAssess.
The audit trail records every scoring action performed on a submission — distinguishing initial grading, rubric updates, manual score overrides, override modifications, and override clearing.

All modifications strictly adhered to the safety rules:
- **No Git reset/checkout/clean/commit/push:** Working tree preserved.
- **Migration Integrity:** Added `V6__create_grade_audit_logs.sql` as a new incremental migration; existing migrations (V1 through V5) were untouched.
- **Security & Authorization:** Audit actor identity is authoritatively derived from the Spring Security authenticated context (`currentUser.getId()`). Client-side actor identity is not accepted or trusted. Resource-level authorization enforces that only teachers assigned to the course class can access scoring and audit trails.
- **Scope Restriction:** Student access to audit logs remains completely blocked (HTTP 403) pending Product Owner confirmation of student-facing presentation rules.

---

## 2. Phase 1: Pre-Design Verification Findings

Prior to schema design and coding, the following behaviors were verified against active code and live data:

| Check Item | Verified Behavior | Code & Architectural Evidence |
|---|---|---|
| **Effective Score Behavior** | • When `overrideScore == null`: `effectiveScore = totalScore` (rubric sum).<br>• When `overrideScore != null`: `effectiveScore = overrideScore`.<br>• When cleared to `null`: `effectiveScore` immediately reverts to rubric sum. | Verified in `TeacherScoringService.java` (`getScoringDetail`, `updateScoring`) and `StudentScoringService.java`. |
| **Max Score Scale & Fallback** | Dynamic calculation: $\sum \text{criteria.maxScore}$. If rubric criteria sum to 0 or are empty, fallback rule defaults to `100.0`. Validated in `updateScoring` and `getScoringDetail`. | Validated via `TeacherScoringServiceTest.testOverrideScoreExceedingScaleRejected`. |
| **Rationale & Evaluation Comments Storage** | • `overrideRationale` was passed in `SaveScoringPayload` but had no persistent storage in `submissions` or any table.<br>• `instructorEvaluationComments` had no dedicated storage.<br>• **Resolution:** Stored permanently in `grade_audit_logs.rationale`. | `V6__create_grade_audit_logs.sql` provides the persistent storage for rationales with historical preservation. |

---

## 3. Phase 2: Schema & Architecture Design

### 3.1. Database Schema (`grade_audit_logs`)

Created via Flyway migration [`V6__create_grade_audit_logs.sql`](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/resources/db/migration/V6__create_grade_audit_logs.sql):

```sql
CREATE TABLE IF NOT EXISTS grade_audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    submission_id VARCHAR(36) NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    pre_rubric_score DECIMAL(5,2),
    post_rubric_score DECIMAL(5,2),
    pre_override_score DECIMAL(5,2),
    post_override_score DECIMAL(5,2),
    pre_effective_score DECIMAL(5,2),
    post_effective_score DECIMAL(5,2),
    rationale TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_grade_audit_submission FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_grade_audit_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    INDEX idx_grade_audit_submission (submission_id),
    INDEX idx_grade_audit_created_at (created_at)
);
```

### 3.2. Action Type Taxonomy

The system dynamically classifies scoring events based on the state delta:

```mermaid
flowchart TD
    A[Incoming Scoring Update Request] --> B{Is Initial Grading?\nevaluatedAt == null || status == PENDING}
    B -- Yes --> C[actionType = INITIAL_GRADING]
    B -- No --> D{preOverride == null && postOverride != null?}
    D -- Yes --> E[actionType = OVERRIDE_SET]
    D -- No --> F{preOverride != null && postOverride == null?}
    F -- Yes --> G[actionType = OVERRIDE_CLEARED\nRevert to Rubric]
    F -- No --> H{preOverride != postOverride?}
    H -- Yes --> I[actionType = OVERRIDE_UPDATE]
    H -- No --> J[actionType = RUBRIC_UPDATE]
```

### 3.3. Transactional Rollback Guarantee
Both rubric score persistence (`ruleScoreRepository`), submission status (`submissionRepository`), and audit log creation (`gradeAuditLogRepository`) reside within the same Spring `@Transactional` boundary in [`TeacherScoringService.updateScoring`](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/scoring/service/TeacherScoringService.java#L79-L188). If criterion scoring validation fails, scale checks fail, or database errors occur, the entire transaction rolls back cleanly, guaranteeing that no partial or orphaned audit log is ever committed.

---

## 4. Phase 3: Implementation & Security Controls

### 4.1. Code Artifacts Created & Modified

1. **Flyway Migration:**
   - [`V6__create_grade_audit_logs.sql`](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/resources/db/migration/V6__create_grade_audit_logs.sql): Creates table, foreign keys, and indexes.
2. **JPA Entity & Repository:**
   - [`GradeAuditLog.java`](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/scoring/entity/GradeAuditLog.java): Mapped entity with UUID generator, relation to `Submission` and `User` (actor).
   - [`GradeAuditLogRepository.java`](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/scoring/repository/GradeAuditLogRepository.java): Query method `findBySubmission_IdOrderByCreatedAtDesc(String submissionId)`.
3. **Response DTO:**
   - [`GradeAuditLogDto.java`](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/scoring/dto/response/GradeAuditLogDto.java): Exposes sanitized audit details (`id`, `actorName`, `actorEmail`, `actionType`, pre/post rubric, pre/post override, pre/post effective, `rationale`, `createdAt`).
4. **Service Layer:**
   - [`TeacherScoringService.java`](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/scoring/service/TeacherScoringService.java):
     - Injected `GradeAuditLogRepository` and `UserRepository`.
     - Integrated audit log recording inside `updateScoring`.
     - Implemented `getAuditLogs(submissionId, teacherId)` with class-teacher ownership validation.
5. **Controllers & Endpoints:**
   - [`TeacherScoringController.java`](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/scoring/controller/TeacherScoringController.java): Exposes `GET /api/teacher/submissions/{id}/scoring/audit-logs`.
   - [`TeacherSubmissionController.java`](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/submission/controller/TeacherSubmissionController.java): Exposes `GET /api/teacher/submissions/{id}/audit-logs`.
   - Both require `@PreAuthorize("hasRole('TEACHER')")` and filter by the teacher's enrolled classes.

### 4.2. Security & Authorization Guarantees

| Requirement | Implementation Mechanism | Evidence |
|---|---|---|
| **Actor Identity Authentication** | Resolved strictly on backend via `@AuthenticationPrincipal User currentUser` -> `teacherId`. Client cannot supply or forge `actor_id` or `changedByUserId`. | Verified in `TeacherScoringServiceTest.testAuditLogActorMatchesAuthenticatedTeacherNotForged`. |
| **Resource-Level Authorization** | Checked via `submissionRepository.findByIdAndExam_CourseClass_Teacher_Id(submissionId, teacherId)`. If a teacher does not teach the course class containing the exam, access is rejected with `404 Not Found / Access Denied`. | Verified in `TeacherScoringServiceTest.testOtherClassTeacherCannotAccessScoringOrAuditLogs`. |
| **Role-Based Authorization** | Secured with `@PreAuthorize("hasRole('TEACHER')")`. Anonymous requests receive HTTP 401; student requests receive HTTP 403. | Verified in `ScoringControllerTest` and live HTTP integration checks. |
| **Student Privacy** | Student endpoint `/api/student/submissions/{id}/result` exposes only `effectiveScore`, `totalScore`, `overrideScore` (as badge), but NO audit log history. | Verified in `ScoringControllerTest` and Playwright tests. |

---

## 5. Automated Verification Results

### 5.1. Backend Unit & Service Tests (`TeacherScoringServiceTest`)
Command: `.\mvnw.cmd test "-Dtest=TeacherScoringServiceTest"`  
**Result: 12/12 PASS (0 errors, 0 failures)**

- `testInitialGradingCreatesAuditLog`: Verifies `actionType = INITIAL_GRADING` and rubric score transition.
- `testRubricUpdateCreatesAuditLog`: Verifies `actionType = RUBRIC_UPDATE` when rubric changes without override.
- `testOverrideSetCreatesAuditLog`: Verifies `actionType = OVERRIDE_SET` and effective score update.
- `testOverrideClearedReturnsToRubricScoreAndLogs`: Verifies `actionType = OVERRIDE_CLEARED` and return to rubric score.
- `testOverrideUpdateCreatesAuditLog`: Verifies `actionType = OVERRIDE_UPDATE` on adjusted override.
- `testNegativeScoreRejected`: Verifies rollback and `gradeAuditLogRepository.save()` never called.
- `testScoreGreaterThanMaxRejected`: Verifies rollback on score exceeding criterion limit.
- `testNegativeOverrideScoreRejected`: Verifies negative override score rejection without audit log.
- `testOverrideScoreExceedingScaleRejected`: Verifies override exceeding total scale rejection without audit log.
- `testOtherClassTeacherCannotAccessScoringOrAuditLogs`: Verifies access denial across courses.
- `testAuditLogActorMatchesAuthenticatedTeacherNotForged`: Confirms actor is bound to authenticated teacher.
- `testGetAuditLogsReturnsOrderedList`: Verifies audit logs returned in reverse chronological order (`createdAt DESC`).

### 5.2. Controller Security Tests (`ScoringControllerTest`)
Command: `.\mvnw.cmd test "-Dtest=ScoringControllerTest"`  
**Result: 7/7 PASS (0 errors, 0 failures)**

- `unauthenticatedReturns401`: HTTP 401 for anonymous access to `/audit-logs`.
- `studentCannotAccessTeacherScoring`: HTTP 403 for student role attempting to read `/audit-logs`.
- `teacherCannotAccessStudentResult`: HTTP 403 for teacher accessing student endpoint.
- `teacherCanAccessScoring`: HTTP 200 for teacher accessing scoring details.
- `teacherCanUpdateScoring`: HTTP 200 for updating scoring.
- `teacherCanAccessAuditLogs`: HTTP 200 with JSON payload validating DTO structure and values.
- `studentCanAccessResult`: HTTP 200 for student reading official result.

### 5.3. Clean Database Migration Test (`CleanDatabaseMigrationTest`)
Command: `.\mvnw.cmd test "-Dtest=CleanDatabaseMigrationTest"`  
**Result: 1/1 PASS (0 errors, 0 failures)**

- Successfully migrated clean temporary database from scratch through all 7 migrations (`V1`, `V1.1`, `V2`, `V3`, `V4`, `V5`, `V6`).
- Target schema version: **6**.
- Total foreign keys verified: **17**.
- Hibernate `SchemaValidator` against all 12 entities (including `GradeAuditLog`): **PASSED with ZERO schema errors**.
- Original production database verified 100% untouched.

### 5.4. Frontend Static Verification
- **TypeScript:** `npx tsc --noEmit` -> **0 errors, exit code 0**.
- **ESLint:** `npm run lint -- --quiet` -> **0 errors, 0 warnings, exit code 0**.
- **Production Build:** `npm run build` -> **Compiled successfully in 8.5s (23 static & dynamic pages generated)**.

### 5.5. Live Integration & Playwright Verification
- **Backend Application Jar:** Started cleanly on port 8080.
- **Frontend Next.js Server:** Started cleanly on port 3000.
- **Playwright Test:** `npx playwright test tests/e2e/scoring-override.spec.ts` -> **2 passed (8.2s)**.
  - Test 1: Teacher sets override, commits, reloads, and confirms persistence.
  - Test 2: Student views effective score and instructor adjustment badge.
- **Database Real Evidence:**
  Query on `plagiarism_system.grade_audit_logs`:
  ```
  id: a393cb11-e08b-4dd1-b807-78b63350fbbf
  submission_id: 007cf9ae-4385-46dc-b492-ba891af6a535
  actor_id: 706fb15a-1b16-4aac-8841-623e54d798c8 (Teacher A)
  action_type: RUBRIC_UPDATE
  pre_override_score: 95.00
  post_override_score: 95.00
  pre_effective_score: 95.00
  post_effective_score: 95.00
  created_at: 2026-10-15 20:16:42
  ```
- **Live HTTP Endpoint Verification:**
  - `GET /api/teacher/submissions/007cf9ae-4385-46dc-b492-ba891af6a535/scoring/audit-logs` -> **HTTP 200 OK** returning audit logs.
  - `GET /api/teacher/submissions/007cf9ae-4385-46dc-b492-ba891af6a535/audit-logs` -> **HTTP 200 OK** returning audit logs.
  - Student attempt to query `/audit-logs` -> **HTTP 403 Forbidden**.

---

## 6. Summary Matrix

| Category | Component / Test Suite | Status | Evidence |
|---|---|:---:|---|
| **Database** | Flyway V6 Migration | **PASS** | `V6__create_grade_audit_logs.sql` applied on clean DB & dev DB |
| **Validation** | Hibernate Schema Validation (12 entities) | **PASS** | `CleanDatabaseMigrationTest` (0 schema mismatch errors) |
| **Logic** | Initial / Rubric / Override Delimitation | **PASS** | `TeacherScoringServiceTest` (12/12 test cases) |
| **Rollback** | Transaction Atomicity on Error | **PASS** | Verified zero audit log written on validation failure |
| **Security** | Teacher-class authorization & authenticated actor | **PASS** | Verified teacher ownership check & 403 for unauthorized users |
| **Endpoints** | `GET .../scoring/audit-logs` & `GET .../audit-logs` | **PASS** | Verified via live authenticated session requests |
| **Student Scope** | Audit history hidden from students | **PASS** | Confirmed 403 Forbidden for Student role |
| **Build & Type** | Backend JAR packaging & Frontend Next.js build | **PASS** | `mvnw package` & `npm run build` both exit 0 |
| **E2E Flow** | Playwright Scoring & Override | **PASS** | `scoring-override.spec.ts` 2/2 tests passed against live server |

The Grade Audit Trail implementation is fully functional, verified with real database records, thoroughly tested, and ready for production deployment.
