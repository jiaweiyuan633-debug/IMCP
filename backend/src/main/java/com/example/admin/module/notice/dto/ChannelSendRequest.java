package com.example.admin.module.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChannelSendRequest {

    @NotNull(message = "渠道配置不能为空")
    private Long channelId;

    @NotBlank(message = "接收目标不能为空")
    @Size(max = 500, message = "接收目标长度不能超过 500")
    private String target;

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过 200")
    private String title;

    @Size(max = 4000, message = "内容长度不能超过 4000")
    private String content;
}
