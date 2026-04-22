param(
    [int]$Port = 8080,
    [string]$Profile = 'dev'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

function Get-ListenerPid {
    param([int]$TargetPort)

    try {
        $listener = Get-NetTCPConnection -LocalPort $TargetPort -State Listen -ErrorAction Stop |
            Select-Object -First 1 -ExpandProperty OwningProcess
        if ($null -ne $listener) {
            return [int]$listener
        }
    } catch {
        # Fallback to netstat on environments where Get-NetTCPConnection is unavailable.
    }

    $lines = netstat -ano | Select-String ("[:.]$TargetPort\s")
    foreach ($line in $lines) {
        $text = $line.Line.Trim()
        if ($text -notmatch 'LISTENING\s+(\d+)$') {
            continue
        }
        return [int]$Matches[1]
    }

    return $null
}

$configFile = Join-Path $root 'target\classes\application-local.yml'
if (Test-Path $configFile) {
    try {
        Set-ItemProperty -Path $configFile -Name IsReadOnly -Value $false
    } catch {
        # Ignore if the file is currently not writable for a transient reason.
    }
}

$listenerPid = Get-ListenerPid -TargetPort $Port
if ($null -ne $listenerPid) {
    Write-Host "Stopping existing process on port ${Port}: PID=$listenerPid"
    Stop-Process -Id $listenerPid -Force -ErrorAction SilentlyContinue
    $deadline = (Get-Date).AddSeconds(20)
    do {
        Start-Sleep -Milliseconds 500
        $listenerPid = Get-ListenerPid -TargetPort $Port
    } while ($null -ne $listenerPid -and (Get-Date) -lt $deadline)
}

if ($null -ne (Get-ListenerPid -TargetPort $Port)) {
    throw "Failed to release port $Port before restart."
}

Write-Host "Port $Port is free. Restarting backend..."
& (Join-Path $root 'start-backend.ps1') -Profile $Profile
exit $LASTEXITCODE
