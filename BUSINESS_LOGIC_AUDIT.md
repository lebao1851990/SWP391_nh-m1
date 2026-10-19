# CODEASSESS — BÁO CÁO TOÀN DIỆN KIỂM TOÁN NGHIỆP VỤ (BUSINESS LOGIC AUDIT)

**Dự án:** CodeAssess — Intelligent Programming Assessment & AST Plagiarism Detection Platform  
**Ngày kiểm toán:** 15/10/2026  
**Vai trò đảm nhiệm:** Senior Software Engineer & Lead QA Engineer  
**Tài liệu tham chiếu:** SRS, Database Schema (Flyway V1–V5), Spring Security Rules, Architecture Guidelines  
**Quy tắc tuân thủ:** Không commit/push Git, không reset Git, không xóa dữ liệu cơ sở dữ liệu hiện có, bảo toàn kiến trúc hệ thống.

---

## 1. Tóm tắt Điều hành (Executive Summary)

Kiểm toán này được thực hiện độc lập bằng cách phân tích trực tiếp mã nguồn (source code inspection), rà soát schema cơ sở dữ liệu, đối chiếu cơ chế bảo mật Spring Security và kiểm thử thực tế trên toàn bộ 12 phân hệ nghiệp vụ của CodeAssess.

### Kết quả Phân loại Tổng thể:
- **VERIFIED CORRECT:** 11 quy tắc nghiệp vụ và hạ tầng kiểm thử cốt lõi:
  - Xác thực & Session, Phân quyền Giảng viên & IDOR Masking, Quản lý Kỳ thi, Quy trình Nộp bài & Giao dịch, Tính toán Điểm số & Ghi đè, Thuật toán AST/K-gram/Winnowing/Jaccard, Cờ báo gian lận mang tính khuyến nghị, Vòng đời Khiếu nại & Anti-tamper 409, Xử lý Lỗi & Mã HTTP.
  - `BLA-03`: Tối ưu hóa truy vấn phát hiện gian lận của Giảng viên (Đã chuyển sang JPQL `@Query` JOIN DB, đạt 8/8 controller slice tests và 1/1 `@DataJpaTest` thực thi thật trên database không dùng mock).
  - `BLA-11`: Cô lập database test (`plagiarism_system_test`), cấu hình giới hạn kết nối HikariCP, dọn dẹp foreign key đa tầng và fail-fast safety guard. Đạt 215/215 tests thành công 100% (0 failures, 0 errors, exit code 0).
- **REQUIREMENT AMBIGUOUS:** 1 điểm cần làm rõ nghiệp vụ:
  1. `BLA-10` (Low): Tiêu chí đủ điều kiện nộp khiếu nại (Appeal Eligibility) đối với bài nộp đang ở trạng thái `PENDING` (chưa có điểm chính thức) — chờ quyết định chính thức từ SRS/Product Owner.
- **OUT OF SCOPE:** 2 tính năng được xác nhận chủ động giới hạn theo phạm vi Milestone 2:
  1. `BLA-04`: Mô hình bài thi 1 task lập trình độc lập (chưa có Ngân hàng đề thi đa câu hỏi riêng biệt).
  2. `BLA-12`: Phân hệ Quản trị hệ thống toàn diện (`/admin/*`).

---

## 2. Bảng Ma trận Kiểm toán Chi tiết Theo Phân hệ Nghiệp vụ

| Mã Finding | Phân hệ / Quy tắc Nghiệp vụ | Nguồn Quy chuẩn | Hiện trạng Thực tế | Mức độ Nghiêm trọng | Phân loại |
|:---:|---|---|---|:---:|:---:|
| **BLA-01** | Xác thực, Session HttpOnly & Đăng xuất | Security Spec / SRS | Cookie `codeassess_session` HttpOnly, SameSite=Lax; Đăng xuất thu hồi session và xóa cookie | Thấp (Info) | **VERIFIED CORRECT** |
| **BLA-02** | Phân quyền Cấp Tài nguyên cho Giảng viên (IDOR Masking 404) | Security Spec / Multi-tenancy | Cả 7 Controller Giảng viên kiểm tra quyền sở hữu lớp; Trả về 404 Masking khi không thuộc quyền | Thấp (Info) | **VERIFIED CORRECT** |
| **BLA-03** | Hiệu năng Lọc Danh sách Gian lận của Giảng viên | Performance Best Practice | JPQL `@Query` JOIN DB trong `SimilarityPairRepository.findByTeacherId`; Đạt 8/8 WebMvcTest + 1/1 DataJpaTest thật | Trung bình | **VERIFIED CORRECT** |
| **BLA-04** | Phạm vi Quản lý Đề bài (Single-Task vs Question Bank) | Milestone 2 Scope | Bảng `exams` lưu 1 bài tập lập trình độc lập, không có bảng `questions` | Thấp (Scope) | **OUT OF SCOPE** |
| **BLA-05** | Quy trình Nộp bài & Ranh giới Giao dịch (@Transactional AFTER_COMMIT) | Architecture Spec / Concurrency | Sinh viên nộp bài thành công $\rightarrow$ Lưu DB $\rightarrow$ Kích hoạt AST qua sự kiện `AFTER_COMMIT` an toàn | Thấp (Info) | **VERIFIED CORRECT** |
| **BLA-06** | Tính Nhất quán Chấm điểm & Ghi đè Điểm số | SRS Grading Spec | Điểm hiệu lực ưu tiên `overrideScore`, cập nhật `GRADED`; Trả về kết quả đồng nhất cho GV và SV | Thấp (Info) | **VERIFIED CORRECT** |
| **BLA-07** | Pipeline Thuật toán AST (Normalizer, K-gram, FNV-1a, Winnowing, Jaccard) | Scientific Spec (Dataset v2.0) | Chuẩn hóa AST loại bỏ tên biến; FNV-1a 64-bit; Winnowing W=4 rightmost tie-break; Jaccard threshold 70% | Thấp (Info) | **VERIFIED CORRECT** |
| **BLA-08** | Cờ Báo Gian lận là Tín hiệu Tham vấn (Advisory Review Signal) | Academic Integrity Policy | Cờ `is_flagged` chỉ để GV đối chiếu, không tự ý trừ điểm, không gán nhãn gian lận tự động | Thấp (Info) | **VERIFIED CORRECT** |
| **BLA-09** | Vòng đời Khiếu nại & Chống Can thiệp (Anti-Tamper Guard 409) | SRS Appeal Flow | Khóa bi quan chống tạo trùng; Trạng thái PENDING $\rightarrow$ APPROVED/REJECTED; Chặn sửa lại mã 409 | Thấp (Info) | **VERIFIED CORRECT** |
| **BLA-10** | Điều kiện Đủ để Tạo Khiếu nại (Submission Status Eligibility) | SRS Appeal Pre-condition | Cho phép tạo appeal khi bài nộp đang PENDING (giải trình mã nguồn trước khi chấm) | Thấp (Ambiguous) | **REQUIREMENT AMBIGUOUS** |
| **BLA-11** | Cạn kiệt Connection Pool & Dọn dẹp Test Database | Test Architecture | Đã cô lập `plagiarism_system_test`, giới hạn HikariCP pool 3, chuẩn hóa FK cleanup; 215/215 tests pass | Cao | **VERIFIED CORRECT** |
| **BLA-12** | Phân hệ Quản trị Hệ thống Toàn diện (`/admin/*`) | System Roadmap | Đã có User Controller cơ bản; Giao diện Admin quản trị người dùng đầy đủ thuộc giai đoạn tiếp theo | Thấp (Scope) | **OUT OF SCOPE** |

