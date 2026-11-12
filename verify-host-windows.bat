@echo off
setlocal EnableExtensions
cd /d "%~dp0"
call "tests\run-host-tests.bat"
if errorlevel 1 exit /b 1
call "tests\run-printer-transport-tests.bat"
if errorlevel 1 exit /b 1
call "tests\run-imaging-pdf-tests.bat"
if errorlevel 1 exit /b 1
call "tests\run-escl-tests.bat"
if errorlevel 1 exit /b 1
call "tests\run-ipp-tests.bat"
if errorlevel 1 exit /b 1
call "tests\run-reliability-tests.bat"
if errorlevel 1 exit /b 1
call "tests\run-usb-http-policy-tests.bat"
if errorlevel 1 exit /b 1
py -3 tests\run-android-signature-smoke.py
if errorlevel 1 exit /b 1
echo PASS: M0.7 host tests and signature mock compile
exit /b 0
