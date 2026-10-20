@echo off
setlocal
cd /d "%~dp0"

set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.12"
set "PATH=%JAVA_HOME%\bin;%PATH%"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] Java executable not found at: "%JAVA_HOME%\bin\java.exe"
    echo Please verify that JDK 21 is installed at "%JAVA_HOME%".
    exit /b 1
)

echo ========================================================
echo JAVA_HOME: %JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version
"%JAVA_HOME%\bin\javac.exe" -version
echo ========================================================

if "%DB_PORT%"=="" set "DB_PORT=3307"
call mvnw.cmd test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.codeassess.DbPreflightExact
endlocal
