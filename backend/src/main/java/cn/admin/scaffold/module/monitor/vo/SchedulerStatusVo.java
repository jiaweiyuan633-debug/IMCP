package cn.admin.scaffold.module.monitor.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 调度器集群状态视图对象。
 *
 * <p>聚合 Quartz 集群元数据与 QRTZ 调度表的实时统计，
 * 用于后台「定时任务 → 调度集群」面板观察分布式调度健康度。
 */
@Data
@Builder
public class SchedulerStatusVo {

    /** 是否启用 Quartz 集群（isClustered） */
    private boolean clustered;

    /** 当前实例 ID（集群中唯一，如 AUTO 分配的 UUID） */
    private String instanceId;

    /** 调度器实例名 */
    private String instanceName;

    /** 当前实例调度线程池大小 */
    private int threadPoolSize;

    /** 集群节点数（QRTZ_SCHEDULER_STATE 活跃行数） */
    private int nodeCount;

    /** 已注册 Job 数 */
    private int jobCount;

    /** 触发器总数 */
    private int triggerCount;

    /** 已暂停触发器数 */
    private int pausedTriggerCount;

    /** ERROR 状态触发器数 */
    private int errorTriggerCount;

    /** 当前正在执行的触发器数 */
    private int firedTriggerCount;

    /** 已过期待处理（潜在 misfire）触发器数 */
    private int overdueTriggerCount;
}
