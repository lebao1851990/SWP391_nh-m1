@echo off
setlocal
cd /d "%~dp0"

if not defined JAVA_HOME (
    if exist "C:\Program Files\Java\jdk-21.0.12.1" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1"
    ) else if exist "C:\Program Files\Java\jdk-21.0.12" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.12"
    )
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] Java executable not found at: "%JAVA_HOME%\bin\java.exe"
    echo Please verify that JDK 21 is installed.
    pause
    exit /b 1
)

echo ========================================================
echo JAVA_HOME: %JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version
echo ========================================================

REM --------------------------------------------------------
REM Load environment variables from .env file if present
REM --------------------------------------------------------
if exist "%~dp0.env" (
    echo [INFO] Loading configuration from %~dp0.env...
    for /f "usebackq eol=# tokens=1* delims==" %%A in ("%~dp0.env") do (
        if not "%%A"=="" (
            set "%%A=%%B"
        )
    )
)

if "%DB_HOST%"=="" set "DB_HOST=127.0.0.1"
if "%DB_PORT%"=="" set "DB_PORT=3306"
if "%DB_NAME%"=="" set "DB_NAME=codeassess"
if "%PORT%"=="" set "PORT=8081"

echo ========================================================
echo [CONFIG] Target DB : %DB_HOST%:%DB_PORT%/%DB_NAME%
echo [CONFIG] SSL / TLS : %DB_USE_SSL% (Mode: %DB_SSL_MODE%)
echo [CONFIG] Server Port: %PORT%
echo ========================================================

call mvnw.cmd spring-boot:run
pause
endlocal
