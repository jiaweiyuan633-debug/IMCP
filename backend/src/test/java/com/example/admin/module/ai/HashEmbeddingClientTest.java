package com.example.admin.module.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地哈希向量：维度对齐配置、确定性可复现、L2 归一化。
 */
class HashEmbeddingClientTest {

    private HashEmbeddingClient newClient(int dim) {
        MilvusProperties properties = new MilvusProperties();
        properties.setDim(dim);
        return new HashEmbeddingClient(properties);
    }

    @Test
    void outputDimMatchesConfig() {
        HashEmbeddingClient client = newClient(8);
        assertEquals(8, client.embed("温度过高告警").length);
        HashEmbeddingClient wide = newClient(1536);
        assertEquals(1536, wide.embed("温度过高告警").length);
    }

    @Test
    void deterministicForSameInput() {
        HashEmbeddingClient client = newClient(16);
        assertArrayEquals(client.embed("设备温度告警，请检查散热"), client.embed("设备温度告警，请检查散热"));
    }

    @Test
    void normalizedToUnitLength() {
        HashEmbeddingClient client = newClient(32);
        float[] vector = client.embed("这是一段用于向量检索的知识库文档内容");
        double normSq = 0;
        for (float value : vector) {
            normSq += value * value;
        }
        assertTrue(Math.abs(Math.sqrt(normSq) - 1.0) < 1e-5, "向量应已 L2 归一化");
    }

    @Test
    void nullTextIsSafe() {
        HashEmbeddingClient client = newClient(8);
        assertEquals(8, client.embed(null).length);
    }
}
