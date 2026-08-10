package com.example.admin.module.report.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 数据大屏聚合数据：指标卡 + 各图表数据 + 最近操作滚动列表。 */
@Data
@Builder
public class ReportScreenVo {

    private long loginSuccessCount;
    private long operTotal;
    private long operErrorCount;
    private long aiTaskCount;

    /** 近 7 天登录趋势 */
    private List<NameValueVo> loginTrend;
    /** 近 7 天操作量趋势 */
    private List<NameValueVo> operTrend;
    /** 操作日志按模块分布 */
    private List<NameValueVo> operByModule;
    /** 设备按类型分布 */
    private List<NameValueVo> deviceByType;
    /** 设备按状态分布 */
    private List<NameValueVo> deviceByStatus;
    /** 任务执行按状态分布 */
    private List<NameValueVo> jobByStatus;
    /** AI 任务按状态分布 */
    private List<NameValueVo> aiByStatus;
    /** 最近 10 条操作 */
    private List<RecentOperVo> recentOpers;
}
