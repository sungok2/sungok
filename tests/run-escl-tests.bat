@echo off
setlocal EnableExtensions
cd /d "%~dp0.."
set "OUT=%TEMP%\GalaxyUniversalIO-ESCL-%RANDOM%"
mkdir "%OUT%" || exit /b 1
javac --release 17 -encoding UTF-8 -d "%OUT%" ^
  tests\stubs\android\hardware\usb\*.java ^
  app\src\main\java\com\galaxyuniversalio\EsclProtocol.java ^
  app\src\main\java\com\galaxyuniversalio\EsclScanClient.java ^
  app\src\main\java\com\galaxyuniversalio\LanEsclTransport.java ^
  app\src\main\java\com\galaxyuniversalio\UsbEsclHttpTransport.java ^
  app\src\main\java\com\galaxyuniversalio\UsbHttpResponseHeaders.java ^
  app\src\main\java\com\galaxyuniversalio\UsbHttpRequestPolicy.java ^
  tests\EsclHostTests.java
if errorlevel 1 goto :failed
java -cp "%OUT%" com.galaxyuniversalio.EsclHostTests
if errorlevel 1 goto :failed
rmdir /s /q "%OUT%"
exit /b 0
:failed
rmdir /s /q "%OUT%"
exit /b 1
