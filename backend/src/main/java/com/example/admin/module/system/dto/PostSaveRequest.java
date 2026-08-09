package com.example.admin.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostSaveRequest {

    private Long id;

    @NotBlank(message = "岗位编码不能为空")
    @Size(max = 50, message = "岗位编码长度不能超过 50")
    private String postCode;

    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 50, message = "岗位名称长度不能超过 50")
    private String postName;

    private Integer sort;
    private Integer status;
    private String description;
}

