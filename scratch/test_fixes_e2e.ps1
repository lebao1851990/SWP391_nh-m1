$ErrorActionPreference = "Continue"

Write-Host "=== TEST 1: FORGOT PASSWORD & RESET PASSWORD (BUG-AUTH-01) ===" -ForegroundColor Cyan

# 1.1 Unknown email
$unknownReq = @{ email = "nonexistent.user9999@example.com" } | ConvertTo-Json
$res1 = Invoke-WebRequest -Uri "http://localhost:8081/api/auth/forgot-password" -Method POST -Body $unknownReq -ContentType "application/json" -SkipHttpErrorCheck
Write-Host "1.1 Forgot password (unknown email) -> Status: $($res1.StatusCode)"
Write-Host "    Body: $($res1.Content)"

# 1.2 Known student email
$studentReq = @{ email = "student.test@codeassess.edu" } | ConvertTo-Json
$res2 = Invoke-WebRequest -Uri "http://localhost:8081/api/auth/forgot-password" -Method POST -Body $studentReq -ContentType "application/json" -SkipHttpErrorCheck
Write-Host "1.2 Forgot password (known email) -> Status: $($res2.StatusCode)"
Write-Host "    Body: $($res2.Content)"

# 1.3 Query token from database
$tokenOutput = docker exec codeassess-mysql mysql -uroot -p123456 plagiarism_system -N -s -e "SELECT token FROM password_reset_tokens ORDER BY created_at DESC LIMIT 1;"
$token = $tokenOutput.Trim()
Write-Host "1.3 Retrieved Token from DB: $token"

# 1.4 Invalid token
$invalidReq = @{ token = "invalid-token-12345"; password = "NewPassword1234!" } | ConvertTo-Json
$res3 = Invoke-WebRequest -Uri "http://localhost:8081/api/auth/reset-password" -Method POST -Body $invalidReq -ContentType "application/json" -SkipHttpErrorCheck
Write-Host "1.4 Reset password (invalid token) -> Status: $($res3.StatusCode)"
Write-Host "    Body: $($res3.Content)"

# 1.5 Short password (< 8 chars)
$shortReq = @{ token = $token; password = "short" } | ConvertTo-Json
$res4 = Invoke-WebRequest -Uri "http://localhost:8081/api/auth/reset-password" -Method POST -Body $shortReq -ContentType "application/json" -SkipHttpErrorCheck
Write-Host "1.5 Reset password (short password) -> Status: $($res4.StatusCode)"

# 1.6 Successful reset
$validReq = @{ token = $token; password = "PasswordReset999!" } | ConvertTo-Json
$res5 = Invoke-WebRequest -Uri "http://localhost:8081/api/auth/reset-password" -Method POST -Body $validReq -ContentType "application/json" -SkipHttpErrorCheck
Write-Host "1.6 Reset password (valid) -> Status: $($res5.StatusCode)"
Write-Host "    Body: $($res5.Content)"

# 1.7 Token reuse prevention
$reuseReq = @{ token = $token; password = "PasswordReset999!" } | ConvertTo-Json
$res6 = Invoke-WebRequest -Uri "http://localhost:8081/api/auth/reset-password" -Method POST -Body $reuseReq -ContentType "application/json" -SkipHttpErrorCheck
Write-Host "1.7 Reset password (token reuse) -> Status: $($res6.StatusCode)"
Write-Host "    Body: $($res6.Content)"

# 1.8 Login with new password
$loginSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginNewReq = @{ email = "student.test@codeassess.edu"; password = "PasswordReset999!" } | ConvertTo-Json
$res7 = Invoke-WebRequest -Uri "http://localhost:8081/api/auth/login" -Method POST -Body $loginNewReq -ContentType "application/json" -WebSession $loginSession -SkipHttpErrorCheck
Write-Host "1.8 Login with new password -> Status: $($res7.StatusCode)"

# Reset password back to Password1234! for consistency
$resetBackTokenReq = @{ email = "student.test@codeassess.edu" } | ConvertTo-Json
Invoke-WebRequest -Uri "http://localhost:8081/api/auth/forgot-password" -Method POST -Body $resetBackTokenReq -ContentType "application/json" -SkipHttpErrorCheck | Out-Null
$tokenBack = (docker exec codeassess-mysql mysql -uroot -p123456 plagiarism_system -N -s -e "SELECT token FROM password_reset_tokens ORDER BY created_at DESC LIMIT 1;").Trim()
$resetBackReq = @{ token = $tokenBack; password = "Password1234!" } | ConvertTo-Json
Invoke-WebRequest -Uri "http://localhost:8081/api/auth/reset-password" -Method POST -Body $resetBackReq -ContentType "application/json" -SkipHttpErrorCheck | Out-Null
Write-Host "1.9 Restored student password to default Password1234!" -ForegroundColor Green

