# Class-aware Exams Implementation Report — CodeAssess Platform

## Executive Summary

The **Class-aware Exams** feature has been successfully designed, migrated, implemented, and verified end-to-end on both Spring Boot backend and Next.js frontend of the CodeAssess platform.

This feature ensures:
1. **Teacher Multi-Class Assignment**: Teachers can assign an exam to one or multiple classes they teach/manage.
2. **Strict Ownership Validation**: Teachers can only select classes assigned to their teacher account; attempting to associate classes from other teachers triggers immediate backend rejection (`404 Not Found` / Access Denied).
3. **Student Class Enrollment Scoping**: Students only see exams associated with classes in which they are actively enrolled.
4. **Direct ID Traversal Protection**: Directly entering, guessing, or invoking API endpoints for exams outside a student's enrolled classes is rejected with `404 Not Found` (adhering to the repository's convention to prevent enumeration leaks).
5. **Direct Submission Protection**: Student submission endpoints (`/api/student/exams/{id}/submit` and `/api/student/exams/{id}/submissions`) strictly enforce class enrollment authorization before allowing code submissions.
6. **Data Integrity & Non-Destructive Migrations**: Flyway migration `V7__create_exam_classes.sql` creates a composite primary key join table `exam_classes` with `ON DELETE CASCADE` on exams and `ON DELETE RESTRICT` on classes. Submissions, score overrides, grade audit history, and plagiarism evidence remain 100% intact and decoupled from class assignment updates.

---

## 1. Initial Architecture and Schema Findings

Prior to implementation, a complete audit of the codebase revealed:
- **`Exam` Entity**: Had a singular `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "class_id") CourseClass courseClass`.
- **`CourseClass` Entity**: Had `id`, `name`, `code`, `semester`, and `@ManyToOne Teacher teacher`.
- **`ClassStudent` Entity**: Join entity with composite PK (`class_id`, `student_id`), mapping enrolled students to classes.
- **`Submission` Entity**: Had `@ManyToOne Exam exam` and `@ManyToOne Student student`.
- **Legacy Exam Query Methods**:
  - `ExamRepository.findByCourseClass_Teacher_Id(String teacherId)`: Scoped exams to a single class's teacher.
  - `ExamRepository.findByStudentId(String studentId)`: Query joined `ClassStudent cs ON cs.courseClass.id = e.courseClass.id`. This meant an exam could only be seen by students belonging to the single `class_id` on the exam row.
- **API Payloads**:
  - `CreateExamDto` and `UpdateExamDto` accepted only a single optional `classId` string.
- **Frontend**:
  - Exam creation dialog only allowed selecting one class via a standard dropdown.
  - Cohort table column only displayed a single class code.

---

## 2. Business Rules Implemented

| Domain | Business Rule | Backend Enforcement | Frontend Behavior |
|---|---|---|---|
| **Teacher Assignment** | Assign exam to 1 or more classes | Multi-class assignment supported via `classIds: List<String>`. Deduplicated and validated. | Checkbox multi-select list with "Select All" and "Clear" shortcuts. |
| **Teacher Authorization** | Only classes owned by the authenticated teacher | `TeacherExamService.validateAndFetchTeacherClasses()` checks each class ID against `courseClass.getTeacher().getId()`. Rejects unauthorized classes with `ResourceNotFoundException`. | Only eligible classes returned by `/api/teacher/classes` are rendered. |
| **Duplicate Prevention** | Safe deduplication of duplicate assignments | Set deduplication in service (`new HashSet<>(classIds)`) and DB composite PK `(exam_id, class_id)`. | UI checkboxes prevent duplicate entries. |
| **Student Visibility** | Students only see exams assigned to their classes | `ExamRepository.findByStudentId` uses JPQL join on `e.classes c JOIN ClassStudent cs ON (c.id = cs.courseClass.id OR e.courseClass.id = cs.courseClass.id)`. | Only authorized exams appear in `/student/exams` and student dashboard. |
| **Direct Access Block** | Students cannot view details of unauthorized exams | `ExamRepository.findByIdAndStudentId` returns `Optional.empty()` if student is not in an assigned class, resulting in `404 Not Found`. | Student UI displays not found / error banner; does not leak questions or details. |
| **Submission Block** | Students cannot submit to unauthorized exams | `StudentExamService.submitExam` retrieves exam via `findByIdAndStudentId`. Throws `ResourceNotFoundException(404)` if student not enrolled. | Submit button and submission endpoints blocked. |
| **History Preservation** | Changing class assignments preserves submissions | `exam_classes` join table is decoupled from `submissions`. Modifying classes does not touch existing submissions, grade audit logs, or rule scores. | Submissions retain exam reference regardless of cohort reassignment. |

