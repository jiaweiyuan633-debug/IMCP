package com.example.admin.module.form.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 已发布表单的渲染结构：字段列表 + 布局配置（layout_json 原样透传，如 {"columns":2}）。
 */
@Data
@Builder
public class FormSchemaVo {

    private List<FormField> fields;
    private String layout;
}
