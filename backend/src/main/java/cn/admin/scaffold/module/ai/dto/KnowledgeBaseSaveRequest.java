package cn.admin.scaffold.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KnowledgeBaseSaveRequest {

    private Long id;

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 100, message = "知识库名称长度不能超过 100")
    private String name;

    @Size(max = 255, message = "描述长度不能超过 255")
    private String description;

    private Integer status;
}
