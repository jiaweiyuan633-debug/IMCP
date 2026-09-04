package cn.admin.scaffold.module.mcp.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class McpServerVo {

    private Long id;
    private String name;
    private String url;
    /** 是否已配置认证令牌（令牌本身不回显，仅标记存在性） */
    private boolean hasAuthToken;
    private Integer enabled;
    private Integer sort;
    private String remark;
    private LocalDateTime createdAt;
}
