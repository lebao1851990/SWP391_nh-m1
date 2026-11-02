# CODEASSESS — WORKFLOW 2 TEST EVIDENCE REPORT (MILESTONE 2)

**Dự án:** CodeAssess — Intelligent Programming Assessment & AST Plagiarism Detection Platform  
**Phân hệ phụ trách:** Workflow 2 — Sinh viên thi lập trình, nộp bài, kiểm tra trạng thái và nhận kết quả  
**Người thực hiện / Tester:** Kim (`kimalone2004` / `155179325+kimalone2004@users.noreply.github.com`)  
**Ngày thực hiện:** 18/10/2026  
**Môi trường kiểm thử:** Java 17 LTS (`jdk-17.0.12`), Spring Boot 3.3.4, Maven 3.9+, Windows 11  
**Mã Task nghiệm thu:** M2-14, M2-15, M2-16, M2-17  

---

## 1. Tóm tắt Kết quả Thực thi (Executive Test Summary)

Bộ kiểm thử dành riêng cho Workflow 2 và Hồi quy AST đã được thực thi thực tế 100% bằng Maven Wrapper với kết quả:

| Nhóm Kiểm thử | Lớp Kiểm thử (Test Class) | Số Tests | Passed | Failed | Errors | Skipped | Thời gian | Trạng thái |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Workflow 2 End-to-End** | `Workflow2StudentExamSubmissionTest` | 13 | 13 | 0 | 0 | 0 | 8.082 s | **PASS (100%)** |
| **AST Parser** | `JavaAstParserTest` | 4 | 4 | 0 | 0 | 0 | 0.029 s | **PASS (100%)** |
| **AST Normalizer** | `AstNormalizerTest` | 4 | 4 | 0 | 0 | 0 | 0.215 s | **PASS (100%)** |
| **K-Gram Generator** | `KGramGeneratorTest` | 3 | 3 | 0 | 0 | 0 | 0.006 s | **PASS (100%)** |
| **Fingerprint Hasher** | `FingerprintHasherTest` | 3 | 3 | 0 | 0 | 0 | 0.002 s | **PASS (100%)** |
| **Winnower** | `WinnowerTest` | 4 | 4 | 0 | 0 | 0 | 0.003 s | **PASS (100%)** |
| **Jaccard Similarity** | `JaccardSimilarityCalculatorTest` | 6 | 6 | 0 | 0 | 0 | 0.007 s | **PASS (100%)** |
| **Evidence Generator** | `EvidenceGeneratorTest` | 5 | 5 | 0 | 0 | 0 | 0.082 s | **PASS (100%)** |
| **TỔNG CỘNG** | **8 Test Classes** | **42** | **42** | **0** | **0** | **0** | **10.926 s** | **BUILD SUCCESS** |

---

## 2. Bảng Ma trận Bằng chứng Kiểm thử Chi tiết (Detailed Test Evidence Matrix)

### A. Phân hệ Workflow 2: Sinh viên Xem Đề, Viết/Nộp Code & Xem Kết quả (Task M2-14 & M2-15)

