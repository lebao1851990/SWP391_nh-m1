# CODEASSESS FRONTEND — MA TRẬN HOÀN THIỆN TÍNH NĂNG TOÀN DIỆN (FRONTEND FEATURE COMPLETION MATRIX)

**Dự án:** CodeAssess — Intelligent Programming Assessment & AST Plagiarism Detection Platform  
**Phiên bản:** Release Milestone 2 Final  
**Ngày kiểm toán:** 15/10/2026  
**Đơn vị thực hiện:** Senior Frontend & QA Engineering Team  

---

## 1. Tổng quan Kiến trúc & Nguyên tắc Hoàn thiện

Ma trận này cung cấp bản đặc tả chi tiết và kiểm kê 100% các tính năng hiện có trên toàn bộ Frontend của hệ thống CodeAssess, bao gồm:
- **Kiến trúc:** Next.js App Router (React 19), TypeScript strict, Tailwind CSS tokens, TanStack Query, Zustand session store, Sonner toast notifications, Lucide React icons.
- **Nguyên tắc cốt lõi:**
  1. *Business Logic & Security > Existing Features & API Contracts > Approved Architecture > UI/UX > Motion.*
  2. Không dùng mock data để giả lập tính năng.
  3. Phân định rõ ràng giữa chức năng hoạt động thật (PASS) và chức năng bị giới hạn/chặn bởi Backend (BLOCKED).

---

## 2. Ma trận Chi tiết Từng Tính năng (Feature-by-Feature Matrix)

### Nhóm 1: Xác thực & Điều hướng Chung (Authentication & Shared Layout)

#### 1.1. User Login (`/login`)
- **Tên tính năng & Route:** Authentication Login — `/login`
- **Vai trò & Quyền hạn:** Public (chưa xác thực). Nếu đã có session, middleware tự động chuyển hướng vào `/{role}/dashboard`.
- **Mục tiêu người dùng:** Đăng nhập vào hệ thống bằng tài khoản Giảng viên hoặc Sinh viên.
- **Entry point & Controls:** Form đăng nhập gồm Input Email, Input Password, Nút "Sign In", Link "Register", Link "Forgot password".
- **Interaction sequence:** Nhập email + password → Bấm "Sign In" → Hiển thị loading spinner trên nút → Gọi API → Lưu token/user vào Zustand và cookie → Sonner toast thành công → Chuyển hướng tới `/{role}/dashboard`.
- **Input & Validation:** Email (đúng định dạng RFC, required), Password (tối thiểu 6 ký tự, required). Báo lỗi inline nếu để trống hoặc sai định dạng.
- **State:** Local state: `email`, `password`, `isSubmitting`, `error`. Server state: TanStack Query cache reset, Zustand `sessionStore.setSession(user, token)`.
- **API Endpoint:** `POST /api/auth/login` | Request: `{ email, password }` | Response: `{ token, user: { id, email, fullName, role } }`.
- **UI Business Rules:** Nút "Sign In" disabled khi đang submit để chống double-click.
- **Backend Business Rules:** Xác thực mật khẩu BCrypt, kiểm tra trạng thái hoạt động của tài khoản, cấp JWT token (HS256).
- **Trạng thái:** Loading (Spinner + disabled), Success (Toast + redirect), Error (Toast + inline error banner), Empty (Form trống).
- **Persistence:** JWT lưu cookie `auth-token` và Zustand `sessionStore` (được rehydrate khi F5).
- **Boundary & Failures:** Sai mật khẩu (401), tài khoản không tồn tại (404/401), máy chủ mất kết nối (500/Network Error).
- **Gaps:** Không có. Hoàn thành 100%.
- **Verification:** **PASS** (Đã xác minh luồng đăng nhập của cả Teacher và Student).

#### 1.2. User Registration (`/register`)
- **Tên tính năng & Route:** Student Registration — `/register`
- **Vai trò & Quyền hạn:** Public.
- **Mục tiêu người dùng:** Đăng ký tài khoản sinh viên mới để tham gia các lớp học lập trình.
- **Entry point & Controls:** Form đăng ký gồm Full Name, Email, Password, Confirm Password, Nút "Create Account", Link quay lại "Sign In".
- **Interaction sequence:** Nhập thông tin → Validate khớp password → Bấm submit → Gọi API → Toast thông báo thành công → Chuyển hướng về `/login`.
- **Input & Validation:** Full Name (>2 ký tự), Email hợp lệ, Password (>=6 ký tự), Confirm Password khớp chính xác.
- **API Endpoint:** `POST /api/auth/register` | Request: `{ fullName, email, password, role: "STUDENT" }`.
- **Trạng thái:** Loading, Success, Error inline validation.
- **Gaps:** Không có. Hoàn thành 100%.
- **Verification:** **PASS**.

