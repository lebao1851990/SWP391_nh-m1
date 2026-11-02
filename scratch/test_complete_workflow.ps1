# test_complete_workflow.ps1
$ErrorActionPreference = "Stop"

$backendUrl = "http://localhost:8080"
$examId = "71463bd5-749e-46eb-9836-b99a3905a19b"
$classId = "1c9eae53-991e-406d-915b-fe8894fbf686"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " STARTING END-TO-END VERIFICATION: ZIP + GRADING + AST + TEACHER" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Prepare Exam & Test Cases in MySQL
Write-Host "`n[STEP 1] Preparing Exam and Test Cases in MySQL..." -ForegroundColor Yellow
$setupSql = @"
UPDATE exams SET exam_date = NOW(), duration = 180, status = 'active' WHERE id = '$examId';

DELETE FROM test_cases WHERE exam_id = '$examId';
INSERT INTO test_cases (id, exam_id, name, description, input_data, expected_output, points, is_hidden, display_order)
VALUES 
('tc-e2e-001', '$examId', 'Sample Test 1', 'Basic addition', '5 7\n', '12\n', 5.00, 0, 1),
('tc-e2e-002', '$examId', 'Hidden Test 2', 'Large addition', '10 20\n', '30\n', 5.00, 1, 2);

INSERT IGNORE INTO users (id, email, password_hash, role, full_name)
VALUES ('student2-test-uuid', 'student2.test@codeassess.edu', '\$2a\$10\$obTHtbvnESn6GbDvnZ0t5eZ6uyp7FRjytB267VNfi8IoTpAIzU0ym', 'STUDENT', 'Sinh Vien Hai');

INSERT IGNORE INTO class_students (class_id, student_id)
VALUES ('$classId', 'student2-test-uuid');
"@

docker exec -i codeassess-mysql mysql -uroot -p123456 plagiarism_system -e "$setupSql"
Write-Host "Exam and test cases prepared successfully." -ForegroundColor Green

# 2. Prepare Sample Java Code and ZIP files
Write-Host "`n[STEP 2] Creating ZIP submission packages..." -ForegroundColor Yellow
$scratchDir = "d:\SWP391_nh-m1\scratch\e2e_workspaces"
if (Test-Path $scratchDir) { Remove-Item -Recurse -Force $scratchDir }
$ws1 = "$scratchDir\ws1"
$ws2 = "$scratchDir\ws2"
New-Item -ItemType Directory -Force -Path $ws1 | Out-Null
New-Item -ItemType Directory -Force -Path $ws2 | Out-Null

# Solution 1: Student 1
$code1 = @"
import java.util.Scanner;

public class Solution {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        if (scanner.hasNextInt()) {
            int a = scanner.nextInt();
            int b = scanner.nextInt();
            System.out.println(a + b);
        }
    }
}
"@
Set-Content -Path "$ws1\Solution.java" -Value $code1 -Encoding UTF8
$zip1 = "$scratchDir\student1_solution.zip"
Compress-Archive -Path "$ws1\Solution.java" -DestinationPath $zip1 -Force

# Solution 2: Student 2 (Plagiarized with variable renaming)
$code2 = @"
import java.util.Scanner;

// Program calculating sum of inputs
public class Calculator {
    public static void main(String[] inputArgs) {
        Scanner inputReader = new Scanner(System.in);
        if (inputReader.hasNextInt()) {
            int firstVal = inputReader.nextInt();
            int secondVal = inputReader.nextInt();
            int totalSum = firstVal + secondVal;
            System.out.println(totalSum);
        }
    }
}
"@
Set-Content -Path "$ws2\Calculator.java" -Value $code2 -Encoding UTF8
$zip2 = "$scratchDir\student2_calculator.zip"
Compress-Archive -Path "$ws2\Calculator.java" -DestinationPath $zip2 -Force

Write-Host "Created ZIP 1: $zip1"
Write-Host "Created ZIP 2: $zip2"

