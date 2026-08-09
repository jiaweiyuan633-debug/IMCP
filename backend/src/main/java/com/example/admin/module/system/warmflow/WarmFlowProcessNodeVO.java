package com.example.admin.module.system.warmflow;

import lombok.Data;

@Data
public class WarmFlowProcessNodeVO {

    private Long id;
    private Long taskId;
    private String nodeName;
    private String nodeKey;
    private String nodeType;
    private String conditionExpression;
    private Integer timeoutHours;
    private Integer nodeOrder;
    private Long approverRoleId;
}
