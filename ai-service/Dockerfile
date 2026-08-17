# 多阶段构建：uv 解析依赖 + 精简运行镜像
# 运行用户固定为 10001（与 k8s securityContext.runAsUser 对齐）
FROM python:3.11-slim AS builder
WORKDIR /app
# 国内网络直连 PyPI 超时，改用清华镜像（pip 装 uv + uv 拉依赖均生效）
ENV PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple \
    UV_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
COPY pyproject.toml uv.lock ./
RUN pip install --no-cache-dir uv && uv sync --no-dev --extra parsing

FROM python:3.11-slim
WORKDIR /app
# 批次3（R4-1.49）：安装 tesseract-ocr（OCR 提供方依赖系统二进制）与解析所需
# 系统库（openpyxl 写 xlsx 不需额外系统包；python-docx/pypdf 为纯 Python）。
# 安装后清理 apt 缓存控制镜像体积；固定路径供非 root 用户访问。
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr \
    && rm -rf /var/lib/apt/lists/*
ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1
COPY --from=builder /app/.venv ./.venv
COPY app ./app
RUN useradd -r -u 10001 -g root app && chown -R app:root /app
USER app
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
