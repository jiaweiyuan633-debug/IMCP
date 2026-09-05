## 变更说明

请简述本次改动解决的问题与主要方式。

## 关联

- Closes #（如有关联 issue）

## 改动范围

- [ ] backend（含数据库迁移：仅新增 V(n+1)，不修改已发布迁移）
- [ ] frontend / website
- [ ] ai-service
- [ ] k8s / gitops / docker / CI
- [ ] 文档（docs / README）

## 自测清单

- [ ] 后端：`cd backend && mvn verify`（或至少 `mvn -DskipTests compile`）
- [ ] 前端：`pnpm lint && pnpm exec vitest run --coverage && pnpm build`（website 用 `pnpm build`）
- [ ] AI 服务：`uv run pytest --locked && uv run ruff check .`
- [ ] 生成器改动：`scripts/crud-gen` 下 `python -m unittest discover -s tests`
- [ ] 涉及密钥/默认口令/端点/配置的改动已同步更新文档

## 评审注意事项

（可选：容易引入回归的地方、需要重点 review 的取舍）
