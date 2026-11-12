@echo off
setlocal
cd /d "%~dp0.."
set "OUTPUT_DIR=%TEMP%\GalaxyUniversalIO-HostTests-%RANDOM%"
mkdir "%OUTPUT_DIR%" || exit /b 1
javac --release 17 -encoding UTF-8 -d "%OUTPUT_DIR%" ^
  app\src\main\java\com\galaxyuniversalio\UsbDeviceClassifier.java ^
  app\src\main\java\com\galaxyuniversalio\UsbProfileCatalog.java ^
  app\src\main\java\com\galaxyuniversalio\UsbPrinterProtocol.java ^
  tests\HostTests.java
if errorlevel 1 goto :failed
java -cp "%OUTPUT_DIR%" com.galaxyuniversalio.HostTests
if errorlevel 1 goto :failed
rmdir /s /q "%OUTPUT_DIR%"
exit /b 0
:failed
rmdir /s /q "%OUTPUT_DIR%"
exit /b 1
