package cn.admin.scaffold.module.ai;

/**
 * 文本向量化客户端：Milvus 写入/检索前把文本转为固定维度向量。
 * 实现类须保证输出维度与集合维度（app.milvus.dim）一致。
 */
public interface EmbeddingClient {

    /** 把文本转为固定维度向量。 */
    float[] embed(String text);

    /** 人类可读描述（诊断/日志展示当前生效的向量化方式）。 */
    String description();
}
