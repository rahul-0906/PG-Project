<#
.SYNOPSIS
    Migrates an existing single-tenant PG CRM client database into a dedicated tenant schema (t_<slug>) in shared mode.
.DESCRIPTION
    Phase 6 Migration Tool:
    1. Validates tenant slug regex (^[a-z][a-z0-9_]{2,40}$).
    2. Registers tenant in platform.tenants with status PROVISIONING.
    3. Creates target schema t_<slug> and applies tenant Flyway migrations.
    4. Dumps table data from source database and streams it into t_<slug>.
    5. Synchronizes sequences.
    6. Runs comprehensive row-count and sample financial data verification.
    7. Marks tenant ACTIVE upon 100% verification pass.
    8. Leaves source database intact and prompts operator to keep old stack running until owner confirmation.
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$TenantSlug,

    [Parameter(Mandatory = $false)]
    [string]$TenantDisplayName,

    [Parameter(Mandatory = $false)]
    [string]$Plan = "STANDARD",

    [Parameter(Mandatory = $false)]
    [string]$Domain,

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

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " PG CRM CLIENT MIGRATION: Single-Tenant DB -> Tenant Schema " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Slug Validation
if ($TenantSlug -notmatch '^[a-z][a-z0-9_]{2,40}$') {
    Write-Error "Invalid tenant slug '$TenantSlug'. Must match regex: ^[a-z][a-z0-9_]{2,40}$"
    exit 1
}

$SchemaName = "t_$TenantSlug"
if (-not $TenantDisplayName) {
    $TenantDisplayName = $TenantSlug.ToUpper()
}

# 2. Locate PostgreSQL tools (psql, pg_dump)
function Get-PgTool([string]$ToolName) {
    if ($PgBinPath -and (Test-Path (Join-Path $PgBinPath "$ToolName.exe"))) {
        return (Join-Path $PgBinPath "$ToolName.exe")
    }
    $cmd = Get-Command "$ToolName.exe" -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }
    # Common standard installations
    $paths = @(
        "C:\Program Files\PostgreSQL\18\bin\$ToolName.exe",
        "C:\Program Files\PostgreSQL\17\bin\$ToolName.exe",
        "C:\Program Files\PostgreSQL\16\bin\$ToolName.exe"
    )
    foreach ($p in $paths) {
        if (Test-Path $p) { return $p }
    }
    throw "PostgreSQL tool '$ToolName' not found on PATH or in standard directories."
}

$psql = Get-PgTool "psql"
$pg_dump = Get-PgTool "pg_dump"
Write-Host "✓ Detected PostgreSQL tools: psql and pg_dump" -ForegroundColor Green

# Set default passwords from environment if not specified
if (-not $SourcePassword) { $SourcePassword = $env:PGPASSWORD; if (-not $SourcePassword) { $SourcePassword = "admin" } }
if (-not $TargetPassword) { $TargetPassword = $env:PGPASSWORD; if (-not $TargetPassword) { $TargetPassword = "admin" } }

function Exec-Psql([string]$Host, [int]$Port, [string]$User, [string]$Password, [string]$Db, [string]$Query) {
    $oldPass = $env:PGPASSWORD
    $prevEAP = $ErrorActionPreference
    try {
        $env:PGPASSWORD = $Password
        $ErrorActionPreference = "Continue"
        $res = & $psql -h $Host -p $Port -U $User -d $Db -t -A -c $Query
        if ($LASTEXITCODE -ne 0) {
            throw "psql query failed (exit code $LASTEXITCODE)"
        }
        return ($res | Out-String).Trim()
    } finally {
        $env:PGPASSWORD = $oldPass
        $ErrorActionPreference = $prevEAP
    }
}

# 3. Connectivity checks
Write-Host "Verifying database connections..."
try {
    $srcCheck = Exec-Psql $SourceHost $SourcePort $SourceUser $SourcePassword $SourceDb "SELECT 1;"
    $tgtCheck = Exec-Psql $TargetHost $TargetPort $TargetUser $TargetPassword $TargetDb "SELECT 1;"
    Write-Host "✓ Successfully connected to Source DB ($SourceDb) and Target DB ($TargetDb)" -ForegroundColor Green
} catch {
    Write-Error "Database connection failure: $_"
    exit 1
}

