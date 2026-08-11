package com.example.admin.module.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 本地确定性哈希向量（兜底）：未配置外部 embedding 端点时的可运行实现。
 * 语义性弱但确定可复现，保证 Milvus 写入/检索结构自洽；
 * 生产建议配置 app.milvus.embedding.endpoint 走真实语义向量。
 */
@Component
@RequiredArgsConstructor
public class HashEmbeddingClient implements EmbeddingClient {

    private final MilvusProperties properties;

    @Override
    public float[] embed(String text) {
        int dim = properties.getDim();
        float[] vector = new float[dim];
        byte[] bytes = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            int bucket = Math.floorMod(i * 31 + bytes[i], dim);
            vector[bucket] += (bytes[i] - 127f) / 127f;
        }
        // L2 归一化：与检索使用的 L2 距离度量保持量纲一致
        float norm = 0f;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm > 0f) {
            float scale = (float) Math.sqrt(norm);
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= scale;
            }
        }
        return vector;
    }

    @Override
    public String description() {
        return "hash(dim=" + properties.getDim() + ")";
    }
}
