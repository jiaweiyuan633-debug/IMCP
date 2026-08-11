package com.example.admin.module.device.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物模型实体。properties/events/services 为 JSON 列，用 String 承接（插入/读取由 JDBC 自动处理）。
 * 租户隔离（tenant_id 由拦截器自动注入），逻辑删除 + 乐观锁。
 */
@Data
@TableName("device_thing_model")
public class ThingModelDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String deviceType;
    private String name;
    private String description;
    private String propertiesJson;
    private String eventsJson;
    private String servicesJson;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
}
