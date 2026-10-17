# CODEASSESS — BÁO CÁO AUDIT CHẤM ĐIỂM & ĐÈ ĐIỂM (TEACHER GRADING & SCORE OVERRIDE)

> **Mã tài liệu**: `AUDIT-GRADING-OVERRIDE-2026-10-15`  
> **Phạm vi kiểm tra**: Toàn bộ luồng chấm điểm rubric, chỉnh sửa điểm và manual score override từ Frontend UI → REST API → Business Service → Persistence Repository → MySQL Database.  
> **Nguyên tắc tuân thủ**: Báo cáo kiểm định thực tế dựa trên source code và kiểm thử tự động; không giả lập, không mock dữ liệu, không thay đổi schema hay business logic trong giai đoạn audit.

---

## 1. Truy Vết Luồng Dữ Liệu (End-to-End Data Flow)

```
[Teacher Frontend UI]
  ├─ /teacher/submissions/[id] (TeacherSubmissionDetail)
  │    └─ Nút "Grade Submission" → điều hướng tới:
  ├─ /teacher/scoring/[id] (TeacherScoringPage → TeacherScoringView)
  │    ├─ Query: useTeacherScoring(id) → GET /api/teacher/submissions/{id}/scoring
  │    ├─ Form: TeacherScoringForm (React Hook Form + Zod)
  │    │    ├─ Nhập điểm tiêu chí: criteriaScores[i].score
  │    │    ├─ Nhận xét: instructorEvaluationComments
  │    │    └─ Modal "Manual Score Override": overrideTotalScore & overrideRationale
  │    └─ Mutation: useUpdateTeacherScoring(id) → PUT /api/teacher/submissions/{id}/scoring
  ▼
[REST Controller Boundary]
  ├─ TeacherScoringController.java (@RequestMapping("/api/teacher/submissions/{id}/scoring"))
  │    ├─ @PreAuthorize("hasRole('TEACHER')")
  │    ├─ GET  → teacherScoringService.getScoringDetail(id, currentUser.getId())
  │    └─ PUT  → teacherScoringService.updateScoring(id, currentUser.getId(), payload)
  ▼
[Business Service Layer]
  ├─ TeacherScoringService.java
  │    ├─ Authorization: submissionRepository.findByIdAndExam_CourseClass_Teacher_Id(id, teacherId)
  │    ├─ Xóa điểm tiêu chí cũ: ruleScoreRepository.deleteBySubmission_Id(id)
  │    ├─ Validate từng tiêu chí: score in [0, criteria.maxScore]
  │    ├─ Tính tổng rubric: totalScore = sum(criterion.score)
  │    ├─ Ghi đè Submission:
  │    │    ├─ submission.setTotalScore(totalScore)
  │    │    ├─ submission.setOverrideScore(payload.getOverrideTotalScore())
  │    │    ├─ submission.setEvaluatedAt(LocalDateTime.now())
  │    │    └─ submission.setStatus("GRADED") (nếu trước đó là PENDING hoặc null)
  │    └─ submissionRepository.save(submission)
  ▼
[Database Persistence Layer]
  ├─ submissions (MySQL table)
  │    ├─ total_score DECIMAL(5,2) NULL  ← Ghi đè giá trị mới, mất giá trị cũ
  │    ├─ override_score DECIMAL(5,2) NULL ← Ghi đè giá trị mới, mất giá trị cũ
  │    ├─ evaluated_at TIMESTAMP NULL     ← Ghi đè thời điểm mới nhất
  │    └─ status VARCHAR(50)              ← Cập nhật thành 'GRADED'
  └─ rule_scores (MySQL table)
       └─ (submission_id, criteria_id, score) ← Xóa toàn bộ row cũ và insert lại row mới
```

---

## 2. Danh Sách Endpoint & File Liên Quan Đã Kiểm Tra

