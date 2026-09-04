package cn.admin.scaffold.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RoleSaveRequest {

    private Long id;

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 50, message = "角色编码长度不能超过 50")
    private String code;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称长度不能超过 50")
    private String name;

    @Size(max = 255, message = "描述长度不能超过 255")
    private String description;

    private Integer status;
    private Integer dataScope = 1;
    private Integer sort;
    private List<Long> menuIds;
    private List<Long> deptIds;
}

