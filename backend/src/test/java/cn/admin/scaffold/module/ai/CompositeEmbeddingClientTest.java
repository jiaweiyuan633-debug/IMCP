package cn.admin.scaffold.module.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 组合向量化器：端点未配置走本地哈希；配置后优先远程，远程失败自动降级哈希并恢复后切回。
 */
class CompositeEmbeddingClientTest {

    private final MilvusProperties properties = new MilvusProperties();

    private CompositeEmbeddingClient build(RemoteEmbeddingClient remote) {
        return new CompositeEmbeddingClient(properties, remote, new HashEmbeddingClient(properties));
    }

    @Test
    void noEndpointUsesHashFallback() {
        properties.setDim(8);
        properties.getEmbedding().setEndpoint("");
        CompositeEmbeddingClient client = build(new RemoteEmbeddingClient(properties));
        assertEquals("hash(dim=8)", client.description());
        assertEquals(8, client.embed("查询").length);
    }

    @Test
    void remoteSuccessReturnsRemoteVector() {
        properties.setDim(8);
        properties.getEmbedding().setEndpoint("http://ai-service:8000/api/v1/embeddings");
        properties.getEmbedding().setApiKey("token");

        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForObject(eq("http://ai-service:8000/api/v1/embeddings"), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("vectors", List.of(List.of(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)), "dim", 8));
        CompositeEmbeddingClient client = build(new RemoteEmbeddingClient(properties, restTemplate));

        float[] vector = client.embed("查询");
        assertEquals(1f, vector[0]);
        assertTrue(client.description().startsWith("remote("));
    }

    @Test
    void remoteFailureFallsBackToHash() {
        properties.setDim(8);
        properties.getEmbedding().setEndpoint("http://127.0.0.1:1/api/v1/embeddings");

        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RestClientException("connection refused"));
        CompositeEmbeddingClient client = build(new RemoteEmbeddingClient(properties, restTemplate));

        // 降级：远程抛异常后返回哈希向量，维度一致、描述标注降级
        float[] vector = client.embed("查询");
        assertEquals(8, vector.length);
        assertArrayEquals(new HashEmbeddingClient(properties).embed("查询"), vector);
        assertTrue(client.description().contains("降级"));
    }
}
