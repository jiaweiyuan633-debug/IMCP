package cn.admin.scaffold.module.notice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 渠道发送记录。 */
@Data
@TableName("sys_channel_log")
public class SysChannelLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String channelType;
    private Long channelId;
    private String target;
    private String title;
    private String content;
    private Integer status;
    private String errorMsg;
    private LocalDateTime createdAt;
}
