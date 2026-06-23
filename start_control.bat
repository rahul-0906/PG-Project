@echo off
setlocal enabledelayedexpansion
title CONTROL PLANE Launcher

echo ===================================================
echo       CONTROL PLANE (B2B SaaS) Startup Launcher
echo ===================================================
echo.

set "ROOT_DIR=%~dp0"

:: Read and export env variables from root .env
if exist "%ROOT_DIR%.env" (
    echo Loading environment variables from .env...
    for /f "usebackq delims=" %%L in ("%ROOT_DIR%.env") do (
        set "line=%%L"
        for /f "tokens=* delims= " %%i in ("!line!") do set "line=%%i"
        if not "!line!" == "" (
            set "firstchar=!line:~0,1!"
            if not "!firstchar!" == "#" (
                for /f "tokens=1* delims==" %%A in ("!line!") do (
                    set "key=%%A"
                    set "val=%%B"
                    set "key=!key: =!"
                    for /f "tokens=1 delims=#" %%K in ("!val!") do set "val=%%K"
                    for /f "tokens=* delims= " %%K in ("!val!") do set "val=%%K"
                    for /l %%G in (1,1,10) do (
                        if "!val:~-1!"==" " set "val=!val:~0,-1!"
                    )
                    if "!val:~0,1!" == """" set "val=!val:~1,-1!"
                    if "!val:~0,1!" == "'" set "val=!val:~1,-1!"
                    set "!key!=!val!"
                    echo   - Loaded: !key!=!val!
                )
            )
        )
    )
) else (
    echo [WARNING] Root .env file not found.
)

:: Set profile environments
set SPRING_PROFILES_ACTIVE=dev

echo.
echo Starting CONTROL PLANE Backend (Port 8090) in a separate window...
start "CONTROL PLANE Backend" cmd /k "cd /d %ROOT_DIR%master-control-plane\backend && ..\..\apache-maven-3.9.16\bin\mvn spring-boot:run -Dspring-boot.run.jvmArguments=\"-DDB_WIPE_ON_STARTUP=%DB_WIPE_ON_STARTUP%\""

echo Starting CONTROL PLANE Frontend (Port 5176) in a separate window...
start "CONTROL PLANE Frontend" cmd /k "cd /d %ROOT_DIR%master-control-plane\frontend && npm run dev"

echo.
echo ===================================================
echo  SaaS CONTROL PLANE services initiated!
echo.
echo  - Admin Frontend:  http://localhost:5176
echo  - Admin Backend:   http://localhost:8090
echo ===================================================
echo.
pause
