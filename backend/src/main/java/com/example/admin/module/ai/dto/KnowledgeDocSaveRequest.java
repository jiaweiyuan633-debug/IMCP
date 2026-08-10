package com.example.admin.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KnowledgeDocSaveRequest {

    private Long id;

    @NotNull(message = "知识库不能为空")
    private Long baseId;

    @NotBlank(message = "文档标题不能为空")
    @Size(max = 200, message = "文档标题长度不能超过 200")
    private String title;

    private String content;

    private Integer status;
}