### Backend (Spring Boot 3, Java 21)
| File / Thành Phần | Vai Trò | Endpoint / Phương Thức |
| :--- | :--- | :--- |
| `TeacherScoringController.java` | REST API Controller | `GET /api/teacher/submissions/{id}/scoring`<br>`PUT /api/teacher/submissions/{id}/scoring` |
| `TeacherScoringService.java` | Scoring Business Logic | `getScoringDetail(id, teacherId)`<br>`updateScoring(id, teacherId, payload)` |
| `StudentScoringController.java` | Student View Result API | `GET /api/student/submissions/{id}/result` |
| `StudentScoringService.java` | Student Result Logic | `getStudentResult(id, studentId)` |
| `TeacherSubmissionController.java` | Submission Detail API | `GET /api/teacher/submissions/{id}` |
| `TeacherSubmissionService.java` | Submission Detail Service | `getTeacherSubmissionById(id, teacherId)` |
| `SaveScoringPayload.java` | Inbound Request DTO | Chứa `criteriaScores` và `overrideTotalScore` |
| `ScoringDetail.java` | Outbound Response DTO | Chứa `totalScore`, `overrideTotalScore`, `effectiveScore`, `criteria` |
| `Submission.java` | JPA Entity (`submissions`) | Quản lý `totalScore`, `overrideScore`, `evaluatedAt`, `status` |
| `RuleScore.java` | JPA Entity (`rule_scores`) | Quản lý điểm từng tiêu chí chấm của submission |
| `ScoringCriteria.java` | JPA Entity (`scoring_criteria`) | Tiêu chí chấm và điểm tối đa (`maxScore`) |
| `V3__extend_submissions_schema.sql` | Flyway Migration | Thêm `total_score`, `override_score`, `evaluated_at` |
| `V5__create_scoring_and_plagiarism_evidence.sql` | Flyway Migration | Tạo `scoring_criteria`, `rule_scores`, `plagiarism_evidence` |

### Frontend (Next.js 16 App Router, TypeScript)
| File / Thành Phần | Vai Trò | Mô Tả |
| :--- | :--- | :--- |
| `src/app/teacher/scoring/[id]/page.tsx` | Route Page | Trang giao diện chấm điểm giáo viên |
| `src/app/teacher/submissions/[id]/page.tsx` | Route Page | Trang chi tiết bài nộp và điều hướng chấm điểm |
| `src/features/scoring/components/teacher-scoring-view.tsx` | UI Container | Xử lý Loading, Error, 403, 404, phân quyền |
| `src/features/scoring/components/teacher-scoring-form.tsx` | Interactive Form | Nhập điểm tiêu chí, tính tổng động, modal override |
| `src/features/scoring/components/student-result-view.tsx` | Student Result UI | Hiển thị bảng điểm và tiêu chí cho sinh viên |
| `src/features/scoring/api/index.ts` | API Client | `getTeacherScoring`, `updateTeacherScoring`, `getStudentResult` |
| `src/features/scoring/hooks/index.ts` | React Query Hooks | `useTeacherScoring`, `useUpdateTeacherScoring`, `useStudentResult` |
| `src/features/scoring/schemas/index.ts` | Zod Validation | Schema `SaveScoringPayloadSchema` |
| `src/features/scoring/types/index.ts` | Type Definitions | Định nghĩa `ScoringDetail`, `SaveScoringPayload`, v.v. |

---

## 3. Bảng Trạng Thái Từng Chức Năng (PASS / PARTIAL / MISSING / BLOCKED)

