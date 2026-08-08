package com.example.admin.module.monitor.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStatsVo {

    private long userCount;
    private long roleCount;
    private long menuCount;
    private long loginLogCount;
    private long operLogCount;
    private long aiTaskTotal;
    private long aiTaskSucceeded;
    private long aiTaskFailed;
    private long aiTaskRunning;
}

