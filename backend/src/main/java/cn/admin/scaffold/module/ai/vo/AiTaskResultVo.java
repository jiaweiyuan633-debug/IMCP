package cn.admin.scaffold.module.ai.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AiTaskResultVo {

    private Long id;
    private Long taskId;
    private String resultType;
    private String resultJson;
    private String rawData;
    private Long durationMs;
    private LocalDateTime createdAt;
}

