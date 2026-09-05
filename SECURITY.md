# 安全策略

本仓库是一个企业级全栈脚手架。我们感谢安全研究者的负责任的漏洞披露。

## 报告漏洞

**请勿在公开 issue 中报告漏洞。**

请通过私有渠道报告，将详细信息发送至项目维护者：

- 邮件：[请替换为维护者邮箱]
- 或使用 GitHub 的 [Private vulnerability reporting](https://docs.github.com/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability) 功能

请在报告中包含：

- 漏洞类型与影响范围
- 复现步骤（尽量最小化）
- 受影响的版本/分支
- 建议的修复方式（可选）

## 安全承诺

- 收到报告后 **5 个工作日内** 确认收悉，并给出初步评估时间线。
- 修复将在私有分支进行，并随版本发布统一公开。
- 在修复发布前，我们会与报告者协调披露时间。

## 安全基线（本脚手架已内置）

- 生产环境密钥缺失即启动失败（fail-fast），代码中不存在可用的生产凭据默认值。
- 依赖仓库扫描（gitleaks）与漏洞扫描（Trivy/CodeQL）已在 CI 强制开启。
- 新增接口默认需要认证与授权；详情见 [docs/architecture-conventions.md](docs/architecture-conventions.md)。