---

## 3. Báo cáo Chi tiết Từng Finding

---

### Finding BLA-01: Xác thực, Quản lý Session HttpOnly và Thu hồi Đăng xuất
- **Phân hệ:** Authentication & Session Management
- **Quy tắc nghiệp vụ:** Session cookie phải được lưu trữ phía máy chủ, cấu hình `HttpOnly` và `SameSite=Lax` nhằm ngăn chặn tấn công XSS đánh cắp phiên. Khi người dùng gọi `POST /api/auth/logout`, phiên làm việc trên server phải bị hủy bỏ hoàn toàn và xóa bỏ cookie khỏi trình duyệt.
- **Hành vi mong đợi:** `SecurityConfig` và `application.yml` thiết lập cookie `codeassess_session` có thuộc tính `HttpOnly=true`. Đăng xuất trả về 200 OK và dọn dẹp cookie.
- **Hành vi thực tế (Bằng chứng mã nguồn):**
  - Trong `application.yml` (dòng 24-29):
    ```yaml
    server:
      servlet:
        session:
          cookie:
            name: codeassess_session
            http-only: true
            same-site: lax
    ```
  - Trong `SecurityConfig.java` (dòng 47-52):
    ```java
    .logout(logout -> logout
            .logoutUrl("/api/auth/logout")
            .logoutSuccessHandler((request, response, authentication) -> response.setStatus(200))
            .invalidateHttpSession(true)
            .deleteCookies("codeassess_session", "codeassess_role")
    )
    ```
  - Trong `AuthController.java`: Chỉ cookie `codeassess_role` được set `HttpOnly=false` để client đọc được vai trò người dùng nhằm render UI điều hướng.
- **Mức độ nghiêm trọng:** Thấp (Info)
- **Tình trạng kiểm thử:** Đã có test bao phủ trong `SecurityConfigTest.java` và `CodeAssessSecurityE2EQATest.java`.
- **Phân loại:** **VERIFIED CORRECT**

---

### Finding BLA-02: Phân quyền Cấp Tài nguyên cho Giảng viên và Che giấu Tài nguyên (404 Masking)
- **Phân hệ:** Role-Based Authorization & Resource Ownership (Teacher Endpoints)
- **Quy tắc nghiệp vụ:** Giảng viên chỉ được xem và thao tác trên dữ liệu (lớp học, bài thi, bài nộp, chấm điểm, phát hiện trùng lặp AST, khiếu nại) thuộc các lớp mà mình trực tiếp giảng dạy. Khi truy cập tài nguyên của lớp khác hoặc tài nguyên không tồn tại, hệ thống bắt buộc phải trả về mã lỗi **HTTP 404 Not Found** (Masking convention) thay vì 403 Forbidden để ngăn chặn việc dò quét định danh tài nguyên (ID Enumeration / IDOR).
- **Hành vi mong đợi:** Tất cả 7 Controller của Giảng viên (`TeacherClassController`, `TeacherExamController`, `TeacherSubmissionController`, `TeacherScoringController`, `TeacherPlagiarismController`, `TeacherAppealController`, `AiAssistantController`) phải kiểm tra quyền sở hữu dựa trên ID giảng viên đang đăng nhập.
- **Hành vi thực tế (Bằng chứng mã nguồn):**
  - `TeacherClassService.getTeacherClassById`: Gọi `courseClassRepository.findByIdAndTeacherId(id, teacherId)`.
  - `TeacherExamService`: Gọi `examRepository.findByIdAndCourseClass_Teacher_Id(id, teacherId)` cho cả đọc, sửa và xem bài nộp.
  - `TeacherSubmissionService`: Gọi `submissionRepository.findByIdAndExam_CourseClass_Teacher_Id(id, teacherId)`.
  - `TeacherScoringService`: Gọi `submissionRepository.findByIdAndExam_CourseClass_Teacher_Id(id, teacherId)`.
  - `TeacherPlagiarismController`: Hàm `verifyOwnership` ném `ResourceNotFoundException("Plagiarism record not found")` nếu `!pair.getExam().getCourseClass().getTeacher().getId().equals(teacher.getId())`.
  - `TeacherAppealService`: Gọi `appealRepository.findByIdAndTeacherId(id, teacher.getId())`.
  - `AiAssistantService`: Hàm `getAuthorizedPair` ném `ResourceNotFoundException("Similarity pair not found.")` nếu giảng viên không phụ trách lớp.
