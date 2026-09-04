package cn.admin.scaffold.module.ai.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiTaskVo {

    private Long id;
    private String taskNo;
    private String bizType;
    private Long bizId;
    private String serviceCode;
    /** 服务展示名（R4-1.24 批量解析自 ai_service_config.name，未命中回退 serviceCode）。 */
    private String serviceName;
    private String status;
    private String paramsJson;
    private String errorMsg;
    private String errorType;
    private Integer retryCount;
    private Integer maxRetry;
    private Integer timeoutSeconds;
    private String callbackUrl;
    private Long createdBy;
    /** 创建人姓名（R4-1.24 批量解析自 sys_user.nickname，未命中回退用户名，无则留空）。 */
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private AiTaskResultVo result;
}

