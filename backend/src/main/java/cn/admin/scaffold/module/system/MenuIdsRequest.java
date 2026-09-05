package cn.admin.scaffold.module.system;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class MenuIdsRequest {

    @NotEmpty(message = "菜单列表不能为空")
    private List<Long> menuIds;
}

