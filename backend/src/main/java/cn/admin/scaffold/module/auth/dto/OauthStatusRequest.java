package cn.admin.scaffold.module.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OauthStatusRequest {

    @NotNull(message = "启用状态不能为空")
    private Integer enabled;
}
