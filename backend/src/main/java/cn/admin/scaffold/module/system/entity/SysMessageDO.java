package cn.admin.scaffold.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_message")
public class SysMessageDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long senderId;
    private Long receiverId;
    private String messageType;
    private String title;
    private String content;
    /** 内容类型 TEXT 纯文本 / HTML 富文本，前端按类型渲染（HTML 不转义）。 */
    private String contentType;
    private String bizType;
    private Long bizId;
    private String priority;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
    @TableField(exist = false)
    private Integer readFlag;
}
