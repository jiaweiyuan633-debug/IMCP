package com.example.admin.module.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_sql_log")
public class SysSqlLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String sqlText;
    private String method;
    private Long durationMs;
    private Integer success;
    private String errorMsg;
    private LocalDateTime createdAt;
}