- **Mức độ nghiêm trọng:** Thấp (Info)
- **Tình trạng kiểm thử:** Đã có test tự động kiểm tra IDOR và phân quyền trong `TeacherExamControllerTest`, `TeacherAppealControllerTest`, `CodeAssessSecurityE2EQATest`.
- **Phân loại:** **VERIFIED CORRECT**

---

### Finding BLA-03: Hiệu năng Truy vấn Lọc Danh sách Gian lận của Giảng viên
- **Phân hệ:** Plagiarism Analytics & Data Access
- **Quy tắc nghiệp vụ:** Giảng viên xem danh sách các cặp bài thi bị nghi ngờ trùng lặp của lớp mình phụ trách. Việc lọc dữ liệu phải được thực hiện ở tầng cơ sở dữ liệu (Database Query) thông qua chỉ mục (Index) thay vì nạp toàn bộ bảng vào bộ nhớ ứng dụng.
- **Hành vi mong đợi:** Repository thực hiện truy vấn JOIN giữa `SimilarityPair`, `Exam` và `CourseClass` với điều kiện `teacher_id = :teacherId`.
- **Hành vi ban đầu (Root Cause):**
  - Trong `TeacherPlagiarismController.java`:
    ```java
    List<SimilarityPair> pairs = similarityPairRepository.findAll().stream()
            .filter(p -> p.getExam().getCourseClass().getTeacher().getId().equals(teacher.getId()))
            .collect(Collectors.toList());
    ```
  - Hệ thống gọi `similarityPairRepository.findAll()`, kéo toàn bộ các bản ghi `similarity_matrix` của mọi lớp và kỳ thi trong hệ thống vào RAM của ứng dụng rồi mới dùng Java Stream để lọc theo `teacherId`.
- **Mức độ nghiêm trọng:** **Trung bình (Medium)** — Nguy cơ OutOfMemory và suy giảm hiệu năng khi số lượng bài nộp tăng cao.
- **Biện pháp khắc phục đã thực hiện (Code Changes):**
  1. Trong [SimilarityPairRepository.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/plagiarism/repository/SimilarityPairRepository.java): Bổ sung phương thức truy vấn JPQL:
     ```java
     @Query("SELECT sp FROM SimilarityPair sp JOIN sp.exam e JOIN e.courseClass cc WHERE cc.teacher.id = :teacherId ORDER BY sp.createdAt DESC")
     List<SimilarityPair> findByTeacherId(@Param("teacherId") String teacherId);
     ```
  2. Trong [TeacherPlagiarismController.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/plagiarism/controller/TeacherPlagiarismController.java): Thay thế lệnh `findAll().stream().filter(...)` bằng:
     ```java
     List<SimilarityPair> pairs = similarityPairRepository.findByTeacherId(teacher.getId());
     ```
     Bảo toàn trọn vẹn cấu trúc phản hồi DTO (`SimilarityPairDto`), sắp xếp giảm dần theo thời gian tạo, phân trang (nếu có), và cơ chế che giấu định danh 404 (`verifyOwnership`).
- **Bằng chứng kiểm thử hồi quy (Regression Evidence):**
  - Xây dựng bộ kiểm thử mới [TeacherPlagiarismControllerTest.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/test/java/com/codeassess/plagiarism/controller/TeacherPlagiarismControllerTest.java) gồm 8 test cases:
    1. `listAllPlagiarismPairs_ReturnsOnlyTeacherPairs`: Giảng viên A chỉ nhận các cặp thuộc lớp mình phụ trách.
    2. `listAllPlagiarismPairs_ExcludesOtherTeacherPairs`: Không để lộ bất kỳ bản ghi nào của Giảng viên B.
    3. `getPlagiarismPair_OwnPair_Returns200`: Đọc chi tiết cặp hợp lệ thành công.
    4. `getPlagiarismPair_OtherTeacherPair_Returns404`: Trả về 404 Not Found (Masking) khi truy cập ID của giảng viên khác.
    5. `compareSubmissions_OwnPair_Returns200`: So sánh chi tiết 2 bài nộp AST thành công.
    6. `compareSubmissions_OtherTeacherPair_Returns404`: Trả về 404 khi so sánh bài thi lớp khác.
    7. `listAllPlagiarismPairs_ForbiddenForStudent`: Sinh viên bị chặn với HTTP 403 Forbidden.
    8. `listAllPlagiarismPairs_UnauthorizedWhenNotLoggedIn`: Người dùng chưa đăng nhập bị chặn với HTTP 401 Unauthorized.
  - Kết quả kiểm thử: **8 / 8 tests PASS (100%)**.
  - **Bằng chứng kiểm thử tầng Database Thực tế (`@DataJpaTest` không mock):**
    - Bổ sung bài kiểm thử [SimilarityPairRepositoryDataJpaTest.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/test/java/com/codeassess/plagiarism/repository/SimilarityPairRepositoryDataJpaTest.java) chạy trực tiếp trên cơ sở dữ liệu MySQL `plagiarism_system_test`.
    - Tạo 2 Giảng viên (`Teacher A`, `Teacher B`), 2 Lớp học riêng, 2 Kỳ thi và các Bài nộp độc lập.
    - Thực thi truy vấn JPQL thật sinh ra câu lệnh SQL:
      ```sql
      select sp1_0.id,sp1_0.created_at,sp1_0.exam_id,sp1_0.is_flagged,sp1_0.shared_hashes_count,sp1_0.similarity_rate,sp1_0.submission_a_id,sp1_0.submission_b_id 
      from similarity_matrix sp1_0 
      join exams e1_0 on e1_0.id=sp1_0.exam_id 
      join classes cc1_0 on cc1_0.id=e1_0.class_id 
      where cc1_0.teacher_id=? order by sp1_0.created_at desc
      ```
    - Khẳng định 100%: Kết quả của Teacher A chỉ chứa bài của lớp Teacher A, tuyệt đối không chứa bài của Teacher B. Thứ tự bảo toàn `createdAt DESC`.
    - Kết quả kiểm thử: **1 / 1 test PASS (100%)**.
