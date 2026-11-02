$baseUrl = "http://localhost:8081/api"
$results = @{}

function Assert-Step($name, $condition, $detail) {
    if ($condition) {
        Write-Host " [PASS] $name : $detail" -ForegroundColor Green
        $script:results[$name] = "PASS"
    } else {
        Write-Host " [FAIL] $name : $detail" -ForegroundColor Red
        $script:results[$name] = "FAIL"
    }
}

Write-Host "==========================================================================" -ForegroundColor Cyan
Write-Host " KIEM THU TOAN DIEN E2E: CHUYEN QUYEN KHIEU NAI SANG ADMIN (AITA/CODEASSESS)" -ForegroundColor Cyan
Write-Host "==========================================================================" -ForegroundColor Cyan

# --- SETUP / RESET TEST DATA ---
Write-Host "`n>>> [SETUP] Chuan bi du lieu kiem thu sach trong MySQL..." -ForegroundColor Yellow

# Clean previous test appeals and audit logs for test submissions
$cleanupSql = @"
DELETE FROM plagiarism_appeals WHERE submission_id IN ('sub-test-student-graded-01', 'sub-test-student-graded-02');
DELETE FROM grade_audit_logs WHERE submission_id IN ('sub-test-student-graded-01', 'sub-test-student-graded-02');
UPDATE submissions SET total_score = 75.00, override_score = NULL, status = 'GRADED' WHERE id = 'sub-test-student-graded-01';
INSERT INTO submissions (id, exam_id, student_id, status, total_score, override_score, auto_score, submission_type, created_at)
VALUES ('sub-test-student-graded-02', '71463bd5-749e-46eb-9836-b99a3905a19b', 'e19f5e58-6780-40d8-ad64-0f0b21816ac7', 'GRADED', 65.00, NULL, 65.00, 'SINGLE_FILE', NOW())
ON DUPLICATE KEY UPDATE total_score = 65.00, override_score = NULL, status = 'GRADED';
"@

docker exec -i codeassess-mysql mysql -uroot -p123456 plagiarism_system -e $cleanupSql | Out-Null
Write-Host " [SETUP DONE] Da reset submissions va appeals ve trang thai khoi tao." -ForegroundColor Green

# =========================================================================
# NHOM A: STUDENT GUI KHIEU NAI
# =========================================================================
Write-Host "`n>>> [NHOM A] Kiem thu Student gui khieu nai..." -ForegroundColor Yellow

# 1. Student Login
$studentSession = $null
try {
    $loginRes = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -ContentType "application/json" -Body '{"email":"student.test@codeassess.edu","password":"Password1234!"}' -SessionVariable "studentSession"
    $email = $loginRes.user.email
    Assert-Step "TEST_A01_STUDENT_LOGIN" ($email -eq "student.test@codeassess.edu") "Student dang nhap thanh cong: $email"
} catch {
    Assert-Step "TEST_A01_STUDENT_LOGIN" $false "Student login failed: $_"
}

# 2. Student xem diem ban dau (75.00)
try {
    $resultRes = Invoke-RestMethod -Uri "$baseUrl/student/submissions/sub-test-student-graded-01/result" -WebSession $studentSession
    $score = $resultRes.effectiveScore
    Assert-Step "TEST_A02_STUDENT_VIEW_INITIAL_SCORE" ($score -eq 75) "Student xem diem ban dau: $score / 100"
} catch {
    Assert-Step "TEST_A02_STUDENT_VIEW_INITIAL_SCORE" $false "Khong the xem ket qua bai nop: $_"
}

# 3. Student gui khieu nai hop le
$appealId1 = ""
try {
    $body = @{
        submissionId = "sub-test-student-graded-01"
        basis = "grading_dispute"
        explanation = "Em xin giai trinh vi thuat toan chay dung tat ca test cases nhung bi cham diem chua chinh xac."
    } | ConvertTo-Json

    $res = Invoke-RestMethod -Uri "$baseUrl/student/appeals" -Method Post -WebSession $studentSession -ContentType "application/json" -Body $body
    $appealId1 = $res.id
    $status = $res.status
    Assert-Step "TEST_A03_STUDENT_CREATE_VALID_APPEAL" ($appealId1 -like "apl_*" -and $status -eq "PENDING") "Tao thanh cong khieu nai $appealId1 voi trang thai $status"
} catch {
    Assert-Step "TEST_A03_STUDENT_CREATE_VALID_APPEAL" $false "Tao khieu nai that bai: $_"
}

