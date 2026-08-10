package com.example.admin.module.notice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChannelStatusRequest {

    @NotNull(message = "状态不能为空")
    private Integer status;
}
