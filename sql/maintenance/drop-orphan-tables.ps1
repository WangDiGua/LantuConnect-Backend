#Requires -Version 5.1
<#
.SYNOPSIS
  Drop live MySQL tables that are not part of the current LantuConnect schema allowlist.

.DESCRIPTION
  This script is intentionally conservative. It keeps Flyway metadata and every
  currently supported application table, including compatibility/state tables.
  Run with -WhatIf first and review the generated DROP statements before using
  it against a shared database.

  Connection values are read from environment variables:
  - LANTU_MYSQL_HOST, default 127.0.0.1
  - LANTU_MYSQL_PORT, default 3306
  - LANTU_MYSQL_DATABASE, default lantu_connect
  - LANTU_MYSQL_USER
  - LANTU_MYSQL_PASSWORD

.PARAMETER WhatIf
  Print the DROP statements without executing them.
#>
param(
  [switch]$WhatIf
)

$ErrorActionPreference = 'Stop'

$mysql = Get-Command mysql -ErrorAction SilentlyContinue
if (-not $mysql) {
  throw 'mysql client not found; install MySQL CLI and add it to PATH.'
}

$hostName = $env:LANTU_MYSQL_HOST
if (-not $hostName) { $hostName = '127.0.0.1' }
$port = $env:LANTU_MYSQL_PORT
if (-not $port) { $port = '3306' }
$database = $env:LANTU_MYSQL_DATABASE
if (-not $database) { $database = 'lantu_connect' }
$user = $env:LANTU_MYSQL_USER
$password = $env:LANTU_MYSQL_PASSWORD

if (-not $user -or -not $password) {
  throw 'Set LANTU_MYSQL_USER and LANTU_MYSQL_PASSWORD before running this script.'
}

# Keep this list aligned with sql/lantu_connect.sql plus sql/incremental/*.sql.
# Do not remove a table here until the corresponding code and migration have
# been removed as well.
$expected = [System.Collections.Generic.HashSet[string]]::new(
  [string[]]@(
    't_alert_record',
    't_alert_record_action',
    't_alert_rule',
    't_announcement',
    't_api_key',
    't_audit_item',
    't_audit_log',
    't_call_log',
    't_developer_application',
    't_favorite',
    't_integration_package',
    't_integration_package_item',
    't_login_history',
    't_notification',
    't_openai_assistant_state',
    't_openai_thread_message_state',
    't_openai_thread_run_state',
    't_openai_thread_state',
    't_org_menu',
    't_platform_role',
    't_rate_limit_rule',
    't_resource',
    't_resource_detail',
    't_resource_draft',
    't_resource_relation',
    't_resource_runtime_policy',
    't_resource_tag_rel',
    't_resource_version',
    't_review',
    't_review_helpful_rel',
    't_robotfactory_corp_mapping',
    't_robotfactory_projection',
    't_robotfactory_sync_log',
    't_sandbox_session',
    't_sensitive_action_audit',
    't_sensitive_word',
    't_system_config',
    't_tag',
    't_trace_span',
    't_usage_record',
    't_user',
    't_user_role_rel'
  )
)

$systemAllow = [System.Collections.Generic.HashSet[string]]::new(
  [string[]]@('flyway_schema_history')
)

$defaultsFile = Join-Path $env:TEMP ("lantu-mysql-" + [guid]::NewGuid().ToString('N') + ".cnf")

function New-MysqlDefaultsFile {
  $content = @"
[client]
host=$hostName
port=$port
user=$user
password="$password"
database=$database
default-character-set=utf8mb4
"@
  Set-Content -LiteralPath $defaultsFile -Value $content -NoNewline
}

function Invoke-MysqlRaw {
  param([string]$Sql)
  & mysql --defaults-extra-file="$defaultsFile" -N -B -e $Sql
}

try {
  New-MysqlDefaultsFile

  $sqlList = "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';"
  $lines = @(Invoke-MysqlRaw -Sql $sqlList | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' })

  $orphans = @(foreach ($t in $lines) {
    if ($systemAllow.Contains($t)) { continue }
    if (-not $expected.Contains($t)) { $t }
  })

  if ($orphans.Length -eq 0) {
    Write-Host "No orphan tables in [$database] (vs current schema allowlist)."
    exit 0
  }

  Write-Host "Found $($orphans.Length) orphan table(s):"
  $orphans | ForEach-Object { Write-Host "  - $_" }

  $dropBatch = ($orphans | ForEach-Object { "DROP TABLE IF EXISTS ``$_``;" }) -join "`n"

  if ($WhatIf) {
    Write-Host "`n[WhatIf] SQL:`n$dropBatch"
    exit 0
  }

  Invoke-MysqlRaw -Sql $dropBatch | Out-Null
  Write-Host "`nDROP TABLE IF EXISTS executed."
} finally {
  Remove-Item -LiteralPath $defaultsFile -Force -ErrorAction SilentlyContinue
}
