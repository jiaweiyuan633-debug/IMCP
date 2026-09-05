package cn.admin.scaffold.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_task_result")
public class AiTaskResultDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long taskId;
    private String resultType;
    private String resultJson;
    private String rawData;
    private Long durationMs;
    private LocalDateTime createdAt;
}

