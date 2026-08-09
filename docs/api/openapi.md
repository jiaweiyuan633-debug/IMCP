# OpenAPI 契约

后端通过 springdoc 自动生成 OpenAPI 文档。

- 在线文档：`http://localhost:8080/doc.html`
- JSON 契约：`http://localhost:8080/v3/api-docs`

刷新本地契约：

```powershell
scripts/fetch-openapi.ps1
```

CI 会在每次提交时执行后端测试、前端测试与构建、AI 测试与规范检查，确保接口与代码保持一致。后续可基于 OpenAPI JSON 自动生成前端类型。

