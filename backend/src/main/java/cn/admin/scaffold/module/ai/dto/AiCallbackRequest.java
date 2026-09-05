package cn.admin.scaffold.module.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AiCallbackRequest {

    @JsonProperty("task_no")
    private String taskNo;

    @JsonProperty("biz_type")
    private String bizType;

    @JsonProperty("retry_count")
    private Integer retryCount;

    private String status;
    private Object result;
    private String error;

    /** AI 侧失败分类（timeout / non_retryable / retries_exhausted），成功回调为 null。 */
    @JsonProperty("reason")
    private String errorType;
}
