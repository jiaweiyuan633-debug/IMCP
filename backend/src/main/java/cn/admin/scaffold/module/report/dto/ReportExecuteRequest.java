package cn.admin.scaffold.module.report.dto;

import lombok.Data;

import java.util.Map;

/**
 * 报表执行入参：提供 data_source 中 :param 命名占位对应的参数值。
 */
@Data
public class ReportExecuteRequest {

    private Map<String, Object> params;
}
