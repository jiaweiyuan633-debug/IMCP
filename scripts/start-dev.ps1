param(
    [string]$BackendPort = '8080',
    [string]$AiPort = '8000',
    [string]$FrontendPort = '5173',
    [string]$WebsitePort = '5174'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $root '.runtime'
$logs = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $runtime, $logs | Out-Null

function Start-HiddenProcess {
    param(
        [string]$Name,
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$WorkingDirectory
    )
    $out = Join-Path $logs "$Name.out.log"
    $err = Join-Path $logs "$Name.err.log"
    $process = Start-Process -FilePath $FilePath -ArgumentList $Arguments `
        -WorkingDirectory $WorkingDirectory -WindowStyle Hidden `
        -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
    $process.Id | Set-Content (Join-Path $runtime "$Name.pid")
    Write-Host "[start] $Name pid=$($process.Id) log=$out"
}

if (-not (Test-Path (Join-Path $root 'backend\target\admin-backend-0.0.1-SNAPSHOT.jar'))) {
    Write-Host '[build] backend jar missing, packaging...'
    Push-Location (Join-Path $root 'backend')
    mvn -q -DskipTests package
    Pop-Location
}

$env:DB_PORT = if ($env:DB_PORT) { $env:DB_PORT } else { '3306' }
$env:DB_PASSWORD = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { '' }
$env:SERVER_PORT = $BackendPort
$env:CALLBACK_BASE_URL = "http://127.0.0.1:$BackendPort"
# application.yml 默认 prod（无密钥即启动失败）；本地开发显式切到 dev 配置层，启用 swagger 与明文兜底密钥
$env:SPRING_PROFILES_ACTIVE = 'dev'

Start-HiddenProcess `
    -Name 'backend' `
    -FilePath 'java' `
    -Arguments @('-jar', (Join-Path $root 'backend\target\admin-backend-0.0.1-SNAPSHOT.jar')) `
    -WorkingDirectory (Join-Path $root 'backend')

Start-HiddenProcess `
    -Name 'ai-service' `
    -FilePath (Join-Path $root 'ai-service\.venv\Scripts\python.exe') `
    -Arguments @('-m', 'uvicorn', 'app.main:app', '--host', '0.0.0.0', '--port', $AiPort) `
    -WorkingDirectory (Join-Path $root 'ai-service')

Start-HiddenProcess `
    -Name 'frontend' `
    -FilePath 'pnpm.cmd' `
    -Arguments @('dev', '--port', $FrontendPort) `
    -WorkingDirectory (Join-Path $root 'frontend')

Start-HiddenProcess `
    -Name 'website' `
    -FilePath 'pnpm.cmd' `
    -Arguments @('dev', '--port', $WebsitePort) `
    -WorkingDirectory (Join-Path $root 'website')

Write-Host ''
Write-Host "Frontend : http://localhost:$FrontendPort"
Write-Host "Backend  : http://localhost:$BackendPort"
Write-Host "AI docs  : http://localhost:$AiPort/docs"
Write-Host "Website  : http://localhost:$WebsitePort"

