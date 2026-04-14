param(
    [int]$Port = 8080
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

function Get-ListenerPid {
    param([int]$TargetPort)

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

$pid = Get-ListenerPid -TargetPort $Port
if ($null -ne $pid) {
    Write-Host "Stopping existing process on port $Port: PID=$pid"
    Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
}

$outLog = Join-Path $root 'backend-restart.out.log'
$errLog = Join-Path $root 'backend-restart.err.log'

$startInfo = Start-Process -FilePath (Join-Path $root 'mvnw.cmd') `
    -ArgumentList @('spring-boot:run', '-DskipTests') `
    -WorkingDirectory $root `
    -RedirectStandardOutput $outLog `
    -RedirectStandardError $errLog `
    -PassThru

Write-Host "Started mvnw wrapper PID=$($startInfo.Id)"
Write-Host "Waiting for port $Port..."

$deadline = (Get-Date).AddMinutes(3)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    $listenerPid = Get-ListenerPid -TargetPort $Port
    if ($null -ne $listenerPid) {
        Write-Host "Port $Port is listening: PID=$listenerPid"
        break
    }
}

if ($null -eq (Get-ListenerPid -TargetPort $Port)) {
    Write-Host "Backend did not start listening on port $Port within the timeout."
}

Write-Host "Logs:"
Write-Host "  $outLog"
Write-Host "  $errLog"
