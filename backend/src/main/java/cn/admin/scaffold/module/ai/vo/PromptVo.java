package cn.admin.scaffold.module.ai.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PromptVo {

    private Long id;
    private String code;
    private String name;
    private String content;
    private String variables;
    private Integer status;
    private Integer sort;
    private String description;
    private LocalDateTime createdAt;
}
