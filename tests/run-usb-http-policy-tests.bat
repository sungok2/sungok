@echo off
setlocal EnableExtensions
cd /d "%~dp0.."
set "OUT=%TEMP%\GalaxyUniversalIO-HTTPPOLICY-%RANDOM%"
mkdir "%OUT%" || exit /b 1
javac --release 17 -encoding UTF-8 -d "%OUT%" ^
  app\src\main\java\com\galaxyuniversalio\UsbHttpRequestPolicy.java ^
  tests\UsbHttpPolicyTests.java
if errorlevel 1 goto :failed
java -cp "%OUT%" com.galaxyuniversalio.UsbHttpPolicyTests
if errorlevel 1 goto :failed
rmdir /s /q "%OUT%"
exit /b 0
:failed
rmdir /s /q "%OUT%"
exit /b 1
