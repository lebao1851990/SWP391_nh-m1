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