---

## 3. Database Migration and Relationship Changes

### Flyway Migration: `V7__create_exam_classes.sql`
Location: [Back end/src/main/resources/db/migration/V7__create_exam_classes.sql](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/resources/db/migration/V7__create_exam_classes.sql)

```sql
CREATE TABLE exam_classes (
    exam_id VARCHAR(36) NOT NULL,
    class_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (exam_id, class_id),
    CONSTRAINT fk_exam_classes_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
    CONSTRAINT fk_exam_classes_class FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE RESTRICT,
    INDEX idx_exam_classes_exam (exam_id),
    INDEX idx_exam_classes_class (class_id)
);

-- Backfill existing relationships from exams.class_id
INSERT INTO exam_classes (exam_id, class_id)
SELECT id, class_id FROM exams 
WHERE class_id IS NOT NULL 
  AND class_id IN (SELECT id FROM classes);
```

### Entity Mapping: `Exam.java`
Location: [Back end/src/main/java/com/codeassess/exam/entity/Exam.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/exam/entity/Exam.java)
- Added `@ManyToMany(fetch = FetchType.LAZY)` with `@JoinTable(name = "exam_classes", joinColumns = @JoinColumn(name = "exam_id"), inverseJoinColumns = @JoinColumn(name = "class_id"))` to map `Set<CourseClass> classes = new HashSet<>()`.
- Maintained legacy `CourseClass courseClass` as primary cohort fallback for full backward compatibility.

---

## 4. Backend and Frontend Files Changed

### Backend Files
1. **[Exam.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/exam/entity/Exam.java)**: Added `Set<CourseClass> classes` with many-to-many join table mapping.
2. **[ClassSummaryDto.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/exam/dto/response/ClassSummaryDto.java)** *(New)*: Lightweight DTO transferring class `id`, `name`, `code`, and `semester`.
3. **[CreateExamDto.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/exam/dto/request/CreateExamDto.java)**: Added `List<String> classIds` field.
4. **[UpdateExamDto.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/exam/dto/request/UpdateExamDto.java)**: Added `List<String> classIds` field.
5. **[ExamResponse.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/exam/dto/response/ExamResponse.java)**: Added `List<String> classIds`, `List<String> classCodes`, `List<String> classNames`, and `List<ClassSummaryDto> assignedClasses`.
6. **[ExamMapper.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/exam/mapper/ExamMapper.java)**: Maps `exam.classes` to response fields and formats multi-class display headers.
7. **[ExamRepository.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/exam/repository/ExamRepository.java)**:
   - Updated `findByCourseClass_Teacher_Id` and `findByIdAndCourseClass_Teacher_Id` to match both direct `courseClass` and joined `classes`.
   - Updated `findByStudentId` and `findByIdAndStudentId` to join `classes` and `ClassStudent` with deduplication (`SELECT DISTINCT`).
8. **[ClassStudentRepository.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/classes/repository/ClassStudentRepository.java)**:
   - Added `countDistinctStudentsByClassIds(@Param("classIds") Collection<String> classIds)`.
9. **[TeacherExamService.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/exam/service/TeacherExamService.java)**:
   - Implemented `validateAndFetchTeacherClasses()` checking teacher ownership of each requested class.
   - Handled deduplicated assignment persistence on exam creation and editing.
   - Updated student count computation to count distinct students across all assigned classes.
10. **[AdminExamService.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/exam/service/AdminExamService.java)**:
    - Updated create and update methods to persist multi-class assignments.
