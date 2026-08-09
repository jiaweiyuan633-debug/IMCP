param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [int]$Concurrency = 10,
    [int]$Rounds = 20
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
                $r = Invoke-RestMethod -Uri "$Url/api/auth/login" -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 30
                if ($r.code -eq 0) { $ok++ }
            } catch {}
        }
        return $ok
    }
}
$results = $jobs | Receive-Job -Wait -AutoRemoveJob
$sw.Stop()
$success = ($results | Measure-Object -Sum).Sum
Write-Host "Concurrency=$Concurrency Rounds=$Rounds Total=$($Concurrency * $Rounds) Success=$success Elapsed=$($sw.Elapsed.TotalSeconds)s"

