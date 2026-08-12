"""pytest 全局配置：注入测试用 AUTH_TOKEN。

config.py 模块级 ``settings = Settings()`` 在 import 时即实例化；R1-1.4 起
``auth_token`` 为必填字段（无默认值，未注入即启动失败 fail-fast）。conftest
在收集任何测试模块之前加载，此处先写入 AUTH_TOKEN 环境变量，保证所有
``Settings()``（含模块级全局实例与各测试内构造）都能拿到测试密钥。
"""

import os

os.environ.setdefault("AUTH_TOKEN", "test-ai-token")
