package cn.admin.scaffold.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class MessageSendRequest {

    @NotBlank(message = "消息标题不能为空")
    @Size(max = 200, message = "消息标题长度不能超过 200")
    private String title;

    private String content;

    private List<Long> receiverIds;
}
