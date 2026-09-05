package cn.admin.scaffold.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_task")
public class AiTaskDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String taskNo;
    private String bizType;
    private Long bizId;
    private String serviceCode;
    private String status;
    private String paramsJson;
    private String errorMsg;
    /** 失败原因分类：timeout / non_retryable / retries_exhausted。 */
    private String errorType;
    private Integer retryCount;
    private Integer maxRetry;
    private Integer timeoutSeconds;
    private String callbackUrl;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

