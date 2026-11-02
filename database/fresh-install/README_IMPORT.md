# CODEASSESS — HƯỚNG DẪN KHỞI TẠO VÀ NHẬP CƠ SỞ DỮ LIỆU MYSQL MỚI (FRESH INSTALLATION)

Tài liệu này hướng dẫn chi tiết cách tạo và nhập toàn bộ cấu trúc bảng, dữ liệu tham chiếu và dữ liệu mẫu vào một MySQL Server hoàn toàn mới cho hệ thống **CodeAssess**.

> [!WARNING]
> **LƯU Ý AN TOÀN QUAN TRỌNG:** Bộ tệp SQL trong thư mục này **CHỈ DÀNH CHO VIỆC KHỞI TẠO CƠ SỞ DỮ LIỆU MỚI TINH (FRESH INSTALLATION) HOẶC MÔI TRƯỜNG THỬ NGHIỆM ĐỘC LẬP (TEST/STAGING)**.
> **TUYỆT ĐỐI KHÔNG** thực thi bộ script này trên cơ sở dữ liệu đang vận hành thực tế (Production / Live database) để tránh nguy cơ ghi đè dữ liệu hoặc xung đột phiên bản Flyway migration!

---

## 1. Tổng quan bộ file SQL (`database/fresh-install/`)

