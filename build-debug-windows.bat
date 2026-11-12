@echo off
setlocal EnableExtensions
cd /d "%~dp0"
echo [Galaxy Universal I/O] Windows debug build
where java >nul 2>nul
if errorlevel 1 goto :missing_java
where javac >nul 2>nul
if errorlevel 1 goto :missing_java
where gradle >nul 2>nul
if errorlevel 1 goto :missing_gradle
java -version
javac -version 2>&1 | findstr /B /C:"javac 21." >nul
if errorlevel 1 goto :wrong_java
gradle --version
gradle --version | findstr /B /C:"Gradle 9.8.0" >nul
if errorlevel 1 goto :wrong_gradle
call "tests\run-host-tests.bat"
if errorlevel 1 goto :tests_failed
call "tests\run-printer-transport-tests.bat"
if errorlevel 1 goto :tests_failed
call "tests\run-imaging-pdf-tests.bat"
if errorlevel 1 goto :tests_failed
call "tests\run-escl-tests.bat"
if errorlevel 1 goto :tests_failed
call "tests\run-ipp-tests.bat"
if errorlevel 1 goto :tests_failed
call "tests\run-reliability-tests.bat"
if errorlevel 1 goto :tests_failed
call "tests\run-usb-http-policy-tests.bat"
if errorlevel 1 goto :tests_failed
if not defined ANDROID_HOME if not defined ANDROID_SDK_ROOT (
  echo WARNING: ANDROID_HOME / ANDROID_SDK_ROOT not set. Gradle may use local.properties SDK path.
)
call gradle --no-daemon :app:assembleDebug --stacktrace
if errorlevel 1 goto :gradle_failed
set "APK=app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" goto :apk_missing
echo.
echo APK produced: "%CD%\%APK%"
where certutil >nul 2>nul
if not errorlevel 1 certutil -hashfile "%APK%" SHA256
exit /b 0
:wrong_java
echo ERROR: JDK 21 compiler is required.
exit /b 1
:wrong_gradle
echo ERROR: Gradle 9.8.0 is required (version mismatch).
exit /b 1
:missing_java
echo ERROR: JDK 21 java/javac were not found in PATH.
exit /b 1
:missing_gradle
echo ERROR: Installed Gradle 9.8.0 is required in PATH; wrapper not bundled.
exit /b 1
:tests_failed
echo ERROR: Host unit tests failed. Android build was not attempted.
exit /b 1
:gradle_failed
echo ERROR: Gradle build failed. Review first actual error in the log.
exit /b 1
:apk_missing
echo ERROR: Gradle reported success but expected APK was not found.
exit /b 1
