package com.example.admin.module.monitor.vo;

import com.example.admin.module.report.vo.NameValueVo;
import lombok.Builder;
import lombok.Data;

import java.util.List;

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
    /** R4-1.26：FAILED 任务按失败分类(error_type)分层计数；"other" 兜底 error_type 为空的失败任务，各桶之和等于 aiTaskFailed。 */
    private List<NameValueVo> aiTaskFailedByErrorType;
}

