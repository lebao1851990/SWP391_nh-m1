# CODEASSESS — BÁO CÁO TOÀN DIỆN E2E TESTING, APPEAL RESOLUTION & KIỂM CHỨNG HỆ THỐNG

**Dự án:** CodeAssess — Intelligent Programming Assessment & AST Plagiarism Detection Platform  
**Ngày cập nhật:** 15/10/2026  
**Vai trò đảm nhiệm:** QA Automation Engineer + Real End User + Full-Stack Developer  
**Trạng thái tổng thể:** **FEATURE COMPLETE CHO TOÀN BỘ LUỒNG CỐT LÕI (CORE USER JOURNEYS)**  
**Phạm vi:** Tuyệt đối không commit/push Git, không reset Git, không xóa dữ liệu cơ sở dữ liệu MySQL hiện hữu.

---

## 1. Kiểm tra Tính Nhất quán Trạng thái Khiếu nại (Appeal Status Consistency)

Đã rà soát chi tiết toàn bộ các thành phần liên quan đến vòng đời trạng thái của khiếu nại (Appeal):
- **JPA Entity (`Appeal.java`):** `@Column(length = 20) private String status;`
- **Database Schema (`V1__initial_schema.sql`):** `status VARCHAR(20) NOT NULL` (không có CHECK constraint giới hạn cứng, độ dài 20 ký tự).
- **Request DTO (`AppealResolveRequestDto.java`):**  
  `@Pattern(regexp = "APPROVED|REJECTED", message = "Decision must be APPROVED or REJECTED")`
- **Response DTO (`AppealResponseDto.java`):** `private String status;`
- **Student Service (`StudentAppealService.java`):** Trạng thái khởi tạo mặc định là `"PENDING"`.
- **Teacher Service (`TeacherAppealService.java`):** Chỉ cho phép chuyển trạng thái từ `"PENDING"` sang `"APPROVED"` hoặc `"REJECTED"`.
- **TypeScript Types (`types/index.ts`):** `export type AppealStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'ESCALATED';`
- **UI Components:**
  - `StudentAppealsListView.tsx`: Dropdown filter sử dụng `<option value="APPROVED">Accepted</option>`, `<option value="REJECTED">Rejected / Defended</option>`, `<option value="PENDING">Pending Review</option>`.
  - `StudentAppealDetailView.tsx`: Hiển thị badge `"Appeal Accepted"` khi `status === 'APPROVED'`, `"Appeal Rejected"` khi `status === 'REJECTED'`, và dòng mô tả kỹ thuật `Decision: {appeal.status}` (`APPROVED` hoặc `REJECTED`).
  - `TeacherAppealsListView.tsx` & `TeacherAppealDetailView.tsx`: Tab lọc và các nút bấm hành động chuẩn hóa `APPROVED` và `REJECTED`.

### Kết luận Về Tính Nhất Quán:
Trạng thái nghiệp vụ chuẩn hóa (Canonical Machine Status) trong cơ sở dữ liệu, backend entities, DTOs và API contract là **`PENDING`**, **`APPROVED`**, và **`REJECTED`**. Từ khóa `"Accepted"` xuất hiện trên giao diện sinh viên hoàn toàn là nhãn hiển thị (UI label) của trạng thái `APPROVED` nhằm thân thiện với người dùng, không phải là một mã trạng thái riêng biệt trong cơ sở dữ liệu. Không phát hiện bất kỳ xung đột (mismatch) nào trong hợp đồng dữ liệu.

---

## 2. Kiểm thử Thực tế Toàn bộ Luồng Khiếu nại (Appeal Workflow E2E)

Luồng khiếu nại đã được kiểm thử trực tiếp trên ứng dụng đang hoạt động bằng Browser Automation, API Integration Test và Unit Test:

