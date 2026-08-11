package com.example.admin.module.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MessageTemplateSaveRequest {

    private Long id;

    @NotBlank(message = "模板编码不能为空")
    @Size(max = 50, message = "模板编码长度不能超过 50")
    private String templateCode;

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称长度不能超过 100")
    private String templateName;

    private String messageType;

    @NotBlank(message = "标题模板不能为空")
    @Size(max = 200, message = "标题模板长度不能超过 200")
    private String titleTemplate;

    @NotBlank(message = "内容模板不能为空")
    private String contentTemplate;

    private String contentType;

    private Integer status;

    private String remark;
}
