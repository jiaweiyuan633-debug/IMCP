param(
    [string]$DbHost = '127.0.0.1',
    [int]$DbPort = 3306,
    [string]$DbUser = 'root',
    [string]$DbPassword = '',
    [string]$DbName = 'admin_scaffold',
    [string]$OutputDir = 'backups',
    [string]$MySqlBin = 'C:\tools\MySQL\MySQL Server 8.0\bin'
)

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$file = Join-Path $OutputDir "$DbName`_$timestamp.sql"
$passwordArg = if ($DbPassword) { "-p$DbPassword" } else { '' }
& (Join-Path $MySqlBin 'mysqldump.exe') `
    --host $DbHost --port $DbPort --user $DbUser $passwordArg `
    --single-transaction --routines --triggers $DbName |
    Set-Content -Path $file -Encoding UTF8
Write-Output "Backup saved: $file"