| STT | Chức Năng Kiểm Tra | Trạng Thái | Chi Tiết & Bằng Chứng Mã Nguồn |
| :---: | :--- | :---: | :--- |
| **1** | **Xác thực và Phân quyền Backend (Role & Tenant Isolation)** | **PASS** | `TeacherScoringController` có `@PreAuthorize("hasRole('TEACHER')")`. `TeacherScoringService` truy vấn qua `findByIdAndExam_CourseClass_Teacher_Id(id, teacherId)`. Giáo viên không thể xem hoặc sửa điểm của lớp khác. |
| **2** | **Tính toán điểm tổng theo Rubric tiêu chí (Dynamic Calculation)** | **PASS** | Frontend tính live total qua `useWatch`; Backend tính lại chính xác bằng `sum(criteria.score)` và lưu vào `submission.totalScore`. |
| **3** | **Validation giới hạn điểm từng tiêu chí (Criteria Min/Max)** | **PASS** | Backend kiểm tra `score < 0 || score > criteria.maxScore` và ném `IllegalArgumentException`. Frontend Zod schema kiểm tra `score >= 0` và HTML input giới hạn `max`. |
| **4** | **Cơ chế ghi nhận điểm đè (Manual Override Score)** | **PASS** | `submission.setOverrideScore(payload.getOverrideTotalScore())` được lưu vào cột `override_score`. `effectiveScore` ưu tiên `overrideScore` nếu khác `null`. |
| **5** | **Bảo vệ chống duplicate request khi đang lưu** | **PASS** | `updateMutation.isPending` khóa form, disable nút submit và hiển thị spinner `Saving...`. |
| **6** | **Validation giới hạn điểm đè (Override Score Min/Max)** | **MISSING** | Backend hoàn toàn **không validate** `overrideTotalScore` (có thể nhận số âm hoặc > 100 nếu gửi qua API). |
| **7** | **Điều kiện khóa sửa điểm theo vòng đời bài thi (Lifecycle Lock)** | **MISSING** | Hệ thống cho phép sửa điểm vô hạn lần bất kỳ lúc nào; không có kiểm tra trạng thái kỳ thi đã kết thúc, bài đã chốt sổ điểm hay sinh viên đang có Appeal. |
| **8** | **Lưu trữ lý do chỉnh sửa / đè điểm (Override Rationale)** | **MISSING** | Frontend có ô nhập `overrideRationale` nhưng Backend DTO `SaveScoringPayload` không khai báo trường này; dữ liệu lý do bị drop âm thầm và không lưu vào DB. |
| **9** | **Lưu trữ nhận xét của giảng viên (Instructor Evaluation Comments)** | **MISSING** | Frontend có ô nhập `instructorEvaluationComments` nhưng Backend DTO `SaveScoringPayload` không khai báo trường này; nhận xét bị drop âm thầm. |
| **10** | **Lưu vết người chỉnh sửa điểm (Modifier / Auditor Tracking)** | **MISSING** | Bảng `submissions` và `rule_scores` không có cột lưu `updated_by` hoặc `teacher_id` của người thực hiện lần sửa đổi gần nhất. |
| **11** | **Lưu vết lịch sử điểm cũ và các mốc thời gian (Audit Trail History)** | **MISSING** | Khi sửa điểm, giá trị `total_score`, `override_score` và `evaluated_at` cũ bị **ghi đè hoàn toàn**. Không có bảng nhật ký (Audit Log) nào tồn tại. |
| **12** | **Đồng bộ hiển thị điểm Override phía Student Result** | **PARTIAL** | Backend trả về `effectiveScore`, nhưng giao diện `student-result-view.tsx` (dòng 105) lại render trực tiếp `{data.totalScore}` thay vì `effectiveScore`, dẫn đến nguy cơ lệch điểm hiển thị khi có override. |
| **13** | **Đồng bộ state Override khi reload trang chấm điểm** | **PARTIAL** | TypeScript interface `ScoringDetail` phía Frontend thiếu trường `overrideTotalScore`. `TeacherScoringForm.tsx` (dòng 46) khởi tạo `overrideTotalScore: initialData.totalScore`, khiến form mất giá trị override trước đó khi refresh. |

---

## 4. Các Lỗi & Rủi Ro Có Bằng Chứng Cụ Thể

### Rủi ro 1: Thất thoát dữ liệu lý do đè điểm (Data Dropping Bug)
- **Bằng chứng**:
  - Tại `Front end/src/features/scoring/components/teacher-scoring-form.tsx`:
    ```tsx
    // Dòng 204: Form thu thập overrideRationale
    {...register('overrideRationale')}
    // Dòng 156: Form thu thập instructorEvaluationComments
    {...register('instructorEvaluationComments')}
    ```
  - Tại `Back end/src/main/java/com/codeassess/scoring/dto/request/SaveScoringPayload.java`:
    ```java
    public class SaveScoringPayload {
        private List<CriteriaScoreRequest> criteriaScores;
        private BigDecimal overrideTotalScore;
        // KHÔNG CÓ overrideRationale
        // KHÔNG CÓ instructorEvaluationComments
    }
    ```