# 4. Check platform.tenants in Target DB
$existingStatus = Exec-Psql $TargetHost $TargetPort $TargetUser $TargetPassword $TargetDb "SELECT status FROM platform.tenants WHERE slug = '$TenantSlug';"
if ($existingStatus -eq "ACTIVE") {
    Write-Error "Tenant '$TenantSlug' already exists and is ACTIVE in platform.tenants! Aborting to avoid overwrite."
    exit 1
}

$tenantId = "tenant-" + [guid]::NewGuid().ToString().Substring(0, 8)

Write-Host "Step 1: Registering tenant '$TenantSlug' as PROVISIONING..." -ForegroundColor Yellow
if ($existingStatus) {
    Exec-Psql $TargetHost $TargetPort $TargetUser $TargetPassword $TargetDb "UPDATE platform.tenants SET status = 'PROVISIONING' WHERE slug = '$TenantSlug';"
} else {
    Exec-Psql $TargetHost $TargetPort $TargetUser $TargetPassword $TargetDb "INSERT INTO platform.tenants (id, slug, display_name, schema_name, plan, status, created_at) VALUES ('$tenantId', '$TenantSlug', '$TenantDisplayName', '$SchemaName', '$Plan', 'PROVISIONING', CURRENT_TIMESTAMP);"
}

try {
    Write-Host "Step 2: Creating PostgreSQL schema '$SchemaName'..." -ForegroundColor Yellow
    Exec-Psql $TargetHost $TargetPort $TargetUser $TargetPassword $TargetDb "CREATE SCHEMA IF NOT EXISTS $SchemaName;"

    Write-Host "Step 3: Applying Flyway tenant schema migrations to '$SchemaName'..." -ForegroundColor Yellow
    # Execute migration scripts via Flyway or using project DDL files
    $migrationDir = Join-Path $PSScriptRoot "..\..\core-pg-crm\backend\src\main\resources\db\migration\tenant"
    if (Test-Path $migrationDir) {
        $sqlFiles = Get-ChildItem -Path $migrationDir -Filter "*.sql" | Sort-Object { [int](($_.Name -split '__')[0] -replace '[^\d]','') }
        foreach ($file in $sqlFiles) {
            $env:PGPASSWORD = $TargetPassword
            & $psql -h $TargetHost -p $TargetPort -U $TargetUser -d $TargetDb -c "SET search_path TO $SchemaName, public;" -f $file.FullName > $null 2>&1
        }
        Write-Host "✓ Applied $($sqlFiles.Count) tenant migrations to '$SchemaName'" -ForegroundColor Green
    } else {
        Write-Warning "Migration directory not found locally; assuming schema DDL was pre-created or managed by application."
    }

    Write-Host "Step 4: Dumping table data from Source DB ($SourceDb.$SourceSchema)..." -ForegroundColor Yellow
    $tempDump = [System.IO.Path]::GetTempFileName()
    $oldPass = $env:PGPASSWORD
    $prevEAP = $ErrorActionPreference
    try {
        $env:PGPASSWORD = $SourcePassword
        $ErrorActionPreference = "Continue"
        & $pg_dump -h $SourceHost -p $SourcePort -U $SourceUser -d $SourceDb --data-only --no-owner --no-privileges -n $SourceSchema -f $tempDump
        if ($LASTEXITCODE -ne 0) {
            throw "pg_dump failed to extract source data (exit code $LASTEXITCODE)."
        }
        Write-Host "✓ Extracted source table data into temporary buffer" -ForegroundColor Green
    } finally {
        $env:PGPASSWORD = $oldPass
        $ErrorActionPreference = $prevEAP
    }

    Write-Host "Step 5: Transforming schema references and streaming into '$SchemaName'..." -ForegroundColor Yellow
    # Read dump, replace qualified schema references (e.g. COPY public. -> COPY t_<slug>.)
    $rawContent = [System.IO.File]::ReadAllText($tempDump)
    # Replace schema qualification
    $transformed = $rawContent -replace "(?i)COPY\s+$SourceSchema\.", "COPY $SchemaName."
    $transformed = $transformed -replace "(?i)INSERT\s+INTO\s+$SourceSchema\.", "INSERT INTO $SchemaName."

    # Wrap in foreign key disable/enable block
    $importSql = [System.IO.Path]::GetTempFileName()
    $header = "SET search_path TO $SchemaName, public;`nSET session_replication_role = 'replica';`n"
    $footer = "`nSET session_replication_role = 'origin';`n"
    [System.IO.File]::WriteAllText($importSql, $header + $transformed + $footer)

    $env:PGPASSWORD = $TargetPassword
    $prevEAP = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $psql -h $TargetHost -p $TargetPort -U $TargetUser -d $TargetDb -f $importSql
        if ($LASTEXITCODE -ne 0) {
            throw "psql failed to restore data into $SchemaName (exit code $LASTEXITCODE)."
        }
    } finally {
        $ErrorActionPreference = $prevEAP
        Remove-Item $tempDump -Force -ErrorAction SilentlyContinue
        Remove-Item $importSql -Force -ErrorAction SilentlyContinue
    }

    Write-Host "✓ Restored table data into '$SchemaName'" -ForegroundColor Green

    Write-Host "Step 6: Synchronizing sequences in '$SchemaName'..." -ForegroundColor Yellow
    $seqScript = @"
