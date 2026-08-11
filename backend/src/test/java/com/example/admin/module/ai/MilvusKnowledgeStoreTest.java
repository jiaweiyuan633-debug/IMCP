package com.example.admin.module.ai;

import com.example.admin.module.ai.entity.AiKnowledgeDocDO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Milvus 知识库降级路径：Milvus 不可达（连接失败）时检索自动回退 MySQL 全文检索，
 * 写入侧静默跳过且不抛异常（文档本体始终在 MySQL）。
 * 构造真实的 MilvusServiceClient 指向未监听端口，走真实 gRPC 连接失败路径。
 */
class MilvusKnowledgeStoreTest {

    private MilvusProperties unreachableProperties() {
        MilvusProperties properties = new MilvusProperties();
        properties.setDim(8);
        properties.setHost("127.0.0.1");
        properties.setPort(1); // 无服务监听 → 立即连接拒绝
        properties.setConnectTimeoutSeconds(1);
        return properties;
    }

    @Test
    void searchFallsBackToMysqlWhenMilvusUnreachable() {
        MilvusProperties properties = unreachableProperties();
        MysqlFulltextSearcher fallback = mock(MysqlFulltextSearcher.class);
        when(fallback.search(1L, 1L, "温度", 5)).thenReturn(List.of("MySQL 命中片段"));

        MilvusKnowledgeStore store = new MilvusKnowledgeStore(properties, new HashEmbeddingClient(properties), fallback);
        List<String> result = store.search(1L, 1L, "温度", 5);

        assertEquals(List.of("MySQL 命中片段"), result);
    }

    @Test
    void upsertIsSafeWhenMilvusUnreachable() {
        MilvusProperties properties = unreachableProperties();
        MilvusKnowledgeStore store = new MilvusKnowledgeStore(properties, new HashEmbeddingClient(properties),
                mock(MysqlFulltextSearcher.class));

        AiKnowledgeDocDO doc = new AiKnowledgeDocDO();
        doc.setId(1L);
        doc.setBaseId(1L);
        doc.setTitle("设备手册");
        doc.setContent("设备上电后进入待机模式。");

        assertDoesNotThrow(() -> store.upsert(1L, doc));
        assertDoesNotThrow(() -> store.deleteByDoc(1L, 1L));
        assertDoesNotThrow(() -> store.deleteByBase(1L, 1L));
    }
}
