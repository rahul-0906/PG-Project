@echo off
setlocal enabledelayedexpansion

title PG CRM Dev Launcher

echo ============================================================
echo   PG CRM - Local Development Stack Launcher
echo ============================================================
echo.

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"

:: 1. Database Configuration Defaults
if not defined DB_HOST set "DB_HOST=localhost"
if not defined DB_PORT set "DB_PORT=5432"
if not defined DB_USER set "DB_USER=postgres"
if not defined DB_PASSWORD set "DB_PASSWORD=admin"
if not defined DB_NAME set "DB_NAME=pgcrmdb"

echo [1/4] Verifying PostgreSQL Database Connection...
echo       Target: %DB_USER%@%DB_HOST%:%DB_PORT%/%DB_NAME%

:: Check Port 5432 TCP connectivity
powershell -NoProfile -Command "(New-Object System.Net.Sockets.TcpClient).Connect('%DB_HOST%', %DB_PORT%)" >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] PostgreSQL is NOT reachable on %DB_HOST%:%DB_PORT%!
    echo.
    echo Please make sure the PostgreSQL service is running:
    echo   - Windows Services: Start 'postgresql-x64-18' (or your installed version)
    echo   - PowerShell (Admin): net start postgresql-x64-18
    echo.
    pause
    exit /b 1
)
echo       [OK] Port %DB_PORT% is open and responding.

:: Check Database Query & Credentials
powershell -NoProfile -Command "$env:PGPASSWORD = '%DB_PASSWORD%'; $pg = Get-Command psql.exe -ErrorAction SilentlyContinue; $p = if ($pg) { $pg.Source } elseif (Test-Path 'C:\Program Files\PostgreSQL\18\bin\psql.exe') { 'C:\Program Files\PostgreSQL\18\bin\psql.exe' } else { 'psql' }; $r = & $p -h '%DB_HOST%' -p %DB_PORT% -U '%DB_USER%' -d '%DB_NAME%' -t -A -c 'SELECT 1;' 2>$null; if ($r -match '1') { exit 0 } else { exit 2 }" >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo       [OK] Database '%DB_NAME%' authentication verified!
) else (
    echo       [NOTICE] Could not verify query via psql CLI directly.
    echo                Spring Boot will connect via JDBC using configured credentials.
)
echo.

:: 2. Check Port Availability (8080 and 5173)
echo [2/4] Checking Port Availability...

powershell -NoProfile -Command "if (Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue) { exit 1 } else { exit 0 }" >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo       [NOTICE] Port 8080 is currently occupied.
) else (
    echo       [OK] Port 8080 (Backend) is free.
)

powershell -NoProfile -Command "if (Get-NetTCPConnection -LocalPort 5173 -ErrorAction SilentlyContinue) { exit 1 } else { exit 0 }" >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo       [NOTICE] Port 5173 is currently occupied. Vite will auto-select next available port.
) else (
    echo       [OK] Port 5173 (Frontend) is free.
)
echo.

:: 3. Launch Backend in separate window
echo [3/4] Starting Spring Boot Backend (Port 8080)...
set "SPRING_PROFILES_ACTIVE=dev"
start "PG CRM Backend (Port 8080)" cmd /k "cd /d "%ROOT_DIR%\core-pg-crm\backend" && title PG CRM Backend [Port 8080] && echo Starting Spring Boot (Profile: dev)... && mvn spring-boot:run"
echo       [OK] Backend startup window launched.
echo.

:: 4. Launch Frontend in separate window
echo [4/4] Starting React Frontend (Port 5173)...
start "PG CRM Frontend (Port 5173)" cmd /k "cd /d "%ROOT_DIR%\core-pg-crm\frontend" && title PG CRM Frontend [Port 5173] && if not exist node_modules (echo Installing dependencies... && call npm install) && echo Starting Vite Dev Server... && npm run dev"
echo       [OK] Frontend startup window launched.
echo.

echo ============================================================
echo   STACK LAUNCHED SUCCESSFULLY!
echo ============================================================
echo.
echo   Frontend Web Portal : http://localhost:5173
echo   Backend REST API    : http://localhost:8080
echo   Health Probe        : http://localhost:8080/api/health
echo.
echo   Default Credentials for Manual Testing:
echo     Email    : owner@pgcrm.com
echo     Password : Admin@123
echo     Role     : PG_OWNER
echo.
echo   (Both servers are running in separate dedicated windows.
echo    You may close this launcher window at any time.)
echo ============================================================
echo.
pause
