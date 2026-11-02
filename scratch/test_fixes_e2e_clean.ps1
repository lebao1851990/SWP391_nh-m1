function Request-Api {
    param(
        [string]$Uri,
        [string]$Method = "GET",
        [string]$Body = $null,
        [object]$Session = $null
    )
    try {
        $params = @{
            Uri = $Uri
            Method = $Method
            ContentType = "application/json"
            UseBasicParsing = $true
        }
        if ($Body) { $params["Body"] = $Body }
        if ($Session) { $params["WebSession"] = $Session }
        $res = Invoke-WebRequest @params
        return [PSCustomObject]@{
            StatusCode = [int]$res.StatusCode
            Content = $res.Content
        }
    } catch [System.Net.WebException] {
        $resp = $_.Exception.Response
        $status = if ($resp) { [int]$resp.StatusCode } else { 0 }
        $content = ""
        if ($resp) {
            $stream = $resp.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $content = $reader.ReadToEnd()
        }
        return [PSCustomObject]@{
            StatusCode = $status
            Content = $content
        }
    }
}

Write-Host "`n=== TEST 1: FORGOT PASSWORD & RESET PASSWORD (BUG-AUTH-01) ===" -ForegroundColor Cyan

# 1.1 Unknown email
$r1 = Request-Api -Uri "http://localhost:8081/api/auth/forgot-password" -Method POST -Body '{"email":"nonexistent.user9999@example.com"}'
Write-Host "1.1 Forgot password (unknown email) -> Status: $($r1.StatusCode)"
Write-Host "    Body: $($r1.Content)"

# 1.2 Known student email
$r2 = Request-Api -Uri "http://localhost:8081/api/auth/forgot-password" -Method POST -Body '{"email":"student.test@codeassess.edu"}'
Write-Host "1.2 Forgot password (known email) -> Status: $($r2.StatusCode)"
Write-Host "    Body: $($r2.Content)"

# 1.3 Query token from database
$tokenLines = docker exec codeassess-mysql mysql -uroot -p123456 plagiarism_system -N -s -e "SELECT token FROM password_reset_tokens ORDER BY created_at DESC LIMIT 1;" 2>$null
$token = ($tokenLines | Out-String).Trim()
Write-Host "1.3 Retrieved Token from DB: $token"

# 1.4 Invalid token
$r3 = Request-Api -Uri "http://localhost:8081/api/auth/reset-password" -Method POST -Body '{"token":"invalid-token-12345","password":"NewPassword1234!"}'
Write-Host "1.4 Reset password (invalid token) -> Status: $($r3.StatusCode)"
Write-Host "    Body: $($r3.Content)"

# 1.5 Short password (< 8 chars)
$r4 = Request-Api -Uri "http://localhost:8081/api/auth/reset-password" -Method POST -Body ('{"token":"' + $token + '","password":"short"}')
Write-Host "1.5 Reset password (short password) -> Status: $($r4.StatusCode)"
Write-Host "    Body: $($r4.Content)"

# 1.6 Successful reset
$r5 = Request-Api -Uri "http://localhost:8081/api/auth/reset-password" -Method POST -Body ('{"token":"' + $token + '","password":"PasswordReset999!"}')
Write-Host "1.6 Reset password (valid) -> Status: $($r5.StatusCode)"
Write-Host "    Body: $($r5.Content)"

# 1.7 Token reuse prevention
$r6 = Request-Api -Uri "http://localhost:8081/api/auth/reset-password" -Method POST -Body ('{"token":"' + $token + '","password":"PasswordReset999!"}')
Write-Host "1.7 Reset password (token reuse) -> Status: $($r6.StatusCode)"
Write-Host "    Body: $($r6.Content)"

# 1.8 Login with new password
$sLoginSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$r7 = Request-Api -Uri "http://localhost:8081/api/auth/login" -Method POST -Body '{"email":"student.test@codeassess.edu","password":"PasswordReset999!"}' -Session $sLoginSession
Write-Host "1.8 Login with new password -> Status: $($r7.StatusCode)"

# 1.9 Restore password to Password1234!
Request-Api -Uri "http://localhost:8081/api/auth/forgot-password" -Method POST -Body '{"email":"student.test@codeassess.edu"}' | Out-Null
$tokenBack = (docker exec codeassess-mysql mysql -uroot -p123456 plagiarism_system -N -s -e "SELECT token FROM password_reset_tokens ORDER BY created_at DESC LIMIT 1;" 2>$null | Out-String).Trim()
Request-Api -Uri "http://localhost:8081/api/auth/reset-password" -Method POST -Body ('{"token":"' + $tokenBack + '","password":"Password1234!"}') | Out-Null
Write-Host "1.9 Restored student password to standard Password1234!" -ForegroundColor Green

