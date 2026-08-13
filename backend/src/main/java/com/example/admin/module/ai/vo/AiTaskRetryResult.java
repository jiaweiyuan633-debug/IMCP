package com.example.admin.module.ai.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiTaskRetryResult {

    /** 请求重试的任务总数。 */
    private int total;
    /** 已重新提交 AI 服务并置回 QUEUED 的任务数。 */
    private int succeeded;
    /** 未执行重试的任务数（不存在/非失败终态/已被并发处理）。 */
    private int skipped;
    /** 重试失败的任务数（服务禁用/不可用/AI 侧任务缺失）。 */
    private int failed;
    /** 重试失败的任务 ID，供前端定位。 */
    private List<Long> failedIds;
}