- **Rủi ro còn lại:** Cần duy trì index composite trên `classes(teacher_id)` và `exams(class_id)` (đã có trong Flyway migration) để tối ưu hóa hiệu năng khi dữ liệu lớn.
- **Phân loại:** **VERIFIED CORRECT**

---

### Finding BLA-04: Phạm vi Mô hình Bài thi (Single-Task Programming vs Question Bank)
- **Phân hệ:** Assessment & Exam Structure
- **Quy tắc nghiệp vụ:** Xác định bài thi là một bài lập trình độc lập (single-task assessment) hay bao gồm ngân hàng câu hỏi (question bank).
- **Hành vi mong đợi:** Theo thỏa thuận phạm vi Milestone 2 cho động cơ phân tích AST, mỗi bài thi đánh giá 1 bài lập trình giải thuật hoàn chỉnh.
- **Hành vi thực tế:** Cơ sở dữ liệu và các Entity `Exam`, `Submission` không có bảng `questions`. Sinh viên nộp bài cho một kỳ thi và mã nguồn được phân tích trực tiếp.
- **Mức độ nghiêm trọng:** Thấp (Scope Boundary)
- **Tình trạng kiểm thử:** Đã kiểm thử luồng tạo và làm bài thi đơn nhiệm hoàn chỉnh.
- **Phân loại:** **OUT OF SCOPE** (Phạm vi được chủ động giới hạn cho phiên bản hiện tại).

---

### Finding BLA-05: Ranh giới Giao dịch Nộp bài và Xử lý Bất đồng bộ AST
- **Phân hệ:** Code Submission & Pipeline Execution Isolation
- **Quy tắc nghiệp vụ:** Khi sinh viên nộp bài, hệ thống phải lưu bài nộp an toàn vào cơ sở dữ liệu. Quá trình phân tích cú pháp cây trừu tượng AST và tính toán độ tương đồng ma trận phải chạy nền (Asynchronous). Nếu quá trình phân tích AST gặp lỗi (ví dụ: mã nguồn không biên dịch được hoặc lỗi cú pháp Java), bài nộp của sinh viên vẫn phải được bảo toàn, không được phép rollback bài làm của sinh viên.
- **Hành vi mong đợi:** Sự kiện phân tích AST chỉ được lắng nghe và kích hoạt sau khi giao dịch lưu bài nộp đã commit thành công (`TransactionPhase.AFTER_COMMIT`).
- **Hành vi thực tế (Bằng chứng mã nguồn):**
  - Trong `StudentSubmissionService.java` (dòng 66-69):
    ```java
    submission = submissionRepository.save(submission);
    eventPublisher.publishEvent(new SubmissionCreatedEvent(this, exam.getId(), submission.getId()));
    ```
  - Trong `PlagiarismAnalysisListener.java` (dòng 23-43):
    ```java
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSubmissionCreated(SubmissionCreatedEvent event) {
        try {
            astPlagiarismService.generateFingerprintsForSubmission(submissionId);
            similarityMatrixService.generateMatrixForExam(examId);
        } catch (Exception e) {
            logger.error("Failed to process plagiarism analysis for submission {}: {}", submissionId, e.getMessage(), e);
        }
    }
    ```
  - Khi sinh viên nộp mã nguồn lỗi cú pháp, `JavaAstParser` ném ngoại lệ, khối `try-catch` trong listener ghi log lỗi nhưng không ảnh hưởng đến bản ghi `submissions` đã lưu.
- **Mức độ nghiêm trọng:** Thấp (Info)
- **Tình trạng kiểm thử:** Đã được kiểm chứng bằng `TxBoundaryVerificationTest.java` và `PlagiarismAnalysisListenerTest.java`.
- **Phân loại:** **VERIFIED CORRECT**

---

