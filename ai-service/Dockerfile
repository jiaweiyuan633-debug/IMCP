# 多阶段构建：uv 解析依赖 + 精简运行镜像
# 运行用户固定为 10001（与 k8s securityContext.runAsUser 对齐）
FROM python:3.11-slim AS builder
WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN pip install --no-cache-dir uv && uv sync --no-dev

FROM python:3.11-slim
WORKDIR /app
ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1
COPY --from=builder /app/.venv ./.venv
COPY app ./app
RUN useradd -r -u 10001 -g root app && chown -R app:root /app
USER app
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