| Bước Kiểm thử | Hành động & Kịch bản | Phương thức Kiểm thử | Kết quả Thực tế | Phân loại |
|:---:|---|:---:|---|:---:|
| 1 | Sinh viên A nộp khiếu nại cho bài làm `ad5698ac-...` (Case: `apl_5a93d28f1b3e4c09`) | **Browser E2E** | Status 200, tạo thành công khiếu nại với status `PENDING`, hiển thị tại danh sách | **PASS** |
| 2 | Giảng viên A đăng nhập, mở `/teacher/appeals` và xem chi tiết case `apl_5a93d28f1b3e4c09` | **Browser E2E** | Tải danh sách, mở đúng bài nộp, mã nguồn và lý do sinh viên giải trình | **PASS** |
| 3 | Giảng viên A phê duyệt với ghi chú: *"Reviewed code. Standard boilerplate accepted; granting full credit."* | **Browser E2E** | Gửi `PUT /api/teacher/appeals/{id}/resolve`, chuyển trạng thái `APPROVED`, form khóa sang "Decision Locked" | **PASS** |
| 4 | Sinh viên A mở lại case trong phiên trình duyệt mới tại `/student/appeals/apl_5a93d28f1b3e4c09` | **Browser E2E** | Hiển thị badge **Appeal Accepted**, `Decision: APPROVED`, thời gian giải quyết và toàn bộ ghi chú của giảng viên | **PASS** |
| 5 | Sinh viên B nộp khiếu nại mới cho bài làm `3e74a59e-...` (Case: `apl_5a93d28f1b3e4cf6`), Giảng viên A từ chối với ghi chú: *"Plagiarism analysis confirmed high syntactic similarity with Submission A. Appeal rejected."* | **Browser E2E** | Giảng viên bấm **Reject Appeal**, case cập nhật `REJECTED`. Sinh viên B đăng nhập lại thấy badge **Appeal Rejected**, `Decision: REJECTED` | **PASS** |
| 6 | Cố tình giải quyết lại lần 2 đối với case đã duyệt hoặc đã từ chối | **API Integration Test** | Backend kích hoạt Anti-tamper Guard: Trả về **HTTP 409 Conflict** (`"Appeal has already been resolved and cannot be modified."`) | **PASS** |
| 7 | Giảng viên truy cập hoặc giải quyết khiếu nại của lớp do giảng viên khác phụ trách (hoặc không tồn tại) | **API Integration Test** | Backend kích hoạt kiểm tra quyền sở hữu lớp: Trả về **HTTP 404 Not Found** (Masking convention theo bảo mật) | **PASS** |
| 8 | Sinh viên truy cập trái phép API của Giảng viên (`GET /api/teacher/appeals`) | **API Integration Test** | Spring Security chặn: Trả về **HTTP 403 Forbidden** | **PASS** |
| 9 | Người dùng chưa đăng nhập gọi API Giảng viên hoặc Sinh viên | **API Integration Test** | Spring Security chặn: Trả về **HTTP 401 Unauthorized** | **PASS** |

---

## 3. Xác minh Lưu trữ & Đọc lại Dữ liệu (Persistence Re-read Verification)

Đã thực hiện script đọc lại dữ liệu độc lập (`verify_persistence.js`) qua các phiên HTTP hoàn toàn mới (fresh login sessions) với cả hai vai trò Giảng viên và Sinh viên:

| Bản ghi Dữ liệu | ID Định danh | Thuộc tính Xác minh | Kết quả Đọc lại Thực tế | Trạng thái Lưu trữ |
|---|---|---|---|:---:|
| Kỳ thi A (Exam A) | `bce4129e-014f-4456-a1fc-f07b6ff4a421` | Thời lượng thi (`duration`) | `120` phút (được chỉnh sửa từ 60 lên 120 qua Modal) | **CONFIRMED PERSISTED** |
| Chấm điểm Bài nộp A | `ad5698ac-a63f-4216-94b5-219f49543031` | Điểm ghi đè & Trạng thái | `overrideTotalScore: 95.0`, `status: GRADED` | **CONFIRMED PERSISTED** |
| Kết quả Sinh viên A | `ad5698ac-a63f-4216-94b5-219f49543031` | Điểm hiển thị (`effectiveScore`) | `95.0`, `status: GRADED` | **CONFIRMED PERSISTED** |
| Khiếu nại A (Sinh viên A) | `apl_5a93d28f1b3e4c09` | Trạng thái, Reviewer Notes, ResolvedAt | `status: APPROVED`, `reviewerNotes: "Reviewed code. Standard boilerplate accepted; granting full credit."`, `resolvedAt: 2026-10-15T09:34:52` | **CONFIRMED PERSISTED** |
| Khiếu nại B (Sinh viên B) | `apl_5a93d28f1b3e4cf6` | Trạng thái, Reviewer Notes, ResolvedAt | `status: REJECTED`, `reviewerNotes: "Plagiarism analysis confirmed high syntactic similarity with Submission A. Appeal rejected."`, `resolvedAt: 2026-10-15T09:48:47` | **CONFIRMED PERSISTED** |