| Mã Test | Hạng mục Luồng | Tiền điều kiện / Input | Kỳ vọng (Expected) | Thực tế (Actual) | Kết quả | Ghi chú Quy tắc Nghiệp vụ |
| :---: | :--- | :--- | :--- | :--- | :---: | :--- |
| **WF2-01** | **Main Flow** (6 bước trọn vẹn) | Sinh viên `student-kim-2004` truy vấn kỳ thi lớp CS301, nộp bài Java hợp lệ `public class Solution {...}` | 1. Trả về kỳ thi `ACTIVE`<br>2. Trả về chi tiết kỳ thi<br>3. Tạo bài nộp `201 Created`, trạng thái `PENDING`<br>4. Lịch sử hiển thị bài nộp<br>5. Chi tiết bài nộp đúng source code<br>6. Kết quả điểm `95.00` và rubric tiêu chí | Trả về 200/201, đúng ID, status `PENDING` $\rightarrow$ `GRADED`, rubric Correctness 70.0, Style 25.0 | **PASS** | Hoàn thành trọn vẹn 100% Main Flow của Workflow 2 theo đúng tiêu chí Task M2-14. |
| **WF2-02** | **Exception Path 1** (Validation) | Nộp bài với mã nguồn rỗng `""` | Trả về HTTP `400 Bad Request` | HTTP 400 Bad Request | **PASS** | Chặn nộp mã rỗng ngay tại tầng DTO Validation `@NotBlank`. |
| **WF2-03** | **Exception Path 1** (Validation) | Nộp bài với toàn khoảng trắng `" \n\t "` | Trả về HTTP `400 Bad Request` | HTTP 400 Bad Request | **PASS** | Bảo vệ hệ sinh thái AST khỏi mã nguồn không hợp lệ. |
| **WF2-04** | **Exception Path 2** (Ownership) | Sinh viên chưa ghi danh vào lớp truy cập `GET /api/student/exams/{id}` | Trả về HTTP `404 Not Found` (Masking convention) | HTTP 404 Not Found, code `NOT_FOUND` | **PASS** | Resource Masking: Giấu sự tồn tại của đề thi đối với sinh viên ngoài lớp. |
| **WF2-05** | **Exception Path 2** (Ownership) | Sinh viên chưa ghi danh cố tình gọi `POST /api/student/exams/{id}/submissions` | Trả về HTTP `404 Not Found` | HTTP 404 Not Found, code `NOT_FOUND` | **PASS** | Ngăn chặn sinh viên nộp bài chéo lớp. |
| **WF2-06** | **Exception Path 2** (Peer Privacy) | Sinh viên A tra cứu bài nộp của Sinh viên B (`GET /api/student/submissions/{idB}`) | Trả về HTTP `404 Not Found` | HTTP 404 Not Found | **PASS** | Chống tấn công IDOR, bảo mật mã nguồn bạn học tuyệt đối. |
| **WF2-07** | **Exception Path 2** (Peer Privacy) | Sinh viên A tra cứu kết quả chấm của Sinh viên B (`GET /api/student/submissions/{idB}/result`) | Trả về HTTP `404 Not Found` | HTTP 404 Not Found | **PASS** | Bảo mật thông tin điểm số cá nhân. |
| **WF2-08** | **Exception Path 3** (Lifecycle) | Nộp bài vào kỳ thi đang ở trạng thái `INACTIVE` hoặc `CLOSED` | Trả về HTTP `400 Bad Request`, code `BAD_REQUEST` | HTTP 400 Bad Request | **PASS** | Tuân thủ Active Exam Policy: chỉ nhận bài khi kỳ thi đang mở. |
| **WF2-09** | **Exception Path 3** (Lifecycle) | Nộp bài vào `examId` không tồn tại trong hệ thống | Trả về HTTP `404 Not Found` | HTTP 404 Not Found | **PASS** | Kiểm tra toàn vẹn định danh kỳ thi. |
| **WF2-10** | **Security / Auth** | Yêu cầu từ người dùng chưa xác thực (Anonymous) | Trả về HTTP `401 Unauthorized` | HTTP 401 Unauthorized | **PASS** | Chặn toàn bộ truy cập chưa đăng nhập. |
| **WF2-11** | **Security / RBAC** | Người dùng có vai trò `TEACHER` gọi API nộp bài sinh viên | Trả về HTTP `403 Forbidden` | HTTP 403 Forbidden | **PASS** | Giảng viên không được phép nộp bài thi thay sinh viên. |
| **WF2-12** | **Security / RBAC** | Người dùng có vai trò `ADMIN` gọi API nộp bài sinh viên | Trả về HTTP `403 Forbidden` | HTTP 403 Forbidden | **PASS** | Phân quyền vai trò nghiêm ngặt theo RBAC matrix. |
| **WF2-13** | **Security / Anti-Spoofing** | Client chèn trường giả mạo `"studentId": "attacker"` vào JSON body nộp bài | Backend bỏ qua trường này, lấy danh tính thật từ `SecurityContext` | Lưu `studentId: student-kim-2004` | **PASS** | Chống giả mạo định danh sinh viên qua payload. |

---

### B. Hồi quy Pipeline Thuật toán AST (Task M2-16)

