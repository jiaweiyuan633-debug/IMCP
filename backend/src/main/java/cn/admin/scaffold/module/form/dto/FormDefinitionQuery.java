package cn.admin.scaffold.module.form.dto;

import lombok.Data;

@Data
public class FormDefinitionQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String name;
    private String code;
}
