package cn.admin.scaffold.module.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 /embeddings 远程向量化（真实语义向量）。
 * 端点未配置或调用失败时抛出 RuntimeException，由 {@link CompositeEmbeddingClient} 决策是否降级。
 */
@Slf4j
@Component
public class RemoteEmbeddingClient {

    private final MilvusProperties properties;
    private final RestTemplate restTemplate;

    @Autowired
    public RemoteEmbeddingClient(MilvusProperties properties) {
        this(properties, buildRestTemplate(properties));
    }

    /** 测试注入 RestTemplate；Spring 仅使用上方的单参公开构造器。 */
    RemoteEmbeddingClient(MilvusProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    private static RestTemplate buildRestTemplate(MilvusProperties properties) {
        int timeoutMs = Math.max(1000, properties.getEmbedding().getTimeoutSeconds() * 1000);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }

    public float[] embed(String text) {
        MilvusProperties.Embedding embedding = properties.getEmbedding();
        if (!StringUtils.hasText(embedding.getEndpoint())) {
            throw new IllegalStateException("未配置 app.milvus.embedding.endpoint");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(embedding.getApiKey())) {
            headers.setBearerAuth(embedding.getApiKey());
        }
        Map<String, Object> body = Map.of(
                "texts", List.of(text),
                "model", embedding.getModel());
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    embedding.getEndpoint(), new HttpEntity<>(body, headers), Map.class);
            return align(extract(response));
        } catch (RestClientException exception) {
            throw new IllegalStateException("远程 embedding 调用失败: " + exception.getMessage(), exception);
        }
    }

    private float[] extract(Map<String, Object> response) {
        if (response == null) {
            throw new IllegalStateException("远程 embedding 返回空响应");
        }
        Object vectors = response.get("vectors");
        if (!(vectors instanceof List<?> vectorList) || vectorList.isEmpty()) {
            throw new IllegalStateException("远程 embedding 响应缺少 vectors");
        }
        Object first = vectorList.get(0);
        if (!(first instanceof List<?> floats)) {
            throw new IllegalStateException("远程 embedding vectors[0] 非数组");
        }
        float[] vector = new float[floats.size()];
        for (int i = 0; i < floats.size(); i++) {
            Object value = floats.get(i);
            vector[i] = value instanceof Number number
                    ? number.floatValue()
                    : Float.parseFloat(String.valueOf(value));
        }
        return vector;
    }

    /** 维度对齐：与配置 dim 不一致时截断/补零，避免写入 Milvus 触发 schema 校验失败。 */
    private float[] align(float[] vector) {
        int dim = properties.getDim();
        if (vector.length == dim) {
            return vector;
        }
        log.warn("embedding 维度 {} 与配置 dim={} 不一致，按配置截断/补零对齐", vector.length, dim);
        float[] aligned = new float[dim];
        System.arraycopy(vector, 0, aligned, 0, Math.min(vector.length, dim));
        return aligned;
    }
}
