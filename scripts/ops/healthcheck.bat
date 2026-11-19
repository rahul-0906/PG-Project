@echo off
REM ============================================================
REM  healthcheck.bat - PG CRM Health Check
REM  Checks PostgreSQL connectivity and backend API availability
REM
REM  Usage: healthcheck.bat
REM  Exit code: 0 = all healthy, 1 = one or more checks failed
REM ============================================================

setlocal
set DB_USER=postgres
set DB_NAME=pgcrmdb
set BACKEND_URL=http://localhost:8080/api/config/public
set EXIT_CODE=0

echo.
echo  =========================================================
echo    PG CRM Health Check - %DATE% %TIME%
echo  =========================================================

REM --- Check 1: PostgreSQL ---
pg_isready -U %DB_USER% -d %DB_NAME% -h localhost -q
if errorlevel 1 (
    echo   [DB]      FAIL  PGCRMDB DOWN - PostgreSQL not responding
    set EXIT_CODE=1
) else (
    echo   [DB]      OK    PGCRMDB UP   - PostgreSQL is healthy
)

REM --- Check 2: Backend API ---
for /f %%i in ('curl -s -o NUL -w "%%{http_code}" %BACKEND_URL% 2^>NUL') do set HTTP_CODE=%%i
if "%HTTP_CODE%"=="200" (
    echo   [BACKEND] OK    API UP       - %BACKEND_URL% responded 200
) else (
    echo   [BACKEND] FAIL  API DOWN     - %BACKEND_URL% returned HTTP %HTTP_CODE%
    set EXIT_CODE=1
)

echo  =========================================================
echo.
endlocal & exit /b %EXIT_CODE%
