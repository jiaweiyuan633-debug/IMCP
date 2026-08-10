package com.example.admin.module.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChannelConfigSaveRequest {

    private Long id;

    @NotBlank(message = "渠道类型不能为空")
    private String channelType;

    @NotBlank(message = "渠道名称不能为空")
    @Size(max = 50, message = "渠道名称长度不能超过 50")
    private String channelName;

    @NotBlank(message = "渠道参数不能为空")
    private String configJson;

    private Integer status;
    private Integer sort;

    @Size(max = 255, message = "描述长度不能超过 255")
    private String description;
}
