package com.example.admin.module.ai;

import com.example.admin.module.ai.entity.AiKnowledgeDocDO;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.ConnectParam;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.dml.UpsertParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Milvus 向量检索知识库（可选集成）：配置 app.milvus.enabled=true 时启用，
 * 替换默认的 {@link MysqlKeywordStore}。向量由确定性伪嵌入占位生成，
 * 生产环境应替换为真实 embedding 服务（如 text-embedding-3-small）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.milvus.enabled", havingValue = "true")
public class MilvusKnowledgeStore implements KnowledgeVectorStore {

    private static final String COLLECTION = "ai_knowledge_doc";
    private static final int VECTOR_DIM = 64;

    private final MilvusServiceClient client;

    public MilvusKnowledgeStore(
            @Value("${app.milvus.host:localhost}") String host,
            @Value("${app.milvus.port:19530}") int port,
            @Value("${app.milvus.secure:false}") boolean secure) {
        this.client = new MilvusServiceClient(ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .withSecure(secure)
                .build());
        ensureCollection();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void upsert(Long tenantId, AiKnowledgeDocDO doc) {
        JsonObject row = new JsonObject();
        row.addProperty("doc_id", doc.getId());
        row.addProperty("tenant_id", tenantId);
        row.addProperty("base_id", doc.getBaseId());
        row.addProperty("text", snippet(doc));
        row.add("embedding", toJsonArray(embed(titleAndContent(doc))));
        R<io.milvus.grpc.MutationResult> result = client.upsert(UpsertParam.newBuilder()
                .withCollectionName(COLLECTION)
                .withRows(List.of(row))
                .build());
        warnOnError(result, "upsert 文档 " + doc.getId());
    }

    @Override
    public void deleteByDoc(Long tenantId, Long docId) {
        client.delete(DeleteParam.newBuilder()
                .withCollectionName(COLLECTION)
                .withExpr("doc_id in [" + docId + "]")
                .build());
    }

    @Override
    public void deleteByBase(Long tenantId, Long baseId) {
        client.delete(DeleteParam.newBuilder()
                .withCollectionName(COLLECTION)
                .withExpr("base_id in [" + baseId + "]")
                .build());
    }

    @Override
    public List<String> search(Long tenantId, Long baseId, String query, int topK) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        SearchParam param = SearchParam.newBuilder()
                .withCollectionName(COLLECTION)
                .withMetricType(MetricType.L2)
                .withVectorFieldName("embedding")
                .withFloatVectors(List.of(toFloatList(embed(query))))
                .withExpr("tenant_id == " + tenantId + " and base_id == " + baseId)
                .withOutFields(List.of("text", "doc_id"))
                .withTopK(topK)
                .build();
        R<io.milvus.grpc.SearchResults> response = client.search(param);
        if (response.getException() != null) {
            log.warn("Milvus 检索失败: {}", response.getException().getMessage());
            return List.of();
        }
        SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
        List<QueryResultsWrapper.RowRecord> records = wrapper.getRowRecords();
        List<String> result = new ArrayList<>(records.size());
        for (QueryResultsWrapper.RowRecord record : records) {
            Object text = record.get("text");
            if (text != null) {
                result.add(text.toString());
            }
        }
        return result;
    }

    private synchronized void ensureCollection() {
        R<Boolean> has = client.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(COLLECTION)
                .build());
        if (has.getData() != null && has.getData()) {
            return;
        }
        List<FieldType> fields = List.of(
                FieldType.newBuilder().withName("doc_id").withDataType(DataType.Int64).withPrimaryKey(true).build(),
                FieldType.newBuilder().withName("tenant_id").withDataType(DataType.Int64).build(),
                FieldType.newBuilder().withName("base_id").withDataType(DataType.Int64).build(),
                FieldType.newBuilder().withName("text").withDataType(DataType.VarChar).withMaxLength(65535).build(),
                FieldType.newBuilder().withName("embedding").withDataType(DataType.FloatVector).withDimension(VECTOR_DIM).build());
        R<RpcStatus> result = client.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(COLLECTION)
                .withFieldTypes(fields)
                .build());
        warnOnError(result, "创建集合 " + COLLECTION);
    }

    private void warnOnError(R<?> result, String action) {
        if (result.getException() != null) {
            log.warn("Milvus {} 失败: {}", action, result.getException().getMessage());
        }
    }

    private static String snippet(AiKnowledgeDocDO doc) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(doc.getTitle())) {
            sb.append("标题：").append(doc.getTitle());
        }
        if (StringUtils.hasText(doc.getContent())) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append("内容：").append(doc.getContent());
        }
        return sb.toString();
    }

    private static String titleAndContent(AiKnowledgeDocDO doc) {
        return (doc.getTitle() == null ? "" : doc.getTitle())
                + "\n"
                + (doc.getContent() == null ? "" : doc.getContent());
    }

    private static List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float value : vector) {
            list.add(value);
        }
        return list;
    }

    private static JsonArray toJsonArray(float[] vector) {
        JsonArray array = new JsonArray();
        for (float value : vector) {
            array.add(value);
        }
        return array;
    }

    /** 确定性伪向量（占位）：无外部 embedding 服务时的可运行实现，语义性弱但确定性可复现。 */
    private static float[] embed(String text) {
        float[] vector = new float[VECTOR_DIM];
        byte[] bytes = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            int bucket = Math.floorMod(i * 7 + bytes[i], VECTOR_DIM);
            vector[bucket] += (bytes[i] - 127f) / 127f;
        }
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
}
