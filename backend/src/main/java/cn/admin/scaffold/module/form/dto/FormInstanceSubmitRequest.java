package cn.admin.scaffold.module.form.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class FormInstanceSubmitRequest {

    /** 业务流水号：用作幂等键 */
    @NotBlank(message = "业务流水号不能为空")
    private String bizNo;

    @NotBlank(message = "表单编码不能为空")
    private String formCode;

    /** 提交数据：key=字段 key，未知 key 忽略 */
    private Map<String, Object> data;
}
