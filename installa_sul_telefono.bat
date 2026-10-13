@echo off
echo ========================================================
echo  Installazione App Turni sul tuo Telefono Android (Pixel)
echo ========================================================
echo.
set PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools;%PATH%

echo In attesa che il telefono sia pronto (Debug USB attivo)...
adb wait-for-device

echo Dispositivo rilevato:
adb devices -l
echo.

echo Installazione APK debug aggiornato...
adb install -r "%~dp0android-app\app\build\outputs\apk\debug\app-debug.apk"
echo.

echo Avvio dell'applicazione sullo schermo del telefono...
adb shell am start -n com.turni.calendario/.MainActivity
echo.

echo ========================================================
echo  Fatto! L'app e ora aperta ed attiva sul tuo telefono.
echo ========================================================
pause
