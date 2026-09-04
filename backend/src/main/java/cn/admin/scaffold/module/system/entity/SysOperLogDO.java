package cn.admin.scaffold.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_oper_log")
public class SysOperLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String module;
    private String action;
    private String method;
    private String requestUrl;
    private String requestMethod;
    private String params;
    private String result;
    private Integer status;
    private String errorMsg;
    private String ip;
    private Long durationMs;
    private LocalDateTime operTime;
}

