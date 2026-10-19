# TEACHER-FACING GRADE AUDIT HISTORY UI REPORT

## Executive Summary
This report documents the implementation, integration, and verification of the Teacher-facing Grade Audit History UI for CodeAssess.
The UI consumes the existing authoritative backend endpoint (`/api/teacher/submissions/{id}/scoring/audit-logs`) to display an immutable, reverse-chronological record of all evaluation and score adjustment events. The UI accurately distinguishes `NULL` manual overrides (rendered as `"No override"`) from numeric zero scores (`0.00 pts`), renders score diffs across rubric, override, and effective scores, displays consolidated instructor justifications, and is strictly restricted to Teacher views.

---

## 1. Files Changed & Created

| Path | Type | Nature of Changes |
| :--- | :--- | :--- |
| `Front end/src/features/scoring/types/index.ts` | Modified | Added `GradeAuditActionType` union and `GradeAuditLog` interface matching the backend DTO. |
| `Front end/src/features/scoring/api/index.ts` | Modified | Added `getTeacherGradeAuditLogs(submissionId: string): Promise<GradeAuditLog[]>` targeting `/teacher/submissions/${submissionId}/scoring/audit-logs`. |
| `Front end/src/features/scoring/hooks/index.ts` | Modified | Added `teacherAuditLogs` query key; added `useTeacherGradeAuditLogs` hook; added automatic query invalidation for audit logs in `useUpdateTeacherScoring`. |
| `Front end/src/features/scoring/components/grade-audit-history.tsx` | **Created** | Reusable Stitch-compliant Grade Audit History component with loading, error, empty, and populated states; score diff formatting with null vs 0 distinction. |
| `Front end/src/features/scoring/components/index.ts` | Modified | Exported `GradeAuditHistory` component. |
| `Front end/src/app/teacher/submissions/[id]/page.tsx` | Modified | Integrated `GradeAuditHistory` beneath the source code card on the submission detail view. |
| `Front end/src/features/scoring/components/teacher-scoring-form.tsx` | Modified | Integrated `GradeAuditHistory` directly below the scoring rubric action buttons on `/teacher/scoring/[id]`. |
| `Front end/tests/e2e/grade-audit-history.spec.ts` | **Created** | Dedicated 7-test Playwright suite verifying populated logs, nullable override vs zero, empty state, error state, student route access restriction, and responsive layouts. |

---

## 2. UI Behavior & Endpoint Specifications

### 2.1 Backend Endpoint Consumed
- **Endpoint**: `GET /api/teacher/submissions/{submissionId}/scoring/audit-logs`
- **Controller**: `TeacherScoringController.getGradeAuditLogs(@PathVariable UUID submissionId)`
- **Response Format**: `List<GradeAuditLogDto>`
- **Response Schema**:
  ```json
  [
    {
      "id": "1",
      "submissionId": "040b61a8-3c7a-4ea2-aace-18a0ba2d9d32",
      "actorId": "2",
      "actorName": "Teacher A",
      "actorEmail": "teachera@fpt.edu.vn",
      "actionType": "OVERRIDE_CLEARED",
      "preRubricScore": 80.0,
      "postRubricScore": 80.0,
      "preOverrideScore": 95.0,
      "postOverrideScore": null,
      "preEffectiveScore": 95.0,
      "postEffectiveScore": 80.0,
      "rationale": "Cleared override. Effective score reverted to rubric score.\nInstructor evaluation comments: Well structured code.",
      "createdAt": "2026-10-15T19:35:48"
    }
  ]
  ```

### 2.2 UI Behavior & Presentation Rules
1. **Chronological Ordering**: Records are sorted strictly reverse-chronological (`new Date(b.createdAt) - new Date(a.createdAt)`), placing the latest evaluation or override event at the top.
2. **Actor & Timestamp**: Each entry renders an actor identification badge (`actorName` or fallback `Authorized Teacher` with optional `actorEmail`) and local formatted date/time.
3. **Action Badges**:
   - `INITIAL_GRADING`: Emerald badge (`Initial Grading`)
   - `OVERRIDE_SET`: Amber badge (`Override Set`)
   - `OVERRIDE_UPDATE`: Sky blue badge (`Override Updated`)
   - `OVERRIDE_CLEARED`: Zinc badge (`Override Cleared`)
   - `RUBRIC_UPDATE`: Indigo badge (`Rubric Updated`)
4. **Score Comparison Grid (3 Columns)**:
   - **Rubric Score**: Displays `preRubricScore -> postRubricScore`. If `null`, renders `"Not evaluated"`. If numeric `0.00`, renders `"0.00 pts"`.
   - **Manual Override**: Displays `preOverrideScore -> postOverrideScore`. If `null`, renders `"No override"` in a discrete pill. If numeric `0.00`, renders `"0.00 pts"` in amber text. **Never renders `NULL` as `0`**.
   - **Effective Score**: Displays `preEffectiveScore -> postEffectiveScore`. Post value highlighted in primary accent. If `null`, renders `"—"`.
