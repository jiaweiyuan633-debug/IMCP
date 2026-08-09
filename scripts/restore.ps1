param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [string]$DbHost = '127.0.0.1',
    [int]$DbPort = 3306,
    [string]$DbUser = 'root',
    [string]$DbPassword = '',
    [string]$DbName = 'admin_scaffold',
    [string]$MySqlBin = 'C:\tools\MySQL\MySQL Server 8.0\bin'
)

if (-not (Test-Path $BackupFile)) {
    throw "Backup file not found: $BackupFile"
}
$passwordArg = if ($DbPassword) { "-p$DbPassword" } else { '' }
Get-Content -Path $BackupFile -Raw |
    & (Join-Path $MySqlBin 'mysql.exe') `
        --host $DbHost --port $DbPort --user $DbUser $passwordArg $DbName
Write-Host "Restore completed from: $BackupFile"

