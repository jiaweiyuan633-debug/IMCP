package cn.admin.scaffold.module.ai.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KnowledgeBaseVo {

    private Long id;
    private String name;
    private String description;
    private Integer status;
    private LocalDateTime createdAt;
}
