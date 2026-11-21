<#
.SYNOPSIS
    Exports a tenant schema from the shared multi-tenant database to migrate it to a dedicated PG CRM instance.
.DESCRIPTION
    Spec Section 1:
    "A tenant can move from shared to dedicated by exporting its schema (pg_dump --schema) and restoring it on a new instance.
     dedicated: the same Docker image runs for a single tenant on its own server, own database, and own domain.
     app.deployment-mode = dedicated
     app.dedicated-tenant = <slug>"
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$TenantSlug,

    [Parameter(Mandatory = $false)]
    [string]$SourceDb = "pgcrmdb",

    [Parameter(Mandatory = $false)]
    [string]$SourceHost = "localhost",

    [Parameter(Mandatory = $false)]
    [int]$SourcePort = 5432,

    [Parameter(Mandatory = $false)]
    [string]$SourceUser = "postgres",

    [Parameter(Mandatory = $false)]
    [string]$SourcePassword,

    [Parameter(Mandatory = $false)]
    [string]$OutputDir,

    [Parameter(Mandatory = $false)]
    [string]$PgBinPath
)

$ErrorActionPreference = "Stop"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " PG CRM TENANT EXPORT: Shared Schema -> Dedicated Instance " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Validate Slug
if ($TenantSlug -notmatch '^[a-z][a-z0-9_]{2,40}$') {
    Write-Error "Invalid tenant slug '$TenantSlug'. Must match regex: ^[a-z][a-z0-9_]{2,40}$"
    exit 1
}

$SchemaName = "t_$TenantSlug"

# 2. Locate PostgreSQL tools
function Get-PgTool([string]$ToolName) {
    if ($PgBinPath -and (Test-Path (Join-Path $PgBinPath "$ToolName.exe"))) {
        return (Join-Path $PgBinPath "$ToolName.exe")
    }
    $cmd = Get-Command "$ToolName.exe" -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $paths = @(
        "C:\Program Files\PostgreSQL\18\bin\$ToolName.exe",
        "C:\Program Files\PostgreSQL\17\bin\$ToolName.exe",
        "C:\Program Files\PostgreSQL\16\bin\$ToolName.exe"
    )
    foreach ($p in $paths) {
        if (Test-Path $p) { return $p }
    }
    throw "PostgreSQL tool '$ToolName' not found."
}

$pg_dump = Get-PgTool "pg_dump"
$pg_restore = Get-PgTool "pg_restore"
$psql = Get-PgTool "psql"

if (-not $SourcePassword) { $SourcePassword = $env:PGPASSWORD; if (-not $SourcePassword) { $SourcePassword = "admin" } }

# 3. Verify tenant existence in platform.tenants
$env:PGPASSWORD = $SourcePassword
$tenantStatus = & $psql -h $SourceHost -p $SourcePort -U $SourceUser -d $SourceDb -t -A -c "SELECT status FROM platform.tenants WHERE slug = '$TenantSlug';" 2>&1
if ($LASTEXITCODE -ne 0 -or -not $tenantStatus.Trim()) {
    Write-Error "Tenant '$TenantSlug' not found in platform.tenants on $SourceDb."
    exit 1
}

Write-Host "✓ Verified tenant '$TenantSlug' (Status: $($tenantStatus.Trim()))" -ForegroundColor Green

# 4. Prepare Output Directory
if (-not $OutputDir) {
    $OutputDir = Join-Path $PSScriptRoot "exports\$TenantSlug"
}
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$timestamp = (Get-Date).ToString("yyyyMMdd_HHmmss")
$dumpFile = Join-Path $OutputDir "${TenantSlug}_export_${timestamp}.dump"

# 5. Execute pg_dump for schema
Write-Host "Step 1: Exporting schema '$SchemaName' using pg_dump..." -ForegroundColor Yellow
$dumpArgs = @(
    "-h", $SourceHost,
    "-p", $SourcePort,
    "-U", $SourceUser,
    "-d", $SourceDb,
    "--schema=$SchemaName",
    "-F", "c",
    "-b",
    "-f", $dumpFile
)

$prevEAP = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    & $pg_dump @dumpArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Error "pg_dump failed to export schema '$SchemaName'."
        exit 1
    }
} finally {
    $ErrorActionPreference = $prevEAP
}
Write-Host "✓ Schema '$SchemaName' exported to: $dumpFile" -ForegroundColor Green