11. **[TeacherClassAwareExamServiceTest.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/test/java/com/codeassess/exam/service/TeacherClassAwareExamServiceTest.java)** *(New)*: Comprehensive service-level unit tests for all 9 assignment and authorization permutations.
12. **[CleanDatabaseMigrationTest.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/test/java/com/codeassess/migration/CleanDatabaseMigrationTest.java)**: Updated to assert 8 migrations and 19 foreign keys in clean database verification.

### Frontend Files
1. **[teacher-exams.api.ts](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/exams/api/teacher-exams.api.ts)**: Added `ClassSummary` interface and updated `ExamResponse`, `CreateExamPayload`, and `UpdateExamPayload` with `classIds` and `assignedClasses`.
2. **[student-exams.api.ts](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/exams/api/student-exams.api.ts)**: Added `classIds` and `classCodes` to student exam interfaces and normalization.
3. **[TeacherExamsView.tsx](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/exams/components/TeacherExamsView.tsx)**:
   - Multi-class checklist selector in Create Exam modal with checkboxes, individual student counts, "Select All", and "Clear".
   - Cohort column renders badge pills for each assigned class code.
4. **[TeacherExamDetailView.tsx](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/exams/components/TeacherExamDetailView.tsx)**:
   - Assigned Classes badge list with total enrolled student count indicator.
   - Edit Assessment Settings modal includes full multi-class checklist selector.
