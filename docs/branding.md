# 品牌化基线说明（包名/命名占位）

> 状态：批次9（R4-1.56）已把后端 Java 包基座由 `com.example.admin` 迁移为中性占位
> `cn.admin.scaffold`，避免首批业务代码继续落在 `com.example`（报告 P3 项）。
> **企业域名确定后，用一条命令换到正式基座**（见下），此占位不进入最终交付物。

## 当前基线

| 项 | 值 | 位置 |
| --- | --- | --- |
| Java 包根 | `cn.admin.scaffold`（原 `com.example.admin`） | `backend/src/main|test/java/cn/admin/scaffold/**` |
| Maven groupId | `cn.admin.scaffold` | `backend/pom.xml` |
| artifactId / 镜像名 / Chart 名 | `admin-backend` 等（保持） | pom / docker / helm |
| CRUD 生成器包名 | `cn.admin.scaffold.module`（`scripts/crud-gen/crud_gen.py` 的 `BASE_PACKAGE`） | `scripts/crud-gen/` |
| SpotBugs 豁免白名单 | `backend/spotbugs-exclude.xml`（含 `cn.admin.scaffold` 类路径正则，换基座时一并全局替换即可） | backend |

## 一键换到企业域名

```powershell
# 假设最终基座为 com.acme.admin（替换目录/包名/gradle 等全部引用）
powershell -ExecutionPolicy Bypass -File scripts/rename-package.ps1 `
    -FromBase cn.admin.scaffold -ToBase com.acme.admin
# 先看影响面（不落盘）：
#   -DryRun
```

执行后人工核对：
1. `git grep -n "cn.admin.scaffold\|com.example"` 应只剩 `docs/release-review-2025.md`
   （历史只读评审记录，不改写）与 `docs/branding.md` 本说明；
2. `backend/pom.xml` groupId 已随之替换；
3. 跑全量回归：`cd backend; mvn clean verify`。

## 若希望"不留占位、直接等域名"的替代方案

包基座集中在本表所列少数声明点；在域名未定前仍可在这些点保留占位。
除 Java 包名外，报告 P3 还包含的其余品牌化点（管理端 `index.html` title/description、PWA 图标、
官网联系方式等）与本次后端包名迁移相互独立，按原计划在首批业务代码前另行落地。
