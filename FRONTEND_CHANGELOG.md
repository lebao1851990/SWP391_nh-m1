# CODEASSESS FRONTEND — NHẬT KÝ THAY ĐỔI (FRONTEND CHANGELOG)

**Dự án:** CodeAssess — Intelligent Programming Assessment & AST Plagiarism Detection Platform  
**Phiên bản:** Release Milestone 2 (UI/UX Redesign, Motion & Complete Functionality)  
**Ngày cập nhật:** 15/10/2026  

---

## 1. Tóm tắt Đợt Cải tiến

Đợt cập nhật này tập trung vào 4 mục tiêu then chốt:
1. **Hoàn thiện 100% chức năng tương tác (Functional Completeness):** Mọi nút bấm, form nhập, modal, dropdown, và bộ lọc đều có hành vi rõ ràng, kết nối API thật, có cơ chế bảo vệ double-click và phản hồi thông báo toast/alert.
2. **Hệ thống chuyển trang & vi chuyển động (Motion System):** Triển khai transition Next.js App Router 180ms (`template.tsx`), instant skeleton loading (`loading.tsx`), hỗ trợ đầy đủ `prefers-reduced-motion` chống nháy trắng và giật layout.
3. **Nâng cấp thẩm mỹ UI/UX (Modern Academic SaaS Redesign):** Bảng màu slate/blue cao cấp, thay thế 100% icon bằng Lucide SVG vector, tối ưu hóa không gian hiển thị code IDE tối màu.
4. **Minh bạch hóa chức năng thật vs chức năng bị chặn (Honest Engineering):** Phân định rạch ròi giữa các tính năng sẵn sàng (PASS) và các tính năng bị chặn từ hạ tầng Backend/AI/SMTP (BLOCKED).

> [!IMPORTANT]
> **Cam kết an toàn dữ liệu & Quy tắc Git:**  
> Toàn bộ quá trình nâng cấp Frontend tuân thủ nghiêm ngặt nguyên tắc: **Không commit, không push và không reset Git**. Không sửa đổi Backend API contracts và không tạo dữ liệu giả để che lỗi.

---

## 2. Danh mục Chi tiết Các File Đã Thay Đổi (Modified Files)

### 2.1. Cấu trúc Layout Chung, Motion & Điều hướng
- [`src/app/globals.css`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/app/globals.css):
  - Định nghĩa keyframes `@keyframes page-enter` (180ms, opacity + translate).
  - Khai báo class `.page-transition` với `will-change`.
  - Bổ sung `@media (prefers-reduced-motion: reduce)` vô hiệu hóa animation toàn cục cho người dùng nhạy cảm chuyển động.
- [`src/app/teacher/template.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/app/teacher/template.tsx) & [`src/app/student/template.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/app/student/template.tsx):
  - Next.js App Router templates áp dụng chuyển trang mượt mà cho toàn bộ tuyến Teacher và Student.
- [`src/app/teacher/loading.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/app/teacher/loading.tsx) & [`src/app/student/loading.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/app/student/loading.tsx):
  - Giao diện skeleton loading tức thì, mô phỏng đúng cấu trúc banner, metric cards và table, ngăn chặn white flash.
- [`src/components/layout/sidebar.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/components/layout/sidebar.tsx):
  - Thay thế icon Material Text bằng Lucide React SVG (`LayoutDashboard`, `BookOpen`, `FileCheck2`, `ShieldAlert`, `Scale`, `GraduationCap`).
  - Nâng cấp trạng thái active route với dải màu `primary/10` viền bo mềm.
  - Bổ sung chỉ báo trạng thái máy chủ AST Engine.
- [`src/components/layout/header.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/components/layout/header.tsx):
  - Tích hợp ô tìm kiếm toàn cục phản hồi phím `Enter` và phím tắt `⌘K` / `Ctrl+K`.
  - **Điều hướng tìm kiếm thông minh theo ngữ cảnh:** Tự động phát hiện trang hiện tại để điều hướng tới `?search=...` của Submissions, Plagiarism, Appeals hoặc Exams.
  - Thay thế icon Bell và Help bằng Popover giám sát tài nguyên (CPU, RAM, AST queue) và Modal quy chế liêm chính học thuật.
  - Xây dựng Profile Dropdown Menu với thông tin người dùng và nút Đăng xuất 1 chạm.
- [`src/features/auth/components/logout-button.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/auth/components/logout-button.tsx):
  - Chuẩn hóa gọi `authApi.logout()`, hiển thị Sonner toast và chuyển về `/login`.

### 2.2. Trang Tổng quan (Dashboard)
- [`src/features/dashboard/components/TeacherDashboardView.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/dashboard/components/TeacherDashboardView.tsx):
  - Xóa bỏ hoàn toàn 7 khối placeholder "Integration Pending".
  - Tích hợp dữ liệu thật từ `useTeacherExams`, `useTeacherSubmissions`, `usePlagiarismList`, `useTeacherAppeals`.
  - Thêm chức năng xuất báo cáo tổng quan ra file CSV ("Export Reports") kèm kiểm tra dữ liệu rỗng.
  - Kích hoạt nút "+ Create New Exam" chuyển hướng tới trang bài thi kèm mở modal tạo bài.
  - Bổ sung bảng các bài thi gần nhất và danh sách bài nộp cần chấm điểm.