| Thứ tự | Tên File | Mục đích | Bắt buộc / Tùy chọn |
|:---:|---|---|:---:|
| **1** | [`01_create_database.sql`](file:///d:/AST%20Plagiarism%20Detection/database/fresh-install/01_create_database.sql) | Tạo database `codeassess` với character set `utf8mb4` và collation `utf8mb4_unicode_ci`. Không xóa database cũ. | **Bắt buộc** |
| **2** | [`02_schema.sql`](file:///d:/AST%20Plagiarism%20Detection/database/fresh-install/02_schema.sql) | Tạo toàn bộ **17 bảng vật lý** (khớp hoàn toàn với Flyway V1–V16 và 16 JPA Entities), đầy đủ Primary Key, Foreign Key, Unique Key, Check Constraint và Indexes. | **Bắt buộc** *(khi dùng Phương án 2)* |
| **3** | [`03_seed_reference_data.sql`](file:///d:/AST%20Plagiarism%20Detection/database/fresh-install/03_seed_reference_data.sql) | Chèn bộ tiêu chí chấm điểm chuẩn (Rubric `scoring_criteria`) và tài khoản Quản trị viên Bootstrap (`ADMIN`) với mật khẩu băm BCrypt. Đảm bảo tính idempotent khi chạy lại. | **Bắt buộc** |
| **4** | [`04_demo_data.sql`](file:///d:/AST%20Plagiarism%20Detection/database/fresh-install/04_demo_data.sql) | Dữ liệu mẫu kiểm thử toàn diện phủ đủ 17 bảng: Giảng viên, Sinh viên, Lớp học, Bài thi, Test cases tự động, Bài nộp (Single file & ZIP), Kết quả test, Vết sửa điểm Audit, AST Hashes, Ma trận tương đồng, Bằng chứng đạo văn, Đơn khiếu nại, Reset password token và Worker Coordination. Đảm bảo tính idempotent khi chạy lại. | **Tùy chọn** *(Kiểm thử / Demo)* |
| **5** | [`05_verify_database.sql`](file:///d:/AST%20Plagiarism%20Detection/database/fresh-install/05_verify_database.sql) | Bộ truy vấn kiểm tra toàn diện ở chế độ Read-Only: Số lượng bảng (17 bảng), số dòng từng bảng, kiểm tra toàn vẹn khóa ngoại (0 vi phạm), báo cáo test cases, điểm số và đạo văn. | **Khuyến nghị** |

---

## 2. Danh sách 17 bảng vật lý trong Schema (Đồng bộ đến Migration V16 & 16 JPA Entities)

Hệ thống backend CodeAssess hiện tại sử dụng **16 JPA Entities**, sinh ra tổng cộng **17 bảng cơ sở dữ liệu vật lý** (bao gồm 16 bảng entity và 1 bảng quan hệ n-n `@JoinTable` `exam_classes`):

1. `users`: Tài khoản, vai trò (`ADMIN`, `TEACHER`, `STUDENT`), mật khẩu BCrypt (`V1`, `V1.1`).
2. `password_reset_tokens`: Token đặt lại mật khẩu và thời gian hết hạn (`V13`).
3. `classes`: Lớp học do giảng viên quản lý (`V2`).
4. `class_students`: Danh sách sinh viên thuộc lớp học (Many-to-Many giữa `classes` và `users`) (`V2`).
5. `exams`: Kỳ thi/bài tập lập trình, hỗ trợ Soft-Delete qua cột `deleted_at` (`V1`, `V2`, `V15`).
6. `exam_classes`: Phân bổ kỳ thi cho nhiều lớp học (Bảng join `@ManyToMany` của `Exam.java`) (`V7`).
7. `test_cases`: Bộ test cases chấm điểm tự động (`input_data`, `expected_output`, `points`, `is_hidden`) (`V14`).
8. `submissions`: Bài làm sinh viên, hỗ trợ chấm tự động (`auto_score`, `test_cases_passed/total`), nộp ZIP (`zip_storage_path`), và nhận xét (`instructor_feedback`) (`V1`, `V3`, `V9`, `V12`, `V14`, `V16`).
9. `submission_test_results`: Kết quả chi tiết của từng test case cho bài nộp (`V14`).
10. `ast_fingerprints`: Hash cây cú pháp trừu tượng AST (Winnowing algorithm) phục vụ đối sánh đạo văn (`V1`, `V9`).
11. `similarity_matrix`: Cặp bài nộp tương đồng, tỷ lệ %, cờ nghi vấn và phán quyết giảng viên (`V1`, `V8`, `V10`).
12. `plagiarism_evidence`: Bằng chứng đoạn mã trùng lặp chi tiết (dòng bắt đầu/kết thúc) (`V5`).
13. `scoring_criteria`: Tiêu chí rubric chuẩn (chuẩn hóa thang điểm 10) (`V5`, `V17`).
14. `rule_scores`: Điểm số chi tiết theo từng rubric của bài nộp (`V5`).
15. `grade_audit_logs`: Lịch sử vết chấm điểm bất biến (lưu điểm trước/sau khi ghi đè và lý do) (`V6`).
16. `plagiarism_appeals`: Đơn khiếu nại điểm/đạo văn của sinh viên kèm kết quả xem xét (`V1`, `V4`).
17. `exam_matrix_coordination`: Điều phối hàng đợi phân tán BullMQ/Worker và retry tính toán ma trận đạo văn (`V10`, `V11`).
*(Nếu dùng Flyway quản lý, bảng thứ 18 là `flyway_schema_history`).*

---

## 3. Lựa chọn phương án triển khai

### Phương án 1 (KHUYÊN DÙNG NHẤT): Khởi tạo tự động qua Flyway
Đây là phương án chuẩn mực nhất của các ứng dụng Spring Boot hiện đại, đảm bảo tính toàn vẹn 100% của lịch sử migration và checksum:

1. **Bước 1:** Mở MySQL Workbench hoặc dòng lệnh, chạy file [`01_create_database.sql`](file:///d:/AST%20Plagiarism%20Detection/database/fresh-install/01_create_database.sql) để tạo database rỗng `codeassess`.
2. **Bước 2:** Cấu hình backend kết nối tới database mới `codeassess` với `FLYWAY_ENABLED=true` (giá trị mặc định trong `application.yml`).
3. **Bước 3:** Khởi động backend CodeAssess. Flyway sẽ tự động chạy toàn bộ 18 migration từ `V1` đến `V16`, khởi tạo chuẩn xác 17 bảng và bảng `flyway_schema_history`.
4. **Bước 4:** Chạy tiếp file [`03_seed_reference_data.sql`](file:///d:/AST%20Plagiarism%20Detection/database/fresh-install/03_seed_reference_data.sql) để chèn Rubric và tài khoản Quản trị viên Bootstrap.
5. **Bước 5 (Tùy chọn):** Chạy tiếp file [`04_demo_data.sql`](file:///d:/AST%20Plagiarism%20Detection/database/fresh-install/04_demo_data.sql) để có sẵn dữ liệu kiểm thử.
6. **Bước 6:** Chạy file [`05_verify_database.sql`](file:///d:/AST%20Plagiarism%20Detection/database/fresh-install/05_verify_database.sql) để xác nhận hệ thống.

---

### Phương án 2: Khởi tạo thủ công bằng Standalone SQL
Dành cho trường hợp bạn muốn tạo sẵn toàn bộ bảng bằng câu lệnh SQL trực tiếp trước khi khởi động ứng dụng:

1. **Bước 1:** Chạy [`01_create_database.sql`](file:///d:/AST%20Plagiarism%20Detection/database/fresh-install/01_create_database.sql) trên MySQL Server.
2. **Bước 2:** Chạy [`02_schema.sql`](file:///d:/AST%20Plagiarism%20Detection/database/fresh-install/02_schema.sql) để tạo đầy đủ 17 bảng vật lý.
3. **Bước 3:** Chạy [`03_seed_reference_data.sql`](file:///d:/AST%20Plagiarism%20Detection/database/fresh-install/03_seed_reference_data.sql).
4. **Bước 4 (Tùy chọn):** Chạy [`04_demo_data.sql`](file:///d:/AST%20Plagiarism%20Detection/database/fresh-install/04_demo_data.sql).
5. **Bước 5:** Chạy [`05_verify_database.sql`](file:///d:/AST%20Plagiarism%20Detection/database/fresh-install/05_verify_database.sql) để đối soát.
6. **Bước 6 (Bắt buộc cấu hình Flyway khi chạy backend):**
   - Trong `application.yml`, cấu hình Flyway được định nghĩa:
     ```yaml
     spring:
       flyway:
         enabled: ${FLYWAY_ENABLED:true}
         baseline-on-migrate: ${FLYWAY_BASELINE_ON_MIGRATE:true}
         baseline-version: ${FLYWAY_BASELINE_VERSION:0}
     ```
   - Khi schema 17 bảng đã được tạo sẵn qua `02_schema.sql`, database không rỗng nhưng thiếu bảng `flyway_schema_history`. Với cấu hình mặc định (`baseline-version: 0`), Flyway sẽ tự baseline ở version 0 và cố gắng thực thi migration `V1`, dẫn đến lỗi dừng khởi động: `Table 'users' already exists`.
   - Vì vậy, khi sử dụng Phương án 2, bạn **bắt buộc** phải tắt Flyway bằng cách đặt biến môi trường:
     ```cmd
     set FLYWAY_ENABLED=false
     ```
   - Khi `FLYWAY_ENABLED=false`, Flyway hoàn toàn không can thiệp. Cơ chế Hibernate (`ddl-auto: validate`) sẽ trực tiếp kiểm chứng 16 JPA entities với 17 bảng đã import và khởi động ứng dụng thành công.
   - **Lưu ý quan trọng:** Không khuyến nghị lạm dụng `FLYWAY_BASELINE_ON_MIGRATE=true` kết hợp với version cao (như v16) như một giải pháp thay thế cho việc xác minh tính đúng đắn của schema, vì lệnh baseline chỉ đơn thuần đóng dấu một mốc phiên bản trong DB mà không kiểm tra xem các bảng thực tế có khớp với code hay không.

---

## 4. Hướng dẫn chi tiết từng bước trên MySQL Workbench

### Bước 1: Mở MySQL Workbench và kết nối tới MySQL Server
1. Khởi động ứng dụng **MySQL Workbench**.
2. Kết nối tới MySQL Server (ví dụ port `3306`).
3. Đăng nhập với quyền `root` (hoặc tài khoản có quyền `CREATE DATABASE`, `CREATE TABLE`).

### Bước 2: Chạy file tạo database `01_create_database.sql`
1. Trên menu, chọn **File** $\rightarrow$ **Open SQL Script...**
2. Trỏ tới file: `database/fresh-install/01_create_database.sql`.
3. Nhấp biểu tượng **Tia sét** (Execute).
4. Kiểm tra tab Output xem database `codeassess` đã được tạo thành công với charset `utf8mb4`.

### Bước 3: Chạy file tạo Schema `02_schema.sql` (Nếu chọn Phương án 2)
1. Chọn **File** $\rightarrow$ **Open SQL Script...** $\rightarrow$ chọn `02_schema.sql`.
2. Nhấp biểu tượng **Tia sét** (Execute).
3. Nhấp nút **Refresh** ở khung Schemas bên trái để thấy toàn bộ 17 bảng:
   - `ast_fingerprints`
   - `classes`
   - `class_students`
   - `exam_classes`
   - `exam_matrix_coordination`
   - `exams`
   - `grade_audit_logs`
   - `password_reset_tokens`
   - `plagiarism_appeals`
   - `plagiarism_evidence`
   - `rule_scores`
   - `scoring_criteria`
   - `similarity_matrix`
   - `submission_test_results`
   - `submissions`
   - `test_cases`
   - `users`

### Bước 4: Chạy file Seed dữ liệu bắt buộc `03_seed_reference_data.sql`
1. Chọn **File** $\rightarrow$ **Open SQL Script...** $\rightarrow$ chọn `03_seed_reference_data.sql`.
2. Thực thi script (Execute).
3. Bảng `scoring_criteria` sẽ có 4 tiêu chí rubric chuẩn (tổng thang 100 điểm) và bảng `users` sẽ có 1 tài khoản quản trị hệ thống Bootstrap:
   - **Email:** `admin.bootstrap@codeassess.edu.vn`
   - **Mật khẩu tạm:** `Admin@CodeAssess2026!` *(đã được băm an toàn bằng BCrypt)*.

### Bước 5: Chạy file Dữ liệu mẫu `04_demo_data.sql` (Tùy chọn)
1. Chọn **File** $\rightarrow$ **Open SQL Script...** $\rightarrow$ chọn `04_demo_data.sql`.
2. Thực thi script (Execute).
3. Dữ liệu mẫu chứa các tài khoản:
   - **Giảng viên 1:** `teacher.giang@codeassess.edu.vn` (Mật khẩu: `Teacher@2026!`)
   - **Giảng viên 2:** `teacher.mai@codeassess.edu.vn` (Mật khẩu: `Teacher@2026!`)
   - **Sinh viên:**
     - `student.an@fpt.edu.vn` (Mật khẩu: `Student@2026!`)
     - `student.binh@fpt.edu.vn` (Mật khẩu: `Student@2026!`)
     - `student.cuong@fpt.edu.vn` (Mật khẩu: `Student@2026!`)
     - `student.dung@fpt.edu.vn` (Mật khẩu: `Student@2026!`)

### Bước 6: Chạy file xác minh `05_verify_database.sql`
1. Chọn **File** $\rightarrow$ **Open SQL Script...** $\rightarrow$ chọn `05_verify_database.sql`.
2. Thực thi script (Execute).
3. Rà soát kết quả:
   - **Mục 2:** Tổng số bảng trong schema $\ge 17$.
   - **Mục 5:** Toàn bộ các dòng kiểm tra vi phạm ràng buộc toàn vẹn khóa ngoại (Orphaned rows) đều phải có `violations = 0`.
   - **Mục 6:** Các cột mới (`deleted_at`, `auto_score`, `zip_storage_path`...) đều hiển thị đúng kiểu dữ liệu, và cột `password_changed_at` không xuất hiện.
   - **Mục 7 & 8:** Test cases, kết quả chấm tự động và rubric điểm hiển thị đầy đủ.

---

## 5. Hướng dẫn chạy nhanh bằng dòng lệnh (MySQL CLI)

```bash
cd "d:/AST Plagiarism Detection/database/fresh-install"

# 1. Tạo database codeassess
mysql -u root -p < 01_create_database.sql

# 2. Tạo schema 16 bảng (Nếu dùng Phương án 2)
mysql -u root -p codeassess < 02_schema.sql

# 3. Nạp dữ liệu cấu hình ban đầu
mysql -u root -p codeassess < 03_seed_reference_data.sql

# 4. Nạp dữ liệu demo kiểm thử (nếu cần)
mysql -u root -p codeassess < 04_demo_data.sql

# 5. Chạy báo cáo xác minh toàn vẹn
mysql -u root -p codeassess < 05_verify_database.sql
```

---

## 6. Cấu hình Backend kết nối Database mới

Khi bạn muốn Backend CodeAssess kết nối vào database mới `codeassess`:

### Cách 1: Thiết lập biến môi trường (Khuyên dùng)
Trước khi khởi động backend, đặt các biến môi trường sau:
```cmd
set DB_HOST=localhost
set DB_PORT=3306
set DB_NAME=codeassess
set DB_USERNAME=root
set DB_PASSWORD=your_password
```
*(Nếu đã tạo schema qua `02_schema.sql` thủ công ở Phương án 2, thiết lập thêm `set FLYWAY_ENABLED=false`).*

### Cách 2: Chỉnh sửa file `application.yml`
Trong file [`Back end/src/main/resources/application.yml`](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/resources/application.yml#L3):
```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:codeassess}?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:your_password}
```

---

## 7. Kiểm tra tính tương thích Hibernate (`ddl-auto: validate`)

Backend CodeAssess sử dụng cấu hình:
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```
- Toàn bộ 17 bảng vật lý và tất cả các cột tương ứng đã được ánh xạ chính xác 100% với 16 JPA Entity classes của backend:
  `User`, `PasswordResetToken`, `CourseClass`, `ClassStudent`, `Exam`, `TestCase`, `Submission`, `SubmissionTestResult`, `AstFingerprint`, `SimilarityPair`, `PlagiarismEvidence`, `Appeal`, `ScoringCriteria`, `RuleScore`, `GradeAuditLog`, `ExamMatrixCoordination` (kèm bảng join `exam_classes` của `Exam`).
- Do đó, Hibernate sẽ vượt qua giai đoạn `validate` thành công mà không báo lỗi schema mismatch.

### Giới hạn của `ddl-auto: validate` và vai trò của script xác minh
Cần lưu ý rằng cơ chế `ddl-auto: validate` của Hibernate **không thay thế toàn bộ việc kiểm tra schema cơ sở dữ liệu**:
- **Hibernate validate kiểm tra:** Tên bảng, tên cột, kiểu dữ liệu cơ bản tương thích và sự hiện diện của khóa chính.
- **Hibernate validate KHÔNG kiểm tra:**
  1. Các Index phụ và Index phức hợp (ví dụ: `idx_exams_deleted_at`, `idx_test_cases_exam_order`, `idx_ast_submission_hash`...).
  2. Các ràng buộc kiểm tra nghiệp vụ (Check Constraints, ví dụ: `chk_users_role CHECK (...)`).
  3. Hành vi lan truyền khóa ngoại (`ON DELETE CASCADE`, `ON DELETE SET NULL`, `ON DELETE RESTRICT`).
  4. Bảng mã và đối chiếu mặc định của database (`utf8mb4`, `utf8mb4_unicode_ci`).
- **Kết luận:** Việc thực thi script [`05_verify_database.sql`](file:///d:/AST%20Plagiarism%20Detection/database/fresh-install/05_verify_database.sql) là bước đối soát toàn diện bắt buộc để đảm bảo đầy đủ các ràng buộc tầng cơ sở dữ liệu mà Hibernate không kiểm tra tới.
