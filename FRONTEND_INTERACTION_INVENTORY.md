# CODEASSESS FRONTEND — KIỂM KÊ TOÀN BỘ TƯƠNG TÁC (FRONTEND INTERACTION INVENTORY)

**Dự án:** CodeAssess — Intelligent Programming Assessment & AST Plagiarism Detection Platform  
**Ngày kiểm kê:** 15/10/2026  
**Mục tiêu:** Kiểm kê 100% route, màn hình và thành phần tương tác (UI Controls) trên toàn bộ Frontend.

---

## 1. Danh mục Tuyến đường (Route Catalog)

| Route Path | Vai trò (Role) | Component View Tương ứng | Trạng thái API Backend | Phân loại Phạm vi |
|---|:---:|---|:---:|:---:|
| `/login` | Public | `LoginForm` | Sẵn sàng (`POST /api/auth/login`) | **Core Milestone 2** |
| `/register` | Public | `RegisterForm` | Sẵn sàng (`POST /api/auth/register`) | **Core Milestone 2** |
| `/forgot-password` | Public | `ForgotPasswordForm` | Chưa cấu hình SMTP/Reset | **Limitation (Informed)** |
| `/reset-password` | Public | `ResetPasswordForm` | Chưa cấu hình Token flow | **Limitation (Informed)** |
| `/student/dashboard` | Student | `StudentDashboardView` | Sẵn sàng (`GET /api/student/dashboard`) | **Core Milestone 2** |
| `/student/exams` | Student | `StudentExamsView` | Sẵn sàng (`GET /api/student/exams`) | **Core Milestone 2** |
| `/student/exams/[id]` | Student | `StudentExamDetailView` | Sẵn sàng (`GET /api/student/exams/{id}`, `POST /submissions`) | **Core Milestone 2** |
| `/student/submissions` | Student | `StudentSubmissionsView` | Sẵn sàng (`GET /api/student/submissions`) | **Core Milestone 2** |
| `/student/submissions/[id]`| Student | `StudentResultView` | Sẵn sàng (`GET /api/student/submissions/{id}/result`) | **Core Milestone 2** |
| `/student/plagiarism/[id]` | Student | `StudentPlagiarismDetailView`| Sẵn sàng (`GET /api/student/plagiarism/{id}`) | **Core Milestone 2** |
| `/student/appeals` | Student | `StudentAppealsListView` | Sẵn sàng (`GET /api/student/appeals`) | **Core Milestone 2** |
| `/student/appeals/new` | Student | `StudentAppealCreateView` | Sẵn sàng (`POST /api/student/appeals`) | **Core Milestone 2** |
| `/student/appeals/[id]` | Student | `StudentAppealDetailView` | Sẵn sàng (`GET /api/student/appeals/{id}`) | **Core Milestone 2** |
| `/teacher/dashboard` | Teacher | `TeacherDashboardView` | Ghép dữ liệu từ các API Teacher | **Core Milestone 2** |
| `/teacher/exams` | Teacher | `TeacherExamsView` | Sẵn sàng (`GET, POST /api/teacher/exams`) | **Core Milestone 2** |
| `/teacher/exams/[id]` | Teacher | `TeacherExamDetailView` | Sẵn sàng (`GET, PUT /api/teacher/exams/{id}`, `GET submissions`) | **Core Milestone 2** |
| `/teacher/submissions` | Teacher | `TeacherSubmissionsView` | Sẵn sàng (`GET /api/teacher/submissions`) | **Core Milestone 2** |
| `/teacher/submissions/[id]`| Teacher | `TeacherScoringView` | Sẵn sàng (`GET, PUT /api/teacher/submissions/{id}/scoring`)| **Core Milestone 2** |
| `/teacher/scoring/[id]` | Teacher | `TeacherScoringView` | Sẵn sàng (`GET, PUT /api/teacher/submissions/{id}/scoring`)| **Core Milestone 2** |
| `/teacher/plagiarism` | Teacher | `TeacherPlagiarismDashboardView`| Sẵn sàng (`GET /api/teacher/plagiarism`) | **Core Milestone 2** |
| `/teacher/plagiarism/[id]` | Teacher | `TeacherPlagiarismDetailView` | Sẵn sàng (`GET /api/teacher/plagiarism/{id}`) | **Core Milestone 2** |
| `/teacher/plagiarism/[id]/compare` | Teacher | `TeacherPlagiarismCompareView` | Sẵn sàng (`GET /api/teacher/plagiarism/{id}/compare`) | **Core Milestone 2** |
| `/teacher/appeals` | Teacher | `TeacherAppealsListView` | Sẵn sàng (`GET /api/teacher/appeals`) | **Core Milestone 2** |
| `/teacher/appeals/[id]` | Teacher | `TeacherAppealDetailView` | Sẵn sàng (`GET, PUT /api/teacher/appeals/{id}/resolve`) | **Core Milestone 2** |
| `/admin/dashboard` | Admin | `AdminDashboardView` | Sẵn sàng (`GET /api/admin/metrics`) | **Out of Scope M2** |
| `/admin/users` | Admin | `AdminUserListView` | Sẵn sàng (`GET, POST /api/admin/users`) | **Out of Scope M2** |
| `/admin/users/[id]` | Admin | `AdminUserDetailView` | Sẵn sàng (`GET, PUT, DELETE /api/admin/users/{id}`) | **Out of Scope M2** |
| `/admin/classes` | Admin | `AdminClassListView` | Sẵn sàng (`GET, POST /api/admin/classes`) | **Out of Scope M2** |
| `/admin/classes/[id]` | Admin | `AdminClassDetailView` | Sẵn sàng (`GET, PUT, DELETE /api/admin/classes/{id}`) | **Out of Scope M2** |
| `/admin/exams` | Admin | `AdminExamListView` | Sẵn sàng (`GET, POST /api/admin/exams`) | **Out of Scope M2** |
| `/admin/exams/[id]` | Admin | `AdminExamDetailView` | Sẵn sàng (`GET, PUT, DELETE /api/admin/exams/{id}`) | **Out of Scope M2** |
| `/dev/preview` | Dev | `DevPreviewView` | Developer UI Component Catalog | **Development Tool** |