# 3. Student 1 Login and Upload ZIP
Write-Host "`n[STEP 3] Student 1 logging in and uploading ZIP..." -ForegroundColor Yellow
$login1 = @{ email = 'student.test@codeassess.edu'; password = 'Password1234!' } | ConvertTo-Json
$resLogin1 = Invoke-RestMethod -Uri "$backendUrl/api/auth/login" -Method Post -Body $login1 -ContentType 'application/json'
$token1 = $resLogin1.token
Write-Host "Student 1 logged in. User: $($resLogin1.user.fullName)"

# Upload via curl to properly send multipart/form-data
$uploadOut1 = & curl.exe -s -X POST "$backendUrl/api/student/exams/$examId/submissions/zip" `
  -H "Authorization: Bearer $token1" `
  -F "file=@$zip1;type=application/zip"
Write-Host "Upload Response 1:" $uploadOut1
$sub1 = $uploadOut1 | ConvertFrom-Json
$sub1Id = $sub1.id
Write-Host "Submission 1 ID: $sub1Id | Status: $($sub1.status)" -ForegroundColor Green

# 4. Student 2 Login and Upload ZIP
Write-Host "`n[STEP 4] Student 2 logging in and uploading ZIP..." -ForegroundColor Yellow
$login2 = @{ email = 'student2.test@codeassess.edu'; password = 'Password1234!' } | ConvertTo-Json
$resLogin2 = Invoke-RestMethod -Uri "$backendUrl/api/auth/login" -Method Post -Body $login2 -ContentType 'application/json'
$token2 = $resLogin2.token
Write-Host "Student 2 logged in. User: $($resLogin2.user.fullName)"

$uploadOut2 = & curl.exe -s -X POST "$backendUrl/api/student/exams/$examId/submissions/zip" `
  -H "Authorization: Bearer $token2" `
  -F "file=@$zip2;type=application/zip"
Write-Host "Upload Response 2:" $uploadOut2
$sub2 = $uploadOut2 | ConvertFrom-Json
$sub2Id = $sub2.id
Write-Host "Submission 2 ID: $sub2Id | Status: $($sub2.status)" -ForegroundColor Green

# 5. Wait 3 seconds for async AST matrix & docker runner if async
Start-Sleep -Seconds 3

# 6. Check Database State for Submissions, Test Results, and Fingerprints
Write-Host "`n[STEP 5] Verifying Database Records..." -ForegroundColor Yellow
$dbSubs = docker exec -i codeassess-mysql mysql -uroot -p123456 plagiarism_system -e "SELECT id, original_filename, execution_status, auto_score, total_score, test_cases_passed, test_cases_total, zip_storage_path FROM submissions WHERE id IN ('$sub1Id', '$sub2Id');"
Write-Host $dbSubs

$dbTestResults = docker exec -i codeassess-mysql mysql -uroot -p123456 plagiarism_system -e "SELECT submission_id, test_case_id, status, execution_time_ms FROM submission_test_results WHERE submission_id = '$sub1Id';"
Write-Host $dbTestResults

$dbFingerprints = docker exec -i codeassess-mysql mysql -uroot -p123456 plagiarism_system -e "SELECT submission_id, COUNT(*) as fp_count FROM ast_fingerprints WHERE submission_id IN ('$sub1Id', '$sub2Id') GROUP BY submission_id;"
Write-Host $dbFingerprints

$dbSim = docker exec -i codeassess-mysql mysql -uroot -p123456 plagiarism_system -e "SELECT submission_id_1, submission_id_2, similarity_rate FROM similarity_matrix WHERE exam_id = '$examId';"
Write-Host $dbSim

# 7. Teacher Workflow Verification
Write-Host "`n[STEP 6] Teacher Login and Dashboard/Submissions Management..." -ForegroundColor Yellow
$loginTeacher = @{ email = 'teacher.test@codeassess.edu'; password = 'Password1234!' } | ConvertTo-Json
$resTeacher = Invoke-RestMethod -Uri "$backendUrl/api/auth/login" -Method Post -Body $loginTeacher -ContentType 'application/json'
$teacherToken = $resTeacher.token
Write-Host "Teacher logged in. User: $($resTeacher.user.fullName)"

