package cn.admin.scaffold.module.system.warmflow;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程轨迹单节点出参 VO，来自 Warm-Flow flow_his_task。
 */
@Data
@Builder
public class WorkflowTraceItemVo {

    private String nodeCode;
    private String nodeName;
    private String approver;
    private String flowStatus;
    private String message;
    private LocalDateTime createTime;
}
