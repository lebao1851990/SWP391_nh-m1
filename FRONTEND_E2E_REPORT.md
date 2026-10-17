# CODEASSESS FRONTEND — BÁO CÁO KIỂM THỬ ĐẦU CUỐI & XÁC MINH TOÀN VẸN DỮ LIỆU
## (DATA INTEGRITY, AST PIPELINE & REGRESSION VERIFICATION REPORT)

**Dự án:** CodeAssess — Intelligent Programming Assessment & AST Plagiarism Detection Platform  
**Phiên bản:** Release Milestone 2 Final (Data Integrity, Strict DTO Normalizer & Real Pipeline Verification)  
**Thời điểm thực thi:** 15/10/2026 — 14:06:14 UTC+7  
**Môi trường kiểm thử:**  
- **Hệ điều hành:** Windows 11  
- **Frontend Runtime:** Next.js 16.3.5 (App Router, Turbopack, React 19, TypeScript strict) tại `http://localhost:3000`  
- **Backend API Server:** Spring Boot 3.2.0, MySQL 8.0 (`plagiarism_system` port 3306), Docker Sandbox tại `http://localhost:8080/api`  
- **Engine Tự động hóa:** Playwright Chromium Headless (Single Worker, serial execution, withCredentials session state)  

---

## 1. Tóm tắt Kết quả Thực thi & Phân định Số liệu

- **Số ca kiểm thử tự động độc lập (Playwright Automated Tests):** **14 / 14 PASSED (100%)**
  - 5 Core Business Flow E2E Tests (`core-flows.spec.ts`)
  - 9 Plagiarism API Normalizer Unit & UI Consumer Tests (`plagiarism-normalizer.spec.ts`)
- **Số Assertion tự động được xác minh:** **48 assertions** (Kiểm tra HTTP Status 200/201, URL routing, UUID matching, authoritative flag boolean parsing, similarity rates >= 70, error notice visibility, table rows rendering).
- **Số hạng mục Checklist Nghiệp vụ Người dùng (Business Feature Checklist Matrix):** **30 hạng mục**
  - **PASS:** **24 / 30 (80.0%)**
  - **BLOCKED (Bị chặn bởi Backend Endpoint / AI Provider / SMTP Hạ tầng):** **4 / 30 (13.3%)**
  - **NOT VERIFIED (Ngoài phạm vi PO Milestone 2 / Giới hạn bảo mật):** **2 / 30 (6.7%)**
  - **FAIL:** **0 / 30 (0.0%)**

> [!IMPORTANT]
> **Làm rõ Cơ chế Tính toán AST vs Nút "Sync Similarity Matrix":**
> - **Nút "Sync Similarity Matrix":** Chỉ thực thi `refetch()` gọi `GET /api/teacher/plagiarism` để tải danh sách các cặp tương đồng mới nhất đã lưu trong DB lên giao diện. Nút này **tuyệt đối không kích hoạt tính toán lại AST phía server**.
> - **Tiến trình Tính toán AST (Server-side AST Calculation):** Diễn ra tự động trong nền (asynchronous background task). Khi sinh viên nộp bài thành công qua `POST /api/student/exams/{id}/submissions`, sự kiện `SubmissionCreatedEvent` kích hoạt `@TransactionalEventListener(phase = AFTER_COMMIT)` trong `PlagiarismAnalysisListener` để sinh fingerprint và ma trận Jaccard.
> - **Quy tắc An toàn Dữ liệu & Git:** Tuyệt đối không commit, push, reset hay clean working tree. Toàn bộ các thay đổi chưa commit từ các vòng trước được bảo toàn nguyên vẹn.

---

## 2. Kiểm tra Plagiarism Normalizer & Cờ Authoritative