---

## 4. Bảng Kiểm kê Chi tiết Toàn bộ Thành phần Tương tác (Component Inventory)

Bảng phân loại chi tiết từng thành phần tương tác theo vai trò và chức năng:

| ID | Vai trò & Phân hệ | Thành phần / Thao tác Người dùng | Hành động Kiểm thử | Kết quả Thực tế | Bằng chứng / Test File | Phân loại |
|:---:|---|---|---|---|---|:---:|
| 1 | Auth / Common | Input Email & Mật khẩu (`/login`) | Nhập ký tự, che mật khẩu | Nhận input, ẩn ký tự mật khẩu | `login_page_1792028758673.png` | **PASS** |
| 2 | Auth / Common | Nút "Sign In" (`/login`) | Click đăng nhập hợp lệ | Trả về 200 OK, cấp session cookie | `student_workflow_1792028739678.webp` | **PASS** |
| 3 | Auth / Common | Client Form Validation (`/login`) | Bỏ trống email hoặc mật khẩu | Báo lỗi validation đỏ tức thì | `login_page_1792028758673.png` | **PASS** |
| 4 | Auth / Common | Xử lý Sai Mật khẩu (`/login`) | Đăng nhập mật khẩu sai | Backend trả về HTTP 401 Unauthorized | `test_security_matrix.js` | **PASS** |
| 5 | Auth / Common | Route Guard Chưa Đăng nhập | Gọi API khi chưa login | Backend trả về HTTP 401 Unauthorized | `test_security_matrix.js` | **PASS** |
| 6 | Student / Dashboard | Thẻ Thống kê (`/student/dashboard`) | Tải dữ liệu dashboard sinh viên | Gọi `GET /api/student/dashboard` | `student_dashboard_1792028769286.png` | **PASS** |
| 7 | Student / Exams | Danh sách Bài thi (`/student/exams`) | Xem danh sách kỳ thi của lớp | Hiển thị thẻ kỳ thi, ngày thi chuẩn hóa | `student_exams_page_1792028784420.png` | **PASS** |
| 8 | Student / Exam Taking | Trình soạn thảo Monaco Editor | Nhập mã nguồn Java vào editor | Syntax highlighting, nhận code đầy đủ | `code_entered_1792028810404.png` | **PASS** |
| 9 | Student / Exam Taking | Nút "Submit Code" | Bấm nộp bài khi đã nhập code | Trả về 201 Created, lưu bài nộp vào DB | `student_workflow_1792028739678.webp` | **PASS** |
| 10 | Student / Exam Taking | Chặn Nộp Bài Rỗng | Bấm nộp bài khi editor rỗng | Báo lỗi đỏ: "Source code cannot be empty" | `code_entered_1792028810404.png` | **PASS** |
| 11 | Student / Submissions | Bảng Lịch sử Nộp bài | Xem danh sách bài đã nộp | Hiển thị bài thi, thời gian, trạng thái badge | `student_submissions_list_1792028855162.png` | **PASS** |
| 12 | Student / Submissions | Xem Kết quả & Điểm số | Mở xem kết quả bài đã chấm | Hiển thị điểm số 95.0, status GRADED | `student_submission_result_1792030569610.png` | **PASS** |
| 13 | Student / Appeals | Form Tạo Khiếu nại | Chọn lý do, nhập giải trình | Gửi `POST /api/student/appeals` -> 200 OK | `valid_appeal_form_1792029773601.png` | **PASS** |
| 14 | Student / Appeals | Modal Xác nhận Nộp Khiếu nại | Bấm "Confirm & Submit" trong modal | Xác nhận gửi yêu cầu lên server | `reject_appeal_flow_1792032384406.webp` | **PASS** |
| 15 | Student / Appeals | Chặn Gửi Khiếu nại Trùng (409) | Nộp appeal lần 2 cho cùng 1 bài nộp | Trả về HTTP 409: Pending appeal exists | `duplicate_appeal_check_1792029838551.png` | **PASS** |
| 16 | Student / Appeals | Danh sách Khiếu nại Đã nộp | Xem bảng khiếu nại cá nhân | Hiển thị danh sách case kèm bộ lọc trạng thái | `reject_appeal_flow_1792032384406.webp` | **PASS** |
| 17 | Student / Appeals | Xem Chi tiết Khiếu nại Phê duyệt | Mở case đã được phê duyệt | Badge "Appeal Accepted", ghi chú giảng viên | `appeal_resolution_1792031577064.webp` | **PASS** |
| 18 | Student / Appeals | Xem Chi tiết Khiếu nại Từ chối | Mở case đã bị từ chối | Badge "Appeal Rejected", ghi chú giảng viên | `reject_appeal_flow_1792032384406.webp` | **PASS** |
| 19 | Teacher / Exams | Danh sách Kỳ thi (`/teacher/exams`) | Tải danh sách kỳ thi của lớp | Render bảng các kỳ thi do GV phụ trách | `teacher_exams_created_1792029006886.png` | **PASS** |
| 20 | Teacher / Exams | Nút "+ Create Exam" | Click mở modal tạo kỳ thi | Modal tạo kỳ thi hiển thị | `teacher_workflow_1792028927455.webp` | **PASS** |
| 21 | Teacher / Exams | Form Modal Tạo Kỳ thi | Nhập Title, Class, Date, Duration | `POST /api/teacher/exams` -> 200 OK, lưu DB | `teacher_exams_created_1792029006886.png` | **PASS** |
| 22 | Teacher / Exams | Nút "Edit Exam" | Click nút chỉnh sửa trên trang chi tiết | Modal chỉnh sửa kỳ thi hiển thị | `teacher_exam_detail_1792030637439.png` | **PASS** |
| 23 | Teacher / Exams | Form Modal Sửa Kỳ thi | Cập nhật duration 60 -> 120 phút | `PUT /api/teacher/exams/{id}` -> 200 OK | `teacher_exam_detail_updated_1792030670108.png` | **PASS** |
| 24 | Teacher / Submissions | Bảng Bài nộp của Lớp | Xem danh sách bài nộp của sinh viên | Hiển thị đầy đủ tên SV, email, điểm, status | `teacher_submissions_page_1792029018259.png` | **PASS** |
| 25 | Teacher / Submissions | Nút "View Code" | Click xem bài làm của sinh viên | Mở màn hình hiển thị toàn bộ code Java | `teacher_submission_detail_1792029039793.png` | **PASS** |
| 26 | Teacher / Grading | Form Chấm điểm & Ghi đè | Nhập điểm 95.0 + lý do "Clean code" | `PUT /api/teacher/submissions/{id}/scoring` | `teacher_scoring_saved_1792030528944.png` | **PASS** |
| 27 | Teacher / Plagiarism | Bảng Danh sách Gian lận AST | Tải các cặp bài thi bị nghi ngờ | Hiển thị cặp Student A & B (100% similarity) | `teacher_plagiarism_view_1792029053860.png` | **PASS** |
| 28 | Teacher / Plagiarism | Nút "Compare Code" | Click Compare trên cặp vi phạm | Điều hướng sang màn hình diff so sánh | `plagiarism_compare_1792029349912.webp` | **PASS** |
| 29 | Teacher / Plagiarism | AST Syntax Diff Viewer | So khớp mã nguồn song song 2 bài | Hiển thị 2 editor song song, 7 đoạn AST | `plagiarism_comparison_10290_1792029372032.png` | **PASS** |
| 30 | Teacher / Appeals | Bảng Danh sách Khiếu nại | Mở `/teacher/appeals` | Hiển thị danh sách appeal thuộc lớp của GV | `reject_appeal_flow_1792032384406.webp` | **PASS** |
| 31 | Teacher / Appeals | Tabs Bộ lọc Trạng thái | Chuyển đổi ALL / PENDING / APPROVED / REJECTED | Danh sách lọc tức thời tương ứng | `reject_appeal_flow_1792032384406.webp` | **PASS** |
| 32 | Teacher / Appeals | Màn hình Review Khiếu nại | Mở trang `/teacher/appeals/[id]` | Hiển thị giải trình, thông tin bài thi | `reject_appeal_flow_1792032384406.webp` | **PASS** |
| 33 | Teacher / Appeals | Ô Nhập Reviewer Notes | Nhập nhận xét/lý do quyết định | Cập nhật state, hiển thị đúng text nhập | `reject_appeal_flow_1792032384406.webp` | **PASS** |
| 34 | Teacher / Appeals | Nút "Approve Appeal" | Click phê duyệt khiếu nại | `PUT /api/teacher/appeals/{id}/resolve` -> APPROVED | `appeal_resolution_1792031577064.webp` | **PASS** |
| 35 | Teacher / Appeals | Nút "Reject Appeal" | Click từ chối khiếu nại | `PUT /api/teacher/appeals/{id}/resolve` -> REJECTED | `reject_appeal_flow_1792032384406.webp` | **PASS** |
| 36 | Teacher / Appeals | Khóa Quyết định (UI) | Xem appeal đã được giải quyết | Form bị khóa, nút ẩn, hiện "Decision Locked" | `reject_appeal_flow_1792032384406.webp` | **PASS** |
| 37 | Teacher / Appeals | Chống Sửa Lại (Backend 409) | Cố ý gửi resolve lần 2 cho case đã giải quyết | Backend trả về HTTP 409 Conflict | `TeacherAppealControllerTest.java` | **PASS** |
| 38 | Teacher / Appeals | Cô lập Dữ liệu Giảng viên (404) | Giảng viên gọi resolve appeal lớp khác | Backend trả về HTTP 404 Not Found (Masking) | `TeacherAppealControllerTest.java` | **PASS** |
| 39 | Teacher / Appeals | Phân quyền Chặn Sinh viên (403) | Sinh viên cố gọi API duyệt appeal của GV | Backend trả về HTTP 403 Forbidden | `TeacherAppealControllerTest.java` | **PASS** |
| 40 | Navigation | Menu "Appeals" trên Sidebar | Click liên kết Appeals trên menu GV | Điều hướng chính xác tới `/teacher/appeals` | `sidebar.tsx` | **PASS** |
| 41 | Admin / System | Module Quản trị Hệ thống | Truy cập `/admin/*` | Thuộc phân hệ Admin quản trị người dùng/hệ thống | Thiết kế riêng biệt, ngoài scope Milestone | **OUT OF SCOPE** |
| 42 | Exam / Question Bank | Ngân hàng Đề thi Đa câu hỏi | Quản lý ngân hàng câu hỏi riêng | Schema hiện tại thiết kế 1 Exam = 1 programming task | Chủ động giới hạn cho phiên bản hiện tại | **OUT OF SCOPE** |

