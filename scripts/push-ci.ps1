# 推分支触发 GitHub Actions 全链路 CI
#
# 背景：本机（沙箱）直连 github.com:443 不可达（无代理、连接超时/被重置），且远程为私有/受保护
# 仓库时需要 PAT。用本脚本在有网环境或配置好代理/PAT 后一键推送并给出 CI 运行地址。
#
# 用法：
#   1) 一次性设置（任选）：
#        git remote add origin https://github.com/<owner>/<repo>.git
#     或代理（示例）：
#        git -c http.proxy=http://127.0.0.1:7890 push ...
#   2) 带 PAT（repo 权限，避免每次输入；会写入当前进程环境，不落盘）：
#        $env:GH_PAT='ghp_xxx'
#        powershell -ExecutionPolicy Bypass -File scripts/push-ci.ps1
#   3) 已配置凭据/credential helper：直接执行
#        powershell -ExecutionPolicy Bypass -File scripts/push-ci.ps1
#
# 推送策略：本仓库 .github/workflows/ci.yml 在 push 到 main/dev 时触发（PR 到 main/dev 亦触发）。
#   - 若远端为空仓库：先推 main（建立默认分支），再推 dev（触发 8 个 job）。
#   - 若远端已有 main 且本地产物需开验证分支：推 dev 即可触发；dev 存在则用 ci-verify 分支 + 开 PR。
# 输出末尾给出 Actions 页面 URL 供跟进。

param(
    [string]$Branch = 'dev',                 # 推送的验证分支（默认 dev，push 事件即触发 ci.yml）
    [switch]$AlsoPushMain,                    # 一并推送 main（远端为空仓库/首次建立默认分支时用）
    [string]$OriginUrl = ''                   # 缺省 origin 时使用该地址 add
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$pat = $env:GH_PAT

function Invoke-Git([string]$argsLine, [switch]$Auth) {
    $cmd = @('git', '-C', $root)
    if ($Auth -and $pat) {
        # 用 http.extraheader 携带 Basic PAT（避免 URL 明文进进程列表），仅单命令生效
        $basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("x-access-token:$pat"))
        $cmd += @('-c', "http.extraheader=AUTHORIZATION: Basic $basic")
    }
    $cmd += ($argsLine -split ' ')
    & $cmd[0] $cmd[1..($cmd.Length-1)]
    if ($LASTEXITCODE -ne 0) { throw "git $argsLine 失败 (exit $LASTEXITCODE)" }
}

$remotes = git -C $root remote
if (-not ($remotes -contains 'origin')) {
    if (-not $OriginUrl) { throw '未配置 origin；请传 -OriginUrl 或先 git remote add origin <url>' }
    Invoke-Git "remote add origin $OriginUrl"
}
Write-Host "== 推送前远端分支探测 =="
git -C $root ls-remote --heads origin 2>&1 | ForEach-Object { Write-Host "  $_" }

Write-Host "== 推送 $Branch =="
if ($AlsoPushMain) {
    Invoke-Git "push -u origin main:main" -Auth
}
Invoke-Git "push -u origin ${Branch}:${Branch}" -Auth

Write-Host ''
Write-Host '推送完成。CI 运行地址（若已触发）：'
$url = git -C $root remote get-url origin
if ($url -match 'github\.com[:/]([^/]+)/([^/.]+)(\.git)?') {
    Write-Host ("  https://github.com/{0}/{1}/actions" -f $Matches[1], $Matches[2])
}
Write-Host '注：Actions 需在仓库 Settings→Actions 已启用；workflow 位于 .github/workflows/ci.yml。'
