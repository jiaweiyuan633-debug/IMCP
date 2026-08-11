package com.example.admin.module.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 远程 embedding 优先、本地哈希兜底的组合向量化器：
 * 配置了 app.milvus.embedding.endpoint 时优先走真实语义向量，
 * 调用失败/超时自动降级为确定性哈希向量并限频提示，避免写入/检索链路中断。
 */
@Slf4j
@Component
public class CompositeEmbeddingClient implements EmbeddingClient {

    private final MilvusProperties properties;
    private final RemoteEmbeddingClient remote;
    private final HashEmbeddingClient fallback;
    private final AtomicBoolean degraded = new AtomicBoolean(false);

    public CompositeEmbeddingClient(MilvusProperties properties,
                                    RemoteEmbeddingClient remote,
                                    HashEmbeddingClient fallback) {
        this.properties = properties;
        this.remote = remote;
        this.fallback = fallback;
    }

    @Override
    public float[] embed(String text) {
        if (StringUtils.hasText(properties.getEmbedding().getEndpoint())) {
            try {
                float[] vector = remote.embed(text);
                if (degraded.compareAndSet(true, false)) {
                    log.info("远程 embedding 已恢复，退出降级");
                }
                return vector;
            } catch (RuntimeException exception) {
                if (degraded.compareAndSet(false, true)) {
                    log.warn("远程 embedding 不可用，已降级为本地哈希向量（恢复前限频提示）: {}", exception.getMessage());
                }
            }
        }
        return fallback.embed(text);
    }

    @Override
    public String description() {
        if (StringUtils.hasText(properties.getEmbedding().getEndpoint())) {
            return degraded.get() ? "remote→hash(降级)" : "remote(" + properties.getEmbedding().getEndpoint() + ")";
        }
        return fallback.description();
    }
}
