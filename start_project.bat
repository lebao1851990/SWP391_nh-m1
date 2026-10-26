@echo off
chcp 65001 >nul
title CodeAssess - Khoi dong he thong
echo ========================================================
echo   DANG KHOI DONG HE THONG CODEASSESS (BACKEND + FRONTEND)
echo ========================================================
echo.

REM 1. Kiem tra MySQL
echo [1/3] Kiem tra ket noi MySQL port 3306...
powershell -Command "if ((Test-NetConnection -ComputerName 127.0.0.1 -Port 3306 -InformationLevel Quiet)) { Write-Host '   -> MySQL dang hoat dong!' -ForegroundColor Green } else { Write-Host '   -> CANH BAO: MySQL chua bat o port 3306! Vui long bat XAMPP/MySQL service truoc.' -ForegroundColor Yellow }"

REM 2. Bat Backend (Spring Boot port 8088)
echo [2/3] Dang khoi dong Spring Boot Backend (:8088)...
start "CodeAssess Backend [Port 8088]" cmd /k "cd /d "%~dp0Back end" && call run_backend.bat"

REM 3. Bat Frontend (Next.js port 3000)
echo [3/3] Dang khoi dong Next.js Frontend (:3000)...
start "CodeAssess Frontend [Port 3000]" cmd /k "cd /d "%~dp0Front end" && npm run dev"

echo.
echo ========================================================
echo   DA KHOI DONG XONG 2 CUA SO TERMINAL RIENG BIET:
echo   - Backend:  http://localhost:8088
echo   - Frontend: http://localhost:3000
echo.
echo   Ban co the dong cua so nay, 2 cua so kia se van chay.
echo ========================================================
echo.
pause
