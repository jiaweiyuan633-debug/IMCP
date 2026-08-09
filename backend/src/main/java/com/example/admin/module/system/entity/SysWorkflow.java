package com.example.admin.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_workflow")
public class SysWorkflow {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String processName;
    private String bizType;
    private Long processDefId;
    private Long currentNodeId;
    private String currentNodeName;
    private String currentNodeIds;
    private String formData;
    private LocalDateTime currentNodeAssignedAt;
    private Integer timeoutNotified;
    private Long assigneeUserId;
    private String assigneeName;
    private Long bizId;
    private Long applicantId;
    private String applicantName;
    private String content;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @Version
    private Integer version;
}

