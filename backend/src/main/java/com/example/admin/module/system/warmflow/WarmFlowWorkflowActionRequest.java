package com.example.admin.module.system.warmflow;

import lombok.Data;

/**
 * 审批/驳回操作请求（R4-1.40）：taskId/nodeId 均允许为空——
 * WarmFlowWorkflowService#resolveTaskId 已完整容忍 null（taskId → nodeId → 单待办回退，
 * 多待办才要求显式指定）。R4-1.35 补齐 @Valid 时误加 @NotNull 打破前端契约
 * （前端 approve 未传 nodeId、reject 未传 taskId），此处与 Service 语义对齐恢复可空。
 */
@Data
public class WarmFlowWorkflowActionRequest {

    private Long taskId;

    private Long nodeId;

    private String remark;
}
