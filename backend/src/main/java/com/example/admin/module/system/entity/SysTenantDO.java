package com.example.admin.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_tenant")
public class SysTenantDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tenantName;
    private String tenantCode;
    private Integer status;
    private String contactName;
    private String contactPhone;
    private Integer userLimit;
    private Long storageLimitMb;
    private Long adminUserId;
    @TableField(exist = false)
    private Boolean clearAdminUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @Version
    private Integer version;
}

