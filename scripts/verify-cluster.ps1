<#
.SYNOPSIS
    上线前目标集群预检脚本（Go/No-Go 第七节 B 组·部署 · 半自动部分）
    配套文档：docs/deploy/cluster-go-nogo.md

.DESCRIPTION
    只做"无需人工填接收人 / 无需触发真实告警"的可判读检查，逐项输出：
        [PASS] 检查名 —— 说明
        [FAIL] 检查名 —— 说明   （任一 FAIL → 脚本退出码 1）
    覆盖（对应 cluster-go-nogo.md 章节）：
        1. kubectl 可用且能连集群（cluster-info）
        2. metrics-server Deployment Ready + `kubectl top nodes` 有输出（§2.1）
        3. RWX 存储能力：期望名存储类存在（-RWXName）+ 集群内已存在 Bound 且
           accessModes 含 ReadWriteMany 的 PVC 作为实证（§2.3）
           —— 注：StorageClass 资源本身无 accessModes 字段（accessModes 是
           PVC/卷的属性），无法"遍历 sc 取 accessModes"；RWX 能力以
           "已绑定 RWX PVC" 或探测 PVC（-ProbeRWX）实证，与文档口径一致。
        4. ingress-nginx controller Deployment Ready（§2.2）
        5. cert-manager 三个 Deployment Ready；CRD 存在时至少一个
           Issuer/ClusterIssuer Ready（§2.4）
        6. monitoring 命名空间 Prometheus/Grafana/Alertmanager 部署状态
           （可选 -SkipMonitor 跳过；组件名按 Deployment/StatefulSet 精确匹配，
            kube-prometheus-stack 等非默认部署请用 -MonitoringDeployments 传实际名）
        7. ArgoCD Application（默认 argocd/admin-scaffold）存在则报
           health/sync 状态（不存在不算 FAIL，仅提示）
    kubectl 输出以 jsonpath/-o json（ConvertFrom-Json）解析，不依赖第三方模块；
    兼容 PowerShell 5.1。本脚本只读集群（-ProbeRWX 除外：临时探测 PVC 后即删）。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/verify-cluster.ps1
    powershell -ExecutionPolicy Bypass -File scripts/verify-cluster.ps1 -KubeContext prod -Namespace admin-scaffold -RWXName efs-sc
    powershell -ExecutionPolicy Bypass -File scripts/verify-cluster.ps1 -SkipMonitor -RWXName nfs-client -ProbeRWX
#>
param(
    [string]$KubeContext = '',                       # 可选：kubectl --context
    [string]$Namespace = 'admin-scaffold',           # 业务命名空间（Chart dest namespace）
    [string]$ReleaseName = 'admin-scaffold',         # Helm release 名（上传 PVC 名 = {release}-upload）
    [string]$RWXName = '',                           # 可选：期望的 RWX 存储类名
    [switch]$ProbeRWX,                               # 可选：RWXName 无实证时创建探测 PVC（创建后即删）
    [switch]$SkipMonitor,                            # 可选：跳过 monitoring 组件检查
    [string]$MetricsServerNamespace = 'kube-system',
    [string]$IngressNginxNamespace = 'ingress-nginx',
    [string]$IngressNginxDeployment = 'ingress-nginx-controller',
    [string]$CertManagerNamespace = 'cert-manager',
    [string]$MonitoringNamespace = 'monitoring',
    [string]$MonitoringDeployments = 'grafana,prometheus,alertmanager',  # 逗号分隔精确名
    [string]$ArgoCDNamespace = 'argocd',
    [string]$ArgoCDApplication = 'admin-scaffold'
)

$script:KubeContext = $KubeContext

# ---------- 全局状态 ----------
$script:KubectlExe = $null
$script:KubectlOutput = ''
$script:KubectlExit = 0
$script:PassCount = 0
$script:FailCount = 0
$script:InfoCount = 0
$script:ClusterOk = $false

