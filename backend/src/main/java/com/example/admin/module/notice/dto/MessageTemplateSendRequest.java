package com.example.admin.module.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class MessageTemplateSendRequest {

    @NotBlank(message = "模板编码不能为空")
    @Size(max = 50, message = "模板编码长度不能超过 50")
    private String templateCode;

    /** 模板占位符参数，渲染后填充 ${key}。 */
    private Map<String, Object> params;

    private String bizType;
    private Long bizId;

    /** 为空表示广播（全体用户）；否则发送给指定用户。 */
    private java.util.List<Long> receiverIds;
}
