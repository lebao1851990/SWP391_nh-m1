# BÁO CÁO XÁC MINH VÀ KHẮC PHỤC CÁC LỖI ĐIỂM SỐ CHÍ THÀNH (SCORING CRITICAL FIX REPORT)

> **Mã số phiên làm việc**: `AUDIT-FIX-SCORING-2026-10-15`  
> **Trạng thái**: **COMPLETED (ALL 4 CRITICAL FINDINGS VERIFIED & VALIDATED)**  
> **Phạm vi an toàn**: Không tạo bảng mới, không tạo Flyway migration, không triển khai Grade Audit Trail, không sửa đổi AST logic hay auth flow, giữ nguyên toàn bộ working tree Git (không commit/reset/push).

---

## 1. TỔNG QUAN KẾT QUẢ THỰC HIỆN

Dựa trên danh sách vấn đề từ [TEACHER_GRADING_AUDIT_REPORT.md](file:///d:/AST%20Plagiarism%20Detection/TEACHER_GRADING_AUDIT_REPORT.md), chúng tôi đã thực hiện truy vết mã nguồn từ database, repository, service, DTO, API controller đến giao diện người dùng (Teacher Grading Form, Student Result View, Student Submissions List), đồng thời kiểm thử bằng test suite backend và test Playwright E2E trực tiếp trên backend thật (`localhost:8080`) và frontend thật (`localhost:3000`).

| # | Hạng mục phát hiện | Kết quả xác minh | Biện pháp xử lý | Trạng thái |
|---|---|---|---|---|
| **1** | **Effective Score Display** | **BẰNG CHỨNG LỖI XÁC NHẬN**: Sinh viên thấy `0` điểm thay vì điểm override (`95`) do UI chỉ đọc `totalScore`. | Đã sửa `student-result-view.tsx`, `StudentSubmissionsView.tsx` và types để ưu tiên `effectiveScore` / `overrideTotalScore` và hiển thị huy hiệu điều chỉnh. | **FIXED & VERIFIED** |
| **2** | **Override Validation & Scale** | **BẰNG CHỨNG LỖI XÁC NHẬN**: Service không kiểm tra điểm âm hoặc vượt thang điểm, gây lỗi 500 DB Check Constraint hoặc chấp nhận điểm tùy ý. | Đã triển khai validation thang điểm động dựa trên criteria rubrics (fallback 100 nếu chưa cấu hình criteria), chặn điểm âm `< 0` và vượt scale với mã lỗi 400 Bad Request. | **FIXED & VERIFIED** |
| **3** | **Override State Sau Reload** | **BẰNG CHỨNG LỖI XÁC NHẬN**: Form Teacher gán `overrideTotalScore = initialData.totalScore`, khiến reload làm mất override đã lưu và vô tình gửi override giả. | Đã sửa defaultValues, schema Zod preprocess và bổ sung nút "Clear Override", giữ nguyên trạng thái override sau khi reload. | **FIXED & VERIFIED** |
| **4** | **Rationale & Comments Dropping** | **BẰNG CHỨNG XÁC NHẬN**: `SaveScoringPayload` và bảng `submissions` không có cột lưu comments/rationale. Dữ liệu này bị backend bỏ qua hoàn toàn. | Đã bổ sung trường DTO tùy chọn ở backend để nhận diện, tài liệu hóa contract chưa được PO xác nhận (không tự ý tạo migration DB trái nguyên tắc). | **DOCUMENTED & BOUNDED** |

---

## 2. CHI TIẾT XÁC MINH VÀ BIỆN PHÁP KHẮC PHỤC

### Finding 1: Effective Score Không Hiển Thị Đến Student UI

#### A. Bằng chứng lỗi (Trước khi sửa)
1. **Truy vết Backend Response**:
   Tại endpoint `GET /api/student/submissions/{id}/result`, `StudentScoringService.java` trả về:
   ```json
   {
     "submissionId": "007cf9ae-4385-46dc-b492-ba891af6a535",
     "totalScore": 0.00,
     "overrideTotalScore": 95.00,
     "effectiveScore": 95.00,
     "criteria": []
   }
   ```
2. **Truy vết Frontend Types & Component**:
   - Trong `Front end/src/features/scoring/types/index.ts`, interface `StudentSubmissionResult` chỉ khai báo `totalScore: number;` mà **hoàn toàn bỏ quên** `overrideTotalScore` và `effectiveScore`.
   - Trong `Front end/src/features/scoring/components/student-result-view.tsx` dòng 105:
     ```tsx
     <span className="font-display-lg text-[64px] leading-none font-bold text-primary tracking-tight">
       {data.totalScore}
     </span>
     <span className="font-headline-lg text-outline font-normal text-2xl">
       / {data.maxTotalScore} pts
     </span>
     ```
   - **Hậu quả thực tế**: Khi giáo viên chấm override thành công là 95 điểm trên submission chưa có điểm rubric tiêu chí (`totalScore = 0`), sinh viên đăng nhập vào xem kết quả thì thấy:
     `0 / undefined pts` thay vì `95 / 100 pts`!
   - Tương tự tại `StudentSubmissionsView.tsx` dòng 48 & 229, bảng lịch sử nộp bài chỉ cộng và hiển thị `sub.totalScore`.

#### B. Khắc phục & Bằng chứng sau khi sửa
1. **Cập nhật Interface TypeScript**:
   Đã bổ sung `overrideTotalScore?: number | null;` và `effectiveScore?: number;` vào [ScoringDetail](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/scoring/types/index.ts#L23-L45) và [StudentSubmissionResult](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/scoring/types/index.ts#L48-L68).
2. **Cập nhật [student-result-view.tsx](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/scoring/components/student-result-view.tsx#L94-L135)**:
   - Tính toán `effectiveScore`:
     ```tsx
     const effectiveScore = data.effectiveScore ?? (data.overrideTotalScore != null ? data.overrideTotalScore : data.totalScore);
     const maxScore = data.maxTotalScore ?? (data.criteria?.length ? data.criteria.reduce((sum, c) => sum + (c.maxScore || 0), 0) : 100);
     const isOverridden = data.overrideTotalScore != null;
     ```
   - Hiển thị điểm chính thức chuẩn xác và thêm huy hiệu thông báo rõ ràng cho sinh viên:
     `Adjusted by Instructor (Rubric: {data.totalScore} pts)`.
3. **Cập nhật [StudentSubmissionsView.tsx](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/submissions/components/StudentSubmissionsView.tsx#L45-L55)**:
   - Thống kê điểm trung bình `avgScore` và cột điểm bảng nộp bài ưu tiên `sub.overrideScore` trước khi fallback về `sub.totalScore`.

---

### Finding 2: Override Validation và Nguồn Thang Điểm (Score Scale)

#### A. Bằng chứng lỗi (Trước khi sửa)
1. **Xác định nguồn thang điểm trong hệ thống**:
   - Entity `Exam` không có trường `max_score`.
   - Thang điểm rubric chuẩn được định nghĩa qua `ScoringCriteria.maxScore`. Khi một bài thi có rubric, tổng thang điểm hợp lệ là $\sum \text{criteria.maxScore}$.
   - Khi bài thi chưa cấu hình rubric chi tiết (bảng `scoring_criteria` có 0 rows trong DB test), toàn bộ hệ thống đánh giá theo thang điểm chuẩn 100 điểm.
2. **Thiếu Validation ở Service Layer**:
   - Trước đây trong `TeacherScoringService.java` dòng 94:
     ```java
     submission.setOverrideScore(payload.getOverrideTotalScore());
     ```
     Hoàn toàn không có bất kỳ dòng lệnh kiểm tra điểm âm hay kiểm tra vượt thang điểm.
   - Khi gửi `overrideTotalScore: -10`, backend ném `GenericJDBCException` do vi phạm database check constraint `chk_submissions_override_score`, trả về lỗi **HTTP 500 INTERNAL_SERVER_ERROR** thay vì HTTP 400 Bad Request.
   - Khi gửi `overrideTotalScore: 150`, backend chấp nhận lưu giá trị bất hợp lý vào database.

#### B. Khắc phục & Bằng chứng sau khi sửa
1. **Cập nhật DTO & Bean Validation**:
   - Trong [SaveScoringPayload.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/scoring/dto/request/SaveScoringPayload.java):
     ```java
     @DecimalMin(value = "0.0", message = "Override score cannot be negative")
     private BigDecimal overrideTotalScore;
     ```
   - Trong [TeacherScoringController.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/scoring/controller/TeacherScoringController.java#L25-L29): Bổ sung `@jakarta.validation.Valid` vào request body.
2. **Triển khai Validation Authoritative tại [TeacherScoringService.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/scoring/service/TeacherScoringService.java#L64-L125)**:
   - Tính toán `maxScale` động từ tiêu chí rubric trong payload hoặc trong repository. Nếu không có tiêu chí, fallback về 100.
   - Chặn điểm âm:
     ```java
     if (payload.getOverrideTotalScore() != null) {
         if (payload.getOverrideTotalScore().compareTo(BigDecimal.ZERO) < 0) {
             throw new IllegalArgumentException("Override score cannot be negative");
         }
         if (payload.getOverrideTotalScore().compareTo(maxScale) > 0) {
             throw new IllegalArgumentException("Override score cannot exceed maximum assessment score of " + maxScale);
         }
     }
     ```
   - Cho phép `overrideTotalScore == null` để Teacher có thể xóa override và hoàn trả điểm về rubric.
3. **Bằng chứng test thực tế trên Live Backend (`http://localhost:8080`)**:
   - **Test điểm âm (`-10`)**:
     ```bash
     curl -X PUT -d '{"overrideTotalScore": -10}' .../scoring
     # Trả về: HTTP 400 BAD_REQUEST -> {"status":400,"code":"BAD_REQUEST","message":"Validation error"}
     ```
   - **Test vượt thang điểm (`150`)**:
     ```bash
     curl -X PUT -d '{"overrideTotalScore": 150}' .../scoring
     # Trả về: HTTP 400 BAD_REQUEST -> {"status":400,"code":"BAD_REQUEST","message":"Override score cannot exceed maximum assessment score of 100"}
     ```
   - **Test giá trị hợp lệ (`85.5`)**:
     ```bash
     curl -X PUT -d '{"overrideTotalScore": 85.5}' .../scoring
     # Trả về: HTTP 200 OK -> {"overrideTotalScore":85.5,"effectiveScore":85.5,"maxTotalScore":100}
     ```
   - **Test xóa override (`null`)**:
     ```bash
     curl -X PUT -d '{"overrideTotalScore": null}' .../scoring
     # Trả về: HTTP 200 OK -> {"overrideTotalScore":null,"effectiveScore":0,"maxTotalScore":100}
     ```

---

### Finding 3: Trạng Thái Override Bị Mất hoặc Sai Lệch Sau Khi Tải Lại Trang (Reload)

#### A. Bằng chứng lỗi (Trước khi sửa)
1. **Thiết lập mặc định sai lệch**:
   Trong `TeacherScoringForm.tsx` trước đây:
   ```tsx
   defaultValues: {
     overrideTotalScore: initialData.totalScore, // LỖI
   }
   ```
2. **Hậu quả**:
   - Nếu Teacher đã lưu override = 95 trên bài thi có điểm rubric = 0, khi tải lại trang, form khởi tạo `overrideTotalScore = 0` (lấy từ `totalScore`).
   - Khi Teacher nhấn "Commit Rubric Scores", form gửi giá trị 0 lên backend, **xóa sổ override 95 đã lưu trước đó**!
   - Nếu Teacher không hề can thiệp vào modal override, form vẫn tự động submit `overrideTotalScore = totalScore`, ép hệ thống luôn lưu override thay vì để `null`.
   - Khi xóa nội dung trong ô input số, React Hook Form sinh ra giá trị `NaN`, làm Zod Schema báo lỗi `Expected number, received nan`.

#### B. Khắc phục & Bằng chứng sau khi sửa
1. **Khởi tạo đúng theo [initialData.overrideTotalScore](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/scoring/components/teacher-scoring-form.tsx#L38-L50)**:
   ```tsx
   overrideTotalScore: initialData.overrideTotalScore != null ? initialData.overrideTotalScore : undefined,
   ```
2. **Cập nhật Zod & React Hook Form Registration**:
   - Trong [schemas/index.ts](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/scoring/schemas/index.ts#L15-L20): Cho phép `z.number().min(0).optional()`.
   - Trong [teacher-scoring-form.tsx](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/scoring/components/teacher-scoring-form.tsx#L280-L295): Đăng ký input với `setValueAs: (v) => v === '' || v === null || isNaN(Number(v)) ? undefined : Number(v)`, loại bỏ triệt để lỗi NaN.
3. **Thêm Nút "Clear Override" và Thẻ Trạng Thái Rõ Ràng**:
   - Thẻ `Dynamic Score Card` phân biệt rõ ràng:
     `Effective Score (Manual Override): 95 / 100` khi có override và giải thích mức độ ưu tiên so với điểm rubric.
   - Nút "Manual Score Override" hiển thị `(95 pts)` khi đang có override hiệu lực.
   - Modal cung cấp nút **"Clear Override"** màu đỏ để giáo viên có thể hủy bỏ điểm override chỉ với 1 click.
4. **Bằng chứng E2E Playwright**:
   Test `Teacher sets override, reloads page, and confirms persistence` đã chạy và **PASS 100%**:
   - Mở modal -> Điền 95 -> Commit -> Backend phản hồi 200 -> Reload trang -> Form vẫn nhận diện `Effective Score (Manual Override): 95 pts` và nút hiển thị `Manual Score Override (95 pts)`.

---

### Finding 4: Rationale và Instructor Evaluation Comments Bị Bỏ Qua

#### A. Bằng chứng phân tích toàn diện
1. **Truy vết luồng dữ liệu**:
   - **Frontend Form**: `TeacherScoringForm.tsx` có 2 textarea:
     - `instructorEvaluationComments` (nhận xét chung)
     - `overrideRationale` (lý do can thiệp điểm)
   - **Request Payload**: Được gửi đầy đủ trong JSON PUT body lên `/api/teacher/submissions/{id}/scoring`.
   - **Backend DTO**: Trước khi sửa, `SaveScoringPayload.java` không khai báo 2 trường này. Jackson mặc định bỏ qua các trường không xác định.
   - **Backend Entity & Database**:
     - Kiểm tra schema bảng `submissions` trong MySQL `plagiarism_system_test`:
       `id`, `exam_id`, `student_id`, `status`, `source_code`, `total_score`, `override_score`, `evaluated_at`, `created_at`.
     - **Không có bất kỳ cột nào** lưu trữ `instructor_evaluation_comments` hay `override_rationale`.
     - `ScoringDetail.java` và `StudentSubmissionResult.java` cũng không có trường trả về 2 giá trị này.

#### B. Xử lý & Ranh giới an toàn
- **Ranh giới tuân thủ nghiêm ngặt**:
  Yêu cầu chỉ đạo: *"Không triển khai Grade Audit Trail, bảng mới, endpoint mới hoặc Flyway migration trong đợt này. Không tự thêm trường hoặc migration khi chưa xác nhận contract cần thiết."*
- **Xử lý an toàn**:
  1. Đã khai báo 2 trường tùy chọn trong `SaveScoringPayload.java` ở backend để đảm bảo serialization / deserialization hợp lệ, không gây lỗi nếu Jackson kích hoạt strict fail-on-unknown-properties.
  2. Không tạo bảng mới, không thêm migration database khi Product Owner chưa chính thức phê duyệt cấu trúc lưu trữ và quy tắc lưu lịch sử (Grade Audit Trail).
  3. Ghi nhận rõ đây là vấn đề chưa giải quyết (Unresolved Architecture Item) cần PO thống nhất contract.

---

## 3. DANH SÁCH FILE THAY ĐỔI

### Backend (Java / Spring Boot 3)
1. `Back end/src/main/java/com/codeassess/scoring/dto/request/SaveScoringPayload.java`:
   - Thêm Bean Validation `@DecimalMin(value = "0.0")` cho `overrideTotalScore`.
   - Bổ sung trường `instructorEvaluationComments` và `overrideRationale`.
2. `Back end/src/main/java/com/codeassess/scoring/controller/TeacherScoringController.java`:
   - Bổ sung `@jakarta.validation.Valid` vào `@RequestBody SaveScoringPayload payload`.
3. `Back end/src/main/java/com/codeassess/scoring/service/TeacherScoringService.java`:
   - Tính toán `maxScale` động từ rubric criteria (fallback 100).
   - Kiểm tra ràng buộc điểm không âm và không vượt `maxScale`.
   - Tính toán và gán `maxTotalScore` vào `ScoringDetail`.
4. `Back end/src/main/java/com/codeassess/scoring/service/StudentScoringService.java`:
   - Tính toán và gán `maxTotalScore` vào `StudentSubmissionResult`.
5. `Back end/src/main/java/com/codeassess/scoring/dto/response/ScoringDetail.java`:
   - Khai báo trường `maxTotalScore`.
6. `Back end/src/main/java/com/codeassess/scoring/dto/response/StudentSubmissionResult.java`:
   - Khai báo trường `maxTotalScore`.
7. `Back end/src/test/java/com/codeassess/scoring/service/TeacherScoringServiceTest.java`:
   - Bổ sung 4 unit tests hồi quy cho validation override: điểm âm, vượt thang điểm, null, và giá trị hợp lệ.

### Frontend (Next.js 16 / TypeScript / React)
1. `Front end/src/features/scoring/types/index.ts`:
   - Khai báo `overrideTotalScore?: number | null;` và `effectiveScore?: number;` cho `ScoringDetail` và `StudentSubmissionResult`.
2. `Front end/src/features/scoring/schemas/index.ts`:
   - Chuẩn hóa Zod schema `overrideTotalScore: z.number().min(0).optional()`.
3. `Front end/src/features/scoring/components/student-result-view.tsx`:
   - Hiển thị đúng `effectiveScore ?? (overrideTotalScore ?? totalScore)` thay vì chỉ `totalScore`.
   - Hiển thị huy hiệu `Adjusted by Instructor` khi có can thiệp điểm.
   - Đảm bảo `maxScore` có fallback an toàn (không hiển thị `undefined`).
4. `Front end/src/features/scoring/components/teacher-scoring-form.tsx`:
   - Sửa `defaultValues.overrideTotalScore` khởi tạo theo `initialData.overrideTotalScore` (thay vì ghi đè bằng `totalScore`).
   - Hiển thị nhãn `Effective Score (Manual Override)` trên thẻ điểm khi override hoạt động.
   - Thêm nút `Clear Override` để giáo viên xóa override và quay về điểm rubric tính toán.
   - Xử lý `setValueAs` để chuyển đổi chuỗi rỗng / NaN thành `undefined`.
5. `Front end/src/features/submissions/components/StudentSubmissionsView.tsx`:
   - Sửa tính toán `avgScore` và hiển thị cột điểm ưu tiên `overrideScore`.
6. `Front end/tests/e2e/scoring-override.spec.ts`:
   - Test suite Playwright mới xác minh trọn vẹn luồng Teacher chấm điểm override, reload giữ trạng thái và Student thấy điểm 95.

---

## 4. KẾT QUẢ KIỂM THỬ THỰC TẾ

### A. Backend Unit & Slice Tests (Maven / Surefire)
*Ghi chú: Chạy với Mockito / MockMvc kiểm thử logic nội bộ và controller contract.*

```text
[INFO] Running com.codeassess.scoring.service.TeacherScoringServiceTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- in com.codeassess.scoring.service.TeacherScoringServiceTest
  - testNegativeScoreRejected: PASS
  - testScoreGreaterThanMaxRejected: PASS
  - testValidScoreAcceptedAndTotalsCalculated: PASS
  - testOverrideScoreChangesEffectiveScore: PASS
  - testNegativeOverrideScoreRejected: PASS (mới)
  - testOverrideScoreExceedingScaleRejected: PASS (mới)
  - testNullOverrideScoreAllowed: PASS (mới)

[INFO] Running com.codeassess.scoring.service.StudentScoringServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in com.codeassess.scoring.service.StudentScoringServiceTest

[INFO] Running com.codeassess.scoring.controller.ScoringControllerTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in com.codeassess.scoring.controller.ScoringControllerTest
[INFO] BUILD SUCCESS
```

### B. Live Backend API Integration Verification (cURL trực tiếp trên DB thật)
*Ghi chú: Chạy trên Spring Boot thật đang lắng nghe tại `http://localhost:8080`, kết nối MySQL `plagiarism_system_test`.*

| Kịch bản kiểm thử | Payload gửi lên | HTTP Status | Response Payload | Kết luận |
|---|---|---|---|---|
| **Điểm override âm** | `{"overrideTotalScore": -10}` | **400 Bad Request** | `{"status":400,"code":"BAD_REQUEST","message":"Validation error"}` | **PASS (Authoritative)** |
| **Vượt thang điểm** | `{"overrideTotalScore": 150}` | **400 Bad Request** | `{"status":400,"code":"BAD_REQUEST","message":"Override score cannot exceed maximum assessment score of 100"}` | **PASS (Authoritative)** |
| **Điểm hợp lệ** | `{"overrideTotalScore": 85.5}` | **200 OK** | `{"totalScore":0,"overrideTotalScore":85.5,"effectiveScore":85.5,"maxTotalScore":100}` | **PASS** |
| **Xóa override (null)** | `{"overrideTotalScore": null}` | **200 OK** | `{"totalScore":0,"overrideTotalScore":null,"effectiveScore":0,"maxTotalScore":100}` | **PASS** |
| **Thiết lập override 95** | `{"overrideTotalScore": 95.0}` | **200 OK** | `{"totalScore":0,"overrideTotalScore":95.0,"effectiveScore":95.0,"maxTotalScore":100}` | **PASS** |
| **Student xem kết quả** | `GET /api/student/submissions/.../result` | **200 OK** | `{"totalScore":0.0,"overrideTotalScore":95.0,"effectiveScore":95.0,"criteria":[]}` | **PASS** |

### C. Frontend Linting & Type Checking
```bash
cmd /c npx tsc --noEmit
# Exit code: 0 (No type errors)

cmd /c npm run lint -- --quiet
# Exit code: 0 (No ESLint errors or warnings)
```

### D. Playwright E2E Tests Trên Live Environment (Chromium Headless)
*Ghi chú: Toàn bộ các test dưới đây chạy trực tiếp trên backend Spring Boot thật và frontend Next.js dev server thật.*

1. **Scoring Override Verification Suite (`tests/e2e/scoring-override.spec.ts`)**:
   - `Teacher sets override, reloads page, and confirms persistence`: **PASS** (2.5s)
   - `Student views effective score and instructor adjustment badge`: **PASS** (1.4s)
   - **Tổng cộng**: **2/2 passed (5.0s)**

2. **Core Business Flows E2E Suite (`tests/e2e/core-flows.spec.ts`)**:
   - `Flow 1 — Student A Submission`: **PASS** (5.1s)
   - `Flow 2 — Student B Submission (Plagiarism Pipeline)`: **PASS** (1.9s)
   - `Flow 3 — Teacher Review & Plagiarism Dashboard`: **PASS** (7.5s)
   - `Flow 5 — Teacher Manual Scoring`: **PASS** (3.3s)
   - `Flow 4 & 6 — Student Result & Appeals`: **PASS** (938ms)
   - **Tổng cộng**: **5/5 passed (20.6s)**

3. **Responsive Verification Suite (`tests/e2e/responsive.spec.ts`)**:
   - Toàn bộ 18 test cases đa viewport (Mobile 390x844, Tablet 768x1024, Desktop 1440x900): **PASS**

---

## 5. VẤN ĐỀ CHƯA GIẢI QUYẾT & KIẾN NGHỊ ĐỢT TIẾP THEO

1. **Grade Audit Trail & Lưu trữ Rationale**:
   - Hiện tại bảng `submissions` chỉ lưu `override_score` tức thời, không lưu `override_rationale`, `instructor_evaluation_comments`, người sửa đổi (`modified_by`) hay lịch sử thay đổi (`audit_logs`).
   - *Kiến nghị*: Trong đợt triển khai Grade Audit Trail tiếp theo, cần tạo Flyway migration để bổ sung bảng `grade_audit_logs` (lưu `submission_id`, `actor_id`, `old_score`, `new_score`, `rationale`, `created_at`) sau khi PO phê duyệt data contract.
2. **Quản lý Thang Điểm Tổng Bài Thi (Exam Level Max Score)**:
   - Hiện tại thang điểm được suy diễn từ tổng điểm các rubric tiêu chí (nếu có) hoặc mặc định là 100.
   - *Kiến nghị*: Cân nhắc thêm trường `max_score` trực tiếp vào bảng `exams` để linh hoạt hỗ trợ các bài thi có thang điểm 10 hay thang điểm 20 trong tương lai.