- [`src/features/dashboard/components/StudentDashboardView.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/dashboard/components/StudentDashboardView.tsx):
  - Loại bỏ các thẻ card lồng nhau trùng lặp gây rối mắt.
  - Chuẩn hóa typography và icon Lucide.
  - Bổ sung thẻ chứng nhận liêm chính học thuật và chỉ số AST.

### 2.3. Phân hệ Quản lý Kỳ thi (Exams)
- [`src/features/exams/components/TeacherExamsView.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/exams/components/TeacherExamsView.tsx):
  - Hỗ trợ tham số URL `search` và `action=create` để mở modal tự động từ Dashboard.
  - Modal tạo kỳ thi mới với date picker, kiểm tra duration > 0, chọn lớp học và Sonner toast.
  - Khởi tạo state bằng lazy initializers, loại bỏ hoàn toàn cảnh báo setState trong effect.
- [`src/features/exams/components/TeacherExamDetailView.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/exams/components/TeacherExamDetailView.tsx):
  - Modal chỉnh sửa thông tin kỳ thi kết nối API `PUT /api/teacher/exams/{id}`.
  - Bộ lọc danh sách bài nộp của kỳ thi theo tên sinh viên và trạng thái.
- [`src/features/exams/components/StudentExamsView.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/exams/components/StudentExamsView.tsx):
  - Tích hợp bộ lọc Tab (Tất cả, Đang diễn ra, Đã hoàn thành).
  - Tìm kiếm nhanh bài thi theo mã môn và tên kỳ thi, đọc URL param.
- [`src/features/exams/components/StudentExamDetailView.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/exams/components/StudentExamDetailView.tsx):
  - Thiết kế lại khung nhập code chuẩn IDE Dark Theme (`bg-slate-950`).
  - Tích hợp bộ đếm số dòng và số lượng ký tự trực tiếp.
  - Modal xác nhận nộp bài phòng ngừa nộp nhầm và chống double-click (`isPending`).
  - Banner cảnh báo nổi bật khi kỳ thi đã đóng hoặc quá hạn.
- [`src/features/exams/api/student-exams.api.ts`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/exams/api/student-exams.api.ts):
  - Chuẩn hóa TypeScript types strict, loại bỏ toàn bộ `any`.

### 2.4. Phân hệ Bài nộp & Chấm điểm (Submissions & Scoring)
- [`src/features/submissions/components/TeacherSubmissionsView.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/submissions/components/TeacherSubmissionsView.tsx):
  - Tích hợp các thẻ thống kê tổng quan (Pending, Testing, Graded).
  - Bộ lọc kết hợp tìm kiếm theo tên SV, mã bài nộp và trạng thái.
  - Hỗ trợ URL search param lazy init.
- [`src/features/submissions/components/StudentSubmissionsView.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/submissions/components/StudentSubmissionsView.tsx):
  - Bảng lịch sử nộp bài của sinh viên, hiển thị điểm số và trạng thái kiểm thử sandbox.
- [`src/features/scoring/components/teacher-scoring-form.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/scoring/components/teacher-scoring-form.tsx):
  - **Tính năng Live Rubric Calculation:** Điểm tổng tự động tính toán theo thời gian thực khi Giảng viên nhập điểm từng tiêu chí.
  - Modal xác nhận ghi đè điểm số chính thức (`overrideTotalScore`) kèm ô nhập lý do.
  - Tích hợp Sonner toast thông báo lưu điểm thành công.
- [`src/features/scoring/components/student-result-view.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/scoring/components/student-result-view.tsx):
  - Banner chứng chỉ kết quả học tập trang trọng.
  - Bảng chi tiết điểm số từng tiêu chí và lời phê của Giảng viên.
  - Nút liên kết trực tiếp để mở đơn khiếu nại.

- [`src/features/plagiarism/api/index.ts`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/plagiarism/api/index.ts):
  - Bổ sung bộ chuẩn hóa dữ liệu `RawPlagiarismItem` trong `getPlagiarismRecords`: tương thích an toàn cả cấu trúc lồng `pair` và cấu trúc phẳng `SimilarityPairDto` trả về từ Backend (`flagged` vs `isFlagged`), loại bỏ hoàn toàn `any`.
- [`src/features/plagiarism/components/TeacherPlagiarismDashboardView.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/plagiarism/components/TeacherPlagiarismDashboardView.tsx):
  - Bổ sung optional chaining `p?.pair?.isFlagged` và kiểm tra null-safe trong bộ lọc `filteredPairs`.
  - Chuẩn hóa nút **"Sync Similarity Matrix"** với `refetch()`, trạng thái xoay và toast thông báo đồng bộ trung thực.
  - Thêm thanh tab lọc: **All Pairs**, **Critical Flagged (>=70%)**, **Moderate (40-69%)**, **Clean (<40%)**.
  - Thêm ô tìm kiếm tác giả, mã SV hoặc cặp đối chiếu.
