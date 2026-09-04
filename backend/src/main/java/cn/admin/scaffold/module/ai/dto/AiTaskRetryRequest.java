package cn.admin.scaffold.module.ai.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AiTaskRetryRequest {

    /** 待重试的任务 ID 列表（上限 100，防止单次操作放大外部 HTTP 往返）。 */
    @NotEmpty(message = "任务 ID 列表不能为空")
    @Size(max = 100, message = "单次最多重试 100 个任务")
    private List<Long> ids;
}
