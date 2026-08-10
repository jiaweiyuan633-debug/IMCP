package com.example.admin.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字段级审计日志实体。
 * 记录重要数据变更前后快照与字段级 diff（JSON）。
 */
@Data
@TableName("sys_field_audit_log")
public class SysFieldAuditLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String module;
    private String entityName;
    private Long entityId;
    private String action;
    private String changedFields;
    private String beforeData;
    private String afterData;
    private Integer status;
    private LocalDateTime createdAt;
}
