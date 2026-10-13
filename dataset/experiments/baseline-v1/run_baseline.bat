@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run_baseline.ps1"
endlocal