- **Hệ quả**: Giảng viên nhập lý do giải trình đè điểm và nhận xét rất chi tiết nhưng hệ thống âm thầm loại bỏ khi gửi qua API. Cơ sở dữ liệu hoàn toàn không lưu trữ được lý do.

### Rủi ro 2: Thiếu Validation chặn giá trị điểm đè bất hợp lệ ở Backend
- **Bằng chứng**:
  - Tại `Back end/src/main/java/com/codeassess/scoring/service/TeacherScoringService.java`:
    ```java
    // Dòng 94:
    submission.setOverrideScore(payload.getOverrideTotalScore());
    ```
- **Hệ quả**: Nếu một request độc hại hoặc lỗi client gửi `overrideTotalScore = -50` hoặc `overrideTotalScore = 9999`, backend vẫn lưu thẳng vào DB (chỉ bị giới hạn bởi kiểu `DECIMAL(5,2)` tối đa 999.99 của MySQL), gây sai lệch dữ liệu học thuật nghiêm trọng.

### Rủi ro 3: Xung đột hiển thị điểm giữa điểm Rubric và điểm Override trên giao diện sinh viên
- **Bằng chứng**:
  - Backend `StudentSubmissionResult.java`:
    ```java
    private BigDecimal totalScore;
    private BigDecimal overrideTotalScore;
    private BigDecimal effectiveScore; // = overrideScore != null ? overrideScore : totalScore
    ```
  - Frontend `StudentResultView.tsx` (dòng 105):
    ```tsx
    <span className="font-display-lg text-[64px] leading-none font-bold text-primary tracking-tight">
      {data.totalScore}
    </span>
    ```
- **Hệ quả**: Khi giáo viên đã chấm điểm đè (ví dụ: điểm rubric là 60, điểm đè là 85), sinh viên vào xem kết quả vẫn thấy hiển thị con số `60` vì giao diện render `data.totalScore` thay vì `data.effectiveScore`.

### Rủi ro 4: Mất dấu vết hoàn toàn khi điểm số bị sửa đổi (No Audit Trail)
- **Bằng chứng**:
  - Phương thức `updateScoring` chạy `submissionRepository.save(submission)`. Điểm số cũ bị UPDATE đè lên record hiện tại.
- **Hệ quả**: Nếu một giảng viên sửa điểm từ 4.0 lên 9.0 (hoặc ngược lại), nhà trường/thanh tra học thuật hoàn toàn không thể biết:
  1. Ai đã sửa điểm?
  2. Điểm trước khi sửa là bao nhiêu?
  3. Sửa vào thời điểm nào?
  4. Lý do sửa là gì?
  5. Điểm ban đầu do hệ thống tự tính hay do giáo viên chấm?

---

## 5. Kết Quả Kiểm Thử Thực Tế

### A. Kiểm thử Backend Unit & Integration Tests
Đã chạy bộ kiểm thử chuyên biệt bằng Maven trên Java 21:
- `com.codeassess.scoring.service.TeacherScoringServiceTest`: **4/4 PASS**
  - `testNegativeScoreRejected`: Chặn điểm tiêu chí < 0 (PASS)
  - `testScoreGreaterThanMaxRejected`: Chặn điểm tiêu chí > max (PASS)
  - `testValidScoreAcceptedAndTotalsCalculated`: Tính tổng rubric chính xác (PASS)
  - `testOverrideScoreChangesEffectiveScore`: Xác nhận `effectiveScore` lấy từ `overrideScore` (PASS)
- `com.codeassess.scoring.controller.ScoringControllerTest`: **6/6 PASS**
  - `unauthenticatedReturns401`: Chặn chưa đăng nhập (PASS)
  - `studentCannotAccessTeacherScoring`: Chặn sinh viên truy cập API chấm điểm (PASS)
  - `teacherCannotAccessStudentResult`: Phân quyền role chính xác (PASS)
  - `teacherCanAccessScoring`: Giáo viên lấy đúng thông tin chấm điểm (PASS)
  - `teacherCanUpdateScoring`: Cập nhật rubric qua PUT thành công (PASS)
  - `studentCanAccessResult`: Sinh viên đọc kết quả được cấp quyền (PASS)

