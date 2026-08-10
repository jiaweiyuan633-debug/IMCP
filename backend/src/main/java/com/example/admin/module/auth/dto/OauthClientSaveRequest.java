package com.example.admin.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OauthClientSaveRequest {

    private Long id;

    @NotBlank(message = "应用名称不能为空")
    private String clientName;

    @NotBlank(message = "client_id 不能为空")
    private String clientId;

    @NotBlank(message = "client_secret 不能为空")
    private String clientSecret;

    private String redirectUri;
    private String scope;
    private Integer enabled;
    private Integer sort;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
