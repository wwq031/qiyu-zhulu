@echo off
set "MVNW_VER=3.2.0"
set "MVN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.7"
set "MVN_CMD=%MVN_HOME%\bin\mvn"

if exist "%MVN_CMD%" goto :run

echo Downloading Maven Wrapper...
mkdir "%MVN_HOME%\..\..\.." 2>nul

java -cp ".mvn\wrapper\maven-wrapper.jar;%USERPROFILE%\.m2\wrapper\maven-wrapper.jar" ^
  org.apache.maven.wrapper.MavenWrapperMain ^
  -Dmaven.repo.local="%USERPROFILE%\.m2\repository" ^
  %* 2>nul

if %ERRORLEVEL% neq 0 (
    echo Maven Wrapper failed. Trying direct download...
    powershell -Command "Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.7/apache-maven-3.9.7-bin.zip' -OutFile '%TEMP%\maven.zip'" 2>nul
    if exist "%TEMP%\maven.zip" (
        powershell -Command "Expand-Archive -Path '%TEMP%\maven.zip' -DestinationPath '%MVN_HOME%\..' -Force" 2>nul
        del "%TEMP%\maven.zip" 2>nul
    )
)

if exist "%MVN_CMD%" (
    goto :run
) else (
    echo ERROR: Maven installation failed.
    echo Please install Maven manually: https://maven.apache.org/download.cgi
    echo Or try: choco install maven
    pause
    exit /b 1
)

:run
"%MVN_CMD%" %*
