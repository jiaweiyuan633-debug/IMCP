package com.example.admin.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/** 第三方登录配置（微信/GitHub/Gitee）。 */
@Data
@TableName("sys_oauth_config")
public class SysOauthConfigDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String provider;
    private String appId;
    private String appSecret;
    private String redirectUri;
    private String scope;
    private Integer enabled;
    private Integer sort;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
}
