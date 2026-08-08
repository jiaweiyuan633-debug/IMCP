param(
    [string]$BaseUrl = 'http://127.0.0.1:8080'
)

$ErrorActionPreference = 'Stop'
$passCount = 0
$failCount = 0

function Check {
    param(
        [string]$Name,
        [bool]$Condition
    )
    if ($Condition) {
        $script:passCount++
        Write-Host "[PASS] $Name"
    } else {
        $script:failCount++
        Write-Host "[FAIL] $Name"
    }
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Url,
        $Headers = @{},
        $Body = $null
    )
    $params = @{
        Uri = $Url
        Method = $Method
        Headers = $Headers
        TimeoutSec = 30
    }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json; charset=utf-8'
        $params.Body = $Body | ConvertTo-Json -Depth 8
    }
    return Invoke-RestMethod @params
}

$adminLogin = Invoke-Api -Method Post -Url "$BaseUrl/api/auth/login" -Body @{
    username = 'admin'
    password = 'admin123'
}
Check '管理员登录' ($adminLogin.code -eq 0)

$adminHeaders = @{ Authorization = "Bearer $($adminLogin.data.accessToken)" }
$me = Invoke-Api -Method Get -Url "$BaseUrl/api/auth/me" -Headers $adminHeaders
Check '当前用户信息与菜单树' ($me.code -eq 0 -and $me.data.menus.Count -gt 0)

$refresh = Invoke-Api -Method Post -Url "$BaseUrl/api/auth/refresh" -Body @{
    refreshToken = $adminLogin.data.refreshToken
}
Check 'Token 刷新' ($refresh.code -eq 0)

$userPage = Invoke-Api -Method Get -Url "$BaseUrl/api/system/user?pageNum=1&pageSize=5" -Headers $adminHeaders
Check '用户分页接口' ($userPage.code -eq 0)

$rolePage = Invoke-Api -Method Get -Url "$BaseUrl/api/system/role?pageNum=1&pageSize=5" -Headers $adminHeaders
Check '角色分页接口' ($rolePage.code -eq 0)

$menuTree = Invoke-Api -Method Get -Url "$BaseUrl/api/system/menu/tree" -Headers $adminHeaders
Check '菜单树接口' ($menuTree.code -eq 0)

$loginLog = Invoke-Api -Method Get -Url "$BaseUrl/api/monitor/login-log?pageNum=1&pageSize=5" -Headers $adminHeaders
Check '登录日志接口' ($loginLog.code -eq 0)

$operLog = Invoke-Api -Method Get -Url "$BaseUrl/api/monitor/oper-log?pageNum=1&pageSize=5" -Headers $adminHeaders
Check '操作日志接口' ($operLog.code -eq 0)

$stats = Invoke-Api -Method Get -Url "$BaseUrl/api/monitor/stats" -Headers $adminHeaders
Check '看板统计接口' ($stats.code -eq 0)

$summaryTask = Invoke-Api -Method Post -Url "$BaseUrl/api/ai/tasks" -Headers $adminHeaders -Body @{
    bizType = 'text_summary'
    params = @{ content = '冒烟测试：Java 创建任务，Python 异步执行并回调 Java。' }
}
Check '创建 AI 摘要任务' ($summaryTask.code -eq 0)

$summaryStatus = 'PENDING'
$summaryDetail = $null
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Milliseconds 500
    $summaryDetail = Invoke-Api -Method Get -Url "$BaseUrl/api/ai/tasks/$($summaryTask.data)" -Headers $adminHeaders
    $summaryStatus = $summaryDetail.data.status
    if ($summaryStatus -in @('SUCCEEDED', 'FAILED')) {
        break
    }
}
Check 'AI 任务全链路成功' (
    $summaryStatus -eq 'SUCCEEDED' -and $null -ne $summaryDetail.data.result.resultJson
)

$testerLogin = Invoke-Api -Method Post -Url "$BaseUrl/api/auth/login" -Body @{
    username = 'tester'
    password = 'tester123'
}
$testerHeaders = @{ Authorization = "Bearer $($testerLogin.data.accessToken)" }
$testerDenied = $false
try {
    Invoke-WebRequest -Uri "$BaseUrl/api/system/user" -Headers $testerHeaders -UseBasicParsing -TimeoutSec 10 | Out-Null
} catch {
    $testerDenied = $_.Exception.Response.StatusCode -eq 403
}
Check '低权限账号访问用户列表返回 403' $testerDenied

$delayTask = Invoke-Api -Method Post -Url "$BaseUrl/api/ai/tasks" -Headers $adminHeaders -Body @{
    bizType = 'text_summary'
    params = @{
        content = '取消任务冒烟测试。'
        delay_seconds = 5
    }
}
Invoke-Api -Method Delete -Url "$BaseUrl/api/ai/tasks/$($delayTask.data)" -Headers $adminHeaders | Out-Null
Start-Sleep -Milliseconds 500
$cancelDetail = Invoke-Api -Method Get -Url "$BaseUrl/api/ai/tasks/$($delayTask.data)" -Headers $adminHeaders
Check '取消 AI 任务' ($cancelDetail.data.status -eq 'CANCELLED')

Invoke-Api -Method Post -Url "$BaseUrl/api/auth/logout" -Headers $adminHeaders | Out-Null
$logoutDenied = $false
try {
    Invoke-WebRequest -Uri "$BaseUrl/api/auth/me" -Headers $adminHeaders -UseBasicParsing -TimeoutSec 10 | Out-Null
} catch {
    $logoutDenied = $_.Exception.Response.StatusCode -eq 401
}
Check '退出后 Token 失效' $logoutDenied

Write-Host ''
Write-Host "Smoke result: PASS=$passCount FAIL=$failCount"
if ($failCount -gt 0) {
    exit 1
}
exit 0
