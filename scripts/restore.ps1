param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [string]$DbHost = '127.0.0.1',
    [int]$DbPort = 3306,
    [string]$DbUser = 'root',
    [string]$DbPassword = '',
    [string]$DbName = 'admin_scaffold',
    [string]$MySqlBin = 'C:\tools\MySQL\MySQL Server 8.0\bin',
    [string]$RedisHost = '127.0.0.1',
    [int]$RedisPort = 6379,
    [string]$RedisPassword = '',
    [string]$RedisCli = 'redis-cli',
    [string]$McBin = 'mc',
    [string]$MinioEndpoint = '',
    [string]$MinioAccessKey = '',
    [string]$MinioSecretKey = '',
    [string]$MinioBucket = '',
    [switch]$SkipVerify
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path $BackupFile)) {
    throw "Backup file not found: $BackupFile"
}
$mysql = Join-Path $MySqlBin 'mysql.exe'
# 批次6（R4-1.52）：口令改经 MYSQL_PWD 环境变量注入，避免进程命令行明文
$env:MYSQL_PWD = $DbPassword

# ---- 1. MySQL 恢复 ----
try {
    Get-Content -Path $BackupFile -Raw |
        & $mysql --host $DbHost --port $DbPort --user $DbUser $DbName
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}
if ($LASTEXITCODE -ne 0) {
    throw "MySQL restore failed: $BackupFile"
}
Write-Host "MySQL restored from: $BackupFile"

# ---- 2. 恢复校验（默认开启，-SkipVerify 可关闭）----
if (-not $SkipVerify) {
    $env:MYSQL_PWD = $DbPassword
    try {
        $tableCount = & $mysql --host $DbHost --port $DbPort --user $DbUser `
            -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$DbName';"
        if ($LASTEXITCODE -ne 0 -or [int]$tableCount -le 0) {
            throw "Restore verify FAILED: restored schema '$DbName' 中未发现任何表"
        }
        # 关键表抽查：业务核心表应存在且有数据
        $userCount = & $mysql --host $DbHost --port $DbPort --user $DbUser `
            -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$DbName' AND table_name = 'sys_user';"
    } finally {
        Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    }
    $hasSysUser = ($userCount -eq 1)
    $detail = if ($hasSysUser) { '关键表 sys_user 存在' } else { '警告：sys_user 缺失，请核对备份完整性' }
    Write-Host "Restore verify PASS: $tableCount 张表，$detail"
    if (-not $hasSysUser) {
        Write-Warning "恢复内容不完整（缺失关键表），请检查备份文件来源"
    }
}

# ---- 3. Redis RDB 恢复（若备份目录含 redis.rdb）----
$rdbFile = Join-Path (Split-Path $BackupFile) 'redis.rdb'
if (Test-Path $rdbFile) {
    Write-Host ""
    Write-Warning "检测到 Redis 快照 $rdbFile，RDB 恢复需停机替换 dump.rdb，本脚本不自动执行以避免数据损坏："
    Write-Host "  1) 停止 Redis 服务"
    Write-Host "  2) 用 $rdbFile 替换 Redis 数据目录下的 dump.rdb"
    Write-Host "  3) 启动 Redis 并验证 keys 数量：$RedisCli -h $RedisHost -p $RedisPort dbsize"
} else {
    Write-Host "未检测到 Redis 快照，跳过 Redis 恢复"
}

# ---- 4. MinIO 桶恢复（若备份目录含 minio/）----
$minioDir = Join-Path (Split-Path $BackupFile) 'minio'
if (-not $MinioEndpoint) {
    Write-Host "未提供 MinioEndpoint，跳过 MinIO 恢复"
} elseif (-not (Test-Path $minioDir)) {
    Write-Host "备份目录下未发现 minio/ 镜像，跳过 MinIO 恢复"
} else {
    $alias = 'adminrestore'
    try {
        & $McBin alias set $alias $MinioEndpoint $MinioAccessKey $MinioSecretKey | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "mc alias set failed" }
        & $McBin mirror --overwrite $minioDir "$alias/$MinioBucket" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "mc mirror failed" }
        Write-Host "MinIO restored to $MinioEndpoint/$MinioBucket"
    } finally {
        & $McBin alias rm $alias 2>&1 | Out-Null
    }
}