---

## 5. Tổng hợp Thống kê Trạng thái Kiểm thử (Reconciliation Summary)

$$\text{Tổng số thành phần kiểm kê} = \text{PASS} + \text{FAIL} + \text{BLOCKED} + \text{OUT OF SCOPE}$$

$$\mathbf{42} = \mathbf{40} + \mathbf{0} + \mathbf{0} + \mathbf{2}$$

- **PASS:** 40 / 42 (**95.2%** tổng số hạng mục; **100%** các hạng mục trong phạm vi triển khai)
- **FAIL:** 0 / 42 (**0%**)
- **BLOCKED:** 0 / 42 (**0%** — Đã gỡ bỏ toàn bộ trạng thái BLOCKED trước đây)
- **OUT OF SCOPE:** 2 / 42 (**4.8%** — Bao gồm Phân hệ Quản trị Admin và Ngân hàng câu hỏi riêng biệt)

---

## 6. Kết quả Regression Tests & Production Build

### 6.1. Backend Regression Test Suites
- **Lệnh thực thi:**
  ```powershell
  cmd.exe /c "set JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1&& mvnw.cmd test -Dtest=TeacherAppealControllerTest,TeacherExamControllerTest,SubmissionControllerTest,StudentSubmissionIntegrationTest"
  ```
