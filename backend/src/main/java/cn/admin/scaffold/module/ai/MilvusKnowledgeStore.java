package cn.admin.scaffold.module.ai;

import cn.admin.scaffold.module.ai.entity.AiKnowledgeDocDO;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Milvus 向量检索知识库（可选集成）：配置 app.milvus.enabled=true 时启用，
 * 替换默认的 {@link MysqlKeywordStore}。
 *
 * <p>能力：真实 embedding（OpenAI 兼容端点，失败自动降级本地哈希向量）；
 * 连接/集合延迟就绪探测；Milvus 运行不可用时检索自动降级为 MySQL ngram 全文检索，
 * 写入侧跳过向量同步（文档本体始终在 MySQL，不丢失）。
 *
 * <p>注意：milvus-sdk-java 2.4.x 的 {@link MilvusServiceClient} 构造器会立即建连，
 * 连接失败直接抛 RuntimeException。因此这里采用惰性连接：bean 初始化不建连，
 * 首次操作时才尝试连接，失败则保持降级并在后续操作中自动重连，避免 Milvus 宕机拖垮后端启动。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.milvus.enabled", havingValue = "true")
public class MilvusKnowledgeStore implements KnowledgeVectorStore {

    private static final String COLLECTION = "ai_knowledge_doc";

    private final MilvusProperties properties;
    private final EmbeddingClient embeddingClient;
    private final MysqlFulltextSearcher fallback;
    /** 惰性初始化：首次 ensureReady() 时建连；连接失败保持 null，后续操作自动重试。 */
    private MilvusServiceClient client;

    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean degraded = new AtomicBoolean(false);

    public MilvusKnowledgeStore(MilvusProperties properties,
                                EmbeddingClient embeddingClient,
                                MysqlFulltextSearcher fallback) {
        this.properties = properties;
        this.embeddingClient = embeddingClient;
        this.fallback = fallback;
    }

    private ConnectParam buildConnectParam() {
        ConnectParam.Builder connect = ConnectParam.newBuilder()
                .withHost(properties.getHost())
                .withPort(properties.getPort())
                .withSecure(properties.isSecure())
                .withConnectTimeout(Math.max(1000, properties.getConnectTimeoutSeconds() * 1000L), TimeUnit.MILLISECONDS);
        if (StringUtils.hasText(properties.getDatabase())) {
            connect.withDatabaseName(properties.getDatabase());
        }
        if (StringUtils.hasText(properties.getUsername()) && StringUtils.hasText(properties.getPassword())) {
            connect.withAuthorization(properties.getUsername(), properties.getPassword());
        }
        return connect.build();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void upsert(Long tenantId, AiKnowledgeDocDO doc) {
        if (!ensureReady()) {
            // 文档已落库 MySQL，Milvus 不可用时跳过向量写入，恢复后由下一轮同步补
            return;
        }
        try {
            JsonObject row = new JsonObject();
            row.addProperty("doc_id", doc.getId());
            row.addProperty("tenant_id", tenantId);
            row.addProperty("base_id", doc.getBaseId());
            row.addProperty("text", snippet(doc));
            row.add("embedding", toJsonArray(embeddingClient.embed(titleAndContent(doc))));
            R<io.milvus.grpc.MutationResult> result = client.upsert(UpsertParam.newBuilder()
                    .withCollectionName(COLLECTION)
                    .withRows(List.of(row))
                    .build());
            throwOnError(result, "upsert 文档 " + doc.getId());
            resetDegraded();
        } catch (RuntimeException exception) {
            degradeAndWarn("upsert", exception);
        }
    }

    @Override
    public void deleteByDoc(Long tenantId, Long docId) {
        if (!ensureReady()) {
            return;
        }
        try {
            throwOnError(client.delete(DeleteParam.newBuilder()
                    .withCollectionName(COLLECTION)
                    .withExpr("doc_id in [" + docId + "]")
                    .build()), "删除文档 " + docId);
            resetDegraded();
        } catch (RuntimeException exception) {
            degradeAndWarn("deleteByDoc", exception);
        }
    }

    @Override
    public void deleteByBase(Long tenantId, Long baseId) {
        if (!ensureReady()) {
            return;
        }
        try {
            throwOnError(client.delete(DeleteParam.newBuilder()
                    .withCollectionName(COLLECTION)
                    .withExpr("base_id in [" + baseId + "]")
                    .build()), "删除知识库 " + baseId);
            resetDegraded();
        } catch (RuntimeException exception) {
            degradeAndWarn("deleteByBase", exception);
        }
    }

    @Override
    public List<String> search(Long tenantId, Long baseId, String query, int topK) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        if (!ensureReady()) {
            return fallback.search(tenantId, baseId, query, topK);
        }
        try {
            List<String> result = doMilvusSearch(tenantId, baseId, query, topK);
            resetDegraded();
            return result;
        } catch (RuntimeException exception) {
            if (degraded.compareAndSet(false, true)) {
                log.warn("Milvus 检索失败（向量维度变更需重建集合？），本次降级为 MySQL 全文检索: {}",
                        exception.getMessage());
            }
            return fallback.search(tenantId, baseId, query, topK);
        }
    }

