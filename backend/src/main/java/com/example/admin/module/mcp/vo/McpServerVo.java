package com.example.admin.module.mcp.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class McpServerVo {

    private Long id;
    private String name;
    private String url;
    private String authToken;
    private Integer enabled;
    private Integer sort;
    private String remark;
    private LocalDateTime createdAt;
}
