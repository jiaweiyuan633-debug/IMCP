package cn.admin.scaffold.module.report.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/** 最近操作记录（大屏滚动列表）。 */
@Data
@AllArgsConstructor
public class RecentOperVo {

    private Long userId;
    private String module;
    private String action;
    private Integer status;
    private Long durationMs;
    private LocalDateTime operTime;
}
