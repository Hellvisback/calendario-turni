@echo off
echo ========================================================
echo  Avvio Emulatore Android e Lancio Applicazione Turni
echo ========================================================
echo.
set PATH=%LOCALAPPDATA%\Android\Sdk\emulator;%LOCALAPPDATA%\Android\Sdk\platform-tools;%PATH%

echo [1/3] Avvio emulatore medium_phone...
start emulator.exe -avd medium_phone

echo [2/3] In attesa del dispositivo adb...
adb wait-for-device

echo [3/3] Installazione ed esecuzione APK...
adb install -r "%~dp0android-app\app\build\outputs\apk\debug\app-debug.apk"
adb shell am start -n com.turni.calendario/.MainActivity

echo.
echo Applicazione avviata con successo!
pause
