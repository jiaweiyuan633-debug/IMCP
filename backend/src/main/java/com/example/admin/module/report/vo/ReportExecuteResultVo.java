package com.example.admin.module.report.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 报表执行结果：列名 + 数据行（前端仅作展示，不做二次写入）。
 */
@Data
@Builder
public class ReportExecuteResultVo {

    private List<String> columns;
    private List<Map<String, Object>> rows;
}
