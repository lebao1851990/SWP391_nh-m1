$s = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-WebRequest -Uri 'http://localhost:8081/api/auth/login' -Method POST -Body '{"email":"student.test@codeassess.edu","password":"Password1234!"}' -ContentType 'application/json' -WebSession $s -UseBasicParsing | Out-Null

$aiBody = '{"mode":"CODE_EXPLANATION","sourceCode":"public class Main { public static void main(String[] args) { System.out.println(123); } }"}'
$res = Invoke-WebRequest -Uri 'http://localhost:8081/api/student/sandbox/ai/explain' -Method POST -Body $aiBody -ContentType 'application/json' -WebSession $s -UseBasicParsing
Write-Host "Status: $($res.StatusCode)"
Write-Host "Content: $($res.Content)"
