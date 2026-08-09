# OpenAPI 契约

后端通过 springdoc 自动生成 OpenAPI 文档：

- 在线文档：`http://localhost:8080/doc.html`
- JSON 契约：`http://localhost:8080/v3/api-docs`

刷新本地契约文件：

```powershell
scripts/fetch-openapi.ps1
```

前端类型建议后续基于该 JSON 生成，保持接口与页面类型同步。

