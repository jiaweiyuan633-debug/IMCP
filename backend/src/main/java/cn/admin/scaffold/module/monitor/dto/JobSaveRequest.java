package cn.admin.scaffold.module.monitor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JobSaveRequest {

    private Long id;

    @NotBlank(message = "任务名称不能为空")
    @Size(max = 100, message = "任务名称长度不能超过 100")
    private String jobName;

    @NotBlank(message = "任务组名不能为空")
    private String jobGroup;

    @NotBlank(message = "调用目标不能为空")
    private String invokeTarget;

    @NotBlank(message = "Cron 表达式不能为空")
    private String cronExpression;

    private String misfirePolicy;
    private Integer concurrent;
    private Integer status;
    private String remark;
}

