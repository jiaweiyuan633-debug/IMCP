package com.example.admin.module.report.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 数据大屏模板视图：内置模板 builtin=true 仅可另存，不可删除/直接覆盖。 */
@Data
@Builder
public class ScreenTemplateVo {

    private Long id;
    private String name;
    private String code;
    private String category;
    private String theme;
    private String layout;
    private String remark;
    private boolean builtin;
    private LocalDateTime createdAt;
}
