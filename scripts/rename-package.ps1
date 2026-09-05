# 包名品牌化一键替换
#
# 背景：仓库后端 Java 包基线现为中性占位 cn.admin.scaffold（由原 com.example.admin 迁移而来）。
# 企业域名确定后，用本脚本一次性把包名/groupId/生成器基线替换为目标域名并移动目录。
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File scripts/rename-package.ps1 `
#       -FromBase cn.admin.scaffold -ToBase com.acme.admin        # 目标基座（须为合法 Java 包）
#   powershell -ExecutionPolicy Bypass -File scripts/rename-package.ps1 `
#       -FromBase cn.admin.scaffold -ToBase com.acme.admin -DryRun  # 只报告将改的文件/目录
#
# 说明：
#   - 只处理文本扩展名文件；跳过 node_modules/.git/target/dist/__pycache__/.pnpm-store/logs 等。
#   - 执行后请人工复核 docs/（release-review-2025.md 为历史只读评审记录，通常不改写）
#     与任何含旧包名字符串的散落引用，再跑后端全量 mvn verify。
param(
    [Parameter(Mandatory = $true)][string]$FromBase,
    [Parameter(Mandatory = $true)][string]$ToBase,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$fromSeg = $FromBase.Split('.')
$toSeg = $ToBase.Split('.')
if ($fromSeg.Length -lt 2 -or $toSeg.Length -lt 2) {
    throw 'FromBase/ToBase 至少两段，如 cn.admin.scaffold'
}

$textExt = @('.java','.xml','.yml','.yaml','.properties','.py','.tpl','.json','.md','.sql','.txt','.ps1','.html','.js','.ts','.vue','.gradle','.factories','.imports')
$skipDirNames = @('node_modules','.git','target','dist','.pnpm-store','__pycache__','logs','.mvn')

function Test-TextFile([string]$path) {
    return $textExt.Contains([System.IO.Path]::GetExtension($path).ToLowerInvariant())
}

Write-Host "== 扫描文本引用（$FromBase -> $ToBase） =="
$textFiles = Get-ChildItem -Path $root -Recurse -File -ErrorAction SilentlyContinue | Where-Object {
    $rel = $_.FullName.Substring($root.Length).TrimStart('\')
    $first = $rel.Split('\')[0]
    $inSkip = $skipDirNames -contains $_.Directory.Name
    $parentSkip = ($_.Directory.FullName -split '\\') | Where-Object { $skipDirNames -contains $_ } | Select-Object -First 1
    (-not $parentSkip) -and (Test-TextFile $_.FullName) -and (-not $_.Name.EndsWith('.pyc'))
}
$changedText = @()
foreach ($f in $textFiles) {
    $content = [System.IO.File]::ReadAllText($f.FullName)
    if ($content.Contains($FromBase)) {
        $new = $content.Replace($FromBase, $ToBase)
        $changedText += $f.FullName.Substring($root.Length)
        if (-not $DryRun) { [System.IO.File]::WriteAllText($f.FullName, $new) }
    }
}
Write-Host ("文本引用文件数：{0}" -f $changedText.Count)
$changedText | ForEach-Object { Write-Host ("  " + $_) }

Write-Host "== 目录迁移（含旧包路径的目录树） =="
$dirs = Get-ChildItem -Path $root -Recurse -Directory -ErrorAction SilentlyContinue | Where-Object {
    $seg = $_.FullName.Substring($root.Length).TrimStart('\') -split '\\'
    for ($i = 0; $i -le $seg.Length - $fromSeg.Length; $i++) {
        $hit = $true
        for ($j = 0; $j -lt $fromSeg.Length; $j++) {
            if ($seg[$i + $j] -ne $fromSeg[$j]) { $hit = $false; break }
        }
        if ($hit) { return $true }
    }
    return $false
} | Sort-Object { $_.FullName.Length } -Descending

foreach ($d in $dirs) {
    $seg = $d.FullName.Substring($root.Length).TrimStart('\') -split '\\'
    $idx = -1
    for ($i = 0; $i -le $seg.Length - $fromSeg.Length; $i++) {
        $hit = $true
        for ($j = 0; $j -lt $fromSeg.Length; $j++) {
            if ($seg[$i + $j] -ne $fromSeg[$j]) { $hit = $false; break }
        }
        if ($hit) { $idx = $i; break }
    }
    if ($idx -lt 0) { continue }
    $newSeg = @()
    $newSeg += $seg[0..($idx - 1)]
    $newSeg += $toSeg
    $newSeg += $seg[($idx + $fromSeg.Length)..($seg.Length - 1)]
    $target = Join-Path $root ($newSeg -join '\')
    $targetParent = Split-Path -Parent $target
    if ($DryRun) {
        Write-Host ("  [move] {0}  ->  {1}" -f $d.FullName.Substring($root.Length), $target.Substring($root.Length))
    } elseif (-not (Test-Path $target)) {
        New-Item -ItemType Directory -Path $targetParent -Force | Out-Null
        Move-Item -Path $d.FullName -Destination $target
        Write-Host ("  [moved] {0}" -f $target.Substring($root.Length))
    }
}

if ($DryRun) {
    Write-Host "DryRun：未做任何修改。"
} else {
    Write-Host "== 残留扫描 =="
    $leftovers = Get-ChildItem -Path $root -Recurse -File -ErrorAction SilentlyContinue | Where-Object {
        $rel = $_.FullName.Substring($root.Length).TrimStart('\')
        $first = $rel.Split('\')[0]
        (-not ($skipDirNames -contains $first)) -and (-not $_.Name.EndsWith('.pyc')) -and (Test-TextFile $_.FullName)
    } | Where-Object { [System.IO.File]::ReadAllText($_.FullName).Contains($FromBase) }
    if ($leftovers) {
        Write-Host ("仍有 {0} 个文件残留旧包名（多为历史/说明性文本，人工确认）：" -f $leftovers.Count)
        $leftovers | ForEach-Object { Write-Host ("  " + $_.FullName.Substring($root.Length)) }
    } else {
        Write-Host '无残留。'
    }
    Write-Host '完成。请人工核对 docs 历史记录与散落引用后执行后端全量回归：cd backend; mvn clean verify'
}
