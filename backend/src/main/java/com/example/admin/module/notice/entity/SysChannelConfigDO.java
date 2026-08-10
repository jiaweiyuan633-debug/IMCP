package com.example.admin.module.notice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息渠道配置。config_json 按渠道类型存不同参数：
 * MAIL {host,port,username,password,from}；SMS {url,apiKey,signName,templateId}；
 * DINGTALK {webhook,secret}；WECOM {webhook}。
 */
@Data
@TableName("sys_channel_config")
public class SysChannelConfigDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String channelType;
    private String channelName;
    private String configJson;
    private Integer status;
    private Integer sort;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
}
