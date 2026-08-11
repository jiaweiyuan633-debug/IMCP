package com.example.admin.module.form.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表单提交记录实体。租户隔离（tenant_id 由拦截器自动注入），逻辑删除；不设 version 乐观锁。
 */
@Data
@TableName("form_instance")
public class FormInstanceDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long formId;
    private String formCode;
    private String dataJson;
    /** SUBMITTED/APPROVED/REJECTED */
    private String status;
    private Long submitterId;
    private LocalDateTime submittedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
