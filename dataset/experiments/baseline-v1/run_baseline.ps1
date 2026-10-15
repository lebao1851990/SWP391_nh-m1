$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = (Get-Item "$scriptDir\..\..\..").FullName
$backendDir = "$rootDir\Back end"
$cpFile = "$backendDir\classpath.txt"
$classesDir = "$backendDir\target\classes"

$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

if (-not (Test-Path $cpFile)) {
    Write-Host "[INFO] Generating Maven classpath..."
    Push-Location $backendDir
    & .\mvnw.cmd dependency:build-classpath -Dmdep.outputFile=classpath.txt
    Pop-Location
}

$mavenCp = Get-Content $cpFile
$fullCp = "$classesDir;$mavenCp;$scriptDir"

Write-Host "[1/2] Compiling BaselineExperimentRunner..."
javac -cp $fullCp -d $scriptDir "$scriptDir\BaselineExperimentRunner.java"

Write-Host "[2/2] Running BaselineExperimentRunner..."
Push-Location $rootDir
java -cp $fullCp com.codeassess.experiment.BaselineExperimentRunner
Pop-Location

Write-Host "[SUCCESS] Baseline experiment execution finished."
