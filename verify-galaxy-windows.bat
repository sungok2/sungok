@echo off
setlocal EnableExtensions
cd /d "%~dp0"
set "APK=app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" (
 echo ERROR: APK not found. Run build-debug-windows.bat first.
 exit /b 1
)
where adb >nul 2>nul
if errorlevel 1 (echo ERROR: adb not found. Install Android SDK Platform Tools.& exit /b 1)
echo [1] Connected Android targets:
adb devices -l
echo [2] Android version (one authorized device must be connected):
adb shell getprop ro.build.version.release
if errorlevel 1 exit /b 1
echo [3] Install debug APK on the connected phone:
adb install -r "%APK%"
if errorlevel 1 exit /b 1
echo [4] APK installation command succeeded. NOT equivalent to USB hardware tests.
echo [5] Manually connect OTG scanner/printer, grant USB permission, perform the device checklist.
echo [6] Save a diagnostic TXT report from the app and fill docs\DEVICE_TEST_RECORD.csv.
exit /b 0
