package com.example.admin.module.system.warmflow;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WarmFlowWorkflowActionRequest {

    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    @NotNull(message = "节点ID不能为空")
    private Long nodeId;

    private String remark;
}
