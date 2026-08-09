# OpenAPI 契约

Java 后端通过 springdoc + Knife4j 自动生成 OpenAPI 文档，覆盖认证、系统管理、监控、AI 与通用文件接口。

- 在线文档：`http://localhost:8080/doc.html`
- JSON 契约：`http://localhost:8080/v3/api-docs`
- Python 服务文档：`http://localhost:8000/docs`

刷新本地契约：

```powershell
scripts/fetch-openapi.ps1
```

CI 会在每次提交时执行后端测试与覆盖率门槛、前端 lint/测试/构建、AI 测试与规范检查，确保接口与代码保持一致。OpenAPI JSON 可作为前端类型生成、接口测试与契约回归的输入。

