@echo off

:: Check for Administrator privileges
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"

:: If the error level is not 0, administrator privileges are not present.
if '%errorlevel%' NEQ '0' (
    powershell.exe -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

:: --- Administrator commands start here ---
:: Change directory to the script's own location
cd /d "%~dp0"

:: Start the Java GUI application without a console window and exit
start "ShutterSoundGUI" javaw -jar AdbShutterSoundGUI.jar

exit /b