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

for /r "%USERPROFILE%\.m2\repository\com\mysql\mysql-connector-j" %%f in (*.jar) do (
    set MYSQL_JAR=%%f
    goto :found
)
:found
echo Using jar: %MYSQL_JAR%
javac -cp "%MYSQL_JAR%" src\test\java\com\codeassess\DescribeTable.java
java -cp "src\test\java;%MYSQL_JAR%" com.codeassess.DescribeTable
endlocal
