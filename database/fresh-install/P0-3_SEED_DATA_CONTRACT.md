# CODEASSESS PLATFORM — SEED DATA CONTRACT (PHASE P0-3 SPECIFICATION)

**Tác giả:** CodeAssess DevOps & Testing Team  
**Mục tiêu:** Đặc tả chuẩn mực hợp đồng dữ liệu thử nghiệm (Seed Data Contract) phục vụ thực thi ổn định, lặp lại (idempotent) cho 45 test cases thuộc hai Playwright projects `live` (43 tests) và `hybrid` (2 tests), đồng thời chuẩn bị dữ liệu chuẩn cho Black-box UAT trước Hội đồng.

---

## 1. Danh sách tài khoản kiểm thử bắt buộc (Core Test Accounts)

Toàn bộ tài khoản sử dụng chuẩn mã hóa mật khẩu BCrypt (Cost factor 10, tiêu chuẩn Spring Security). Khi chạy Playwright E2E suite, biến môi trường `E2E_TEST_PASSWORD` (hoặc các biến theo role `E2E_TEACHER_PASSWORD`, `E2E_STUDENT_PASSWORD`, `E2E_ADMIN_PASSWORD`) sẽ nạp mật khẩu giải mã tương ứng.

| STT | Email kiểm thử | Role | Họ và tên hiển thị | Mã định danh (`id`) | Mục đích sử dụng trong E2E |
| :---: | :--- | :---: | :--- | :--- | :--- |
| 1 | `teachera@fpt.edu.vn` | `TEACHER` | GV. Tran Thi Mai (Teacher A) | `usr-e2e-teacher-000000000001` | Dùng chung: `auth-guard-session`, `class-aware-exams`, `core-flows`, `grade-audit-history`, `scoring-override`, `responsive`, `ast-dashboard` |
| 2 | `teacher.test@codeassess.edu.vn` | `TEACHER` | GV. Nguyen Van Giang | `usr-e2e-teacher-000000000002` | Dùng cho: `plagiarism-normalizer` (hybrid UI consumer tests) |
| 3 | `studenta@fpt.edu.vn` | `STUDENT` | SV. Nguyen Van An (Student A) | `usr-e2e-student-000000000001` | Dùng cho: `auth-guard-session`, `class-aware-exams`, `core-flows` (Flow 1, 4, 6), `scoring-override`, `responsive` |
| 4 | `studentb@fpt.edu.vn` | `STUDENT` | SV. Tran Thi Binh (Student B) | `usr-e2e-student-000000000002` | Dùng cho: `core-flows` (Flow 2 - nộp bài trùng lặp tạo cặp đạo văn AST) |
| 5 | `student.test@codeassess.edu.vn` | `STUDENT` | SV. Le Quoc Cuong | `usr-e2e-student-000000000003` | Dùng cho: `student-plagiarism-appeal-link` |
| 6 | `admin@codeassess.edu.vn` | `ADMIN` | Quản trị viên CodeAssess E2E | `usr-e2e-admin-0000000000001` | Dự phòng cho Admin Portal và xác thực phân quyền RBAC |

---

## 2. Danh mục Lớp học & Phân bổ Sinh viên (Classes & Enrollment)

| Mã lớp (`id`) | Tên môn học | Mã môn (`code`) | Giảng viên phụ trách | Danh sách sinh viên ghi danh (`class_students`) |
| :--- | :--- | :--- | :--- | :--- |
| `cls-e2e-pro192-00000001` | Lập trình Hướng đối tượng Java | `PRO192-E2E-FA26` | `usr-e2e-teacher-000000000001` | `usr-e2e-student-000000000001`, `usr-e2e-student-000000000002`, `usr-e2e-student-000000000003` |
| `cls-e2e-csd201-00000002` | Cấu trúc Dữ liệu & Giải thuật | `CSD201-E2E-FA26` | `usr-e2e-teacher-000000000001` | `usr-e2e-student-000000000001` |

---

## 3. Danh mục Bài thi chuẩn hóa (Assessments & Exam Classes)

| Mã bài thi (`id`) | Tiêu đề (`title`) | Lớp gán (`exam_classes`) | Thời lượng | Trạng thái | Yêu cầu đặc thù E2E |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `exm-e2e-exam-a-00000001` | **`Exam A`** *(Bắt buộc đúng tên)* | `cls-e2e-pro192-00000001` | 90 phút | `ACTIVE` | Phục vụ `core-flows.spec.ts` (Student A & B tìm bài thi theo tiêu đề "Exam A" để nộp code) |
| `exm-e2e-pro192-midterm1` | `PRO192 Midterm Practical Assessment` | `cls-e2e-pro192-00000001`, `cls-e2e-csd201-00000002` | 120 phút | `ACTIVE` | Phục vụ `class-aware-exams.spec.ts` (kiểm tra phân bổ đa lớp & nút "Manage") |

---

## 4. Đặc tả Bài nộp cố định cho 2 Spec có Hardcoded UUID (Critical Invariants)

Hai bài test sau có chứa UUID tĩnh trong mã nguồn frontend E2E, bắt buộc phải có bản ghi tương ứng trong CSDL:

### A. Bài nộp cho `grade-audit-history.spec.ts`
- **Submission ID:** `040b61a8-3c7a-4ea2-aace-18a0ba2d9d32`
- **Exam ID:** `exm-e2e-pro192-midterm1`
- **Student ID:** `usr-e2e-student-000000000001` (`studenta@fpt.edu.vn`)
- **Trạng thái:** `COMPLETED`
- **Điểm:** `total_score = 80.00`, `override_score = NULL`, `auto_score = 80.00`
- **Bản ghi Lịch sử Chấm điểm (`grade_audit_logs`):**
  - Cần ít nhất 1 bản ghi lịch sử:
    - `id`: `log-e2e-grade-audit-00000001`
    - `submission_id`: `040b61a8-3c7a-4ea2-aace-18a0ba2d9d32`
    - `actor_id`: `usr-e2e-teacher-000000000001` (`teachera@fpt.edu.vn`)
    - `action_type`: `OVERRIDE_CLEARED`
    - `pre_rubric_score`: `80.00`, `post_rubric_score`: `80.00`
    - `pre_override_score`: `NULL`, `post_override_score`: `NULL` *(Để UI hiển thị pill "No override")*
    - `pre_effective_score`: `80.00`, `post_effective_score`: `80.00`
    - `rationale`: `Initial evaluation completed by rubric.`

### B. Bài nộp cho `scoring-override.spec.ts`
- **Submission ID:** `007cf9ae-4385-46dc-b492-ba891af6a535`
- **Exam ID:** `exm-e2e-pro192-midterm1`
- **Student ID:** `usr-e2e-student-000000000001` (`studenta@fpt.edu.vn`)
- **Trạng thái:** `COMPLETED`
- **Điểm số khởi tạo:**
  - `total_score = 0.00` (hoặc `rubric_total = 0.00`)
  - `override_score = NULL`
- **Hành vi kiểm thử:** Test 1 sẽ cập nhật `override_score = 95.00`. Test 2 kiểm tra sinh viên nhìn thấy huy hiệu `Adjusted by Instructor (Rubric: 0 pts)` và điểm hiệu lực `95 / 100 pts`.

---

## 5. Tiêu chí Chấm điểm Mẫu (`scoring_criteria`)

| ID tiêu chí | Tên tiêu chí | Mô tả | Điểm tối đa |
| :--- | :--- | :--- | :---: |
| `crit-e2e-compilation-01` | Compilation & Execution Integrity | Mã nguồn biên dịch thành công, không phát sinh lỗi bộ nhớ. | 3.00 |
| `crit-e2e-correctness-02` | Functional Correctness & Test Pass Rate | Vượt qua các bộ test case tự động. | 4.00 |
| `crit-e2e-structure-03` | Code Quality & Structural Modularity | Cấu trúc OOP sạch sẽ, xử lý ngoại lệ tốt. | 1.50 |
| `crit-e2e-efficiency-04` | Time & Space Efficiency | Thời gian thực thi và bộ nhớ nằm trong giới hạn cho phép. | 1.50 |

---

## 6. Chính Sách Mật Khẩu & Xác Thực Trong E2E Runner

Trong tệp `Front end/tests/e2e/helpers.ts`, hàm `login()` phân giải mật khẩu theo thứ tự ưu tiên:
1. `explicitPassword` (tham số truyền vào khi gọi hàm).
2. `rolePassword` (`E2E_ADMIN_PASSWORD` / `E2E_TEACHER_PASSWORD` / `E2E_STUDENT_PASSWORD`).
3. `E2E_TEST_PASSWORD` (biến môi trường chung áp dụng cho toàn bộ test accounts).

Mật khẩu lưu trong CSDL dưới dạng BCrypt hash (cost 10). Tuyệt đối không lưu plaintext password.

---

## 7. Phân Loại Toàn Bộ 72 Tests Playwright (Single Classification Matrix)

Toàn bộ 72 test cases được phân loại duy nhất, không chồng chéo:

1. **Nhóm Mocked Suite (27 tests):**
   - `admin-class-roster.spec.ts` (9 tests): Mocked UI qua `page.route` + Mock cookies.
   - `student-ai-assistant.spec.ts` (3 tests): Mocked UI qua `page.route` + Mock cookies.
   - `teacher-plagiarism-detail-visual.spec.ts` (8 tests): Mocked UI qua `page.route` + Mock cookies.
   - `plagiarism-normalizer.spec.ts` Tests 1–7 (7 tests): Pure Unit Logic chạy in-memory trong Node.js.
2. **Nhóm Live & Hybrid Suite (45 tests):**
   - **Category A — Live Pipeline Xuyên Suốt (5 tests):** `core-flows.spec.ts` (Flow 1, 2, 3, 5, 4/6).
   - **Category B — Live Database CRUD & State Persistence (6 tests):** `scoring-override.spec.ts` (2 tests), `grade-audit-history.spec.ts` Tests 1 & 2 (2 tests), `class-aware-exams.spec.ts` Tests 1 & 2 (2 tests).
   - **Category C — Live Navigation & Multi-Viewport Layout Integrity (9 tests):** `responsive.spec.ts` Tests 2–10 across Desktop, Tablet, Mobile.
   - **Category D — Pure In-Memory Store (1 test):** `responsive.spec.ts` Test 1 (Zustand store).
   - **Category E — Live Auth Session với Mocked API Responses (24 tests):** `auth-guard-session.spec.ts` (5 tests), `class-aware-exams.spec.ts` Tests 3–5 (3 tests), `grade-audit-history.spec.ts` Tests 3–7 (5 tests), `student-plagiarism-appeal-link.spec.ts` (3 tests), `ast-plagiarism-dashboard-matrix.spec.ts` (6 tests), `plagiarism-normalizer.spec.ts` Tests 8 & 9 (2 tests - Hybrid).

Tổng cộng: $27\text{ (mocked)} + 45\text{ (live/hybrid)} = \mathbf{72\text{ tests}}$.
Mỗi test được phân loại đúng 1 lần duy nhất.
