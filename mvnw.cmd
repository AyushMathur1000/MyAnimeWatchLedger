@echo off
setlocal EnableExtensions
set "MAVEN_VERSION=3.10.0"
set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%"
set "MAVEN_EXE=%MAVEN_HOME%\bin\mvn.cmd"
if not exist "%MAVEN_EXE%" (
  echo Downloading Apache Maven %MAVEN_VERSION%...
  if not exist "%MAVEN_HOME%" mkdir "%MAVEN_HOME%"
  set "MAVEN_ZIP=%TEMP%\apache-maven-%MAVEN_VERSION%.zip"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Invoke-WebRequest 'https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile $env:MAVEN_ZIP; Expand-Archive -LiteralPath $env:MAVEN_ZIP -DestinationPath $env:TEMP -Force; Move-Item -LiteralPath (Join-Path $env:TEMP 'apache-maven-%MAVEN_VERSION%') -Destination $env:MAVEN_HOME -Force; Remove-Item $env:MAVEN_ZIP -Force"
  if errorlevel 1 exit /b 1
)
call "%MAVEN_EXE%" %*
exit /b %ERRORLEVEL%
