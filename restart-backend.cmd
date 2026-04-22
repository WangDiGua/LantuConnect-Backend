@echo off
setlocal
set "ROOT=%~dp0"
chcp 65001>nul
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -NoExit -File "%ROOT%restart-backend.ps1"
endlocal