- [`src/features/plagiarism/components/AiAnalysisModal.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/plagiarism/components/AiAnalysisModal.tsx):
  - Thay thế 100% biểu tượng text bằng Lucide React SVG (`Bot`, `X`, `Loader2`, `Info`, `Sparkles`).
  - Báo lỗi trung thực khi Backend `StubAiClient` trả về lỗi cấu hình AI.
- [`src/features/plagiarism/components/TeacherPlagiarismCompareView.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/plagiarism/components/TeacherPlagiarismCompareView.tsx):
  - Giao diện đối chiếu mã nguồn song song 2 cột với số dòng đối ứng.
  - Highlight các vùng trùng khớp AST cấu trúc bằng đường viền màu tương phản.
- [`src/features/plagiarism/components/TeacherPlagiarismDetailView.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/plagiarism/components/TeacherPlagiarismDetailView.tsx):
  - Hiển thị biểu đồ cây AST vector minh họa cấu trúc vi phạm.
- [`src/features/plagiarism/components/StudentPlagiarismDetailView.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/plagiarism/components/StudentPlagiarismDetailView.tsx):
  - Hiển thị tỷ lệ tương đồng AST minh bạch cho sinh viên kèm link mở ticket giải trình.

### 2.6. Phân hệ Khiếu nại (Appeals)
- [`src/features/appeals/components/TeacherAppealsListView.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/appeals/components/TeacherAppealsListView.tsx):
  - Thanh tab lọc theo trạng thái kèm bộ đếm số lượng bản ghi trên từng tab.
  - Hỗ trợ đọc URL search param lazy init.
- [`src/features/appeals/components/TeacherAppealDetailView.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/appeals/components/TeacherAppealDetailView.tsx):
  - Nút Approve / Reject khiếu nại kèm ghi chú của Hội đồng khảo thí.
  - **Khóa an toàn:** Tự động vô hiệu hóa toàn bộ nút và ô nhập khi đơn đã có quyết định (`APPROVED` hoặc `REJECTED`).
- [`src/features/appeals/components/StudentAppealsListView.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/appeals/components/StudentAppealsListView.tsx):
  - Nút "Export Statement" xuất file CSV tổng hợp các đơn khiếu nại kèm kiểm tra dữ liệu rỗng.
  - Hỗ trợ đọc URL search param lazy init.
- [`src/features/appeals/components/StudentAppealCreateView.tsx`](file:///d:/AST%20Plagiarism%20Detection/Front%20end/src/features/appeals/components/StudentAppealCreateView.tsx):
  - Bộ đếm số ký tự giải trình theo thời gian thực (20-2000 ký tự).
  - Modal xác nhận trước khi nộp đơn chính thức.

---

## 3. Các Chức năng Nghiệp vụ Được Bảo lưu Tuyệt đối (Retained Functionality)

1. **Bảo mật & Phân quyền (Security & Authorization):**
   - Giữ nguyên toàn bộ cơ chế bảo vệ Route của Next.js Middleware và Spring Security 6.
   - Không can thiệp hoặc thay đổi các quyền của Teacher và Student.
2. **Backend API Contracts:**
   - 100% các endpoint của Backend giữ nguyên URL, HTTP Method và định dạng DTO request/response.
3. **Database Schema & Business Logic:**
   - Bảo toàn logic quyết định khiếu nại (`PENDING`, `APPROVED`, `REJECTED`), quy trình lưu điểm chấm (`overrideTotalScore`), và logic phân tích tương đồng cây cú pháp trừu tượng AST.

---

## 4. Các Vấn đề Kiến nghị cho Backend & Product Owner

1. **SMTP Gateway cho Khôi phục Mật khẩu:** Cần cấu hình dịch vụ mailer và bảng lưu trữ token khôi phục mật khẩu để kích hoạt luồng `/forgot-password` và `/reset-password`.
2. **AI Provider Configuration:** Cần cung cấp API key (Gemini hoặc OpenAI) trong `application.yml` để thay thế `StubAiClient` và kích hoạt tính năng AI Forensic Code Review.
3. **Manual AST Re-clustering Endpoint:** Nếu muốn hỗ trợ giáo viên chủ động kích hoạt quét lại cụm sinh viên từ giao diện, Backend cần bổ sung endpoint `POST /api/teacher/plagiarism/reanalyze`.
