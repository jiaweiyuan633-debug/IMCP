package cn.admin.scaffold.module.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkflowDelegateRequest {

    @NotNull(message = "转办用户不能为空")
    private Long delegateUserId;
}