---

## 2. Kiểm kê Chi tiết Từng Thành phần Tương tác (Interaction Inventory by Route)

### 2.1. Common Layout (Header & Sidebar)
| ID | Vị trí | Control | Loại Control | Hành vi Mong đợi | API / Route | Tình trạng Ban đầu | Giải pháp Cải tiến |
|---|---|---|---|---|---|:---:|---|
| **C-01** | Header | Search Input | Text Input (`input`) | Tìm kiếm toàn cục bài thi/khiếu nại theo từ khóa | Client Filter / Route Jump | Có input nhưng không bind phím Enter / search action | Tích hợp submit search chuyển trang tương ứng |
| **C-02** | Header | Học kỳ Badge (`Fall 2026`) | Select / Dropdown | Hiển thị học kỳ hiện tại | Static display | Đặt làm badge thông tin học thuật rõ ràng |
| **C-03** | Header | Notification Bell | Button Icon | Xem thông báo mới | N/A | Không có handler | Hiển thị popover thông báo hệ thống |
| **C-04** | Header | Help / FAQ Button | Button Icon | Mở tài liệu hướng dẫn quy chế thi | N/A | Không có handler | Mở modal / tooltip hướng dẫn quy chế |
| **C-05** | Header | User Profile Menu | Clickable Group | Xem thông tin người dùng và Đăng xuất | `/api/auth/logout` | Menu tĩnh, thiếu dropdown profile/logout | Thêm dropdown menu hiển thị thông tin & nút Logout |
| **C-06** | Sidebar | Logo & Home link | Brand Anchor | Điều hướng về Dashboard theo role | `/{role}/dashboard` | Hoạt động tốt | Nâng cấp logo & badge chuyên nghiệp |
| **C-07** | Sidebar | Navigation Items | Nav Link (`Link`) | Điều hướng giữa các phân hệ, highlight active | Routes tương ứng | Hoạt động, nhưng dùng font Material text thay vì icon chuẩn | Chuyển sang Lucide React icons đồng nhất |
| **C-08** | Sidebar | Logout Button | Button | Thu hồi session và chuyển về `/login` | `POST /api/auth/logout` | Hoạt động tốt | Nâng cấp styling nút Logout cuối sidebar |

---

