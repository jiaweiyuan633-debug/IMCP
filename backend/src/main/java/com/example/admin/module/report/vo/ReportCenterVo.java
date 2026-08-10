package com.example.admin.module.report.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 报表中心聚合数据：核心计数 + 各维度分布。 */
@Data
@Builder
public class ReportCenterVo {

    private long userCount;
    private long roleCount;
    private long deptCount;
    private long deviceCount;
    private long jobCount;
    private long flowCount;

    /** 近 7 天登录趋势 */
    private List<NameValueVo> loginTrend;
    /** 操作日志按模块分布 */
    private List<NameValueVo> operByModule;
    /** 设备按类型分布 */
    private List<NameValueVo> deviceByType;
    /** 设备按状态分布（1启用/0停用） */
    private List<NameValueVo> deviceByStatus;
    /** 任务执行按状态分布 */
    private List<NameValueVo> jobByStatus;
    /** AI 任务按状态分布 */
    private List<NameValueVo> aiByStatus;
}
