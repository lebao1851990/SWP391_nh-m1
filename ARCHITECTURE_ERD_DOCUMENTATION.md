# CODEASSESS — KIẾN TRÚC HỆ THỐNG, CƠ SỞ DỮ LIỆU & SƠ ĐỒ WORKFLOW 2 (MILESTONE 2)

**Dự án:** CodeAssess — Intelligent Programming Assessment & AST Plagiarism Detection Platform  
**Phân hệ:** Architecture, Database ERD & Student Workflow 2  
**Người lập / Author:** Kim (`kimalone2004` / `155179325+kimalone2004@users.noreply.github.com`)  
**Ngày cập nhật:** 18/10/2026  
**Mã Task nghiệm thu:** M2-18 (P2)  

---

## 1. Phân Tách Giữa Đặc Tả SRS Baseline và Cơ Sở Dữ Liệu Thực Tế

Theo tiêu chí Task M2-18 ("*Tách SRS baseline khỏi schema đã xác minh*"):

1. **Danh mục 10 Bảng theo SRS Baseline (Lý thuyết ban đầu):**
   - `users`, `course_classes`, `class_students`, `exams`, `programming_questions`, `submissions`, `ast_fingerprints`, `similarity_pairs`, `plagiarism_evidence`, `appeals`.
   - *Ghi chú khoảng trống:* Bảng `programming_questions` **không tồn tại dưới dạng bảng vật lý độc lập** trong MySQL hiện tại. Thay vào đó, mỗi kỳ thi (`exams`) gắn trực tiếp với 1 bài tập lập trình độc lập (Single-task model). Bảng `similarity_pairs` trong SRS thực tế được đặt tên vật lý là `similarity_matrix`.

2. **Danh mục 12 Bảng Thực Tế Đang Vận Hành (MySQL Physical Schema V1 $\rightarrow$ V8):**
   - Đã được chuẩn hóa qua các bản Flyway Migration:
     1. `users`: Tài khoản người dùng (Admin, Teacher, Student).
     2. `classes`: Lớp học phần do giảng viên phụ trách.
     3. `class_students`: Bảng nối học viên ghi danh vào lớp.
     4. `exams`: Kỳ thi lập trình.
     5. `exam_classes` *(Bổ sung V7)*: Hỗ trợ kỳ thi đa lớp (Many-to-Many).
     6. `submissions`: Bài nộp mã nguồn Java của sinh viên.
     7. `scoring_criteria` *(Bổ sung V5)*: Danh mục tiêu chí chấm điểm rubric.
     8. `rule_scores` *(Bổ sung V5)*: Điểm thành phần theo từng tiêu chí cho bài nộp.
     9. `ast_fingerprints`: Bộ dấu vân tay AST sau khi chạy thuật toán Winnowing.
     10. `similarity_matrix`: Ma trận kết quả so sánh cặp bài nộp (tương đương `similarity_pairs`).
     11. `plagiarism_evidence`: Tọa độ dòng mã nguồn trùng khớp giữa 2 bài nộp.
     12. `grade_audit_logs` *(Bổ sung V6)*: Nhật ký kiểm toán bất biến khi giảng viên sửa điểm hoặc sửa rubric.

---

## 2. Sơ Đồ Thực Thể Liên Kết Thực Tế (Physical Database ERD)

