package cn.admin.scaffold.module.form.vo;

import lombok.Data;

import java.util.List;

/**
 * 表单字段定义：与 schema_json 数组中的单个元素一一对应。
 */
@Data
public class FormField {

    private String key;
    private String label;
    /** 字段类型：input/textarea/number/date/select/multi-select/switch */
    private String type;
    private Boolean required;
    private List<String> options;
    private String placeholder;
    private Integer maxLength;
}