5. **Consolidated Rationale**: When `rationale` is non-empty, renders in a shaded callout with preserved line-breaks (`whitespace-pre-line`).
6. **State Handling**:
   - **Loading State**: Accessible spinner with text `"Loading authoritative grade audit trail..."`.
   - **Error State**: Styled error container displaying error message with a working `"Retry Request"` button.
   - **Empty State**: Shield icon with `"No grade history recorded yet"` for submissions without evaluations.
   - **Header Controls**: Real-time event counter badge (`N events`) and an explicit `"Refresh"` button with rotating indicator.
7. **Role Isolation**:
   - Accessible on `/teacher/submissions/[id]` and `/teacher/scoring/[id]`.
   - **Strictly omitted from student views** (`/student/submissions/[id]`).

---

## 3. Test Commands & Exact Execution Results

### 3.1 Playwright E2E Test Suite (`tests/e2e/grade-audit-history.spec.ts`)
- **Command**:
  ```powershell
  cmd /c npx playwright test tests/e2e/grade-audit-history.spec.ts
  ```
- **Result**: **PASS (7 of 7 passed, 10.2s)**
- **Console Log**:
  ```
  Running 7 tests using 1 worker

  API Response POST http://localhost:8080/api/auth/login -> 200
  API Response GET http://localhost:8080/api/teacher/submissions/040b61a8-3c7a-4ea2-aace-18a0ba2d9d32 -> 200
  API Response GET http://localhost:8080/api/teacher/submissions/040b61a8-3c7a-4ea2-aace-18a0ba2d9d32/scoring/audit-logs -> 200
    ok 1 [chromium] › tests\e2e\grade-audit-history.spec.ts:7:7 › Teacher Grade Audit History UI Verification › Teacher views populated audit history with chronological order and score diffs (2.1s)
  API Response POST http://localhost:8080/api/auth/login -> 200
  API Response GET http://localhost:8080/api/teacher/submissions/040b61a8-3c7a-4ea2-aace-18a0ba2d9d32/scoring -> 200
  API Response GET http://localhost:8080/api/teacher/submissions/040b61a8-3c7a-4ea2-aace-18a0ba2d9d32/scoring/audit-logs -> 200
    ok 2 [chromium] › tests\e2e\grade-audit-history.spec.ts:52:7 › Teacher Grade Audit History UI Verification › Teacher scoring page also embeds and displays Grade Audit History (846ms)
  API Response POST http://localhost:8080/api/auth/login -> 200
  API Response GET http://localhost:8080/api/teacher/submissions/040b61a8-3c7a-4ea2-aace-18a0ba2d9d32/scoring/audit-logs -> 200
    ok 3 [chromium] › tests\e2e\grade-audit-history.spec.ts:64:7 › Teacher Grade Audit History UI Verification › Renders NULL override as "No override" distinctly from numeric 0.00 pts (1.3s)
  API Response POST http://localhost:8080/api/auth/login -> 200
  API Response GET http://localhost:8080/api/teacher/submissions/040b61a8-3c7a-4ea2-aace-18a0ba2d9d32/scoring/audit-logs -> 200
    ok 4 [chromium] › tests\e2e\grade-audit-history.spec.ts:107:7 › Teacher Grade Audit History UI Verification › Displays empty state when no audit history exists (1.2s)
  API Response POST http://localhost:8080/api/auth/login -> 200
  API Response GET http://localhost:8080/api/teacher/submissions/040b61a8-3c7a-4ea2-aace-18a0ba2d9d32/scoring/audit-logs -> 500
    ok 5 [chromium] › tests\e2e\grade-audit-history.spec.ts:127:7 › Teacher Grade Audit History UI Verification › Displays error callout and retry button on API failure (1.3s)
  API Response POST http://localhost:8080/api/auth/login -> 200
  API Response GET http://localhost:8080/api/student/dashboard -> 200
  API Response GET http://localhost:8080/api/student/submissions/040b61a8-3c7a-4ea2-aace-18a0ba2d9d32/result -> 200
    ok 6 [chromium] › tests\e2e\grade-audit-history.spec.ts:147:7 › Teacher Grade Audit History UI Verification › Student route does NOT expose Grade Audit History (800ms)
  API Response POST http://localhost:8080/api/auth/login -> 200
  API Response GET http://localhost:8080/api/teacher/submissions/040b61a8-3c7a-4ea2-aace-18a0ba2d9d32 -> 200
  API Response GET http://localhost:8080/api/teacher/submissions/040b61a8-3c7a-4ea2-aace-18a0ba2d9d32/scoring/audit-logs -> 200
    ok 7 [chromium] › tests\e2e\grade-audit-history.spec.ts:161:7 › Teacher Grade Audit History UI Verification › Responsive verification: Mobile and Desktop layouts (1.2s)

    7 passed (10.2s)
  ```

