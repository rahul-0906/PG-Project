@echo off
:: ============================================================
:: backup_pgcrmdb.bat - PG CRM PostgreSQL Backup Script
:: Database : pgcrmdb
:: Retention : 30 days (older .dump files are auto-deleted)
:: Format   : pg_dump custom format (-Fc), restore with pg_restore
::
:: Set PGPASSWORD environment variable before running, or configure
:: a .pgpass file so pg_dump does not prompt for a password.
:: ============================================================

setlocal EnableDelayedExpansion

:: -- Configuration --
set DB_NAME=pgcrmdb
set DB_USER=%DB_USERNAME%
if "%DB_USER%"=="" set DB_USER=postgres
set BACKUP_DIR=E:\PG-Project\backups

:: -- Password: read from PGPASSWORD env or use DB_PASSWORD --
if "%PGPASSWORD%"=="" (
    if not "%DB_PASSWORD%"=="" set PGPASSWORD=%DB_PASSWORD%
)

:: -- Ensure backup directory exists --
if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"

:: -- Build datetime string YYYY-MM-DD_HH-MM --
:: %DATE% format varies by locale. Parse dd/mm/yyyy or dd-mm-yyyy.
for /f "tokens=1-3 delims=/-" %%a in ("%DATE%") do (
    set DAY=%%a
    set MON=%%b
    set YR=%%c
)
:: Strip leading space from hour (single-digit hours have a leading space)
for /f "tokens=1-2 delims=:." %%a in ("%TIME: =0%") do (
    set HR=%%a
    set MIN=%%b
)
set DATETIME=%YR%-%MON%-%DAY%_%HR%-%MIN%
set DUMP_FILE=%BACKUP_DIR%\pgcrmdb_%DATETIME%.dump

:: -- Run pg_dump --
echo [%DATE% %TIME%] Starting backup of %DB_NAME% ...
pg_dump -U %DB_USER% -Fc -f "%DUMP_FILE%" %DB_NAME%

if %ERRORLEVEL% == 0 (
    echo [SUCCESS] Backup completed: %DUMP_FILE%
) else (
    echo [FAILED] Backup FAILED - check PostgreSQL connectivity and credentials.
    exit /b 1
)

:: -- Rotate: delete .dump files older than 30 days --
echo Cleaning up dump files older than 30 days...
forfiles /p "%BACKUP_DIR%" /m "*.dump" /d -30 /c "cmd /c del @path" 2>nul
if %ERRORLEVEL% == 0 (
    echo Old backups removed.
) else (
    echo No old backups to remove, or none older than 30 days.
)

endlocal
exit /b 0