function Write-Pass { param([string]$Name, [string]$Detail) $script:PassCount++; Write-Host "[PASS] $Name —— $Detail" }
function Write-Fail { param([string]$Name, [string]$Detail) $script:FailCount++; Write-Host "[FAIL] $Name —— $Detail" }
function Write-Info { param([string]$Name, [string]$Detail) $script:InfoCount++; Write-Host "[INFO] $Name —— $Detail" }

# kubectl 执行器：所有输出落到 $script:KubectlOutput，退出码到 $script:KubectlExit。
# -StdoutOnly：只取 stdout（stderr 丢弃，用于预期可能失败的探测）
# -Quiet    ：只取 stdout 且 stderr 重定向空设备（用于"存在性"探测，避免红字噪音）
function Invoke-Kubectl {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$StdoutOnly,
        [switch]$Quiet
    )
    $cmd = @($script:KubectlExe)
    if ($script:KubeContext) { $cmd += '--context'; $cmd += $script:KubeContext }
    $cmd += $Arguments
    if ($Quiet) {
        $script:KubectlOutput = ((& $cmd 2>$null) -join "`n")
    } elseif ($StdoutOnly) {
        $script:KubectlOutput = ((& $cmd) -join "`n")
    } else {
        $script:KubectlOutput = (& $cmd 2>&1 | Out-String)
    }
    $script:KubectlExit = $LASTEXITCODE
    return ($script:KubectlExit -eq 0)
}

# 查单个 Deployment/StatefulSet 是否 Ready（name 精确匹配）
function Get-WorkloadReady {
    param([string]$Kind, [string]$Name, [string]$Ns)
    $ok = Invoke-Kubectl -Arguments @('get', $Kind, $Name, '-n', $Ns, '-o', 'json') -Quiet
    if (-not $ok) { return @{ Exists = $false; Ready = $false; Detail = "未找到 $Kind/$Name（ns=$Ns）" } }
    try {
        $obj = $script:KubectlOutput | ConvertFrom-Json
        $spec = 0; $ready = 0
        if ($obj.spec.replicas) { $spec = [int]$obj.spec.replicas }
        if ($obj.status.readyReplicas) { $ready = [int]$obj.status.readyReplicas }
        $isReady = ($spec -gt 0 -and $ready -ge $spec)
        return @{ Exists = $true; Ready = $isReady; Detail = "$Ns/$Name Ready=$ready/$spec" }
    } catch {
        return @{ Exists = $true; Ready = $false; Detail = "$Ns/$Name 状态解析失败：$($_.Exception.Message)" }
    }
}

function Get-NamesOfKind {
    # 返回某命名空间下某 kind 的全部名称数组；失败返回 $null
    param([string]$Kind, [string]$Ns)
    $ok = Invoke-Kubectl -Arguments @('get', $Kind, '-n', $Ns, '-o', 'json') -Quiet
    if (-not $ok) { return $null }
    try {
        $obj = $script:KubectlOutput | ConvertFrom-Json
        return @($obj.items | ForEach-Object { $_.metadata.name })
    } catch { return $null }
}

function Get-RWXEvidence {
    # 返回集群内第一条 "Bound 且 accessModes 含 ReadWriteMany" 的 PVC 信息；
    # 无则 $null。可选按存储类过滤。
    param([string]$StorageClassName = '')
    $ok = Invoke-Kubectl -Arguments @('get', 'pvc', '-A', '-o', 'json') -Quiet
    if (-not $ok) { return $null }
    try {
        $all = $script:KubectlOutput | ConvertFrom-Json
        foreach ($p in @($all.items)) {
            $modes = @($p.spec.accessModes)
            $sc = if ($p.spec.storageClassName) { [string]$p.spec.storageClassName } else { '(default)' }
            if ($StorageClassName -and $sc -ne $StorageClassName) { continue }
            if (($p.status.phase -eq 'Bound') -and ($modes -contains 'ReadWriteMany')) {
                return @{ Namespace = $p.metadata.namespace; Name = $p.metadata.name; SC = $sc }
            }
        }
    } catch { }
    return $null
}