```mermaid
erDiagram
    users ||--o{ classes : "teaches"
    users ||--o{ class_students : "enrolled_in"
    classes ||--o{ class_students : "has"
    classes ||--o{ exams : "hosts"
    exams ||--o{ exam_classes : "assigned_to"
    classes ||--o{ exam_classes : "contains"
    
    exams ||--o{ submissions : "receives"
    users ||--o{ submissions : "submits"
    
    submissions ||--o{ rule_scores : "scored_by"
    scoring_criteria ||--o{ rule_scores : "defines"
    
    submissions ||--o{ ast_fingerprints : "generates"
    
    exams ||--o{ similarity_matrix : "evaluates"
    submissions ||--o{ similarity_matrix : "submission_a"
    submissions ||--o{ similarity_matrix : "submission_b"
    
    similarity_matrix ||--o{ plagiarism_evidence : "contains"
    
    submissions ||--o{ grade_audit_logs : "audited"
    users ||--o{ grade_audit_logs : "modified_by"

    users {
        varchar(36) id PK
        varchar(255) email UK
        varchar(255) password_hash
        varchar(20) role "ADMIN, TEACHER, STUDENT"
        varchar(255) full_name
        timestamp created_at
    }

    classes {
        varchar(36) id PK
        varchar(36) teacher_id FK
        varchar(100) code UK
        varchar(255) name
        varchar(100) semester
        timestamp created_at
    }

    class_students {
        varchar(36) class_id PK,FK
        varchar(36) student_id PK,FK
    }

    exams {
        varchar(36) id PK
        varchar(36) class_id FK "nullable for multi-class"
        varchar(255) title
        datetime exam_date
        int duration
        varchar(50) status "UPCOMING, ACTIVE, COMPLETED"
        timestamp created_at
    }

    submissions {
        varchar(36) id PK
        varchar(36) exam_id FK
        varchar(36) student_id FK
        varchar(50) status "PENDING, TESTING, GRADED"
        longtext source_code
        decimal total_score
        decimal override_score
        timestamp evaluated_at
        timestamp created_at
    }

    ast_fingerprints {
        bigint id PK
        varchar(36) submission_id FK
        bigint hash_value
        varchar(64) node_type
        int start_line
        int end_line
        datetime created_at
    }

    similarity_matrix {
        bigint id PK
        varchar(36) exam_id FK
        varchar(36) submission_a_id FK
        varchar(36) submission_b_id FK
        decimal similarity_rate
        int shared_hashes_count
        tinyint is_flagged "advisory signal"
        datetime created_at
    }

    plagiarism_evidence {
        varchar(36) id PK
        bigint similarity_matrix_id FK
        varchar(100) evidence_type
        text description
        int submission_a_start_line
        int submission_a_end_line
        int submission_b_start_line
        int submission_b_end_line
    }

    grade_audit_logs {
        varchar(36) id PK
        varchar(36) submission_id FK
        varchar(36) actor_id FK
        decimal pre_effective_score
        decimal post_effective_score
        text rationale
        timestamp created_at
    }
```

---

## 3. Sơ Đồ Tuần Tự Workflow 2 (Student Execution Sequence Diagram)

Sơ đồ thể hiện luồng hoạt động chuẩn mực từ lúc sinh viên xem đề bài, thực thi và nộp mã nguồn cho đến khi nhận kết quả:

```mermaid
sequenceDiagram
    autonumber
    actor Student as Sinh viên (Client)
    participant Gateway as Security Filter
    participant ExamCtrl as StudentExamController
    participant SubCtrl as StudentSubmissionController
    participant ScoreCtrl as StudentScoringController
    participant SubService as StudentSubmissionService
    participant Sandbox as DockerSandboxService
    participant AST as PlagiarismAnalysisListener
    participant DB as MySQL Database

    %% Bước 1: Xem danh sách kỳ thi
    Student->>Gateway: GET /api/student/exams (Cookie session)
    Gateway->>ExamCtrl: Chuyển tiếp với AuthenticationPrincipal
    ExamCtrl->>DB: Truy vấn kỳ thi thuộc lớp sinh viên đã ghi danh
    DB-->>ExamCtrl: Danh sách kỳ thi (ACTIVE/UPCOMING)
    ExamCtrl-->>Student: 200 OK [Exams List]

    %% Bước 2: Vào làm bài thi
    Student->>Gateway: GET /api/student/exams/{id}
    Gateway->>ExamCtrl: Kiểm tra quyền sở hữu lớp (Class Enrollment)
    alt Không thuộc lớp
        ExamCtrl-->>Student: 404 Not Found (Resource Masking)
    else Hợp lệ
        ExamCtrl->>DB: Lấy chi tiết đề thi, thời lượng, hạn nộp
        DB-->>ExamCtrl: Chi tiết kỳ thi
        ExamCtrl-->>Student: 200 OK (Exam Detail)
    end

    %% Bước 3: Soạn thảo và nộp bài
    Note over Student: Soạn mã Java trên giao diện Dark IDE
    Student->>Gateway: POST /api/student/exams/{id}/submissions {sourceCode}
    Gateway->>SubCtrl: Validate @NotBlank sourceCode
    alt Source code rỗng
        SubCtrl-->>Student: 400 Bad Request
    else Hợp lệ
        SubCtrl->>SubService: submitCode(examId, studentId, request)
        SubService->>DB: Kiểm tra exam.status == 'ACTIVE'
        SubService->>DB: INSERT INTO submissions (status='PENDING')
        DB-->>SubService: Submission created (sub-id)
        SubService-)AST: Phát sự kiện SubmissionCreatedEvent (AFTER_COMMIT)
        SubService-->>SubCtrl: SubmissionDetailResponse
        SubCtrl-->>Student: 201 Created {id: sub-id, status: PENDING}
    end

    %% Bước 4: Xử lý ngầm bất đồng bộ
    Note over AST,DB: Phân tích AST ngầm không làm chậm Client
    AST->>DB: Lấy các bài nộp khác cùng kỳ thi
    AST->>AST: Chuẩn hóa, K-Gram (K=5), Winnowing (W=4), Jaccard
    AST->>DB: Lưu ast_fingerprints & similarity_matrix (is_flagged nếu >= 70%)

    %% Bước 5: Xem kết quả chấm điểm
    Student->>Gateway: GET /api/student/submissions/{id}/result
    Gateway->>ScoreCtrl: getStudentResult(id, studentId)
    ScoreCtrl->>DB: Kiểm tra quyền sở hữu bài nộp
    alt Bài thi của sinh viên khác
        ScoreCtrl-->>Student: 404 Not Found (Peer Privacy)
    else Bài nộp chính chủ
        DB-->>ScoreCtrl: Điểm tổng, điểm ghi đè, rubric breakdown
        ScoreCtrl-->>Student: 200 OK {totalScore: 95.0, criteria: [...]}
    end
```

---

## 4. Hợp Đồng Giao Tiếp API Của Phân Hệ Sinh Viên (API Contracts)

| Phương thức | Đường dẫn (Path) | Quyền hạn | Request Payload | Response Shape | Mã Lỗi Chính |
| :--- | :--- | :---: | :--- | :--- | :--- |
| `GET` | `/api/student/exams` | `ROLE_STUDENT` | Không có | `200 OK`: Mảng `ExamResponse[]` | 401 (Chưa đăng nhập) |
| `GET` | `/api/student/exams/{id}` | `ROLE_STUDENT` | Không có | `200 OK`: `ExamResponse` chi tiết | 401, 404 (Không thuộc lớp / Không tồn tại) |
| `POST` | `/api/student/exams/{id}/submissions` | `ROLE_STUDENT` | JSON: `{ "sourceCode": "string" }` | `201 Created`: `SubmissionDetailResponse` | 400 (Mã rỗng / Thi đóng), 401, 404 (Lớp khác) |
| `GET` | `/api/student/submissions` | `ROLE_STUDENT` | Không có | `200 OK`: Mảng `SubmissionListResponse[]` | 401 |
| `GET` | `/api/student/submissions/{id}` | `ROLE_STUDENT` | Không có | `200 OK`: `SubmissionDetailResponse` | 401, 404 (Không phải bài của mình) |
| `GET` | `/api/student/submissions/{id}/result` | `ROLE_STUDENT` | Không có | `200 OK`: `StudentSubmissionResult` | 401, 404 (Không phải bài của mình) |