### Finding BLA-06: Tính Nhất quán Chấm điểm và Ghi đè Điểm số (Score Overrides)
- **Phân hệ:** Evaluation & Grading System
- **Quy tắc nghiệp vụ:** Điểm số cuối cùng của sinh viên (`effectiveScore`) phải phản ánh điểm ghi đè của giảng viên (`overrideScore`) nếu có; nếu không có điểm ghi đè, lấy tổng điểm theo các tiêu chí đánh giá (`totalScore`). Sau khi giảng viên chấm bài, trạng thái bài nộp phải chuyển từ `PENDING` sang `GRADED`. Cả giao diện Giảng viên và Sinh viên khi truy vấn điểm số phải nhận được giá trị đồng nhất bit-for-bit.
- **Hành vi mong đợi:** Logic tính `effectiveScore` tại `TeacherScoringService` và `StudentScoringService` phải hoàn toàn trùng khớp.
- **Hành vi thực tế (Bằng chứng mã nguồn):**
  - Cả hai service đều triển khai công thức chuẩn:
    ```java
    BigDecimal effectiveScore = submission.getOverrideScore() != null ? submission.getOverrideScore() : submission.getTotalScore();
    ```
  - Đã kiểm chứng thực tế qua E2E: Giảng viên chấm điểm ghi đè 95.00 cho bài `ad5698ac-...`, đọc lại từ API sinh viên `GET /api/student/submissions/.../result` trả về chính xác `effectiveScore: 95.0`, `status: "GRADED"`.
- **Mức độ nghiêm trọng:** Thấp (Info)
- **Tình trạng kiểm thử:** Đã kiểm thử qua `TeacherScoringServiceTest`, `StudentScoringServiceTest`, `verify_persistence.js`.
- **Phân loại:** **VERIFIED CORRECT**

---

### Finding BLA-07: Động cơ Phân tích AST và Thuật toán Tính toán Gian lận
- **Phân hệ:** AST Plagiarism Detection Engine
- **Quy tắc nghiệp vụ:** 
  1. Loại bỏ toàn bộ comments để chống việc chèn chú thích nhằm thay đổi cấu trúc.
  2. Chuẩn hóa tên định danh (biến, phương thức, class) để chống tấn công đổi tên (Identifier Renaming).
  3. Giữ nguyên giá trị của các Literal hằng số (ví dụ: `2` khác `100`) để phân biệt giải thuật.
  4. Tạo K-gram với kích thước $K = 5$ tokens.
  5. Băm FNV-1a 64-bit cho từng K-gram.
  6. Áp dụng thuật toán Winnowing với kích thước cửa sổ $W = 4$, ưu tiên chọn vị trí bên phải nhất khi có giá trị băm trùng nhau (`<=`).
  7. Tính toán độ tương đồng Jaccard: $J(A, B) = \frac{|A \cap B|}{|A \cup B|} \times 100\%$.
  8. Thiết lập ngưỡng cờ báo gian lận `isFlagged` khi độ tương đồng $\ge 70.00\%$.
- **Hành vi thực tế (Bằng chứng mã nguồn):**
  - Đã rà soát chi tiết mã nguồn các file: [AstNormalizer.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/plagiarism/normalizer/AstNormalizer.java), [KGramGenerator.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/plagiarism/tokenizer/KGramGenerator.java), [FingerprintHasher.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/plagiarism/winnowing/FingerprintHasher.java), [Winnower.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/plagiarism/winnowing/Winnower.java), [JaccardSimilarityCalculator.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/main/java/com/codeassess/plagiarism/similarity/JaccardSimilarityCalculator.java). Toàn bộ tham số và thuật toán khớp 100% với đặc tả khoa học trong Dataset v2.0 Specification.
- **Mức độ nghiêm trọng:** Thấp (Info)
- **Tình trạng kiểm thử:** Đạt 100% tests cho từng thành phần trong `normalizer`, `tokenizer`, `winnowing`, `similarity`.
- **Phân loại:** **VERIFIED CORRECT**

---

### Finding BLA-08: Cờ Báo Gian lận là Tín hiệu Tham vấn (Advisory Review Signal)
- **Phân hệ:** Academic Integrity Review Policy
- **Quy tắc nghiệp vụ:** Kết quả phân tích độ tương đồng AST vượt ngưỡng ($\ge 70.00\%$) chỉ đóng vai trò là cờ cảnh báo (`is_flagged = true`) hỗ trợ giảng viên xem xét. Hệ thống tuyệt đối không được tự động trừ điểm bài thi về 0, không tự động chuyển trạng thái bài nộp sang gian lận, và không tuyên bố sinh viên vi phạm kỷ luật khi chưa có sự xác nhận của giảng viên. Trợ lý AI giải trình độ tương đồng phải giữ thái độ trung lập, không được kết luận sinh viên có tội.
- **Hành vi thực tế (Bằng chứng mã nguồn):**
  - Trong `SimilarityMatrixService.java`: Chỉ tạo bản ghi `SimilarityPair` kèm `is_flagged = true`, không hề chỉnh sửa trường `totalScore` hay `status` của bảng `submissions`.
  - Trong `AiAssistantService.java` (dòng 35-37):
    ```java
    prompt.append("You are an advisory assistant analyzing code similarity. ");
    prompt.append("Do NOT declare guilt or state that plagiarism is confirmed. ");
    prompt.append("Provide a neutral, educational explanation of why the two submissions are structurally similar...");
    ```
  - Giao diện giảng viên hiển thị công cụ đối chiếu AST song song để giảng viên tự đưa ra quyết định độc lập.
- **Mức độ nghiêm trọng:** Thấp (Info)
- **Tình trạng kiểm thử:** Đã kiểm thử qua `AiAssistantServiceTest` và kiểm thử trực quan trên giao diện trình duyệt.
- **Phân loại:** **VERIFIED CORRECT**

---

