@echo off
setlocal
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\automation\Github-push.ps1" %*
echo.
pause
exit /b %ERRORLEVEL%