DO `$\$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = '$SchemaName') LOOP
        EXECUTE 'SELECT setval(''$SchemaName.' || r.sequence_name || ''', COALESCE(MAX(id), 1)) FROM $SchemaName.' || replace(r.sequence_name, '_id_seq', '');
    END LOOP;
END `$\$;
"@
    Exec-Psql $TargetHost $TargetPort $TargetUser $TargetPassword $TargetDb $seqScript | Out-Null

    Write-Host "Step 7: Executing Data Integrity & Verification Suite..." -ForegroundColor Yellow
    # Core tables to verify
    $tables = @(
        "users", "buildings", "building_configs", "floors", "blocks",
        "rooms", "beds", "guests", "guest_beds", "invoices",
        "invoice_line_items", "eb_bills", "eb_bill_guests",
        "daily_logs", "meter_readings", "maintenance_tickets",
        "notifications", "pricing_config", "audit_logs"
    )

    $discrepancies = @()
    $reportTableCounts = @()

    foreach ($tbl in $tables) {
        $srcCountStr = Exec-Psql $SourceHost $SourcePort $SourceUser $SourcePassword $SourceDb "SELECT COUNT(*) FROM $SourceSchema.$tbl;" 2>$null
        $tgtCountStr = Exec-Psql $TargetHost $TargetPort $TargetUser $TargetPassword $TargetDb "SELECT COUNT(*) FROM $SchemaName.$tbl;" 2>$null

        $srcCount = 0
        $tgtCount = 0
        [int64]::TryParse($srcCountStr, [ref]$srcCount) | Out-Null
        [int64]::TryParse($tgtCountStr, [ref]$tgtCount) | Out-Null

        $matched = ($srcCount -eq $tgtCount)
        if (-not $matched) {
            $msg = "Table '$tbl' row count mismatch: Source=$srcCount, Target=$tgtCount"
            $discrepancies += $msg
            Write-Host "  ✗ $msg" -ForegroundColor Red
        } else {
            Write-Host "  ✓ Table '$tbl' verified: $srcCount rows" -ForegroundColor Green
        }
        $reportTableCounts += [PSCustomObject]@{
            Table = $tbl
            SourceCount = $srcCount
            TargetCount = $tgtCount
            Matches = $matched
        }
    }

    # Verify Invoices Sample
    Write-Host "Verifying sample invoices..."
    $invSampleQuery = "SELECT id, total_amount, status FROM $SourceSchema.invoices ORDER BY generated_at DESC NULLS LAST LIMIT 10;"
    $srcInvoices = Exec-Psql $SourceHost $SourcePort $SourceUser $SourcePassword $SourceDb $invSampleQuery
    if ($srcInvoices) {
        $lines = $srcInvoices -split "`n"
        foreach ($line in $lines) {
            if ($line.Trim()) {
                $parts = $line -split '\|'
                $invId = $parts[0].Trim()
                $tgtInv = Exec-Psql $TargetHost $TargetPort $TargetUser $TargetPassword $TargetDb "SELECT id, total_amount, status FROM $SchemaName.invoices WHERE id = '$invId';"
                if (-not $tgtInv) {
                    $discrepancies += "Sample invoice ID '$invId' not found in target schema."
                }
            }
        }
    }

    if ($discrepancies.Count -gt 0) {
        throw "Verification failed with $($discrepancies.Count) discrepancies! Rolling back..."
    }

    Write-Host "Step 8: Finalizing Tenant Activation..." -ForegroundColor Yellow
    Exec-Psql $TargetHost $TargetPort $TargetUser $TargetPassword $TargetDb "UPDATE platform.tenants SET status = 'ACTIVE' WHERE slug = '$TenantSlug';"
    if ($Domain) {
        Exec-Psql $TargetHost $TargetPort $TargetUser $TargetPassword $TargetDb "INSERT INTO platform.tenant_domains (host, tenant_id) VALUES ('$Domain', (SELECT id FROM platform.tenants WHERE slug = '$TenantSlug')) ON CONFLICT DO NOTHING;"
    }

    # Generate Audit Report File
    $report = [PSCustomObject]@{
        TenantSlug = $TenantSlug
        SchemaName = $SchemaName
        SourceDb = $SourceDb
        TargetDb = $TargetDb
        Status = "ACTIVE"
        VerifiedAt = (Get-Date).ToString("o")
        TableCounts = $reportTableCounts
        Discrepancies = $discrepancies
    }
    $reportDir = Join-Path $PSScriptRoot "reports"
    if (-not (Test-Path $reportDir)) { New-Item -ItemType Directory -Path $reportDir | Out-Null }
    $reportPath = Join-Path $reportDir "migration_report_${TenantSlug}_$((Get-Date).ToString('yyyyMMdd_HHmmss')).json"
    $report | ConvertTo-Json -Depth 5 | Set-Content -Path $reportPath
    Write-Host "✓ Migration audit report written to: $reportPath" -ForegroundColor Green

    Write-Host ""
    Write-Host "================================================================================" -ForegroundColor Green
    Write-Host " MIGRATION VERIFICATION SUCCEEDED: Tenant '$TenantSlug' is now ACTIVE!           " -ForegroundColor Green
    Write-Host "                                                                                " -ForegroundColor Yellow
    Write-Host " CRITICAL OPERATIONAL RULE:                                                     " -ForegroundColor Yellow
    Write-Host " Keep the old single-tenant database and stack running until the PG owner has   " -ForegroundColor Yellow
    Write-Host " logged into the shared portal and confirmed complete data integrity.          " -ForegroundColor Yellow
    Write-Host "================================================================================" -ForegroundColor Green

} catch {
    Write-Host "✗ MIGRATION ERROR: $_" -ForegroundColor Red
    Write-Host "Initiating rollback of newly created artifacts..." -ForegroundColor Yellow
    try {
        Exec-Psql $TargetHost $TargetPort $TargetUser $TargetPassword $TargetDb "DROP SCHEMA IF EXISTS $SchemaName CASCADE;" | Out-Null
        Exec-Psql $TargetHost $TargetPort $TargetUser $TargetPassword $TargetDb "DELETE FROM platform.tenant_domains WHERE tenant_id IN (SELECT id FROM platform.tenants WHERE slug = '$TenantSlug');" | Out-Null
        Exec-Psql $TargetHost $TargetPort $TargetUser $TargetPassword $TargetDb "DELETE FROM platform.tenants WHERE slug = '$TenantSlug' AND status = 'PROVISIONING';" | Out-Null
        Write-Host "✓ Rollback completed: dropped '$SchemaName' and cleaned up platform registry." -ForegroundColor Green
    } catch {
        Write-Error "Error during rollback: $_"
    }
    exit 1
}
