package com.example.admin.module.form.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FormDefinitionSaveRequest {

    private Long id;

    @NotBlank(message = "表单名称不能为空")
    @Size(max = 100, message = "表单名称长度不能超过 100")
    private String name;

    @NotBlank(message = "表单编码不能为空")
    @Size(max = 64, message = "表单编码长度不能超过 64")
    private String code;

    @Size(max = 255, message = "描述长度不能超过 255")
    private String description;

    @NotBlank(message = "字段定义不能为空")
    private String schemaJson;

    private String layoutJson;

    /** 乐观锁版本号：编辑时携带，用于冲突检测（新增时为空） */
    private Integer version;
}