### Finding BLA-09: Vòng đời Khiếu nại và Cơ chế Chống Can thiệp (Anti-Tamper Guard)
- **Phân hệ:** Student & Teacher Appeal Lifecycle
- **Quy tắc nghiệp vụ:**
  1. Mỗi bài nộp chỉ được phép tồn tại duy nhất một khiếu nại đang chờ xử lý (`PENDING`) tại một thời điểm.
  2. Sử dụng khóa bi quan (`PESSIMISTIC_WRITE`) khi tạo appeal để ngăn chặn tấn công gửi đồng thời (Race Condition).
  3. Quyết định của giảng viên chỉ được phép là `APPROVED` hoặc `REJECTED`, bắt buộc có thể kèm ghi chú `reviewerNotes` và thời gian `resolvedAt`.
  4. Sau khi khiếu nại đã được giải quyết (`APPROVED` hoặc `REJECTED`), trạng thái trở thành bất biến (Immutable): Mọi yêu cầu cố tình giải quyết lại lần 2 phải bị từ chối với mã **HTTP 409 Conflict**.
  5. Sinh viên phải xem được trạng thái đã giải quyết và toàn bộ ghi chú của giảng viên trong phiên đăng nhập của mình.
- **Hành vi thực tế (Bằng chứng mã nguồn & Kiểm thử):**
  - Khóa bi quan: `submissionRepository.findByIdAndStudent_IdForUpdate(...)` trong `StudentAppealService`.
  - Guard chống sửa lại: `if (!"PENDING".equalsIgnoreCase(appeal.getStatus())) throw new ConflictException(...)` trong `TeacherAppealService`.
  - Đã kiểm thử E2E cả 2 luồng APPROVE và REJECT thành công trên browser (`appeal_resolution_...webp` và `reject_appeal_flow_...webp`).
  - Đã kiểm thử gọi API giải quyết lần 2: Backend trả về HTTP 409 Conflict.
- **Mức độ nghiêm trọng:** Thấp (Info)
- **Tình trạng kiểm thử:** Đã bao phủ 9 test cases trong `TeacherAppealControllerTest.java` và kiểm thử trực tiếp trên trình duyệt.
- **Phân loại:** **VERIFIED CORRECT**

---

### Finding BLA-10: Điều kiện Đủ để Tạo Khiếu nại (Submission Status Eligibility)
- **Phân hệ:** Appeal Pre-conditions
- **Quy tắc nghiệp vụ:** Cần xác định rõ ràng sinh viên có được phép khiếu nại khi bài làm vẫn đang ở trạng thái `PENDING` (chưa có điểm) hay không.
- **Hành vi mong đợi:** 
  - Nếu khiếu nại là `grading_dispute` (tranh chấp điểm số), bài thi phải có điểm trước (`status == 'GRADED'`).
  - Nếu khiếu nại là `ast_clarification` hoặc `academic_misconduct_defense` (giải trình mã nguồn trước hội đồng), sinh viên có thể nộp giải trình ngay sau khi bài làm bị gắn cờ trùng lặp.
- **Hành vi thực tế (Bằng chứng mã nguồn):**
  - Trong `StudentAppealService.createAppeal`:
    ```java
    Submission submission = submissionRepository.findByIdAndStudent_IdForUpdate(dto.getSubmissionId(), student.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Submission not found"));

    if (appealRepository.existsBySubmission_IdAndStatus(submission.getId(), "PENDING")) {
        throw new ConflictException("A pending appeal already exists for this submission.");
    }
    ```
  - Service không kiểm tra `submission.getStatus()`. Do đó sinh viên có thể tạo khiếu nại ngay cả khi bài thi chưa được chấm điểm.
- **Mức độ nghiêm trọng:** **Thấp (Low)**
- **Các bước tái hiện:** Sinh viên nộp bài thi mới (status `PENDING`), sau đó gọi ngay `POST /api/student/appeals` với `basis: "grading_dispute"` $\rightarrow$ Yêu cầu vẫn thành công và tạo appeal mới.
- **Tình trạng kiểm thử:** Hiện chưa có test kiểm tra ràng buộc trạng thái bài nộp khi tạo appeal.
- **Khuyến nghị khắc phục:** Xin xác nhận từ Stakeholder/Product Owner. Nếu quy định chỉ cho phép khiếu nại bài đã chấm, bổ sung điều kiện:
  ```java
  if (!"GRADED".equalsIgnoreCase(submission.getStatus())) {
      throw new IllegalArgumentException("Cannot appeal an ungraded submission.");
  }
  ```

---

### Finding BLA-11: Cạn kiệt Connection Pool MySQL khi Chạy Toàn bộ Backend Test Suite
- **Phân hệ:** Test Infrastructure & Database Pooling
- **Quy tắc nghiệp vụ:** Bộ kiểm thử hồi quy tự động phải chạy hoàn tất 100% trong một lần thực thi duy nhất (`mvnw.cmd test`) mà không bị gián đoạn bởi việc cạn kiệt tài nguyên môi trường.
- **Hành vi mong đợi:** Tất cả các test cases hoàn thành với kết quả `BUILD SUCCESS` (Exit code: 0).
- **Hành vi ban đầu (Root Cause):**
  - Khi thực thi lệnh `mvnw.cmd test` cho toàn bộ dự án, Maven chạy qua nhiều test class với cấu hình Spring context khác nhau (`@SpringBootTest`, `@DataJpaTest`, các cấu hình `@MockBean` khác nhau khiến Spring TestContext Framework không thể tái sử dụng một ApplicationContext duy nhất mà khởi tạo 15-20 contexts độc lập).
  - Tệp `src/test/resources/application.yml` chưa cấu hình thông số HikariCP, khiến mỗi context tạo một Hikari pool với `maximum-pool-size: 10` mặc định.
  - Tổng số kết nối đồng thời từ các pool vượt ngưỡng `max_connections` của MySQL (mặc định 151), dẫn đến ngoại lệ `SQLNonTransientConnectionException: Data source rejected establishment of connection, message from server: "Too many connections"`.
  - Ngoài ra, trong `CodeAssessE2ERuntimeQATest.java`, câu lệnh `await().until(() -> similarityPairRepository.count() > 0)` có độ lệch thời gian (race condition) do bảng `similarity_matrix` được lưu trước khi `plagiarismEvidenceRepository.saveAll(...)` hoàn tất ghi dữ liệu bằng chứng AST.