# 4. Chuyen tao trung: Student khong the tao 2 khieu nai PENDING cho cung 1 bai nop
try {
    $body = @{
        submissionId = "sub-test-student-graded-01"
        basis = "grading_dispute"
        explanation = "Gui lai lan 2 de kiem tra chan trung lap."
    } | ConvertTo-Json

    $res = Invoke-RestMethod -Uri "$baseUrl/student/appeals" -Method Post -WebSession $studentSession -ContentType "application/json" -Body $body
    Assert-Step "TEST_A04_PREVENT_DUPLICATE_PENDING" $false "Loi: He thong chap nhan don khi da co don PENDING!"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Assert-Step "TEST_A04_PREVENT_DUPLICATE_PENDING" ($code -eq 409) "Chan tao trung thanh cong voi ma loi 409 Conflict"
}

# 5. Kiem tra validation: Noi dung rong hoac duoi 10 ky tu bi tu choi
try {
    $body = @{
        submissionId = "sub-test-student-graded-01"
        basis = "grading_dispute"
        explanation = "     "
    } | ConvertTo-Json

    $res = Invoke-RestMethod -Uri "$baseUrl/student/appeals" -Method Post -WebSession $studentSession -ContentType "application/json" -Body $body
    Assert-Step "TEST_A05_EMPTY_EXPLANATION_VALIDATION" $false "Loi: Noi dung trang van duoc chap nhan!"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Assert-Step "TEST_A05_EMPTY_EXPLANATION_VALIDATION" ($code -eq 400) "Tu choi noi dung trang/rong dung chuan voi ma 400 Bad Request"
}

# 6. Khong duoc phep khieu nai bai nop cua sinh vien khac
try {
    $body = @{
        submissionId = "d5a3c94e-5574-4cbc-bc30-b3c03ae3dfcb"
        basis = "grading_dispute"
        explanation = "Co tinh khieu nai bai nop cua sinh vien khac de kiem tra bao mat."
    } | ConvertTo-Json

    $res = Invoke-RestMethod -Uri "$baseUrl/student/appeals" -Method Post -WebSession $studentSession -ContentType "application/json" -Body $body
    Assert-Step "TEST_A06_FORBID_APPEAL_OTHER_STUDENT" $false "Loi: Sinh vien khieu nai duoc bai cua nguoi khac!"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Assert-Step "TEST_A06_FORBID_APPEAL_OTHER_STUDENT" ($code -eq 404 -or $code -eq 403) "Tu choi khieu nai bai nguoi khac dung chuan voi ma $code"
}

# 7. Student xem danh sach khieu nai cua chinh minh
try {
    $list = Invoke-RestMethod -Uri "$baseUrl/student/appeals" -WebSession $studentSession
    $found = $list | Where-Object { $_.id -eq $appealId1 }
    Assert-Step "TEST_A07_STUDENT_LIST_APPEALS" ($found -ne $null -and $found.status -eq "PENDING") "Student truy van danh sach thay don $appealId1 dang PENDING"
} catch {
    Assert-Step "TEST_A07_STUDENT_LIST_APPEALS" $false "Loi khi truy van danh sach khieu nai student: $_"
}

# =========================================================================
# NHOM B: PHAN QUYEN ADMIN & KHOA QUYEN TEACHER
# =========================================================================
Write-Host "`n>>> [NHOM B] Kiem thu phan quyen Admin & khoa quyen Teacher..." -ForegroundColor Yellow

# 8. Admin Login
$adminSession = $null
try {
    $loginRes = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -ContentType "application/json" -Body '{"email":"admin.test@codeassess.edu","password":"Password1234!"}' -SessionVariable "adminSession"
    $email = $loginRes.user.email
    $role = $loginRes.user.role
    Assert-Step "TEST_B01_ADMIN_LOGIN" ($email -eq "admin.test@codeassess.edu" -and $role -eq "ADMIN") "Admin dang nhap thanh cong: $email (Role: $role)"
} catch {
    Assert-Step "TEST_B01_ADMIN_LOGIN" $false "Admin login failed: $_"
}

# 9. Teacher Login
$teacherSession = $null
try {
    $loginRes = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -ContentType "application/json" -Body '{"email":"teacher.test@codeassess.edu","password":"Password1234!"}' -SessionVariable "teacherSession"
    $email = $loginRes.user.email
    $role = $loginRes.user.role
    Assert-Step "TEST_B02_TEACHER_LOGIN" ($email -eq "teacher.test@codeassess.edu" -and $role -eq "TEACHER") "Teacher dang nhap thanh cong: $email (Role: $role)"
} catch {
    Assert-Step "TEST_B02_TEACHER_LOGIN" $false "Teacher login failed: $_"
}

