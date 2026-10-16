# Database P0 Audit — review và đối chiếu schema source

Trạng thái: source-level audit hoàn tất; live MySQL metadata và Flyway history chưa xác minh được. Migration V6 là đề xuất tương thích nguồn, chưa được chạy trên database thật.

## 1. Mục tiêu và phạm vi
Bản audit này chỉ kiểm tra các nguồn sau:
- Flyway migrations trong `Back end/src/main/resources/db/migration/`
- JPA entities trong `Back end/src/main/java/com/codeassess/**`
- Repository layer
- Datasource trong `application.yml` (chỉ để thử kết nối đọc metadata; file cấu hình ngoài phạm vi commit)

Không thực thi migration trên live database. Không chạy SQL phá hủy dữ liệu.

## 2. Ba lớp bằng chứng (không tương đương)
1. Schema trong source migration: có thể xác minh từ file SQL.
2. Schema ánh xạ JPA: có thể xác minh từ entity.
3. Schema thực tế MySQL / `flyway_schema_history`: **NOT VERIFIED**.

## 3. Chuỗi migration nguồn
- `V1__initial_schema.sql` — users, exams, submissions, ast_fingerprints, similarity_matrix, plagiarism_appeals
- `V1.1__extend_users_schema.sql` — email, password_hash, timestamps, CHECK role
- `V2__create_classes_and_extend_exams.sql` — classes, class_students, exams columns + FK
- `V3__extend_submissions_schema.sql` — source_code, scores, evaluated_at, created_at
- `V4__extend_appeals_schema.sql` — basis
- `V5__create_scoring_and_plagiarism_evidence.sql` — scoring_criteria, rule_scores, plagiarism_evidence
- `V6__align_scoring_and_evidence_columns.sql` — đề xuất thêm cột JPA, giữ cột legacy

`origin/main` đã có `V1__initial_schema.sql` (commit `23c0436`). Working tree local trước đó thiếu V1 vì HEAD đang behind `origin/main`. File trùng `V1__create_baseline_schema.sql` đã được loại khỏi thư mục migration để tránh hai version V1.

Sửa format V1.1 (tách ADD COLUMN) đã được hoàn tác: đổi checksum Flyway trên migration có thể đã áp dụng là không an toàn.

## 4. SRS
Không tìm thấy SRS chính thức liệt kê đúng 10 bảng / PK / FK bắt buộc. README mô tả kiến trúc, không phải schema chuẩn. **SRS status: NOT VERIFIED**. Source hiện có 11 bảng (kể cả `plagiarism_evidence`).

## 5. Đối chiếu migration vs JPA (source)

| Bảng | Trạng thái source | Ghi chú |
| --- | --- | --- |
| `users` | MATCH | PK `id`; unique `email`; CHECK role |
| `classes` | MATCH | FK teacher_id |
| `class_students` | MATCH | composite PK |
| `exams` | MATCH | FK class_id |
| `submissions` | MATCH | FK exam_id, student_id |
| `similarity_matrix` | MATCH | unique pair + FKs |
| `plagiarism_appeals` | MATCH | V4 thêm `basis` |
| `ast_fingerprints` | MATCH | |
| `scoring_criteria` | MATCH | |
| `rule_scores` | MISMATCH | V5: `score_awarded`; entity: `score` |
| `plagiarism_evidence` | MISMATCH | V5: `*_location`; entity: `*_start_line` / `*_end_line` |

Live MySQL: **NOT VERIFIED**.

## 6. Live metadata
Datasource working tree trỏ MySQL local `plagiarism_system`. Flyway app đang `enabled: false`, Hibernate `ddl-auto: validate`. Không có MySQL CLI trên PATH. Kết nối đọc `information_schema` không xác minh được trong phiên này; không chạy migration thử.

## 7. Row 13 — V6
Mismatch V5 vs JPA đã chứng minh bằng source, nên V6 là cầu nối không phá hủy:
- Thêm `rule_scores.score`, backfill từ `score_awarded` khi `score` NULL
- Thêm 4 cột line cho `plagiarism_evidence`
- Giữ cột legacy
- Không dùng `ADD COLUMN IF NOT EXISTS` (cú pháp MariaDB, không phải Flyway/MySQL chuẩn)

V6 **chưa** được coi là schema đích chính thức đã phát hành. Không sửa V6 nếu đã có trong `flyway_schema_history`. Không convert `*_location` sang line number vì format legacy chưa chứng minh.

## 8. Row 14
`StudentSubmissionServiceTest` dùng Mockito cho repository (interface) và `SubmissionMapper` thật (class concrete). Các case:
- Exam inactive → `IllegalStateException`
- Student chưa đăng ký → `ResourceNotFoundException`
- Student missing → `ResourceNotFoundException`
- Submit hợp lệ → lưu PENDING + `SubmissionCreatedEvent`
- GET by id missing / list mapping

Controller integration test kiểm tra studentId lấy từ principal, không từ body.

## 9. Row 15
Không tìm thấy mô tả chính thức trong workspace, issue tracker local, hay docs. Không bịa yêu cầu. **waiting for owner / instructor**.

## 10. Test contract
- `DatabaseSchemaContractTest` — chuỗi V1–V5, bảng, FK (static)
- `DatabaseJpaMappingMismatchTest` — hai mismatch đã chứng minh
- `DatabaseSchemaCompatibilityTest` — V6 không phá hủy, không IF NOT EXISTS
- Các test trên **không** xác minh live MySQL

## 11. Kết quả kiểm thử thực tế (JDK 17.0.17)
Maven Surefire targeted run, exit code 0, BUILD SUCCESS, `Tests run: 28, Failures: 0, Errors: 0, Skipped: 0`:
- `DatabaseJpaMappingMismatchTest`: 2
- `DatabaseSchemaCompatibilityTest`: 1
- `DatabaseSchemaContractTest`: 2
- `StudentSubmissionIntegrationTest`: 9
- `SubmissionControllerTest`: 8
- `StudentSubmissionServiceTest`: 6

Full backend suite: NOT RUN.
Live MySQL metadata: BLOCKED (SQLSTATE 28000 ACCESS_DENIED). Port 3306 open, credentials in local `application.yml` rejected. No Flyway history read.