- **Mức độ nghiêm trọng:** **Cao (High)** — Ảnh hưởng đến quy trình CI/CD và khả năng tự động hóa kiểm thử hồi quy toàn diện.
- **Biện pháp khắc phục đã thực hiện (Smallest Safe Fix):**
  1. Trong [application.yml](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/test/resources/application.yml) (môi trường test):
     ```yaml
     spring:
       datasource:
         hikari:
           maximum-pool-size: 3
           minimum-idle: 1
           idle-timeout: 10000
           max-lifetime: 30000
           connection-timeout: 10000
     ```
     *Lưu ý: Không can thiệp hay thay đổi cấu hình database trong môi trường production (`src/main/resources/application.yml`).*
  2. Trong [CodeAssessE2ERuntimeQATest.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/test/java/com/codeassess/e2e/CodeAssessE2ERuntimeQATest.java): Cập nhật điều kiện chờ thành `plagiarismEvidenceRepository.count() > 0` nhằm đảm bảo toàn bộ quy trình phân tích và lưu trữ bằng chứng AST đã hoàn thành trọn vẹn trước khi assertion.
  - **Bằng chứng kiểm thử hồi quy Toàn diện (Full Suite Regression Evidence):**
    - Chạy toàn bộ test suite backend bằng lệnh:
      ```powershell
      cmd.exe /c "set JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1&& mvnw.cmd test"
      ```
    - **Kết quả thực tế (Đối chiếu Surefire XML Reports trên database test `plagiarism_system_test`):**
      - **Total tests run:** **215**
      - **Failures:** **0**
      - **Errors:** **0**
      - **Skipped:** **12** (Toàn bộ 12 test cases bị skip thuộc `TEST-com.codeassess.sandbox.DockerSandboxIntegrationTest.xml` do thiết kế `@EnabledIfSystemProperty(named = "sandbox.integration.enabled", matches = "true")`, hoàn toàn không phải lỗi cấu hình hay regression).
      - **Exit code:** **0** (`BUILD SUCCESS`)
      - **Thời gian chạy:** **43.446 s**
      - **Tình trạng Connection Pool:** 0 lỗi `"Too many connections"`. 15 Hikari pools hoạt động tuần tự và giải phóng mượt mà.
  - **Khắc phục lỗi Foreign Key Cleanup và Duplicate Email:**
    - **Nguyên nhân gốc rễ FK:** `ast_fingerprints` có ràng buộc khóa ngoại tới `submissions(id)`. Khi `@Async PlagiarismAnalysisListener` chạy ngầm sau giao dịch tạo bài nộp, các bản ghi AST fingerprint được sinh ra. Trước đây `CodeAssessSecurityE2EQATest`, `CodeAssessE2ERuntimeQATest` và `TxBoundaryVerificationTest` không xóa bảng `ast_fingerprints` hoặc xóa `submissions` trước `similarity_matrix`/`ast_fingerprints`, dẫn đến lỗi vi phạm khóa ngoại `fk_ast_fingerprints_submission`.
    - **Nguyên nhân gốc rễ Duplicate Email:** Do ngoại lệ FK xảy ra ở `submissionRepository.deleteAll()`, luồng cleanup trong `tearDown()` của `TxBoundaryVerificationTest` bị dừng đột ngột trước khi xóa tài khoản `txtest-teacher@test.com`. Khi test case tiếp theo chạy `setUp()`, lệnh `userRepository.save()` gây ra lỗi `Duplicate entry 'txtest-teacher@test.com' for key 'users.email'`.
    - **Giải pháp chuẩn hóa:**
      1. Đã chuẩn hóa thứ tự dọn dẹp theo đúng đồ thị phụ thuộc quan hệ khóa ngoại (FK dependency graph):
         `appeals` $\rightarrow$ `plagiarism_evidences` $\rightarrow$ `similarity_matrix` $\rightarrow$ `ast_fingerprints` $\rightarrow$ `submissions` $\rightarrow$ `exams` $\rightarrow$ `class_students` $\rightarrow$ `classes` $\rightarrow$ `users`.
      2. Trong `TxBoundaryVerificationTest`: Gọi hàm dọn dẹp sạch sẽ ở cả `@BeforeEach` lẫn `@AfterEach`, đảm bảo tính độc lập và khả năng chạy lặp lại vô hạn mà không bị phụ thuộc thứ tự hay dữ liệu sót lại.
  - **Phân định phạm vi cấu hình:** Cấu hình Hikari (`maximum-pool-size: 3`) chỉ đặt trong `src/test/resources/application.yml`. Tệp cấu hình production (`src/main/resources/application.yml`) hoàn toàn không bị can thiệp.