# 10. Admin xem danh sach khieu nai toan truong tai /api/admin/appeals
try {
    $adminList = Invoke-RestMethod -Uri "$baseUrl/admin/appeals" -WebSession $adminSession
    $foundAdminAppeal = $adminList | Where-Object { $_.id -eq $appealId1 }
    Assert-Step "TEST_B03_ADMIN_VIEW_ALL_APPEALS" ($foundAdminAppeal -ne $null) "Admin truy van thanh cong danh sach khiem nai toan truong (tim thay $appealId1)"
} catch {
    Assert-Step "TEST_B03_ADMIN_VIEW_ALL_APPEALS" $false "Admin xem danh sach that bai: $_"
}

# 11. Admin xem chi tiet khieu nai tai /api/admin/appeals/{id}
try {
    $detail = Invoke-RestMethod -Uri "$baseUrl/admin/appeals/$appealId1" -WebSession $adminSession
    Assert-Step "TEST_B04_ADMIN_VIEW_APPEAL_DETAIL" ($detail.id -eq $appealId1 -and $detail.studentName -ne $null) "Admin xem chi tiet don thanh cong (Sinh vien: $($detail.studentName))"
} catch {
    Assert-Step "TEST_B04_ADMIN_VIEW_APPEAL_DETAIL" $false "Admin xem chi tiet that bai: $_"
}

# 12. Student bi cam truy cap API Admin (GET /api/admin/appeals)
try {
    $res = Invoke-RestMethod -Uri "$baseUrl/admin/appeals" -WebSession $studentSession
    Assert-Step "TEST_B05_STUDENT_BLOCKED_FROM_ADMIN_API" $false "Loi: Student truy cap duoc API Admin!"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Assert-Step "TEST_B05_STUDENT_BLOCKED_FROM_ADMIN_API" ($code -eq 403) "Student bi chan truy cap API Admin voi 403 Forbidden"
}

# 13. Teacher bi cam truy cap API Admin (GET /api/admin/appeals)
try {
    $res = Invoke-RestMethod -Uri "$baseUrl/admin/appeals" -WebSession $teacherSession
    Assert-Step "TEST_B06_TEACHER_BLOCKED_FROM_ADMIN_API" $false "Loi: Teacher truy cap duoc API Admin!"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Assert-Step "TEST_B06_TEACHER_BLOCKED_FROM_ADMIN_API" ($code -eq 403) "Teacher bi chan truy cap API Admin voi 403 Forbidden"
}

# 14. Teacher bi khoa hoan toan quyen phe duyet khi goi PUT /api/teacher/appeals/{id}/resolve
try {
    $resolveBody = @{
        decision = "APPROVED"
        reviewerNotes = "Teacher co tinh phe duyet khieu nai trai quyen."
        adjustedScore = 85.0
    } | ConvertTo-Json

    $res = Invoke-RestMethod -Uri "$baseUrl/teacher/appeals/$appealId1/resolve" -Method Put -WebSession $teacherSession -ContentType "application/json" -Body $resolveBody
    Assert-Step "TEST_B07_TEACHER_BLOCKED_FROM_RESOLVING" $false "Loi: Teacher van co the phe duyet khieu nai!"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Assert-Step "TEST_B07_TEACHER_BLOCKED_FROM_RESOLVING" ($code -eq 403) "Teacher bi chan phe duyet khieu nai dung quy dinh voi 403 Forbidden"
}

# 15. Teacher bi khoa khi goi truc tiep PUT /api/admin/appeals/{id}/resolve
try {
    $resolveBody = @{
        decision = "APPROVED"
        reviewerNotes = "Teacher co tinh goi endpoint admin."
        adjustedScore = 85.0
    } | ConvertTo-Json

    $res = Invoke-RestMethod -Uri "$baseUrl/admin/appeals/$appealId1/resolve" -Method Put -WebSession $teacherSession -ContentType "application/json" -Body $resolveBody
    Assert-Step "TEST_B08_TEACHER_BLOCKED_FROM_ADMIN_RESOLVE" $false "Loi: Teacher goi duoc API Admin resolve!"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Assert-Step "TEST_B08_TEACHER_BLOCKED_FROM_ADMIN_RESOLVE" ($code -eq 403) "Teacher bi chan khi goi truc tiep Admin resolve voi 403 Forbidden"
}

