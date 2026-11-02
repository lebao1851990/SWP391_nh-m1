$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-WebRequest -Uri 'http://localhost:8081/api/auth/login' -Method POST -Body '{"email":"teacher.test@codeassess.edu","password":"Password1234!"}' -ContentType 'application/json' -WebSession $s -UseBasicParsing | Out-Null

try {
    $res = Invoke-WebRequest -Uri 'http://localhost:8081/api/teacher/classes' -Method POST -Body '{"name":"Lap trinh Kiem thu","code":"TEST991","semester":"Fall 2026"}' -ContentType 'application/json' -WebSession $s -UseBasicParsing
    Write-Host "Success: $($res.Content)"
} catch {
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    Write-Host "Error Body: $($reader.ReadToEnd())"
}
