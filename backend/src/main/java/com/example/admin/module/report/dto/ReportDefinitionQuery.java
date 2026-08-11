package com.example.admin.module.report.dto;

import lombok.Data;

/**
 * 报表定义分页查询条件：name/code/category 模糊过滤。
 */
@Data
public class ReportDefinitionQuery {

    private long pageNum = 1;
    private long pageSize = 10;
    private String name;
    private String code;
    private String category;
}