    private List<String> doMilvusSearch(Long tenantId, Long baseId, String query, int topK) {
        SearchParam param = SearchParam.newBuilder()
                .withCollectionName(COLLECTION)
                .withMetricType(MetricType.L2)
                .withVectorFieldName("embedding")
                .withFloatVectors(List.of(toFloatList(embeddingClient.embed(query))))
                .withExpr("tenant_id == " + tenantId + " and base_id == " + baseId)
                .withOutFields(List.of("text", "doc_id"))
                .withTopK(topK)
                .build();
        R<io.milvus.grpc.SearchResults> response = client.search(param);
        throwOnError(response, "检索");
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

    /**
     * 延迟就绪：构造不建连，首次操作时才连接并探测集合，Milvus 后启动也能接上。
     * 连接失败不抛异常，返回 false 并保持降级；后续每次操作都会重试连接（自动恢复）。
     */
    private boolean ensureReady() {
        if (ready.get()) {
            return true;
        }
        synchronized (this) {
            if (ready.get()) {
                return true;
            }
            if (client == null) {
                try {
                    client = new MilvusServiceClient(buildConnectParam());
                } catch (RuntimeException exception) {
                    if (degraded.compareAndSet(false, true)) {
                        log.warn("Milvus 连接失败，本次操作降级（后续自动重试）: {}", exception.getMessage());
                    }
                    return false;
                }
            }
            try {
                ensureCollection(client);
                ready.set(true);
                if (degraded.compareAndSet(true, false)) {
                    log.info("Milvus 连接恢复");
                }
                return true;
            } catch (RuntimeException exception) {
                if (degraded.compareAndSet(false, true)) {
                    log.warn("Milvus 不可用（连接/集合未就绪），本次操作降级: {}", exception.getMessage());
                }
                return false;
            }
        }
    }

    private void ensureCollection(MilvusServiceClient connected) {
        R<Boolean> has = connected.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(COLLECTION)
                .build());
        if (has.getException() != null) {
            throw new IllegalStateException(has.getException().getMessage());
        }
        if (Boolean.TRUE.equals(has.getData())) {
            return;
        }
        List<FieldType> fields = List.of(
                FieldType.newBuilder().withName("doc_id").withDataType(DataType.Int64).withPrimaryKey(true).build(),
                FieldType.newBuilder().withName("tenant_id").withDataType(DataType.Int64).build(),
                FieldType.newBuilder().withName("base_id").withDataType(DataType.Int64).build(),
                FieldType.newBuilder().withName("text").withDataType(DataType.VarChar).withMaxLength(65535).build(),
                FieldType.newBuilder().withName("embedding").withDataType(DataType.FloatVector)
                        .withDimension(properties.getDim()).build());
        R<RpcStatus> result = connected.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(COLLECTION)
                .withFieldTypes(fields)
                .build());
        if (result.getException() != null) {
            throw new IllegalStateException("创建集合失败: " + result.getException().getMessage());
        }
        log.info("Milvus 集合 {} 已创建（dim={}）", COLLECTION, properties.getDim());
    }

    private void throwOnError(R<?> result, String action) {
        if (result.getException() != null) {
            throw new IllegalStateException("Milvus " + action + " 失败: " + result.getException().getMessage());
        }
    }

    private void degradeAndWarn(String action, RuntimeException exception) {
        if (degraded.compareAndSet(false, true)) {
            log.warn("Milvus {} 失败（已降级，恢复前限频提示）: {}", action, exception.getMessage());
        }
    }

    private void resetDegraded() {
        if (degraded.compareAndSet(true, false)) {
            log.info("Milvus 已恢复在线");
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
}
