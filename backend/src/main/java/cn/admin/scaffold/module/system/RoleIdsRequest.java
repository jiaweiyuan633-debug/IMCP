package cn.admin.scaffold.module.system;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class RoleIdsRequest {

    @NotEmpty(message = "角色列表不能为空")
    private List<Long> roleIds;
}

