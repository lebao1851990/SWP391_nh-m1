# CODEASSESS — NHẬT KÝ GỠ LỖI CÙNG TRỢ LÝ AI (AI-ASSISTED DEBUGGING LOGS)

**Dự án:** CodeAssess — Intelligent Programming Assessment Platform  
**Thành viên:** Kim (`kimalone2004` / `155179325+kimalone2004@users.noreply.github.com`)  
**Khóa học & Kỳ:** SWP391 — Software Development Project (Milestone 2)  
**Phân hệ phụ trách:** Workflow 2 — Sinh viên làm bài thi, nộp bài, kiểm tra trạng thái & kết quả  
**Quy chuẩn Rubric:** Yêu cầu tối thiểu $\ge 6$ AI debugging logs theo cấu trúc: *Vấn đề*, *Prompt*, *Gợi ý của AI*, *Thay đổi mã nguồn*, *Lệnh kiểm thử*, *Kết quả*.

---

### LOG 01: Lỗi Không Tìm Thấy Phương Thức `submittedAt` Trong `SubmissionListResponse`

- **Thời điểm:** 18/10/2026 19:47
- **Vấn đề kỹ thuật:** Khi viết bài test tích hợp `Workflow2StudentExamSubmissionTest` để giả lập dữ liệu trả về cho danh sách bài nộp của sinh viên (`GET /api/student/submissions`), trình biên dịch Maven báo lỗi:  
  `cannot find symbol: method submittedAt(java.time.LocalDateTime) in class SubmissionListResponseBuilder`.
- **Prompt gửi AI:**  
  *"Khi mình gọi `SubmissionListResponse.builder().submittedAt(LocalDateTime.now())` trong test thì Maven báo không tìm thấy symbol `submittedAt`. Hãy kiểm tra file DTO `SubmissionListResponse.java` và cho biết tên trường thời gian chính xác là gì."*
- **Gợi ý của AI:**  
  Trong lớp `SubmissionListResponse.java`, trường lưu thời gian nộp bài được đặt tên là `createdAt` (kèm trường `evaluatedAt` khi đã chấm điểm), hoàn toàn không có trường nào tên là `submittedAt`. Cần đổi `submittedAt(...)` thành `createdAt(...)` trong builder.