#### 1.3. Forgot & Reset Password (`/forgot-password`, `/reset-password`)
- **Tên tính năng & Route:** Password Recovery — `/forgot-password`, `/reset-password`
- **Vai trò & Quyền hạn:** Public.
- **Mục tiêu người dùng:** Khôi phục mật khẩu thông qua liên kết gửi về email.
- **Entry point & Controls:** Input Email yêu cầu reset; Input New Password + Token trên trang reset.
- **API Endpoint:** Backend chưa cấu hình SMTP Gateway và chưa có controller cấp phát reset token.
- **Trạng thái:** Giao diện, form validation, và routing đã sẵn sàng.
- **Gaps:** Backend thiếu hạ tầng gửi mail SMTP và bảng lưu trữ token khôi phục mật khẩu.
- **Verification:** **BLOCKED** (Thiếu hạ tầng Backend; không tạo mock success).

#### 1.4. Shared Layout, Sidebar & Global Navigation
- **Tên tính năng & Route:** Persistent Layout — `/teacher/*`, `/student/*`
- **Vai trò & Quyền hạn:** Teacher cho tuyến `/teacher/*`; Student cho tuyến `/student/*`.
- **Mục tiêu người dùng:** Cung cấp thanh điều hướng bên (Sidebar) cố định, header viễn thám, chuyển trang mượt mà không nháy trắng.
- **Entry point & Controls:**
  - Sidebar: Logo CodeAssess, AST Engine Status badge, Navigation links với icon Lucide SVG, nút Logout.
  - Header: Input Search toàn cục, phím tắt `Ctrl+K` / `⌘K`, Bell notification popover, Help academic integrity modal, Profile avatar menu.
- **Interaction sequence:**
  - Bấm navigation link: Chỉ phần nội dung `<main>` chuyển trang với hiệu ứng `page-transition` (180ms ease), Sidebar và Header giữ nguyên không re-render.
  - Bấm `Ctrl+K`: Con trỏ focus ngay vào ô tìm kiếm.
  - Gõ từ khóa + `Enter`: Điều hướng thông minh theo ngữ cảnh (`/teacher/submissions?search=...`, `/teacher/exams?search=...`).
  - Bấm Bell: Mở popover tài nguyên hệ thống (tải CPU, RAM, hàng đợi AST).
  - Bấm Help: Mở modal quy chế liêm chính học thuật.
  - Bấm Sign Out: Gọi `authApi.logout()`, xóa session và chuyển về `/login`.
- **Gaps:** Không có. Hoàn thành 100%.
- **Verification:** **PASS**.

---

### Nhóm 2: Phân hệ Sinh viên (Student Experience)

#### 2.1. Student Dashboard (`/student/dashboard`)
- **Tên tính năng & Route:** Student Overview — `/student/dashboard`
- **Vai trò & Quyền hạn:** Sinh viên đã xác thực (`ROLE_STUDENT`).
- **Mục tiêu người dùng:** Theo dõi lịch thi sắp tới, hạn chót nộp bài, chỉ số liêm chính học thuật và bài thi đã hoàn thành.
- **API Endpoint:** `GET /api/student/dashboard` | Response: `{ upcomingDeadlines: [...], completedExamsCount, averageScore, astIntegrityScore }`.
- **UI Business Rules:** Phân loại hạn nộp theo thời gian thực (đếm ngược giờ/ngày thi).
- **Trạng thái:** Skeleton loading khi tải dữ liệu, empty state khi chưa có bài thi nào được ghi danh.
- **Verification:** **PASS**.

#### 2.2. Student Exam Catalog & Details (`/student/exams`, `/student/exams/[id]`)
- **Tên tính năng & Route:** Exam List & Detail Workspace — `/student/exams`, `/student/exams/[id]`
- **Vai trò & Quyền hạn:** Sinh viên thuộc lớp học được mở kỳ thi.
- **Mục tiêu người dùng:** Xem danh sách bài thi của môn học, mở phòng thi, đọc đề bài và nộp mã nguồn.
- **Entry point & Controls:**
  - Bảng bài thi: Tab lọc (All, Active, Completed), ô tìm kiếm mã môn/tên bài thi, nút "Enter Exam" / "View Submission".
  - Chi tiết bài thi: Thẻ thông tin bài thi (thời lượng, hạn nộp), banner cảnh báo nếu kỳ thi đã đóng, khung soạn thảo mã nguồn Dark IDE (`bg-slate-950`), bộ đếm Lines & Characters, nút "Submit Solution".
