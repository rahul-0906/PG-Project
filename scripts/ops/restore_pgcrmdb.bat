@echo off
:: ============================================================
:: restore_pgcrmdb.bat — PG CRM PostgreSQL Restore Test Script
:: Restores a .dump file into pgcrmdb_restore_test (NOT the live DB)
:: Usage: restore_pgcrmdb.bat <path\to\pgcrmdb_YYYY-MM-DD_HH-MM.dump>
:: ============================================================

setlocal EnableDelayedExpansion

:: ── Parameter check ──────────────────────────────────────────
if "%~1"=="" (
    echo.
    echo Usage: restore_pgcrmdb.bat ^<path\to\backup.dump^>
    echo.
    echo Example:
    echo   restore_pgcrmdb.bat E:\PG-Project\backups\pgcrmdb_2024-01-15_02-00.dump
    echo.
    echo The dump is restored into a TEST database (pgcrmdb_restore_test),
    echo NOT the live pgcrmdb database.
    exit /b 1
)

set DUMP_FILE=%~1
set TEST_DB=pgcrmdb_restore_test
set DB_USER=postgres

:: ── Confirm ──────────────────────────────────────────────────
echo.
echo ⚠️  This will DROP and recreate database: %TEST_DB%
echo    Dump file : %DUMP_FILE%
echo    Live database (pgcrmdb) will NOT be touched.
echo.
set /p CONFIRM="Continue? (Y/N): "
if /i not "%CONFIRM%"=="Y" (
    echo Aborted.
    exit /b 0
)

:: ── Drop existing test DB (ignore error if it doesn't exist) ─
echo.
echo [%DATE% %TIME%] Dropping existing test database (if any)...
dropdb -U %DB_USER% %TEST_DB% 2>nul

:: ── Create fresh test DB ─────────────────────────────────────
echo [%DATE% %TIME%] Creating test database: %TEST_DB% ...
createdb -U %DB_USER% %TEST_DB%
if %ERRORLEVEL% neq 0 (
    echo ❌ Failed to create test database. Aborting.
    exit /b 1
)

:: ── Restore ──────────────────────────────────────────────────
echo [%DATE% %TIME%] Restoring dump into %TEST_DB% ...
pg_restore -U %DB_USER% -d %TEST_DB% "%~1"
if %ERRORLEVEL% == 0 (
    echo.
    echo ✅ Restore succeeded into: %TEST_DB%
    echo    Verify with: psql -U %DB_USER% -d %TEST_DB%
) else (
    echo.
    echo ❌ Restore FAILED — check dump file integrity and PostgreSQL logs.
    exit /b 1
)

endlocal
exit /b 0
