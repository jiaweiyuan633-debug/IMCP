# 品牌与命名说明

本文说明本仓库的命名基线（项目代号、模块与包命名、展示名），以及把占位基座一键
换成企业正式基座的方法与核对清单。改动任何品牌点时，按文末**检查清单**逐项过一遍，
避免只改一处留下不一致。

## 项目代号与当前基线

仓库代号 **admin-scaffold**（中文展示名“智能管理平台”，见根目录 `README.md`）。
Java 后端包基座为中性占位 **`cn.admin.scaffold`**：占位是刻意的，确保首批业务代码
落在中性命名空间而不是 `com.example` 之类废弃段；企业域名确定后按「一键换到企业
基座」替换，替换前不影响任何开发与交付流程。

| 项 | 当前值 | 位置 |
| --- | --- | --- |
| Java 包基座 | `cn.admin.scaffold` | `backend/src/main|test/java/cn/admin/scaffold/**` |
| Maven groupId / artifactId | `cn.admin.scaffold` / `admin-backend` | `backend/pom.xml` |
| Helm Chart 名 / 默认 release / 命名空间 | `admin-scaffold` | `k8s/helm/admin-scaffold/Chart.yaml`、values |
| 镜像名 | `admin-backend` / `ai-service` / `frontend` / `website` | `k8s/helm/admin-scaffold/values.yaml` |
| docker compose 项目名 | `admin-scaffold` | `docker-compose.yml` |
| MySQL 库名 | `admin_scaffold`（dev 默认；test 为 `admin_scaffold_test`） | `application-dev.yml` / `docker-compose.yml` / Chart values |
| AI 服务名 | `ai-service` | `ai-service/pyproject.toml` |
| CRUD 生成器包基座 | `cn.admin.scaffold.module`（`BASE_PACKAGE`） | `scripts/crud-gen/crud_gen.py` |
| SpotBugs 豁免白名单 | 含 `cn.admin.scaffold.*` 类路径条目 | `backend/spotbugs-exclude.xml` |
| 管理端 npm 包名 | `admin-frontend` | `frontend/package.json` |
| 官网 npm 包名 | `y15-platform-website`（与基线命名风格不一致的**历史残留示例**） | `website/package.json` |
| 管理端页面标题 | 智能管理平台 | `frontend/index.html` |
| 官网页面标题 | 智能管理平台 - 企业智能管理官网 | `website/index.html` |

> 上表只是“当前基线快照”，可能随重命名漂移；以各文件实际内容为最终事实源。

## 一键换到企业基座

`scripts/rename-package.ps1` 把仓库内 Java 包名（目录 + 文本引用）从 `-FromBase`
批量替换为 `-ToBase`：

```powershell
# 目标基座须为合法 Java 包，如 com.acme.admin
powershell -ExecutionPolicy Bypass -File scripts/rename-package.ps1 `
    -FromBase cn.admin.scaffold -ToBase com.acme.admin

# 先看影响面（不落盘）：只报告将改写的文件/目录
powershell -ExecutionPolicy Bypass -File scripts/rename-package.ps1 `
    -FromBase cn.admin.scaffold -ToBase com.acme.admin -DryRun
```

脚本只处理文本类扩展名文件，自动跳过 `node_modules` / `.git` / `target` / `dist`
等目录。执行后**人工核对**：

1. `git grep -n "cn.admin.scaffold"` 的剩余命中应只剩本说明这类“白名单”位置
   （如本文档自身、评审类历史文档），业务代码零残留；同样再
   `git grep -n "com.example"` 确认无遗漏；
2. `backend/pom.xml` 的 groupId、`scripts/crud-gen/crud_gen.py` 的 `BASE_PACKAGE`、
   `backend/spotbugs-exclude.xml` 中的类路径条目已随之替换；
3. 跑全量回归：`cd backend; mvn clean verify` + `scripts/smoke.ps1`；
4. 按下方检查清单处理非 Java 品牌点。

## 品牌点检查清单

更换域名/品牌时逐项核对（不只是 Java 包名）：

- [ ] **Java 包基座**与 Maven groupId（`backend/pom.xml`），以及测试目录包结构
- [ ] CRUD 生成器 `BASE_PACKAGE`（`scripts/crud-gen/crud_gen.py`）与新生成模块落点
- [ ] SpotBugs 豁免白名单类路径（`backend/spotbugs-exclude.xml`）
- [ ] 镜像名 / Chart 名 / Helm release / 命名空间 / docker compose 项目名 / K8s
      label 与 `gitops/argocd` 清单
- [ ] MySQL 库名与连接串（`application.yml`、compose、Chart values）、Flyway 无关
- [ ] **npm 包名**：`frontend/package.json` 与 `website/package.json` 的 `name`，
      以及任何依赖该名字的脚本/引用（当前 `website/package.json` 的
      `y15-platform-website` 与基线不一致，属历史残留，应随本次归一）
- [ ] **页面品牌**：`frontend/index.html`、`website/index.html` 的 `title` 与
      `<meta name="description">`；PWA 图标 / manifest（`frontend|website/public`
      资源）与站点图标
- [ ] **官网文案与联系方式**：官网页面文案、联系方式/线索端点由配置驱动（未配置时
      演示模式），检查默认值与示例域名（如 `admin.example.com`）类占位符
- [ ] README 与 `docs/` 中出现的项目代号、展示名与示例地址
- [ ] 全仓搜旧代号：`git grep -ni "<旧代号>"`（大小写不敏感），逐一确认是“引用
      基线本身”还是“需要替换”

## 注意点

- 占位 `cn.admin.scaffold`、示例域名与默认口令类配置属于**开发基线占位**，不代表
  最终交付品牌；上线前按上述清单统一替换并全量验证。
- 命名切换是一次性动作：先 `-DryRun` 看影响面，替换后跑完整回归
  （`mvn verify`、前端/官网构建、冒烟），不要在后续迭代中混用两套基座。
- 若企业域名未定，保持当前占位即可继续开发，无需等域名；除 Java 包基座外，其余
  品牌点（页面 title/description、图标、官网文案等）相互独立，可单独先行落地。
- 修改命名后，新生成的 CRUD 模块、文档示例中的类路径与包名都要同步更新，
  保持文档与代码一致（文档规约见 `docs/architecture-conventions.md`）。