# =====================================================================
Write-Host '== 目标集群预检（Go/No-Go B 组·部署 · 半自动）=='
Write-Host ''

# ---------- 0. kubectl 可用性 ----------
$cmd = Get-Command kubectl -ErrorAction SilentlyContinue
if ($null -eq $cmd) {
    Write-Fail 'kubectl 可用' '未找到 kubectl。请先安装 kubectl 并加入 PATH（或 kubectl.exe 所在目录）后重试。'
    Write-Host ''
    Write-Host ("verify-cluster result: PASS={0} FAIL={1} INFO={2}" -f $script:PassCount, $script:FailCount, $script:InfoCount)
    Write-Host '重试提示：安装 kubectl（https://kubernetes.io/docs/tasks/tools/）后重新执行本脚本。'
    exit 1
}
$script:KubectlExe = $cmd.Source
Write-Pass 'kubectl 可用' "找到 kubectl：$($cmd.Source)"

# ---------- 1. 集群可达 ----------
if (-not (Invoke-Kubectl -Arguments @('cluster-info'))) {
    Write-Fail '集群可达' 'kubectl cluster-info 失败——无法连接目标集群。请检查 --kubeconfig/当前 context（-KubeContext）与网络。'
    Write-Host ''
    Write-Host ("verify-cluster result: PASS={0} FAIL={1} INFO={2}" -f $script:PassCount, $script:FailCount, $script:InfoCount)
    Write-Host '重试提示：确认 kubectl config current-context 指向目标集群；集群 API 端点可达后再重跑。'
    exit 1
}
$script:ClusterOk = $true
Write-Pass '集群可达' 'kubectl cluster-info 成功（控制面与核心服务可访问）'

# ---------- 2. metrics-server（§2.1） ----------
$ms = Get-WorkloadReady -Kind 'deployment' -Name 'metrics-server' -Ns $MetricsServerNamespace
if (-not $ms.Exists) {
    Write-Fail 'metrics-server Deployment Ready' "未找到 deployment/metrics-server（ns=$MetricsServerNamespace）。HPA 依赖 metrics-server，四件套缺一即 No-Go。"
} elseif ($ms.Ready) {
    Write-Pass 'metrics-server Deployment Ready' $ms.Detail
} else {
    Write-Fail 'metrics-server Deployment Ready' "$($ms.Detail) —— 未就绪，查看 Pod 日志（常见：内网 CA 需 --kubelet-insecure-tls）。"
}

if ($script:ClusterOk) {
    if (Invoke-Kubectl -Arguments @('top', 'nodes') -StdoutOnly) {
        $lines = @($script:KubectlOutput -split "`r?`n" | Where-Object { $_.Trim() })
        $nodeCount = [Math]::Max(0, $lines.Count - 1)   # 去掉表头行
        Write-Pass 'kubectl top nodes 有输出' "metrics-server 实际服务正常，top nodes 返回 $nodeCount 个节点数据"
    } else {
        Write-Fail 'kubectl top nodes 有输出' 'top nodes 无输出/报错——metrics-server 未真正服务（Deployment Ready 不等于可采集）。检查 metrics-server 日志与 kubelet 证书。'
    }
}

# ---------- 3. RWX 存储能力（§2.3） ----------
$rwxEvidence = Get-RWXEvidence
$scOk = $true
if ($RWXName) {
    # 期望名存储类存在性检查
    if (Invoke-Kubectl -Arguments @('get', 'sc', $RWXName) -Quiet) {
        Write-Pass "RWX 存储类存在（$RWXName）" "storageclass/$RWXName 存在"
    } else {
        $scOk = $false
        Write-Fail "RWX 存储类存在（$RWXName）" "storageclass/$RWXName 未找到。运行 'kubectl get sc' 列出候选类；按云厂商创建 RWX 动态供给 SC（EFS/NFS/CephFS）后重试。"
    }
}
$evidenceForName = $null
if ($RWXName -and $scOk) { $evidenceForName = Get-RWXEvidence -StorageClassName $RWXName }

