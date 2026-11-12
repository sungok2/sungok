@echo off
setlocal
cd /d "%~dp0"
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle 8.11.1 and Android SDK 35 are required.
  echo Alternatively open this folder in Android Studio or use GitHub Actions.
  exit /b 1
)
gradle --no-daemon :app:assembleDebug
if errorlevel 1 exit /b 1
echo APK: app\build\outputs\apk\debug\app-debug.apk
endlocal
