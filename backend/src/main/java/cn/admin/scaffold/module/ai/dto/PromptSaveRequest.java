package cn.admin.scaffold.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PromptSaveRequest {

    private Long id;

    @NotBlank(message = "模板编码不能为空")
    @Size(max = 50, message = "模板编码长度不能超过 50")
    private String code;

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称长度不能超过 100")
    private String name;

    @NotBlank(message = "模板内容不能为空")
    private String content;

    private String variables;
    private Integer status;
    private Integer sort;

    @Size(max = 255, message = "描述长度不能超过 255")
    private String description;
}
