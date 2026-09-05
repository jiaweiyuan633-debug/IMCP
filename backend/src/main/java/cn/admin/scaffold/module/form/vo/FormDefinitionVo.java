package cn.admin.scaffold.module.form.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FormDefinitionVo {

    private Long id;
    private String name;
    private String code;
    private String description;
    /** 0草稿 1已发布 */
    private Integer status;
    private Integer version;
    private String schemaJson;
    private String layoutJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