Write-Host "`n=== TEST 2: TEACHER CLASS MANAGEMENT & ROSTER (BUG-CLS-01) ===" -ForegroundColor Cyan
$teacherSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$teacherLoginReq = @{ email = "teacher.test@codeassess.edu"; password = "Password1234!" } | ConvertTo-Json
$tLogin = Invoke-WebRequest -Uri "http://localhost:8081/api/auth/login" -Method POST -Body $teacherLoginReq -ContentType "application/json" -WebSession $teacherSession
Write-Host "2.1 Teacher Login -> Status: $($tLogin.StatusCode)"

# 2.2 Get teacher classes
$tClasses = Invoke-WebRequest -Uri "http://localhost:8081/api/teacher/classes" -Method GET -WebSession $teacherSession
$classesList = $tClasses.Content | ConvertFrom-Json
Write-Host "2.2 Get Teacher Classes -> Status: $($tClasses.StatusCode), Count: $($classesList.Count)"

# 2.3 Create a new test class
$randCode = "CLS" + (Get-Random -Minimum 1000 -Maximum 9999)
$createClassReq = @{ name = "Lập trình Kiểm thử Nâng cao"; code = $randCode; semester = "Fall 2026" } | ConvertTo-Json
$createdClassRes = Invoke-WebRequest -Uri "http://localhost:8081/api/teacher/classes" -Method POST -Body $createClassReq -ContentType "application/json" -WebSession $teacherSession
$createdClass = $createdClassRes.Content | ConvertFrom-Json
$targetClassId = $createdClass.id
Write-Host "2.3 Created Class -> ID: $targetClassId, Code: $($createdClass.code)"

# 2.4 Get class students (expect 0)
$tStudents1 = Invoke-WebRequest -Uri "http://localhost:8081/api/teacher/classes/$targetClassId/students" -Method GET -WebSession $teacherSession
Write-Host "2.4 Initial Roster Count: $((($tStudents1.Content | ConvertFrom-Json)).Count)"

# 2.5 Enroll student.test@codeassess.edu
$enrollReq = @{ email = "student.test@codeassess.edu" } | ConvertTo-Json
$enrollRes = Invoke-WebRequest -Uri "http://localhost:8081/api/teacher/classes/$targetClassId/students" -Method POST -Body $enrollReq -ContentType "application/json" -WebSession $teacherSession -SkipHttpErrorCheck
Write-Host "2.5 Enroll Student -> Status: $($enrollRes.StatusCode)"
Write-Host "    Response: $($enrollRes.Content)"

# 2.6 Duplicate enrollment attempt
$enrollDupRes = Invoke-WebRequest -Uri "http://localhost:8081/api/teacher/classes/$targetClassId/students" -Method POST -Body $enrollReq -ContentType "application/json" -WebSession $teacherSession -SkipHttpErrorCheck
Write-Host "2.6 Duplicate Enrollment -> Status: $($enrollDupRes.StatusCode)"
Write-Host "    Body: $($enrollDupRes.Content)"

# 2.7 Verify student count is 1
$tStudents2 = Invoke-WebRequest -Uri "http://localhost:8081/api/teacher/classes/$targetClassId/students" -Method GET -WebSession $teacherSession
$studentList = $tStudents2.Content | ConvertFrom-Json
Write-Host "2.7 Updated Roster Count: $($studentList.Count)"
$studentObjId = $studentList[0].studentId

# 2.8 Student trying to delete roster (unauthorized boundary)
$sSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$sLoginReq = @{ email = "student.test@codeassess.edu"; password = "Password1234!" } | ConvertTo-Json
Invoke-WebRequest -Uri "http://localhost:8081/api/auth/login" -Method POST -Body $sLoginReq -ContentType "application/json" -WebSession $sSession | Out-Null
$unauthDel = Invoke-WebRequest -Uri "http://localhost:8081/api/teacher/classes/$targetClassId/students/$studentObjId" -Method DELETE -WebSession $sSession -SkipHttpErrorCheck
Write-Host "2.8 Student Unauthorized Delete Attempt -> Status: $($unauthDel.StatusCode) (Expect 403)"

# 2.9 Teacher unenroll student
$delRes = Invoke-WebRequest -Uri "http://localhost:8081/api/teacher/classes/$targetClassId/students/$studentObjId" -Method DELETE -WebSession $teacherSession -SkipHttpErrorCheck
Write-Host "2.9 Teacher Unenroll Student -> Status: $($delRes.StatusCode) (Expect 204)"

Write-Host "`n=== TEST 3: AI SERVICE SAFE MOCK & ADVISORY (BUG-AI-01) ===" -ForegroundColor Cyan
# 3.1 Plagiarism AI explanation
$aiRes1 = Invoke-WebRequest -Uri "http://localhost:8081/api/teacher/plagiarism/1/ai/similarity-explanation" -Method POST -WebSession $teacherSession -SkipHttpErrorCheck
Write-Host "3.1 Teacher Plagiarism AI Explanation -> Status: $($aiRes1.StatusCode)"
if ($aiRes1.StatusCode -eq 200) {
    $aiObj1 = $aiRes1.Content | ConvertFrom-Json
    Write-Host "    AI Text Preview: $($aiObj1.analysis.Substring(0, [Math]::Min(120, $aiObj1.analysis.Length)))..." -ForegroundColor Yellow
}
