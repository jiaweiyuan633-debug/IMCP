package com.example.admin.module.importexport.dto;

import lombok.Data;

/**
 * 导入导出模板分页查询参数。
 */
@Data
public class TemplateQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String name;
    private String code;
    private String type;
}
