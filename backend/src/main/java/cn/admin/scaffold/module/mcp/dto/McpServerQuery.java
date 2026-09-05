package cn.admin.scaffold.module.mcp.dto;

import lombok.Data;

@Data
public class McpServerQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String keyword;
    private Integer enabled;
}
