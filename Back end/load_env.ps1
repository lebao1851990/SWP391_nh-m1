# load_env.ps1 - Exports Back end/.env variables into the current PowerShell process
$envPath = Join-Path $PSScriptRoot ".env"
if (Test-Path $envPath) {
    Write-Host "[INFO] Loading environment variables from $envPath..." -ForegroundColor Cyan
    Get-Content $envPath | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#")) {
            $parts = $line.Split('=', 2)
            if ($parts.Length -eq 2) {
                $key = $parts[0].Trim()
                $val = $parts[1].Trim()
                [System.Environment]::SetEnvironmentVariable($key, $val, "Process")
            }
        }
    }
    Write-Host "[CONFIG] Target DB : $($env:DB_HOST):$($env:DB_PORT)/$($env:DB_NAME)" -ForegroundColor Green
    Write-Host "[CONFIG] SSL / TLS : $($env:DB_USE_SSL) (Mode: $($env:DB_SSL_MODE))" -ForegroundColor Green
    Write-Host "[CONFIG] Server Port: $($env:PORT)" -ForegroundColor Green
} else {
    Write-Warning "File .env not found at $envPath"
}
