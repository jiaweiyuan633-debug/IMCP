package com.example.admin.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeptSaveRequest {

    private Long id;
    private Long parentId = 0L;

    @NotBlank(message = "部门名称不能为空")
    @Size(max = 50, message = "部门名称长度不能超过 50")
    private String deptName;

    private Integer orderNum;
    private String leader;
    private String phone;
    private String email;
    private Integer status;
}

