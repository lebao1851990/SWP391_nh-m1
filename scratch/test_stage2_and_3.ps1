$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-WebRequest -Uri 'http://localhost:8081/api/auth/login' -Method POST -Body '{"email":"teacher.test@codeassess.edu","password":"Password1234!"}' -ContentType 'application/json' -WebSession $s -UseBasicParsing | Out-Null

$classId = "04e96ced-0f45-4707-a65b-7457502481f3"

Write-Host "1. GET /api/teacher/classes/$classId/students"
$res1 = Invoke-WebRequest -Uri "http://localhost:8081/api/teacher/classes/$classId/students" -Method GET -WebSession $s -UseBasicParsing
Write-Host "Status: $($res1.StatusCode), Content: $($res1.Content)"

Write-Host "`n2. POST /api/teacher/classes/$classId/students (enroll student.test@codeassess.edu)"
$enrollRes = Invoke-WebRequest -Uri "http://localhost:8081/api/teacher/classes/$classId/students" -Method POST -Body '{"email":"student.test@codeassess.edu"}' -ContentType 'application/json' -WebSession $s -UseBasicParsing
Write-Host "Status: $($enrollRes.StatusCode), Content: $($enrollRes.Content)"

Write-Host "`n3. Duplicate enroll attempt (expect 409 Conflict)"
try {
    Invoke-WebRequest -Uri "http://localhost:8081/api/teacher/classes/$classId/students" -Method POST -Body '{"email":"student.test@codeassess.edu"}' -ContentType 'application/json' -WebSession $s -UseBasicParsing
    Write-Host "Unexpected success"
} catch {
    Write-Host "Duplicate successfully rejected: Status $([int]$_.Exception.Response.StatusCode)"
}

Write-Host "`n4. GET /api/teacher/classes/$classId/students after enrollment"
$res2 = Invoke-WebRequest -Uri "http://localhost:8081/api/teacher/classes/$classId/students" -Method GET -WebSession $s -UseBasicParsing
$students = $res2.Content | ConvertFrom-Json
Write-Host "Roster Count: $($students.Count)"
$studentId = $students[0].studentId
Write-Host "Enrolled Student ID: $studentId, Email: $($students[0].email), Name: $($students[0].fullName)"

Write-Host "`n5. Student tries to unenroll (Boundary check: Student role forbidden 403)"
$sStudent = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-WebRequest -Uri 'http://localhost:8081/api/auth/login' -Method POST -Body '{"email":"student.test@codeassess.edu","password":"Password1234!"}' -ContentType 'application/json' -WebSession $sStudent -UseBasicParsing | Out-Null
try {
    Invoke-WebRequest -Uri "http://localhost:8081/api/teacher/classes/$classId/students/$studentId" -Method DELETE -WebSession $sStudent -UseBasicParsing
    Write-Host "Unexpected success"
} catch {
    Write-Host "Student DELETE correctly blocked with Status: $([int]$_.Exception.Response.StatusCode)"
}

Write-Host "`n6. Teacher unenrolls student (expect 204 No Content)"
$delRes = Invoke-WebRequest -Uri "http://localhost:8081/api/teacher/classes/$classId/students/$studentId" -Method DELETE -WebSession $s -UseBasicParsing
Write-Host "Teacher DELETE Status: $($delRes.StatusCode)"

Write-Host "`n7. GET /api/teacher/classes/$classId/students after deletion"
$res3 = Invoke-WebRequest -Uri "http://localhost:8081/api/teacher/classes/$classId/students" -Method GET -WebSession $s -UseBasicParsing
Write-Host "Final Roster Count: $((($res3.Content | ConvertFrom-Json)).Count)"