5. **[class-aware-exams.spec.ts](file:///d:/AST%20Plagiarism%20Detection/Front%20end/tests/e2e/class-aware-exams.spec.ts)** *(New)*: Playwright E2E test suite covering teacher multi-class creation, editing, student scoped visibility, direct ID rejection, and unauthorized submission prevention.

---

## 5. Authorization Checks Added

1. **Teacher Class Ownership Check**:
   - Location: `TeacherExamService.validateAndFetchTeacherClasses()`
   - Logic: Compares each class's `getTeacher().getId()` against the authenticated teacher's ID.
   - Failure Response: Throws `ResourceNotFoundException("Class not found or not authorized: " + classId)` (`404 Not Found`).

2. **Student Class Enrollment Scoping (List)**:
   - Location: `ExamRepository.findByStudentId(studentId)`
   - JPQL:
     ```sql
     SELECT DISTINCT e FROM Exam e 
     LEFT JOIN e.classes c 
     JOIN ClassStudent cs ON (c.id = cs.courseClass.id OR e.courseClass.id = cs.courseClass.id) 
     WHERE cs.student.id = :studentId
     ```
   - Behavior: Only exams where the student is in `class_students` for at least one assigned class are returned.

3. **Student Direct Exam Detail Access Check**:
   - Location: `StudentExamService.getExamById()` / `ExamRepository.findByIdAndStudentId()`
   - Logic: Looks up exam matching both `examId` AND enrollment in `e.classes` / `e.courseClass`.
   - Failure Response: Returns empty `Optional`, resulting in `404 Not Found`.

4. **Student Direct Exam Submission Check**:
   - Location: `StudentExamService.submitExam()`
   - Logic: Exam entity lookup is guarded by `findByIdAndStudentId(examId, studentId)`.
   - Failure Response: If student is not enrolled in an assigned class, throws `ResourceNotFoundException` (`404 Not Found`), blocking any submission creation.

---

## 6. Tests Executed With Exact Results

### Backend Tests
Executed via Maven Wrapper (`mvnw.cmd test`):

```
[INFO] Running com.codeassess.exam.service.TeacherClassAwareExamServiceTest
- teacherAssignsExamToSingleClass_success: PASSED
- teacherAssignsExamToMultipleClasses_success: PASSED
- teacherCannotAssignExamToAnotherTeachersClass_throwsException: PASSED
- duplicateClassAssignments_handledSafelyWithoutDuplicates: PASSED
- studentSeesExamAssignedToEnrolledClass: PASSED
- studentDoesNotSeeExamAssignedOnlyToUnrelatedClasses: PASSED
- studentCannotRetrieveUnauthorizedExamDetailsByDirectId_throwsNotFound: PASSED
- studentCannotSubmitToUnauthorizedExam_throwsNotFound: PASSED
- updatingClassAssignments_doesNotDeleteSubmissionsOrAuditLogs: PASSED
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.117 s

[INFO] Running com.codeassess.migration.CleanDatabaseMigrationTest
- Flyway migrations executed: 8 (V1, V1.1, V2, V3, V4, V5, V6, V7)
- Target schema version: 7
- Clean DB Foreign Key count: 19
- Hibernate validation PASSED for all 12 entities with ZERO schema errors
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.551 s

[INFO] Running com.codeassess.exam.controller.TeacherExamControllerTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.177 s

[INFO] Running com.codeassess.exam.controller.ExamControllerTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.175 s
```
**Total Backend Tests: 25/25 PASSED (100%)**

### Frontend E2E Tests
Executed via Playwright (`npx playwright test tests/e2e/class-aware-exams.spec.ts`):

```
Running 5 tests using 1 worker

  ok 1 [chromium] › tests/e2e/class-aware-exams.spec.ts:6:7
       Teacher assigns an exam to multiple classes and verifies assignments in list and detail (2.4s)
  ok 2 [chromium] › tests/e2e/class-aware-exams.spec.ts:69:7
       Teacher can edit assigned classes on an existing exam (1.3s)
  ok 3 [chromium] › tests/e2e/class-aware-exams.spec.ts:99:7
       Student views only exams for their enrolled classes (803ms)
  ok 4 [chromium] › tests/e2e/class-aware-exams.spec.ts:115:7
       Student is blocked from accessing unauthorized exams by direct ID (8.2s)
  ok 5 [chromium] › tests/e2e/class-aware-exams.spec.ts:128:7
       Unauthorized exam submission is rejected with 404 (585ms)

5 passed (15.2s)
```
**Total Class-aware E2E Tests: 5/5 PASSED (100%)**

### Regression Tests
Executed via Playwright (`npx playwright test tests/e2e/grade-audit-history.spec.ts`):

```
Running 7 tests using 1 worker

  ok 1 [chromium] Teacher views populated audit history with chronological order and score diffs (2.4s)
  ok 2 [chromium] Teacher scoring page also embeds and displays Grade Audit History (844ms)
  ok 3 [chromium] Renders NULL override as "No override" distinctly from numeric 0.00 pts (1.3s)
  ok 4 [chromium] Displays empty state when no audit history exists (1.2s)
  ok 5 [chromium] Displays error callout and retry button on API failure (1.3s)
  ok 6 [chromium] Student route does NOT expose Grade Audit History (741ms)
  ok 7 [chromium] Responsive verification: Mobile and Desktop layouts (1.3s)

7 passed (10.3s)
```
**Regression Tests: 7/7 PASSED (100%)**

---

## 7. Build and Lint Results

1. **TypeScript Type Check**:
   - Command: `npx tsc --noEmit`
   - Exit Code: `0`
   - Output: Clean, zero type errors.

2. **ESLint**:
   - Command: `npm run lint -- --quiet`
   - Exit Code: `0`
   - Output: Clean, zero errors, zero warnings.

3. **Next.js Production Build**:
   - Command: `npm run build`
   - Exit Code: `0`
   - Output: Compiled successfully, all 23 application routes generated cleanly.

4. **Spring Boot Backend Packaging**:
   - Command: `mvnw.cmd package -DskipTests`
   - Exit Code: `0`
   - Artifact: `Back end/target/codeassess-backend-0.0.1-SNAPSHOT.jar`

---

## 8. Known Limitations and Blocked Checks

- **Database Connection Prerequisite**: MySQL must be running on `localhost:3306` with user `root` / password `123456` for live server daemons and clean migration tests.
- **No Blocked Checks**: All unit, integration, clean schema migration, type check, lint, build, and live E2E tests completed with exit code 0.

---

## 9. Confirmation of Working-Tree Changes

All pre-existing working-tree modifications across the repository have been strictly preserved. No destructive Git commands (`git reset`, `git checkout`, `git clean`) were executed.

Verification via `git status --short`:
- Grade Audit Trail files, reports, and UI components remain unmodified from their prior verified state.
- Plagiarism, scoring, and appeals components remain intact.
- The repository is clean, consistent, and ready for review.
