@echo off
echo ========================================================
echo  Installazione Android Emulator Hypervisor Driver (AEHD)
echo ========================================================
echo.
echo Questa operazione richiede i permessi di amministratore.
echo Si aprira una finestra di conferma di Windows (UAC): clicca "Si".
echo.
powershell -Command "Start-Process cmd -ArgumentList '/c \"\"%LOCALAPPDATA%\Android\Sdk\extras\google\Android_Emulator_Hypervisor_Driver\silent_install.bat\"\"' -Verb RunAs -Wait"
echo.
echo Verifica stato servizio aehd:
sc.exe query aehd
echo.
pause