### 2.2. Authentication (`/login`, `/register`, `/forgot-password`, `/reset-password`)
| ID | Màn hình | Control | Loại Control | Hành vi Mong đợi | API Liên quan | Tình trạng Ban đầu | Giải pháp Cải tiến |
|---|---|---|---|---|---|:---:|---|
| **AUTH-01**| `/login` | Identity Input | Text Input | Nhập email hoặc mã sinh viên/giảng viên | N/A | Hoạt động tốt | Bổ sung helper và auto-format |
| **AUTH-02**| `/login` | Password Input | Password Input | Nhập mật khẩu tài khoản | N/A | Hoạt động tốt | Giữ icon show/hide password |
| **AUTH-03**| `/login` | Toggle Password | Icon Button | Ẩn/Hiện ký tự mật khẩu | Client State | Hoạt động tốt | Hoàn thiện focus và accessibility |
| **AUTH-04**| `/login` | Remember Me Checkbox | Checkbox | Lưu tùy chọn ghi nhớ phiên đăng nhập | Form state | Hoạt động tốt | Căn chỉnh spacing và label |
| **AUTH-05**| `/login` | Forgot Password Link | Link | Chuyển sang màn hình khôi phục mật khẩu | `/forgot-password` | Hoạt động tốt | Điều hướng đúng |
| **AUTH-06**| `/login` | Sign In Button | Submit Button | Gửi request đăng nhập, hiển thị spinner | `POST /api/auth/login` | Hoạt động tốt | Bổ sung double-click guard và Sonner toast |
| **AUTH-07**| `/login` | Register Link | Link | Chuyển sang màn hình đăng ký | `/register` | Hoạt động tốt | Điều hướng đúng |
| **AUTH-08**| `/register`| Full Name Input | Text Input | Nhập họ tên đầy đủ | Form validation | Hoạt động tốt | Validation z.string().min(2) |
| **AUTH-09**| `/register`| Email Input | Email Input | Nhập email tổ chức hợp lệ | Form validation | Hoạt động tốt | Validation email chuẩn |
| **AUTH-10**| `/register`| Role Selector | Radio / Tabs | Chọn vai trò Sinh viên hoặc Giảng viên | Form state | Hoạt động tốt | Badge phân biệt rõ ràng |
| **AUTH-11**| `/register`| Password & Confirm | Inputs | Nhập mật khẩu bảo mật $\ge 10$ ký tự | Form validation | Hoạt động tốt | Hiển thị thanh đo độ mạnh mật khẩu |
| **AUTH-12**| `/register`| Terms Checkbox | Checkbox | Đồng ý điều khoản liêm chính học thuật | Form state | Hoạt động tốt | Validation bắt buộc check |
| **AUTH-13**| `/register`| Submit Register | Submit Button | Gửi yêu cầu đăng ký | `POST /api/auth/register` | Hoạt động tốt | Xử lý lỗi 409 email trùng lặp |
| **AUTH-14**| `/forgot-password` | Send Instructions | Button | Gửi hướng dẫn khôi phục | Chờ cấu hình SMTP | Button disabled có thông báo | Thiết kế banner giải thích rõ ràng và nút quay lại login |
| **AUTH-15**| `/reset-password` | Save Password | Button | Lưu mật khẩu mới | Chờ token flow | Button disabled có thông báo | Thiết kế banner giải thích rõ ràng |

---