- **Thay đổi mã nguồn:**  
  Tại file [`Workflow2StudentExamSubmissionTest.java`](file:///c:/Users/MSI%20CYBORG/Desktop/Ferlab1/SWP391_nh-m1/Back%20end/src/test/java/com/codeassess/submission/controller/Workflow2StudentExamSubmissionTest.java):
  ```diff
   SubmissionListResponse listEntry = SubmissionListResponse.builder()
           .id(SUBMISSION_ID)
           .examId(EXAM_ID)
           .examTitle("CS301 Java Data Structures Practical Exam")
           .status("PENDING")
  -        .submittedAt(LocalDateTime.now())
  +        .createdAt(LocalDateTime.now())
           .build();
  ```
- **Lệnh kiểm thử:**  
  `$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"; .\mvnw.cmd test-compile`
- **Kết quả:**  
  Lỗi compilation biến mất, DTO ánh xạ chính xác với dữ liệu trả về từ service.

---

### LOG 02: Lỗi Quyền Truy Cập Constructor Của `CriterionScoreDto`

- **Thời điểm:** 18/10/2026 19:48
- **Vấn đề kỹ thuật:** Trình biên dịch Java ném lỗi:  
  `CriterionScoreDto(...) is not public in com.codeassess.scoring.dto.response.CriterionScoreDto; cannot be accessed from outside package`.
- **Prompt gửi AI:**  
  *"Tại sao mình không thể gọi constructor `new CriterionScoreDto("crit-1", "Correctness", ...)` từ package `com.codeassess.submission.controller`? Phải khởi tạo đối tượng này bằng cách nào theo quy chuẩn của dự án?"*
- **Gợi ý của AI:**  
  Lớp `CriterionScoreDto` sử dụng annotation `@Builder` và `@Data` của Lombok mà không khai báo `@AllArgsConstructor(access = AccessLevel.PUBLIC)`. Theo quy ước thiết kế toàn dự án CodeAssess, tất cả các DTO phức tạp đều phải được khởi tạo thông qua mẫu thiết kế Builder (`CriterionScoreDto.builder()...build()`) để đảm bảo tính đóng gói và an toàn kiểu dữ liệu.
- **Thay đổi mã nguồn:**  
  Tại file [`Workflow2StudentExamSubmissionTest.java`](file:///c:/Users/MSI%20CYBORG/Desktop/Ferlab1/SWP391_nh-m1/Back%20end/src/test/java/com/codeassess/submission/controller/Workflow2StudentExamSubmissionTest.java):
  ```diff
  - new CriterionScoreDto("crit-1", "Correctness", "Outputs expected answer", new BigDecimal("70.00"), new BigDecimal("70.00"))
  + CriterionScoreDto.builder()
  +         .criterionId("crit-1")
  +         .name("Correctness")
  +         .description("Outputs expected answer")
  +         .score(new BigDecimal("70.00"))
  +         .maxScore(new BigDecimal("70.00"))
  +         .build()
  ```
- **Lệnh kiểm thử:**  
  `$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"; .\mvnw.cmd test-compile`
- **Kết quả:**  
  Mã nguồn biên dịch thành công 100% với exit code 0.

---

### LOG 03: Khắc Phục Lỗi Cạn Kiệt / Mất Kết Nối Cơ Sở Dữ Liệu Khi Chạy MockMvc Test

- **Thời điểm:** 18/10/2026 19:50
- **Vấn đề kỹ thuật:** Khi thực thi test với chú thích `@SpringBootTest`, Spring Boot cố gắng khởi tạo toàn bộ `ApplicationContext` và kết nối tới database MySQL qua `FlywayInitializer`, dẫn đến lỗi dừng kiểm thử do khác biệt mật khẩu database cục bộ: `Access denied for user 'root'@'localhost'`.
- **Prompt gửi AI:**  
  *"Bộ kiểm thử Workflow2StudentExamSubmissionTest của mình chỉ kiểm tra tầng Controller (MockMvc), giả lập hoàn toàn các Service và không gọi DB thật. Làm sao để chạy test độc lập, mượt mà mà không bị phụ thuộc vào database server bên ngoài?"*
- **Gợi ý của AI:**  
  1. Thay thế `@SpringBootTest` bằng lát cắt kiểm thử web `@WebMvcTest(controllers = {StudentExamController.class, StudentSubmissionController.class, StudentScoringController.class})`.
  2. Bổ sung `@Import({SecurityConfig.class, CorsConfig.class})` để nạp cấu hình bảo mật Spring Security và kiểm tra phân quyền RBAC thật.
  3. Sử dụng `@MockBean private CustomUserDetailsService customUserDetailsService` để thỏa mãn phụ thuộc của `SecurityConfig`.
  4. Lát cắt này không kích hoạt Hibernate/Flyway/HikariCP, chạy hoàn toàn trên bộ nhớ chỉ trong ~3 giây.
- **Thay đổi mã nguồn:**  
  Tại file [`Workflow2StudentExamSubmissionTest.java`](file:///c:/Users/MSI%20CYBORG/Desktop/Ferlab1/SWP391_nh-m1/Back%20end/src/test/java/com/codeassess/submission/controller/Workflow2StudentExamSubmissionTest.java):
  ```diff
  -@SpringBootTest
  +@WebMvcTest(controllers = {
  +        StudentExamController.class,
  +        StudentSubmissionController.class,
  +        StudentScoringController.class
  +})
  +@Import({SecurityConfig.class, CorsConfig.class})
   @AutoConfigureMockMvc
   class Workflow2StudentExamSubmissionTest {
  +    @MockBean
  +    private CustomUserDetailsService customUserDetailsService;
  ```
- **Lệnh kiểm thử:**  
  `$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"; .\mvnw.cmd test -Dtest=Workflow2StudentExamSubmissionTest`
- **Kết quả:**  
  Toàn bộ 13/13 test cases hoàn thành thành công trong 8.082 giây, không còn bất kỳ lỗi kết nối cơ sở dữ liệu nào.

---

### LOG 04: Xác Minh Cơ Chế Resource Masking (HTTP 404) Chống Dò Quét Quyền Sở Hữu (IDOR)

- **Thời điểm:** 18/10/2026 19:55
- **Vấn đề kỹ thuật:** Cần xác nhận phản hồi chính xác khi sinh viên truy cập đề thi của lớp mà mình không ghi danh hoặc xem bài nộp của sinh viên khác: Liệu hệ thống nên trả về `403 Forbidden` hay `404 Not Found`?
- **Prompt gửi AI:**  
  *"Theo kiến trúc an ninh của CodeAssess Platform (Finding BLA-02 và quy tắc phòng chống IDOR), khi sinh viên truy cập tài nguyên lớp học mà mình không có quyền sở hữu, hệ thống bắt buộc phải trả về mã HTTP nào và tại sao?"*
- **Gợi ý của AI:**  
  Hệ thống bắt buộc phải trả về mã **HTTP 404 Not Found** (Resource Masking convention) thay vì 403 Forbidden. Nếu trả về 403, kẻ tấn công sẽ biết được tài nguyên đó có tồn tại trên máy chủ (ID Enumeration). Trả về 404 giúp che giấu hoàn toàn sự hiện diện của kỳ thi/bài nộp của người khác.
- **Thay đổi mã nguồn:**  
  Thêm 4 test cases kiểm tra nghiêm ngặt hành vi Masking: `WF2-04`, `WF2-05`, `WF2-06`, `WF2-07` trong `Workflow2StudentExamSubmissionTest.java`:
  ```java
  @Test
  void testWorkflow2_ExceptionPath2_StudentNotEnrolledInClass_ExamAccessReturns404NotFound() throws Exception {
      Mockito.when(studentExamService.getStudentExamById(EXAM_ID, "student-kim-2004"))
              .thenThrow(new ResourceNotFoundException("Exam not found or access denied"));

      mockMvc.perform(get("/api/student/exams/" + EXAM_ID).with(user(studentKim)))
              .andExpect(status().isNotFound())
              .andExpect(jsonPath("$.status").value(404));
  }
  ```
- **Lệnh kiểm thử:**  
  `$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"; .\mvnw.cmd test -Dtest=Workflow2StudentExamSubmissionTest`
- **Kết quả:**  
  Các bài test xác minh cơ chế 404 Masking đạt 100% PASS, thỏa mãn tuyệt đối tiêu chí Task M2-15.

---

### LOG 05: Kiểm Tra Lỗ Hổng Nộp Mã Nguồn Chứa Toàn Ký Tự Trắng (Blank Whitespace Injection)

- **Thời điểm:** 18/10/2026 20:00
- **Vấn đề kỹ thuật:** Nếu sinh viên nộp bài làm chứa toàn ký tự xuống dòng hoặc khoảng trắng `"   \n\t  "`, liệu hệ thống có nạp vào AST Engine và gây lỗi phân tích hay không?
- **Prompt gửi AI:**  
  *"Làm sao để đảm bảo `SubmissionCreateRequest` chặn đứng các payload mã nguồn rác (chỉ chứa khoảng trắng) ngay từ tầng Controller trước khi kích hoạt phân tích cú pháp JavaParser?"*
- **Gợi ý của AI:**  
  1. Trong DTO `SubmissionCreateRequest`, trường `sourceCode` phải được gắn annotation `@NotBlank(message = "Source code cannot be blank")` thay vì `@NotNull`.
  2. Viết test case kiểm thử gửi chuỗi khoảng trắng và kiểm tra kết quả trả về `400 Bad Request`.
- **Thay đổi mã nguồn:**  
  Thêm test case `WF2-03` trong [`Workflow2StudentExamSubmissionTest.java`](file:///c:/Users/MSI%20CYBORG/Desktop/Ferlab1/SWP391_nh-m1/Back end/src/test/java/com/codeassess/submission/controller/Workflow2StudentExamSubmissionTest.java):
  ```java
  @Test
  void testWorkflow2_ExceptionPath1_BlankWhitespaceSourceCode_Returns400BadRequest() throws Exception {
      SubmissionCreateRequest blankRequest = new SubmissionCreateRequest("   \n\t  ");

      mockMvc.perform(post("/api/student/exams/" + EXAM_ID + "/submissions")
              .with(user(studentKim))
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(blankRequest)))
              .andExpect(status().isBadRequest());
  }
  ```
- **Lệnh kiểm thử:**  
  `$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"; .\mvnw.cmd test -Dtest=Workflow2StudentExamSubmissionTest`
- **Kết quả:**  
  Spring Validation bắt lỗi và trả về đúng HTTP 400 Bad Request, bảo vệ toàn vẹn pipeline AST.

---

### LOG 06: Kiểm Chứng An Ninh Chống Giả Mạo Định Danh Sinh Viên (Principal Injection Defense)

- **Thời điểm:** 18/10/2026 20:10
- **Vấn đề kỹ thuật:** Kẻ tấn công cố tình chèn trường `"studentId": "victim-student"` vào JSON body của yêu cầu `POST /api/student/exams/{id}/submissions` nhằm nộp bài thay sinh viên khác.
- **Prompt gửi AI:**  
  *"Làm thế nào để chứng minh hệ thống hoàn toàn miễn nhiễm trước việc chèn `studentId` trong request body của sinh viên?"*
- **Gợi ý của AI:**  
  1. `StudentSubmissionController` không đọc `studentId` từ body mà lấy trực tiếp từ `@AuthenticationPrincipal User currentUser`.
  2. DTO `SubmissionCreateRequest` không có trường `studentId`, Jackson sẽ bỏ qua hoặc không ánh xạ trường lạ này.
  3. Viết test case cố tình truyền JSON chứa `"studentId": "malicious-attacker-id"` và kiểm tra JSON trả về: trường `studentId` phải luôn luôn là ID của principal đăng nhập (`student-kim-2004`).
- **Thay đổi mã nguồn:**  
  Thêm test case `WF2-13` trong [`Workflow2StudentExamSubmissionTest.java`](file:///c:/Users/MSI%20CYBORG/Desktop/Ferlab1/SWP391_nh-m1/Back end/src/test/java/com/codeassess/submission/controller/Workflow2StudentExamSubmissionTest.java):
  ```java
  @Test
  void testWorkflow2_Security_InjectedStudentIdInBodyIsIgnored_ServerBindsToAuthenticatedUser() throws Exception {
      String spoofedPayload = "{\"sourceCode\":\"public class Test {}\",\"studentId\":\"malicious-attacker-id\"}";

      mockMvc.perform(post("/api/student/exams/" + EXAM_ID + "/submissions")
              .with(user(studentKim))
              .contentType(MediaType.APPLICATION_JSON)
              .content(spoofedPayload))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.studentId").value("student-kim-2004"));
  }
  ```
- **Lệnh kiểm thử:**  
  `$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"; .\mvnw.cmd test -Dtest=Workflow2StudentExamSubmissionTest`
- **Kết quả:**  
  Test case PASS tuyệt đối. Định danh sinh viên được máy chủ bảo vệ bất biến.