- **Interaction sequence:**
  - Sinh viên nhập/paste code Java vào editor → Bộ đếm ký tự và số dòng cập nhật tức thì.
  - Bấm "Submit Solution" → Mở Modal hộp thoại xác nhận nộp bài.
  - Bấm "Confirm Submission" → Nút chuyển sang trạng thái loading xoay vòng (`animate-spin`), disabled chống double-click.
  - Gọi API `POST /api/student/exams/{id}/submissions` → Nhận submission ID → Toast thành công → Điều hướng tới `/student/submissions/{id}`.
- **Input & Validation:** Mã nguồn Java không được để trống (báo lỗi inline nếu rỗng).
- **Backend Rules:** Kiểm tra kỳ thi còn hiệu lực thời gian hay không; nếu đã kết thúc từ chối nhận bài (`400 Bad Request`).
- **Verification:** **PASS**.

#### 2.3. Student Submissions & Result View (`/student/submissions`, `/student/submissions/[id]`)
- **Tên tính năng & Route:** Submission History & Grading Result — `/student/submissions`, `/student/submissions/[id]`
- **Vai trò & Quyền hạn:** Sinh viên sở hữu bài nộp (`Ownership verification`).
- **Mục tiêu người dùng:** Xem lịch sử nộp bài, trạng thái chấm điểm (PENDING, TESTING, GRADED), xem chi tiết bảng rubric điểm và nhận xét của Giảng viên.
- **API Endpoints:**
  - `GET /api/student/submissions`: Lấy danh sách bài nộp của sinh viên.
  - `GET /api/student/submissions/{id}/result`: Lấy kết quả chấm điểm chi tiết của bài nộp.
- **UI Controls:** Thẻ chứng nhận kết quả, bảng tiêu chí chấm điểm (Criteria score / max score), lời phê của Giảng viên, nút "File Academic Appeal" liên kết trực tiếp sang tạo đơn khiếu nại.
- **Verification:** **PASS**.

#### 2.4. Student AST Plagiarism Transparency (`/student/plagiarism/[id]`)
- **Tên tính năng & Route:** Student Similarity Transparency — `/student/plagiarism/[id]`
- **Vai trò & Quyền hạn:** Sinh viên có bài nộp bị quét tương đồng AST.
- **Mục tiêu người dùng:** Xem tỷ lệ tương đồng cây cú pháp và nhận giải thích minh bạch để chuẩn bị giải trình nếu bị nghi vấn.
- **Bảo mật & Quyền riêng tư:** Sinh viên chỉ nhìn thấy tỷ lệ tương đồng của bài mình và mô tả bằng chứng chung; **tuyệt đối không hiển thị mã nguồn hoặc danh tính của sinh viên đối chiếu**.
- **Verification:** **PASS**.

#### 2.5. Student Appeals Management (`/student/appeals`, `/student/appeals/new`, `/student/appeals/[id]`)
- **Tên tính năng & Route:** Academic Appeals Hub — `/student/appeals`, `/student/appeals/new`, `/student/appeals/[id]`
- **Vai trò & Quyền hạn:** Sinh viên đã xác thực.
- **Mục tiêu người dùng:** Nộp đơn khiếu nại điểm số hoặc khiếu nại nghi vấn đạo văn, theo dõi tiến độ xử lý và tải bản tường trình khiếu nại.
- **Entry point & Controls:**
  - Danh sách: Nút "New Appeal Ticket", nút "Export Statement" (xuất CSV), bảng khiếu nại với status badge (Pending, Approved, Rejected).
  - Form tạo mới: Chọn lý do (Scoring Discrepancy, AST Plagiarism False Positive, Execution Environment Issue), ô nhập giải trình chi tiết kèm bộ đếm ký tự (min 20, max 2000 ký tự), modal xác nhận nộp đơn.
  - Chi tiết đơn: Thẻ tiến trình, văn bản giải trình của sinh viên, quyết định và lời phê của Hội đồng khảo thí.
- **API Endpoints:**
  - `GET /api/student/appeals`: Danh sách đơn của sinh viên.
  - `POST /api/student/appeals`: Gửi đơn khiếu nại mới.
  - `GET /api/student/appeals/{id}`: Xem chi tiết đơn khiếu nại.
- **UI Business Rules:** Ngăn chặn tạo đơn nếu bài nộp đã có khiếu nại đang chờ xử lý (`PENDING`).
- **Verification:** **PASS**.

---

