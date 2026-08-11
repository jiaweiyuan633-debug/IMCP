package com.example.admin.module.form.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表单定义实体。租户隔离（tenant_id 由拦截器自动注入），逻辑删除 + 乐观锁（version 发布时递增）。
 */
@Data
@TableName("form_definition")
public class FormDefinitionDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String code;
    private String description;
    private String schemaJson;
    private String layoutJson;
    /** 0草稿 1已发布 */
    private Integer status;
    @Version
    private Integer version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    @TableLogic
    private Integer deleted;
}