### 3.2 Regression Verification (`tests/e2e/scoring-override.spec.ts`)
- **Command**:
  ```powershell
  cmd /c npx playwright test tests/e2e/scoring-override.spec.ts
  ```
- **Result**: **PASS (2 of 2 passed, 3.3s)**
- **Console Log**:
  ```
  Running 2 tests using 1 worker

  API Response POST http://localhost:8080/api/auth/login -> 200
  API Response GET http://localhost:8080/api/teacher/submissions/007cf9ae-4385-46dc-b492-ba891af6a535/scoring -> 200
  API Response GET http://localhost:8080/api/teacher/submissions/007cf9ae-4385-46dc-b492-ba891af6a535/scoring/audit-logs -> 200
  API Response PUT http://localhost:8080/api/teacher/submissions/007cf9ae-4385-46dc-b492-ba891af6a535/scoring -> 200
  API Response GET http://localhost:8080/api/teacher/submissions/007cf9ae-4385-46dc-b492-ba891af6a535/scoring/audit-logs -> 200
    ok 1 [chromium] › tests\e2e\scoring-override.spec.ts:7:7 › Scoring & Override Critical Flow Verification › Teacher sets override, reloads page, and confirms persistence (1.6s)
  API Response POST http://localhost:8080/api/auth/login -> 200
  API Response GET http://localhost:8080/api/student/submissions/007cf9ae-4385-46dc-b492-ba891af6a535/result -> 200
    ok 2 [chromium] › tests\e2e\scoring-override.spec.ts:53:7 › Scoring & Override Critical Flow Verification › Student views effective score and instructor adjustment badge (891ms)

    2 passed (3.3s)
  ```

---

## 4. Build and Lint Results

### 4.1 TypeScript Verification
- **Command**: `cmd /c npx tsc --noEmit`
- **Result**: **PASS (Exit code 0, 0 diagnostic errors)**

### 4.2 ESLint Verification
- **Command**: `cmd /c npm run lint -- --quiet`
- **Result**: **PASS (Exit code 0, 0 warnings, 0 errors)**

### 4.3 Production Build
- **Command**: `cmd /c npm run build`
- **Result**: **PASS (Exit code 0)**
- **Summary**:
  - Compiled successfully in 3.1s
  - Finished TypeScript check in 3.2s
  - Generated all 23 static and dynamic routes including `/teacher/submissions/[id]` and `/teacher/scoring/[id]`.

---

## 5. Browser Verification Results

Direct browser verification was executed using Chrome/CDP on real submission `040b61a8-3c7a-4ea2-aace-18a0ba2d9d32`:

1. **Submission Detail View (`/teacher/submissions/040b61a8-3c7a-4ea2-aace-18a0ba2d9d32`)**:
   - The section header **Grade & Audit History** was rendered with an event counter pill (`3 events`) and a functional `Refresh` button.
   - Three audit log timeline cards were rendered in reverse-chronological order:
     - **Card 1 (Newest - Override Cleared)**:
       - Action Badge: `Override Cleared` (Zinc)
       - Actor: `Teacher A (teachera@fpt.edu.vn)`
       - Timestamp: Local time
       - Rubric Score: `80.00 pts -> 80.00 pts`
       - Manual Override: `95.00 pts -> No override` (Explicitly pill-tagged, not `0`)
       - Effective Score: `95.00 pts -> 80.00 pts`
       - Justification: `Cleared override. Effective score reverted to rubric score.`
     - **Card 2 (Override Updated)**:
       - Action Badge: `Override Updated` (Sky blue)
       - Manual Override: `90.00 pts -> 95.00 pts`
       - Effective Score: `90.00 pts -> 95.00 pts`
     - **Card 3 (Oldest - Initial Grading)**:
       - Action Badge: `Initial Grading` (Emerald)
       - Rubric Score: `Not evaluated -> 80.00 pts`
       - Manual Override: `No override -> 90.00 pts`
       - Effective Score: `— -> 90.00 pts`
   - Verified artifact: `grade_audit_history_submission_1792072939488.png`.

2. **Scoring Page View (`/teacher/scoring/040b61a8-3c7a-4ea2-aace-18a0ba2d9d32`)**:
   - Embedded cleanly beneath the scoring criteria list and action buttons (`Manual Score Override`, `Commit Rubric Scores`).
   - Reflects identical real-time records and live updates immediately when new overrides are submitted.
   - Verified artifact: `grade_audit_history_scoring_1792072951581.png`.

---

## 6. Known Limitations or Blocked Checks
- **None**: All automated tests, backend APIs, live database records, TypeScript checks, ESLint, production build, and interactive browser verification completed with zero blockers.
