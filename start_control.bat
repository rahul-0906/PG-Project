@echo off
setlocal enabledelayedexpansion
title CONTROL PLANE Launcher

echo ===================================================
echo       CONTROL PLANE (B2B SaaS) Startup Launcher
echo ===================================================
echo.

set "ROOT_DIR=%~dp0"

:: Set profile environments
set SPRING_PROFILES_ACTIVE=dev

echo.
echo Starting CONTROL PLANE Backend (Port 8090) in a separate window...
start "CONTROL PLANE Backend" cmd /k "cd /d %ROOT_DIR%master-control-plane\backend && ..\..\apache-maven-3.9.16\bin\mvn spring-boot:run"

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
