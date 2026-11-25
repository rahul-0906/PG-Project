@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\dev\start_dev.ps1"
if %ERRORLEVEL% neq 0 (
    echo.
    echo Launcher encountered an error. Press any key to exit.
    pause >nul
)
