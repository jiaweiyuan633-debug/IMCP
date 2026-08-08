package com.example.admin.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MenuSaveRequest {

    private Long id;
    private Long parentId = 0L;

    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 50, message = "菜单名称长度不能超过 50")
    private String name;

    @NotBlank(message = "菜单类型不能为空")
    private String type;

    @Size(max = 200, message = "路径长度不能超过 200")
    private String path;

    @Size(max = 200, message = "组件路径长度不能超过 200")
    private String component;

    @Size(max = 100, message = "权限标识长度不能超过 100")
    private String perm;

    @Size(max = 100, message = "图标长度不能超过 100")
    private String icon;

    private Integer sort;
    private Integer visible;
    private Integer status;
}