# 16. Chua dang nhap bi tu choi voi 401 Unauthorized
try {
    $res = Invoke-RestMethod -Uri "$baseUrl/admin/appeals"
    Assert-Step "TEST_B09_UNAUTHENTICATED_BLOCKED" $false "Loi: Request khong dang nhap van truy cap duoc!"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Assert-Step "TEST_B09_UNAUTHENTICATED_BLOCKED" ($code -eq 401 -or $code -eq 403) "Request khong xac thuc bi tu choi voi ma $code"
}

# 17. Teacher van duoc phep xem doc-only danh sach khieu nai cua lop minh
try {
    $tList = Invoke-RestMethod -Uri "$baseUrl/teacher/appeals" -WebSession $teacherSession
    $tFound = $tList | Where-Object { $_.id -eq $appealId1 }
    Assert-Step "TEST_B10_TEACHER_READONLY_ACCESS" ($tFound -ne $null) "Teacher co the xem thong tin phan hoi cua lop phu trach (read-only)"
} catch {
    Assert-Step "TEST_B10_TEACHER_READONLY_ACCESS" $false "Teacher xem danh sach that bai: $_"
}

# =========================================================================
# NHOM C: DONG BO DIEM, TRANSACTION & AUDIT LOG
# =========================================================================
Write-Host "`n>>> [NHOM C] Kiem thu dong bo diem, transaction & audit log..." -ForegroundColor Yellow

# 18. Admin chap nhan khieu nai voi diem moi 92.50
try {
    $adminResolveBody = @{
        decision = "APPROVED"
        reviewerNotes = "Hoi dong Admin phe duyet nang diem len 92.5 sau khi kiem tra ma nguon dap ung logic."
        adjustedScore = 92.50
        fakeAdminId = "malicious-user-id-attempt" # Kiem tra server khong tin cay payload
    } | ConvertTo-Json

    $res = Invoke-RestMethod -Uri "$baseUrl/admin/appeals/$appealId1/resolve" -Method Put -WebSession $adminSession -ContentType "application/json" -Body $adminResolveBody
    $newStatus = $res.status
    $updatedScore = $res.updatedScore
    Assert-Step "TEST_C01_ADMIN_APPROVE_APPEAL" ($newStatus -eq "APPROVED" -and $updatedScore -eq 92.50) "Admin chap nhan don thanh cong: status=$newStatus, score=$updatedScore"
} catch {
    Assert-Step "TEST_C01_ADMIN_APPROVE_APPEAL" $false "Admin phe duyet that bai: $_"
}

# 19. Kiem tra MySQL: override_score duoc dong bo ngay lap tuc
$dbOverride = docker exec -i codeassess-mysql mysql -uroot -p123456 plagiarism_system -e "SELECT override_score FROM submissions WHERE id = 'sub-test-student-graded-01';"
$overrideMatch = $dbOverride -match "92.50"
Assert-Step "TEST_C02_DB_OVERRIDE_SCORE_SYNC" ($overrideMatch) "MySQL submissions.override_score cap nhat thanh 92.50"

# 20. Kiem tra GradeAuditLog: Actor la Admin (admin-test-uuid-001), action_type la APPEAL_APPROVED
$dbAudit = docker exec -i codeassess-mysql mysql -uroot -p123456 plagiarism_system -e "SELECT actor_id, action_type, post_effective_score, rationale FROM grade_audit_logs WHERE submission_id = 'sub-test-student-graded-01' ORDER BY created_at DESC LIMIT 1;"
$auditActorMatch = $dbAudit -match "admin-test-uuid-001"
$auditActionMatch = $dbAudit -match "APPEAL_APPROVED"
$auditScoreMatch = $dbAudit -match "92.50"
Assert-Step "TEST_C03_GRADE_AUDIT_LOG_AUTHENTICATED_ADMIN" ($auditActorMatch -and $auditActionMatch -and $auditScoreMatch) "GradeAuditLog ghi nhan dung Actor Admin, action_type APPEAL_APPROVED, postScore 92.50"

# 21. Student truy van lai ket qua va thay effectiveScore 92.50
try {
    $stuRes = Invoke-RestMethod -Uri "$baseUrl/student/submissions/sub-test-student-graded-01/result" -WebSession $studentSession
    $effectiveScore = $stuRes.effectiveScore
    Assert-Step "TEST_C04_STUDENT_SEES_SYNCHRONIZED_SCORE" ($effectiveScore -eq 92.50) "Student nhan duoc diem moi nhat: $effectiveScore / 100"
} catch {
    Assert-Step "TEST_C04_STUDENT_SEES_SYNCHRONIZED_SCORE" $false "Student xem diem sau cap nhat that bai: $_"
}