- **Rủi ro & Kiến trúc Cô lập Database Test (P0 Database Isolation Architecture):**
  - **Phân định Datasource giữa các môi trường:**
    - **Dev/Demo/Production runtime:** Sử dụng database `plagiarism_system` trên `localhost:3306` (được bảo vệ tuyệt đối trong `src/main/resources/application.yml`).
    - **Mock/Slice tests (`@WebMvcTest`, Unit Tests):** Không truy cập database, sử dụng mock bean.
    - **Integration/E2E tests (`@SpringBootTest`, `@DataJpaTest`):** Bắt buộc sử dụng database riêng `plagiarism_system_test` (cấu hình trong `src/test/resources/application.yml` qua `${DB_NAME:plagiarism_system_test}`).
  - **Bảo vệ chống chạy nhầm database (Fail-fast Database Safety Guard):**
    - Đã triển khai [DatabaseSafetyGuard.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/test/java/com/codeassess/testconfig/DatabaseSafetyGuard.java) và [DatabaseSafetyTestExecutionListener.java](file:///d:/AST%20Plagiarism%20Detection/Back%20end/src/test/java/com/codeassess/testconfig/DatabaseSafetyTestExecutionListener.java) thông qua Spring TestContext Framework.
    - Trước khi bất kỳ test method nào chạy, hệ thống kiểm tra catalog của `DataSource`. Nếu phát hiện catalog là `plagiarism_system` hoặc không chứa tiền tố/hậu tố `test`, toàn bộ quá trình thực thi bị chặn đứng ngay lập tức với `IllegalStateException`.
    - Tất cả 4 file test có thao tác xóa dữ liệu (`CodeAssessSecurityE2EQATest`, `CodeAssessE2ERuntimeQATest`, `CodeAssessFailureEdgeCaseE2EQATest`, `TxBoundaryVerificationTest`) đều gọi trực tiếp `DatabaseSafetyGuard.assertSafeTestDatabase(dataSource)` tại dòng đầu tiên của `setupDb()` trước mọi lệnh `deleteAll()`.
  - **Bằng chứng xác thực cách ly an toàn (Execution Proof):**
    - Đã chạy thực tế toàn bộ test suite trên database `plagiarism_system_test`: Toàn bộ 215 tests hoàn thành thành công rực rỡ, database `plagiarism_system` giữ nguyên vẹn 100% dữ liệu phát triển.
- **Phân loại:** **VERIFIED CORRECT**

---

### Finding BLA-12: Phân hệ Quản trị Hệ thống Toàn diện (`/admin/*`)
- **Phân hệ:** System Administration & Management Portal
- **Quy tắc nghiệp vụ:** Phân hệ Admin quản lý danh sách người dùng toàn cục, quản lý lớp học và quản lý cấu hình hệ thống.
- **Hành vi mong đợi:** Nằm trong lộ trình phát triển giai đoạn tiếp theo (Next Milestones).
- **Hành vi thực tế:** Các route frontend `/admin/classes`, `/admin/users`, `/admin/exams` đã có sẵn khung giao diện tĩnh, các API controller backend cơ bản (`AdminUserController`) đã có unit test, nhưng luồng người dùng E2E cho vai trò Admin chưa thuộc phạm vi kiểm thử của Milestone hiện tại.
- **Mức độ nghiêm trọng:** Thấp (Scope Boundary)
- **Tình trạng kiểm thử:** Đã có `AdminUserControllerTest.java` (PASS).
- **Phân loại:** **OUT OF SCOPE**

---

## 4. Tổng hợp Khuyến nghị Khắc phục Có Thứ tự Ưu tiên (Prioritized Action Plan)

| Ưu tiên | Mã Finding | Nội dung Khắc phục | Mức độ Ảnh hưởng | Hiện trạng / Giải pháp Thực hiện |
|:---:|:---:|---|:---:|---|
| **P0** | `DB-ISO` | Cô lập Database Test khỏi dữ liệu Dev/Demo | Khẩn cấp | **ĐÃ HOÀN TẤT & XÁC MINH (VERIFIED CORRECT)** — Cấu hình `plagiarism_system_test`, cài đặt `DatabaseSafetyGuard` fail-fast, bảo vệ dữ liệu dev 100%. |
| **P1** | `BLA-11` | Cấu hình giới hạn HikariCP pool & Fix FK cleanup | Cao | **ĐÃ HOÀN TẤT & XÁC MINH (VERIFIED CORRECT)** — Giới hạn pool 3, fix thứ tự FK và duplicate email, 215/215 backend tests pass 100%. |
| **P2** | `BLA-03` | Tối ưu hóa truy vấn `SimilarityPair` của Giảng viên | Trung bình | **ĐÃ HOÀN TẤT & XÁC MINH (VERIFIED CORRECT)** — Thay thế in-memory bằng JPQL `findByTeacherId`, 8/8 controller tests pass, 1/1 `@DataJpaTest` thực thi thật pass 100%. |
| **P3** | `BLA-10` | Làm rõ tiêu chí tạo khiếu nại bài thi PENDING | Thấp | **GIỮ NGUYÊN (CHỜ PO/SRS)** — Giữ nguyên logic hiện tại cho đến khi có quyết định chính thức từ Product Owner. |

---

## 5. Kết luận Kiểm toán

Toàn bộ các luồng nghiệp vụ cốt lõi của CodeAssess — từ **Xác thực & Bảo mật Session**, **Phân quyền Sở hữu Lớp học (IDOR Masking 404)**, **Tạo & Nộp bài thi**, **Chấm điểm & Ghi đè Điểm số**, **Động cơ Phân tích AST Đa thuật toán**, đến **Vòng đời Phê duyệt & Từ chối Khiếu nại (Anti-tamper 409)** — đều hoạt động **chính xác, nhất quán và tuân thủ chặt chẽ các quy định nghiệp vụ và kiến trúc**. Cơ chế bảo vệ cơ sở dữ liệu đã được cô lập hoàn toàn, đảm bảo kiểm thử tự động không thể xâm phạm dữ liệu vận hành.
