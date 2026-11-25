# ==============================================================================
# PG CRM — Local Development Stack Launcher
# Performs pre-flight database connection check, port verification,
# and starts both Backend (Spring Boot) and Frontend (Vite) in separate windows.
# ==============================================================================

[CmdletBinding()]
param(
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$DbUser = "postgres",
    [string]$DbPassword = "admin",
    [string]$DbName = "pgcrmdb"
)

$ErrorActionPreference = "Stop"

Clear-Host
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "        PG CRM — Local Development Stack Launcher           " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

$rootDir = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$backendDir = Join-Path $rootDir "core-pg-crm\backend"
$frontendDir = Join-Path $rootDir "core-pg-crm\frontend"

# 1. Verify Database Connectivity
Write-Host "[1/4] Verifying PostgreSQL Database Connection..." -ForegroundColor Yellow
Write-Host "      Target: ${DbUser}@${DbHost}:${DbPort}/${DbName}" -ForegroundColor DarkGray

$tcpCheck = $false
try {
    $tcp = Test-NetConnection -ComputerName $DbHost -Port $DbPort -WarningAction SilentlyContinue
    $tcpCheck = $tcp.TcpTestSucceeded
} catch {
    $tcpCheck = $false
}

if (-not $tcpCheck) {
    Write-Host ""
    Write-Host "[ERROR] PostgreSQL is NOT reachable on ${DbHost}:${DbPort}!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please ensure the PostgreSQL service is running:" -ForegroundColor Yellow
    Write-Host "  1. Windows Services: Start 'postgresql-x64-18' (or your installed version)" -ForegroundColor White
    Write-Host "  2. PowerShell (Admin): net start postgresql-x64-18" -ForegroundColor White
    Write-Host ""
    exit 1
}

Write-Host "      [OK] PostgreSQL port $DbPort is open and listening." -ForegroundColor Green

# Optional: Verify database query
$psqlCmd = Get-Command "psql.exe" -ErrorAction SilentlyContinue
$psqlPath = if ($psqlCmd) { $psqlCmd.Source } elseif (Test-Path "C:\Program Files\PostgreSQL\18\bin\psql.exe") { "C:\Program Files\PostgreSQL\18\bin\psql.exe" } else { $null }

if ($psqlPath) {
    $env:PGPASSWORD = $DbPassword
    try {
        $res = & $psqlPath -h $DbHost -p $DbPort -U $DbUser -d $DbName -t -A -c "SELECT 1;" 2>$null
        if ($res -match "1") {
            Write-Host "      [OK] Database '$DbName' authentication verified!" -ForegroundColor Green
        }
    } catch {
        Write-Host "      [NOTICE] Spring Boot will connect to '$DbName' via JDBC." -ForegroundColor DarkGray
    }
}
Write-Host ""

# 2. Check Port Availability (8080 and 5173)
Write-Host "[2/4] Checking Port Availability..." -ForegroundColor Yellow

$port8080 = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
if ($port8080) {
    Write-Host "      [NOTICE] Port 8080 is currently occupied (PID: $($port8080[0].OwningProcess))." -ForegroundColor DarkYellow
} else {
    Write-Host "      [OK] Port 8080 (Backend) is available." -ForegroundColor Green
}

$port5173 = Get-NetTCPConnection -LocalPort 5173 -ErrorAction SilentlyContinue
if ($port5173) {
    Write-Host "      [NOTICE] Port 5173 is currently occupied (PID: $($port5173[0].OwningProcess))." -ForegroundColor DarkYellow
} else {
    Write-Host "      [OK] Port 5173 (Frontend) is available." -ForegroundColor Green
}
Write-Host ""

# 3. Launch Spring Boot Backend
Write-Host "[3/4] Starting Spring Boot Backend (Port 8080)..." -ForegroundColor Yellow
$backendCmd = "cd /d `"$backendDir`" && title PG CRM Backend [Port 8080] && set SPRING_PROFILES_ACTIVE=dev && echo Starting Spring Boot (Profile: dev)... && mvn spring-boot:run"
Start-Process cmd.exe -ArgumentList "/k", $backendCmd
Write-Host "      [OK] Backend window launched in background." -ForegroundColor Green
Write-Host ""

# 4. Launch React Frontend
Write-Host "[4/4] Starting React Frontend (Port 5173)..." -ForegroundColor Yellow
$frontendCmd = "cd /d `"$frontendDir`" && title PG CRM Frontend [Port 5173] && if not exist node_modules (echo Installing dependencies... && call npm install) && echo Starting Vite Dev Server... && npm run dev"
Start-Process cmd.exe -ArgumentList "/k", $frontendCmd
Write-Host "      [OK] Frontend window launched in background." -ForegroundColor Green
Write-Host ""

# 5. Access Info
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "               STACK LAUNCHED SUCCESSFULLY!                 " -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Frontend Web Portal : " -NoNewline; Write-Host "http://localhost:5173" -ForegroundColor White
Write-Host "  Backend REST API    : " -NoNewline; Write-Host "http://localhost:8080" -ForegroundColor White
Write-Host "  Health Monitoring   : " -NoNewline; Write-Host "http://localhost:8080/api/health" -ForegroundColor White
Write-Host ""
Write-Host "  Default Owner Credentials for Manual Testing:" -ForegroundColor Yellow
Write-Host "    Email    : " -NoNewline; Write-Host "owner@pgcrm.com" -ForegroundColor White
Write-Host "    Password : " -NoNewline; Write-Host "Admin@123" -ForegroundColor White
Write-Host "    Role     : " -NoNewline; Write-Host "PG_OWNER" -ForegroundColor White
Write-Host ""
Write-Host "  (Both servers are running in separate dedicated windows." -ForegroundColor DarkGray
Write-Host "   You can safely close this launcher window.)" -ForegroundColor DarkGray
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""
Read-Host "Press Enter to close this launcher..."