### Nhóm 3: Phân hệ Giảng viên (Teacher Experience)

#### 3.1. Teacher Dashboard (`/teacher/dashboard`)
- **Tên tính năng & Route:** Faculty Management Dashboard — `/teacher/dashboard`
- **Vai trò & Quyền hạn:** Giảng viên (`ROLE_TEACHER`).
- **Mục tiêu người dùng:** Nắm bắt toàn diện các chỉ số lớp học: tổng số bài thi, bài nộp cần chấm, bài bị cờ đạo văn AST, khiếu nại đang chờ xử lý.
- **Entry point & Controls:**
  - 4 Thẻ KPI: Live data từ các API teacher.
  - Nút "Export Reports": Xuất file CSV báo cáo khoa tổng hợp các bài thi (kiểm tra dữ liệu trước khi tải).
  - Nút "+ Create New Exam": Điều hướng sang `/teacher/exams?action=create` mở sẵn modal tạo bài thi.
  - Bảng "Recent Active Exams" và "Submissions Awaiting Grading".
- **Verification:** **PASS**.

#### 3.2. Teacher Exam Management (`/teacher/exams`, `/teacher/exams/[id]`)
- **Tên tính năng & Route:** Course Exam Administration — `/teacher/exams`, `/teacher/exams/[id]`
- **Vai trò & Quyền hạn:** Giảng viên phụ trách lớp học.
- **Mục tiêu người dùng:** Tạo kỳ thi mới, chỉnh sửa thông tin kỳ thi, theo dõi danh sách sinh viên nộp bài.
- **API Endpoints:**
  - `GET /api/teacher/exams`: Danh sách kỳ thi của Giảng viên.
  - `POST /api/teacher/exams`: Tạo kỳ thi mới (validate title, classId, date, duration > 0).
  - `GET /api/teacher/exams/{id}`: Chi tiết kỳ thi và danh sách bài nộp.
  - `PUT /api/teacher/exams/{id}`: Cập nhật thông tin kỳ thi (title, duration, date, status).
- **UI Controls:** Modal tạo kỳ thi, modal chỉnh sửa kỳ thi, bộ lọc theo lớp và từ khóa, bảng bài nộp với nút "Score / View Code".
- **Verification:** **PASS**.

#### 3.3. Teacher Submission Grading & Rubric Override (`/teacher/submissions`, `/teacher/scoring/[id]`)
- **Tên tính năng & Route:** Grading Workspace & Live Rubric Scoring — `/teacher/submissions`, `/teacher/scoring/[id]`
- **Vai trò & Quyền hạn:** Giảng viên phụ trách bài nộp.
- **Mục tiêu người dùng:** Đọc mã nguồn bài làm của sinh viên, chấm điểm theo các tiêu chí rubric, ghi đè điểm số chính thức và lưu kết quả.
- **API Endpoints:**
  - `GET /api/teacher/submissions`: Danh sách toàn bộ bài nộp của các lớp do GV phụ trách.
  - `GET /api/teacher/submissions/{id}/scoring`: Dữ liệu rubric, điểm số hiện tại và mã nguồn bài nộp.
  - `PUT /api/teacher/submissions/{id}/scoring`: Lưu kết quả chấm điểm.
- **UI Controls & Business Logic:**
  - **Live Rubric Calculation:** Khi Giảng viên nhập điểm từng tiêu chí trong rubric, điểm tổng cộng tự động nhảy theo thời gian thực mà không cần reload.
  - **Score Override:** Checkbox kích hoạt ghi đè điểm chính thức (`overrideTotalScore`), mở modal xác nhận yêu cầu nhập lý do giải trình.
  - Nút "Save Scoring": Disabled khi đang submit chống double-click, hiển thị Sonner toast khi lưu thành công và cập nhật bài thi sang trạng thái `GRADED`.
- **Verification:** **PASS**.

#### 3.4. Teacher AST Plagiarism Matrix & Side-by-Side Comparison (`/teacher/plagiarism`, `/teacher/plagiarism/[id]`, `/teacher/plagiarism/[id]/compare`)
- **Tên tính năng & Route:** AST Plagiarism Diagnostics & Code Comparison — `/teacher/plagiarism/*`
- **Vai trò & Quyền hạn:** Giảng viên phụ trách kỳ thi.
- **Mục tiêu người dùng:** Phát hiện các cặp bài thi có cấu trúc cây cú pháp trừu tượng tương đồng cao, đối chiếu mã nguồn song song 2 cột, xem bằng chứng và kích hoạt phân tích AI nếu có.
- **API Endpoints:**
  - `GET /api/teacher/plagiarism`: Lấy ma trận các cặp bài thi có độ tương đồng AST.
  - `GET /api/teacher/plagiarism/{id}`: Chi tiết bằng chứng vi phạm của cặp bài thi.
  - `GET /api/teacher/plagiarism/{id}/compare`: Dữ liệu so sánh mã nguồn đối ứng giữa 2 bài nộp.
  - `POST /api/teacher/plagiarism/{id}/ai/code-review`: Phân tích mã nguồn AI.