### B. Kiểm thử Trình Duyệt Thực Tế (Playwright E2E)
- Suite `core-flows.spec.ts` (Flow 5 — Teacher Manual Scoring): **PASS**
  - Trình duyệt đăng nhập tài khoản giảng viên thật `teachera@fpt.edu.vn`.
  - Mở bài thi `d94ae48c-e24a-4a74-93bf-539d5aba716e` của sinh viên `studenta@fpt.edu.vn`.
  - Mở form chấm điểm `/teacher/scoring/[id]`.
  - Mở modal "Manual Score Override", nhập điểm đè `95`, nhập lý do `'Edge case test suite resolution confirmed'`.
  - Gửi request `PUT /api/teacher/submissions/{id}/scoring` thành công (HTTP 200).
  - Trình duyệt nhận response, refresh cache và hiển thị banner `'Evaluation saved successfully!'`.

---

## 6. Đề Xuất Thiết Kế Grade Audit Trail Tương Thích Kiến Trúc Hiện Tại

Để bổ sung Grade Audit Trail mà **không làm thay đổi logic tính điểm hiện tại, không gây sai lệch điểm số, và không làm vỡ các API contract đang có**, đề xuất kiến trúc như sau:

### 6.1. Thiết kế Cơ sở dữ liệu (Flyway Migration `V6__create_grade_audit_trail.sql`)
Tạo một bảng độc lập dành riêng cho nhật ký kiểm toán điểm, không sửa đổi cấu trúc bảng `submissions` hiện tại:

```sql
CREATE TABLE grade_audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    submission_id VARCHAR(36) NOT NULL,
    changed_by_user_id VARCHAR(36) NOT NULL,
    action_type VARCHAR(50) NOT NULL, -- 'INITIAL_GRADING', 'RUBRIC_UPDATE', 'MANUAL_OVERRIDE'
    old_total_score DECIMAL(5,2) NULL,
    new_total_score DECIMAL(5,2) NULL,
    old_override_score DECIMAL(5,2) NULL,
    new_override_score DECIMAL(5,2) NULL,
    old_effective_score DECIMAL(5,2) NULL,
    new_effective_score DECIMAL(5,2) NOT NULL,
    reason TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_submission FOREIGN KEY (submission_id) REFERENCES submissions(id),
    CONSTRAINT fk_audit_user FOREIGN KEY (changed_by_user_id) REFERENCES users(id),
    INDEX idx_audit_submission_created (submission_id, created_at DESC)
);
```

### 6.2. Thiết kế Backend Service Layer
1. **Entity**: Tạo `GradeAuditLog.java` map với bảng `grade_audit_logs`.
2. **Repository**: `GradeAuditLogRepository.java` với method `findBySubmission_IdOrderByCreatedAtDesc(String submissionId)`.
3. **Mở rộng DTO `SaveScoringPayload.java`**:
   ```java
   public class SaveScoringPayload {
       private List<CriteriaScoreRequest> criteriaScores;
       private BigDecimal overrideTotalScore;
       private String reason; // Hỗ trợ nhận lý do giải trình
       private String instructorEvaluationComments;
   }
   ```
4. **Cơ chế ghi log trong `TeacherScoringService.java`**:
   - Trước khi cập nhật submission, đọc `oldTotalScore = submission.getTotalScore()`, `oldOverrideScore = submission.getOverrideScore()`, `oldEffectiveScore = effectiveScore`.
   - Sau khi tính toán `newTotalScore` và `newOverrideScore`:
     - Nếu `oldTotalScore == null && oldOverrideScore == null`: action là `INITIAL_GRADING`.
     - Nếu có thay đổi `overrideScore`: action là `MANUAL_OVERRIDE`.
     - Nếu chỉ thay đổi điểm tiêu chí: action là `RUBRIC_UPDATE`.
   - Lưu một bản ghi `GradeAuditLog` mới trong cùng một transaction `@Transactional`.
5. **Validation bổ sung**:
   - Kiểm tra `overrideTotalScore`: nếu khác `null`, bắt buộc `>= 0` và `<= 100.0`.
   - Nếu `action == MANUAL_OVERRIDE`, bắt buộc `reason` không được để trống (`@NotBlank`).
