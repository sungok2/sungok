@echo off
setlocal EnableExtensions
cd /d "%~dp0.."
set "OUT=%TEMP%\GalaxyUniversalIO-ImagingTests-%RANDOM%"
mkdir "%OUT%" || exit /b 1
javac --release 17 -encoding UTF-8 -d "%OUT%" ^
  tests\stubs\android\hardware\usb\*.java ^
  app\src\main\java\com\galaxyuniversalio\PtpProtocol.java ^
  app\src\main\java\com\galaxyuniversalio\PtpUsbTransport.java ^
  app\src\main\java\com\galaxyuniversalio\PdfSpoolPreflight.java ^
  tests\ImagingAndPdfTests.java tests\PtpTransportTests.java
if errorlevel 1 goto :failed
java -cp "%OUT%" com.galaxyuniversalio.ImagingAndPdfTests
if errorlevel 1 goto :failed
java -cp "%OUT%" com.galaxyuniversalio.PtpTransportTests
if errorlevel 1 goto :failed
rmdir /s /q "%OUT%"
exit /b 0
:failed
rmdir /s /q "%OUT%"
exit /b 1