- **UI Controls & Minh bạch Kỹ thuật:**
  - Nút **"Sync Similarity Matrix"**: Gọi `refetch()` cập nhật các bản ghi mới nhất từ DB, toast thông báo đồng bộ thành công.
  - **Manual Cluster Re-Analysis:** **BLOCKED** trên Backend vì AST Engine chạy tự động ngầm theo sự kiện nộp bài, không có endpoint re-cluster bằng tay.
  - **AI Structural Audit:** Gọi API thật. Khi Backend `StubAiClient` trả về lỗi, Modal [`AiAnalysisModal`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/plagiarism/components/AiAnalysisModal.tsx) hiển thị trung thực *"AI Assistant Not Configured"*, **tuyệt đối không báo thành công giả**.
  - **Side-by-Side Diff:** 2 cột code dark IDE, hiển thị số dòng tương ứng và đánh dấu viền màu đỏ nổi bật trên các đoạn mã trùng khớp AST.
- **Verification:** **PASS** (cho Sync, Diff, Evidence) | **BLOCKED** (cho Manual Re-Analysis & AI Provider).

#### 3.5. Teacher Appeal Resolution (`/teacher/appeals`, `/teacher/appeals/[id]`)
- **Tên tính năng & Route:** Faculty Appeal Adjudication — `/teacher/appeals`, `/teacher/appeals/[id]`
- **Vai trò & Quyền hạn:** Giảng viên phụ trách lớp học.
- **Mục tiêu người dùng:** Xem xét các đơn khiếu nại của sinh viên, ghi nhận xét và đưa ra quyết định Chấp thuận (`APPROVE`) hoặc Từ chối (`REJECT`).
- **API Endpoints:**
  - `GET /api/teacher/appeals`: Danh sách khiếu nại theo bộ lọc trạng thái (ALL, PENDING, APPROVED, REJECTED).
  - `PUT /api/teacher/appeals/{id}/resolve`: Quyết định khiếu nại `{ decision: "APPROVED" | "REJECTED", reviewerNotes: string }`.
- **UI Business Rules:**
  - **Decision Locking:** Ngay khi khiếu nại đã được giải quyết, form tự động khóa toàn bộ nút và ô nhập, hiển thị banner xác nhận quyết định bất biến.
- **Verification:** **PASS**.

---

### Nhóm 4: Phân hệ Quản trị viên (Admin Features)

#### 4.1. Admin Management Routes (`/admin/*`)
- **Tên tính năng & Route:** Admin Console — `/admin/dashboard`, `/admin/users`, `/admin/classes`, `/admin/exams`
- **Vai trò & Quyền hạn:** Quản trị viên hệ thống (`ROLE_ADMIN`).
- **Phân loại phạm vi:** Đã xây dựng khung giao diện cơ bản nhưng được phân loại **Out of Scope cho Milestone 2** theo quyết định của Product Owner.
- **Verification:** **NOT VERIFIED**.

---

## 3. Bảng Tổng Hợp Trạng Thái Hoàn Thiện (Summary Matrix)

| Nhóm Tính năng | Tổng số Chức năng | PASS | BLOCKED | NOT VERIFIED | Ghi chú Trọng tâm |
|---|:---:|:---:|:---:|:---:|---|
| **Xác thực & Layout** | 6 | 4 | 2 | 0 | Chặn 2 luồng khôi phục mật khẩu do thiếu SMTP mailer. |
| **Sinh viên (Student)** | 10 | 10 | 0 | 0 | Hoàn tất 100%: thi, nộp bài, xem điểm, khiếu nại, xuất CSV. |
| **Giảng viên (Teacher)**| 12 | 10 | 2 | 0 | Hoàn tất 100% quản lý thi, chấm rubric, đối chiếu AST; chặn AI stub & re-cluster. |
| **Quản trị (Admin)** | 2 | 0 | 0 | 2 | Nằm ngoài phạm vi Milestone 2. |
| **TỔNG CỘNG** | **30** | **24 (80%)** | **4 (13.3%)** | **2 (6.7%)** | **Không có lỗi tương tác (0 FAIL)** |