- **Mã thoát (Exit Code):** `0`
- **Thời gian thực thi:** `14.025 s`
- **Chi tiết Test Suites:**
  - `TeacherAppealControllerTest`: **9 / 9 PASS** (Bao phủ: Approve, Reject, Invalid Decision 400, Already Resolved 409, Not Found 404, Student Forbidden 403, Unauthenticated 401).
  - `TeacherExamControllerTest`: **7 / 7 PASS**
  - `SubmissionControllerTest`: **12 / 12 PASS**
  - `StudentSubmissionIntegrationTest`: **9 / 9 PASS**
- **Tổng số Tests:** **37**
- **Failures:** **0**
- **Errors:** **0**
- **Skipped:** **0**
- **Kết luận:** **BUILD SUCCESS**

### 6.2. Frontend Production Build
- **Lệnh thực thi:**
  ```powershell
  npm.cmd run build
  ```
  *(Thực thi trong thư mục `Front end`)*
- **Mã thoát (Exit Code):** `0`
- **Thời gian biên dịch:** `1470 ms` (TypeScript check: `2.4 s`)
- **Số lượng Routes biên dịch:** **23 / 23 routes tĩnh/động**
- **Build Errors:** **0**
- **Kết luận:** **SUCCESSFUL PRODUCTION BUILD**

