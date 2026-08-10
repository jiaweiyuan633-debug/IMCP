package com.example.admin.module.mcp.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class McpCallResultVo {

    private boolean isError;
    private List<String> content;
    private Map<String, Object> structuredContent;
}
