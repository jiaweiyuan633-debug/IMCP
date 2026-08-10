param(
    [string]$DbHost = '127.0.0.1',
    [int]$DbPort = 3306,
    [string]$DbUser = 'root',
    [string]$DbPassword = '',
    [string]$DbName = 'admin_scaffold',
    [string]$OutputDir = 'backups',
    [string]$MySqlBin = 'C:\tools\MySQL\MySQL Server 8.0\bin',
    # Redis：RDB 快照（--rdb 由服务器端传输，密码经 REDISCLI_AUTH 环境变量注入，避免命令行明文）
    [string]$RedisHost = '127.0.0.1',
    [int]$RedisPort = 6379,
    [string]$RedisPassword = '',
    [string]$RedisCli = 'redis-cli',
    # MinIO：通过 mc 客户端镜像桶到本地备份目录（可下载独立 mc 可执行文件，无需安装）
    [string]$McBin = 'mc',
    [string]$MinioEndpoint = '',
    [string]$MinioAccessKey = '',
    [string]$MinioSecretKey = '',
    [string]$MinioBucket = '',
    [switch]$SkipRedis,
    [switch]$SkipMinio
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd_HHmmss'
# 单次备份统一放进同名前缀目录：SQL + redis.rdb + minio/ 便于整体归档与恢复
$backupRoot = Join-Path $OutputDir "$DbName`_$timestamp"
New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null

# ---- 1. MySQL 逻辑备份 ----
$file = Join-Path $backupRoot "$DbName.sql"
$passwordArg = if ($DbPassword) { "-p$DbPassword" } else { '' }
& (Join-Path $MySqlBin 'mysqldump.exe') `
    --host $DbHost --port $DbPort --user $DbUser $passwordArg `
    --single-transaction --routines --triggers $DbName |
    Set-Content -Path $file -Encoding UTF8
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $file)) {
    throw "MySQL backup failed"
}
Write-Output "Backup saved: $file"

# ---- 2. Redis RDB 快照（可选）----
if (-not $SkipRedis) {
    $rdbFile = Join-Path $backupRoot 'redis.rdb'
    $env:REDISCLI_AUTH = $RedisPassword
    try {
        & $RedisCli -h $RedisHost -p $RedisPort --rdb $rdbFile 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0 -and (Test-Path $rdbFile)) {
            Write-Output "Redis snapshot saved: $rdbFile"
        } else {
            Write-Warning "Redis backup skipped (redis-cli --rdb failed)。请确认 RedisCli 路径与端口。"
        }
    } finally {
        Remove-Item Env:REDISCLI_AUTH -ErrorAction SilentlyContinue
    }
} else {
    Write-Output "Redis backup skipped (SkipRedis)"
}

# ---- 3. MinIO 桶镜像（可选）----
if (-not $SkipMinio -and $MinioEndpoint) {
    $minioDir = Join-Path $backupRoot 'minio'
    $alias = 'adminbackup'
    try {
        & $McBin alias set $alias $MinioEndpoint $MinioAccessKey $MinioSecretKey | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "mc alias set failed" }
        & $McBin mirror --overwrite "$alias/$MinioBucket" $minioDir | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "mc mirror failed" }
        Write-Output "MinIO mirror saved: $minioDir"
    } catch {
        Write-Warning "MinIO backup skipped: $_"
    } finally {
        & $McBin alias rm $alias 2>&1 | Out-Null
    }
} else {
    Write-Output "MinIO backup skipped (未提供 MinioEndpoint 或 SkipMinio)"
}

Write-Output "Backup root: $backupRoot"
