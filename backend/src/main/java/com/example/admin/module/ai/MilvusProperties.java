package com.example.admin.module.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Milvus 向量库接入配置（app.milvus.*）。默认不启用；
 * enabled=true 时替换默认 MySQL 全文检索为向量检索，且 Milvus 运行不可用时自动降级回 MySQL。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.milvus")
public class MilvusProperties {

    private boolean enabled = false;
    private String host = "localhost";
    private int port = 19530;
    private boolean secure = false;
    private String username;
    private String password;
    /** Milvus 数据库名（v2.3+ 多库隔离；默认库为 default）。 */
    private String database = "default";
    /**
     * 向量维度：须与集合已建维度一致。
     * text-embedding-3-small 为 1536；bge-m3 为 1024。变更维度需重建集合（drop 后自动重建）。
     */
    private int dim = 1536;
    private int connectTimeoutSeconds = 5;
    private Embedding embedding = new Embedding();

    @Data
    public static class Embedding {

        /** OpenAI 兼容 /embeddings 端点（如 ai-service http://ai-service:8000/api/v1/embeddings）；留空则用本地确定性哈希向量 */
        private String endpoint;
        /** Bearer 令牌：ai-service 的 AUTH_TOKEN 或供应商 apiKey */
        private String apiKey;
        private String model = "text-embedding-3-small";
        private int timeoutSeconds = 10;
    }
}