# 6. Verify dump integrity using pg_restore --list
Write-Host "Step 2: Verifying dump file integrity..." -ForegroundColor Yellow
$prevEAP = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    $restoreList = & $pg_restore --list $dumpFile
    if ($LASTEXITCODE -ne 0 -or -not $restoreList) {
        Write-Error "Dump verification failed. Corrupted or invalid archive."
        exit 1
    }
} finally {
    $ErrorActionPreference = $prevEAP
}
$tableCount = ($restoreList | Select-String "TABLE DATA").Count
Write-Host "✓ Dump archive verified: contains $tableCount table data segments" -ForegroundColor Green

# 7. Generate Dedicated Deployment Configuration
Write-Host "Step 3: Generating dedicated deployment bundle..." -ForegroundColor Yellow

$dedicatedCompose = @"
version: '3.8'

services:
  pgcrm-db:
    image: postgres:18-alpine
    container_name: pgcrm-dedicated-db
    restart: unless-stopped
    environment:
      POSTGRES_DB: pgcrmdb
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: \${DB_PASSWORD:-admin}
    volumes:
      - pgcrm_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"

  pgcrm-backend:
    image: pgcrm-backend:latest
    container_name: pgcrm-dedicated-backend
    restart: unless-stopped
    depends_on:
      - pgcrm-db
    environment:
      SPRING_PROFILES_ACTIVE: prod
      APP_DEPLOYMENT_MODE: dedicated
      APP_DEDICATED_TENANT: $TenantSlug
      APP_ENCRYPTION_KEY: \${APP_ENCRYPTION_KEY}
      DB_URL: jdbc:postgresql://pgcrm-db:5432/pgcrmdb
      DB_USERNAME: postgres
      DB_PASSWORD: \${DB_PASSWORD:-admin}
    ports:
      - "8080:8080"

volumes:
  pgcrm_data:
"@
Set-Content -Path (Join-Path $OutputDir "docker-compose.dedicated.yml") -Value $dedicatedCompose

$restoreScript = @"
<#
.SYNOPSIS
    Restores this tenant export into a dedicated PG CRM PostgreSQL database.
#>
param(
    [string]`$TargetDb = "pgcrmdb",
    [string]`$TargetHost = "localhost",
    [int]`$TargetPort = 5432,
    [string]`$TargetUser = "postgres",
    [string]`$TargetPassword = "admin"
)
`$env:PGPASSWORD = `$TargetPassword
Write-Host "Restoring $dumpFile into dedicated database `$TargetDb..."
& "$pg_restore" -h `$TargetHost -p `$TargetPort -U `$TargetUser -d `$TargetDb -v "$dumpFile"
if (`$LASTEXITCODE -eq 0) {
    Write-Host "Restore complete! Registering tenant in platform.tenants on dedicated DB..."
    & "$psql" -h `$TargetHost -p `$TargetPort -U `$TargetUser -d `$TargetDb -c "CREATE SCHEMA IF NOT EXISTS platform; CREATE TABLE IF NOT EXISTS platform.tenants (id VARCHAR(255) PRIMARY KEY, slug VARCHAR(50) UNIQUE NOT NULL, display_name VARCHAR(255) NOT NULL, schema_name VARCHAR(50) UNIQUE NOT NULL, plan VARCHAR(50) NOT NULL, status VARCHAR(20) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, renewal_date DATE); INSERT INTO platform.tenants (id, slug, display_name, schema_name, plan, status) VALUES ('tenant-dedicated-001', '$TenantSlug', '$TenantSlug Dedicated', '$SchemaName', 'DEDICATED', 'ACTIVE') ON CONFLICT (slug) DO UPDATE SET status = 'ACTIVE';"
    Write-Host "Dedicated instance ready to launch with APP_DEPLOYMENT_MODE=dedicated and APP_DEDICATED_TENANT=$TenantSlug" -ForegroundColor Green
}
"@
Set-Content -Path (Join-Path $OutputDir "restore-dedicated.ps1") -Value $restoreScript

Write-Host ""
Write-Host "================================================================================" -ForegroundColor Green
Write-Host " EXPORT COMPLETE: Tenant '$TenantSlug' package generated!                       " -ForegroundColor Green
Write-Host " Output Directory: $OutputDir                                                  " -ForegroundColor Cyan
Write-Host " Dump File:        $dumpFile                                                   " -ForegroundColor Cyan
Write-Host " Dedicated Config: $(Join-Path $OutputDir "docker-compose.dedicated.yml")       " -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Green
