@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1
set PATH=%JAVA_HOME%\bin;%PATH%
mvnw.cmd test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.codeassess.DbPreflightExact