if ($RWXName -and $scOk -and $evidenceForName) {
    Write-Pass 'RWX 存储能力实证' "存储类 $RWXName 已有 Bound+RWX PVC：$($evidenceForName.Namespace)/$($evidenceForName.Name)"
} elseif (-not $RWXName -and $rwxEvidence) {
    Write-Pass 'RWX 存储能力实证' "集群存在 Bound+RWX PVC：$($rwxEvidence.Namespace)/$($rwxEvidence.Name)（SC=$($rwxEvidence.SC)）。生产上传卷须用 RWX 类（Chart storage.className）"
} elseif ($ProbeRWX -and $RWXName) {
    # 可选：探测 PVC 实证 RWX（默认不写集群，探测后即删）
    $probe = "$ReleaseName-rwx-probe"
    $probeYaml = @"
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: $probe
  namespace: $Namespace
spec:
  accessModes: ["ReadWriteMany"]
  storageClassName: $RWXName
  resources:
    requests:
      storage: 1Gi
"@
    $applied = $probeYaml | & $script:KubectlExe apply -f - 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Fail 'RWX 存储能力实证' "探测 PVC 创建失败（$Namespace/$probe on $RWXName），请人工创建后 kubectl describe pvc 查看事件。"
    } else {
        $bound = $false
        for ($i = 0; $i -lt 30; $i++) {
            Start-Sleep -Seconds 2
            $p = Get-WorkloadReady -Kind 'pvc' -Name $probe -Ns $Namespace
            if ($p.Exists -and (Invoke-Kubectl -Arguments @('get', 'pvc', $probe, '-n', $Namespace, '-o', 'jsonpath={.status.phase}') -Quiet) -and $script:KubectlOutput.Trim() -eq 'Bound') { $bound = $true; break }
        }
        & $script:KubectlExe delete pvc $probe -n $Namespace --ignore-not-found 2>$null | Out-Null
        if ($bound) { Write-Pass 'RWX 存储能力实证' "探测 PVC Bound 成功：$RWXName 可供给 ReadWriteMany" }
        else { Write-Fail 'RWX 存储能力实证' "探测 PVC 30s 内未 Bound（$RWXName），该存储类可能不支持 RWX；kubectl describe pvc $probe 查看卷供给事件。" }
    }
} else {
    $hint = '运行 "kubectl get pvc -A | grep -i bound" 并核对 accessModes=ReadWriteMany；或按 docs/deploy/cluster-go-nogo.md §2.3 用探测 PVC 实证。Chart 默认 storage.className 为空（默认类），多副本上传卷必须为 RWX。'
    if ($RWXName) {
        Write-Fail 'RWX 存储能力实证' "存储类 $RWXName 存在但暂无 Bound+RWX PVC 证据（SC 无 accessModes 字段，不能仅凭 SC 判定 RWX）。$hint （可加 -ProbeRWX 自动探测）"
    } else {
        Write-Fail 'RWX 存储能力实证' "集群内未发现 Bound 且 accessModes=ReadWriteMany 的 PVC，无法确认 RWX 能力。$hint"
    }
}

# ---------- 4. ingress-nginx controller（§2.2） ----------
$ic = Get-WorkloadReady -Kind 'deployment' -Name $IngressNginxDeployment -Ns $IngressNginxNamespace
if (-not $ic.Exists) {
    Write-Fail 'ingress-nginx controller Ready' "未找到 deployment/$IngressNginxDeployment（ns=$IngressNginxNamespace）。若控制器名/命名空间不同，用 -IngressNginxDeployment/-IngressNginxNamespace 覆盖；缺失时四件套缺一即 No-Go。"
} elseif ($ic.Ready) {
    Write-Pass 'ingress-nginx controller Ready' $ic.Detail
} else {
    Write-Fail 'ingress-nginx controller Ready' "$($ic.Detail) —— kubectl describe deployment $IngressNginxDeployment -n $IngressNginxNamespace 查看就绪事件。"
}

