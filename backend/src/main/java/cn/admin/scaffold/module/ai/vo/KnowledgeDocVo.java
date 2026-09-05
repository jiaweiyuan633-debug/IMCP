package cn.admin.scaffold.module.ai.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KnowledgeDocVo {

    private Long id;
    private Long baseId;
    private String title;
    private String content;
    private Integer chunkIndex;
    private Integer status;
    private LocalDateTime createdAt;
}
