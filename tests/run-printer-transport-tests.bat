@echo off
setlocal
cd /d "%~dp0.."
set "OUT=%TEMP%\GalaxyUniversalIO-PrinterTests-%RANDOM%"
mkdir "%OUT%" || exit /b 1
javac --release 17 -encoding UTF-8 -d "%OUT%" ^
  tests\stubs\android\hardware\usb\*.java ^
  app\src\main\java\com\galaxyuniversalio\UsbPrinterProtocol.java ^
  app\src\main\java\com\galaxyuniversalio\UsbPrinterTransport.java ^
  tests\PrinterTransportTests.java
if errorlevel 1 goto :failed
java -cp "%OUT%" com.galaxyuniversalio.PrinterTransportTests
if errorlevel 1 goto :failed
rmdir /s /q "%OUT%"
exit /b 0
:failed
rmdir /s /q "%OUT%"
exit /b 1
