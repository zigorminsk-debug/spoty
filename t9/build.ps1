# Сборка Spoty T9 для Windows (требуется Go >= 1.22).
$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot
go build -trimpath -ldflags "-s -w -H windowsgui" -o dist\spoty-t9.exe .
Write-Host "Готово: dist\spoty-t9.exe"
