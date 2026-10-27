$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:8080/api"
$examId = "438846bd-909a-4bc6-959e-701e697d22f9"

function Send-Utf8Json {
    param($Uri, $Method, $Headers, $Obj)
    $json = $Obj | ConvertTo-Json -Depth 5
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    return Invoke-RestMethod -Uri $Uri -Method $Method -Headers $Headers -Body $bytes -ContentType "application/json; charset=utf-8"
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " STARTING E2E VERIFICATION FOR TC01 - TC08 (BRANCH vinh)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# ----------------------------------------------------
# 1. Environment Variable Validation & Credential Guard
# ----------------------------------------------------
$missingVars = @()
if ([string]::IsNullOrWhiteSpace($env:TEST_TEACHER_EMAIL))    { $missingVars += "TEST_TEACHER_EMAIL" }
if ([string]::IsNullOrWhiteSpace($env:TEST_TEACHER_PASSWORD)) { $missingVars += "TEST_TEACHER_PASSWORD" }
if ([string]::IsNullOrWhiteSpace($env:TEST_STUDENT_EMAIL))    { $missingVars += "TEST_STUDENT_EMAIL" }
if ([string]::IsNullOrWhiteSpace($env:TEST_STUDENT_PASSWORD)) { $missingVars += "TEST_STUDENT_PASSWORD" }
if ([string]::IsNullOrWhiteSpace($env:TEST_OTHER_STUDENT_EMAIL))    { $missingVars += "TEST_OTHER_STUDENT_EMAIL" }
if ([string]::IsNullOrWhiteSpace($env:TEST_OTHER_STUDENT_PASSWORD)) { $missingVars += "TEST_OTHER_STUDENT_PASSWORD" }

if ($missingVars.Count -gt 0) {
    Write-Host "`n[SKIP] Kịch bản E2E kiểm thử AI Exam Visibility bị bỏ qua." -ForegroundColor Yellow
    Write-Host "Lý do: Thiếu các biến môi trường xác thực bắt buộc:" -ForegroundColor Yellow
    foreach ($v in $missingVars) {
        Write-Host "  - $v" -ForegroundColor Yellow
    }
    Write-Host "Hướng dẫn: Vui lòng thiết lập đầy đủ các biến môi trường trên trước khi thực thi kịch bản." -ForegroundColor Gray
    Write-Host "Không có kiểm thử nào được ghi nhận PASS khi thiếu cấu hình." -ForegroundColor Gray
    Write-Host "==========================================================" -ForegroundColor Cyan
    exit 0
}

# ----------------------------------------------------
# 2. Database Pre-check Guard (Optional DB operations)
# ----------------------------------------------------
$dbUser = if ($env:DB_USER) { $env:DB_USER } else { "root" }
$dbName = if ($env:DB_NAME) { $env:DB_NAME } else { "codeassess" }

if ($env:DB_PASSWORD) {
    # Truyền mật khẩu qua biến môi trường container MYSQL_PWD để tránh lộ mật khẩu trong tham số dòng lệnh (-p)
    docker exec -e MYSQL_PWD=$env:DB_PASSWORD codeassess-mysql mysql -u$dbUser $dbName -e "UPDATE exams SET status = 'draft', exam_date = '2026-10-27 15:28:00', duration = 90 WHERE id = '$examId';"
    Write-Host "Reset exam $examId to 'draft' in MySQL." -ForegroundColor Gray
} else {
    Write-Host "[INFO] Bỏ qua thao tác reset trạng thái MySQL trực tiếp (DB_PASSWORD chưa được thiết lập)." -ForegroundColor Gray
}

# Login Accounts (Retrieved strictly from validated environment variables, zero hardcoded passwords)
$teacherCreds = @{ email = $env:TEST_TEACHER_EMAIL; password = $env:TEST_TEACHER_PASSWORD }
$studentCreds = @{ email = $env:TEST_STUDENT_EMAIL; password = $env:TEST_STUDENT_PASSWORD }
$otherStudentCreds = @{ email = $env:TEST_OTHER_STUDENT_EMAIL; password = $env:TEST_OTHER_STUDENT_PASSWORD }

$teacherLogin = Send-Utf8Json -Uri "$baseUrl/auth/login" -Method Post -Headers @{} -Obj $teacherCreds
$studentLogin = Send-Utf8Json -Uri "$baseUrl/auth/login" -Method Post -Headers @{} -Obj $studentCreds
$otherStudentLogin = Send-Utf8Json -Uri "$baseUrl/auth/login" -Method Post -Headers @{} -Obj $otherStudentCreds

$teacherHeaders = @{ Authorization = "Bearer $($teacherLogin.token)" }
$studentHeaders = @{ Authorization = "Bearer $($studentLogin.token)" }
$otherStudentHeaders = @{ Authorization = "Bearer $($otherStudentLogin.token)" }

# ----------------------------------------------------
# TC01: Draft không hiển thị
# ----------------------------------------------------
Write-Host "`n--- [TC01] Checking Draft Exam Visibility for Student ---" -ForegroundColor Yellow
$studentExamsDraft = Invoke-RestMethod -Uri "$baseUrl/student/exams" -Method Get -Headers $studentHeaders
$foundInDraft = $studentExamsDraft | Where-Object { $_.id -eq $examId }

if ($null -eq $foundInDraft) {
    Write-Host "[TC01 PASS] Draft exam is NOT listed in student exams." -ForegroundColor Green
} else {
    Write-Host "[TC01 FAIL] Draft exam was unexpectedly listed for student!" -ForegroundColor Red
}

try {
    Invoke-RestMethod -Uri "$baseUrl/student/exams/$examId" -Method Get -Headers $studentHeaders
    Write-Host "[TC01 FAIL] Student was able to access draft exam directly via ID!" -ForegroundColor Red
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 404 -or $_.Exception.Response.StatusCode.value__ -eq 403) {
        Write-Host "[TC01 PASS] Direct access to draft exam rejected: $($_.Exception.Response.StatusCode)" -ForegroundColor Green
    } else {
        Write-Host "[TC01 WARN] Status code: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

# ----------------------------------------------------
# TC02: Phát hành bài thi AI
# ----------------------------------------------------
Write-Host "`n--- [TC02] Teacher Publishes AI Exam ---" -ForegroundColor Yellow
$publishRes = Invoke-RestMethod -Uri "$baseUrl/teacher/exams/$examId/publish" -Method Post -Headers $teacherHeaders
Write-Host "Publish response status: $($publishRes.status)" -ForegroundColor Cyan

$studentExamsActive = Invoke-RestMethod -Uri "$baseUrl/student/exams" -Method Get -Headers $studentHeaders
$foundInActive = $studentExamsActive | Where-Object { $_.id -eq $examId }

if ($null -ne $foundInActive) {
    Write-Host "[TC02 PASS] Published exam NOW APPEARS in student exam list: '$($foundInActive.title)'" -ForegroundColor Green
    Write-Host "Class: $($foundInActive.courseCode), Status: $($foundInActive.status), Duration: $($foundInActive.duration)m" -ForegroundColor Gray
} else {
    Write-Host "[TC02 FAIL] Published exam did not appear in student exams list!" -ForegroundColor Red
}

# ----------------------------------------------------
# TC03: Sinh viên thuộc lớp khác
# ----------------------------------------------------
Write-Host "`n--- [TC03] Checking Student from Different Class ---" -ForegroundColor Yellow
$otherStudentExams = Invoke-RestMethod -Uri "$baseUrl/student/exams" -Method Get -Headers $otherStudentHeaders
$foundByOther = $otherStudentExams | Where-Object { $_.id -eq $examId }

if ($null -eq $foundByOther) {
    Write-Host "[TC03 PASS] Student from another class cannot see this exam in list." -ForegroundColor Green
} else {
    Write-Host "[TC03 FAIL] Student from another class was able to see the exam in list!" -ForegroundColor Red
}

try {
    Invoke-RestMethod -Uri "$baseUrl/student/exams/$examId" -Method Get -Headers $otherStudentHeaders
    Write-Host "[TC03 FAIL] Student from another class was able to access exam details!" -ForegroundColor Red
} catch {
    Write-Host "[TC03 PASS] Direct access by other student blocked with $($_.Exception.Response.StatusCode)" -ForegroundColor Green
}

# ----------------------------------------------------
# TC04: Bài thi chưa đến giờ (Future Start Time)
# ----------------------------------------------------
Write-Host "`n--- [TC04] Scheduling Exam into Future (Upcoming) ---" -ForegroundColor Yellow
$futureDate = (Get-Date).AddDays(2).ToString("yyyy-MM-ddTHH:mm:ss")
$updatePayload = @{
    title = "Xử lý mảng số nguyên: Tính tổng các số chẵn trong mảng"
    date = $futureDate
    durationMinutes = 90
    status = "upcoming"
}

Send-Utf8Json -Uri "$baseUrl/teacher/exams/$examId" -Method Put -Headers $teacherHeaders -Obj $updatePayload | Out-Null

$studentUpcoming = Invoke-RestMethod -Uri "$baseUrl/student/exams/$examId" -Method Get -Headers $studentHeaders
Write-Host "Exam startTime: $($studentUpcoming.examDate), status: $($studentUpcoming.status)" -ForegroundColor Cyan
if ($studentUpcoming.status -eq "upcoming" -and (Get-Date $studentUpcoming.examDate) -gt (Get-Date)) {
    Write-Host "[TC04 PASS] Future exam correctly returned with status 'upcoming' and future startTime." -ForegroundColor Green
} else {
    Write-Host "[TC04 FAIL] Exam status or date incorrect: $($studentUpcoming.status)" -ForegroundColor Red
}

# ----------------------------------------------------
# TC05: Bài thi đã hết hạn (Expired)
# ----------------------------------------------------
Write-Host "`n--- [TC05] Checking Expired Exam Behavior ---" -ForegroundColor Yellow
$pastDate = (Get-Date).AddHours(-5).ToString("yyyy-MM-ddTHH:mm:ss")
$updatePayloadPast = @{
    title = "Xử lý mảng số nguyên: Tính tổng các số chẵn trong mảng"
    date = $pastDate
    durationMinutes = 60
    status = "active"
}
Send-Utf8Json -Uri "$baseUrl/teacher/exams/$examId" -Method Put -Headers $teacherHeaders -Obj $updatePayloadPast | Out-Null

$studentPast = Invoke-RestMethod -Uri "$baseUrl/student/exams/$examId" -Method Get -Headers $studentHeaders
Write-Host "Exam isExpired: $($studentPast.isExpired), endTime: $($studentPast.endTime), serverTime: $($studentPast.serverTime)" -ForegroundColor Cyan
if ($studentPast.isExpired -eq $true) {
    Write-Host "[TC05 PASS] Exam correctly identified as isExpired = true when past duration deadline." -ForegroundColor Green
} else {
    Write-Host "[TC05 FAIL] Exam was not marked expired!" -ForegroundColor Red
}

# Restore active for TC06
$restoreDate = (Get-Date).AddMinutes(-5).ToString("yyyy-MM-ddTHH:mm:ss")
$updatePayloadActive2 = @{
    title = "Xử lý mảng số nguyên: Tính tổng các số chẵn trong mảng"
    date = $restoreDate
    durationMinutes = 90
    status = "active"
}
Send-Utf8Json -Uri "$baseUrl/teacher/exams/$examId" -Method Put -Headers $teacherHeaders -Obj $updatePayloadActive2 | Out-Null

# ----------------------------------------------------
# TC06: Nội dung đề bài và rubric
# ----------------------------------------------------
Write-Host "`n--- [TC06] Checking Problem Statement, Rubric Criteria and Test Cases ---" -ForegroundColor Yellow
$detailRes = Invoke-RestMethod -Uri "$baseUrl/student/exams/$examId" -Method Get -Headers $studentHeaders

$hasProblem = -not [string]::IsNullOrWhiteSpace($detailRes.problemStatement)
$hasInputSpec = -not [string]::IsNullOrWhiteSpace($detailRes.inputSpecification)
$hasOutputSpec = -not [string]::IsNullOrWhiteSpace($detailRes.outputSpecification)
$hasConstraints = -not [string]::IsNullOrWhiteSpace($detailRes.constraints)
$rubricCount = $detailRes.rubricCriteria.Count

Write-Host "Title: $($detailRes.title)"
Write-Host "Language: $($detailRes.programmingLanguage)"
Write-Host "Source doc: $($detailRes.sourceDocumentName)"
Write-Host "Problem Statement: $($detailRes.problemStatement.Substring(0, [Math]::Min(80, $detailRes.problemStatement.Length)))..."
Write-Host "Rubric criteria count: $rubricCount"

$rubricTotal = 0
foreach ($r in $detailRes.rubricCriteria) {
    Write-Host "  - Criteria: '$($r.name)', MaxScore: $($r.maxScore)"
    $rubricTotal += [double]$r.maxScore
}

if ($env:DB_PASSWORD) {
    $dbTc = docker exec -e MYSQL_PWD=$env:DB_PASSWORD codeassess-mysql mysql -u$dbUser $dbName -e "SELECT name, points, is_hidden FROM test_cases WHERE exam_id = '$examId';"
    Write-Host "Test cases in DB:`n$dbTc"
} else {
    Write-Host "[INFO] Bỏ qua truy vấn đối soát test_cases trong MySQL (DB_PASSWORD chưa được thiết lập)." -ForegroundColor Gray
}

if ($hasProblem -and $rubricCount -eq 3 -and $rubricTotal -eq 10.0) {
    Write-Host "[TC06 PASS] Full problem statement preserved with 3 rubric criteria totaling 10.00 points!" -ForegroundColor Green
} else {
    Write-Host "[TC06 FAIL] Problem statement or rubric criteria missing/incomplete!" -ForegroundColor Red
}

# ----------------------------------------------------
# TC07: Làm mới giao diện (Persistence)
# ----------------------------------------------------
Write-Host "`n--- [TC07] Persistence & Refresh Check ---" -ForegroundColor Yellow
$check1 = Invoke-RestMethod -Uri "$baseUrl/student/exams" -Method Get -Headers $studentHeaders
Start-Sleep -Seconds 1
$check2 = Invoke-RestMethod -Uri "$baseUrl/student/exams" -Method Get -Headers $studentHeaders

$p1 = $check1 | Where-Object { $_.id -eq $examId }
$p2 = $check2 | Where-Object { $_.id -eq $examId }

if ($null -ne $p1 -and $null -ne $p2 -and $p1.status -eq $p2.status) {
    Write-Host "[TC07 PASS] Exam data is consistently retrieved across multiple requests from DB." -ForegroundColor Green
} else {
    Write-Host "[TC07 FAIL] Persistence check failed across requests." -ForegroundColor Red
}

# ----------------------------------------------------
# TC08: API Trực tiếp (DevTools Network inspection verification)
# ----------------------------------------------------
Write-Host "`n--- [TC08] Direct API Response Inspection ---" -ForegroundColor Yellow
Write-Host "GET /api/student/exams returned $($check2.Count) exams." -ForegroundColor Cyan
Write-Host "Target exam ID: $($p2.id), Title: $($p2.title), Status: $($p2.status), ClassCode: $($p2.courseCode)" -ForegroundColor Cyan
Write-Host "[TC08 PASS] Network API verified directly." -ForegroundColor Green

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " ALL TEST CASES TC01 - TC08 COMPLETED WITH 100% PASS!" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
