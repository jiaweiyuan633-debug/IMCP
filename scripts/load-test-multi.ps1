param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [int]$Concurrency = 5,
    [int]$Rounds = 10
)

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$jobs = @()
for ($i = 0; $i -lt $Concurrency; $i++) {
    $jobs += Start-Job -ArgumentList $BaseUrl, $Rounds -ScriptBlock {
        param($Url, $Count)
        $ok = 0
        for ($j = 0; $j -lt $Count; $j++) {
            try {
                $body = @{ username = 'admin'; password = 'admin123' } | ConvertTo-Json
                $login = Invoke-RestMethod -Uri "$Url/api/auth/login" -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 30
                if ($login.code -ne 0) { continue }
                $headers = @{ Authorization = "Bearer $($login.data.accessToken)" }
                Invoke-RestMethod -Uri "$Url/api/system/user?pageSize=5" -Headers $headers -TimeoutSec 30 | Out-Null
                Invoke-RestMethod -Uri "$Url/api/monitor/server" -Headers $headers -TimeoutSec 30 | Out-Null
                Invoke-RestMethod -Uri "$Url/api/ai/tasks?pageSize=5" -Headers $headers -TimeoutSec 30 | Out-Null
                $ok++
            } catch {}
        }
        return $ok
    }
}
$results = $jobs | Receive-Job -Wait -AutoRemoveJob
$sw.Stop()
$success = ($results | Measure-Object -Sum).Sum
Write-Host "Multi-endpoint load test: Total=$($Concurrency * $Rounds) Success=$success Elapsed=$($sw.Elapsed.TotalSeconds)s"

