# CODEASSESS — FINAL ACCEPTANCE AUDIT (MILESTONE 2)

**Dự án:** CodeAssess — Intelligent Programming Assessment & AST Plagiarism Detection Platform  
**Phiên bản kiểm toán:** Milestone 2 Final Acceptance Review  
**Ngày thực hiện:** 15/10/2026  
**Vai trò:** Senior Software Engineer + Lead QA Engineer + Security Auditor  
**Quy tắc:** Không commit/push Git, không reset Git, không xóa dữ liệu cơ sở dữ liệu production, không bỏ qua test.

---

## 1. Tuyên bố về Nguồn Gốc và Tính Xác Thực của Đặc tả Nghiệp vụ (SRS Baseline Statement)

> [!WARNING]
> **TÌNH TRẠNG XÁC THỰC TÀI LIỆU SRS:**
> Quá trình quét toàn bộ cây thư mục repository không tìm thấy bất kỳ tệp vật lý độc lập nào có tiêu đề chính thức là SRS (ví dụ: `SRS.pdf`, `SRS.docx`, `Software_Requirements_Specification.md`).
> 
> **Do đó, việc đối chiếu với tài liệu SRS chính thức được phê duyệt CHƯA ĐƯỢC XÁC MINH ĐẦY ĐỦ (UNVERIFIED AGAINST APPROVED PHYSICAL SRS DOCUMENT).**
> 
> Toàn bộ các Use Case và quy chuẩn nghiệp vụ trong báo cáo này được tái thiết lập (reverse-engineered) và tổng hợp từ:
> 1. Đặc tả khoa học thuật toán AST: [`dataset/v2/DATASET_V2_SPECIFICATION.md`](file:///d:/AST%20Plagiarism%20Detection/dataset/v2/DATASET_V2_SPECIFICATION.md).
> 2. Lược đồ cơ sở dữ liệu Flyway Migration từ `V1` đến `V5` (`src/main/resources/db/migration/`).
> 3. Các API Controllers, DTOs, Spring Security Configuration và Next.js Frontend Routes hiện hữu.
> 4. Các biên bản kiểm toán trước đó: [`BUSINESS_LOGIC_AUDIT.md`](file:///d:/AST%20Plagiarism%20Detection/BUSINESS_LOGIC_AUDIT.md) và [`E2E_TEST_REPORT.md`](file:///d:/AST%20Plagiarism%20Detection/E2E_TEST_REPORT.md).

---

## 2. Bảng Phân loại Ma trận Nghiệm thu Chi tiết (Acceptance Matrix)

Các hạng mục được phân định rành mạch thành 4 nhóm trạng thái:
- **[A] ĐÃ KIỂM THỬ VÀ PASS (Tested & Passed):** Có bằng chứng log thực thi kiểm thử tự động, `@DataJpaTest`, hoặc video session artifact.
- **[B] ĐÃ KIỂM TRA BẰNG CODE REVIEW (Code Review Verified):** Đã phân tích mã nguồn, schema, config và luồng dữ liệu; tính đúng đắn được khẳng định qua cấu trúc code.
- **[C] CHƯA KIỂM THỬ (Untested / Verification Gap):** Tính năng hoặc điều kiện biên chưa có bài test tự động bao phủ hoặc chưa kích hoạt trong môi trường kiểm thử.
- **[D] NGOÀI PHẠM VI MILESTONE 2 (Out of Scope):** Tính năng thuộc lộ trình giai đoạn sau hoặc chủ động giới hạn theo Milestone.

| Mã Hạng mục | Use Case / Yêu cầu Nghiệp vụ | API Endpoint | UI Route | Vai trò | Bằng chứng Xác thực | Phân loại Trạng thái | Ghi chú & Khoảng trống Còn lại |
|:---:|---|---|---|:---:|---|:---:|---|
| **REQ-01** | Đăng nhập hệ thống (Session HttpOnly) | `POST /api/auth/login` | `/login` | Public | `SecurityConfigTest`, `CodeAssessSecurityE2EQATest` | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Cookie `codeassess_session` HttpOnly |
| **REQ-02** | Thu hồi phiên khi Đăng xuất | `POST /api/auth/logout` | All | Authenticated | `acceptance_live_check.cjs` (Status 200) | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Hủy session và xóa cookie |
| **REQ-03** | Khóa phiên đồng thời / Session Clustered | N/A | N/A | All | Code review `application.yml` | **[B] CODE REVIEW** | Quản lý in-memory Tomcat, chưa có Redis |
| **REQ-04** | Giảng viên xem danh sách lớp học | `GET /api/teacher/classes` | `/teacher/classes` | TEACHER | `TeacherClassControllerTest`, Browser E2E | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Đạt 200 OK |
| **REQ-05** | Giảng viên tạo kỳ thi mới | `POST /api/teacher/exams` | `/teacher/exams` | TEACHER | `TeacherExamControllerTest`, Browser artifact | **[A] ĐÃ KIỂM THỬ VÀ PASS** | 1 Exam = 1 programming task |
| **REQ-06** | Giảng viên chỉnh sửa thời lượng kỳ thi | `PUT /api/teacher/exams/{id}` | `/teacher/exams/{id}` | TEACHER | `TeacherExamControllerTest`, persistence check | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Sửa duration 60 -> 120 phút |
| **REQ-07** | Chặn sửa kỳ thi sau khi đã có bài nộp | `PUT /api/teacher/exams/{id}` | `/teacher/exams/{id}` | TEACHER | Code review `TeacherExamService.java` | **[C] CHƯA KIỂM THỬ** | Chưa có test kiểm tra chặn sửa khi đã có submission |
| **REQ-08** | Sinh viên xem danh sách bài thi | `GET /api/student/exams` | `/student/exams` | STUDENT | `StudentExamControllerTest`, Browser artifact | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Chỉ hiện bài thi lớp đã đăng ký |
| **REQ-09** | Sinh viên làm bài & Nộp bài Java | `POST /api/student/submissions` | `/student/exams/{id}/take` | STUDENT | `StudentSubmissionIntegrationTest`, Monaco | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Trả về 201 Created |
| **REQ-10** | Ranh giới giao dịch nộp bài (@Transactional AFTER_COMMIT) | Event `SubmissionCreatedEvent` | N/A | SYSTEM | `TxBoundaryVerificationTest` (PASS 2 lần) | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Lỗi AST không làm rollback bài nộp |
| **REQ-11** | Sinh viên xem kết quả & điểm ghi đè | `GET /api/student/submissions/{id}/result` | `/student/submissions` | STUDENT | `SubmissionControllerTest`, persistence check | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Hiển thị điểm 95.0, status GRADED |
| **REQ-12** | Giảng viên xem bài nộp của lớp | `GET /api/teacher/exams/{id}/submissions` | `/teacher/exams/{id}` | TEACHER | `TeacherSubmissionControllerTest`, Browser | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Dữ liệu đúng tên, email, điểm |
| **REQ-13** | Giảng viên chấm điểm & ghi đè điểm số | `PUT /api/teacher/submissions/{id}/scoring` | `/teacher/submissions/{id}` | TEACHER | `TeacherScoringControllerTest`, Browser | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Cập nhật `overrideScore`, chuyển GRADED |
| **REQ-14** | Pipeline AST (Normalizer, K-gram, Winnowing, Jaccard) | Engine nội bộ | N/A | SYSTEM | Unit tests: `Normalizer`, `Winnower`, `Jaccard` | **[A] ĐÃ KIỂM THỬ VÀ PASS** | $K=5, W=4, 70\%$, FNV-1a chuẩn xác |
| **REQ-15** | Giảng viên truy vấn danh sách gian lận JPQL (`BLA-03`) | `GET /api/teacher/plagiarism` | `/teacher/plagiarism` | TEACHER | `SimilarityPairRepositoryDataJpaTest` | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Lọc DB `WHERE teacher_id=?`, không in-memory |
| **REQ-16** | Giảng viên so sánh AST Diff 2 bài vi phạm | `GET /api/teacher/plagiarism/{id}/compare` | `/teacher/plagiarism/{id}` | TEACHER | `TeacherPlagiarismControllerTest`, Browser | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Hiển thị 2 editor song song, 7 đoạn AST |
| **REQ-17** | Cờ gian lận là tín hiệu tham vấn, không tự trừ điểm | N/A (Business Rule) | `/teacher/plagiarism` | TEACHER | Code review `SimilarityMatrixService.java` | **[B] CODE REVIEW** | `is_flagged = true` không đổi điểm |
| **REQ-18** | Sinh viên tạo khiếu nại (Appeal Creation) | `POST /api/student/appeals` | `/student/appeals/new` | STUDENT | `StudentAppealServiceTest`, Browser | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Khóa bi quan chống gửi trùng |
| **REQ-19** | Tiêu chí nộp khiếu nại bài PENDING (`BLA-10`) | `POST /api/student/appeals` | `/student/appeals/new` | STUDENT | Code review `StudentAppealService.java` | **[B] CODE REVIEW** | Giữ nguyên; chờ quyết định SRS |
| **REQ-20** | Giảng viên xem danh sách khiếu nại | `GET /api/teacher/appeals` | `/teacher/appeals` | TEACHER | `TeacherAppealControllerTest`, Browser | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Lọc tab ALL, PENDING, APPROVED, REJECTED |
| **REQ-21** | Giảng viên phê duyệt khiếu nại (Approve) | `PUT /api/teacher/appeals/{id}/resolve` | `/teacher/appeals/{id}` | TEACHER | `TeacherAppealControllerTest`, Browser | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Lưu trạng thái APPROVED + reviewerNotes |
| **REQ-22** | Giảng viên từ chối khiếu nại (Reject) | `PUT /api/teacher/appeals/{id}/resolve` | `/teacher/appeals/{id}` | TEACHER | `TeacherAppealControllerTest`, Browser | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Lưu trạng thái REJECTED + reviewerNotes |
| **REQ-23** | Chống sửa khiếu nại đã duyệt (Anti-tamper 409) | `PUT /api/teacher/appeals/{id}/resolve` | `/teacher/appeals/{id}` | TEACHER | `TeacherAppealControllerTest` (HTTP 409) | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Trả về HTTP 409 Conflict |
| **REQ-24** | Sinh viên xem trạng thái khiếu nại đã giải quyết | `GET /api/student/appeals/{id}` | `/student/appeals/{id}` | STUDENT | `StudentAppealServiceTest`, Browser | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Badge Appeal Accepted / Rejected |
| **REQ-25** | Bảo vệ IDOR giữa 2 Giảng viên (Masking 404) | All Teacher Endpoints | N/A | TEACHER | `CodeAssessSecurityE2EQATest` | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Trả về 404 Not Found khi truy cập lớp khác |
| **REQ-26** | Bảo vệ IDOR giữa 2 Sinh viên (Masking 404/403) | All Student Endpoints | N/A | STUDENT | `CodeAssessSecurityE2EQATest` | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Trả về 404/403, không lộ mã nguồn bạn học |
| **REQ-27** | Phân quyền vai trò chéo (Cross-Role 403) | All Endpoints | N/A | All Roles | `CodeAssessSecurityE2EQATest` | **[A] ĐÃ KIỂM THỬ VÀ PASS** | SV gọi API GV -> 403; GV gọi API SV -> 403 |
| **REQ-28** | Thực thi Docker Sandbox an toàn | `DockerSandboxService` | N/A | SYSTEM | `DockerSandboxIntegrationTest` (12/12 PASS) | **[A] ĐÃ KIỂM THỬ VÀ PASS** | Timeout 3s, output size, concurrency limit |
| **REQ-29** | Giới hạn tài nguyên cgroup cấp nhân (OOM-killer) | Docker Host | N/A | SYSTEM | DockerSandboxIntegrationTest javadoc | **[C] CHƯA KIỂM THỬ** | Cần công cụ audit nhân OS cấp máy chủ |
| **REQ-30** | Thu hồi Linux Capabilities / No-new-privileges | Docker Host | N/A | SYSTEM | DockerSandboxIntegrationTest javadoc | **[C] CHƯA KIỂM THỬ** | Cần kiểm toán binary setuid cấp máy chủ |
| **REQ-31** | Phân hệ Quản trị Hệ thống Toàn diện (`/admin/*`) | `/api/admin/*` | `/admin/*` | ADMIN | `AdminUserControllerTest` (Unit test) | **[D] OUT OF SCOPE** | Chưa có giao diện E2E Admin hoàn chỉnh |
| **REQ-32** | Ngân hàng Đề thi Đa câu hỏi (Question Bank) | N/A | N/A | TEACHER | Database Schema Inspection | **[D] OUT OF SCOPE** | Schema hiện tại là 1 Exam = 1 programming task |

---

## 3. Kiểm toán Mã nguồn và Toàn vẹn Hệ thống (Workspace Integrity Audit)

Đã đối chiếu toàn bộ `git status --short` và nội dung diff chi tiết:

1. **Authentication & Authorization:**
   - [`SecurityConfig.java`](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/common/config/SecurityConfig.java): Không bị sửa đổi ngoài phạm vi. Cookie session `codeassess_session` được bảo vệ `HttpOnly=true`, `SameSite=Lax`.
   - Các controller của giảng viên (`TeacherExamController`, `TeacherSubmissionController`, `TeacherPlagiarismController`, `TeacherAppealController`) đều có `@PreAuthorize("hasRole('TEACHER')")` và kiểm tra quyền sở hữu lớp (Masking 404).
2. **Database Configuration:**
   - `src/main/resources/application.yml` (Production): **Không bị thay đổi**. Datasource giữ nguyên `DB_NAME: plagiarism_system`, pool size mặc định của Spring Boot.
   - `src/test/resources/application.yml` (Test): Được định tuyến tới `DB_NAME: plagiarism_system_test`, bổ sung `hikari.maximum-pool-size: 3` và bật Flyway test.
   - Cơ chế fail-fast: [`DatabaseSafetyGuard.java`](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/test/java/com/codeassess/testconfig/DatabaseSafetyGuard.java) chặn đứng mọi thao tác test nếu catalog là `plagiarism_system`. Dữ liệu dev/demo được bảo toàn 100%.
3. **Flyway Migrations:**
   - Toàn bộ 5 migration gốc (`V1__initial_schema.sql` đến `V5__create_appeals_table.sql`) giữ nguyên vẹn. Không có file migration nào bị sửa hay thêm mới ngoài dự kiến.
4. **Docker Sandbox Service:**
   - [`DockerSandboxService.java`](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/sandbox/service/DockerSandboxService.java) hoạt động ổn định, 12/12 integration tests chạy thực tế trên Docker Engine và hoàn thành thành công trong 33.39 giây.
5. **Appeals Feature:**
   - Bổ sung controller và service cho giảng viên (`TeacherAppealController`, `TeacherAppealService`) bảo toàn đúng contract `APPROVED`/`REJECTED`, khóa 409 khi sửa lại, và che giấu 404 khi truy cập appeal của lớp khác.
6. **Kiểm tra Secret / Thông tin Nhạy cảm:**
   - Không có private key, API token hay mật khẩu thực nào bị đưa vào code. `git diff --check` sạch hoàn toàn (exit code 0).

---

## 4. Danh sách Bằng chứng Còn Thiếu để Đạt Chuẩn Production-Ready

Hệ thống **CHƯA ĐỦ ĐIỀU KIỆN ĐỂ NÂNG CẤP LÊN TRẠNG THÁI PRODUCTION-READY**. Để đạt được mức độ sẵn sàng sản phẩm hoàn chỉnh, bắt buộc phải có thêm các bằng chứng sau:

1. **Văn bản SRS Chính thức Được Phê duyệt (Signed/Approved SRS):**
   - Cần có tài liệu SRS được ký duyệt chính thức để đối chiếu từng mã Use Case (UC-xx) thay vì suy diễn từ codebase.
2. **Quyết định Nghiệp vụ Chính thức cho `BLA-10`:**
   - Xác nhận bằng văn bản từ Product Owner về việc: Sinh viên có được phép nộp appeal khi bài làm chưa chấm (`PENDING`) hay bắt buộc phải chờ có điểm (`GRADED`).
3. **Kiểm thử Tải và Căng thẳng (Load & Stress Testing):**
   - Chưa có bằng chứng kiểm thử chịu tải đồng thời (ví dụ: 100 sinh viên cùng nộp bài cùng lúc trong 1 phút) đối với hàng đợi phân tích AST và Docker sandbox runner.
4. **Kiểm toán Bảo mật Cấp Nhân Hệ Điều Hành (Kernel-Level Sandbox Auditing):**
   - Cần kiểm chứng cơ chế cgroup hard OOM-killer và khả năng ngăn chặn leo thang đặc quyền (privilege escalation) với công cụ kiểm toán bảo mật chuyên biệt trên máy chủ Linux chạy Docker daemon.
5. **Kiểm thử E2E Toàn diện cho Phân hệ Quản trị Viên (`/admin/*`):**
   - Phân hệ Admin cần được hoàn thiện giao diện người dùng và kiểm thử E2E trước khi phát hành cho môi trường vận hành thực tế.
6. **Kiểm thử Chặn Sửa Kỳ thi Sau khi Có Bài Nộp (`REQ-07`):**
   - Cần bổ sung test case tự động xác minh rằng giảng viên không thể chỉnh sửa đề bài hoặc thời lượng sau khi đã có sinh viên bắt đầu nộp bài.

---

## 5. Kết luận Nghiệm thu Chính thức

* **Nghiệm thu Milestone 2:** **ACCEPTED WITH KNOWN LIMITATIONS**
* **Trạng thái Production-Ready:** **NOT READY (Cần bổ sung 6 bằng chứng tại Mục 4)**
