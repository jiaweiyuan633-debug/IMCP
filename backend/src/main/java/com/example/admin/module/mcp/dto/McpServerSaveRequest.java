package com.example.admin.module.mcp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class McpServerSaveRequest {

    private Long id;

    @NotBlank(message = "服务名称不能为空")
    @Size(max = 100, message = "服务名称长度不能超过 100")
    private String name;

    @NotBlank(message = "MCP Server 地址不能为空")
    @Size(max = 255, message = "地址长度不能超过 255")
    private String url;

    @Size(max = 255, message = "认证令牌长度不能超过 255")
    private String authToken;

    private Integer enabled;
    private Integer sort;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
