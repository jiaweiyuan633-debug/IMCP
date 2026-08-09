package com.example.admin.module.system.warmflow;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 流程实例详情出参 VO：实例头部信息 + 表单回显 + 完整流程轨迹 + 当前待办节点。
 */
@Data
@Builder
public class WorkflowDetailVo {

    private Long id;
    private String processName;
    private String bizType;
    private Long bizId;
    private String status;
    private Long applicantId;
    private String applicantName;
    private String currentNodeName;
    private String content;
    private String remark;
    private LocalDateTime createdAt;
    private Long flowInstanceId;
    private Map<String, Object> formData;
    private List<WorkflowTraceItemVo> trace;
    private List<WarmFlowProcessNodeVO> currentNodes;
}