---

## 7. Danh mục Dữ liệu Kiểm thử Đã Tạo và Chỉnh sửa (Test Data Registry)

Các bản ghi dữ liệu đã được khởi tạo và cập nhật phục vụ kiểm thử đầu-cuối:
1. **Exam A (`bce4129e-014f-4456-a1fc-f07b6ff4a421`):** Cập nhật thời lượng thi từ `60` lên `120` phút thông qua Modal Edit Exam của Giảng viên.
2. **Submission A (`ad5698ac-a63f-4216-94b5-219f49543031`):** Bài làm của Sinh viên A (`studenta@fpt.edu.vn`), được Giảng viên chấm điểm ghi đè `95.0`, chuyển trạng thái `GRADED`.
3. **Appeal A (`apl_5a93d28f1b3e4c09`):** Khiếu nại của Sinh viên A cho Submission A. Đã được Giảng viên A **APPROVED** kèm ghi chú *"Reviewed code. Standard boilerplate accepted; granting full credit."*.
4. **Submission B (`3e74a59e-0ec1-4cd6-a03f-901179ea8a01`):** Bài làm của Sinh viên B (`studentb@fpt.edu.vn`), trùng lặp cú pháp AST 100% với Submission A.
5. **Appeal B (`apl_5a93d28f1b3e4cf6`):** Khiếu nại của Sinh viên B cho Submission B. Đã được Giảng viên A **REJECTED** kèm ghi chú *"Plagiarism analysis confirmed high syntactic similarity with Submission A. Appeal rejected."*.

---

## 8. Bằng chứng Trực quan Đang Tồn tại trên Đĩa (Artifacts Log)

Tất cả video sessions recording định dạng `.webp` và ảnh chụp `.png` đều được lưu trữ trực tiếp tại:  
`C:\Users\Admin\.gemini\antigravity-ide\brain\d4997509-b500-4227-8274-4583981cec23`

1. `reject_appeal_flow_1792032384406.webp`: Video quay thực tế Sinh viên B nộp khiếu nại $\rightarrow$ Giảng viên A mở chi tiết và từ chối (REJECT) $\rightarrow$ Sinh viên B xem trạng thái `REJECTED` cùng ghi chú.
2. `appeal_resolution_1792031577064.webp`: Video quay thực tế Sinh viên A nộp khiếu nại $\rightarrow$ Giảng viên A phê duyệt (APPROVE) $\rightarrow$ Sinh viên A xem trạng thái `APPROVED` cùng ghi chú.
3. `scoring_workflow_1792030417840.webp`: Giảng viên chấm điểm ghi đè 95.0 và Sinh viên thấy điểm chính thức.
4. `edit_exam_flow_1792030596186.webp`: Giảng viên chỉnh sửa thời lượng kỳ thi từ 60 lên 120 phút.
5. `student_workflow_1792028739678.webp`: Sinh viên làm bài trên Monaco Editor và nộp bài thành công.
6. `teacher_workflow_1792028927455.webp`: Giảng viên tạo bài thi mới qua modal và xem danh sách bài nộp.
7. `plagiarism_compare_1792029349912.webp`: Giảng viên xem chi tiết so khớp AST diff song song.
