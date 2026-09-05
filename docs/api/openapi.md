# OpenAPI 契约：生成与使用

Java 后端通过 springdoc + Knife4j 自动生成 OpenAPI 文档，覆盖认证、系统管理、监控、
工作流、AI、文件等全部 `/api/**` 接口。**运行中的契约是唯一权威**，任何手写接口
清单都可能过期。

## 文档入口

- Java 后端在线文档：`http://localhost:8080/doc.html`（Knife4j 界面，dev 环境）
- Java 后端 JSON 契约：`http://localhost:8080/v3/api-docs`
- Python AI 服务（FastAPI 自动生成）：`http://localhost:8000/docs`

## 获取契约快照

`scripts/fetch-openapi.ps1` 从运行中的后端抓取 `/v3/api-docs`：

```powershell
# 默认：http://127.0.0.1:8080 → docs/api/openapi.json
scripts/fetch-openapi.ps1
# 指定地址与输出
scripts/fetch-openapi.ps1 -BaseUrl https://admin.example.com -Output docs/api/openapi.json
```

前置条件：后端已在本地（或目标地址）运行，且处于暴露 Knife4j/springdoc 的配置
（dev 环境默认开启；生产环境如需导出，建议在预发布环境执行）。

## 快照入库与使用

- 契约变化随代码提交：执行上述脚本后把 `docs/api/openapi.json` 一并提交，作为
  可 diff 的契约基线。
- 典型消费方：
  - **前端类型生成**：依据 JSON 生成请求/响应类型，避免手写 DTO 漂移；
  - **接口测试 / 契约回归**：以快照为输入跑自动生成的用例，或在集成测试中校验
    关键路径返回；
  - **评审**：Pull Request 中 diff `openapi.json` 快速判断是否引入破坏性变更。

## CI 门禁建议

- 现有 CI（`.github/workflows/ci.yml`）已覆盖后端 `mvn verify`（测试 + JaCoCo 覆盖率
  门槛）、前端 lint/test/build、AI 测试与规范检查，保证代码与测试一致；
- **可选的契约门禁**：增加一个 job，在测试环境拉起后端后执行
  `scripts/fetch-openapi.ps1`，并与已提交的 `docs/api/openapi.json` 做 diff——
  有差异即失败并提示“运行 fetch-openapi.ps1 后提交契约”。注意事项：
  - springdoc 输出可能含易变字段（版本号、示例值、服务器地址），比较前建议归一化
    （或只对关键路径做子集断言），否则会引入噪音 diff；
  - 破坏性变更（删路径/改必填字段）建议通过 diff 人工评审确认，再更新快照。

接口通用约定（认证、统一响应、错误码、分页、限流）见 [API 接口约定](./README.md)。