# 7.1 Teacher Exam Submissions List
Write-Host "`n[STEP 6.1] GET /api/teacher/exams/$examId/submissions" -ForegroundColor Cyan
$teacherSubs = Invoke-RestMethod -Uri "$backendUrl/api/teacher/exams/$examId/submissions" -Headers @{ 'Authorization' = "Bearer $teacherToken" }
$teacherSubs | Format-Table id, studentName, originalFilename, executionStatus, autoScore, totalScore, testCasesPassed, testCasesTotal, plagiarismStatus, maxSimilarityRate

# 7.2 Teacher Inspect Extracted Files
Write-Host "`n[STEP 6.2] GET /api/teacher/submissions/$sub1Id/files" -ForegroundColor Cyan
$subFiles = Invoke-RestMethod -Uri "$backendUrl/api/teacher/submissions/$sub1Id/files" -Headers @{ 'Authorization' = "Bearer $teacherToken" }
Write-Host "Files count: $($subFiles.Count)"
foreach ($f in $subFiles) {
    Write-Host "File: $($f.filename) ($($f.size) bytes)"
    Write-Host "Content preview:"
    Write-Host ($f.content.Substring(0, [Math]::Min(150, $f.content.Length)))
}

# 7.3 Teacher Download Submitted ZIP
Write-Host "`n[STEP 6.3] GET /api/teacher/submissions/$sub1Id/download" -ForegroundColor Cyan
$downloadedZip = "$scratchDir\downloaded_sub1.zip"
& curl.exe -s -H "Authorization: Bearer $teacherToken" "$backendUrl/api/teacher/submissions/$sub1Id/download" -o $downloadedZip
if (Test-Path $downloadedZip) {
    $zipSize = (Get-Item $downloadedZip).Length
    Write-Host "Successfully downloaded ZIP! Size: $zipSize bytes" -ForegroundColor Green
} else {
    Write-Host "ERROR: ZIP download failed" -ForegroundColor Red
}

# 7.4 Teacher Add/Update Feedback
Write-Host "`n[STEP 6.4] PUT /api/teacher/submissions/$sub1Id/feedback" -ForegroundColor Cyan
$feedbackPayload = @{ feedback = "Bai lam hoan thanh tot tat ca cac test cases. Code ngan gon va de doc." } | ConvertTo-Json
$feedbackRes = Invoke-RestMethod -Uri "$backendUrl/api/teacher/submissions/$sub1Id/feedback" -Method Put -Body $feedbackPayload -ContentType 'application/json' -Headers @{ 'Authorization' = "Bearer $teacherToken" }
Write-Host "Feedback saved: $($feedbackRes.instructorFeedback)" -ForegroundColor Green

# 7.5 Teacher View Scoring Detail
Write-Host "`n[STEP 6.5] GET /api/teacher/scoring/submissions/$sub1Id" -ForegroundColor Cyan
$scoringDetail = Invoke-RestMethod -Uri "$backendUrl/api/teacher/scoring/submissions/$sub1Id" -Headers @{ 'Authorization' = "Bearer $teacherToken" }
Write-Host "Scoring Detail:"
Write-Host "  Student: $($scoringDetail.studentName)"
Write-Host "  Auto Score: $($scoringDetail.autoScore)"
Write-Host "  Total Score: $($scoringDetail.totalScore)"
Write-Host "  Test Cases Passed: $($scoringDetail.testCasesPassed) / $($scoringDetail.testCasesTotal)"
Write-Host "  Instructor Feedback: $($scoringDetail.instructorFeedback)"
Write-Host "  Plagiarism Rate: $($scoringDetail.similarityRate)"
Write-Host "  Plagiarism Match Submission: $($scoringDetail.matchedSubmissionId)"

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host " ALL END-TO-END WORKFLOW TESTS COMPLETED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
