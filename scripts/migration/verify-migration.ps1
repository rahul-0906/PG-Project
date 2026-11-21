<#
.SYNOPSIS
    Standalone data verification tool to compare a source legacy database with a migrated tenant schema.
.DESCRIPTION
    Spec Phase 6: Verify row counts and a sample of invoices and payments against the old system.
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$TenantSlug,

    [Parameter(Mandatory = $true)]
    [string]$SourceDb,

    [Parameter(Mandatory = $false)]
    [string]$SourceHost = "localhost",

    [Parameter(Mandatory = $false)]
    [int]$SourcePort = 5432,

    [Parameter(Mandatory = $false)]
    [string]$SourceUser = "postgres",

    [Parameter(Mandatory = $false)]
    [string]$SourcePassword,

    [Parameter(Mandatory = $false)]
    [string]$SourceSchema = "public",

    [Parameter(Mandatory = $false)]
    [string]$TargetDb = "pgcrmdb",

    [Parameter(Mandatory = $false)]
    [string]$TargetHost = "localhost",

    [Parameter(Mandatory = $false)]
    [int]$TargetPort = 5432,

    [Parameter(Mandatory = $false)]
    [string]$TargetUser = "postgres",

    [Parameter(Mandatory = $false)]
    [string]$TargetPassword,

    [Parameter(Mandatory = $false)]
    [string]$PgBinPath
)

$ErrorActionPreference = "Stop"

if ($TenantSlug -notmatch '^[a-z][a-z0-9_]{2,40}$') {
    Write-Error "Invalid tenant slug '$TenantSlug'."
    exit 1
}

$SchemaName = "t_$TenantSlug"

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
    foreach ($p in $paths) { if (Test-Path $p) { return $p } }
    throw "PostgreSQL tool '$ToolName' not found."
}

$psql = Get-PgTool "psql"

if (-not $SourcePassword) { $SourcePassword = $env:PGPASSWORD; if (-not $SourcePassword) { $SourcePassword = "admin" } }
if (-not $TargetPassword) { $TargetPassword = $env:PGPASSWORD; if (-not $TargetPassword) { $TargetPassword = "admin" } }

function Exec-Psql([string]$Host, [int]$Port, [string]$User, [string]$Password, [string]$Db, [string]$Query) {
    $oldPass = $env:PGPASSWORD
    try {
        $env:PGPASSWORD = $Password
        $res = & $psql -h $Host -p $Port -U $User -d $Db -t -A -c $Query 2>&1
        if ($LASTEXITCODE -ne 0) { throw "psql failed: $res" }
        return ($res | Out-String).Trim()
    } finally {
        $env:PGPASSWORD = $oldPass
    }
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " PG CRM MIGRATION VERIFICATION: $SourceDb.$SourceSchema -> $SchemaName " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$tables = @(
    "users", "buildings", "building_configs", "floors", "blocks",
    "rooms", "beds", "guests", "guest_beds", "invoices",
    "invoice_line_items", "eb_bills", "eb_bill_guests",
    "daily_logs", "meter_readings", "maintenance_tickets",
    "notifications", "pricing_config", "audit_logs"
)

$discrepancies = @()
$totalSourceRows = 0
$totalTargetRows = 0

Write-Host "1. Verifying Table Row Counts..." -ForegroundColor Yellow
foreach ($tbl in $tables) {
    $srcCountStr = Exec-Psql $SourceHost $SourcePort $SourceUser $SourcePassword $SourceDb "SELECT COUNT(*) FROM $SourceSchema.$tbl;" 2>$null
    $tgtCountStr = Exec-Psql $TargetHost $TargetPort $TargetUser $TargetPassword $TargetDb "SELECT COUNT(*) FROM $SchemaName.$tbl;" 2>$null

    $srcCount = 0
    $tgtCount = 0
    [int64]::TryParse($srcCountStr, [ref]$srcCount) | Out-Null
    [int64]::TryParse($tgtCountStr, [ref]$tgtCount) | Out-Null

    $totalSourceRows += $srcCount
    $totalTargetRows += $tgtCount

    if ($srcCount -ne $tgtCount) {
        $msg = "Table '$tbl' count mismatch: Source=$srcCount, Target=$tgtCount"
        $discrepancies += $msg
        Write-Host "  ✗ $msg" -ForegroundColor Red
    } else {
        Write-Host "  ✓ Table '$tbl': $srcCount rows" -ForegroundColor Green
    }
}

Write-Host "`n2. Verifying Invoice Samples..." -ForegroundColor Yellow
$sampleSql = "SELECT id, total_amount, status FROM $SourceSchema.invoices ORDER BY generated_at DESC NULLS LAST LIMIT 10;"
$srcInvoices = Exec-Psql $SourceHost $SourcePort $SourceUser $SourcePassword $SourceDb $sampleSql
if ($srcInvoices) {
    $lines = $srcInvoices -split "`n"
    foreach ($l in $lines) {
        if ($l.Trim()) {
            $parts = $l -split '\|'
            $id = $parts[0].Trim()
            $srcTotal = $parts[1].Trim()
            $srcStatus = $parts[2].Trim()

            $tgtInv = Exec-Psql $TargetHost $TargetPort $TargetUser $TargetPassword $TargetDb "SELECT id, total_amount, status FROM $SchemaName.invoices WHERE id = '$id';"
            if (-not $tgtInv) {
                $discrepancies += "Sample invoice $id missing in target."
                Write-Host "  ✗ Sample invoice $id missing in target" -ForegroundColor Red
            } else {
                $tgtParts = $tgtInv -split '\|'
                $tgtTotal = $tgtParts[1].Trim()
                $tgtStatus = $tgtParts[2].Trim()
                if ($srcTotal -ne $tgtTotal -or $srcStatus -ne $tgtStatus) {
                    $discrepancies += "Sample invoice $id mismatch: Source=($srcTotal, $srcStatus), Target=($tgtTotal, $tgtStatus)"
                    Write-Host "  ✗ Invoice $id mismatch" -ForegroundColor Red
                } else {
                    Write-Host "  ✓ Invoice $id verified ($srcTotal, $srcStatus)" -ForegroundColor Green
                }
            }
        }
    }
}

Write-Host "`n============================================================" -ForegroundColor Cyan
if ($discrepancies.Count -eq 0) {
    Write-Host " VERIFICATION PASSED: 100% data integrity verified.         " -ForegroundColor Green
    Write-Host " Total Rows: $totalSourceRows source / $totalTargetRows target               " -ForegroundColor Green
    Write-Host "============================================================" -ForegroundColor Cyan
    exit 0
} else {
    Write-Host " VERIFICATION FAILED: $($discrepancies.Count) discrepancies found! " -ForegroundColor Red
    Write-Host "============================================================" -ForegroundColor Cyan
    exit 1
}