### 2.3. Student Portal (`/student/*`)
| ID | Màn hình | Control | Loại Control | Hành vi Mong đợi | API Liên quan | Tình trạng Ban đầu | Giải pháp Cải tiến |
|---|---|---|---|---|---|:---:|---|
| **STU-01** | Dashboard | Stat Cards | Display Cards | Xem số lượng kỳ thi, bài nộp, khiếu nại | `GET /api/student/dashboard` | Hoạt động, có text hardcoded | Dọn sạch text hardcoded, hiển thị số liệu từ API |
| **STU-02** | Dashboard | Upcoming Deadlines | Cards / Links | Xem kỳ thi sắp diễn ra, click để vào thi | `GET /api/student/dashboard` | Hoạt động | Thêm nút bấm "Take Exam" điều hướng trực tiếp |
| **STU-03** | Dashboard | Recent Submissions | Table / Links | Xem bài nộp gần đây và nút View Result | `GET /api/student/dashboard` | Nút View trỏ tới submission | Hiển thị tên bài thi thay vì mã UUID |
| **STU-04** | Exams List | Tabs Bộ lọc | Tab Buttons | Lọc All / Upcoming / Submitted / Completed | Client Filter | Hoạt động tốt | Nâng cấp styling tabs hiện đại |
| **STU-05** | Exams List | Search Input | Text Input | Tìm kiếm kỳ thi theo tên hoặc mã lớp | Client Filter | Hoạt động tốt | Bổ sung nút clear search |
| **STU-06** | Exams List | Status Dropdown | Select | Lọc danh sách theo trạng thái chi tiết | Client Filter | Hoạt động tốt | Tích hợp mượt mà với tabs |
| **STU-07** | Exams List | Nút "Take / View Exam"| Action Link | Chuyển sang màn hình chi tiết/làm bài thi | `/student/exams/[id]` | Hoạt động tốt | Nâng cấp button phân biệt trạng thái |
| **STU-08** | Exams List | Nút "View Result" | Action Link | Xem điểm bài thi đã nộp | `/student/submissions/[id]` | Hoạt động tốt | Nút bấm rõ ràng |
| **STU-09** | Exam Detail | Source Code Editor | Textarea / Editor | Soạn thảo mã nguồn giải thuật Java | Form State | Dùng textarea cơ bản | Nâng cấp giao diện editor dạng IDE tối ưu, syntax monospace |
| **STU-10** | Exam Detail | Cancel Button | Button | Hủy bỏ và quay lại danh sách bài thi | `/student/exams` | Hoạt động tốt | Điều hướng an toàn |
| **STU-11** | Exam Detail | Submit Code Button | Submit Button | Nộp bài làm lên hệ thống | `POST /api/student/submissions` | Hoạt động | Bổ sung modal xác nhận trước khi nộp chống gửi nhầm |
| **STU-12** | Submissions | Search & Status Filter| Input & Select | Lọc danh sách bài đã nộp | Client Filter | Hoạt động tốt | Cải tiến responsive |
| **STU-13** | Submissions | Nút "View Result" | Action Link | Mở chi tiết điểm và nhận xét | `/student/submissions/[id]` | Hoạt động tốt | Điều hướng đúng kết quả |
| **STU-14** | Submissions | Nút "Submit Appeal" | Action Link | Nộp khiếu nại cho bài làm | `/student/appeals/new?submissionId={id}` | Hoạt động tốt | Nút nổi bật khi bài đã có điểm |
| **STU-15** | Submissions | Retry Button | Button | Tải lại danh sách khi gặp lỗi mạng | `refetch()` | Hoạt động tốt | Toast thông báo |
| **STU-16** | Result Detail | Submit Appeal Button | Action Link | Chuyển sang tạo đơn khiếu nại cho bài | `/student/appeals/new?submissionId={id}` | Hoạt động tốt | Truyền đúng `submissionId` |
| **STU-17** | Plagiarism View | Back to Submissions | Action Link | Quay lại danh sách bài nộp | `/student/submissions` | Hoạt động tốt | Điều hướng đúng |
| **STU-18** | Appeals List | Search & Status Filter| Input & Select | Lọc đơn khiếu nại cá nhân | Client Filter | Hoạt động tốt | Bộ lọc PENDING/APPROVED/REJECTED |
| **STU-19** | Appeals List | Reset Filters Button | Icon Button | Xóa toàn bộ bộ lọc tìm kiếm | Client State | Hoạt động tốt | Reset search & filter |
| **STU-20** | Appeals List | Export Statement | Button | Xuất bản sao kê khiếu nại | Client Action | Nút tĩnh không có action | Bổ sung lệnh in/export bảng sao kê định dạng in ấn |
| **STU-21** | Appeal Create | Basis Selector | Radio / Select | Chọn phân loại khiếu nại (AST, Điểm...) | Form State | Hoạt động tốt | Hiển thị mô tả cho từng phân loại |
| **STU-22** | Appeal Create | Explanation Textarea | Textarea | Nhập nội dung giải trình chi tiết | Form State | Hoạt động tốt | Bổ sung bộ đếm ký tự $\ge 20$ ký tự |
| **STU-23** | Appeal Create | Submit Appeal Button | Submit Button | Mở modal xác nhận nộp khiếu nại | Client State | Hoạt động tốt | Mở confirmation dialog |
| **STU-24** | Appeal Create | Confirm Modal Button | Button | Xác nhận gửi yêu cầu lên server | `POST /api/student/appeals` | Hoạt động tốt | Xử lý lỗi 409 chống gửi trùng |
| **STU-25** | Appeal Detail | Back to Appeals Link | Link | Quay lại danh sách đơn khiếu nại | `/student/appeals` | Hoạt động tốt | Điều hướng đúng |

---

