$ErrorActionPreference = 'SilentlyContinue'
$root = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $root '.runtime'

foreach ($name in @('backend', 'ai-service', 'frontend', 'website')) {
    $pidFile = Join-Path $runtime "$name.pid"
    if (Test-Path $pidFile) {
        $processId = [int](Get-Content $pidFile | Select-Object -First 1)
        Stop-Process -Id $processId -Force
        Remove-Item $pidFile
        Write-Host "[stop] $name pid=$processId"
    }
}