# ---------- 5. cert-manager（§2.4） ----------
$certFound = $false
foreach ($dn in @('cert-manager', 'cert-manager-cainjector', 'cert-manager-webhook')) {
    $w = Get-WorkloadReady -Kind 'deployment' -Name $dn -Ns $CertManagerNamespace
    if (-not $w.Exists) { Write-Fail "cert-manager Deployment Ready（$dn）" "未找到 deployment/$dn（ns=$CertManagerNamespace）" }
    elseif ($w.Ready) { $certFound = $true; Write-Pass "cert-manager Deployment Ready（$dn）" $w.Detail }
    else { Write-Fail "cert-manager Deployment Ready（$dn）" "$($w.Detail) —— 未就绪" }
}
if (-not $certFound) {
    Write-Fail 'cert-manager 三件套' 'cert-manager 任一 Deployment 未就绪——ingress.tls.enabled=true 时 TLS 自动签发不可用，四件套缺一即 No-Go。'
}

# Issuer/ClusterIssuer Ready（CRD 存在才查）
if (Invoke-Kubectl -Arguments @('get', 'crd', 'clusterissuers.cert-manager.io') -Quiet) {
    $issuerReady = $false
    $issuerNames = @()
    if (Invoke-Kubectl -Arguments @('get', 'clusterissuer', '-o', 'json') -Quiet) {
        try {
            $all = $script:KubectlOutput | ConvertFrom-Json
            foreach ($ci in @($all.items)) {
                $issuerNames += $ci.metadata.name
                $conds = @($ci.status.conditions)
                foreach ($c in $conds) {
                    if ($c.type -eq 'Ready' -and $c.status -eq 'True') { $issuerReady = $true }
                }
            }
        } catch { }
    }
    if (Invoke-Kubectl -Arguments @('get', 'issuer', '-A', '-o', 'json') -Quiet) {
        try {
            $all = $script:KubectlOutput | ConvertFrom-Json
            foreach ($i in @($all.items)) {
                $issuerNames += "$($i.metadata.namespace)/$($i.metadata.name)"
                $conds = @($i.status.conditions)
                foreach ($c in $conds) {
                    if ($c.type -eq 'Ready' -and $c.status -eq 'True') { $issuerReady = $true }
                }
            }
        } catch { }
    }
    if ($issuerReady) {
        Write-Pass 'Issuer/ClusterIssuer Ready' '存在 READY=True 的 issuer/clusterissuer（Ingress 的 cert-manager.io/cluster-issuer 注解应填其名）'
    } else {
        $list = if ($issuerNames.Count) { ('已发现：' + ($issuerNames -join ', ')) } else { '未发现任何 issuer/clusterissuer' }
        Write-Fail 'Issuer/ClusterIssuer Ready' "$list。至少需要一个 READY=True 的 ClusterIssuer（如 letsencrypt-prod/ca-issuer），否则 TLS 证书签不出来。kubectl describe clusterissuer <名> 看原因。"
    }
} else {
    Write-Info 'Issuer/ClusterIssuer' 'CRD clusterissuers.cert-manager.io 不存在——cert-manager 未正常安装或 installCRDs=false，按 §2.4 处理。'
}

