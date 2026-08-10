package com.example.admin.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OauthConfigSaveRequest {

    private Long id;

    @NotBlank(message = "提供方不能为空")
    private String provider;

    @NotBlank(message = "客户端 ID 不能为空")
    private String appId;

    @NotBlank(message = "客户端密钥不能为空")
    @Size(max = 255, message = "客户端密钥长度不能超过 255")
    private String appSecret;

    private String redirectUri;
    private String scope;
    private Integer enabled;
    private Integer sort;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