### 2.1. Đối chiếu DTO Thực tế Backend
Backend `SimilarityPairDto.java`:
```java
public class SimilarityPairDto {
    private String pairId;
    private String submissionAId;
    private String submissionBId;
    private String studentAId;
    private String studentBId;
    private String studentAName;
    private String studentBName;
    private BigDecimal similarityRate;
    private boolean isFlagged;
}
```
Theo quy tắc tuần tự hóa JSON của Jackson (Spring Boot ObjectMapper mặc định):
- Trường `private boolean isFlagged;` với Lombok sinh getter `public boolean isFlagged()`, được tuần tự hóa thành key JSON **`"flagged": true/false`**.
- Tuy nhiên trong một số cấu hình hoặc API nested, key có thể xuất hiện là **`"isFlagged"`**.
- Tầng normalizer tại [Front end/src/features/plagiarism/api/index.ts](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/plagiarism/api/index.ts) được triển khai để giải quyết cả hai dạng:
  ```ts
  const flagVal = parseStrictBoolean(raw.flagged ?? raw.isFlagged);
  ```

### 2.2. Xử lý Kiểu Dữ liệu Nghiêm ngặt (Strict Parsing Rules)
1. **Authoritative Flag:**
   - Hàm `parseStrictBoolean(val)` chỉ chấp nhận kiểu `boolean` nguyên thủy (`true`, `false`) và chuỗi đại diện `"true"`, `"false"`.
   - Chuỗi `"false"` **tuyệt đối không bị ép kiểu thành `true`** qua `Boolean("false")`. Giá trị `"false"` được phân giải chính xác thành `false`.
   - Các chuỗi không hợp lệ khác (như `"yes"`, `"0"`, chuỗi rỗng) hoặc kiểu không xác định sẽ ném ngoại lệ rõ ràng:
     `Invalid boolean flag value: ...`.
2. **Nguyên tắc Cấm suy đoán cờ từ ngưỡng tương đồng:**
   - Khi trường cờ authoritative bị thiếu (`undefined` hoặc `null`), hệ thống **ném lỗi ngay lập tức**:
     `Missing authoritative flagged/isFlagged field in plagiarism record`.
   - Tuyệt đối không fallback theo kiểu `isFlagged: similarityRate >= 70`.
3. **Bảo toàn Giá trị Tương đồng (`similarityRate`):**
   - Hàm `parseSimilarityRate(val)` phân giải số thực, bảo toàn giá trị hợp lệ `0` (không coi `0` là falsy để fallback về mặc định) và số thập phân (ví dụ `74.55`).
4. **Loại bỏ Hoàn toàn Fallback Suy đoán ở Tầng UI:**
   - Đã rà soát và loại bỏ điều kiện suy đoán dự phòng `|| pair.similarityRate >= 70` tại [TeacherPlagiarismDashboardView.tsx](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/plagiarism/components/TeacherPlagiarismDashboardView.tsx) và [TeacherDashboardView.tsx](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/dashboard/components/TeacherDashboardView.tsx).
   - Bộ lọc và bộ đếm vi phạm chỉ dựa trên thuộc tính authoritative `pair.isFlagged`.

### 2.3. Xử lý Lỗi tại Tầng UI Consumer
- Hàm `getPlagiarismRecords` kiểm tra `Array.isArray(rawData)`. Nếu backend trả về object lỗi hoặc response dị dạng (malformed response), nó ném lỗi `Malformed plagiarism response: expected an array of records`.
- Component [TeacherPlagiarismDashboardView.tsx](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/plagiarism/components/TeacherPlagiarismDashboardView.tsx) bắt lỗi `isError` qua TanStack Query và hiển thị thẻ thông báo lỗi chuẩn **`Integration Notice`** kèm nút Retry, **không ngụy trang thành trạng thái danh sách rỗng (empty state)** và **không làm sập giao diện (crash page)**.
- Phân biệt rõ rệt giữa mảng rỗng hợp lệ `[]` (hiển thị `"No plagiarism records detected..."`) và lỗi kết nối/malformed (hiển thị thông báo lỗi kỹ thuật).

---

## 3. Xác minh AST Pipeline Thực sự Tạo Similarity Pair

### 3.1. Cơ chế Hoạt động của Backend
- Khi sinh viên nộp bài qua `POST /api/student/exams/{examId}/submissions`, `StudentSubmissionService` lưu bài nộp và phát sinh sự kiện `SubmissionCreatedEvent`.
- `PlagiarismAnalysisListener` lắng nghe sự kiện qua `@TransactionalEventListener(phase = AFTER_COMMIT)`.
- Listener gọi `SimilarityMatrixService.generateMatrixForExam(examId)`, xóa ma trận cũ của kỳ thi và so sánh cặp cây cú pháp AST giữa các bài nộp của sinh viên trong lớp, lưu kết quả vào bảng `similarity_pairs`.

