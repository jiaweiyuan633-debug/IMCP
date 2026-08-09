# Y15智能管理平台官网

面向外部客户与访客的官网，核心目标是产品展示、品牌认知与需求转化。默认运行在 `http://localhost:5174`。

## 本地运行

```bash
cd website
pnpm install
pnpm dev --port 5174
```

## 构建

```bash
pnpm build
```

构建产物输出到 `website/dist/`，Docker 镜像通过 `docker/Dockerfile.website` 构建，Nginx 配置见 `docker/nginx-website.conf`。

## 页面结构

- Hero：Y15智能管理平台主视觉与核心卖点
- 产品能力：组织权限、流程引擎、AI 编排、实时监控、多租户、工程基线
- 平台体验：后台管理系统界面与关键体验
- 解决方案：中小企业、集团多组织、服务商交付
- 定价与预约演示：标准版、专业版、旗舰版与需求表单
