param(
    [string]$DbHost = '127.0.0.1',
    [int]$DbPort = 3306,
    [string]$DbUser = 'root',
    [string]$DbPassword = '',
    [string]$DbName = 'admin_scaffold',
    [string]$MySqlBin = 'C:\tools\MySQL\MySQL Server 8.0\bin'
)

$ErrorActionPreference = 'Stop'
$mysql = Join-Path $MySqlBin 'mysql.exe'
$passwordArg = if ($DbPassword) { "-p$DbPassword" } else { '' }
$drillDb = "$DbName`_drill"
$tempDir = Join-Path $env:TEMP 'admin-backup-drill'
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

& $mysql --host $DbHost --port $DbPort --user $DbUser $passwordArg -e "DROP DATABASE IF EXISTS $drillDb; CREATE DATABASE $drillDb;" | Out-Null

# 演练仅覆盖 MySQL 恢复链路（Redis RDB 恢复需停机、MinIO 需 mc 环境，不纳入自动演练）
$backupOutput = & (Join-Path $PSScriptRoot 'backup.ps1') `
    -DbHost $DbHost -DbPort $DbPort -DbUser $DbUser -DbPassword $DbPassword `
    -DbName $DbName -OutputDir $tempDir -SkipRedis -SkipMinio 2>&1 | Out-String

$file = [regex]::Match($backupOutput, 'Backup saved: (.+)').Groups[1].Value.Trim()
# restore.ps1 默认开启恢复校验（表数量 + 关键表 sys_user 抽查），失败即抛异常令演练失败
& (Join-Path $PSScriptRoot 'restore.ps1') `
    -BackupFile $file -DbHost $DbHost -DbPort $DbPort -DbUser $DbUser `
    -DbPassword $DbPassword -DbName $drillDb | Out-Null

$tableCount = & $mysql --host $DbHost --port $DbPort --user $DbUser $passwordArg `
    -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$drillDb';"
if ([int]$tableCount -le 0) {
    throw "Backup drill FAILED: 演练库 $drillDb 未恢复出任何表"
}

if ($drillDb -like '*_drill') {
    & $mysql --host $DbHost --port $DbPort --user $DbUser $passwordArg -e "DROP DATABASE $drillDb;" | Out-Null
}

Write-Host "Backup drill PASS: restored $tableCount tables from $file"
