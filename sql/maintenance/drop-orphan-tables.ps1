#Requires -Version 5.1
<#
.SYNOPSIS
  将当前库中「不在代码库预期表清单内」的基表视为孤儿表并 DROP（幂等 IF EXISTS）。

  连接信息：与仓库 mysql-local-schema 约定一致，使用环境变量（勿把密码写入仓库）。
  - LANTU_MYSQL_HOST（默认 127.0.0.1）
  - LANTU_MYSQL_PORT（默认 3306）
  - LANTU_MYSQL_DATABASE（默认 lantu_connect）
  - LANTU_MYSQL_USER
  - LANTU_MYSQL_PASSWORD

.PARAMETER WhatIf
  仅打印将执行的 DROP，不调用 mysql。
#>
param(
  [switch]$WhatIf
)

$ErrorActionPreference = 'Stop'

$mysql = Get-Command mysql -ErrorAction SilentlyContinue
if (-not $mysql) {
  throw 'mysql client not found; install MySQL CLI and add to PATH.'
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
  throw 'Set LANTU_MYSQL_USER and LANTU_MYSQL_PASSWORD (see mysql-local-schema workspace rule).'
}

# 与仓库 Java @TableName + sql 目录下 CREATE TABLE 保持一致的预期业务表（含 flyway 历史表排除项由下方 allowlist 处理）
$expected = [System.Collections.Generic.HashSet[string]]::new(
  [string[]]@(
    't_access_token','t_alert_record','t_alert_rule','t_announcement','t_api_key','t_audit_item','t_audit_log',
    't_call_log','t_category','t_developer_application','t_favorite','t_login_history','t_notification','t_org_menu',
    't_platform_role','t_provider','t_quota','t_quota_rate_limit','t_rate_limit_rule','t_resource','t_resource_agent_ext',
    't_resource_app_ext','t_resource_circuit_breaker','t_resource_dataset_ext','t_resource_draft','t_resource_grant_application',
    't_resource_health_config','t_resource_invoke_grant','t_resource_mcp_ext','t_resource_relation','t_resource_skill_ext',
    't_resource_tag_rel','t_resource_version','t_review','t_review_helpful_rel','t_sandbox_session','t_security_setting',
    't_sensitive_action_audit','t_sensitive_word','t_skill_external_catalog_item','t_skill_external_catalog_sync',
    't_skill_external_download_event','t_skill_external_favorite','t_skill_external_review','t_skill_external_view_event',
    't_skill_pack_download_event','t_system_param','t_tag','t_trace_span','t_usage_record','t_user','t_user_role_rel'
  )
)

# Flyway / 工具链；勿删
$systemAllow = [System.Collections.Generic.HashSet[string]]::new(
  [string[]]@('flyway_schema_history')
)

function Invoke-MysqlRaw {
  param([string]$Sql)
  $mysqlCliArgs = @(
    '-h', $hostName,
    '-P', $port,
    '-u', $user,
    "-p$password",
    $database,
    '-N', '-B',
    '--default-character-set=utf8mb4',
    '-e', $Sql
  )
  & mysql @mysqlCliArgs
}

$sqlList = "SELECT table_name FROM information_schema.tables WHERE table_schema = '$database' AND table_type = 'BASE TABLE';"
$lines = @(Invoke-MysqlRaw -Sql $sqlList | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' })

$orphans = @(foreach ($t in $lines) {
  if ($systemAllow.Contains($t)) { continue }
  if (-not $expected.Contains($t)) { $t }
})

if ($orphans.Length -eq 0) {
  Write-Host "No orphan tables in [$database] (vs code expected set)."
  exit 0
}

Write-Host "Dropping $($orphans.Length) orphan table(s):"
$orphans | ForEach-Object { Write-Host "  - $_" }

$dropBatch = ($orphans | ForEach-Object { "DROP TABLE IF EXISTS ``$_``;" }) -join "`n"

if ($WhatIf) {
  Write-Host "`n[WhatIf] SQL:`n$dropBatch"
  exit 0
}

Invoke-MysqlRaw -Sql $dropBatch | Out-Null
Write-Host "`nDROP TABLE IF EXISTS executed."
