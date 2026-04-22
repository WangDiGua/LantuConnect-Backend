param(
    [string]$Profile = 'dev'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

function Set-Utf8Console {
    $utf8 = [System.Text.UTF8Encoding]::new($false)

    try {
        & "$env:SystemRoot\System32\chcp.com" 65001 | Out-Null
    } catch {
        # Ignore code-page switching failures and still set PowerShell encodings.
    }

    [Console]::InputEncoding = $utf8
    [Console]::OutputEncoding = $utf8
    $script:OutputEncoding = $utf8
}

function Add-JvmOptionIfMissing {
    param(
        [string]$CurrentValue,
        [string]$OptionPrefix,
        [string]$Option
    )

    if ([string]::IsNullOrWhiteSpace($CurrentValue)) {
        return $Option
    }

    if ($CurrentValue -match [regex]::Escape($OptionPrefix)) {
        return $CurrentValue
    }

    return "$Option $CurrentValue"
}

function Unlock-GeneratedConfig {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        return
    }

    try {
        Set-ItemProperty -Path $Path -Name IsReadOnly -Value $false
    } catch {
        # Ignore transient attribute update failures on generated files.
    }
}

Unlock-GeneratedConfig -Path (Join-Path $root 'target\classes\application-local.yml')
Unlock-GeneratedConfig -Path (Join-Path $root 'src\main\resources\application-local.yml')

Set-Utf8Console

$env:SPRING_PROFILES_ACTIVE = $Profile
$env:JAVA_TOOL_OPTIONS = Add-JvmOptionIfMissing -CurrentValue $env:JAVA_TOOL_OPTIONS -OptionPrefix '-Dfile.encoding=' -Option '-Dfile.encoding=UTF-8'
$env:JAVA_TOOL_OPTIONS = Add-JvmOptionIfMissing -CurrentValue $env:JAVA_TOOL_OPTIONS -OptionPrefix '-Dsun.stdout.encoding=' -Option '-Dsun.stdout.encoding=UTF-8'
$env:JAVA_TOOL_OPTIONS = Add-JvmOptionIfMissing -CurrentValue $env:JAVA_TOOL_OPTIONS -OptionPrefix '-Dsun.stderr.encoding=' -Option '-Dsun.stderr.encoding=UTF-8'
$env:MAVEN_OPTS = Add-JvmOptionIfMissing -CurrentValue $env:MAVEN_OPTS -OptionPrefix '-Dfile.encoding=' -Option '-Dfile.encoding=UTF-8'

Write-Host "Working directory: $root"
Write-Host "Spring profile: $Profile"
Write-Host "Console encoding: $([Console]::OutputEncoding.WebName)"
Write-Host "Starting backend in foreground. Press Ctrl+C to stop."

& (Join-Path $root 'mvnw.cmd') 'spring-boot:run' '-DskipTests'
exit $LASTEXITCODE
