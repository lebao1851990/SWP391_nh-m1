@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

REM ========================================================
REM 1. ENFORCE JDK 21
REM ========================================================
set "TARGET_JDK="
if exist "C:\Program Files\Java\jdk-21.0.12\bin\java.exe" (
    set "TARGET_JDK=C:\Program Files\Java\jdk-21.0.12"
) else if exist "C:\Program Files\Java\jdk-21.0.12.1\bin\java.exe" (
    set "TARGET_JDK=C:\Program Files\Java\jdk-21.0.12.1"
) else if exist "C:\Program Files\Java\jdk-21\bin\java.exe" (
    set "TARGET_JDK=C:\Program Files\Java\jdk-21"
)

if "%TARGET_JDK%"=="" (
    echo [ERROR] JDK 21 was not found in "C:\Program Files\Java\jdk-21*".
    echo Please verify that JDK 21 is installed.
    pause
    exit /b 1
)

set "JAVA_HOME=%TARGET_JDK%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo [ERROR] Javac compiler not found at: "%JAVA_HOME%\bin\javac.exe"
    pause
    exit /b 1
)

echo ========================================================
echo JAVA_HOME: %JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version
echo ========================================================

REM ========================================================
REM 2. LOAD ENVIRONMENT VARIABLES (.env.local or .env)
REM ========================================================
set "ENV_FILE="
if exist "%~dp0.env.local" (
    set "ENV_FILE=%~dp0.env.local"
) else if exist "%~dp0.env" (
    set "ENV_FILE=%~dp0.env"
)

if not "%ENV_FILE%"=="" (
    echo [INFO] Loading configuration from %ENV_FILE%...
    for /f "usebackq eol=# tokens=1* delims==" %%A in ("%ENV_FILE%") do (
        set "KEY=%%A"
        set "VAL=%%B"
        if not "!KEY!"=="" (
            REM Trim spaces
            for /f "tokens=* delims= " %%k in ("!KEY!") do set "KEY=%%k"
            set "!KEY!=!VAL!"
        )
    )
)

REM Support SERVER_PORT mapping to PORT
if not "%SERVER_PORT%"=="" if "%PORT%"=="" set "PORT=%SERVER_PORT%"

REM Apply sensible local defaults if not defined
if "%DB_HOST%"=="" set "DB_HOST=127.0.0.1"
if "%DB_PORT%"=="" set "DB_PORT=3306"
if "%DB_NAME%"=="" set "DB_NAME=plagiarism_system"
if "%PORT%"=="" set "PORT=8081"

REM Handle local SSL configuration
if /i "%DB_USE_SSL%"=="false" (
    if "%DB_SSL_MODE%"=="" set "DB_SSL_MODE=DISABLED"
) else if "%DB_USE_SSL%"=="" (
    if "%DB_HOST%"=="127.0.0.1" (
        set "DB_USE_SSL=false"
        set "DB_SSL_MODE=DISABLED"
    ) else if "%DB_HOST%"=="localhost" (
        set "DB_USE_SSL=false"
        set "DB_SSL_MODE=DISABLED"
    )
)

REM Ensure local schema checksum compatibility
if "%SPRING_FLYWAY_VALIDATE_ON_MIGRATE%"=="" set "SPRING_FLYWAY_VALIDATE_ON_MIGRATE=false"

echo ========================================================
echo [CONFIG] Target DB : %DB_HOST%:%DB_PORT%/%DB_NAME%
echo [CONFIG] DB User   : %DB_USERNAME%
echo [CONFIG] SSL / TLS : %DB_USE_SSL% (Mode: %DB_SSL_MODE%)
echo [CONFIG] Server Port: %PORT%
echo ========================================================

call mvnw.cmd spring-boot:run
pause
endlocal