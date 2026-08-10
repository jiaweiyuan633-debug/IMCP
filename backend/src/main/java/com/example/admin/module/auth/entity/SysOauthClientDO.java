package com.example.admin.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/** SSO 第三方应用（本平台作为 OAuth2 授权服务时注册的客户端）。 */
@Data
@TableName("sys_oauth_client")
public class SysOauthClientDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String clientName;
    private String clientId;
    private String clientSecret;
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
