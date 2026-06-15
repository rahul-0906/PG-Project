@echo off
setlocal enabledelayedexpansion
title PG CRM Launcher

echo ===================================================
echo             PG CRM Startup Launcher
echo ===================================================
echo.

set "ROOT_DIR=%~dp0"

:: Read and export env variables
if exist "%ROOT_DIR%.env" (
    echo Loading environment variables from .env...
    for /f "usebackq delims=" %%L in ("%ROOT_DIR%.env") do (
        set "line=%%L"
        :: Strip leading spaces
        for /f "tokens=* delims= " %%i in ("!line!") do set "line=%%i"
        
        :: Check if it's not a comment or empty line
        if not "!line!"=="" if not "!line:~0,1!"=="#" (
            :: Split at key = value
            for /f "tokens=1* delims==" %%A in ("!line!") do (
                set "key=%%A"
                set "val=%%B"
                
                :: Clean Key
                set "key=!key: =!"
                
                :: Strip inline comments starting with #
                for /f "tokens=1 delims=#" %%K in ("!val!") do set "val=%%K"
                
                :: Trim leading spaces from value
                for /f "tokens=* delims= " %%K in ("!val!") do set "val=%%K"
                
                :: Trim trailing spaces
                for /l %%G in (1,1,10) do (
                    if "!val:~-1!"==" " set "val=!val:~0,-1!"
                )
                
                :: Strip surrounding double or single quotes
                if "!val:~0,1!"=="^"" set "val=!val:~1,-1!"
                if "!val:~0,1!"=="'" set "val=!val:~1,-1!"
                
                :: Export to environment
                set "!key!=!val!"
            )
        )
    )
) else (
    echo [WARNING] .env file not found. Make sure to configure it.
)

:: Set explicit local environment variables for the QA environment
set SPRING_PROFILES_ACTIVE=dev
set DB_PASSWORD=admin
set APP_SEED-DEMO=false

echo.
echo Starting Spring Boot Backend in a separate window...
start "PG CRM Backend" cmd /k "cd /d %ROOT_DIR%backend && ..\apache-maven-3.9.16\bin\mvn spring-boot:run"

echo Starting Vite/React Frontend in a separate window...
start "PG CRM Frontend" cmd /k "cd /d %ROOT_DIR%frontend && npm run dev"

echo.
echo ===================================================
echo  Services started successfully!
echo.
echo  - Frontend Dev Server:  http://localhost:5173
echo  - Backend API Server:   http://localhost:8080
echo ===================================================
echo.
pause