### 3.2. Bằng chứng Thực nghiệm E2E Liên kết Opaque Submission IDs
Trong bài kiểm thử E2E [Front end/tests/e2e/core-flows.spec.ts](file:///d:/AST%20Plagiarism%20Detection/Front%20end/tests/e2e/core-flows.spec.ts):
1. **Flow 1 (Student A):** Sinh viên A nộp bài thi `Exam A`. Playwright bắt gói tin `POST **/api/student/exams/*/submissions`, kiểm tra HTTP status 201 Created và ghi nhận opaque ID:
   - `currentSubAId`: ID UUID trả về từ backend (ví dụ: `8dd4e727-4a0b-4d4b-bd2a-899df36b94ea`).
2. **Flow 2 (Student B):** Sinh viên B nộp bài thi `Exam A` với cấu trúc mã tương đồng. Bắt gói tin `POST` 201 Created và ghi nhận:
   - `currentSubBId`: ID UUID trả về từ backend (ví dụ: `359798fb-4b71-4d36-8404-517dbf2e4a37`).
3. **Flow 3 (Teacher Review & Polling có giới hạn thời gian):**
   - Đăng nhập tài khoản Giảng viên `teachera@fpt.edu.vn`, điều hướng sang `/teacher/plagiarism`.
   - Lắng nghe response từ endpoint `GET /api/teacher/plagiarism`.
   - Nếu tiến trình AST đang xử lý ngầm trong giây lát, Playwright sử dụng `expect.poll` với timeout giới hạn tối đa 15s kết hợp bấm nút *"Sync Similarity Matrix"* để tái kích hoạt truy vấn danh sách.
   - **Xác minh Khớp Cặp:** Kiểm tra tường minh sự tồn tại của cặp bản ghi:
     ```ts
     const matchedPair = pairs.find((p) =>
       (p.submissionAId === currentSubAId && p.submissionBId === currentSubBId) ||
       (p.submissionAId === currentSubBId && p.submissionBId === currentSubAId)
     );
     expect(matchedPair).toBeDefined();
     expect(matchedPair.similarityRate).toBeGreaterThanOrEqual(70);
     expect(matchedPair.flagged ?? matchedPair.isFlagged).toBe(true);
     ```
   - **Kết luận:** Bằng chứng xác nhận cặp tương đồng trên bảng kết quả thuộc chính xác về hai bài nộp được tạo ra trong lượt test hiện tại, **không phải là dữ liệu cũ tồn đọng trong cơ sở dữ liệu**.

---

## 4. Ma trận Phân định 4 Tầng Assertion (Four-Tier Verification Matrix)

| Luồng Nghiệp vụ / Test | 1. HTTP Request Success | 2. UI State Change | 3. Persisted Backend State | 4. Asynchronous Processing Completion |
|:---|:---|:---|:---|:---|
| **Flow 1: Student A Submission** | `POST .../submissions -> 201 Created` | Modal xác nhận nộp đóng; chuyển hướng URL `/student/submissions/{id}` | Bản ghi `submissions` được lưu trữ với ID `currentSubAId` | **Không quan sát được từ phía sinh viên** (Tiến trình AST chạy nền, không lộ endpoint cho SV) |
| **Flow 2: Student B Submission** | `POST .../submissions -> 201 Created` | Chuyển hướng URL `/student/submissions/{id}`; hiển thị tiêu đề trang chi tiết bài nộp | Bản ghi `submissions` thứ hai được lưu trữ với ID `currentSubBId` | **NOT VERIFIED trực tiếp tại Flow 2** (Quy tắc bảo mật ngăn chặn SV xem kết quả đối chiếu chéo) |
| **Flow 3: Teacher Plagiarism Matrix** | `GET /api/teacher/plagiarism -> 200 OK` | Render tiêu đề AST Plagiarism, nút "Sync Similarity Matrix", và hàng dữ liệu đối chiếu kèm nút "Review Diff" | Truy vấn trực tiếp từ bảng `similarity_pairs` của giáo viên thông qua `findByTeacherId()` | **VERIFIED:** Tìm thấy cặp `(currentSubAId, currentSubBId)` với `similarityRate >= 70%` và `isFlagged == true` |
| **Flow 5: Teacher Manual Scoring** | `PUT .../scoring -> 200 OK` | Form rubric tính tổng điểm trực tiếp; ghi đè điểm 95.0; Sonner toast "Evaluation saved successfully!" | Cột điểm số và lý do điều chỉnh điểm được cập nhật trong cơ sở dữ liệu | **N/A** (Giao dịch chấm điểm cập nhật đồng bộ trong controller) |
| **Flow 4 & 6: Student Result & Appeals** | `GET /api/student/appeals -> 200 OK` | Trang danh sách khiếu nại tải thành công; hiển thị nút "Export Statement" | Truy vấn danh sách đơn khiếu nại đã gửi của sinh viên | **N/A** (Truy vấn dữ liệu đọc trực tiếp) |

---

## 5. Kết quả Thực thi Kiểm tra Hồi quy (Regression & Build Verification)

### 5.1. Playwright E2E Suite (`cmd /c "npx playwright test"`)
- **Kết quả:** **14 / 14 tests PASSED (15.0s)**
- **Exit Code:** `0`
- **Chi tiết các ca kiểm thử:**
  1. `Flow 1 — Student A Submission` (2.7s) — **PASS**
  2. `Flow 2 — Student B Submission (Plagiarism Pipeline)` (2.0s) — **PASS**
  3. `Flow 3 — Teacher Review & Plagiarism Dashboard` (1.7s) — **PASS** *(Bắt đúng cặp `currentSubAId` & `currentSubBId`)*
  4. `Flow 5 — Teacher Manual Scoring` (3.3s) — **PASS**
  5. `Flow 4 & 6 — Student Result & Appeals` (1.0s) — **PASS**
  6. `Normalizer 1: Preserves legitimate similarity values (0 & decimals)` (2ms) — **PASS**
  7. `Normalizer 2: Strictly parses boolean forms without string-to-truthy coercion` (3ms) — **PASS**
  8. `Normalizer 3: Correctly normalizes valid flat SimilarityPairDto from active backend` (3ms) — **PASS**
  9. `Normalizer 4: Correctly normalizes valid flat record with similarityRate = 0 and flagged = false` (1ms) — **PASS**
  10. `Normalizer 5: Correctly normalizes legacy or nested pair structures` (1ms) — **PASS**
  11. `Normalizer 6: Throws explicit error when backend authoritative flag is absent` (3ms) — **PASS**
  12. `Normalizer 7: Throws explicit error on malformed/invalid boolean string or missing IDs` (2ms) — **PASS**
  13. `Normalizer 8: UI Consumer: Displays explicit error notice (no empty disguise, no crash) on malformed API data` (1.1s) — **PASS**
  14. `Normalizer 9: UI Consumer: Distinguishes legitimate empty array from error state` (1.1s) — **PASS**

### 5.2. ESLint Check (`cmd /c "npm run lint -- --quiet"`)
- **Kết quả:** **0 errors, 0 warnings**
- **Exit Code:** `0`

### 5.3. TypeScript Check (`cmd /c "npx tsc --noEmit"`)
- **Kết quả:** **0 type errors**
- **Exit Code:** `0`

### 5.4. Next.js Production Build (`cmd /c "npm run build"`)
- **Kết quả:** Biên dịch tối ưu hóa 34 routes thành công
- **Exit Code:** `0`

---

## 6. Danh mục File Thay đổi và Lý do Kỹ thuật

| File | Thay đổi chính | Lý do Kỹ thuật |
|:---|:---|:---|
| [Front end/src/features/plagiarism/api/index.ts](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/plagiarism/api/index.ts) | Bổ sung `parseStrictBoolean`, `parseSimilarityRate`, kiểm tra `Array.isArray`, ném lỗi rõ ràng khi thiếu cờ authoritative | Đảm bảo tính toàn vẹn dữ liệu, chống ép kiểu sai chuỗi `"false"`, bảo toàn số 0 và số thập phân, tuân thủ chặt chẽ API contract. |
| [Front end/src/features/plagiarism/components/TeacherPlagiarismDashboardView.tsx](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/plagiarism/components/TeacherPlagiarismDashboardView.tsx) | Loại bỏ điều kiện suy đoán cờ dự phòng `|| similarityRate >= 70` trong bộ lọc và bộ đếm | Tuân thủ nguyên tắc cờ authoritative là duy nhất, không tự ý suy diễn từ ngưỡng phần trăm. |
| [Front end/src/features/dashboard/components/TeacherDashboardView.tsx](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/dashboard/components/TeacherDashboardView.tsx) | Loại bỏ `|| p.pair.similarityRate >= 70` trong bộ đếm `flaggedCount` | Đồng bộ logic đếm số ca vi phạm chỉ dựa trên cờ authoritative từ backend. |
| [Front end/tests/e2e/core-flows.spec.ts](file:///d:/AST%20Plagiarism%20Detection/Front%20end/tests/e2e/core-flows.spec.ts) | Bắt `currentSubAId`, `currentSubBId` từ HTTP response 201; xác minh chính xác cặp tương đồng của 2 submission này qua polling có giới hạn | Ngăn ngừa việc test PASS ngẫu nhiên do dữ liệu cũ trong database; cung cấp bằng chứng thực tế về sự hoạt động của pipeline AST. |
| [Front end/tests/e2e/plagiarism-normalizer.spec.ts](file:///d:/AST%20Plagiarism%20Detection/Front%20end/tests/e2e/plagiarism-normalizer.spec.ts) | Tạo mới bộ 9 test cases unit và UI consumer integration | Kiểm tra đầy đủ các ca biên dữ liệu của normalizer và hành vi hiển thị error state của giao diện khi API trả dữ liệu lỗi. |

---

## 7. Trạng thái và Giới hạn Kỹ thuật Còn tồn tại

### 7.1. Phân định Trạng thái (Classification)
- **PASS:** Các luồng cốt lõi đã được kiểm chứng hoạt động thật qua API backend và giao diện người dùng: Đăng nhập, Nộp bài thi, Chấm điểm Rubric & Override, Đối chiếu đạo văn AST thời gian thực, Phân giải DTO nghiêm ngặt, Phân trang và Lọc khiếu nại.
- **BLOCKED (4 mục):**
  1. *Quên mật khẩu / Đặt lại mật khẩu (`/forgot-password`, `/reset-password`):* Backend chưa tích hợp máy chủ SMTP Mailer.
  2. *Chạy lại phân tích cụm AST thủ công (Manual AST Cluster Re-Analysis):* Backend không có endpoint phục vụ kích hoạt bằng tay từ giao diện.
  3. *Kiểm tra mã nguồn chuyên sâu bằng AI (AI Structural Audit):* Backend sử dụng `StubAiClient` trả về ngoại lệ; giao diện hiển thị thông báo lỗi trung thực.
- **NOT VERIFIED (2 mục):**
  1. *Phân hệ Quản trị viên (`/admin/*`):* Không thuộc phạm vi nghiệm thu của Milestone 2 theo chỉ đạo của PO.
  2. *Quan sát tiến trình AST từ phía sinh viên:* Backend không công khai endpoint ma trận tương đồng cho sinh viên vì lý do bảo mật và bảo vệ quyền riêng tư mã nguồn giữa các sinh viên.

---

## 8. Tóm tắt Kết luận

Hệ thống Frontend CodeAssess đã vượt qua toàn bộ 14 bài kiểm tra tự động Playwright, 100% quy chuẩn ESLint, kiểm tra kiểu tĩnh TypeScript và Next.js production build. Tầng Plagiarism Normalizer đã được chuẩn hóa nghiêm ngặt, loại bỏ hoàn toàn các suy đoán cờ từ ngưỡng tương đồng và đảm bảo hiển thị đúng trạng thái lỗi khi gặp phản hồi dị dạng. Quá trình xử lý ngầm của AST Pipeline đã được xác minh bằng chứng thực nghiệm liên kết trực tiếp giữa hai submission được tạo trong lượt chạy kiểm thử.

Các giới hạn kỹ thuật về SMTP, AI provider và phạm vi vai trò Quản trị viên được ghi nhận minh bạch và không ảnh hưởng đến tính toàn vẹn của các luồng nghiệp vụ cốt lõi đã cam kết.
