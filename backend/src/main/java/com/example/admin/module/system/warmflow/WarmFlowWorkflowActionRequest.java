package com.example.admin.module.system.warmflow;

import lombok.Data;

@Data
public class WarmFlowWorkflowActionRequest {

    private Long taskId;
    private Long nodeId;
    private String remark;
}