### 2.4. Teacher Portal (`/teacher/*`)
| ID | Màn hình | Control | Loại Control | Hành vi Mong đợi | API Liên quan | Tình trạng Ban đầu | Giải pháp Cải tiến |
|---|---|---|---|---|---|:---:|---|
| **TCH-01** | Dashboard | Export Reports Button | Button | Xuất báo cáo tổng quan kỳ thi | Client Action | Nút tĩnh không có action | Bổ sung action xuất dữ liệu / in báo cáo |
| **TCH-02** | Dashboard | + Create New Exam | Button | Mở form tạo kỳ thi mới | `/teacher/exams` | Nút tĩnh không có action | Chuyển hướng sang `/teacher/exams` kèm mở modal tạo |
| **TCH-03** | Dashboard | Stat Cards (5 thẻ) | Cards | Thống kê Exams, Submissions, Flagged... | API tổng hợp | Hardcoded "Integration Pending" | Tích hợp lấy dữ liệu thật từ exams, submissions, plagiarism |
| **TCH-04** | Dashboard | Recent Exams Table | Table | Bảng các kỳ thi gần nhất của GV | `GET /api/teacher/exams` | Hardcoded "Integration Pending" | Render danh sách kỳ thi thật của GV kèm link chi tiết |
| **TCH-05** | Dashboard | Shortcuts Links | Action Links | Điều hướng nhanh tới Exams, Plagiarism | Routes tương ứng | Hoạt động tốt | Nâng cấp visual |
| **TCH-06** | Exams List | "+ Create Exam" | Button | Mở Modal tạo kỳ thi mới | Client State | Hoạt động tốt | Nâng cấp modal form validation |
| **TCH-07** | Exams List | Modal Create Submit | Submit Button | Lưu kỳ thi mới vào cơ sở dữ liệu | `POST /api/teacher/exams` | Hoạt động tốt | Sonner toast thông báo thành công |
| **TCH-08** | Exams List | Search & Class Filter | Input & Select | Lọc danh sách bài thi theo tên và lớp | Client Filter | Hoạt động tốt | Cải tiến giao diện |
| **TCH-09** | Exams List | "View Details" Link | Action Link | Xem chi tiết bài thi và danh sách nộp bài | `/teacher/exams/[id]` | Hoạt động tốt | Điều hướng đúng |
| **TCH-10** | Exam Detail | "Edit Exam" Button | Button | Mở Modal chỉnh sửa bài thi | Client State | Hoạt động tốt | Mở modal điền sẵn dữ liệu |
| **TCH-11** | Exam Detail | Modal Edit Submit | Submit Button | Cập nhật thời lượng/thông tin bài thi | `PUT /api/teacher/exams/{id}` | Hoạt động tốt | Cập nhật DB và hiển thị Sonner toast |
| **TCH-12** | Exam Detail | Submissions Filter | Input & Select | Lọc danh sách bài nộp của kỳ thi | Client Filter | Hoạt động tốt | Lọc theo sinh viên và trạng thái |
| **TCH-13** | Exam Detail | "Score / View Code" | Action Link | Mở màn hình chấm điểm & ghi đè | `/teacher/submissions/[id]` | Hoạt động tốt | Điều hướng đúng |
| **TCH-14** | Submissions | Search & Status Filter| Input & Select | Tìm kiếm bài nộp của tất cả các lớp | Client Filter | Hoạt động tốt | Tìm theo tên, email, bài thi, mã lớp |
| **TCH-15** | Submissions | "Review & Score" Link | Action Link | Điều hướng tới màn hình chấm điểm | `/teacher/submissions/[id]` | Hoạt động tốt | Điều hướng chính xác |
| **TCH-16** | Scoring View | Criteria Score Inputs | Number Inputs | Nhập điểm cho từng tiêu chí đánh giá | Form State | Hoạt động tốt | Tự động tính tổng điểm theo thời gian thực |
| **TCH-17** | Scoring View | Override Score Toggle | Checkbox/Switch | Bật/tắt chế độ ghi đè điểm số | Form State | Hoạt động tốt | Mở ô nhập điểm ghi đè |
| **TCH-18** | Scoring View | Override Score Input | Number Input | Nhập điểm số ghi đè chính thức | Form State | Hoạt động tốt | Ghi nhận `overrideTotalScore` |
| **TCH-19** | Scoring View | Instructor Comments | Textarea | Nhập nhận xét bài làm của sinh viên | Form State | Hoạt động tốt | Lưu vào cơ sở dữ liệu |
| **TCH-20** | Scoring View | Save Scoring Button | Submit Button | Lưu kết quả chấm và chuyển sang GRADED | `PUT /api/teacher/submissions/{id}/scoring` | Hoạt động tốt | Sonner toast thông báo thành công |
| **TCH-21** | Plagiarism | Cluster Re-Analysis | Button | Kích hoạt quét lại toàn bộ ma trận AST | `refetch()` | Nút tĩnh không có action | Kết nối `refetch()` danh sách kèm Sonner toast |
| **TCH-22** | Plagiarism | Filter & Search | Input & Select | Lọc cặp vi phạm theo ngưỡng và mã SV | Client Filter | Hoạt động tốt | Lọc theo cờ `is_flagged` |
| **TCH-23** | Plagiarism | "Compare Code" Link | Action Link | Mở màn hình đối chiếu AST diff song song| `/teacher/plagiarism/[id]/compare` | Hoạt động tốt | Điều hướng đúng |
| **TCH-24** | Plagiarism | "View Evidence" Link | Action Link | Mở màn hình chi tiết bằng chứng | `/teacher/plagiarism/[id]` | Hoạt động tốt | Điều hướng đúng |
| **TCH-25** | Plagiarism Diff| Source Code Diff | Code Viewer | So khớp mã nguồn 2 bài thi theo line | Client View | Hoạt động tốt | Đánh dấu vùng vi phạm AST đỏ |
| **TCH-26** | Plagiarism Diff| "AI Code Review" | Button | Kích hoạt phân tích giải trình AI | `GET /api/teacher/plagiarism/{id}/ai-review` | Hoạt động tốt | Mở modal giải trình AI trung lập |
| **TCH-27** | Appeals List | Status Tabs (4 tabs) | Tab Buttons | Lọc All / Pending / Approved / Rejected | Client State | Hoạt động tốt | Hiển thị số lượng counter trên từng tab |
| **TCH-28** | Appeals List | Search Input | Text Input | Tìm kiếm theo Case ID, tên SV, bài thi | Client Filter | Hoạt động tốt | Lọc tức thời |
| **TCH-29** | Appeals List | "Review Case" Link | Action Link | Mở màn hình chi tiết xử lý khiếu nại | `/teacher/appeals/[id]` | Hoạt động tốt | Điều hướng đúng |
| **TCH-30** | Appeal Detail| Reviewer Notes Input | Textarea | Nhập ghi chú/lý do quyết định | Form State | Hoạt động tốt | Cập nhật state |
| **TCH-31** | Appeal Detail| "Approve Appeal" | Action Button | Chấp thuận khiếu nại | `PUT /api/teacher/appeals/{id}/resolve` | Hoạt động tốt | Chuyển trạng thái `APPROVED`, khóa form |
| **TCH-32** | Appeal Detail| "Reject Appeal" | Action Button | Từ chối khiếu nại | `PUT /api/teacher/appeals/{id}/resolve` | Hoạt động tốt | Chuyển trạng thái `REJECTED`, khóa form |
| **TCH-33** | Appeal Detail| Back to Appeals Link | Link | Quay lại danh sách khiếu nại | `/teacher/appeals` | Hoạt động tốt | Điều hướng an toàn |

