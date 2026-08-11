package com.example.admin.module.notice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息模板实体：复用模板 + ${key} 占位符渲染 + TEXT/HTML 富文本内容类型。
 */
@Data
@TableName("sys_message_template")
public class SysMessageTemplateDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String templateCode;
    private String templateName;
    private String messageType;
    private String titleTemplate;
    private String contentTemplate;
    private String contentType;
    private Integer status;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version
    private Integer version;
    private Integer deleted;
}