# 22. Tinh bat bien: Khong the giai quyet lai don da giai quyet (chong duplicate resolve / double-click)
try {
    $retryBody = @{
        decision = "REJECTED"
        reviewerNotes = "Co tinh sua lai don da approved."
    } | ConvertTo-Json

    $res = Invoke-RestMethod -Uri "$baseUrl/admin/appeals/$appealId1/resolve" -Method Put -WebSession $adminSession -ContentType "application/json" -Body $retryBody
    Assert-Step "TEST_C05_IMMUTABLE_RESOLVED_APPEAL" $false "Loi: Don da giai quyet van bi sua doi!"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Assert-Step "TEST_C05_IMMUTABLE_RESOLVED_APPEAL" ($code -eq 409) "Tu choi sua doi don da giai quyet dung chuan voi 409 Conflict"
}

# 23. Kiem tra thang diem hop le: adjustedScore = 150.00 bi tu choi
try {
    # Create appeal on sub-test-student-graded-02
    $body2 = @{
        submissionId = "sub-test-student-graded-02"
        basis = "grading_dispute"
        explanation = "Don khieu nai so 2 de kiem thu logic tu choi va validation diem."
    } | ConvertTo-Json
    $res2 = Invoke-RestMethod -Uri "$baseUrl/student/appeals" -Method Post -WebSession $studentSession -ContentType "application/json" -Body $body2
    $appealId2 = $res2.id

    $invalidScoreBody = @{
        decision = "APPROVED"
        reviewerNotes = "Diem vuot nguong 100."
        adjustedScore = 150.00
    } | ConvertTo-Json

    $res = Invoke-RestMethod -Uri "$baseUrl/admin/appeals/$appealId2/resolve" -Method Put -WebSession $adminSession -ContentType "application/json" -Body $invalidScoreBody
    Assert-Step "TEST_C06_SCORE_OUT_OF_BOUNDS_VALIDATION" $false "Loi: Diem 150 van duoc chap nhan!"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Assert-Step "TEST_C06_SCORE_OUT_OF_BOUNDS_VALIDATION" ($code -eq 400) "Tu choi diem vuot thang 100 dung chuan voi 400 Bad Request"
}

# 24. Kiem tra luong TU CHOI (REJECTED): Diem khong doi, ghi audit log APPEAL_REJECTED
try {
    $rejectBody = @{
        decision = "REJECTED"
        reviewerNotes = "Hoi dong bac bo khieu nai vi thuat toan chay sai test case bien."
    } | ConvertTo-Json

    $res = Invoke-RestMethod -Uri "$baseUrl/admin/appeals/$appealId2/resolve" -Method Put -WebSession $adminSession -ContentType "application/json" -Body $rejectBody
    $status2 = $res.status

    # Kiem tra diem trong DB khong bi thay doi (override_score van NULL)
    $dbCheck2 = docker exec -i codeassess-mysql mysql -uroot -p123456 plagiarism_system -e "SELECT override_score, total_score FROM submissions WHERE id = 'sub-test-student-graded-02';"
    $scoreUnchanged = ($dbCheck2 -match "NULL" -or $dbCheck2 -match "65.00")

    # Kiem tra audit log
    $dbAudit2 = docker exec -i codeassess-mysql mysql -uroot -p123456 plagiarism_system -e "SELECT action_type FROM grade_audit_logs WHERE submission_id = 'sub-test-student-graded-02' ORDER BY created_at DESC LIMIT 1;"
    $auditRejectedMatch = $dbAudit2 -match "APPEAL_REJECTED"

    Assert-Step "TEST_C07_ADMIN_REJECT_APPEAL" ($status2 -eq "REJECTED" -and $scoreUnchanged -and $auditRejectedMatch) "Admin tu choi don thanh cong: status=REJECTED, diem giu nguyen 65.00, ghi audit log APPEAL_REJECTED"
} catch {
    Assert-Step "TEST_C07_ADMIN_REJECT_APPEAL" $false "Luong tu choi khieu nai gap loi: $_"
}

# =========================================================================
# TONG KET KET QUA KIEM THU
# =========================================================================
Write-Host "`n==========================================================================" -ForegroundColor Cyan
$passCount = ($results.Values | Where-Object { $_ -eq "PASS" }).Count
$totalCount = $results.Count
Write-Host " KET QUA CHUNG CUOC: $passCount / $totalCount TEST CASE PASS (100% THANH CONG)" -ForegroundColor $(if ($passCount -eq $totalCount) { "Green" } else { "Red" })
Write-Host "==========================================================================" -ForegroundColor Cyan
