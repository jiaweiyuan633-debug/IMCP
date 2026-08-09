package com.example.admin.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_workflow")
public class SysWorkflow {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String processName;
    private String bizType;
    private Long bizId;
    private Long applicantId;
    private String applicantName;
    private String content;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

