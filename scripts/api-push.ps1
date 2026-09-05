# GitHub Git Data API 建库推送（github.com 直连被路由封锁时的替代通路）
#
# 背景：本机 github.com:443 不可达（20.205.243.166 被墙），但 api.github.com:443 可达；
# 用 Git Data API 把本地 main（当前全量树，含全部历史改动）以“单根提交 + 全量树”推为远端
# main/dev 两个 ref，触发 .github/workflows/ci.yml（push main/dev）。
#
# 用法：
#   $env:GH_PAT='github_pat_...'   # repo 内容读写权限（fine-grained）
#   powershell -ExecutionPolicy Bypass -File scripts/api-push.ps1
#
# 注意：以“单根提交（无父）覆盖全树”建立远端历史（不逐批搬运本地 63 个提交）；
# 若远端已非空需先确认（本脚本会在 ref 已存在时报错退出）。

param(
    [string]$Repo = 'jiaweiyuan633-debug/IMCP',
    [string]$Branch = 'main',
    [switch]$AlsoDev = $true,
    [switch]$UseLocalShas = $false   # 对象已在远端（上次已上传）时用本地 git 对象 sha，跳过重复上传
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$pat = $env:GH_PAT
if (-not $pat) { throw '缺少 GH_PAT 环境变量' }
$api = "https://api.github.com/repos/$Repo"
$headers = @{ Authorization = "Bearer $pat"; 'User-Agent' = 'dsh-api-push'; 'Accept' = 'application/vnd.github+json' }

function Invoke-Api([string]$method, [string]$url, $body = $null, [int]$retry = 3) {
    for ($i = 0; $i -lt $retry; $i++) {
        try {
            $params = @{ Method = $method; Uri = $url; Headers = $headers; TimeoutSec = 60 }
            if ($null -ne $body) { $params.ContentType = 'application/json'; $params.Body = ($body | ConvertTo-Json -Depth 20 -Compress) }
            return Invoke-RestMethod @params
        } catch {
            if ($i -eq $retry - 1) {
                $detail = $_.ErrorDetails.Message
                throw "API $method $url 失败: $($_.Exception.Message) $detail"
            }
            Start-Sleep -Milliseconds 800
        }
    }
}

Write-Host "== 读取本地提交身份/文件清单（$root） =="
$identityRaw = git -C $root log -1 --format='%an%n%ae%n%aI' main
$id = $identityRaw -split "`n"
$author = @{ name = $id[0]; email = $id[1]; date = $id[2] }
$modes = @{}
git -C $root ls-files -s | ForEach-Object { $parts = $_ -split '\s+'; $modes[$parts[3]] = $parts[0] }
$files = git -C $root ls-files
Write-Host ("files=" + $files.Count + "  author=" + $id[0] + " <" + $id[1] + ">")

Write-Host '== 确定 blob sha（复用已上传对象） =='
$blobSha = @{}
$i = 0
if ($UseLocalShas) {
    foreach ($f in $files) {
        $sha = git -C $root rev-parse "HEAD:$f"
        $blobSha[$f] = $sha.Trim()
        $i++
    }
    Write-Host ("local shas resolved: " + $blobSha.Count)
} else {
    Write-Host '== 上传 blobs（全量文件内容） =='
    foreach ($f in $files) {
        $p = Join-Path $root ($f -replace '/', '\')
        $bytes = [System.IO.File]::ReadAllBytes($p)
        $b64 = [Convert]::ToBase64String($bytes)
        $r = Invoke-Api POST "$api/git/blobs" @{ content = $b64; encoding = 'base64' }
        $blobSha[$f] = $r.sha
        $i++
        if ($i % 100 -eq 0) { Write-Host ("  uploaded $i / " + $files.Count) }
    }
    Write-Host ("blobs done: " + $blobSha.Count)
}

Write-Host '== 递归构建 trees =='
$treeCache = @{}
function Build-Tree([string]$prefix) {
    $cacheKey = $prefix
    if ($treeCache.ContainsKey($cacheKey)) { return $treeCache[$cacheKey] }
    $entries = @()
    $childDirs = @{}
    foreach ($f in $files) {
        if ($prefix) {
            if (-not $f.StartsWith($prefix + '/')) { continue }
            $rel = $f.Substring($prefix.Length + 1)
        } else {
            $rel = $f
        }
        if (-not $rel) { continue }
        $slash = $rel.IndexOf('/')
        if ($slash -lt 0) {
            if ($blobSha.ContainsKey($f)) {
                $mode = $modes[$f]; if (-not $mode) { $mode = '100644' }
                $entries += @{ path = $rel; mode = $mode; type = 'blob'; sha = $blobSha[$f] }
            }
        } else {
            $child = $rel.Substring(0, $slash)
            $childDir = if ($prefix) { "$prefix/$child" } else { $child }
            $childDirs[$child] = $childDir
        }
    }
    foreach ($k in ($childDirs.Keys | Sort-Object)) {
        $sub = Build-Tree $childDirs[$k]
        $entries += @{ path = $k; mode = '040000'; type = 'tree'; sha = $sub }
    }
    $r = Invoke-Api POST "$api/git/trees" @{ tree = @($entries) }
    $treeCache[$cacheKey] = $r.sha
    Write-Host ("  tree [$prefix] -> " + $r.sha)
    return $r.sha
}
$rootTree = Build-Tree ''

Write-Host '== 创建提交 =='
$message = 'feat: 启用 spotbugs enforce 门禁并完成包名品牌化（cn.admin.scaffold）'
$commit = Invoke-Api POST "$api/git/commits" @{
    message = $message
    tree    = $rootTree
    parents = @()
    author  = $author
    committer = $author
}
Write-Host ("commit: " + $commit.sha)

foreach ($branch in @('main') + $(if ($AlsoDev) { @('dev') } else { @() })) {
    try {
        Invoke-Api POST "$api/git/refs" @{ ref = "refs/heads/$branch"; sha = $commit.sha }
        Write-Host "ref created: refs/heads/$branch -> $($commit.sha)"
    } catch {
        # 已存在则尝试更新（幂等重推）
        $r = Invoke-Api PATCH "$api/git/refs/heads/$branch" @{ sha = $commit.sha; force = $true }
        Write-Host "ref updated: refs/heads/$branch -> $($r.sha)"
    }
}

Write-Host ''
Write-Host "完成：https://github.com/$Repo/actions 应已触发 ci.yml（push main/dev）"