---

## 3. Tổng hợp Số lượng Hạng mục Kiểm kê

- **Tổng số Route được kiểm kê:** **32** (gồm 4 Auth, 9 Student, 11 Teacher, 7 Admin, 1 Dev).
- **Tổng số Control tương tác được kiểm kê:** **76 controls**.
- **Số control hoạt động tốt ngay từ đầu:** **69 / 76 (90.8%)**.
- **Số control bị lỗi no-op / static / hardcoded:** **7 / 76 (9.2%)**:
  1. `C-01`: Header Search input chưa có trigger submit.
  2. `C-03` & `C-04`: Notification và Help icon buttons chưa có popover/dialog.
  3. `C-05`: Header profile area chưa có dropdown menu xem thông tin & Logout.
  4. `TCH-01`: Dashboard "Export Reports" không có hành động.
  5. `TCH-02`: Dashboard "+ Create New Exam" không có hành động.
  6. `TCH-03` & `TCH-04`: Teacher Dashboard hiển thị 7 khối "Integration Pending" thay vì tổng hợp dữ liệu thật từ các API Teacher.
  7. `TCH-21`: Plagiarism Dashboard "Run Cluster Re-Analysis" không có hành động.
  8. `STU-20`: Student Appeals "Export Statement" không có hành động.