6. **API Endpoint mới (Tách biệt hoàn toàn, không vỡ API cũ)**:
   - `GET /api/teacher/submissions/{id}/audit-trail`: Trả về danh sách lịch sử sửa điểm gồm thời gian, người sửa (tên/email/role), điểm cũ, điểm mới và lý do.

### 6.3. Thiết kế Frontend Layer
1. **Cập nhật `types/index.ts`**:
   - Bổ sung `overrideTotalScore?: number` vào `ScoringDetail`.
   - Tạo interface `GradeAuditLogEntry`.
2. **Sửa lỗi hiển thị**:
   - Tại `student-result-view.tsx`: Hiển thị `data.effectiveScore ?? data.totalScore`.
   - Tại `TeacherScoringForm.tsx`: Khởi tạo `overrideTotalScore: initialData.overrideTotalScore ?? initialData.totalScore`.
3. **Hiển thị Audit Trail Component**:
   - Thêm tab/card "Lịch sử điều chỉnh điểm" trong `/teacher/scoring/[id]` và `/teacher/submissions/[id]`.
   - Hiển thị timeline các lần chỉnh sửa điểm minh bạch.

---

## 7. Danh Sách Quyết Định Cần PO / Nhóm Xác Nhận Trước Khi Triển Khai

Trước khi bắt tay vào triển khai code của Grade Audit Trail, các quyết định sau đây cần được chốt rõ:

| STT | Vấn Đề Quyết Định | Lựa Chọn Đề Xuất | Tác Động Nghiệp Vụ |
| :---: | :--- | :--- | :--- |
| **Q1** | **Bắt buộc nhập lý do khi Override điểm?** | **BẮT BUỘC** (Tối thiểu 10 ký tự) | Tránh việc giảng viên tùy tiện đè điểm mà không có căn cứ giải trình. |
| **Q2** | **Bắt buộc nhập lý do khi sửa điểm Rubric sau lần chấm đầu?** | **BẮT BUỘC** nếu bài đã ở trạng thái `GRADED` | Đảm bảo mọi thay đổi sau khi đã công bố điểm đều được giải thích rõ. |
| **Q3** | **Sinh viên có được xem Audit Trail không?** | **CHỈ XEM THỜI GIAN & LÝ DO TÓM TẮT**, không xem danh tính giảng viên cụ thể nếu có hội đồng chấm | Bảo vệ danh tính giảng viên nếu chấm ẩn danh, nhưng đảm bảo sinh viên biết bài mình đã được điều chỉnh điểm sau khiếu nại. |
| **Q4** | **Quyền hạn của Admin đối với Audit Trail?** | Admin có quyền xem Audit Trail của tất cả bài nộp trong hệ thống | Phục vụ công tác thanh tra học thuật và giải quyết khiếu nại cấp khoa/trường. |
| **Q5** | **Khóa sửa điểm (Grading Lockout Period)?** | Khóa sửa điểm sau 7 ngày kể từ khi kết thúc kỳ thi hoặc sau khi đã chốt sổ điểm đợt 1 | Tránh sửa điểm tùy tiện sau khi đã nộp bảng điểm lên phòng đào tạo. |

---

## 8. Kết Luận
- Hiện trạng hệ thống CodeAssess **đã có luồng tính điểm Rubric và đè điểm Override hoạt động ổn định ở mức cơ bản**, phân quyền giáo viên theo lớp học chặt chẽ.
- Tuy nhiên, hệ thống **hoàn toàn chưa có cơ chế Audit Trail**: điểm số cũ bị ghi đè trực tiếp, lý do giảng viên nhập trên giao diện bị thất thoát do Backend DTO chưa đón nhận, và phía sinh viên còn tồn tại lỗi hiển thị lệch giữa `totalScore` và `effectiveScore`.
- Đề xuất kiến trúc bổ sung bảng `grade_audit_logs` cùng endpoint độc lập là giải pháp tối ưu nhất: bảo toàn 100% tính toàn vẹn của dữ liệu hiện tại, không gây breaking changes và đáp ứng đầy đủ tiêu chuẩn kiểm định học thuật.
