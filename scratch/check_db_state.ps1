# verify_e2e_workflow.ps1
$ErrorActionPreference = "Stop"

$backendUrl = "http://localhost:8080"
Write-Host "=== 1. Checking backend health and basic info ===" -ForegroundColor Cyan
try {
    $health = Invoke-RestMethod -Uri "$backendUrl/api/v1/auth/me" -Method Get
    Write-Host "Auth check response: $health"
} catch {
    Write-Host "Auth check status: $($_.Exception.Response.StatusCode.value__)"
}

# Let's find an existing teacher and student or login credentials from database or previous scripts
Write-Host "`n=== 2. Checking available accounts in MySQL ===" -ForegroundColor Cyan
$users = docker exec -i codeassess-mysql mysql -uroot -p123456 plagiarism_system -e "SELECT id, email, role, status FROM users LIMIT 10;"
Write-Host $users

# Check active exams
Write-Host "`n=== 3. Checking active exams ===" -ForegroundColor Cyan
$exams = docker exec -i codeassess-mysql mysql -uroot -p123456 plagiarism_system -e "SELECT id, title, status, exam_date, duration, course_class_id FROM exams WHERE deleted_at IS NULL LIMIT 5;"
Write-Host $exams

# Check test cases for exams
Write-Host "`n=== 4. Checking test cases for exams ===" -ForegroundColor Cyan
$testcases = docker exec -i codeassess-mysql mysql -uroot -p123456 plagiarism_system -e "SELECT id, exam_id, name, score_weight, is_hidden FROM test_cases LIMIT 10;"
Write-Host $testcases