# ---------- 6. monitoring 组件（可选跳过） ----------
if (-not $SkipMonitor) {
    $depNames = Get-NamesOfKind -Kind 'deployment' -Ns $MonitoringNamespace
    $stsNames = Get-NamesOfKind -Kind 'statefulset' -Ns $MonitoringNamespace
    if ($null -eq $depNames -and $null -eq $stsNames) {
        Write-Fail 'monitoring 组件' "命名空间 $MonitoringNamespace 不存在或无权限访问——Prometheus/Grafana/Alertmanager 未部署（仓库只含配置与 Grafana 清单，服务端需预先部署）。可用 -SkipMonitor 跳过本组。"
    } else {
        $allNames = @()
        if ($depNames) { $allNames += $depNames }
        if ($stsNames) { $allNames += $stsNames }
        foreach ($comp in ($MonitoringDeployments -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
            if ($allNames -contains $comp) {
                # Deployment 优先，其次 StatefulSet
                $w = Get-WorkloadReady -Kind 'deployment' -Name $comp -Ns $MonitoringNamespace
                if (-not $w.Exists) { $w = Get-WorkloadReady -Kind 'statefulset' -Name $comp -Ns $MonitoringNamespace }
                if ($w.Ready) { Write-Pass "monitoring 组件 Ready（$comp）" $w.Detail }
                else { Write-Fail "monitoring 组件 Ready（$comp）" "$($w.Detail) —— 未就绪；kube-prometheus-stack 等部署的 Pod/Service 名不同，可用 -MonitoringDeployments 传实际名。" }
            } else {
                $foundList = ($allNames -join ', ')
                Write-Fail "monitoring 组件 Ready（$comp）" "在 $MonitoringNamespace 未找到名为 $comp 的 Deployment/StatefulSet（现含：$foundList）。Prometheus 抓取与告警依赖其就绪；-SkipMonitor 可跳过。"
            }
        }
    }
} else {
    Write-Info 'monitoring 组件' '-SkipMonitor 指定，跳过 Prometheus/Grafana/Alertmanager 检查'
}

# ---------- 7. ArgoCD Application（存在才判定，不存在仅提示） ----------
if (Invoke-Kubectl -Arguments @('get', 'crd', 'applications.argoproj.io') -Quiet) {
    if (Invoke-Kubectl -Arguments @('get', 'application', $ArgoCDApplication, '-n', $ArgoCDNamespace, '-o', 'json') -Quiet) {
        try {
            $app = $script:KubectlOutput | ConvertFrom-Json
            $health = $app.status.health.status
            $sync = $app.status.sync.status
            $healthy = ($health -eq 'Healthy')
            $synced = ($sync -eq 'Synced')
            $detail = "Application $ArgoCDNamespace/$ArgoCDApplication health=$health sync=$sync"
            if ($healthy -and $synced) { Write-Pass 'ArgoCD Application 健康/同步' $detail }
            else { Write-Fail 'ArgoCD Application 健康/同步' "$detail —— 非 Healthy/Synced；查看 .status.operationState.message（密钥未注入时同步会因 Chart fail-fast 失败，见 docs/deploy/cluster-go-nogo.md §6）。" }
        } catch {
            Write-Info 'ArgoCD Application' "解析 Application 状态失败（$($_.Exception.Message)），请人工 kubectl get application $ArgoCDApplication -n $ArgoCDNamespace -o wide 确认"
        }
    } else {
        Write-Info 'ArgoCD Application' "未创建/未找到 Application $ArgoCDApplication（ns=$ArgoCDNamespace）——不存在不算 FAIL，仅提示：GitOps 未启用时此项不构成 B7 证据。"
    }
} else {
    Write-Info 'ArgoCD Application' 'CRD applications.argoproj.io 不存在（ArgoCD 未安装）——不存在不算 FAIL，仅提示。'
}

# ---------- 汇总 ----------
Write-Host ''
Write-Host ("verify-cluster result: PASS={0} FAIL={1} INFO={2}" -f $script:PassCount, $script:FailCount, $script:InfoCount)
if ($script:FailCount -gt 0) {
    Write-Host '判定：存在 FAIL → No-Go（先整改再复检）。'
    Write-Host '重试提示：按上方 [FAIL] 说明与 docs/deploy/cluster-go-nogo.md §2-§6 处置后重新执行本脚本（默认只读集群可重复运行；-ProbeRWX 会临时创建并删除探测 PVC）。'
    exit 1
}
Write-Host '判定：全部通过 → 可继续人工项验收（Ingress 路由/TLS 实测、Prometheus 抓取 200、Alertmanager 真实接收器、备份链路、ESO 密钥），见 docs/deploy/cluster-go-nogo.md。'
exit 0
