@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1
set PATH=%JAVA_HOME%\bin;%PATH%
for /r "%USERPROFILE%\.m2\repository\com\mysql\mysql-connector-j" %%f in (*.jar) do (
    set MYSQL_JAR=%%f
    goto :found
)
:found
echo Using jar: %MYSQL_JAR%
javac -cp "%MYSQL_JAR%" src\test\java\com\codeassess\DescribeTable.java
java -cp "src\test\java;%MYSQL_JAR%" com.codeassess.DescribeTable