Write-Host "`n=== TEST 2: TEACHER CLASS MANAGEMENT & ROSTER (BUG-CLS-01) ===" -ForegroundColor Cyan
$teacherSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$tLogin = Request-Api -Uri "http://localhost:8081/api/auth/login" -Method POST -Body '{"email":"teacher.test@codeassess.edu","password":"Password1234!"}' -Session $teacherSession
Write-Host "2.1 Teacher Login -> Status: $($tLogin.StatusCode)"

# 2.2 Get teacher classes
$tClasses = Request-Api -Uri "http://localhost:8081/api/teacher/classes" -Method GET -Session $teacherSession
$classesList = $tClasses.Content | ConvertFrom-Json
Write-Host "2.2 Get Teacher Classes -> Status: $($tClasses.StatusCode), Count: $($classesList.Count)"

# 2.3 Create a new test class
$randCode = "CLS" + (Get-Random -Minimum 1000 -Maximum 9999)
$createBody = '{"name":"Lập trình Kiểm thử Nâng cao","code":"' + $randCode + '","semester":"Fall 2026"}'
$createdClassRes = Request-Api -Uri "http://localhost:8081/api/teacher/classes" -Method POST -Body $createBody -Session $teacherSession
$createdClass = $createdClassRes.Content | ConvertFrom-Json
$targetClassId = $createdClass.id
Write-Host "2.3 Created Class -> Status: $($createdClassRes.StatusCode), ID: $targetClassId, Code: $($createdClass.code)"

# 2.4 Get class students (expect 0)
$tStudents1 = Request-Api -Uri "http://localhost:8081/api/teacher/classes/$targetClassId/students" -Method GET -Session $teacherSession
Write-Host "2.4 Initial Roster Count: $((($tStudents1.Content | ConvertFrom-Json)).Count)"

# 2.5 Enroll student.test@codeassess.edu
$enrollRes = Request-Api -Uri "http://localhost:8081/api/teacher/classes/$targetClassId/students" -Method POST -Body '{"email":"student.test@codeassess.edu"}' -Session $teacherSession
Write-Host "2.5 Enroll Student -> Status: $($enrollRes.StatusCode)"
Write-Host "    Response: $($enrollRes.Content)"

# 2.6 Duplicate enrollment attempt
$enrollDupRes = Request-Api -Uri "http://localhost:8081/api/teacher/classes/$targetClassId/students" -Method POST -Body '{"email":"student.test@codeassess.edu"}' -Session $teacherSession
Write-Host "2.6 Duplicate Enrollment -> Status: $($enrollDupRes.StatusCode)"
Write-Host "    Body: $($enrollDupRes.Content)"

# 2.7 Verify student count is 1
$tStudents2 = Request-Api -Uri "http://localhost:8081/api/teacher/classes/$targetClassId/students" -Method GET -Session $teacherSession
$studentList = $tStudents2.Content | ConvertFrom-Json
Write-Host "2.7 Updated Roster Count: $($studentList.Count)"
$studentObjId = $studentList[0].studentId

# 2.8 Student trying to delete roster (unauthorized boundary)
$sSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Request-Api -Uri "http://localhost:8081/api/auth/login" -Method POST -Body '{"email":"student.test@codeassess.edu","password":"Password1234!"}' -Session $sSession | Out-Null
$unauthDel = Request-Api -Uri "http://localhost:8081/api/teacher/classes/$targetClassId/students/$studentObjId" -Method DELETE -Session $sSession
Write-Host "2.8 Student Unauthorized Delete Attempt -> Status: $($unauthDel.StatusCode) (Expect 403)"

# 2.9 Teacher unenroll student
$delRes = Request-Api -Uri "http://localhost:8081/api/teacher/classes/$targetClassId/students/$studentObjId" -Method DELETE -Session $teacherSession
Write-Host "2.9 Teacher Unenroll Student -> Status: $($delRes.StatusCode) (Expect 204)"

Write-Host "`n=== TEST 3: AI SERVICE SAFE MOCK & ADVISORY (BUG-AI-01) ===" -ForegroundColor Cyan
$aiRes1 = Request-Api -Uri "http://localhost:8081/api/teacher/plagiarism/1/ai/similarity-explanation" -Method POST -Session $teacherSession
Write-Host "3.1 Teacher Plagiarism AI Explanation -> Status: $($aiRes1.StatusCode)"
if ($aiRes1.StatusCode -eq 200) {
    $aiObj1 = $aiRes1.Content | ConvertFrom-Json
    Write-Host "    AI Output Content:`n$($aiObj1.analysis)" -ForegroundColor Yellow
}