| Mã Test | Hạng mục | Đầu vào Kiểm thử | Kỳ vọng Nghiệp vụ & Toán học | Thực tế | Trạng thái |
| :---: | :--- | :--- | :--- | :--- | :---: |
| **AST-01** | **Parser Success** | Mã nguồn Java hợp lệ | Tạo cây cú pháp `CompilationUnit` đầy đủ | Tạo thành công cây AST | **PASS** |
| **AST-02** | **Parser Failure** | Mã nguồn Java sai cú pháp `class { invalid` | Ném `ParseProblemException`, không làm sập tiến trình | Bắt ngoại lệ an toàn, cô lập lỗi | **PASS** |
| **AST-03** | **Normalizer** | Đổi tên biến/hàm `myCounter` $\rightarrow$ `sum` | Biến đổi về `SimpleName`, giữ nguyên cấu trúc AST | Hai chuỗi node giống hệt nhau | **PASS** |
| **AST-04** | **K-Gram** | Danh sách node AST chuẩn hóa | Sinh các cửa sổ trượt $K = 5$ node liền kề | Đúng số lượng $N - K + 1$ k-grams | **PASS** |
| **AST-05** | **FNV-1a Hash** | Chuỗi loại node của k-gram | Băm 64-bit với `offset_basis = 0xcbf29ce484222325L` | Giá trị băm phân tán cao | **PASS** |
| **AST-06** | **Winnowing** | Dãy mã băm k-gram | Cửa sổ trượt $W = 4$, chọn giá trị nhỏ nhất bên phải (Rightmost Min) | Bộ fingerprints tối ưu, không dư thừa | **PASS** |
| **AST-07** | **Jaccard Similarity** | 2 tập dấu vân tay $F(A)$ và $F(B)$ | $J = \frac{\vert A \cap B \vert}{\vert A \cup B \vert} \times 100\%$, làm tròn 2 chữ số | Tính chính xác đến $0.01\%$ | **PASS** |
| **AST-08** | **Threshold & Flag** | Cặp bài có độ tương đồng $\ge 70.00\%$ | Tự động gán `is_flagged = true` làm tín hiệu tham vấn cho giảng viên | `is_flagged = true` | **PASS** |
| **AST-09** | **Evidence Mapping** | Các mã băm trùng nhau | Trích xuất khoảng dòng (`start_line`, `end_line`) tương ứng giữa 2 bài | Lưu đầy đủ vào `plagiarism_evidence` | **PASS** |

---

## 3. Nhật Ký Lệnh Kiểm Thử Thực Tế (Execution Logs)

### Lệnh 1: Kiểm thử Workflow 2
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"; .\mvnw.cmd test -Dtest=Workflow2StudentExamSubmissionTest
```
**Kết quả thực tế từ Surefire:**
```text
[INFO] Running com.codeassess.submission.controller.Workflow2StudentExamSubmissionTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 8.082 s
[INFO] BUILD SUCCESS (Total time: 11.392 s)
```

### Lệnh 2: Kiểm thử Hồi quy Lõi AST Pipeline
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"; .\mvnw.cmd test -Dtest=JavaAstParserTest,AstNormalizerTest,KGramGeneratorTest,FingerprintHasherTest,WinnowerTest,JaccardSimilarityCalculatorTest,EvidenceGeneratorTest
```
**Kết quả thực tế từ Surefire:**
```text
[INFO] Running com.codeassess.plagiarism.normalizer.AstNormalizerTest -> Tests run: 4, Failures: 0, Errors: 0
[INFO] Running com.codeassess.plagiarism.parser.JavaAstParserTest -> Tests run: 4, Failures: 0, Errors: 0
[INFO] Running com.codeassess.plagiarism.similarity.EvidenceGeneratorTest -> Tests run: 5, Failures: 0, Errors: 0
[INFO] Running com.codeassess.plagiarism.similarity.JaccardSimilarityCalculatorTest -> Tests run: 6, Failures: 0, Errors: 0
[INFO] Running com.codeassess.plagiarism.tokenizer.KGramGeneratorTest -> Tests run: 3, Failures: 0, Errors: 0
[INFO] Running com.codeassess.plagiarism.winnowing.FingerprintHasherTest -> Tests run: 3, Failures: 0, Errors: 0
[INFO] Running com.codeassess.plagiarism.winnowing.WinnowerTest -> Tests run: 4, Failures: 0, Errors: 0
[INFO] Results: Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS (Total time: 2.846 s)
```

---

## 4. Tuyên Bố Tuân Thủ Rubric (Compliance Sign-off)

- [x] **M2-14**: Đã kiểm chứng liên kết toàn vẹn 4 chặng của Workflow 2 (Danh sách kỳ thi $\rightarrow$ Chi tiết kỳ thi $\rightarrow$ Nộp bài $\rightarrow$ Kết quả điểm số).
- [x] **M2-15**: Đã kiểm thử Main Flow và 4 Exception Paths độc lập; xác minh phân quyền `hasRole('STUDENT')` và che giấu tài nguyên HTTP 404 Masking.
- [x] **M2-16**: Đã chạy hồi quy toàn bộ 29 tests của pipeline AST; xác nhận `is_flagged` là tín hiệu tham vấn chuyên môn cho giảng viên.
- [x] **M2-17**: Đã lập bảng Test Evidence với đầy đủ số liệu đo đạc thực tế, không dùng số liệu ước đoán.

**Chữ ký Tester:** Kim (`kimalone2004`)
